
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static java.lang.Math.*;

/**
 * =========================================================
 * First-person: Coding -> Dimming -> Approach -> Impact -> Blackout
 * (เวอร์ชันที่ “ไม่ใช้” การสร้างรูปของ Graphics2D ทั้งหมด
 * ยกเว้น Polygon (ตระกูล draw/fill) + อัลกอริทึม Bresenham/Bezier/Midpoint
 *
 * ทุกอย่างวาดใหม่ด้วยโพลิกอนล้วน ๆ:
 * - สี่เหลี่ยม/มุมโค้ง → อนุกรมจุดจาก quarter-circle (Midpoint/parametric)
 * - วงรี/เงา → พอยต์ตามพาราเมตริก (Midpoint-ellipse style sampling)
 * - capsule นิ้ว → สองครึ่งวงกลม + สันตรง
 * - highlight โค้ง → โพลิกอนมุมมน
 * - พื้นหลัง/ฐานจอ/คีย์บอร์ด → โพลิกอน
 * - clip ในจอ → setClip ด้วย Polygon
 * =========================================================
 */
public class FPS_Coding extends JPanel implements Runnable {

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
    private final Rectangle screen = new Rectangle(26, 22, W - 52, 240);
    private final Rectangle desk = new Rectangle(20, 330, W - 40, 40);
    private final Rectangle keyboard = new Rectangle(40, 380, W - 80, 170);

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

        float contentLeft = screen.x + codePadding;
        float contentRight = screen.x + screen.width - codePadding;
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
        keyW = (keyboard.width - pad * (cols + 1)) / cols;
        keyH = (keyboard.height - pad * (rows + 1)) / rows;
        keys = new KeyCell[rows][cols];

