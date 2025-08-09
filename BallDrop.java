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
    private final double reboundForce = 0.5;
    private double vx = 150; //Velocity move ball in x axis
    private double vy = 0; //Velocity in y axis
    private final double friction = 0.98;
    private final double mu = 0.07; //Just Appoximate
    
    //bounce
    private boolean isBounced = false;
    private double prevY = y;
    private boolean onGround = false;

    //Transform setting
    private final int tolPoint = 20; //tolerance point that ball will transform into animal
    enum Mode {BALL, GLOWING, KOMODO};
    private Mode mode = Mode.BALL;

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
        double startTime = System.currentTimeMillis();

        double currentTime, elapsedTime;

        while (true) {
            currentTime = System.currentTimeMillis();
            elapsedTime = (currentTime - lastTime)/1000;
            lastTime = currentTime;

            //Physics Apply method
            updatePhysics(elapsedTime);
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, W, H);
        drawBall(g2, x, y, ballRadius);
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
    private void updatePhysics(double elapsedTime){
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

        if(onGround){
            double ax = -mu * gravity * Math.signum(vx);
            double newVx = vx + ax * elapsedTime;
            if(vx != 0 && Math.signum(newVx) != Math.signum(vx)) vx = 0;
            vx = newVx;
        }

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
