import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import javax.swing.*;


public class BallDrop extends JPanel implements Runnable{
    private static final int H = 600, W = 600;
    private final double ballRadius = 70;

    //Physics logic
    private double x = 80, y = 80;
    private final double gravity = 2000; // px/s^2 (200 pixels * 10(~9.8 -->gravity acceleration))
    private final double reboundForce = 0.8;
    private double vx = 150; //Velocity move ball in x axis
    private double vy = 0; //Velocity in y axis
    private final double mu = 0.07; //Just Appoximate, use to calculate friction of ball when ball touch floor
    
    //bounce
    private boolean isBounced = false;
    private double prevY = y;
    private boolean onGround = false;

    //Transform setting
    private final int tolPoint = 20; //tolerance point that ball will transform into animal
    enum Mode {BALL, GLOWING, KOMODO, FLASH};
    private Mode mode = Mode.BALL;
    private boolean isGlowStart = false;
    private double glowingStartTime = 0;
    private final double glowingDuration = 3;
    private final double flashDuration = 0.3; //Approximately
    private boolean flashLocked = false;
    private double flastStartTime = 0;
    private final double flashOverlap = 0.25;

    public static void main(String[] args) {
        createGUI();
    }

    public static void createGUI(){
        BallDrop panel = new BallDrop();
        JFrame f = new JFrame();
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setContentPane(panel);
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);