        for (int r = 0; r < rows; r++) {
            double offset = stagger(r) * (keyW + pad);
            for (int c = 0; c < cols; c++) {
                int x = keyboard.x + pad + (int) (c * (keyW + pad) + offset);
                int y = keyboard.y + pad + r * (keyH + pad);
                boolean in = (x + keyW <= keyboard.x + keyboard.width - pad);

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
            return new Point(keyboard.x, keyboard.y);
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

        int top = screen.y + codePadding + 8;
        int bottom = screen.y + screen.height - codePadding - 8;
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
        double pivotY = desk.y + desk.height / 2.0;

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

        // พื้นหลัง (สี่เหลี่ยมด้วยโพลิกอน)
        g2.setColor(new Color(230, 238, 255));
        fillRectPoly(g2, 0, 0, W, H);

        // โต๊ะ
        g2.setColor(new Color(194, 180, 160));
        fillRoundRectPoly(g2, desk.x, desk.y, desk.width, desk.height, 14, 8);

        // จอ + โค้ด
        drawMonitor(g2);

        // คีย์บอร์ด
        drawKeyboard(g2);

        // มือ
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
        int mx = screen.x + screen.width / 2;
        int top = screen.y + screen.height;
        int gap = 10, postW = 28, postH = Math.max(18, (desk.y - (top + gap)) - 14);

        // เสา/ฐานจอ
        g2.setColor(new Color(85, 90, 110));
        fillRoundRectPoly(g2, mx - postW / 2, top + gap, postW, postH, 10, 8);
        int baseW = 160, baseH = 14, baseY = desk.y - baseH - 4;
        fillRoundRectPoly(g2, mx - baseW / 2, baseY, baseW, baseH, 10, 8);

        // เงาฐานจอ (วงรี)
        g2.setColor(new Color(0, 0, 0, 40));
        fillEllipsePoly(g2, mx, desk.y - 0, (int) (baseW * 0.45), 6, 36);

        // กรอบจอ
        g2.setColor(new Color(58, 62, 78));
        fillRoundRectPoly(g2, screen.x - 6, screen.y - 6, screen.width + 12, screen.height + 12, 16, 12);

        double dim = switch (state) {
            case CODING -> 1.0;
            case DIMMING -> max(0.55, 1.0 - 0.35 * phaseProg);
            case APPROACH -> max(0.25, 1.0 - 0.70 * phaseProg);
            case IMPACT, BLACKOUT -> 0.0;
        };

        float glow = (float) ((0.86 + 0.14 * sin(2 * PI * 0.7 * t)) * dim);
        Color screenBg = new Color(24, 36, 70);
        Color screenGlow = new Color(90, 190, 255, (int) (70 * glow));

        // พื้นจอ
        g2.setColor(screenBg);
        fillRoundRectPoly(g2, screen.x, screen.y, screen.width, screen.height, 12, 12);

        // glow ด้านใน
        g2.setColor(screenGlow);
        fillRoundRectPoly(g2, screen.x + 6, screen.y + 6, screen.width - 12, screen.height - 12, 10, 12);

        boolean showCode = (state == Scene.CODING) || (state == Scene.DIMMING);
        if (showCode) {
            Shape oldClip = g2.getClip();
            Polygon contentClip = roundRectPolygon(screen.x + codePadding, screen.y + codePadding,
                    screen.width - 2 * codePadding, screen.height - 2 * codePadding, 8, 10);
            g2.setClip(contentClip);

            g2.setFont(codeFont);
            g2.setColor(new Color(180, 230, 255));
            FontMetrics fm = g2.getFontMetrics();
            float contentRight = screen.x + screen.width - codePadding;

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
        // ฐานคีย์บอร์ด
        g2.setColor(new Color(72, 78, 95));
        fillRoundRectPoly(g2, keyboard.x, keyboard.y, keyboard.width, keyboard.height, 18, 12);

        // ปุ่มทั้งหมด
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                KeyCell k = keys[r][c];
                if (!k.exists)
                    continue;

                // ปุ่ม
                g2.setColor(new Color(200, 208, 224));
                fillRoundRectPoly(g2, k.x, k.y, k.w, k.h, 10, 8);

                // ไฮไลต์ขอบบน
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

            // ฝ่ามือ (โพลิกอนมุมมน + ไล่สี)
            Paint grad = new GradientPaint(anchor.x + extraSide, anchor.y + extraDrop - palmH / 2, skin1,
                    anchor.x + extraSide, anchor.y + extraDrop + palmH / 2, skin2);
            gg.setPaint(grad);
            fillRoundRectPoly(gg,
                    (int) (anchor.x - palmW / 2 + extraSide),
                    (int) (anchor.y - palmH / 2 + extraDrop),
                    (int) palmW, (int) palmH, 22, 10);

            // นิ้ว (หลังสุด → หน้า)
            int[] order = { 4, 3, 2, 1, 0 };
            for (int i : order)
                fs[i].draw(gg, new Point2D.Float(anchor.x + extraSide, anchor.y + extraDrop), isLeft);

            // แขนเสื้อ (สี่เหลี่ยมธรรมดา)
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

            // นิ้ว = แคปซูล (สองครึ่งวงกลม + สันตรง) → โพลิกอน
            gg.setColor(new Color(255, 220, 190));
            fillCapsulePoly(gg, jx, jy, tipX, tipY, thick / 1.8f, 16);

            // เล็บ (มุมมนเล็ก ๆ)
            float nx = tipX - (isLeft ? 6 : 8), ny = tipY - 4;
            gg.setColor(new Color(255, 245, 235, 220));
            fillRoundRectPoly(gg, Math.round(nx), Math.round(ny), 14, 8, 4, 6);

            // ไฮไลต์เล็บ
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

    /** เติมสี่เหลี่ยมด้วยโพลิกอน */
    private static void fillRectPoly(Graphics2D g2, int x, int y, int w, int h) {
        int[] xs = { x, x + w, x + w, x };
        int[] ys = { y, y, y + h, y + h };
        g2.fillPolygon(xs, ys, 4);
    }

    /** เติมวงรีด้วยโพลิกอน (พอยต์แบบพาราเมตริก: Midpoint-ellipse sampling style) */
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

    /** โพลิกอนสี่เหลี่ยมมุมมน (มุมละ segPerQuarter จุด) */
    private static Polygon roundRectPolygon(int x, int y, int w, int h, int r, int segPerQuarter) {
        int rr = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        int seg = Math.max(3, segPerQuarter);
        List<Point> pts = new ArrayList<>(seg * 4 + 4);

        // ศูนย์กลางมุม
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

    /** เติมสี่เหลี่ยมมุมมนด้วยโพลิกอน */
    private static void fillRoundRectPoly(Graphics2D g2, int x, int y, int w, int h, int r, int segPerQuarter) {
        g2.fillPolygon(roundRectPolygon(x, y, w, h, r, segPerQuarter));
    }

    /**
     * เติม “แคปซูล” (ปลายครึ่งวงกลม 2 ด้าน + สันตรงกลาง) ด้วยโพลิกอน
     * ใช้พอยต์กึ่งพาราเมตริก (Midpoint-circle sampling) ต่อเนื่อง
     */
    private static void fillCapsulePoly(Graphics2D g2, float x1, float y1, float x2, float y2, float r, int seg) {
        double dx = x2 - x1, dy = y2 - y1;
        double L = Math.hypot(dx, dy);
        if (L < 1e-3) {
            // กรณีสั้นมาก → วาดเป็นวงกลม
            fillEllipsePoly(g2, Math.round(x1), Math.round(y1), Math.round(r), Math.round(r), Math.max(16, seg * 2));
            return;
        }

        double ux = dx / L, uy = dy / L;
        double px = -uy, py = ux; // ตั้งฉาก

        // ศูนย์ครึ่งวงกลม
        double cxA = x2, cyA = y2; // ปลาย
        double cxB = x1, cyB = y1; // โคน

        int arcSeg = Math.max(6, seg);
        List<Point> pts = new ArrayList<>(arcSeg * 2 + 8);

        // ครึ่งวงกลมปลาย A: มุม (φ-90) → (φ+90)
        double phi = Math.atan2(dy, dx);
        for (int i = 0; i <= arcSeg; i++) {
            double a = (phi - Math.PI / 2) + (Math.PI * i / arcSeg);
            double cx = cxA + r * Math.cos(a);
            double cy = cyA + r * Math.sin(a);
            pts.add(new Point((int) Math.round(cx), (int) Math.round(cy)));
        }

        // สันด้านล่าง: จาก A-(+p*r) ไป B-(+p*r)
        double ax = cxA + r * Math.cos(phi + Math.PI / 2);
        double ay = cyA + r * Math.sin(phi + Math.PI / 2);
        double bx = cxB + r * Math.cos(phi + Math.PI / 2);
        double by = cyB + r * Math.sin(phi + Math.PI / 2);
        pts.add(new Point((int) Math.round(bx), (int) Math.round(by)));

        // ครึ่งวงกลมโคน B: มุม (φ+90) → (φ+270) (ทิศกลับ)
        for (int i = 0; i <= arcSeg; i++) {
            double a = (phi + Math.PI / 2) + (Math.PI * i / arcSeg);
            double cx = cxB + r * Math.cos(a);
            double cy = cyB + r * Math.sin(a);
            pts.add(new Point((int) Math.round(cx), (int) Math.round(cy)));
        }

        // สันด้านบน: จาก B-(-p*r) กลับ A-(-p*r)
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
