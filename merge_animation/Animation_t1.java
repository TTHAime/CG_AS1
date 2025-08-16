package merge_animation;
import static java.lang.Math.*;

import java.util.Objects;
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
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.CardLayout;
import java.awt.Component;

import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.JComponent;

/**
 * =========================================================
 * First-person: Coding -> Dimming -> Approach -> Impact -> Blackout
 * (เวอร์ชันตัดเอฟเฟกต์ 4 อย่าง: ไฮไลต์ปุ่ม, เคอร์เซอร์กระพริบ, มือหายใจ,
 * ปุ่มยุบ)
 *
 * โครงหลัก:
 * - ลูปอัปเดต/วาด (Runnable + repaint)
 * - State (CODING → DIMMING → APPROACH → IMPACT → BLACKOUT)
 * - กล้อง (ซูม/เลื่อน/เอียง + สั่นช่วง IMPACT)
 * - คีย์บอร์ด (ตารางปุ่มคงที่)
 * - มือ/นิ้ว (กำหนดตำแหน่ง, ขยับนิ้วตาม pattern)
 * - จอ/โค้ดที่เลื่อนจากขวาไปซ้าย
 * =========================================================
 */
class FPS_Coding extends JPanel implements Runnable {

    /* ========================= Canvas & Loop ========================= */

    // ขนาดพื้นที่วาด
    static final int W = 600, H = 600;
    // สวิตช์หยุด/เดินลูปเรนเดอร์
    private volatile boolean running = true;
    // ตัวจับเวลา: t = เวลาเฟสรวม, dtSec = เวลาเฟรมล่าสุด
    private double t = 0.0, dtSec = 0.0;

    /*
     * ========================= State Machine (สถานะฉาก) =========================
     */

    enum Scene {
        CODING, DIMMING, APPROACH, IMPACT, BLACKOUT
    }

    private Scene state = Scene.CODING; // ฉากเริ่มต้น

    // ระยะเวลาของแต่ละเฟส (วินาที)
    private double codeDuration = 5.2;
    private double dimDuration = 2.0;
    private double approachDur = 0.85;
    private double impactDur = 0.45;
    // ความคืบหน้าของเฟสปัจจุบัน 0..1 (นับใหม่ทุกครั้งที่เปลี่ยนเฟส)
    private double phaseProg = 0.0;

    /* ========================= Layout ของฉาก ========================= */

    // พื้นที่จอ, โต๊ะ, คีย์บอร์ด (ตำแหน่งคงที่บนแคนวาส)
    private final Rectangle screen = new Rectangle(26, 22, W - 52, 240);
    private final Rectangle desk = new Rectangle(20, 330, W - 40, 40);
    private final Rectangle keyboard = new Rectangle(40, 380, W - 80, 170);

    /* ========================= คีย์บอร์ด (ตารางปุ่ม) ========================= */

    // จำนวนคอลัมน์/แถวของคีย์บอร์ด + ระยะห่าง
    private final int cols = 14, rows = 5;
    private int keyW, keyH, pad = 6;

    // โครงสร้างเก็บพิกัดปุ่มทั้งหมด (ไม่มีแรงยุบแล้ว)
    private KeyCell[][] keys;

    static class KeyCell {
        int x, y, w, h; // พิกัด/ขนาดปุ่ม
        boolean exists = false; // true = ปุ่มนี้อยู่ภายในฐานคีย์บอร์ด (ขอบไม่เกิน)
    }

    /*
     * =========================
     * ลำดับการพิมพ์ (typing pattern)
     * =========================
     */

    // ตัวบ่งมือซ้าย/ขวา/โป้ง (space)
    private static final int LEFT = 0, RIGHT = 1, THUMB = 2;

    // 1 “จังหวะ” ของการกด: ปุ่ม (r,c) ด้วยมือ/นิ้วใด
    static class Tap {
        final int r, c, hand, finger;

        Tap(int r, int c, int hand, int finger) {
            this.r = r;
            this.c = c;
            this.hand = hand;
            this.finger = finger;
        }
    }

    // ลิสต์จังหวะกด วนลูปไปเรื่อย ๆ ให้ดูเหมือนกำลังกดแป้นพิมพ์
    private final List<Tap> pattern = new ArrayList<>();
    private int patternIndex = 0; // ชี้จังหวะปัจจุบันใน pattern
    private double tapTick = 0.0; // สะสมเวลารอ “ถึงคาบ” เพื่อกดครั้งต่อไป
    private double tapInterval = 0.10; // คาบเวลาต่อหนึ่งจังหวะ (วินาที)

    /* ========================= มือและนิ้ว ========================= */

    // สร้างสองมือ (พร้อมนิ้ว 5 ต่อข้าง)
    private final Hand leftHand = Hand.left();
    private final Hand rightHand = Hand.right();

    /*
     * =========================
     * โค้ดบนหน้าจอ (เลื่อนขวา→ซ้าย)
     * =========================
     */

    static class CodeLine {
        String text; // ข้อความในบรรทัด
        float x; // ตำแหน่ง X (จะเลื่อนซ้าย)
        int y; // ตำแหน่ง Y คงที่ (แถว)
        float speed; // ความเร็วในการเลื่อน
        int width; // ความกว้างข้อความ (คำนวณครั้งแรกตอนวาด)

        CodeLine(String s, int y, float v) {
            text = s;
            this.y = y;
            speed = v;
        }
    }

    // กล่องเก็บหลายบรรทัด + รูปแบบตัวอักษร
    private final List<CodeLine> codeLines = new ArrayList<>();
    private final int codePadding = 14;
    private final Font codeFont = new Font("Consolas", Font.PLAIN, 14);

    /* ========================= กล้อง/แรงกระแทก ========================= */

    // กล้อง: สเกล/เลื่อนลง/เอียง (เอียงด้วยค่าสุ่มช่วง IMPACT)
    private double camScale = 1.0, camDrop = 0.0;
    private double camRot = 0.0; // มุมเอียง (เรเดียน)
    private double rotVel = 0.0; // ความเร็วเชิงมุม (เรเดียน/วินาที)

    // ค่าสุ่มสำหรับการสั่นตอน IMPACT
    private double shakeAmp = 0.0;
    private final Random rng = new Random(23);

    /* ========================= Constructor ========================= */