        (new Thread(panel)).start();
    }

    public BallDrop(){
        this.setPreferredSize(new Dimension(W,H));
        this.setBackground(Color.WHITE);
    }

    @Override
    public void run() {
        double lastTime = System.currentTimeMillis();

        double currentTime, elapsedTime;

        while (true) {
            currentTime = System.currentTimeMillis();
            elapsedTime = (currentTime - lastTime)/1000.0;
            lastTime = currentTime;

            //Physics Apply method
            updatePhysics(elapsedTime, currentTime);
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, W, H);

        if(mode != Mode.KOMODO){
            drawBall(g2, x, y, ballRadius);
        }
        
        if(mode == Mode.GLOWING){
            double t = (System.currentTimeMillis() - glowingStartTime)/1000.0;
            double flashTime = (System.currentTimeMillis() - flastStartTime)/1000.0;
            float glowP = normalize((float) (t/glowingDuration)); //1e-6 : use for prevent divided by zero
            float flashP = normalize((float) (flashTime / flashDuration));
            drawGlow(g2, x, y, glowP, (int) ballRadius); //Make ball glowing golden light
            if(flashLocked){
                drawSolidWhite(g2);
            }else if(flashP > 0f){
                drawFlashCutscene(g2, x, y, flashP, (int) ballRadius);
                if(flashP >= 1f){
                    flashLocked = true;
                    mode = Mode.KOMODO;
                    System.out.println(mode);
                }
            }
        }

    }

    private void drawBall(Graphics2D g2, double cx, double cy, double r){
        //Draw Outter ring of ball
        double innerRingRadius = r * (1.0/3.5);
        g2.setColor(Color.BLACK);
        int xc = (int) Math.round(cx);
        int yc = (int) Math.round(cy);
        int R = (int) Math.round(r);

        int innerR = (int) Math.round(innerRingRadius);
        g2.setColor(Color.RED);
        g2.fill(new Arc2D.Double(cx-r, cy-r, 2*r, 2*r, 0, 180, Arc2D.PIE));

        g2.setColor(Color.WHITE);
        g2.fill(new Arc2D.Double(cx-r, cy-r, 2*r, 2*r, 180, 180, Arc2D.PIE));
        g2.fill(new Ellipse2D.Double(cx-innerR,cy-innerR,2*innerR,2*innerR));

        g2.setColor(Color.BLACK);
        midpointCircle(g2, xc, yc, R);
        midpointCircle(g2, xc, yc, innerR);
        g2.drawRect(xc-R, yc, R-innerR, (int) Math.round(R*0.01));
        g2.drawRect(xc+innerR, yc, R-innerR, (int) Math.round(R*0.01));
    }

    //Physics Apply Method
    private void updatePhysics(double elapsedTime, double currentTime){
        prevY = y;

        //Make ball fall
        vy += gravity * elapsedTime;
        x += vx * elapsedTime;
        y += vy * elapsedTime;

        //Check if ball touch ground
        if(y + ballRadius > H){
            y = H - ballRadius;
            if(Math.abs(vy) < 20) vy = 0; //Prevent ball spam bounce
            else vy = -vy * reboundForce;
            isBounced = true;
        }

        //If ball touch ceil
        if(y - ballRadius < 0){
            y = H;
            vy -= gravity * reboundForce;
        }

        //If ball touch left side wall
        if(x - ballRadius < 0){
            x = ballRadius;
            vx = -vx * reboundForce;
        }

        //If ball touch right side wall
        if(x + ballRadius > W){
            x = W - ballRadius;
            vx = -vx * reboundForce;
        }

        //Make ball rolling friction
        onGround = (y + ballRadius >= H - 0.5)? true : false;

        if(onGround){ //Friction
            double ax = -mu * gravity * Math.signum(vx);
            double newVx = vx + ax * elapsedTime;
            if(vx != 0 && Math.signum(newVx) != Math.signum(vx)) vx = 0;
            vx = newVx;
        }

        //Make the ball glowing
        Double mid = H/2.0;
        if(!isGlowStart && isBounced && mode == Mode.BALL){
            boolean movingUp = vy < 0;
            boolean isCrossMid = (prevY > mid + tolPoint) && (y <= mid + tolPoint);
            if(movingUp && isCrossMid){
                mode = Mode.GLOWING;
                isGlowStart = true;
                glowingStartTime = currentTime;
            }
        }

        //Prepare for flash
        if(mode == Mode.GLOWING){
            flashLocked = false;
            flastStartTime = glowingStartTime + (long)((glowingDuration - flashOverlap)*1000.0);
        }
        
    }

    private void drawGlow(Graphics2D g2, double cx, double cy, float p, int baseRadius){
        //p is progress of glowing animation
        float t = normalize(p); //Normalize progress value into 0...1 range

        float corePhase = normalize(t/0.35f);
        float expanPhase = normalize((t - 0.35f)/0.65f);

        float rCore = baseRadius * (1f + 0.4f * eassingOutCubic(corePhase)); //Radius of glowing light in core phase
        float aCore = lerp(0.2f, 1f, eassingOutCubic(corePhase)); //Alpha Core, core brightness

        float diag = (float) Math.hypot(W, H); //Get screen diagonal, use for bright glowing entire screen

        float rHalo = lerp(rCore, 1.10f * diag, eassingOutCubic(expanPhase)); //Halo radius
        float aHalo = lerp(0.0f, 0.3f, eassingOutCubic(expanPhase)); // Alpha value of halo, Halo strength

        Point2D c = new Point2D.Float((float)cx,(float)cy);

        Paint oldPaint = g2.getPaint();
        Composite oldCompo = g2.getComposite();

        g2.setComposite(AlphaComposite.SrcOver);

        { //Draw core light at the ball
            float[] coreFraction = {0.0f, 0.20f, 1f};

            Color[] coreColors = {
                new Color(255,255,240, Math.round(255 * aCore)),
                new Color(255, 255, 120, Math.round(255 * (0.8f * aCore))),
                new Color(255,255,120, 0)
            };
            RadialGradientPaint rGradientPaint = new RadialGradientPaint(c, rCore, coreFraction, coreColors);
            g2.setPaint(rGradientPaint);
            g2.fill(new Ellipse2D.Float((float)(cx - rCore),(float)(cy-rCore),2*rCore,2*rCore));
        }

        { //Draw Expan light
            float[] expanFraction = {0f, 1f};
            Color[] haloColors = {
                new Color(255, 220, 90, Math.round(255 * aHalo)),
                new Color(255, 220, 90, 0)
            };
            RadialGradientPaint rGradientPaint = new RadialGradientPaint(c, rHalo, expanFraction, haloColors);
            g2.setPaint(rGradientPaint);
            g2.fillRect(0,0,W,H);
        }

        g2.setPaint(oldPaint);
        g2.setComposite(oldCompo);
    }

    private void drawFlashCutscene(Graphics2D g2, double cx,double cy, float p,int baseRadius){
        float t = normalize(p); //Normalze progress into 0...1

        float diag = (float) Math.hypot(W, H); //Calculate screen diagonal, make flash can reach entire screen
        float r = lerp(baseRadius * 1.4f, 1.05f * diag, eassingOutCubic(t));
        float core = eassingOutCubic(t); //Ease in quadratic. bright in core of ball
        float wash = 0.85f * eassingOutCubic(t); //Draw WHITE color with 85% of opacity

        Point2D center = new Point2D.Float((float) cx, (float) cy);
        float[] fraction = {0.0f, 0.1f, 1f};
        Color[] colors = {
            new Color(255,255,255, Math.round(255 * core)),
            new Color(255,245,210, Math.round(255 * (0.5f * core))),
            new Color(255,245,210, 0)  
        };

        RadialGradientPaint rGradientPaint = new RadialGradientPaint(center, r, fraction, colors);

        Paint oldPaint = g2.getPaint(); //Save current paint
        Composite oldCompo = g2.getComposite(); // Save current composite

        g2.setComposite(AlphaComposite.SrcOver);
        g2.setPaint(rGradientPaint);
        g2.fillRect(0, 0, W, H);


        g2.setComposite(AlphaComposite.SrcOver.derive(wash));
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, W, H);

        g2.setPaint(oldPaint);//Restore paint
        g2.setComposite(oldCompo);//Restore Composite
    }

    private void drawSolidWhite(Graphics2D g2){
        g2.setComposite(AlphaComposite.SrcOver);
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, W, H);
    }

    private float normalize(float x){
        return (x < 0f)? 0f : (x >= 1f)? 1f : x;
    }

    private float easingInQuad(float x){
        x = normalize(x);
        return (float) Math.pow(x, 2);
    }

    private float eassingInCubic(float t){
        t = normalize(t);
        return (float) Math.pow(t, 3);
    }

    private float lerp(float a, float b, float t){
        return a + (normalize(t) * (b-a)); //Linear interpolation A + t * (B-A) where t is value between 0...1
    }

    private float eassingOutCubic(float t){
        return 1f - ((float) Math.pow( 1 - normalize(t), 3));
    }

    //Implement midpoint circle
    public void midpointCircle(Graphics2D g, int xc, int yc, int r)
    {
        int x = 0;
        int y = r;
        int Dx = 2 * x;
        int Dy = 2 * y;
        int D = 1 - r;

        while (x <= y) 
        {
            plot(g, x + xc, y + yc);
            plot(g, -x + xc, y + yc);
            plot(g, x + xc, -y + yc);
            plot(g, -x + xc, -y + yc);
            plot(g, y + xc, x + yc);
            plot(g, -y + xc, x + yc);
            plot(g, y + xc, -x + yc);
            plot(g, -y + xc, -x + yc);

            x++;
            Dx += 2;
            D += Dx + 1;

            if(D >= 0)
            {
                y--;
                Dy -= 2;
                D -= Dy;
            }
        }
    }
    
    public void plot(Graphics g, int x, int y)
    {
        g.fillRect(x, y, 2, 2);
    }
}
