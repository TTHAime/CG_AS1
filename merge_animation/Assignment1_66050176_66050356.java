package merge_animation;

import static java.lang.Math.*;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Queue;
import java.util.LinkedList;
import java.util.Collections;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.JComponent;

public class Assignment1_66050176_66050356 {

    // === Scene durations (milliseconds) ===
    private static final int DUR_FPS_MS = 10000; // 10 s
    private static final int DUR_BALL_MS = 6000; // 6 s
    // Last scene (Komodo) runs indefinitely, no timer

    // === Scene card names ===
    private static final String CARD_FPS = "scene_fps";
    private static final String CARD_BALL = "scene_ball";
    private static final String CARD_KOMODO = "scene_komodo";

    private final JFrame frame;
    private final JPanel cardRoot;
    private final CardLayout cards;

    // Panels for each scene
    private Component fpsPanel;
    private Component ballPanel;
    private Component komodoPanel;

    public Assignment1_66050176_66050356() {
        frame = new JFrame("Assignment1_66050176_66050356");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        cards = new CardLayout();
        cardRoot = new JPanel(cards);
        frame.setContentPane(cardRoot);

        // === Create each scene ===
        fpsPanel = createScenePanel(FPS_Coding.class); 
        ballPanel = createScenePanel(BallDrop.class);
        komodoPanel = createScenePanel(DrawKomodo.class); 

        // === Add scenes to CardLayout ===
        cardRoot.add(fpsPanel, CARD_FPS);
        cardRoot.add(ballPanel, CARD_BALL);
        cardRoot.add(komodoPanel, CARD_KOMODO);

        frame.pack(); 
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // === Start at scene 1 (FPS) ===
        showAndStart(CARD_FPS, fpsPanel);

        // === Timer → Scene 2 ===
        new javax.swing.Timer(DUR_FPS_MS, e -> {
            showAndStart(CARD_BALL, ballPanel);

            // ตั้ง Timer → Scene 3
            new javax.swing.Timer(DUR_BALL_MS, e2 -> {
                showAndStart(CARD_KOMODO, komodoPanel);

                // Last scene runs forever
            }) {
                {
                    setRepeats(false);
                }
            }.start();

        }) {
            {
                setRepeats(false);
            }
        }.start();
    }

    private Component createScenePanel(Class<?> panelClass) {
        try {
            Object obj = panelClass.getDeclaredConstructor().newInstance();
            if (!(obj instanceof Component)) {
                throw new IllegalArgumentException(panelClass.getName() + " is not a java.awt.Component");
            }
            return (Component) obj;
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException("Class " + panelClass.getName()
                    + " must have a no-arg constructor", e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create panel: " + panelClass.getName(), e);
        }
    }

    private void showAndStart(String cardName, Component panel) {
        cards.show(cardRoot, cardName);

        if (panel instanceof Runnable) {
            if (panel instanceof JComponent jc) {
                Thread t = (Thread) jc.getClientProperty("scene-thread");
                if (t == null || !t.isAlive()) {
                    t = new Thread((Runnable) panel, panel.getClass().getSimpleName() + "-Thread");
                    jc.putClientProperty("scene-thread", t);
                    t.start();
                }
            } else {
                new Thread((Runnable) panel, panel.getClass().getSimpleName() + "-Thread").start();
            }
        }

    }


    // === main ===
    public static void main(String[] args) {
        SwingUtilities.invokeLater(Assignment1_66050176_66050356::new);
    }
}

class FPS_Coding extends JPanel implements Runnable {

    /* ========================= Canvas & Loop ========================= */

    static final int W = 600, H = 600;
    private volatile boolean running = true;
    private double t = 0.0, dtSec = 0.0;

    /*
     * ========================= State Machine =========================
     */
    enum Scene {
        CODING, DIMMING, APPROACH, IMPACT, BLACKOUT
    }

    private Scene state = Scene.CODING;

    private double codeDuration = 5.2;
    private double dimDuration = 2.0;
    private double approachDur = 0.85;
    private double impactDur = 0.45;
    private double phaseProg = 0.0;

    /* ========================= Layout ========================= */
    private static Polygon polyRect(int x, int y, int w, int h) {
        Polygon p = new Polygon();
        p.addPoint(x, y);
        p.addPoint(x + w, y);
        p.addPoint(x + w, y + h);
        p.addPoint(x, y + h);
        return p;
    }

    // Geometry for screen/desk/keyboard 
    private final int screenX = 26, screenY = 22, screenW = W - 52, screenH = 240;
    private final Polygon screen = polyRect(screenX, screenY, screenW, screenH);
    private final int deskX = 20, deskY = 330, deskW = W - 40, deskH = 40;
    private final Polygon desk = polyRect(deskX, deskY, deskW, deskH);
    private final int keyboardX = 40, keyboardY = 380, keyboardW = W - 80, keyboardH = 170;
    private final Polygon keyboard = polyRect(keyboardX, keyboardY, keyboardW, keyboardH);

    /* ========================= Keyboard grid ========================= */
    private final int cols = 14, rows = 5;
    private int keyW, keyH, pad = 6;
    private KeyCell[][] keys;

    static class KeyCell {
        int x, y, w, h;
        boolean exists = false;
    }

    /* ========================= Typing pattern ========================= */
    private static final int LEFT = 0, RIGHT = 1, THUMB = 2;

    static class Tap {
        final int r, c, hand, finger;

        Tap(int r, int c, int hand, int finger) {
            this.r = r;
            this.c = c;
            this.hand = hand;
            this.finger = finger;
        }
    }

    private final List<Tap> pattern = new ArrayList<>();
    private int patternIndex = 0;
    private double tapTick = 0.0;
    private double tapInterval = 0.10;

    /* ========================= Hands ========================= */
    private final Hand leftHand = Hand.left();
    private final Hand rightHand = Hand.right();

