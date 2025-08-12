import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;
import javax.swing.JPanel;

public class DrawKomodo extends JPanel{
    public static final int W = 600, H = 600;
    public static void main(String[] args) {
        createGUI();
    }

    static public void createGUI(){
        DrawKomodo panel = new DrawKomodo();
        JFrame f = new JFrame();
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setContentPane(panel);
        f.pack();
        f.setVisible(true);
    }

    public DrawKomodo(){
        this.setPreferredSize(new Dimension(W,H));
        this.setBackground(Color.WHITE);
    }

    @Override
    protected void paintComponent(Graphics g) {
        BufferedImage buffer = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = buffer.createGraphics();
        g2.setComposite(AlphaComposite.Src);
        g2.setColor(Color.WHITE);
        g2.drawRect(0, 0, W, H);
        g2.setColor(Color.BLACK);
        // drawColorKom(g2);
        drawBlackKom(g2);
        g2.dispose();
        g.drawImage(buffer, 0, 0, null);
    }

    private void drawBlackKom(Graphics2D g2){
        KomodoPoint.draw(g2, Color.BLACK, Color.BLACK);
    }

    private void drawColorKom(Graphics2D g2){
        KomodoPoint.draw(g2, new Color(75,83,32), new Color(218,112, 214));
    }

    // public Point cubicBerzierCurve(double t, Point p0, Point p1, Point p2, Point p3)
    // {
    //     if(t < 0.0) t = 0.0;
    //     else if(t > 1.0) t = 1.0;
        
    //     double u = 1.0 - t;
    //     double u2 = u * u;
    //     double u3 = u2 * u;
    //     double t2 = t * t;
    //     double t3 = t2 * t;

    //     double x = u3 * p0.x + 3 * u2 * t * p1.x + 3 * u * t2 * p2.x + t3 * p3.x;
    //     double y = u3 * p0.y + 3 * u2 * t * p1.y + 3 * u * t2 * p2.y + t3 * p3.y;
    //     return new Point(x, y);
    // }

    // public void generateCubicBezierCurve(Graphics2D g2, KomodoPath.Cubic recordControlPoint, int n)
    // {
    //     if(n < 1)
    //     {
    //         throw new IllegalArgumentException("n must be at least 1");
    //     }

    //     Point p0 = new Point(recordControlPoint.p0().x(), recordControlPoint.p0().y());
    //     Point p1 = new Point(recordControlPoint.c1().x(), recordControlPoint.c1().y());
    //     Point p2 = new Point(recordControlPoint.c2().x(), recordControlPoint.c2().y());
    //     Point p3 = new Point(recordControlPoint.p3().x(), recordControlPoint.p3().y());

    //     Point prev = p0;
    //     for(int i = 1; i <= n; i++)
    //     {
    //         double t = i /(double) n;
    //         Point newPoint = cubicBerzierCurve(t, p0, p1, p2, p3);
    //         int x = (int) Math.round(newPoint.x);
    //         int y = (int) Math.round(newPoint.y);
    //         g2.drawLine((int) Math.round(prev.x), (int) Math.round(prev.y), x, y);

    //         prev = newPoint;
    //     }
    // }

    // public void plot(Graphics2D g2,int x, int y)
    // {
    //     g2.setColor(Color.BLACK);
    //     g2.fillRect(x, y, 3, 3);
    // }

    // public class Point {
    //     public double x;
    //     public double y;

    //     public Point(double x, double y)
    //     {
    //         this.x = x;
    //         this.y = y;
    //     }
    // }
}
