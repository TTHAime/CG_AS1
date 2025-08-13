import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;
import javax.swing.JPanel;

public class DrawKomodo extends JPanel{
    public static final int W = 600, H = 600;

    public static final double tx0 = 4.10999996856333, ty0 = 25.4699990461196;
    public static final double tx1 = 23.7599984020607, ty1 = 37.1581143299928;
    public static final double tx2 = 25.8693748021331, ty2 = 37.8731247103201;
    public static final double tx3 = 48.9823371125868, ty3 = 37.2374986054189;
    public static final double tx4 = 28.3274997833327, ty4 = 33.1871566105005;
    public static final double tx5 = 41.7374996807645, ty5 = 45.3487496531433;
    public static final double tx6 = 43.0762496705248, ty6 = 44.7524996577038;
    public static final double tx7 = 43.9199996640713, ty7 = 44.2124996618341;
    public static final double tx8 = 4.39499970442183, ty8 = 30.1349979733224;
    public static final double tx9 = 9.28499937555334, ty9 = 28.0124981160676;
    public static final AffineTransform at0 = AffineTransform.getTranslateInstance(tx0, ty0);
    public static final AffineTransform at1 = AffineTransform.getTranslateInstance(tx1, ty1);
    public static final AffineTransform at2 = AffineTransform.getTranslateInstance(tx2, ty2);
    public static final AffineTransform at3 = AffineTransform.getTranslateInstance(tx3, ty3);
    public static final AffineTransform at4 = AffineTransform.getTranslateInstance(tx4, ty4);
    public static final AffineTransform at5 = AffineTransform.getTranslateInstance(tx5, ty5);
    public static final AffineTransform at6 = AffineTransform.getTranslateInstance(tx6, ty6);
    public static final AffineTransform at7 = AffineTransform.getTranslateInstance(tx7, ty7);
    public static final AffineTransform at8 = AffineTransform.getTranslateInstance(tx8, ty8);
    public static final AffineTransform at9 = AffineTransform.getTranslateInstance(tx9, ty9);

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
        // drawBlackKom(g2);
        drawColorKom(g2);
        g2.dispose();
        g.drawImage(buffer, 0, 0, null);
    }

    private void drawBlackKom(Graphics2D g2){
        draw(g2, Color.BLACK, Color.WHITE);
    }

    private void drawColorKom(Graphics2D g2){
        draw(g2, new Color(75,83,32), new Color(218,112, 214));
    }

    private void drawBackground(Graphics2D g2){

    }
    public static Shape buildShape0Path() { //All Control point of my shape
      Path2D.Double p = new Path2D.Double();
      p.moveTo(0.0, 3.72);
      p.curveTo(0.775567, 2.39013, 2.01193, 2.41562, 3.32967, 2.10431);
      p.curveTo(5.41843, 0.686844, 7.85357, 0.400372, 11.13, 0.600001);
      p.curveTo(16.279, 1.51854, 21.4136, 1.79177, 26.5793, 2.4805);
      p.curveTo(29.9754, 2.9333, 33.355, 4.6447, 36.791, 5.19674);
      p.curveTo(40.3738, 5.77238, 44.6834, 5.49796, 47.67, 5.7);
      p.curveTo(51.846, 6.43094, 55.6398, 7.73537, 59.34, 9.18);
      p.curveTo(57.8136, 6.97295, 49.0761, 4.097, 44.01, 4.2);
      p.curveTo(41.1868, 4.18066, 38.0766, 4.07784, 36.18, 2.91);
      p.curveTo(35.188, 2.55782, 34.7279, 2.11818, 34.35, 1.2);
      p.lineTo(34.05, 0.0);
      p.curveTo(34.6958, 1.31667, 35.605, 2.29231, 36.93, 2.73);
      p.curveTo(39.0457, 3.00328, 41.1636, 3.35223, 43.26, 2.97);
      p.curveTo(50.6797, 3.20404, 57.9927, 3.59648, 63.06, 7.32);
      p.curveTo(63.9427, 9.70826, 64.8753, 12.0987, 63.03, 14.37);
      p.curveTo(61.3136, 16.205, 58.5136, 16.2004, 55.08, 15.12);
      p.curveTo(53.8267, 15.9289, 52.7998, 17.1097, 51.03, 17.07);
      p.curveTo(49.8258, 20.3109, 48.1557, 21.9387, 46.08, 21.99);
      p.curveTo(46.3498, 22.9417, 46.168, 23.0803, 46.17, 23.55);
      p.curveTo(45.8606, 22.8965, 45.4286, 22.2686, 44.76, 21.69);
      p.curveTo(44.1002, 21.649, 43.4861, 21.3178, 42.87, 21.0);
      p.curveTo(42.6053, 20.9613, 42.3405, 21.2781, 42.0758, 21.2592);
      p.curveTo(41.1609, 21.0593, 40.1814, 20.719, 38.91, 21.06);
      p.curveTo(38.3333, 20.8138, 37.7651, 20.0538, 37.17, 20.91);
      p.lineTo(36.93, 21.4313);
      p.curveTo(36.8868, 21.1411, 36.5032, 20.8973, 36.8512, 20.5538);
      p.curveTo(37.4, 19.9152, 38.0239, 19.6779, 38.6287, 19.3388);
      p.curveTo(39.4347, 18.7522, 40.2683, 18.6631, 41.0925, 18.405);
      p.lineTo(42.5775, 18.9675);
      p.curveTo(43.2048, 18.8105, 43.815, 18.7725, 44.4225, 18.7538);
      p.lineTo(44.9062, 17.8875);
      p.curveTo(42.333, 18.2162, 39.7528, 18.3344, 37.14, 17.475);
      p.curveTo(34.8119, 17.1349, 32.5017, 16.6349, 30.255, 15.69);
      p.curveTo(30.1993, 16.5738, 30.4386, 17.2609, 30.06, 18.36);
      p.curveTo(29.8346, 18.9017, 29.5904, 19.5183, 29.325, 20.22);
      p.curveTo(28.5714, 20.8985, 27.7668, 21.509, 26.775, 21.87);
      p.curveTo(25.9361, 21.7801, 25.353, 22.1167, 24.195, 21.495);
      p.curveTo(23.1023, 21.2632, 22.672, 21.473, 21.915, 21.465);
      p.lineTo(21.78, 21.765);
      p.lineTo(21.645, 21.375);
      p.lineTo(20.79, 21.075);
      p.lineTo(19.92, 22.035);
      p.lineTo(20.88, 20.385);
      p.lineTo(22.08, 20.385);
      p.lineTo(21.105, 19.77);
      p.lineTo(19.89, 20.685);
      p.lineTo(20.685, 19.41);
      p.lineTo(22.17, 19.305);
      p.lineTo(22.65, 19.815);
      p.lineTo(24.57, 19.77);
      p.lineTo(23.895, 19.725);
      p.lineTo(23.235, 18.42);
      p.lineTo(22.365, 19.245);
      p.lineTo(22.56, 17.97);
      p.lineTo(24.03, 18.045);
      p.lineTo(24.75, 19.23);
      p.lineTo(25.395, 19.29);
      p.lineTo(25.35, 18.15);
      p.lineTo(25.755, 18.18);
      p.lineTo(26.415, 18.645);
      p.lineTo(25.125, 14.325);
      p.curveTo(23.7046, 12.9176, 22.3363, 11.4741, 20.16, 10.59);
      p.lineTo(19.56, 10.575);
      p.lineTo(19.245, 10.8225);
      p.curveTo(18.4606, 9.60164, 16.6861, 9.72452, 15.105, 9.585);
      p.curveTo(12.0298, 8.91496, 8.94418, 8.37528, 5.985, 6.255);
      p.curveTo(2.07656, 6.13667, 0.181559, 5.74167, 0.3, 5.07);
      p.curveTo(0.255, 4.89, 0.255, 4.89, 0.0, 3.72);
      p.closePath();
      return p;
    }

    public static Shape buildShape1Path() {
      Path2D.Double p = new Path2D.Double();
      p.moveTo(2.5875, 0.0568832);
      p.curveTo(2.15389, -0.0282192, 1.71485, -0.0154831, 1.27125, 0.0793832);
      p.curveTo(0.696543, 0.337176, 0.415069, 0.888202, 0.0, 1.30563);
      p.curveTo(0.495392, 1.11567, 0.982449, 0.93821, 1.49625, 0.720633);
      p.curveTo(1.8321, 0.842106, 2.04647, 1.00547, 2.0925, 1.22688);
      p.curveTo(2.07492, 1.63188, 2.06155, 2.03688, 2.17125, 2.44188);
      p.curveTo(2.34942, 1.99984, 2.61531, 1.813, 2.9025, 1.68813);
      p.curveTo(3.49523, 1.95018, 4.09154, 2.20506, 4.5675, 2.70063);
      p.curveTo(4.83, 2.64234, 5.0925, 2.52597, 5.355, 2.57688);
      p.lineTo(5.32125, 2.56563);
      return p;
    }

    public static Shape buildShape2Path() {
      Path2D.Double p = new Path2D.Double();
      p.moveTo(0.0, 0.70875);
      p.curveTo(0.218484, 0.342085, 0.476544, 0.116764, 0.765, 0.0);
      p.lineTo(1.27125, 0.23625);
      return p;
    }

    public static Shape buildShape3Path() {
      Path2D.Double p = new Path2D.Double();
      p.moveTo(0.000161053, 6.12);
      p.curveTo(-0.0134967, 5.60253, 0.841503, 4.33503, 2.56516, 2.3175);
      p.curveTo(3.1463, 1.36564, 3.68054, 0.3532, 4.72516, 0.0);
      p.lineTo(4.72516, 0.0);
      return p;
    }

    public static Shape buildShape4Path() {
      Path2D.Double p = new Path2D.Double();
      p.moveTo(0.0, 0.000343136);
      p.curveTo(1.44728, -0.0188438, 1.85047, 0.770306, 2.4075, 1.44034);
      p.curveTo(4.55599, 2.42905, 5.22771, 3.43669, 5.04, 4.45534);
      p.curveTo(5.53825, 5.59681, 6.14416, 6.72482, 6.03, 7.94284);
      return p;
    }

    public static Shape buildShape5Path() {
      Path2D.Double p = new Path2D.Double();
      p.moveTo(0.0, 0.045);
      p.lineTo(0.97875, 0.0);
      p.lineTo(1.58625, 0.41625);
      p.lineTo(1.58625, 0.36);
      return p;
    }

    public static Shape buildShape6Path() {
      Path2D.Double p = new Path2D.Double();
      p.moveTo(0.0, 0.0);
      p.lineTo(0.585, 0.14625);
      p.lineTo(0.945, 0.48375);
      return p;
    }

    public static Shape buildShape7Path() {
      Path2D.Double p = new Path2D.Double();
      p.moveTo(0.0, 0.0);
      p.lineTo(0.88875, 0.2925);
      p.lineTo(1.74375, 0.63);
      return p;
    }

    public static Shape buildShape8Path() {
      Path2D.Double p = new Path2D.Double();
      p.moveTo(0.0, 0.0);
      p.curveTo(0.230347, 0.260219, 0.60946, 0.307915, 0.975, 0.375);
      p.curveTo(2.15355, 0.514366, 3.36306, 0.845753, 4.485, 0.57);
      p.curveTo(5.78504, 0.665939, 7.17872, 0.829979, 7.845, 0.465);
      return p;
    }

    public static Shape buildShape9Path() {
      Path2D.Double p = new Path2D.Double();
      p.moveTo(0.0, 0.585);
      p.curveTo(0.291721, 0.265326, 0.605792, 0.0797509, 0.9375, 0.0);
      p.curveTo(1.42001, 0.0632331, 1.46024, 0.256547, 1.6275, 0.4125);
      p.curveTo(1.47753, 0.668542, 1.22157, 0.797379, 0.975, 0.9375);
      p.curveTo(1.01112, 1.07561, 0.686123, 0.958112, 0.0, 0.585);
      p.closePath();
      return p;
    }

    public static Shape buildTranslatedPath(AffineTransform affineTransform, Shape s) {
      return affineTransform.createTransformedShape(s);
    }

    public static void draw(Graphics2D g2, Color c, Color eyeColor) {
      Path2D s0 = (Path2D) buildShape0Path(); //Make path2D from buildShape0Path()
      Path2D s1 = (Path2D) buildShape1Path();
      Path2D s2 = (Path2D) buildShape2Path();
      Path2D s3 = (Path2D) buildShape3Path();
      Path2D s4 = (Path2D) buildShape4Path();
      Path2D s5 = (Path2D) buildShape5Path();
      Path2D s6 = (Path2D) buildShape6Path();
      Path2D s7 = (Path2D) buildShape7Path();
      Path2D s8 = (Path2D) buildShape8Path();
      Path2D s9 = (Path2D) buildShape9Path();
      
      Shape shape0Local = at0.createTransformedShape(s0);
      Shape shape1Local = at1.createTransformedShape(s1);
      Shape shape2Local = at2.createTransformedShape(s2);
      Shape shape3Local = at3.createTransformedShape(s3);
      Shape shape4Local = at4.createTransformedShape(s4);
      Shape shape5Local = at5.createTransformedShape(s5);
      Shape shape6Local = at6.createTransformedShape(s6);
      Shape shape7Local = at7.createTransformedShape(s7);
      Shape shape8Local = at8.createTransformedShape(s8);
      Shape shape9Local = at9.createTransformedShape(s9);

      Area group = new Area(shape0Local); //Group shapes
      group.add(new Area(shape1Local));
      group.add(new Area(shape2Local));
      group.add(new Area(shape3Local));
      group.add(new Area(shape4Local));
      group.add(new Area(shape5Local));
      group.add(new Area(shape6Local));
      group.add(new Area(shape7Local));
      group.add(new Area(shape8Local));
      group.add(new Area(shape9Local));

      Rectangle2D groupRect = group.getBounds2D();
      double groupCX = groupRect.getCenterX();
      double groupCY = groupRect.getCenterX();

      double panelCenterX = DrawKomodo.W / 2.0 , panelCenterY = DrawKomodo.H / 2.0;


      AffineTransform centerPanel = new AffineTransform();
      centerPanel.translate(panelCenterX, panelCenterY);
      centerPanel.scale(8.0, 8.0);
      centerPanel.translate(-groupCX, -groupCY); //Align shape0 pivot at center

      AffineTransform oldTx = g2.getTransform(); //Save old Transform


      Shape shape0 = centerPanel.createTransformedShape(shape0Local);
      Shape shape1 = centerPanel.createTransformedShape(shape1Local);
      Shape shape2 = centerPanel.createTransformedShape(shape2Local);
      Shape shape3 = centerPanel.createTransformedShape(shape3Local);
      Shape shape4 = centerPanel.createTransformedShape(shape4Local);
      Shape shape5 = centerPanel.createTransformedShape(shape5Local);
      Shape shape6 = centerPanel.createTransformedShape(shape6Local);
      Shape shape7 = centerPanel.createTransformedShape(shape7Local);
      Shape shape8 = centerPanel.createTransformedShape(shape8Local);
      Shape shape9 = centerPanel.createTransformedShape(shape9Local);
      
      

      g2.setStroke(new BasicStroke(0.8f)); //Prevent stroke scaled follow shape
      g2.setColor(c);
      g2.fill(shape0);
      g2.setColor(Color.BLACK);
      g2.draw(shape0);
      g2.setColor(c);
      g2.fill(shape1);
      g2.setColor(Color.BLACK);
      g2.draw(shape1);
      g2.setColor(c);
      g2.fill(shape2);
      g2.setColor(Color.BLACK);
      g2.draw(shape2);
      g2.setColor(c);
      g2.fill(shape3);
      g2.setColor(Color.BLACK);
      g2.draw(shape3);
      g2.setColor(c);
      g2.fill(shape4);
      g2.setColor(Color.BLACK);
      g2.draw(shape4);
      g2.setColor(c);
      g2.fill(shape5);
      g2.setColor(Color.BLACK);
      g2.draw(shape5);
      g2.setColor(c);
      g2.fill(shape6);
      g2.setColor(Color.BLACK);
      g2.draw(shape6);
      g2.setColor(c);
      g2.fill(shape7);
      g2.setColor(Color.BLACK);
      g2.draw(shape7);
      g2.setColor(c);
      g2.fill(shape8);
      g2.setColor(Color.BLACK);
      g2.draw(shape8);
      g2.setColor(eyeColor);
      g2.fill(shape9);
      g2.setColor(Color.BLACK);
      g2.draw(shape9);
      g2.setTransform(oldTx);
  }
}

class Point {
    public double x;
     public double y;
        
    public Point(double x, double y)
    {
        this.x = x;
        this.y = y;
    }
}