    public FPS_Coding() {
        // ตั้งค่าพื้นฐานของ JPanel
        setPreferredSize(new Dimension(W, H));
        setBackground(new Color(245, 248, 255));
        setDoubleBuffered(true);

        // เตรียมฉาก: คีย์บอร์ด, ลำดับการกด, โค้ดบนหน้าจอ
        buildKeyboard();
        buildPattern();
        initCodeLines();

        // ตั้งตำแหน่งมือให้ใกล้แถว Home row (F/J)
        Point f = getKeyCenter(2, 3), j = getKeyCenter(2, 9);
        leftHand.setAnchor(f.x - 20, f.y + 22);
        rightHand.setAnchor(j.x + 20, j.y + 22);

        // จูนออฟเซ็ตนิ้วรายนิ้ว (0=โป้ง, 1=ชี้, 2=กลาง, 3=นาง, 4=ก้อย)
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

    /* ========================= Game Loop (Runnable) ========================= */

    @Override
    public void run() {
        // ลูปอัปเดต-วาดอย่างง่ายด้วยเวลาจริงจาก System.currentTimeMillis()
        double last = System.currentTimeMillis();
        while (running) {
            double now = System.currentTimeMillis();
            dtSec = (now - last) / 1000.0; // คำนวณ delta time ของเฟรม
            last = now;

            update(dtSec); // อัปเดตสถานะ/กล้อง/มือ/โค้ด
            repaint(); // ขอให้ Swing วาดใหม่

            try {
                Thread.sleep(1);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /*
     * =========================
     * Update Logic (แกนของแอนิเมชัน)
     * =========================
     */

    private void update(double dt) {
        t += dt; // เดินนาฬิกาหลัก

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

        // เดิน phaseProg ด้วยเวลาของแต่ละเฟส
        if (state == Scene.DIMMING)
            phaseProg = min(1.0, phaseProg + dt / dimDuration);
        if (state == Scene.APPROACH)
            phaseProg = min(1.0, phaseProg + dt / approachDur);
        if (state == Scene.IMPACT)
            phaseProg = min(1.0, phaseProg + dt / impactDur);

        // 2) กล้อง/มือปรับค่าตามเฟส
        if (state == Scene.CODING) {
            camScale = 1.0;
            camDrop = 0.0;
            camRot = 0.0;
            rotVel = 0.0;

        } else if (state == Scene.DIMMING) {
            // ease-in ด้วย 1 - cos(πp) ให้รู้สึก “หนักตัวลง”
            double k = 1.0 - cos(PI * phaseProg);
            camScale = 1.0 + 0.05 * k; // ซูมเล็กน้อย
            camDrop = 5.0 * k; // ลดระดับลงเล็กน้อย
            leftHand.extraDrop = (float) (8 * k);
            rightHand.extraDrop = (float) (10 * k);
            camRot = 0.0;
            rotVel = 0.0;

        } else if (state == Scene.APPROACH) {
            // พุ่งเข้าโต๊ะ: ซูมแรง + ลดระดับ + มือแยกออกเล็กน้อย
            double k = 1.0 - cos(PI * phaseProg);
            camScale = 1.0 + 0.60 * k;
            camDrop = 34 * k;
            leftHand.extraDrop = (float) (28 * k);
            rightHand.extraDrop = (float) (32 * k);
            leftHand.extraSide = (float) (-14 * k);
            rightHand.extraSide = (float) (14 * k);
            camRot *= 0.9; // ลดเอียงก่อนเข้าชน

        } else if (state == Scene.IMPACT) {
            // ช่วงกระแทก: สั่น + สมการสปริงหมุน (หน่วง)
            shakeAmp = 1.0 * exp(-3.5 * phaseProg); // ยิ่งนานยิ่งเบา
            camScale = 1.60; // ซูมคงที่ระหว่างชน
            camDrop = 36;

            // camRot'' + c*camRot' + k*camRot = 0 (แกว่งแล้วสงบ)
            double c = 6.0, k = 90.0;
            rotVel += (-c * rotVel - k * camRot) * dt;
            camRot += rotVel * dt;

            leftHand.extraDrop = 38;
            rightHand.extraDrop = 42;

        } else { // BLACKOUT
            camScale = 1.60;
            camDrop = 36;
            camRot *= 0.9; // ค่อย ๆ หยุดเอียง
        }

        // 3) คุม “ความเร็วพิมพ์” ให้สัมพันธ์กับสติ
        double speedMul = 0.0;
        if (state == Scene.CODING) {
            double remain = max(0.0, codeDuration - t);
            speedMul = (remain < 0.5) ? (0.3 + 1.4 * remain) : 1.0; // ท้ายเฟสช้าลง
        } else if (state == Scene.DIMMING) {
            speedMul = 0.8 * (1.0 - 0.6 * phaseProg); // มืดลง → ช้าลง
        }

        // 4) เดิน pattern: ทุก ๆ tapInterval จะสั่งให้ “นิ้ว” เอื้อมไปกด key
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

        // 5) อัปเดตมือ (ปล่อยแรงกดนิ้ว) และเลื่อนข้อความบนหน้าจอ
        leftHand.update(dt, t);
        rightHand.update(dt, t);

        float contentLeft = screen.x + codePadding;
        float contentRight = screen.x + screen.width - codePadding;
        for (CodeLine cl : codeLines) {
            cl.x -= cl.speed * dt * (float) speedMul; // เลื่อนซ้ายตามความเร็ว
            if (cl.x + cl.width < contentLeft) // หลุดซ้าย → วนกลับด้านขวา
                cl.x = contentRight + 40 + (float) (Math.random() * 80);
        }

        // (ตัดระบบคืนตัวปุ่ม เพราะไม่มีการยุบแล้ว)
    }

    /*
     * =========================
     * เหตุการณ์เริ่มชน (ตั้งค่าเริ่ม IMPACT)
     * =========================
     */

    private void onImpactStart() {
        // สุ่มทิศการหมุนซ้าย/ขวา และความเร็วตั้งต้น เพื่อให้ภาพ “หัวหมุน” มีคาแรกเตอร์
        double sign = rng.nextBoolean() ? 1 : -1;
        camRot = toRadians(1.0 * sign);
        rotVel = toRadians((220 + rng.nextInt(80)) * sign);
    }

    /*
     * ========================= สร้างคีย์บอร์ด (พิกัดปุ่ม)
     * =========================
     */

    private void buildKeyboard() {
        // คำนวณขนาดปุ่มจากกรอบและจำนวนคอลัมน์/แถว
        keyW = (keyboard.width - pad * (cols + 1)) / cols;
        keyH = (keyboard.height - pad * (rows + 1)) / rows;
        keys = new KeyCell[rows][cols];

        // เติม KeyCell แต่ละช่อง พร้อมเช็กว่าปุ่มนี้อยู่ภายในฐานบอร์ดหรือไม่
        for (int r = 0; r < rows; r++) {
            double offset = stagger(r) * (keyW + pad); // เยื้องแถวให้เหมือนคีย์จริง
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

    // รูปทรงแถวคีย์บอร์ด
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

    // คืนตำแหน่งกึ่งกลางของปุ่ม (ใช้เป็นเป้าหมายให้ “นิ้ว” เอื้อมไป)
    private Point getKeyCenter(int r, int c) {
        if (r < 0 || r >= rows || c < 0 || c >= cols || !keys[r][c].exists)
            return new Point(keyboard.x, keyboard.y);
        KeyCell k = keys[r][c];
        return new Point(k.x + k.w / 2, k.y + k.h / 2);
    }

    /*
     * =========================
     * ลำดับการพิมพ์
     * =========================
     */

    private void buildPattern() {
        // ใส่จังหวะกดปุ่ม
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

    /*
     * ========================= โค้ดบนจอ (เตรียมบรรทัด) =========================
     */

    private void initCodeLines() {
        // ข้อความ/จำนวนบรรทัด บนหน้าจอ
        String[] lines = {
                "public static void main(String[] args) {",
                "Hello World",
                "}",
                "Computer Graphic",
                "ah hehe",
                "CG",
                "Bézier",
                "Midpoint",
                "Animation"
        };

        // วางแต่ละบรรทัดเป็นแนวนอนหลายแถวในหน้าจอ (ตำแหน่ง y คงที่)
        int top = screen.y + codePadding + 8;
        int bottom = screen.y + screen.height - codePadding - 8;
        int gap = 22, y = top;

        // สุ่มความเร็วเล็กน้อยต่อบรรทัด เพื่อให้ดูมีมิติ
        for (int i = 0; i < lines.length && y < bottom; i++, y += gap) {
            codeLines.add(new CodeLine(lines[i], y, 70 + (float) (Math.random() * 60)));
        }
    }

    /*
     * ========================= วาดเฟรม (paintComponent) =========================
     */

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // จุดหมุนกล้อง (pivot) = กลางโต๊ะ → เวลาซูม/เอียงจะดูอิงโต๊ะ
        double pivotX = W / 2.0;
        double pivotY = desk.y + desk.height / 2.0;

        // แปลงพิกัดตามกล้อง: ย้ายไป pivot → scale → rotate → ย้ายกลับ + drop
        g2.translate(pivotX, pivotY);
        g2.scale(camScale, camScale);
        g2.rotate(camRot);
        g2.translate(-pivotX, -pivotY + camDrop);

        // สั่นแบบสุ่มเล็กน้อยเฉพาะช่วง IMPACT
        if (state == Scene.IMPACT && shakeAmp > 0) {
            double sx = (rng.nextDouble() * 2 - 1) * 6 * shakeAmp;
            double sy = (rng.nextDouble() * 2 - 1) * 4 * shakeAmp;
            double rot = (rng.nextDouble() * 2 - 1) * Math.toRadians(0.7 * shakeAmp);
            g2.translate(sx, sy);
            g2.rotate(rot, pivotX, pivotY);
        }

        // พื้นหลังห้อง + โต๊ะ
        g2.setColor(new Color(230, 238, 255));
        g2.fillRect(0, 0, W, H);
        g2.setColor(new Color(194, 180, 160));
        g2.fillRoundRect(desk.x, desk.y, desk.width, desk.height, 14, 14);

        // จอ + โค้ด
        drawMonitor(g2);

        // คีย์บอร์ด (เวอร์ชันเรียบ: ไม่ยุบ/ไม่ไฮไลต์ปุ่ม)
        drawKeyboard(g2);

        // มือสองข้าง (รวมแขนเสื้อสี่เหลี่ยม)
        leftHand.draw(g2, true);
        rightHand.draw(g2, false);

        g2.dispose();

        // เลเยอร์มืดทับทั้งฉาก ตามเฟสสติ
        Graphics2D go = (Graphics2D) g.create();
        if (state == Scene.DIMMING) {
            float a = (float) (0.10 + 0.40 * phaseProg);
            go.setColor(new Color(0, 0, 0, (int) (255 * a)));
            go.fillRect(0, 0, getWidth(), getHeight());
        } else if (state == Scene.APPROACH) {
            float a = (float) min(1.0, 0.65 + 0.25 * phaseProg);
            go.setColor(new Color(0, 0, 0, (int) (255 * a)));
            go.fillRect(0, 0, getWidth(), getHeight());
        } else if (state == Scene.IMPACT) {
            float a = (float) min(1.0, 0.85 + 0.15 * phaseProg);
            go.setColor(new Color(0, 0, 0, (int) (255 * a)));
            go.fillRect(0, 0, getWidth(), getHeight());
        } else if (state == Scene.BLACKOUT) {
            go.setColor(Color.BLACK);
            go.fillRect(0, 0, getWidth(), getHeight());
        }
        go.dispose();
    }

    /* ========================= วาดจอ + เนื้อหา ========================= */

    private void drawMonitor(Graphics2D g2) {
        // ขาตั้ง + เงาบนโต๊ะ
        int mx = screen.x + screen.width / 2;
        int top = screen.y + screen.height;
        int gap = 10, postW = 28, postH = Math.max(18, (desk.y - (top + gap)) - 14);
        g2.setColor(new Color(85, 90, 110));
        g2.fillRoundRect(mx - postW / 2, top + gap, postW, postH, 10, 10);
        int baseW = 160, baseH = 14, baseY = desk.y - baseH - 4;
        g2.fillRoundRect(mx - baseW / 2, baseY, baseW, baseH, 10, 10);
        g2.setColor(new Color(0, 0, 0, 40));
        g2.fillOval(mx - (int) (baseW * 0.45), desk.y - 6, (int) (baseW * 0.90), 12);

        // กรอบจอ
        g2.setColor(new Color(58, 62, 78));
        g2.fillRoundRect(screen.x - 6, screen.y - 6, screen.width + 12, screen.height + 12, 16, 16);

        // สว่างจอตามเฟส (ค่าดิมลดลงเรื่อย ๆ)
        double dim = switch (state) {
            case CODING -> 1.0;
            case DIMMING -> max(0.55, 1.0 - 0.35 * phaseProg);
            case APPROACH -> max(0.25, 1.0 - 0.70 * phaseProg);
            case IMPACT, BLACKOUT -> 0.0;
        };

        // จอ + glow เบา ๆ (แอมป์ขึ้นกับ dim)
        float glow = (float) ((0.86 + 0.14 * sin(2 * PI * 0.7 * t)) * dim);
        Color screenBg = new Color(24, 36, 70);
        Color screenGlow = new Color(90, 190, 255, (int) (70 * glow));
        g2.setColor(screenBg);
        g2.fillRoundRect(screen.x, screen.y, screen.width, screen.height, 12, 12);
        g2.setColor(screenGlow);
        g2.fillRoundRect(screen.x + 6, screen.y + 6, screen.width - 12, screen.height - 12, 10, 10);

        // วาดโค้ดเฉพาะช่วงยังเห็นจอ (CODING/DIMMING)
        boolean showCode = (state == Scene.CODING) || (state == Scene.DIMMING);
        if (showCode) {
            // clip เนื้อหาไว้ในจอ (กันหลุด)
            Shape oldClip = g2.getClip();
            RoundRectangle2D content = new RoundRectangle2D.Float(
                    screen.x + codePadding, screen.y + codePadding,
                    screen.width - 2 * codePadding, screen.height - 2 * codePadding, 8, 8);
            g2.setClip(content);

            // วาดทีละบรรทัด (ตำแหน่ง x เปลี่ยนตาม update())
            g2.setFont(codeFont);
            g2.setColor(new Color(180, 230, 255));
            FontMetrics fm = g2.getFontMetrics();
            float contentRight = screen.x + screen.width - codePadding;

            for (CodeLine cl : codeLines) {
                if (cl.width == 0) { // lazy init: วัดความกว้างครั้งแรก
                    cl.width = fm.stringWidth(cl.text);
                    cl.x = contentRight + (float) (Math.random() * 120); // spawn ทางขวาสุ่มเล็กน้อย
                }
                g2.drawString(cl.text, cl.x, cl.y);
            }
            g2.setClip(oldClip);
        }
    }

    /*
     * =========================
     * วาดคีย์บอร์ด (เรียบ ไม่ยุบ)
     * =========================
     */

    private void drawKeyboard(Graphics2D g2) {
        // ฐานคีย์บอร์ด
        g2.setColor(new Color(72, 78, 95));
        g2.fillRoundRect(keyboard.x, keyboard.y, keyboard.width, keyboard.height, 18, 18);

        // วาดปุ่มทั้งหมดในตาราง
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                KeyCell k = keys[r][c];
                if (!k.exists)
                    continue;

                int drawX = k.x, drawY = k.y, drawW = k.w, drawH = k.h;

                // พื้นปุ่ม
                g2.setColor(new Color(200, 208, 224));
                RoundRectangle2D rr = new RoundRectangle2D.Float(drawX, drawY, drawW, drawH, 10, 10);
                g2.fill(rr);

                // ไฮไลต์ขอบบนปุ่มเล็กน้อย (เพื่อมิติ)
                g2.setColor(new Color(255, 255, 255, 60));
                g2.fill(new RoundRectangle2D.Float(drawX + 2, drawY + 2, drawW - 4, 4, 6, 6));
            }
        }
    }

    /*
     * =========================
     * มือ/นิ้ว (โครงวาด + ฟิสิกส์นิ้ว)
     * =========================
     */

    static class Hand {
        final Point2D.Float anchor = new Point2D.Float(); // จุดอ้างอิงฝ่ามือบนโต๊ะ
        final Finger[] fs; // นิ้ว 0..4
        final float palmW = 74, palmH = 56; // ขนาดฝ่ามือ (เรขาคณิตวาด)
        final Color skin1 = new Color(255, 220, 190), // ไล่เฉดสีผิว
                skin2 = new Color(240, 200, 170);
        double phase; // (ไม่ใช้ในรุ่นนี้)
        float extraDrop = 0, extraSide = 0; // ชดเชยทั้งมือ (ตามเฟส)

        private Hand(Finger[] fs, double ph) {
            this.fs = fs;
            this.phase = ph;
        }

        // พรีเซ็ตมือซ้าย/ขวา: วางฐานนิ้วแบบคร่าว ๆ
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

        // ขยับ “ทั้งมือ” ไปตำแหน่งใหม่
        void setAnchor(float x, float y) {
            anchor.setLocation(x, y);
        }

        void setAnchor(double x, double y) {
            setAnchor((float) x, (float) y);
        }

        // ปรับออฟเซ็ต “นิ้วเดี่ยว” (0=โป้ง..4=ก้อย)
        void setFingerOffset(int idx, float dx, float dy) {
            if (idx < 0 || idx >= fs.length)
                return;
            fs[idx].offsetX = dx;
            fs[idx].offsetY = dy;
        }

        // สั่งนิ้ว (ดัชนี/กลาง/นาง/ก้อย) ให้เอื้อมไปยังจุดศูนย์กลางปุ่ม
        void tapFinger(int fingerIndex, Point key) {
            int idx = Math.max(1, Math.min(4, fingerIndex + 1)); // map ให้ไม่ไปชนโป้ง
            fs[idx].pressAt(key.x, key.y);
        }

        // สั่งโป้ง (space)
        void tapThumb(Point key) {
            fs[0].pressAt(key.x, key.y + 8);
        }

        // อัปเดตสถานะนิ้ว (คายแรงกดตามเวลา)
        void update(double dt, double tg) {
            for (Finger f : fs)
                f.update(dt);
        }

        // วาดมือ: เงา → ฝ่ามือ → นิ้ว → แขนเสื้อ
        void draw(Graphics2D g2, boolean isLeft) {
            Graphics2D gg = (Graphics2D) g2.create();

            // เงามือบนโต๊ะ
            gg.setColor(new Color(0, 0, 0, 40));
            gg.fillOval((int) (anchor.x - palmW * 0.42 + extraSide),
                    (int) (anchor.y - palmH * 0.1 + 26 + extraDrop),
                    (int) (palmW * 0.84), 18);

            // ฝ่ามือ (สี่เหลี่ยมมุมมน + ไล่เฉดแนวตั้ง)
            Paint grad = new GradientPaint(anchor.x + extraSide, anchor.y + extraDrop - palmH / 2, skin1,
                    anchor.x + extraSide, anchor.y + extraDrop + palmH / 2, skin2);
            gg.setPaint(grad);
            gg.fill(new RoundRectangle2D.Float(
                    anchor.x - palmW / 2 + extraSide,
                    anchor.y - palmH / 2 + extraDrop, palmW, palmH, 22, 22));

            // วาดนิ้วจาก “ไกลผู้ชมสุด” มาก่อน เพื่อให้ซ้อนทับดูถูกต้อง
            int[] order = { 4, 3, 2, 1, 0 };
            for (int i : order)
                fs[i].draw(gg, new Point2D.Float(anchor.x + extraSide, anchor.y + extraDrop), isLeft);

            // แขนเสื้อ (เวอร์ชันสี่เหลี่ยม)
            gg.setColor(new Color(35, 95, 165));
            int bw = 80, bh = 90;
            int bx = (int) (anchor.x + extraSide - bw / 2);
            int by = (int) (anchor.y + extraDrop + palmH / 2 - 6);
            gg.fillRect(bx, by, bw, bh);

            gg.dispose();
        }
    }

    static class Finger {
        // จุดฐานของนิ้ว (สัมพัทธ์กับ anchor มือ) + รูปร่าง
        final float baseX, baseY, len, thick;
        final boolean thumb;

        // สถานะการกด (press ลดจาก 1 → 0)
        float press = 0f;
        float tx = Float.NaN, ty = Float.NaN; // เป้าหมาย (จุดศูนย์กลางปุ่ม)

        // ออฟเซ็ตแก้ทรงนิ้วรายนิ้ว
        float offsetX = 0f, offsetY = 0f;

        private Finger(float bx, float by, float l, float th, boolean t) {
            baseX = bx;
            baseY = by;
            len = l;
            thick = th;
            thumb = t;
        }

        // พรีเซ็ตความยาว/ความหนาของนิ้ว
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

        // เริ่ม “เอื้อมไปกด” เป้าหมาย (ตั้งค่า press = 1)
        void pressAt(float x, float y) {
            tx = x;
            ty = y;
            press = 1f;
        }

        void pressAt(double x, double y) {
            pressAt((float) x, (float) y);
        }

        // คายแรงกดตามเวลา (ลด press)
        void update(double dt) {
            press = max(0f, (float) (press - dt * 3.5));
        }

        // วาดนิ้วแบบ “แคปซูล” จากฐาน → ปลาย (เอื้อมเข้าหาเป้าตาม press)
        void draw(Graphics2D g2, Point2D.Float anchor, boolean isLeft) {
            Graphics2D gg = (Graphics2D) g2.create();

            // จุดฐานนิ้วจริง = anchor มือ + base + offset ที่ปรับทรง
            float jx = anchor.x + baseX + offsetX;
            float jy = anchor.y + baseY + offsetY;

            // เวกเตอร์เอื้อมไปยังเป้าหมาย (กดมาก → เอื้อมมาก)
            float dx = 0, dy = 0;
            if (!Float.isNaN(tx)) {
                dx = (tx - jx) * press * 0.28f;
                dy = (ty - jy) * press * 0.28f + 6f * press; // มีน้ำหนักกดลง
            }

            // ความงอของนิ้ว (โป้งงอน้อยกว่า)
            float curl = thumb ? 8f : 12f;
            float tipX = jx + dx, tipY = jy - (len - curl) + dy;

            // นิ้วเป็นแคปซูล (โคนมน-ปลายมน)
            Shape finger = buildCapsule(jx, jy, tipX, tipY, thick / 1.8f);
            gg.setColor(new Color(255, 220, 190));
            gg.fill(finger);

            // เล็บ/ไฮไลต์เล็กน้อย
            float nx = tipX - (isLeft ? 6 : 8), ny = tipY - 4;
            gg.setColor(new Color(255, 245, 235, 220));
            gg.fill(new RoundRectangle2D.Float(nx, ny, 14, 8, 4, 4));
            gg.setColor(new Color(255, 255, 255, 120));
            gg.fill(new RoundRectangle2D.Float(nx + 2, ny + 1, 10, 3, 3, 3));

            gg.dispose();
        }

        // สร้างรูปร่าง “แคปซูล” จากจุด A→B ด้วยรัศมี r
        private static Shape buildCapsule(float x1, float y1, float x2, float y2, float r) {
            double dx = x2 - x1, dy = y2 - y1, L = hypot(dx, dy);
            if (L < 1e-3)
                L = 1e-3;
            double ux = dx / L, uy = dy / L, px = -uy, py = ux;
            float hx = (float) (px * r), hy = (float) (py * r);

            Path2D p = new Path2D.Float();
            p.moveTo(x1 + hx, y1 + hy);
            p.lineTo(x2 + hx, y2 + hy);
            p.quadTo(x2 + hx + (float) (ux * r), y2 + hy + (float) (uy * r), x2, y2);
            p.quadTo(x2 - hx + (float) (ux * r), y2 - hy - (float) (uy * r), x2 - hx, y2 - hy);
            p.lineTo(x1 - hx, y1 - hy);
            p.quadTo(x1 - hx - (float) (ux * r), y1 - hy - (float) (uy * r), x1, y1);
            p.quadTo(x1 + hx - (float) (ux * r), y1 + hy - (float) (uy * r), x1 + hx, y1 + hy);
            p.closePath();
            return p;
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FPS_Coding panel = new FPS_Coding();
            JFrame f = new JFrame("Code → Dimming → Impact (Spin) → Blackout");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(panel);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            new Thread(panel).start(); // เริ่มลูปเรนเดอร์
        });
    }
}

class BallDrop extends JPanel implements Runnable {
    private static final int H = 600, W = 600;
    private final double ballRadius = 70;

    // Physics logic
    private double x = 80, y = 80;
    private final double gravity = 2000; // px/s^2 (200 pixels * 10(~9.8 -->gravity acceleration))
    private final double reboundForce = 0.8;
    private double vx = 150; // Velocity move ball in x axis
    private double vy = 0; // Velocity in y axis
    private final double mu = 0.07; // Just Appoximate, use to calculate friction of ball when ball touch floor

    // bounce
    private boolean isBounced = false;
    private double prevY = y;
    private boolean onGround = false;

    // Transform setting
    private final int tolPoint = 20; // tolerance point that ball will transform into animal

    enum Mode {
        BALL, GLOWING, KOMODO, FLASH
    };

    private Mode mode = Mode.BALL;
    private boolean isGlowStart = false;
    private double glowingStartTime = 0;
    private final double glowingDuration = 3;
    private final double flashDuration = 0.3; // Approximately
    private boolean flashLocked = false;
    private double flastStartTime = 0;
    private final double flashOverlap = 0.25;

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

        while (true) {
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
        Graphics2D g2 = (Graphics2D) g;
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, W, H);

        if (mode != Mode.KOMODO) {
            drawBall(g2, x, y, ballRadius);
        }

        if (mode == Mode.GLOWING) {
            double t = (System.currentTimeMillis() - glowingStartTime) / 1000.0;
            double flashTime = (System.currentTimeMillis() - flastStartTime) / 1000.0;
            float glowP = normalize((float) (t / glowingDuration)); // 1e-6 : use for prevent divided by zero
            float flashP = normalize((float) (flashTime / flashDuration));
            drawGlow(g2, x, y, glowP, (int) ballRadius); // Make ball glowing golden light
            if (flashLocked) {
                drawSolidWhite(g2);
            } else if (flashP > 0f) {
                drawFlashCutscene(g2, x, y, flashP, (int) ballRadius);
                if (flashP >= 1f) {
                    flashLocked = true;
                    mode = Mode.KOMODO;
                    System.out.println(mode);
                }
            }
        }

    }

    private void drawBall(Graphics2D g2, double cx, double cy, double r) {
        // Draw Outter ring of ball
        double innerRingRadius = r * (1.0 / 3.5);
        g2.setColor(Color.BLACK);
        int xc = (int) Math.round(cx);
        int yc = (int) Math.round(cy);
        int R = (int) Math.round(r);

        int innerR = (int) Math.round(innerRingRadius);
        g2.setColor(Color.RED);
        g2.fill(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 0, 180, Arc2D.PIE));

        g2.setColor(Color.WHITE);
        g2.fill(new Arc2D.Double(cx - r, cy - r, 2 * r, 2 * r, 180, 180, Arc2D.PIE));
        g2.fill(new Ellipse2D.Double(cx - innerR, cy - innerR, 2 * innerR, 2 * innerR));

        g2.setColor(Color.BLACK);
        midpointCircle(g2, xc, yc, R);
        midpointCircle(g2, xc, yc, innerR);
        g2.drawRect(xc - R, yc, R - innerR, (int) Math.round(R * 0.01));
        g2.drawRect(xc + innerR, yc, R - innerR, (int) Math.round(R * 0.01));
    }

    // Physics Apply Method
    private void updatePhysics(double elapsedTime, double currentTime) {
        prevY = y;

        // Make ball fall
        vy += gravity * elapsedTime;
        x += vx * elapsedTime;
        y += vy * elapsedTime;

        // Check if ball touch ground
        if (y + ballRadius > H) {
            y = H - ballRadius;
            if (Math.abs(vy) < 20)
                vy = 0; // Prevent ball spam bounce
            else
                vy = -vy * reboundForce;
            isBounced = true;
        }

        // If ball touch ceil
        if (y - ballRadius < 0) {
            y = H;
            vy -= gravity * reboundForce;
        }

        // If ball touch left side wall
        if (x - ballRadius < 0) {
            x = ballRadius;
            vx = -vx * reboundForce;
        }

        // If ball touch right side wall
        if (x + ballRadius > W) {
            x = W - ballRadius;
            vx = -vx * reboundForce;
        }

        // Make ball rolling friction
        onGround = (y + ballRadius >= H - 0.5) ? true : false;

        if (onGround) { // Friction
            double ax = -mu * gravity * Math.signum(vx);
            double newVx = vx + ax * elapsedTime;
            if (vx != 0 && Math.signum(newVx) != Math.signum(vx))
                vx = 0;
            vx = newVx;
        }

        // Make the ball glowing
        Double mid = H / 2.0;
        if (!isGlowStart && isBounced && mode == Mode.BALL) {
            boolean movingUp = vy < 0;
            boolean isCrossMid = (prevY > mid + tolPoint) && (y <= mid + tolPoint);
            if (movingUp && isCrossMid) {
                mode = Mode.GLOWING;
                isGlowStart = true;
                glowingStartTime = currentTime;
            }
        }

        // Prepare for flash
        if (mode == Mode.GLOWING) {
            flashLocked = false;
            flastStartTime = glowingStartTime + (long) ((glowingDuration - flashOverlap) * 1000.0);
        }

    }

    private void drawGlow(Graphics2D g2, double cx, double cy, float p, int baseRadius) {
        // p is progress of glowing animation
        float t = normalize(p); // Normalize progress value into 0...1 range

        float corePhase = normalize(t / 0.35f);
        float expanPhase = normalize((t - 0.35f) / 0.65f);

        float rCore = baseRadius * (1f + 0.4f * eassingOutCubic(corePhase)); // Radius of glowing light in core phase
        float aCore = lerp(0.2f, 1f, eassingOutCubic(corePhase)); // Alpha Core, core brightness

        float diag = (float) Math.hypot(W, H); // Get screen diagonal, use for bright glowing entire screen

        float rHalo = lerp(rCore, 1.10f * diag, eassingOutCubic(expanPhase)); // Halo radius
        float aHalo = lerp(0.0f, 0.3f, eassingOutCubic(expanPhase)); // Alpha value of halo, Halo strength

        Point2D c = new Point2D.Float((float) cx, (float) cy);

        Paint oldPaint = g2.getPaint();
        Composite oldCompo = g2.getComposite();

        g2.setComposite(AlphaComposite.SrcOver);

        { // Draw core light at the ball
            float[] coreFraction = { 0.0f, 0.20f, 1f };

            Color[] coreColors = {
                    new Color(255, 255, 240, Math.round(255 * aCore)),
                    new Color(255, 255, 120, Math.round(255 * (0.8f * aCore))),
                    new Color(255, 255, 120, 0)
            };
            RadialGradientPaint rGradientPaint = new RadialGradientPaint(c, rCore, coreFraction, coreColors);
            g2.setPaint(rGradientPaint);
            g2.fill(new Ellipse2D.Float((float) (cx - rCore), (float) (cy - rCore), 2 * rCore, 2 * rCore));
        }

        { // Draw Expan light
            float[] expanFraction = { 0f, 1f };
            Color[] haloColors = {
                    new Color(255, 220, 90, Math.round(255 * aHalo)),
                    new Color(255, 220, 90, 0)
            };
            RadialGradientPaint rGradientPaint = new RadialGradientPaint(c, rHalo, expanFraction, haloColors);
            g2.setPaint(rGradientPaint);
            g2.fillRect(0, 0, W, H);
        }

        g2.setPaint(oldPaint);
        g2.setComposite(oldCompo);
    }

    private void drawFlashCutscene(Graphics2D g2, double cx, double cy, float p, int baseRadius) {
        float t = normalize(p); // Normalze progress into 0...1

        float diag = (float) Math.hypot(W, H); // Calculate screen diagonal, make flash can reach entire screen
        float r = lerp(baseRadius * 1.4f, 1.05f * diag, eassingOutCubic(t));
        float core = eassingOutCubic(t); // Ease in quadratic. bright in core of ball
        float wash = 0.85f * eassingOutCubic(t); // Draw WHITE color with 85% of opacity

        Point2D center = new Point2D.Float((float) cx, (float) cy);
        float[] fraction = { 0.0f, 0.1f, 1f };
        Color[] colors = {
                new Color(255, 255, 255, Math.round(255 * core)),
                new Color(255, 245, 210, Math.round(255 * (0.5f * core))),
                new Color(255, 245, 210, 0)
        };

        RadialGradientPaint rGradientPaint = new RadialGradientPaint(center, r, fraction, colors);

        Paint oldPaint = g2.getPaint(); // Save current paint
        Composite oldCompo = g2.getComposite(); // Save current composite

        g2.setComposite(AlphaComposite.SrcOver);
        g2.setPaint(rGradientPaint);
        g2.fillRect(0, 0, W, H);

        g2.setComposite(AlphaComposite.SrcOver.derive(wash));
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, W, H);

        g2.setPaint(oldPaint);// Restore paint
        g2.setComposite(oldCompo);// Restore Composite
    }

