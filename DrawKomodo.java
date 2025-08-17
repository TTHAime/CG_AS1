import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DrawKomodo extends JPanel implements Runnable {
    public static final int W = 600, H = 600;

    // ===== Named constants =====
    private static final double ZOOM_DELAY_SEC = 2.0;
    private static final double ZOOM_DURATION_SEC = 0.8;
    private static final double ZOOM_START = 1.0;
    private static final double ZOOM_END = 3.0;

    private static final float OUTLINE_STROKE_W = 0.8f;
    private static final float MOUNTAIN_OUTLINE_W = 1.2f;
    private static final float RIVER_WIDTH = 50f;
    private static final float RIVER_EDGE_W = 2f;

    private static final int SKY_TOP_R = 210, SKY_TOP_G = 230, SKY_TOP_B = 255;
    private static final int SKY_BOT_R = 170, SKY_BOT_G = 210, SKY_BOT_B = 250;

    private static final double POPUP_FADE_IN = 0.40;
    private static final float POPUP_FONT_SIZE = 28f;
    private static final double POPUP_OFFSET_X = 22;
    private static final double POPUP_OFFSET_Y = -28;

    // === Animation (threaded game loop) ===
    private volatile boolean running = true;
    private double elapsedSec = 0.0; // time since start (seconds)

    private Point2D eyeCenterPanel = new Point2D.Double(W * 0.58, H * 0.42);

    private Point2D zoomTarget = null;
    private double zoomOffsetX = 0, zoomOffsetY = 0;

    // ======== Original tiny-shape translations (kept as numbers only) ========
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

    // ===== Reused objects/states (not shape creation) =====
    private final BasicStroke outlineStroke = new BasicStroke(OUTLINE_STROKE_W);
    private final BasicStroke mountainStroke = new BasicStroke(MOUNTAIN_OUTLINE_W);
    private final BasicStroke riverEdgeStroke = new BasicStroke(RIVER_EDGE_W);
    private final GradientPaint skyPaint = new GradientPaint(
            0, 0, new Color(SKY_TOP_R, SKY_TOP_G, SKY_TOP_B),
            0, H, new Color(SKY_BOT_R, SKY_BOT_G, SKY_BOT_B));

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DrawKomodo panel = new DrawKomodo();
            JFrame f = new JFrame("Komodo • Polygon-only + Bezier (Eye-centered Zoom)");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(panel);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            new Thread(panel, "AnimationLoop").start();
        });
    }

    public DrawKomodo() {
        setPreferredSize(new Dimension(W, H));
        setBackground(Color.WHITE);
        setDoubleBuffered(true);
    }

    // === Zoom controls ===
    public void setZoomTarget(double x, double y) {
        this.zoomTarget = new Point2D.Double(x, y);
    }

    public void clearZoomTarget() {
        this.zoomTarget = null;
    }

    public void setZoomOffset(double dx, double dy) {
        this.zoomOffsetX = dx;
        this.zoomOffsetY = dy;
    }

    @Override
    public void run() {
        double lastTime = System.currentTimeMillis();
        while (running) {
            double now = System.currentTimeMillis();
            double dt = (now - lastTime) / 1000.0;
            lastTime = now;
            elapsedSec += dt;
            repaint();
            try {
                Thread.sleep(1);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // SKY 
        g2.setPaint(skyPaint);
        fillPoly(g2, rectPoly(0, 0, W, H)); 

        // Zoom timing
        double t = 0.0;
        if (elapsedSec >= ZOOM_DELAY_SEC) {
            t = clamp((elapsedSec - ZOOM_DELAY_SEC) / ZOOM_DURATION_SEC, 0, 1);
            t = smoothstep(t);
        }
        double zoom = ZOOM_START + (ZOOM_END - ZOOM_START) * t;

        Point2D target = (zoomTarget != null ? zoomTarget : eyeCenterPanel);
        double cx = (target != null ? target.getX() + zoomOffsetX : W / 2.0);
        double cy = (target != null ? target.getY() + zoomOffsetY : H / 2.0);

        double fx = (1.0 - t) * (W / 2.0) + t * cx;
        double fy = (1.0 - t) * (H / 2.0) + t * cy;

        AffineTransform view = new AffineTransform();
        view.translate(W / 2.0, H / 2.0);
        view.scale(zoom, zoom);
        view.translate(-fx, -fy);

        Graphics2D worldG = (Graphics2D) g2.create();
        worldG.transform(view);

        // WORLD: mountains + river + komodo 
        drawBackgroundWorld(worldG);
        drawKomodoPolygon(worldG);

        // popup '?'
        if (elapsedSec >= ZOOM_DELAY_SEC && eyeCenterPanel != null) {
            double appear = clamp((elapsedSec - ZOOM_DELAY_SEC) / POPUP_FADE_IN, 0, 1);
            drawQuestionMark(worldG, eyeCenterPanel, (float) appear);
        }

        worldG.dispose();
        g2.dispose();
    }

    // ---------- Background ----------
    private void drawBackgroundWorld(Graphics2D g2) {
        // Mountain range 1 (far) 
        List<Point2D.Double> m1 = new ArrayList<>();
        appendCubic(m1, p(-50, 420), p(80, 300), p(180, 360), p(260, 320), 160, true);
        appendCubic(m1, p(260, 320), p(320, 260), p(420, 360), p(520, 300), 160, false);
        appendCubic(m1, p(520, 300), p(560, 280), p(640, 360), p(700, 420), 160, false);
        m1.add(p(W, H));
        m1.add(p(0, H)); 
        g2.setColor(new Color(90, 105, 120));
        fillPoly(g2, m1);
        g2.setColor(new Color(60, 72, 84));
        g2.setStroke(mountainStroke);
        drawPoly(g2, m1);

        // Mountain range 2 (near)
        List<Point2D.Double> m2 = new ArrayList<>();
        appendCubic(m2, p(-80, 480), p(60, 380), p(160, 440), p(260, 400), 160, true);
        appendCubic(m2, p(260, 400), p(330, 350), p(420, 440), p(520, 380), 160, false);
        appendCubic(m2, p(520, 380), p(580, 360), p(660, 430), p(720, 480), 160, false);
        m2.add(p(W, H));
        m2.add(p(0, H));
        g2.setColor(new Color(120, 135, 150));
        fillPoly(g2, m2);
        g2.setColor(new Color(80, 92, 104));
        drawPoly(g2, m2);

        // River
        List<Point2D.Double> center = new ArrayList<>();
        appendCubic(center, p(-40, 520), p(120, 560), p(260, 520), p(360, 560), 220, true);
        appendCubic(center, p(360, 560), p(460, 600), p(540, 540), p(660, 560), 220, false);
        List<Point2D.Double> riverPoly = ribbonFromCenterline(center, RIVER_WIDTH);
        g2.setColor(new Color(90, 155, 210));
        fillPoly(g2, riverPoly);
        g2.setColor(new Color(200, 230, 255, 140));
        g2.setStroke(riverEdgeStroke);
        drawPoly(g2, riverPoly);
    }

    // ---------- Komodo 
    private void drawKomodoPolygon(Graphics2D g2) {
        List<List<Point2D.Double>> partsLocal = new ArrayList<>();
        partsLocal.add(shape0_local());
        partsLocal.add(shape1_local());
        partsLocal.add(shape2_local());
        partsLocal.add(shape3_local());
        partsLocal.add(shape4_local());
        partsLocal.add(shape5_local());
        partsLocal.add(shape6_local());
        partsLocal.add(shape7_local());
        partsLocal.add(shape8_local());
        partsLocal.add(shape9_local()); // eye

        double[][] txy = {
                { tx0, ty0 }, { tx1, ty1 }, { tx2, ty2 }, { tx3, ty3 }, { tx4, ty4 },
                { tx5, ty5 }, { tx6, ty6 }, { tx7, ty7 }, { tx8, ty8 }, { tx9, ty9 }
        };
        List<List<Point2D.Double>> partsOffset = new ArrayList<>();
        for (int i = 0; i < partsLocal.size(); i++) {
            partsOffset.add(translate(pointsCopy(partsLocal.get(i)), txy[i][0], txy[i][1]));
        }

        // Compute group center from bounds (to mimic original Area bounds)
        Bounds b = boundsOf(partsOffset);
        double groupCX = (b.minX + b.maxX) * 0.5;
        double groupCY = (b.minY + b.maxY) * 0.5;

        // Center on panel and scale 8x 
        double panelCenterX = W / 2.0, panelCenterY = H / 2.0;
        double scale = 8.0;

        List<List<Point2D.Double>> partsFinal = new ArrayList<>();
        for (List<Point2D.Double> pts : partsOffset) {
            List<Point2D.Double> out = new ArrayList<>(pts.size());
            for (Point2D.Double q : pts) {
                double x = panelCenterX + scale * (q.x - groupCX);
                double y = panelCenterY + scale * (q.y - groupCY);
                out.add(new Point2D.Double(x, y));
            }
            partsFinal.add(out);
        }

        g2.setStroke(outlineStroke);
        Color body = new Color(75, 83, 32);
        for (int i = 0; i <= 8; i++) {
            g2.setColor(body);
            fillPoly(g2, partsFinal.get(i));
            g2.setColor(Color.BLACK);
            drawPoly(g2, partsFinal.get(i));
        }
        // eye
        g2.setColor(new Color(218, 112, 214));
        fillPoly(g2, partsFinal.get(9));
        g2.setColor(Color.BLACK);
        drawPoly(g2, partsFinal.get(9));

        // cache eye center
        Bounds eyeB = boundsOf(Collections.singletonList(partsFinal.get(9)));
        this.eyeCenterPanel = new Point2D.Double((eyeB.minX + eyeB.maxX) * 0.5, (eyeB.minY + eyeB.maxY) * 0.5);
    }

    // ===== popup '?' =====
    private void drawQuestionMark(Graphics2D g2, Point2D head, float alpha) {
        float a = (float) smoothstep(alpha);
        Composite old = g2.getComposite();
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));

        double scale = 0.6 + 0.4 * a;
        double baseX = head.getX() + POPUP_OFFSET_X;
        double baseY = head.getY() + POPUP_OFFSET_Y;

        String q = "?";
        Font f = g2.getFont().deriveFont(Font.BOLD, (float) (POPUP_FONT_SIZE * scale));

        AffineTransform oldT = g2.getTransform();
        g2.translate(baseX, baseY);
        g2.scale(scale, scale);
        g2.setColor(Color.BLACK);
        g2.setFont(f);
        g2.drawString(q, 0, 0);
        g2.setTransform(oldT);
        g2.setComposite(old);
    }

    // ===== Helpers: geometry / polygons =====
    private static Point2D.Double p(double x, double y) {
        return new Point2D.Double(x, y);
    }

    private static List<Point2D.Double> rectPoly(double x, double y, double w, double h) {
        List<Point2D.Double> r = new ArrayList<>(4);
        r.add(p(x, y));
        r.add(p(x + w, y));
        r.add(p(x + w, y + h));
        r.add(p(x, y + h));
        return r;
    }

    private static void appendCubic(List<Point2D.Double> out, Point2D.Double P0, Point2D.Double P1,
            Point2D.Double P2, Point2D.Double P3, int samples, boolean includeStart) {
        if (includeStart)
            out.add(new Point2D.Double(P0.x, P0.y));
        for (int i = 1; i <= samples; i++) {
            double t = i / (double) samples, u = 1 - t;
            double x = u * u * u * P0.x + 3 * u * u * t * P1.x + 3 * u * t * t * P2.x + t * t * t * P3.x;
            double y = u * u * u * P0.y + 3 * u * u * t * P1.y + 3 * u * t * t * P2.y + t * t * t * P3.y;
            out.add(new Point2D.Double(x, y));
        }
    }

    private static List<Point2D.Double> ribbonFromCenterline(List<Point2D.Double> center, double width) {
        double hw = width * 0.5;
        int n = center.size();
        List<Point2D.Double> left = new ArrayList<>(n);
        List<Point2D.Double> right = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            Point2D.Double a = center.get(Math.max(0, i - 1));
            Point2D.Double b = center.get(Math.min(n - 1, i + 1));
            double dx = b.x - a.x, dy = b.y - a.y;
            double len = Math.hypot(dx, dy);
            if (len < 1e-6) {
                dx = 1;
                dy = 0;
                len = 1;
            }
            dx /= len;
            dy /= len; // tangent
            double nx = -dy, ny = dx; // normal
            Point2D.Double c = center.get(i);
            left.add(new Point2D.Double(c.x + nx * hw, c.y + ny * hw));
            right.add(new Point2D.Double(c.x - nx * hw, c.y - ny * hw));
        }
        Collections.reverse(right);
        left.addAll(right);
        return left;
    }

    private static void fillPoly(Graphics2D g2, List<Point2D.Double> poly) {
        int[] xs = new int[poly.size()];
        int[] ys = new int[poly.size()];
        for (int i = 0; i < poly.size(); i++) {
            xs[i] = (int) Math.round(poly.get(i).x);
            ys[i] = (int) Math.round(poly.get(i).y);
        }
        g2.fillPolygon(xs, ys, poly.size());
    }

    private static void drawPoly(Graphics2D g2, List<Point2D.Double> poly) {
        int[] xs = new int[poly.size()];
        int[] ys = new int[poly.size()];
        for (int i = 0; i < poly.size(); i++) {
            xs[i] = (int) Math.round(poly.get(i).x);
            ys[i] = (int) Math.round(poly.get(i).y);
        }
        g2.drawPolygon(xs, ys, poly.size());
    }

    private static List<Point2D.Double> translate(List<Point2D.Double> pts, double tx, double ty) {
        for (Point2D.Double q : pts) {
            q.x += tx;
            q.y += ty;
        }
        return pts;
    }

    private static List<Point2D.Double> pointsCopy(List<Point2D.Double> pts) {
        List<Point2D.Double> r = new ArrayList<>(pts.size());
        for (Point2D.Double q : pts)
            r.add(new Point2D.Double(q.x, q.y));
        return r;
    }

    private static class Bounds {
        double minX = 1e18, minY = 1e18, maxX = -1e18, maxY = -1e18;
    }

    private static Bounds boundsOf(List<List<Point2D.Double>> shapes) {
        Bounds b = new Bounds();
        for (List<Point2D.Double> s : shapes)
            for (Point2D.Double q : s) {
                if (q.x < b.minX)
                    b.minX = q.x;
                if (q.y < b.minY)
                    b.minY = q.y;
                if (q.x > b.maxX)
                    b.maxX = q.x;
                if (q.y > b.maxY)
                    b.maxY = q.y;
            }
        return b;
    }

    private static double clamp(double v, double a, double b) {
        return Math.max(a, Math.min(b, v));
    }

    private static double smoothstep(double t) {
        return t * t * (3 - 2 * t);
    }

    // ===== Polygon-builder for each tiny komodo piece (cubic → poly, no
    // Shape/Path2D) =====
    private static final int S0 = 60; // sampling density per cubic for bigger parts
    private static final int S1 = 24; // for tiny parts

    private static class PolyBuilder {
        final List<Point2D.Double> pts = new ArrayList<>();
        Point2D.Double cur = null;

        void moveTo(double x, double y) {
            cur = new Point2D.Double(x, y);
            pts.add(new Point2D.Double(x, y));
        }

        void lineTo(double x, double y) {
            cur = new Point2D.Double(x, y);
            pts.add(new Point2D.Double(x, y));
        }

        void curveTo(double cx1, double cy1, double cx2, double cy2, double x, double y, int samples) {
            Point2D.Double P0 = cur, P1 = p(cx1, cy1), P2 = p(cx2, cy2), P3 = p(x, y);
            for (int i = 1; i <= samples; i++) {
                double t = i / (double) samples, u = 1 - t;
                double px = u * u * u * P0.x + 3 * u * u * t * P1.x + 3 * u * t * t * P2.x + t * t * t * P3.x;
                double py = u * u * u * P0.y + 3 * u * u * t * P1.y + 3 * u * t * t * P2.y + t * t * t * P3.y;
                pts.add(new Point2D.Double(px, py));
            }
            cur = P3;
        }

        List<Point2D.Double> build() {
            return pts;
        }
    }

    private static List<Point2D.Double> shape0_local() {
        PolyBuilder b = new PolyBuilder();
        b.moveTo(0.0, 3.72);
        b.curveTo(0.775567, 2.39013, 2.01193, 2.41562, 3.32967, 2.10431, S0);
        b.curveTo(5.41843, 0.686844, 7.85357, 0.400372, 11.13, 0.600001, S0);
        b.curveTo(16.279, 1.51854, 21.4136, 1.79177, 26.5793, 2.4805, S0);
        b.curveTo(29.9754, 2.9333, 33.355, 4.6447, 36.791, 5.19674, S0);
        b.curveTo(40.3738, 5.77238, 44.6834, 5.49796, 47.67, 5.7, S0);
        b.curveTo(51.846, 6.43094, 55.6398, 7.73537, 59.34, 9.18, S0);
        b.curveTo(57.8136, 6.97295, 49.0761, 4.097, 44.01, 4.2, S0);
        b.curveTo(41.1868, 4.18066, 38.0766, 4.07784, 36.18, 2.91, S0);
        b.curveTo(35.188, 2.55782, 34.7279, 2.11818, 34.35, 1.2, S0);
        b.lineTo(34.05, 0.0);
        b.curveTo(34.6958, 1.31667, 35.605, 2.29231, 36.93, 2.73, S0);
        b.curveTo(39.0457, 3.00328, 41.1636, 3.35223, 43.26, 2.97, S0);
        b.curveTo(50.6797, 3.20404, 57.9927, 3.59648, 63.06, 7.32, S0);
        b.curveTo(63.9427, 9.70826, 64.8753, 12.0987, 63.03, 14.37, S0);
        b.curveTo(61.3136, 16.205, 58.5136, 16.2004, 55.08, 15.12, S0);
        b.curveTo(53.8267, 15.9289, 52.7998, 17.1097, 51.03, 17.07, S0);
        b.curveTo(49.8258, 20.3109, 48.1557, 21.9387, 46.08, 21.99, S0);
        b.curveTo(46.3498, 22.9417, 46.168, 23.0803, 46.17, 23.55, S0);
        b.curveTo(45.8606, 22.8965, 45.4286, 22.2686, 44.76, 21.69, S0);
        b.curveTo(44.1002, 21.649, 43.4861, 21.3178, 42.87, 21.0, S0);
        b.curveTo(42.6053, 20.9613, 42.3405, 21.2781, 42.0758, 21.2592, S0);
        b.curveTo(41.1609, 21.0593, 40.1814, 20.719, 38.91, 21.06, S0);
        b.curveTo(38.3333, 20.8138, 37.7651, 20.0538, 37.17, 20.91, S0);
        b.lineTo(36.93, 21.4313);
        b.curveTo(36.8868, 21.1411, 36.5032, 20.8973, 36.8512, 20.5538, S0);
        b.curveTo(37.4, 19.9152, 38.0239, 19.6779, 38.6287, 19.3388, S0);
        b.curveTo(39.4347, 18.7522, 40.2683, 18.6631, 41.0925, 18.405, S0);
        b.lineTo(42.5775, 18.9675);
        b.curveTo(43.2048, 18.8105, 43.815, 18.7725, 44.4225, 18.7538, S0);
        b.lineTo(44.9062, 17.8875);
        b.curveTo(42.333, 18.2162, 39.7528, 18.3344, 37.14, 17.475, S0);
        b.curveTo(34.8119, 17.1349, 32.5017, 16.6349, 30.255, 15.69, S0);
        b.curveTo(30.1993, 16.5738, 30.4386, 17.2609, 30.06, 18.36, S0);
        b.curveTo(29.8346, 18.9017, 29.5904, 19.5183, 29.325, 20.22, S0);
        b.curveTo(28.5714, 20.8985, 27.7668, 21.509, 26.775, 21.87, S0);
        b.curveTo(25.9361, 21.7801, 25.353, 22.1167, 24.195, 21.495, S0);
        b.curveTo(23.1023, 21.2632, 22.672, 21.473, 21.915, 21.465, S0);
        b.lineTo(21.78, 21.765);
        b.lineTo(21.645, 21.375);
        b.lineTo(20.79, 21.075);
        b.lineTo(19.92, 22.035);
        b.lineTo(20.88, 20.385);
        b.lineTo(22.08, 20.385);
        b.lineTo(21.105, 19.77);
        b.lineTo(19.89, 20.685);
        b.lineTo(20.685, 19.41);
        b.lineTo(22.17, 19.305);
        b.lineTo(22.65, 19.815);
        b.lineTo(24.57, 19.77);
        b.lineTo(23.895, 19.725);
        b.lineTo(23.235, 18.42);
        b.lineTo(22.365, 19.245);
        b.lineTo(22.56, 17.97);
        b.lineTo(24.03, 18.045);
        b.lineTo(24.75, 19.23);
        b.lineTo(25.395, 19.29);
        b.lineTo(25.35, 18.15);
        b.lineTo(25.755, 18.18);
        b.lineTo(26.415, 18.645);
        b.lineTo(25.125, 14.325);
        b.curveTo(23.7046, 12.9176, 22.3363, 11.4741, 20.16, 10.59, S0);
        b.lineTo(19.56, 10.575);
        b.lineTo(19.245, 10.8225);
        b.curveTo(18.4606, 9.60164, 16.6861, 9.72452, 15.105, 9.585, S0);
        b.curveTo(12.0298, 8.91496, 8.94418, 8.37528, 5.985, 6.255, S0);
        b.curveTo(2.07656, 6.13667, 0.181559, 5.74167, 0.3, 5.07, S0);
        b.lineTo(0.0, 3.72);
        return b.build();
    }

    private static List<Point2D.Double> shape1_local() {
        PolyBuilder b = new PolyBuilder();
        b.moveTo(2.5875, 0.0568832);
        b.curveTo(2.15389, -0.0282192, 1.71485, -0.0154831, 1.27125, 0.0793832, S1);
        b.curveTo(0.696543, 0.337176, 0.415069, 0.888202, 0.0, 1.30563, S1);
        b.curveTo(0.495392, 1.11567, 0.982449, 0.93821, 1.49625, 0.720633, S1);
        b.curveTo(1.8321, 0.842106, 2.04647, 1.00547, 2.0925, 1.22688, S1);
        b.curveTo(2.07492, 1.63188, 2.06155, 2.03688, 2.17125, 2.44188, S1);
        b.curveTo(2.34942, 1.99984, 2.61531, 1.813, 2.9025, 1.68813, S1);
        b.curveTo(3.49523, 1.95018, 4.09154, 2.20506, 4.5675, 2.70063, S1);
        b.curveTo(4.83, 2.64234, 5.0925, 2.52597, 5.355, 2.57688, S1);
        b.lineTo(5.32125, 2.56563);
        return b.build();
    }

    private static List<Point2D.Double> shape2_local() {
        PolyBuilder b = new PolyBuilder();
        b.moveTo(0.0, 0.70875);
        b.curveTo(0.218484, 0.342085, 0.476544, 0.116764, 0.765, 0.0, S1);
        b.lineTo(1.27125, 0.23625);
        return b.build();
    }

    private static List<Point2D.Double> shape3_local() {
        PolyBuilder b = new PolyBuilder();
        b.moveTo(0.000161053, 6.12);
        b.curveTo(-0.0134967, 5.60253, 0.841503, 4.33503, 2.56516, 2.3175, S1);
        b.curveTo(3.1463, 1.36564, 3.68054, 0.3532, 4.72516, 0.0, S1);
        return b.build();
    }

    private static List<Point2D.Double> shape4_local() {
        PolyBuilder b = new PolyBuilder();
        b.moveTo(0.0, 0.000343136);
        b.curveTo(1.44728, -0.0188438, 1.85047, 0.770306, 2.4075, 1.44034, S1);
        b.curveTo(4.55599, 2.42905, 5.22771, 3.43669, 5.04, 4.45534, S1);
        b.curveTo(5.53825, 5.59681, 6.14416, 6.72482, 6.03, 7.94284, S1);
        return b.build();
    }

    private static List<Point2D.Double> shape5_local() {
        PolyBuilder b = new PolyBuilder();
        b.moveTo(0.0, 0.045);
        b.lineTo(0.97875, 0.0);
        b.lineTo(1.58625, 0.41625);
        b.lineTo(1.58625, 0.36);
        return b.build();
    }

    private static List<Point2D.Double> shape6_local() {
        PolyBuilder b = new PolyBuilder();
        b.moveTo(0.0, 0.0);
        b.lineTo(0.585, 0.14625);
        b.lineTo(0.945, 0.48375);
        return b.build();
    }

    private static List<Point2D.Double> shape7_local() {
        PolyBuilder b = new PolyBuilder();
        b.moveTo(0.0, 0.0);
        b.lineTo(0.88875, 0.2925);
        b.lineTo(1.74375, 0.63);
        return b.build();
    }

    private static List<Point2D.Double> shape8_local() {
        PolyBuilder b = new PolyBuilder();
        b.moveTo(0.0, 0.0);
        b.curveTo(0.230347, 0.260219, 0.60946, 0.307915, 0.975, 0.375, S1);
        b.curveTo(2.15355, 0.514366, 3.36306, 0.845753, 4.485, 0.57, S1);
        b.curveTo(5.78504, 0.665939, 7.17872, 0.829979, 7.845, 0.465, S1);
        return b.build();
    }

    private static List<Point2D.Double> shape9_local() {
        PolyBuilder b = new PolyBuilder();
        b.moveTo(0.0, 0.585);
        b.curveTo(0.291721, 0.265326, 0.605792, 0.0797509, 0.9375, 0.0, S1);
        b.curveTo(1.42001, 0.0632331, 1.46024, 0.256547, 1.6275, 0.4125, S1);
        b.curveTo(1.47753, 0.668542, 1.22157, 0.797379, 0.975, 0.9375, S1);
        b.curveTo(1.01112, 1.07561, 0.686123, 0.958112, 0.0, 0.585, S1);
        return b.build();
    }
}
