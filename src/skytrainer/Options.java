package skytrainer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/** Player-facing settings, persisted to options.txt. */
public final class Options {
    public float fov = 70f;               // 50..100
    public int renderDistance = 10;       // terrain tiles 4..20
    public float sensitivity = 1.0f;      // 0..2
    public float masterVolume = 0.8f;     // 0..1
    public boolean music = true;
    public boolean fog = true;
    public boolean clouds = true;
    public boolean invertY = false;       // false = mouse down pulls nose up (stick style)
    public float dayLength = 12f;         // minutes per full day, 1..40
    public float startClock = 9.5f;       // 0..24 h; slider "Time"

    public static File file() { return new File("options.txt"); }

    public static Options load() {
        Options o = new Options();
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(file())) {
            p.load(in);
        } catch (IOException ignored) { }
        o.fov = clampF(p.getProperty("fov", "70"), 50, 100, 70);
        o.renderDistance = (int) clampF(p.getProperty("renderDistance", "10"), 4, 20, 10);
        o.sensitivity = clampF(p.getProperty("sensitivity", "1.0"), 0.05f, 2, 1);
        o.masterVolume = clampF(p.getProperty("masterVolume", "0.8"), 0, 1, 0.8f);
        o.music = Boolean.parseBoolean(p.getProperty("music", "true"));
        o.fog = Boolean.parseBoolean(p.getProperty("fog", "true"));
        o.clouds = Boolean.parseBoolean(p.getProperty("clouds", "true"));
        o.invertY = Boolean.parseBoolean(p.getProperty("invertY", "false"));
        o.dayLength = clampF(p.getProperty("dayLength", "12"), 1, 40, 12);
        o.startClock = clampF(p.getProperty("startClock", "9.5"), 0, 24, 9.5f);
        return o;
    }

    private static float clampF(String s, float min, float max, float def) {
        try {
            float v = Float.parseFloat(s.trim());
            return Math.max(min, Math.min(max, v));
        } catch (Exception e) {
            return def;
        }
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("fov", String.valueOf(fov));
        p.setProperty("renderDistance", String.valueOf(renderDistance));
        p.setProperty("sensitivity", String.valueOf(sensitivity));
        p.setProperty("masterVolume", String.valueOf(masterVolume));
        p.setProperty("music", String.valueOf(music));
        p.setProperty("fog", String.valueOf(fog));
        p.setProperty("clouds", String.valueOf(clouds));
        p.setProperty("invertY", String.valueOf(invertY));
        p.setProperty("dayLength", String.valueOf(dayLength));
        p.setProperty("startClock", String.valueOf(startClock));
        try (FileOutputStream out = new FileOutputStream(file())) {
            p.store(out, "SkyTrainer options");
        } catch (IOException ignored) { }
    }

    public static String formatFov(float f) { return "FOV: " + (int) f; }
}
