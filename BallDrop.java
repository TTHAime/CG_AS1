import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Queue;
import java.util.LinkedList;

import javax.swing.*;


public class BallDrop extends JPanel implements Runnable{

    private static final int W = 600, H = 600;
    
    //Ball setting
    private final int ballRadius = 70;
    private final int innerBallRadius = Math.round(ballRadius/3.5f);
    private double x = ballRadius+10;
    private double y = ballRadius;
    
    //Bounce
    private boolean onGround = false;

    //Physics Setting
    private double vx = 150;
    private double vy = 0;
    private final double gravity = 2000; //Gravity (px/s^2)
    private final int groundY = H-2;
    private boolean isStopped = false;
    private boolean wasStopped = false;
    private final double reboundForce = 0.7;
    private final double mu = 0.07; //Just Appoximate, use to calculate friction of ball when ball touch floor.
    
    //Flash setting
    private boolean flashing = false;
    private double flashStartTime = 0;
    private double flashDuration = 2;

    //finish flashing
    private boolean isComplete = false;

    //Color
    private final Color outlineColor = new Color(30, 30, 30);
    private final Color red = Color.RED;
    private final Color white = Color.WHITE;
    private final Color band    = new Color(20,20,20);
    private Color[] flashColor;

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

        while (!isComplete) {
            currentTime = System.currentTimeMillis();
            elapsedTime = (currentTime - lastTime)/1000.0;
            lastTime = currentTime;

            //Physics Apply method
            updatePhysics(elapsedTime, currentTime);

            repaint();
        }
    }

    @Override protected void paintComponent(Graphics g) {
        g.setColor(white);
        g.fillRect(0, 0, W, H);

        BufferedImage buf = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics gBuf = buf.createGraphics();
        //Draw gound line
        gBuf.setColor(Color.BLACK);
        bresenhamLine(gBuf, 0, groundY, W-1, groundY);

        //draw Ball
        int ballCenterX = (int) Math.round(x);
        int ballCenterY = (int) Math.round(y);

        gBuf.setColor(outlineColor);
        midpointCircle(gBuf, ballCenterX, ballCenterY, ballRadius);

        //Band in Ball
        gBuf.setColor(band);
        bresenhamLine(gBuf, ballCenterX - ballRadius, ballCenterY , ballCenterX + ballRadius,ballCenterY);
        bresenhamLine(gBuf, ballCenterX - ballRadius, ballCenterY - 1, ballCenterX + ballRadius,ballCenterY - 1);
        bresenhamLine(gBuf, ballCenterX - ballRadius, ballCenterY + 1, ballCenterX + ballRadius,ballCenterY + 1);

        //Inner circle
        gBuf.setColor(outlineColor);
        midpointCircle(gBuf, ballCenterX, ballCenterY, innerBallRadius);

        //Floodfill
        // System.out.println("CENTER : "+buf.getRGB(ballCenterX, ballCenterY));
        floodFill(buf, ballCenterX, ballCenterY - (ballRadius/2), new Color(buf.getRGB(ballCenterX, ballCenterY - (ballRadius/2)),true), red); //Top half ball
        floodFill(buf, ballCenterX, ballCenterY + (ballRadius/2), new Color(buf.getRGB(ballCenterX, ballCenterY + (ballRadius/2)), true), white); //Bottom half ball
        floodFill(buf, ballCenterX, ballCenterY, new Color(buf.getRGB(ballCenterX, ballCenterY)), white); //Inner Circle

        g.drawImage(buf, 0, 0, null);

        //Start to flash.
        if(!wasStopped && isStopped && !flashing){
            flashing = true;
            flashStartTime = System.currentTimeMillis() / 1000.0;
        }
        wasStopped = isStopped; //wasStopped use for prevent above if run more than 1 times

        if(flashing){
            // System.out.println("Flashing");
            double current = System.currentTimeMillis() / 1000.0;
            double t = (current - flashStartTime) / flashDuration;
            if(t >= 1.0f){
                t = 1.0;
                flashing = false;
                isComplete = true;
            }

            drawFlash(g, ballCenterX, ballCenterY, t, ballRadius);
        }

        if(isComplete && !flashing){
            drawWhiteScreen(g);
        }
    }


    private void updatePhysics(double elapsedTime, double currentTime){
        if(isStopped) return;
        vy += gravity * elapsedTime;
        x += vx * elapsedTime;
        y += vy * elapsedTime;

        //Check ball touch ground
        if(y + ballRadius > groundY){
            y = groundY - ballRadius;
            if(Math.abs(vy) < 20) vy = 0; //Prevent ball spam bounce
            else vy = -vy * reboundForce;
        }

        //Check ball touch ceil
        if(y - ballRadius < 0){
            y = 1 + ballRadius;
            vy = -vy * reboundForce;
        }

        if(x - ballRadius < 0){
            x = ballRadius;
            vx = -vx * reboundForce;
        }

        if(x + ballRadius > W){
            x = W - ballRadius;
            vx = -vx * reboundForce;
        }

        onGround = (y + ballRadius >= groundY- 0.5)? true : false;

        if(onGround){ //Friction
            double ax = -mu * gravity * Math.signum(vx);
            double newVx = vx + ax * elapsedTime;
            if(vx != 0 && Math.signum(newVx) != Math.signum(vx)) vx = 0;
            vx = newVx;
        }

        isStopped = (onGround && Math.abs(vx) < 0.5 && Math.abs(vy) < 0.5);
    }
    
    //Create white color for make flash on screen
    private void flashPalette(){
        if(flashColor != null) return;
        flashColor = new Color[256];
        for(int alpha = 0; alpha < 256; alpha++){
            flashColor[alpha] = new Color(255,255,255,alpha); 
        }
    }

    //Make/draw flash after ball is stop.
    private void drawFlash(Graphics g, double x, double y, double t, double radius){
        flashPalette();
        int xc = (int) Math.round(x), yc = (int) Math.round(y);
        t = clamp01(t);

        //SmoothStep function
        double smoothS = t * t * (3.0 - 2.0 * t);


        //Make core of ball flash
        int diag = (int) Math.hypot(W, H); //Screen diagonal
        int r = (int)Math.round(lerp(radius * 1.4, 1.05 * diag, smoothS)); //radius of flash in core,which span from core to 105% of screen.
        int alphaCore = Math.min(255, (int)(255 * smoothS)); //Brightness of flash according distance.
        g.setColor(flashColor[alphaCore]); //Use color from flashColor that is make from flashPalette().
        fillMidpointCircle(g, xc, yc, r);

        //Make screen white by flash
        if(t > 0.85f){
            double tt = (t - 0.85f) / 0.15; //Duration of make screen white.
            int alpha = (int)(Math.min(255, 255 * tt));
            g.setColor(flashColor[alpha]);
            int step = (alpha < 200)? 2 : 1; //help to prevent overload.
            for(int yy = 0; yy < H; yy += step){
                bresenhamLine(g, 0, yy, W-1, yy);
            }
        }
    }

    private void drawWhiteScreen(Graphics g){
        if(!isComplete) return;
        for(int yy = 0; yy < H; yy+=2){
            bresenhamLine(g, 0, yy, W-1, yy);
        }
    }

    //Bresenham + Cubic Bezier (Use bresenham as plot)
    public void bresenhamCubicBezier(Graphics g, Point[] points, int steps){
        Point prev = cubicBerzierCurve(0, points);
        for(int i = 1; i <= steps; i++){
            double t = i/ (double) steps;
            Point current = cubicBerzierCurve(t, points);
            bresenhamLine(g, (int) Math.round(prev.x), (int) Math.round(prev.x), (int) Math.round(current.x), (int) Math.round(current.y));

            prev = current;
        }
    }
    
    //Plot
    private void plot(Graphics g, int x, int y) {
        g.fillRect(x, y, 3, 3);
    }

    //Linear interpolation
    private double lerp(double a, double b,double t){
        t = clamp01(t);
        return (a + t*(b-a));
    }

    //Make value into range[0,1]
    private double clamp01(double t){
        return (t < 0)? 0 : ((t > 1)? 1 : t);
    }

    //Bresenham's line drawing method
    public void bresenhamLine(Graphics g,int x1, int y1, int x2, int y2)
    {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);

        int sx = (x1 < x2)? 1 : -1; //Step x
        int sy = (y1 < y2)? 1 : -1; //Step y

        boolean isSwap = false;

        if(dy > dx) //swap in case that slope is so steep
        {
            int temp = dx;
            dx = dy;
            dy = temp;
            isSwap = true;
        }

        int D = 2 * dy - dx;

        int x = x1;
        int y = y1;

        for(int i = 0; i <= dx; i++)
        {
            plot(g,x, y);
            if(D >= 0) //D is decision parameter / error term
            {
                //Minor Axis occur when D >= 0 ,This indicates that the line has deviated far enough to move minor Axis
                if(isSwap) x += sx;
                else y += sy;

                D -= 2*dx;
            }

            if(isSwap) y += sy;
            else x += sx;

            D += 2 * dy;
        }
    }

    // Midpoint circle (outline)
    private void midpointCircle(Graphics g, int xc, int yc, int r) {
        int x = 0, y = r;
        int D = 1 - r;
        while (x <= y) {
            plot(g,  xc + x, yc + y);
            plot(g,  xc - x, yc + y);
            plot(g,  xc + x, yc - y);
            plot(g,  xc - x, yc - y);
            plot(g,  xc + y, yc + x);
            plot(g,  xc - y, yc + x);
            plot(g,  xc + y, yc - x);
            plot(g,  xc - y, yc - x);
            x++;
            D += 2*x + 1;
            if (D >= 0) { y--; D -= 2*y; }
        }
    }

    //Midpoint Circle with fill color in circle
    public void fillMidpointCircle(Graphics g, int xc, int yc, int r){
        int x = 0;
        int y = r; //start at (0,radius), which upper of circle
        int d = 1 - r;

        while(x <= y){
            span(g, xc, yc, x, y);
            span(g, xc, yc, -x, y);
            span(g, xc, yc, y, x); //Upper octant
            span(g, xc, yc, -y, x); //Lower octant
            x++;
            d += 2*x + 1;
            if (d >= 0) { y--; d -= 2*y; }
        }
    }

    //Use to fill color in Midpoint circle by draw holizontal line (Bresenham).
    public void span(Graphics g,int xc, int yc, int yy, int xx){
        int yyy = yc + yy; //y that should draw
        if(yyy < 0 || yyy >= H) return;
        int x1 = (int) Math.round(xc - xx);
        int x2 = (int) Math.round(xc + xx);
        bresenhamLine(g, x1, yyy, x2, yyy);
    }

    //Cubic Bezier curve algorithm
    public Point cubicBerzierCurve(double t, Point[] controlPoints)
    {
        if(controlPoints == null || controlPoints.length == 0)
        {
            throw new IllegalArgumentException("Control points cannot be null or empty");
        }
        if(t < 0.0 || t > 1.0)
        {
            System.err.println("t value is outside [0,1] range");
        }

        Point p1 = controlPoints[0];
        Point p2 = controlPoints[1];
        Point p3 = controlPoints[2];
        Point p4 = controlPoints[3];

        double x = (Math.pow((1-t), 3) * p1.x) + (3 * t * Math.pow(1-t, 2) * p2.x) + (3 * Math.pow(t, 2) * (1 - t) * p3.x) + (Math.pow(t, 3) * p4.x);
        double y = (Math.pow((1-t), 3) * p1.y) + (3 * t * Math.pow(1-t, 2) * p2.y) + (3 * Math.pow(t, 2) * (1 - t) * p3.y) + (Math.pow(t, 3) * p4.y);

        return new Point(x, y);
    }

    public BufferedImage floodFill(BufferedImage m, int x, int y, Color target_colour, Color replacement_Colour)
    {   
        if(m == null) return null;

        Queue<Point> q = new LinkedList<>();
        q.add(new Point(x, y));

        while(!q.isEmpty())
        {
            Point currentPoint = q.poll();
            int currentX = (int) Math.round(currentPoint.x);
            int currentY = (int) Math.round(currentPoint.y);

            if(currentY+1 < m.getHeight() && m.getRGB(currentX,currentY+1) == target_colour.getRGB()){
                m.setRGB(currentX, currentY+1, replacement_Colour.getRGB());
                q.add(new Point(currentX,currentY+1));
            }
            if(currentY-1 >=0 && m.getRGB(currentX,currentY-1) == target_colour.getRGB()){
                m.setRGB(currentX, currentY-1, replacement_Colour.getRGB());
                q.add(new Point(currentX,currentY-1));
            }
            if(currentX+1 < m.getWidth() && m.getRGB(currentX+1,currentY) == target_colour.getRGB()){
                m.setRGB(currentX+1, currentY, replacement_Colour.getRGB());
                q.add(new Point(currentX+1,currentY));
            }
            if(currentX-1 >= 0 && m.getRGB(currentX-1,currentY) == target_colour.getRGB()){
                m.setRGB(currentX-1, currentY, replacement_Colour.getRGB());
                q.add(new Point(currentX-1,currentY));
            }
        }

        return m;
    }
    
    //Point(x,y)
    public class Point {
        public double x;
        public double y;

        public Point(double x, double y)
        {
            this.x = x;
            this.y = y;
        }
    }
   
}