    private void drawSolidWhite(Graphics2D g2) {
        g2.setComposite(AlphaComposite.SrcOver);
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, W, H);
    }

    private float normalize(float x) {
        return (x < 0f) ? 0f : (x >= 1f) ? 1f : x;
    }

    private float easingInQuad(float x) {
        x = normalize(x);
        return (float) Math.pow(x, 2);
    }

    private float eassingInCubic(float t) {
        t = normalize(t);
        return (float) Math.pow(t, 3);
    }

    private float lerp(float a, float b, float t) {
        return a + (normalize(t) * (b - a)); // Linear interpolation A + t * (B-A) where t is value between 0...1
    }

    private float eassingOutCubic(float t) {
        return 1f - ((float) Math.pow(1 - normalize(t), 3));
    }

    // Implement midpoint circle
    public void midpointCircle(Graphics2D g, int xc, int yc, int r) {
        int x = 0;
        int y = r;
        int Dx = 2 * x;
        int Dy = 2 * y;
        int D = 1 - r;

        while (x <= y) {
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

            if (D >= 0) {
                y--;
                Dy -= 2;
                D -= Dy;
            }
        }
    }

    public void plot(Graphics g, int x, int y) {
        g.fillRect(x, y, 2, 2);
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
    private static final float RIVER_STROKE_W = 50f;
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

    // ======== Transform offsets ========
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

    // ===== Reused objects =====
    private final BasicStroke outlineStroke = new BasicStroke(OUTLINE_STROKE_W);
    private final BasicStroke mountainStroke = new BasicStroke(MOUNTAIN_OUTLINE_W);
    private final BasicStroke riverStroke = new BasicStroke(RIVER_STROKE_W, BasicStroke.CAP_ROUND,
            BasicStroke.JOIN_ROUND);
    private final BasicStroke riverEdgeStroke = new BasicStroke(RIVER_EDGE_W);
    private final GradientPaint skyPaint = new GradientPaint(
            0, 0, new Color(SKY_TOP_R, SKY_TOP_G, SKY_TOP_B),
            0, H, new Color(SKY_BOT_R, SKY_BOT_G, SKY_BOT_B));

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DrawKomodo panel = new DrawKomodo();
            JFrame f = new JFrame("Komodo • Threaded Animation (Eye-centered Zoom)");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setContentPane(panel);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            // start game loop thread
            new Thread(panel, "AnimationLoop").start();
        });
    }

    public DrawKomodo() {
        setPreferredSize(new Dimension(W, H));
        setBackground(Color.WHITE);
        setDoubleBuffered(true); // ใช้ Swing double buffering แทนการสร้าง BufferedImage ทุกเฟรม
    }

    // === API ควบคุมจุดซูม ===
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
            double dt = (now - lastTime) / 1000.0; // seconds
            lastTime = now;

            // update time
            elapsedSec += dt;

            // schedule a repaint on EDT
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

        // quality
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // SKY (screen space) — reused GradientPaint
        drawSkyScreenSpace(g2);

        // Zoom timing with constants
        double t = 0.0;
        if (elapsedSec >= ZOOM_DELAY_SEC) {
            t = Math.min(1.0, Math.max(0.0, (elapsedSec - ZOOM_DELAY_SEC) / ZOOM_DURATION_SEC));
            t = t * t * (3 - 2 * t); // smoothstep
        }
        double zoom = ZOOM_START + (ZOOM_END - ZOOM_START) * t;

        Point2D target = (zoomTarget != null ? zoomTarget : eyeCenterPanel);
        double cx = (target != null ? target.getX() + zoomOffsetX : W / 2.0);
        double cy = (target != null ? target.getY() + zoomOffsetY : H / 2.0);

        // ให้เฟรมแรก ๆ ค่อย ๆ ดึง "ตา" เข้าศูนย์กลาง
        double fx = (1.0 - t) * (W / 2.0) + t * cx;
        double fy = (1.0 - t) * (H / 2.0) + t * cy;

        AffineTransform view = new AffineTransform();
        view.translate(W / 2.0, H / 2.0);
        view.scale(zoom, zoom);
        view.translate(-fx, -fy);

        Graphics2D worldG = (Graphics2D) g2.create();
        worldG.transform(view);

        // WORLD (อยู่ใน world space): ภูเขา แม่น้ำ คอมโด
        drawBackgroundWorld(worldG); // ภูเขา+แม่น้ำ
        drawColorKom(worldG); // คอมโด

        // popup '?'
        if (elapsedSec >= ZOOM_DELAY_SEC && eyeCenterPanel != null) {
            double appear = Math.min(1.0, Math.max(0.0, (elapsedSec - ZOOM_DELAY_SEC) / POPUP_FADE_IN));
            drawQuestionMark(worldG, eyeCenterPanel, appear, elapsedSec);
        }

        worldG.dispose();
        g2.dispose();
    }

    // ---------- SKY ---------
    private void drawSkyScreenSpace(Graphics2D g2) {
        g2.setComposite(AlphaComposite.Src);
        g2.setPaint(skyPaint); // reused object
        g2.fillRect(0, 0, W, H);
        g2.setComposite(AlphaComposite.SrcOver);
    }

    // ---------- ภูเขา+แม่น้ำ (world-space, โดนซูม) ----------
    private void drawBackgroundWorld(Graphics2D g2) {
        int S = 600;

        // Mountain range 1 (far)
        Path2D m1 = new Path2D.Double();
        Point2D a0 = new Point2D.Double(-50, 420);
        Point2D a1 = new Point2D.Double(80, 300);
        Point2D a2 = new Point2D.Double(180, 360);
        Point2D a3 = new Point2D.Double(260, 320);
        Path2D segA = cubicBezierPath(a0, a1, a2, a3, S);

        Point2D b0 = a3;
        Point2D b1 = new Point2D.Double(320, 260);
        Point2D b2 = new Point2D.Double(420, 360);
        Point2D b3 = new Point2D.Double(520, 300);
        Path2D segB = cubicBezierPath(b0, b1, b2, b3, S);

        Point2D c0 = b3;
        Point2D c1 = new Point2D.Double(560, 280);
        Point2D c2 = new Point2D.Double(640, 360);
        Point2D c3 = new Point2D.Double(700, 420);
        Path2D segC = cubicBezierPath(c0, c1, c2, c3, S);

        m1.append(segA, false);
        m1.append(segB, true);
        m1.append(segC, true);
        m1.lineTo(W, H);
        m1.lineTo(0, H);
        m1.closePath();

        g2.setColor(new Color(90, 105, 120));
        g2.fill(m1);
        g2.setColor(new Color(60, 72, 84));
        g2.setStroke(mountainStroke); // reused stroke
        g2.draw(m1);

        // Mountain range 2 (near)
        Path2D m2 = new Path2D.Double();
        Point2D d0 = new Point2D.Double(-80, 480);
        Point2D d1 = new Point2D.Double(60, 380);
        Point2D d2 = new Point2D.Double(160, 440);
        Point2D d3 = new Point2D.Double(260, 400);
        Path2D segD = cubicBezierPath(d0, d1, d2, d3, S);

        Point2D e0 = d3;
        Point2D e1 = new Point2D.Double(330, 350);
        Point2D e2 = new Point2D.Double(420, 440);
        Point2D e3 = new Point2D.Double(520, 380);
        Path2D segE = cubicBezierPath(e0, e1, e2, e3, S);

        Point2D f0 = e3;
        Point2D f1 = new Point2D.Double(580, 360);
        Point2D f2 = new Point2D.Double(660, 430);
        Point2D f3 = new Point2D.Double(720, 480);
        Path2D segF = cubicBezierPath(f0, f1, f2, f3, S);

        m2.append(segD, false);
        m2.append(segE, true);
        m2.append(segF, true);
        m2.lineTo(W, H);
        m2.lineTo(0, H);
        m2.closePath();

        g2.setColor(new Color(120, 135, 150));
        g2.fill(m2);
        g2.setColor(new Color(80, 92, 104));
        g2.draw(m2);

        // River
        Point2D r0 = new Point2D.Double(-40, 520);
        Point2D r1 = new Point2D.Double(120, 560);
        Point2D r2 = new Point2D.Double(260, 520);
        Point2D r3 = new Point2D.Double(360, 560);
        Path2D rSeg1 = cubicBezierPath(r0, r1, r2, r3, 500);

        Point2D r4 = new Point2D.Double(460, 600);
        Point2D r5 = new Point2D.Double(540, 540);
        Point2D r6 = new Point2D.Double(660, 560);
        Path2D rSeg2 = cubicBezierPath(r3, r4, r5, r6, 500);

        Path2D riverCenter = new Path2D.Double();
        riverCenter.append(rSeg1, false);
        riverCenter.append(rSeg2, true);

        Shape river = riverStroke.createStrokedShape(riverCenter); // reused base stroke

        g2.setColor(new Color(90, 155, 210));
        g2.fill(river);
        g2.setColor(new Color(200, 230, 255, 140));
        g2.setStroke(riverEdgeStroke); // reused edge stroke
        g2.draw(river);
    }

    // ===== Bézier helper =====
    private static Path2D cubicBezierPath(Point2D p0, Point2D p1, Point2D p2, Point2D p3, int samples) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(p0.getX(), p0.getY());
        for (int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            double u = 1.0 - t;
            double b0 = u * u * u;
            double b1 = 3 * u * u * t;
            double b2 = 3 * u * t * t;
            double b3 = t * t * t;
            double x = b0 * p0.getX() + b1 * p1.getX() + b2 * p2.getX() + b3 * p3.getX();
            double y = b0 * p0.getY() + b1 * p1.getY() + b2 * p2.getY() + b3 * p3.getY();
            path.lineTo(x, y);
        }
        return path;
    }

    // ===== Komodo parts =====
    public static Shape buildShape0Path() {
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

    // ===== วาดคอมโด + เก็บตำแหน่ง "ตา" ไว้ใช้โฟกัสซูม =====
    private void draw(Graphics2D g2, Color c, Color eyeColor) {
        Path2D s0 = (Path2D) buildShape0Path();
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

        Area group = new Area(shape0Local);
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
        double groupCY = groupRect.getCenterY();

        double panelCenterX = W / 2.0, panelCenterY = H / 2.0;

        AffineTransform centerPanel = new AffineTransform();
        centerPanel.translate(panelCenterX, panelCenterY);
        centerPanel.scale(8.0, 8.0); // ขนาดคอมโดรวม
        centerPanel.translate(-groupCX, -groupCY);

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

        g2.setStroke(outlineStroke); // reused outline stroke

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

        // cache ตา (พิกัด panel-space หลัง centerPanel)
        Rectangle2D eyeB = shape9.getBounds2D();
        this.eyeCenterPanel = new Point2D.Double(eyeB.getCenterX(), eyeB.getCenterY());
    }

    private void drawColorKom(Graphics2D g2) {
        draw(g2, new Color(75, 83, 32), new Color(218, 112, 214));
    }

    // ===== popup '?' แบบลอยค้าง ไม่มีกรอบ =====
    private void drawQuestionMark(Graphics2D g2, Point2D head, double alpha, double timeSec) {
        // 0→1 ใน ~0.4s แล้วค้าง
        double t = Math.min(1.0, Math.max(0.0, (elapsedSec - ZOOM_DELAY_SEC) / POPUP_FADE_IN));
        t = t * t * (3 - 2 * t);
        float a = (float) Math.max(alpha, t);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));

        double scale = 0.6 + 0.4 * t;
        double baseX = head.getX() + POPUP_OFFSET_X;
        double baseY = head.getY() + POPUP_OFFSET_Y;

        String q = "?";
        Font f = g2.getFont().deriveFont(Font.BOLD, (float) (POPUP_FONT_SIZE * scale));

        AffineTransform old = g2.getTransform();
        g2.translate(baseX, baseY);
        g2.scale(scale, scale);
        g2.setColor(Color.BLACK);
        g2.setFont(f);
        g2.drawString(q, 0, 0);
        g2.setTransform(old);

        g2.setComposite(AlphaComposite.SrcOver);
    }
}