    /* ========================= Scrolling code ========================= */
    static class CodeLine {
        String text;
        float x;
        int y;
        float speed;
        int width;

        CodeLine(String s, int y, float v) {
            text = s;
            this.y = y;
            speed = v;
        }
    }

    private final List<CodeLine> codeLines = new ArrayList<>();
    private final int codePadding = 14;
    private final Font codeFont = new Font("Consolas", Font.PLAIN, 14);

    /* ========================= Camera / Impact ========================= */
    private double camScale = 1.0, camDrop = 0.0;
    private double camRot = 0.0, rotVel = 0.0;
    private double shakeAmp = 0.0;
    private final Random rng = new Random(23);

    /* ========================= Constructor ========================= */
    public FPS_Coding() {
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(245, 248, 255));
        setDoubleBuffered(true);

        buildKeyboard();
        buildPattern();
        initCodeLines();

        Point f = getKeyCenter(2, 3), j = getKeyCenter(2, 9);
        leftHand.setAnchor(f.x - 20, f.y + 22);
        rightHand.setAnchor(j.x + 20, j.y + 22);

        leftHand.setFingerOffset(0, 50, -30);
        leftHand.setFingerOffset(1, 25, -2);
        leftHand.setFingerOffset(2, 20, -3);
        leftHand.setFingerOffset(3, 15, 0);
        leftHand.setFingerOffset(4, 10, 1);

        rightHand.setFingerOffset(0, -50, -30);
        rightHand.setFingerOffset(1, -25, -2);
        rightHand.setFingerOffset(2, -20, -3);
        rightHand.setFingerOffset(3, -15, 0);
        rightHand.setFingerOffset(4, -10, 1);
    }

