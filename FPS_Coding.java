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
public class FPS_Coding extends JPanel implements Runnable {

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
     *  Update Logic (แกนของแอนิเมชัน)
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
                "ah hehe" ,
                "CG",
                "Bézier",
                "Midpoint" ,
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

    /*
     * ========================= 
     * Main (สร้างหน้าต่าง + เริ่มลูป)
     * =========================
     */

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