/**
 * เล่น 3 ฉากต่อเนื่องในหน้าต่างเดียว:
 * 1) FPS_Coding -> 2) BallDrop -> 3) DrawKomodo
 *
 * การทำงาน:
 * - ใช้ CardLayout สลับ JPanel ทีละฉาก
 * - ใช้ Swing Timer จับเวลาความยาวแต่ละฉาก
 * - ถ้า panel ใด implements Runnable จะ start thread ให้อัตโนมัติเมื่อถึงคิว
 *
 * หมายเหตุ:
 * - โค้ดนี้ "ไม่แก้" ไฟล์เดิมของคุณ (FPS_Coding, BallDrop, DrawKomodo)
 * - ถ้าคุณอยากให้หยุดเธรดเมื่อจบฉากจริง ๆ แนะนำเพิ่ม method stop() ในคลาสเดิม
 * แล้วแก้ TODO: callStopIfPresent(panel) ให้เรียก stop() นั้น
 */
public class Animation_t1 {

    // === ตั้งเวลาความยาวต่อฉาก (มิลลิวินาที) ปรับได้ตามต้องการ ===
    private static final int DUR_FPS_MS = 10000; // 8 วินาทีสำหรับ FPS_Coding
    private static final int DUR_BALL_MS = 4500; // 4.5 วินาทีสำหรับ BallDrop
    // ฉากสุดท้าย (Komodo) ปล่อยให้เล่นต่อไปเรื่อย ๆ (ไม่ตั้ง Timer เพิ่ม)