    /* ========================= Game loop ========================= */
    @Override
    public void run() {
        double last = System.currentTimeMillis();
        while (running) {
            double now = System.currentTimeMillis();
            dtSec = (now - last) / 1000.0;
            last = now;

            update(dtSec);
            repaint();
            try {
                Thread.sleep(1);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /* ========================= Update ========================= */
    private void update(double dt) {
        t += dt;

        if (state == Scene.CODING && t >= codeDuration) {
            state = Scene.DIMMING;
            phaseProg = 0;
        } else if (state == Scene.DIMMING && phaseProg >= 1) {
            state = Scene.APPROACH;
            phaseProg = 0;
        } else if (state == Scene.APPROACH && phaseProg >= 1) {
            state = Scene.IMPACT;
            phaseProg = 0;
            onImpactStart();
        } else if (state == Scene.IMPACT && phaseProg >= 1) {
            state = Scene.BLACKOUT;
            phaseProg = 0;
        }

        if (state == Scene.DIMMING)
            phaseProg = min(1.0, phaseProg + dt / dimDuration);
        if (state == Scene.APPROACH)
            phaseProg = min(1.0, phaseProg + dt / approachDur);
        if (state == Scene.IMPACT)
            phaseProg = min(1.0, phaseProg + dt / impactDur);

        if (state == Scene.CODING) {
            camScale = 1.0;
            camDrop = 0.0;
            camRot = 0.0;
            rotVel = 0.0;
        } else if (state == Scene.DIMMING) {
            double k = 1.0 - cos(PI * phaseProg);
            camScale = 1.0 + 0.05 * k;
            camDrop = 5.0 * k;
            leftHand.extraDrop = (float) (8 * k);
            rightHand.extraDrop = (float) (10 * k);
            camRot = 0.0;
            rotVel = 0.0;
        } else if (state == Scene.APPROACH) {
            double k = 1.0 - cos(PI * phaseProg);
            camScale = 1.0 + 0.60 * k;
            camDrop = 34 * k;
            leftHand.extraDrop = (float) (28 * k);
            rightHand.extraDrop = (float) (32 * k);
            leftHand.extraSide = (float) (-14 * k);
            rightHand.extraSide = (float) (14 * k);
            camRot *= 0.9;
        } else if (state == Scene.IMPACT) {
            shakeAmp = 1.0 * exp(-3.5 * phaseProg);
            camScale = 1.60;
            camDrop = 36;
            double c = 6.0, k = 90.0;
            rotVel += (-c * rotVel - k * camRot) * dt;
            camRot += rotVel * dt;
            leftHand.extraDrop = 38;
            rightHand.extraDrop = 42;
        } else {
            camScale = 1.60;
            camDrop = 36;
            camRot *= 0.9;
        }

        double speedMul = 0.0;
        if (state == Scene.CODING) {
            double remain = max(0.0, codeDuration - t);
            speedMul = (remain < 0.5) ? (0.3 + 1.4 * remain) : 1.0;
        } else if (state == Scene.DIMMING) {
            speedMul = 0.8 * (1.0 - 0.6 * phaseProg);
        }

        tapTick += dt * speedMul;
        if (!pattern.isEmpty() && tapTick >= tapInterval) {
            tapTick = 0;
            Tap tap = pattern.get(patternIndex);
            patternIndex = (patternIndex + 1) % pattern.size();
            Point key = getKeyCenter(tap.r, tap.c);
            if (tap.hand == LEFT)
                leftHand.tapFinger(tap.finger, key);
            else if (tap.hand == RIGHT)
                rightHand.tapFinger(tap.finger, key);
            else
                rightHand.tapThumb(getKeyCenter(4, 6));
        }

        leftHand.update(dt, t);
        rightHand.update(dt, t);

        float contentLeft = screenX + codePadding;
        float contentRight = screenX + screenW - codePadding;
        for (CodeLine cl : codeLines) {
            cl.x -= cl.speed * dt * (float) speedMul;
            if (cl.x + cl.width < contentLeft)
                cl.x = contentRight + 40 + (float) (Math.random() * 80);
        }
    }

    private void onImpactStart() {
        double sign = rng.nextBoolean() ? 1 : -1;
        camRot = toRadians(1.0 * sign);
        rotVel = toRadians((220 + rng.nextInt(80)) * sign);
    }

    /* ========================= Keyboard build ========================= */
    private void buildKeyboard() {
        keyW = (keyboardW - pad * (cols + 1)) / cols;
        keyH = (keyboardH - pad * (rows + 1)) / rows;
        keys = new KeyCell[rows][cols];

        for (int r = 0; r < rows; r++) {
            double offset = stagger(r) * (keyW + pad);
            for (int c = 0; c < cols; c++) {
                int x = keyboardX + pad + (int) (c * (keyW + pad) + offset);
                int y = keyboardY + pad + r * (keyH + pad);
                boolean in = (x + keyW <= keyboardX + keyboardW - pad);

                keys[r][c] = new KeyCell();
                keys[r][c].x = x;
                keys[r][c].y = y;
                keys[r][c].w = keyW;
                keys[r][c].h = keyH;
                keys[r][c].exists = in;
            }
        }
    }

    private double stagger(int r) {
        return switch (r) {
            case 0 -> 0.00;
            case 1 -> 0.40;
            case 2 -> 0.55;
            case 3 -> 0.80;
            case 4 -> 0.20;
            default -> 0.00;
        };
    }

    private Point getKeyCenter(int r, int c) {
        if (r < 0 || r >= rows || c < 0 || c >= cols || !keys[r][c].exists)
            return new Point(keyboardX, keyboardY);
        KeyCell k = keys[r][c];
        return new Point(k.x + k.w / 2, k.y + k.h / 2);
    }

    private void buildPattern() {
        addTap(1, 6, RIGHT, 0);
        addTap(1, 7, RIGHT, 1);
        addTap(1, 7, RIGHT, 1);
        addTap(1, 8, RIGHT, 2);
        addTap(4, 6, THUMB, 0);
        addTap(2, 9, RIGHT, 0);
        addTap(2, 10, RIGHT, 2);
        addTap(1, 6, RIGHT, 0);
        addTap(2, 8, RIGHT, 0);
        addTap(2, 3, LEFT, 3);
        addTap(2, 2, LEFT, 2);
        addTap(2, 1, LEFT, 1);
        addTap(2, 2, LEFT, 2);
        addTap(4, 6, THUMB, 0);
    }

    private void addTap(int r, int c, int hand, int finger) {
        if (r >= 0 && r < rows && c >= 0 && c < cols && keys[r][c].exists)
            pattern.add(new Tap(r, c, hand, finger));
    }

    private void initCodeLines() {
        String[] lines = {
                "public static void main(String[] args) {",
                "Hello World",
                "}",
                "Computer Graphic",
                "ah hehe",
                "CG",
                "B\u00E9zier",
                "Midpoint",
                "Animation"
        };

        int top = screenY + codePadding + 8;
        int bottom = screenY + screenH - codePadding - 8;
        int gap = 22, y = top;

        for (int i = 0; i < lines.length && y < bottom; i++, y += gap) {
            codeLines.add(new CodeLine(lines[i], y, 70 + (float) (Math.random() * 60)));
        }
    }

    /* ========================= Painting ========================= */
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double pivotX = W / 2.0;
        double pivotY = deskY + deskH / 2.0;

        g2.translate(pivotX, pivotY);
        g2.scale(camScale, camScale);
        g2.rotate(camRot);
        g2.translate(-pivotX, -pivotY + camDrop);

        if (state == Scene.IMPACT && shakeAmp > 0) {
            double sx = (rng.nextDouble() * 2 - 1) * 6 * shakeAmp;
            double sy = (rng.nextDouble() * 2 - 1) * 4 * shakeAmp;
            double rot = (rng.nextDouble() * 2 - 1) * Math.toRadians(0.7 * shakeAmp);
            g2.translate(sx, sy);
            g2.rotate(rot, pivotX, pivotY);
        }

        // background
        g2.setColor(new Color(230, 238, 255));
        fillRectPoly(g2, 0, 0, W, H);

        // desk
        g2.setColor(new Color(194, 180, 160));
        fillRoundRectPoly(g2, deskX, deskY, deskW, deskH, 14, 8);

        drawMonitor(g2);

        drawKeyboard(g2);

        leftHand.draw(g2, true);
        rightHand.draw(g2, false);

        g2.dispose();

        // Overlays (dimming/blackout)
        Graphics2D go = (Graphics2D) g.create();
        if (state == Scene.DIMMING) {
            float a = (float) (0.10 + 0.40 * phaseProg);
            go.setColor(new Color(0, 0, 0, (int) (255 * a)));
            fillRectPoly(go, 0, 0, getWidth(), getHeight());
        } else if (state == Scene.APPROACH) {
            float a = (float) min(1.0, 0.65 + 0.25 * phaseProg);
            go.setColor(new Color(0, 0, 0, (int) (255 * a)));
            fillRectPoly(go, 0, 0, getWidth(), getHeight());
        } else if (state == Scene.IMPACT) {
            float a = (float) min(1.0, 0.85 + 0.15 * phaseProg);
            go.setColor(new Color(0, 0, 0, (int) (255 * a)));
            fillRectPoly(go, 0, 0, getWidth(), getHeight());
        } else if (state == Scene.BLACKOUT) {
            go.setColor(Color.BLACK);
            fillRectPoly(go, 0, 0, getWidth(), getHeight());
        }
        go.dispose();
    }

    private void drawMonitor(Graphics2D g2) {
        int mx = screenX + screenW / 2;
        int top = screenY + screenH;
        int gap = 10, postW = 28, postH = Math.max(18, (deskY - (top + gap)) - 14);

        // Stand / base
        g2.setColor(new Color(85, 90, 110));
        fillRoundRectPoly(g2, mx - postW / 2, top + gap, postW, postH, 10, 8);
        int baseW = 160, baseH = 14, baseY = deskY - baseH - 4;
        fillRoundRectPoly(g2, mx - baseW / 2, baseY, baseW, baseH, 10, 8);

        // Base shadow
        g2.setColor(new Color(0, 0, 0, 40));
        fillEllipsePoly(g2, mx, deskY - 0, (int) (baseW * 0.45), 6, 36);

        // Screen outer frame
        g2.setColor(new Color(58, 62, 78));
        fillRoundRectPoly(g2, screenX - 6, screenY - 6, screenW + 12, screenH + 12, 16, 12);

        // Screen brightness dimming per phase
        double dim = switch (state) {
            case CODING -> 1.0;
            case DIMMING -> max(0.55, 1.0 - 0.35 * phaseProg);
            case APPROACH -> max(0.25, 1.0 - 0.70 * phaseProg);
            case IMPACT, BLACKOUT -> 0.0;
        };

        float glow = (float) ((0.86 + 0.14 * sin(2 * PI * 0.7 * t)) * dim);
        Color screenBg = new Color(24, 36, 70);
        Color screenGlow = new Color(90, 190, 255, (int) (70 * glow));

        // Screen fill
        g2.setColor(screenBg);
        fillRoundRectPoly(g2, screenX, screenY, screenW, screenH, 12, 12);

        // Inner glow
        g2.setColor(screenGlow);
        fillRoundRectPoly(g2, screenX + 6, screenY + 6, screenW - 12, screenH - 12, 10, 12);

        boolean showCode = (state == Scene.CODING) || (state == Scene.DIMMING);
        if (showCode) {
            Shape oldClip = g2.getClip();
            Polygon contentClip = roundRectPolygon(screenX + codePadding, screenY + codePadding,
                    screenW - 2 * codePadding, screenH - 2 * codePadding, 8, 10);
            g2.setClip(contentClip);

            g2.setFont(codeFont);
            g2.setColor(new Color(180, 230, 255));
            FontMetrics fm = g2.getFontMetrics();
            float contentRight = screenX + screenW - codePadding;

            for (CodeLine cl : codeLines) {
                if (cl.width == 0) {
                    cl.width = fm.stringWidth(cl.text);
                    cl.x = contentRight + (float) (Math.random() * 120);
                }
                g2.drawString(cl.text, cl.x, cl.y);
            }
            g2.setClip(oldClip);
        }
    }

    private void drawKeyboard(Graphics2D g2) {
        // Base
        g2.setColor(new Color(72, 78, 95));
        fillRoundRectPoly(g2, keyboardX, keyboardY, keyboardW, keyboardH, 18, 12);

        // Keys
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                KeyCell k = keys[r][c];
                if (!k.exists)
                    continue;

                g2.setColor(new Color(200, 208, 224));
                fillRoundRectPoly(g2, k.x, k.y, k.w, k.h, 10, 8);

                g2.setColor(new Color(255, 255, 255, 60));
                fillRoundRectPoly(g2, k.x + 2, k.y + 2, k.w - 4, 4, 6, 6);
            }
        }
    }

