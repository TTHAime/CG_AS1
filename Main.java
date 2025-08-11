import javax.swing.*;
import java.awt.*;
import java.util.Timer;
import java.util.TimerTask;


public class Main {
    // Duration per scene (milliseconds). Adjust to match your desired lengths.
    private static final long SCENE_1_MS = 9000;  // FPS_Coding on screen duration
    private static final long SCENE_2_MS = 7000;  // BallDrop on screen duration

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Animation Sequence (Single Window)");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(600, 600);  // matches H/W in BallDrop
            frame.setLocationRelativeTo(null);

            CardLayout card = new CardLayout();
            JPanel root = new JPanel(card);

            FPS_Coding scene1 = new FPS_Coding();
            BallDrop scene2 = new BallDrop();

            root.add(scene1, "scene1");
            root.add(scene2, "scene2");

            frame.setContentPane(root);
            frame.setVisible(true);

            // Start scene 1
            Thread t1 = new Thread(scene1, "Scene-1-FPS_Coding");
            t1.setDaemon(true);
            t1.start();

            // After SCENE_1_MS, switch to scene 2
            Timer switchTimer = new Timer("SequenceTimer", true);
            switchTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    SwingUtilities.invokeLater(() -> {
                        card.show(root, "scene2");
                        // Start scene 2
                        Thread t2 = new Thread(scene2, "Scene-2-BallDrop");
                        t2.setDaemon(true);
                        t2.start();
                    });
                }
            }, SCENE_1_MS);

            // After SCENE_1_MS + SCENE_2_MS, optionally finish (or loop, add more scenes, etc.)
            switchTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    // Close the window, or comment this out if you want it to keep running
                    SwingUtilities.invokeLater(() -> {
                        frame.dispose();
                        System.exit(0);
                    });
                }
            }, SCENE_1_MS + SCENE_2_MS);
        });
    }
}