    // === ชื่อการ์ดของแต่ละฉาก ===
    private static final String CARD_FPS = "scene_fps";
    private static final String CARD_BALL = "scene_ball";
    private static final String CARD_KOMODO = "scene_komodo";

    private final JFrame frame;
    private final JPanel cardRoot;
    private final CardLayout cards;

    // Panels ของแต่ละฉาก (ประกาศเป็น Component เพื่อยืดหยุ่น)
    private Component fpsPanel;
    private Component ballPanel;
    private Component komodoPanel;

    public Animation_t1() {
        frame = new JFrame("One Window • Scene Sequencer (FPS → Ball → Komodo)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        cards = new CardLayout();
        cardRoot = new JPanel(cards);
        frame.setContentPane(cardRoot);

        // === สร้างแต่ละฉาก ===
        fpsPanel = createScenePanel(FPS_Coding.class); 
        ballPanel = createScenePanel(BallDrop.class);
        komodoPanel = createScenePanel(DrawKomodo.class); 

        // === เพิ่มลงการ์ด ===
        cardRoot.add(fpsPanel, CARD_FPS);
        cardRoot.add(ballPanel, CARD_BALL);
        cardRoot.add(komodoPanel, CARD_KOMODO);

        frame.pack(); // แต่ละ panel ควร setPreferredSize ไว้แล้ว
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        // === เริ่มที่ฉาก 1 (FPS) ===
        showAndStart(CARD_FPS, fpsPanel);

        // === ตั้ง Timer ให้ไปฉาก 2 ===
        new javax.swing.Timer(DUR_FPS_MS, e -> {
            // TODO: ถ้ามี stop() ใน FPS_Coding ให้เรียกผ่าน callStopIfPresent(fpsPanel);
            showAndStart(CARD_BALL, ballPanel);

            // ตั้ง Timer ต่อให้ไปฉาก 3
            new javax.swing.Timer(DUR_BALL_MS, e2 -> {
                // TODO: ถ้ามี stop() ใน BallDrop ให้เรียกผ่าน callStopIfPresent(ballPanel);
                showAndStart(CARD_KOMODO, komodoPanel);

                // ฉากสุดท้ายไม่ตั้ง timer ต่อ ปล่อยเล่นไปยาว ๆ
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

    /**
     * สร้างอินสแตนซ์ panel ของฉากจากคลาสที่ให้มา
     * - รองรับคลาสที่เป็น JPanel โดยตรง
     * - ถ้าคลาสไม่มี constructor เปล่า จะ throw เพื่อให้เห็นปัญหาเร็ว
     */
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

    /** แสดงการ์ด + start thread ถ้า panel นั้น implements Runnable */
    private void showAndStart(String cardName, Component panel) {
        cards.show(cardRoot, cardName);

        // ถ้า panel เป็น Runnable จะ start เธรดให้ (บางคลาสของคุณมี loop ภายใน)
        if (panel instanceof Runnable) {
            // หลีกเลี่ยง start ซ้ำ: ผูก thread กับ panel ผ่าน client property
            if (panel instanceof JComponent jc) {
                Thread t = (Thread) jc.getClientProperty("scene-thread");
                if (t == null || !t.isAlive()) {
                    t = new Thread((Runnable) panel, panel.getClass().getSimpleName() + "-Thread");
                    jc.putClientProperty("scene-thread", t);
                    t.start();
                }
            } else {
                // กรณีไม่ใช่ JComponent ก็ start ตรง ๆ (จะหยุดยากหน่อย)
                new Thread((Runnable) panel, panel.getClass().getSimpleName() + "-Thread").start();
            }
        }

    }


    // === main ===
    public static void main(String[] args) {
        // ให้ทำงานบน EDT
        SwingUtilities.invokeLater(Animation_t1::new);
    }
}