    /* ========================= Hand/Fingers ========================= */
    static class Hand {
        final Point2D.Float anchor = new Point2D.Float();
        final Finger[] fs;
        final float palmW = 74, palmH = 56;
        final Color skin1 = new Color(255, 220, 190), skin2 = new Color(240, 200, 170);
        double phase;
        float extraDrop = 0, extraSide = 0;

        private Hand(Finger[] fs, double ph) {
            this.fs = fs;
            this.phase = ph;
        }

        static Hand left() {
            return new Hand(new Finger[] {
                    Finger.thumb(-18, 28), Finger.index(-10, -8), Finger.middle(-22, -10),
                    Finger.ring(-34, -8), Finger.pinky(-46, -4)
            }, 0.0);
        }

        static Hand right() {
            return new Hand(new Finger[] {
                    Finger.thumb(18, 28), Finger.index(10, -8), Finger.middle(22, -10),
                    Finger.ring(34, -8), Finger.pinky(46, -4)
            }, Math.PI / 3);
        }

        void setAnchor(float x, float y) {
            anchor.setLocation(x, y);
        }

        void setAnchor(double x, double y) {
            setAnchor((float) x, (float) y);
        }

        void setFingerOffset(int idx, float dx, float dy) {
            if (idx < 0 || idx >= fs.length)
                return;
            fs[idx].offsetX = dx;
            fs[idx].offsetY = dy;
        }

        void tapFinger(int fingerIndex, Point key) {
            int idx = Math.max(1, Math.min(4, fingerIndex + 1));
            fs[idx].pressAt(key.x, key.y);
        }

        void tapThumb(Point key) {
            fs[0].pressAt(key.x, key.y + 8);
        }

        void update(double dt, double tg) {
            for (Finger f : fs)
                f.update(dt);
        }

        void draw(Graphics2D g2, boolean isLeft) {
            Graphics2D gg = (Graphics2D) g2.create();

            // เงามือ (วงรี)
            gg.setColor(new Color(0, 0, 0, 40));
            fillEllipsePoly(gg,
                    (int) (anchor.x + extraSide),
                    (int) (anchor.y + 26 + extraDrop),
                    (int) (palmW * 0.42),
                    9,
                    36);

            Paint grad = new GradientPaint(anchor.x + extraSide, anchor.y + extraDrop - palmH / 2, skin1,
                    anchor.x + extraSide, anchor.y + extraDrop + palmH / 2, skin2);
            gg.setPaint(grad);
            fillRoundRectPoly(gg,
                    (int) (anchor.x - palmW / 2 + extraSide),
                    (int) (anchor.y - palmH / 2 + extraDrop),
                    (int) palmW, (int) palmH, 22, 10);

            int[] order = { 4, 3, 2, 1, 0 };
            for (int i : order)
                fs[i].draw(gg, new Point2D.Float(anchor.x + extraSide, anchor.y + extraDrop), isLeft);

            // Sleeve
            gg.setColor(new Color(35, 95, 165));
            int bw = 80, bh = 90;
            int bx = (int) (anchor.x + extraSide - bw / 2);
            int by = (int) (anchor.y + extraDrop + palmH / 2 - 6);
            fillRectPoly(gg, bx, by, bw, bh);

            gg.dispose();
        }
    }

