import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.LinkedList;
import java.util.Queue;

public class KomodoDrawing extends JPanel {
    //Screen size
    private static final int W = 600, H = 600;

    //Buffer
    private final BufferedImage buf = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);         
    private int penRGB = Color.BLACK.getRGB(); // sync pen color for buf

    //Colors
    private static final Color backgroundColor  = new Color(243, 233, 215);;
    private static final Color outline   = new Color(43,  43,  43 );
    private static final Color bodyColorFill = new Color(110, 143, 94 );
    private static final Color bodySpot = new Color(90,  118, 79 );
    private static final Color belly     = new Color(143, 174, 127);
    private static final Color eyeColor = new Color(255, 255, 255);
    private static final Color pupil     = new Color(16,  16,  16 );
    private static final Color tongue    = new Color(180, 71,  61 );
    private static final Color ground    = new Color(175, 165, 147);

    public static void main(String[] args) {
        JFrame f = new JFrame();
        KomodoDrawing p = new KomodoDrawing();
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        p.setPreferredSize(new Dimension(W, H));
        f.setContentPane(p);
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        // Clear background in buffer (floodFill will use backgroundColor as target color)
        fillBuffer(backgroundColor.getRGB());

        /* ground */
        useColor(g,ground);
        bresenhamLine(g,25, 425, 575, 425); // straight ground line
        for (int xs = 30; xs <= 570; xs += 4) { // tiny sand texture
            int y = 425 + (int)(2 * Math.sin(xs * 0.08));
            plot(g,xs, y);
        }

        //Body outline by using Cubic Bezier Connect adjacent points with straight lines using the Bresenham algorithm.
        useColor(g,outline);

        // Top contour
        int[][] top = new int[][]{
                {112,369, 122,356, 134,350, 150,349},
                {150,349, 188,343, 238,337, 281,343},
                {281,343, 350,350, 400,356, 438,359},
                {438,359, 488,362, 525,369, 550,381}
        };
        // Bottom contour
        int[][] bot = new int[][]{
                {550,381, 525,393, 488,400, 444,399},
                {444,399, 400,397, 350,390, 306,387},
                {306,387, 262,384, 225,381, 194,378},
                {194,378, 162,376, 138,376, 125,373}
        };
        for (int[] c : top)  drawCubicBezier(g,c, 180);
        for (int[] c : bot)  drawCubicBezier(g,c, 180);
        bresenhamLine(g,112,369, 125,373); // close the snout gap

        //  Head details 
        useColor(g,eyeColor); midpointCircle(g, 147, 362, 4);
        useColor(g,pupil);     midpointCircle(g, 147, 362, 1);
        useColor(g,outline);   midpointEllipse(g, 131, 366, 3, 1); //nostril
        bresenhamLine(g,125,373, 149,371); // mouth

        // Forked tongue
        useColor(g,tongue);
        bresenhamLine(g,112,369, 103,370);
        bresenhamLine(g,103,370,  99,367);
        bresenhamLine(g,103,370,  99,372);

        /*  Legs & claws  */
        useColor(g,outline);
        // Front leg
        midpointEllipse(g, 225, 384, 6, 4); // shoulder
        bresenhamLine(g,222, 387, 206, 406); // upper
        bresenhamLine(g,206, 406, 222, 415);  // fore
        midpointEllipse(g, 222, 416, 8, 4);// palm
        bresenhamLine(g,219, 419, 216, 422); // claws
        bresenhamLine(g,222, 419, 219, 422);
        bresenhamLine(g,225, 419, 222, 422);

        // Hind leg
        midpointEllipse(g, 378, 387, 8, 5); // hip
        bresenhamLine(g,375, 391, 359, 409);// thigh
        bresenhamLine(g,359, 409, 375, 417);// shin
        midpointEllipse(g, 376, 419, 8, 4); // foot
        bresenhamLine(g,372, 420, 369, 423);// claws
        bresenhamLine(g,376, 420, 373, 425);
        bresenhamLine(g,380, 420, 378, 425);

        //ticks on back
        useColor(g,outline);
        for (int xs = 200; xs <= 438; xs += 16) {
            //Calculate the Y position along the parabola curve that make the pattern sticks to the back line.
            int ys = (int)(-0.00075 * (xs - 312) * (xs - 312) + 362);
            bresenhamLine(g,xs, ys, xs + 6, ys - 6);
        }

        //  Close tiny tail gap
        bresenhamLine(g,441, 399, 444, 399);

        /* floodfill */

        // Body fill backgroundColor
        floodFill(buf, 281, 375, backgroundColor, bodyColorFill);

        // belly border line
        useColor(g,outline);
        bresenhamLine(g,162, 381, 431, 393);
        // belly tint 
        floodFill(buf, 312, 390, bodyColorFill, belly);

        //Spots by midpoint circle
        useColor(g,bodySpot);
        int[][] spots = {
                {262,365,4}, {284,362,3}, {325,363,4},
                {353,369,3}, {381,372,4}, {403,376,3}, {425,380,2}
        };
        for (int[] s : spots) midpointCircle(g, s[0], s[1], s[2]);

        // Tail stripes 
        useColor(g,outline);
        drawCubicBezier(g,new int[]{450,381, 459,378, 469,382, 478,380}, 60);
        drawCubicBezier(g,new int[]{462,386, 472,383, 481,387, 492,384}, 60);

        //Present buffer
        g.drawImage(buf, 0, 0, null);
    }

    /*Algorithms */

    // Bresenham
    public void bresenhamLine(Graphics g,int x1, int y1, int x2, int y2) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);

        int sx = (x1 < x2)? 1 : -1; //Step x
        int sy = (y1 < y2)? 1 : -1; //Step y

        boolean isSwap = false;

        if(dy > dx) { // swap if slope is steep
            int temp = dx; dx = dy; dy = temp;
            isSwap = true;
        }

        int D = 2 * dy - dx;
        int x = x1, y = y1;

        for(int i = 0; i <= dx; i++) {
            plot(g,x, y);
            if(D >= 0) {
                if(isSwap) x += sx;
                else       y += sy;
                D -= 2*dx;
            }
            if(isSwap) y += sy;
            else       x += sx;

            D += 2 * dy;
        }
    }

    // Cubic Bezier
    public Point cubicBerzierCurve(double t, Point[] controlPoints) {
        if(controlPoints == null || controlPoints.length == 0)
            throw new IllegalArgumentException("Control points cannot be null or empty");
        if(t < 0.0 || t > 1.0)
            System.err.println("t value is outside [0,1] range");

        Point p1 = controlPoints[0];
        Point p2 = controlPoints[1];
        Point p3 = controlPoints[2];
        Point p4 = controlPoints[3];

        double x = (Math.pow((1-t), 3) * p1.x) + (3 * t * Math.pow(1-t, 2) * p2.x)
                 + (3 * Math.pow(t, 2) * (1 - t) * p3.x) + (Math.pow(t, 3) * p4.x);
        double y = (Math.pow((1-t), 3) * p1.y) + (3 * t * Math.pow(1-t, 2) * p2.y)
                 + (3 * Math.pow(t, 2) * (1 - t) * p3.y) + (Math.pow(t, 3) * p4.y);

        return new Point((int)Math.round(x), (int)Math.round(y));
    }

    // Stroke a Bezier by connecting sampled points with Bresenham
    private void drawCubicBezier(Graphics g,int[] c, int steps) {
        Point[] cps = new Point[]{
                new Point(c[0],c[1]), new Point(c[2],c[3]),
                new Point(c[4],c[5]), new Point(c[6],c[7])
        };
        Point prev = cubicBerzierCurve(0.0, cps);
        for (int i = 1; i <= steps; i++) {
            Point cur = cubicBerzierCurve(i/(double)steps, cps);
            bresenhamLine(g,prev.x, prev.y, cur.x, cur.y);
            prev = cur;
        }
    }

    // Flood fill on buffer
    public BufferedImage floodFill(BufferedImage m, int x, int y, Color target_colour, Color replacement_Colour) {
        Queue<Point> q = new LinkedList<>();
        q.add(new Point(x, y));

        while(!q.isEmpty()) {
            Point currentPoint = q.poll();
            int currentX = currentPoint.x;
            int currentY = currentPoint.y;

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

    // Midpoint circle
    public void midpointCircle(Graphics g, int xc, int yc, int r) {
        int x = 0;
        int y = r;
        int Dx = 2 * x;
        int Dy = 2 * y;
        int D = 1 - r;

        while (x <= y) {
            plot(g,  x + xc,  y + yc);
            plot(g, -x + xc,  y + yc);
            plot(g,  x + xc, -y + yc);
            plot(g, -x + xc, -y + yc);
            plot(g,  y + xc,  x + yc);
            plot(g, -y + xc,  x + yc);
            plot(g,  y + xc, -x + yc);
            plot(g, -y + xc, -x + yc);

            x++;
            Dx += 2;
            D  += Dx + 1;

            if(D >= 0) {
                y--;
                Dy -= 2;
                D  -= Dy;
            }
        }
    }

    // Midpoint ellipse
    public void midpointEllipse(Graphics g,int xc, int yc, int a, int b) {
        int a2 = a * a;
        int b2 = b * b;
        int twoA2 = 2 * a2;
        int twoB2 = 2 * b2;

        // Region 1
        int x = 0;
        int y = b;

        int D  = (int) Math.round(b2 - a2 * b + (a2/4.0));
        int Dx = 0, Dy = twoA2 * y;

        while(Dx <= Dy) {
            plot(g,x+xc,y+yc);
            plot(g,x+xc,-y+yc);
            plot(g,-x+xc,y+yc);
            plot(g,-x+xc,-y+yc);

            x++;
            Dx += twoB2;
            D  += Dx + b2;

            if(D >= 0) {
                y--;
                Dy -= twoA2;
                D  -= Dy;
            }
        }

        // Region 2
        x = a; y = 0;
        D  = (int) Math.round(a2 - b2*a + (b2/4.0));
        Dx = twoB2*x;
        Dy = 0;

        while(Dx >= Dy) {
            plot(g,x+xc,y+yc);
            plot(g,x+xc,-y+yc);
            plot(g,-x+xc,y+yc);
            plot(g,-x+xc,-y+yc);

            y++;
            Dy += twoA2;
            D  += Dy + a2;

            if(D >= 0) {
                x--;
                Dx -= twoB2;
                D  -= Dx;
            }
        }
    }

    //plot
    public void plot(Graphics g, int x, int y) {
        g.fillRect(x, y, 1, 1);  // draw to screen
        if (x>=0 && x<W && y>=0 && y<H) {
            buf.setRGB(x, y, penRGB); // mirror into buffer
        }
    }

    //SetColor
    private void useColor(Graphics g,Color c) { g.setColor(c); penRGB = c.getRGB(); }

    private void fillBuffer(int argb) {
        int[] row = new int[W];
        for (int i = 0; i < W; i++) row[i] = argb;
        for (int y = 0; y < H; y++) buf.getRaster().setDataElements(0, y, W, 1, row);
    }

    class Point {
        public int x, y;
        public Point(int x, int y) { this.x = x; this.y = y; }
    }
}