    static class Finger {
        final float baseX, baseY, len, thick;
        final boolean thumb;

        float press = 0f;
        float tx = Float.NaN, ty = Float.NaN;
        float offsetX = 0f, offsetY = 0f;

        private Finger(float bx, float by, float l, float th, boolean t) {
            baseX = bx;
            baseY = by;
            len = l;
            thick = th;
            thumb = t;
        }

        static Finger thumb(float x, float y) {
            return new Finger(x, y, 34, 14, true);
        }

        static Finger index(float x, float y) {
            return new Finger(x, y, 46, 12, false);
        }

        static Finger middle(float x, float y) {
            return new Finger(x, y, 50, 12, false);
        }

        static Finger ring(float x, float y) {
            return new Finger(x, y, 46, 12, false);
        }

        static Finger pinky(float x, float y) {
            return new Finger(x, y, 40, 11, false);
        }

        void pressAt(float x, float y) {
            tx = x;
            ty = y;
            press = 1f;
        }

        void pressAt(double x, double y) {
            pressAt((float) x, (float) y);
        }

        void update(double dt) {
            press = max(0f, (float) (press - dt * 3.5));
        }

        void draw(Graphics2D g2, Point2D.Float anchor, boolean isLeft) {
            Graphics2D gg = (Graphics2D) g2.create();

            float jx = anchor.x + baseX + offsetX;
            float jy = anchor.y + baseY + offsetY;

            float dx = 0, dy = 0;
            if (!Float.isNaN(tx)) {
                dx = (tx - jx) * press * 0.28f;
                dy = (ty - jy) * press * 0.28f + 6f * press;
            }

            float curl = thumb ? 8f : 12f;
            float tipX = jx + dx;
            float tipY = jy - (len - curl) + dy;

            // Finger as a capsule
            gg.setColor(new Color(255, 220, 190));
            fillCapsulePoly(gg, jx, jy, tipX, tipY, thick / 1.8f, 16);

            // Nail
            float nx = tipX - (isLeft ? 6 : 8), ny = tipY - 4;
            gg.setColor(new Color(255, 245, 235, 220));
            fillRoundRectPoly(gg, Math.round(nx), Math.round(ny), 14, 8, 4, 6);

            // Nail highlight
            gg.setColor(new Color(255, 255, 255, 120));
            fillRoundRectPoly(gg, Math.round(nx + 2), Math.round(ny + 1), 10, 3, 3, 5);

            gg.dispose();
        }
    }

    /* ========================= Main ========================= */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FPS_Coding panel = new FPS_Coding();
            JFrame f = new JFrame("Code → Dimming → Impact (Spin) → Blackout");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(panel);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            new Thread(panel).start();
        });
    }

    /*
     * ======================================================================
     * ========================= POLY HELPERS ONLY ===========================
     * ====================================================================
     */


    private static void fillRectPoly(Graphics2D g2, int x, int y, int w, int h) {
        int[] xs = { x, x + w, x + w, x };
        int[] ys = { y, y, y + h, y + h };
        g2.fillPolygon(xs, ys, 4);
    }

  
    private static void fillEllipsePoly(Graphics2D g2, int cx, int cy, int rx, int ry, int seg) {
        seg = max(8, seg);
        int[] xs = new int[seg];
        int[] ys = new int[seg];
        for (int i = 0; i < seg; i++) {
            double ang = (2 * Math.PI * i) / seg;
            xs[i] = (int) Math.round(cx + rx * Math.cos(ang));
            ys[i] = (int) Math.round(cy + ry * Math.sin(ang));
        }
        g2.fillPolygon(xs, ys, seg);
    }

 
    private static Polygon roundRectPolygon(int x, int y, int w, int h, int r, int segPerQuarter) {
        int rr = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        int seg = Math.max(3, segPerQuarter);
        List<Point> pts = new ArrayList<>(seg * 4 + 4);

        // centers
        double cx0 = x + rr, cy0 = y + rr; // TL
        double cx1 = x + w - rr, cy1 = y + rr; // TR
        double cx2 = x + w - rr, cy2 = y + h - rr; // BR
        double cx3 = x + rr, cy3 = y + h - rr; // BL

        // TL: 180→270
        for (int i = 0; i <= seg; i++) {
            double a = Math.PI + (Math.PI / 2.0) * (i / (double) seg);
            pts.add(new Point((int) Math.round(cx0 + rr * Math.cos(a)),
                    (int) Math.round(cy0 + rr * Math.sin(a))));
        }
        // TR: 270→360
        for (int i = 0; i <= seg; i++) {
            double a = 1.5 * Math.PI + (Math.PI / 2.0) * (i / (double) seg);
            pts.add(new Point((int) Math.round(cx1 + rr * Math.cos(a)),
                    (int) Math.round(cy1 + rr * Math.sin(a))));
        }
        // BR: 0→90
        for (int i = 0; i <= seg; i++) {
            double a = 0 + (Math.PI / 2.0) * (i / (double) seg);
            pts.add(new Point((int) Math.round(cx2 + rr * Math.cos(a)),
                    (int) Math.round(cy2 + rr * Math.sin(a))));
        }
        // BL: 90→180
        for (int i = 0; i <= seg; i++) {
            double a = 0.5 * Math.PI + (Math.PI / 2.0) * (i / (double) seg);
            pts.add(new Point((int) Math.round(cx3 + rr * Math.cos(a)),
                    (int) Math.round(cy3 + rr * Math.sin(a))));
        }

        Polygon p = new Polygon();
        for (Point pt : pts)
            p.addPoint(pt.x, pt.y);
        return p;
    }

  
    private static void fillRoundRectPoly(Graphics2D g2, int x, int y, int w, int h, int r, int segPerQuarter) {
        g2.fillPolygon(roundRectPolygon(x, y, w, h, r, segPerQuarter));
    }

   
    private static void fillCapsulePoly(Graphics2D g2, float x1, float y1, float x2, float y2, float r, int seg) {
        double dx = x2 - x1, dy = y2 - y1;
        double L = Math.hypot(dx, dy);
        if (L < 1e-3) {

            fillEllipsePoly(g2, Math.round(x1), Math.round(y1), Math.round(r), Math.round(r), Math.max(16, seg * 2));
            return;
        }

        double ux = dx / L, uy = dy / L;
        double px = -uy, py = ux; // ตั้งฉาก

    
        double cxA = x2, cyA = y2; // ปลาย
        double cxB = x1, cyB = y1; // โคน

        int arcSeg = Math.max(6, seg);
        List<Point> pts = new ArrayList<>(arcSeg * 2 + 8);

   
        double phi = Math.atan2(dy, dx);
        for (int i = 0; i <= arcSeg; i++) {
            double a = (phi - Math.PI / 2) + (Math.PI * i / arcSeg);
            double cx = cxA + r * Math.cos(a);
            double cy = cyA + r * Math.sin(a);
            pts.add(new Point((int) Math.round(cx), (int) Math.round(cy)));
        }

        double ax = cxA + r * Math.cos(phi + Math.PI / 2);
        double ay = cyA + r * Math.sin(phi + Math.PI / 2);
        double bx = cxB + r * Math.cos(phi + Math.PI / 2);
        double by = cyB + r * Math.sin(phi + Math.PI / 2);
        pts.add(new Point((int) Math.round(bx), (int) Math.round(by)));

    
        for (int i = 0; i <= arcSeg; i++) {
            double a = (phi + Math.PI / 2) + (Math.PI * i / arcSeg);
            double cx = cxB + r * Math.cos(a);
            double cy = cyB + r * Math.sin(a);
            pts.add(new Point((int) Math.round(cx), (int) Math.round(cy)));
        }

     
        double ax2 = cxA + r * Math.cos(phi - Math.PI / 2);
        double ay2 = cyA + r * Math.sin(phi - Math.PI / 2);
        double bx2 = cxB + r * Math.cos(phi - Math.PI / 2);
        double by2 = cyB + r * Math.sin(phi - Math.PI / 2);
        pts.add(new Point((int) Math.round(ax2), (int) Math.round(ay2)));

        Polygon p = new Polygon();
        for (Point pt : pts)
            p.addPoint(pt.x, pt.y);
        g2.fillPolygon(p);
    }
}

class BallDrop extends JPanel implements Runnable {

    private static final int W = 600, H = 600;

    // Ball setting
    private final int ballRadius = 70;
    private final int innerBallRadius = Math.round(ballRadius / 3.5f);
    private double x = ballRadius + 10;
    private double y = ballRadius;

    // Bounce
    private boolean onGround = false;

    // Physics Setting
    private double vx = 150;
    private double vy = 0;
    private final double gravity = 2000; // Gravity (px/s^2)
    private final int groundY = H - 2;
    private boolean isStopped = false;
    private boolean wasStopped = false;
    private final double reboundForce = 0.7;
    private final double mu = 0.07; // Just Appoximate, use to calculate friction of ball when ball touch floor.

    // Flash setting
    private boolean flashing = false;
    private double flashStartTime = 0;
    private double flashDuration = 2;

    // finish flashing
    private boolean isComplete = false;

    // Color
    private final Color outlineColor = new Color(30, 30, 30);
    private final Color red = Color.RED;
    private final Color white = Color.WHITE;
    private final Color band = new Color(20, 20, 20);
    private Color[] flashColor;

    public static void main(String[] args) {
        createGUI();
    }

    public static void createGUI() {
        BallDrop panel = new BallDrop();
        JFrame f = new JFrame();
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setContentPane(panel);
        f.pack();
        f.setLocationRelativeTo(null);
        f.setVisible(true);

        (new Thread(panel)).start();
    }

    public BallDrop() {
        this.setPreferredSize(new Dimension(W, H));
        this.setBackground(Color.WHITE);
    }

    @Override
    public void run() {
        double lastTime = System.currentTimeMillis();

        double currentTime, elapsedTime;

        while (!isComplete) {
            currentTime = System.currentTimeMillis();
            elapsedTime = (currentTime - lastTime) / 1000.0;
            lastTime = currentTime;

            // Physics Apply method
            updatePhysics(elapsedTime, currentTime);

            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(white);
        g.fillRect(0, 0, W, H);

        BufferedImage buf = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics gBuf = buf.createGraphics();
        // Draw gound line
        gBuf.setColor(Color.BLACK);
        bresenhamLine(gBuf, 0, groundY, W - 1, groundY);

        // draw Ball
        int ballCenterX = (int) Math.round(x);
        int ballCenterY = (int) Math.round(y);

        gBuf.setColor(outlineColor);
        midpointCircle(gBuf, ballCenterX, ballCenterY, ballRadius);

        // Band in Ball
        gBuf.setColor(band);
        bresenhamLine(gBuf, ballCenterX - ballRadius, ballCenterY, ballCenterX + ballRadius, ballCenterY);
        bresenhamLine(gBuf, ballCenterX - ballRadius, ballCenterY - 1, ballCenterX + ballRadius, ballCenterY - 1);
        bresenhamLine(gBuf, ballCenterX - ballRadius, ballCenterY + 1, ballCenterX + ballRadius, ballCenterY + 1);

        // Inner circle
        gBuf.setColor(outlineColor);
        midpointCircle(gBuf, ballCenterX, ballCenterY, innerBallRadius);

        // Floodfill
        // System.out.println("CENTER : "+buf.getRGB(ballCenterX, ballCenterY));
        floodFill(buf, ballCenterX, ballCenterY - (ballRadius / 2),
                new Color(buf.getRGB(ballCenterX, ballCenterY - (ballRadius / 2)), true), red); // Top half ball
        floodFill(buf, ballCenterX, ballCenterY + (ballRadius / 2),
                new Color(buf.getRGB(ballCenterX, ballCenterY + (ballRadius / 2)), true), white); // Bottom half ball
        floodFill(buf, ballCenterX, ballCenterY, new Color(buf.getRGB(ballCenterX, ballCenterY)), white); // Inner
                                                                                                          // Circle

        g.drawImage(buf, 0, 0, null);

        // Start to flash.
        if (!wasStopped && isStopped && !flashing) {
            flashing = true;
            flashStartTime = System.currentTimeMillis() / 1000.0;
        }
        wasStopped = isStopped; // wasStopped use for prevent above if run more than 1 times

        if (flashing) {
            // System.out.println("Flashing");
            double current = System.currentTimeMillis() / 1000.0;
            double t = (current - flashStartTime) / flashDuration;
            if (t >= 1.0f) {
                t = 1.0;
                flashing = false;
                isComplete = true;
            }

            drawFlash(g, ballCenterX, ballCenterY, t, ballRadius);
        }

        if (isComplete && !flashing) {
            drawWhiteScreen(g);
        }
    }

    private void updatePhysics(double elapsedTime, double currentTime) {
        if (isStopped)
            return;
        vy += gravity * elapsedTime;
        x += vx * elapsedTime;
        y += vy * elapsedTime;

        // Check ball touch ground
        if (y + ballRadius > groundY) {
            y = groundY - ballRadius;
            if (Math.abs(vy) < 20)
                vy = 0; // Prevent ball spam bounce
            else
                vy = -vy * reboundForce;
        }

        // Check ball touch ceil
        if (y - ballRadius < 0) {
            y = 1 + ballRadius;
            vy = -vy * reboundForce;
        }

        if (x - ballRadius < 0) {
            x = ballRadius;
            vx = -vx * reboundForce;
        }

        if (x + ballRadius > W) {
            x = W - ballRadius;
            vx = -vx * reboundForce;
        }

        onGround = (y + ballRadius >= groundY - 0.5) ? true : false;

        if (onGround) { // Friction
            double ax = -mu * gravity * Math.signum(vx);
            double newVx = vx + ax * elapsedTime;
            if (vx != 0 && Math.signum(newVx) != Math.signum(vx))
                vx = 0;
            vx = newVx;
        }

        isStopped = (onGround && Math.abs(vx) < 0.5 && Math.abs(vy) < 0.5);
    }

    // Create white color for make flash on screen
    private void flashPalette() {
        if (flashColor != null)
            return;
        flashColor = new Color[256];
        for (int alpha = 0; alpha < 256; alpha++) {
            flashColor[alpha] = new Color(255, 255, 255, alpha);
        }
    }

    // Make/draw flash after ball is stop.
    private void drawFlash(Graphics g, double x, double y, double t, double radius) {
        flashPalette();
        int xc = (int) Math.round(x), yc = (int) Math.round(y);
        t = clamp01(t);

        // SmoothStep function
        double smoothS = t * t * (3.0 - 2.0 * t);

        // Make core of ball flash
        int diag = (int) Math.hypot(W, H); // Screen diagonal
        int r = (int) Math.round(lerp(radius * 1.4, 1.05 * diag, smoothS)); // radius of flash in core,which span from
                                                                            // core to 105% of screen.
        int alphaCore = Math.min(255, (int) (255 * smoothS)); // Brightness of flash according distance.
        g.setColor(flashColor[alphaCore]); // Use color from flashColor that is make from flashPalette().
        fillMidpointCircle(g, xc, yc, r);

        // Make screen white by flash
        if (t > 0.85f) {
            double tt = (t - 0.85f) / 0.15; // Duration of make screen white.
            int alpha = (int) (Math.min(255, 255 * tt));
            g.setColor(flashColor[alpha]);
            int step = (alpha < 200) ? 2 : 1; // help to prevent overload.
            for (int yy = 0; yy < H; yy += step) {
                bresenhamLine(g, 0, yy, W - 1, yy);
            }
        }
    }

    private void drawWhiteScreen(Graphics g) {
        if (!isComplete)
            return;
        for (int yy = 0; yy < H; yy += 2) {
            bresenhamLine(g, 0, yy, W - 1, yy);
        }
    }

    // Bresenham + Cubic Bezier (Use bresenham as plot)
    public void bresenhamCubicBezier(Graphics g, Point[] points, int steps) {
        Point prev = cubicBerzierCurve(0, points);
        for (int i = 1; i <= steps; i++) {
            double t = i / (double) steps;
            Point current = cubicBerzierCurve(t, points);
            bresenhamLine(g, (int) Math.round(prev.x), (int) Math.round(prev.x), (int) Math.round(current.x),
                    (int) Math.round(current.y));

            prev = current;
        }
    }

    // Plot
    private void plot(Graphics g, int x, int y) {
        g.fillRect(x, y, 3, 3);
    }

    // Linear interpolation
    private double lerp(double a, double b, double t) {
        t = clamp01(t);
        return (a + t * (b - a));
    }

    // Make value into range[0,1]
    private double clamp01(double t) {
        return (t < 0) ? 0 : ((t > 1) ? 1 : t);
    }

    // Bresenham's line drawing method
    public void bresenhamLine(Graphics g, int x1, int y1, int x2, int y2) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);

        int sx = (x1 < x2) ? 1 : -1; // Step x
        int sy = (y1 < y2) ? 1 : -1; // Step y

        boolean isSwap = false;

        if (dy > dx) // swap in case that slope is so steep
        {
            int temp = dx;
            dx = dy;
            dy = temp;
            isSwap = true;
        }

        int D = 2 * dy - dx;

        int x = x1;
        int y = y1;

        for (int i = 0; i <= dx; i++) {
            plot(g, x, y);
            if (D >= 0) // D is decision parameter / error term
            {
                // Minor Axis occur when D >= 0 ,This indicates that the line has deviated far
                // enough to move minor Axis
                if (isSwap)
                    x += sx;
                else
                    y += sy;

                D -= 2 * dx;
            }

            if (isSwap)
                y += sy;
            else
                x += sx;

            D += 2 * dy;
        }
    }

    // Midpoint circle (outline)
    private void midpointCircle(Graphics g, int xc, int yc, int r) {
        int x = 0, y = r;
        int D = 1 - r;
        while (x <= y) {
            plot(g, xc + x, yc + y);
            plot(g, xc - x, yc + y);
            plot(g, xc + x, yc - y);
            plot(g, xc - x, yc - y);
            plot(g, xc + y, yc + x);
            plot(g, xc - y, yc + x);
            plot(g, xc + y, yc - x);
            plot(g, xc - y, yc - x);
            x++;
            D += 2 * x + 1;
            if (D >= 0) {
                y--;
                D -= 2 * y;
            }
        }
    }

    // Midpoint Circle with fill color in circle
    public void fillMidpointCircle(Graphics g, int xc, int yc, int r) {
        int x = 0;
        int y = r; // start at (0,radius), which upper of circle
        int d = 1 - r;

        while (x <= y) {
            span(g, xc, yc, x, y);
            span(g, xc, yc, -x, y);
            span(g, xc, yc, y, x); // Upper octant
            span(g, xc, yc, -y, x); // Lower octant
            x++;
            d += 2 * x + 1;
            if (d >= 0) {
                y--;
                d -= 2 * y;
            }
        }
    }

    // Use to fill color in Midpoint circle by draw holizontal line (Bresenham).
    public void span(Graphics g, int xc, int yc, int yy, int xx) {
        int yyy = yc + yy; // y that should draw
        if (yyy < 0 || yyy >= H)
            return;
        int x1 = (int) Math.round(xc - xx);
        int x2 = (int) Math.round(xc + xx);
        bresenhamLine(g, x1, yyy, x2, yyy);
    }

    // Cubic Bezier curve algorithm
    public Point cubicBerzierCurve(double t, Point[] controlPoints) {
        if (controlPoints == null || controlPoints.length == 0) {
            throw new IllegalArgumentException("Control points cannot be null or empty");
        }
        if (t < 0.0 || t > 1.0) {
            System.err.println("t value is outside [0,1] range");
        }

        Point p1 = controlPoints[0];
        Point p2 = controlPoints[1];
        Point p3 = controlPoints[2];
        Point p4 = controlPoints[3];

        double x = (Math.pow((1 - t), 3) * p1.x) + (3 * t * Math.pow(1 - t, 2) * p2.x)
                + (3 * Math.pow(t, 2) * (1 - t) * p3.x) + (Math.pow(t, 3) * p4.x);
        double y = (Math.pow((1 - t), 3) * p1.y) + (3 * t * Math.pow(1 - t, 2) * p2.y)
                + (3 * Math.pow(t, 2) * (1 - t) * p3.y) + (Math.pow(t, 3) * p4.y);

        return new Point(x, y);
    }

    public BufferedImage floodFill(BufferedImage m, int x, int y, Color target_colour, Color replacement_Colour) {
        if (m == null)
            return null;

        Queue<Point> q = new LinkedList<>();
        q.add(new Point(x, y));

        while (!q.isEmpty()) {
            Point currentPoint = q.poll();
            int currentX = (int) Math.round(currentPoint.x);
            int currentY = (int) Math.round(currentPoint.y);

            if (currentY + 1 < m.getHeight() && m.getRGB(currentX, currentY + 1) == target_colour.getRGB()) {
                m.setRGB(currentX, currentY + 1, replacement_Colour.getRGB());
                q.add(new Point(currentX, currentY + 1));
            }
            if (currentY - 1 >= 0 && m.getRGB(currentX, currentY - 1) == target_colour.getRGB()) {
                m.setRGB(currentX, currentY - 1, replacement_Colour.getRGB());
                q.add(new Point(currentX, currentY - 1));
            }
            if (currentX + 1 < m.getWidth() && m.getRGB(currentX + 1, currentY) == target_colour.getRGB()) {
                m.setRGB(currentX + 1, currentY, replacement_Colour.getRGB());
                q.add(new Point(currentX + 1, currentY));
            }
            if (currentX - 1 >= 0 && m.getRGB(currentX - 1, currentY) == target_colour.getRGB()) {
                m.setRGB(currentX - 1, currentY, replacement_Colour.getRGB());
                q.add(new Point(currentX - 1, currentY));
            }
        }

        return m;
    }

    // Point(x,y)
    public class Point {
        public double x;
        public double y;

        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

}


class DrawKomodo extends JPanel implements Runnable {
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

    // === Animation  ===
    private volatile boolean running = true;
    private double elapsedSec = 0.0;

    private Point2D eyeCenterPanel = new Point2D.Double(W * 0.58, H * 0.42);

    private Point2D zoomTarget = null;
    private double zoomOffsetX = 0, zoomOffsetY = 0;

    // ======== Original tiny-shape translations  ========
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

    // ===== Reused objects/states  =====
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
            dy /= len; 
            double nx = -dy, ny = dx; 
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
