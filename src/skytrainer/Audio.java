package skytrainer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Tiny procedural synth: one-shot SFX voices + continuous engine / wind / stall beeper
 * driven by the aircraft state, plus sparse pentatonic menu music.
 */
public final class Audio {
    private static final int RATE = 44100;

    private static final int SINE = 0, NOISE = 1, SQUARE = 2, SAW = 3;

    private static final class Voice {
        int kind;
        float f0, f1, vol, attack, dur, release, lp;
        double t;
        float lpState;
    }

    private final List<Voice> voices = new ArrayList<>();
    private final Random rng = new Random();
    private SourceDataLine line;
    private Thread thread;
    private volatile boolean running;
    public volatile float master = 0.8f;
    public volatile float musicVol = 1f;

    // continuous state, set from the game thread each frame
    public volatile float rpm = 0;          // 0..~2900
    public volatile float airspeed = 0;     // m/s
    public volatile boolean stallWarn = false;
    public volatile boolean inFlight = false;

    private double clock;
    private double nextNoteAt = 2.0;
    private int lastNote = 3;
    private float windState;
    private float enginePhase, enginePhase2, bladePhase, lfoState;
    private float stallBeepPhase;

    private static final float[] SCALE = {261.63f, 293.66f, 329.63f, 392.00f, 440.00f, 523.25f, 587.33f, 659.26f};

    public synchronized void start() {
        try {
            AudioFormat fmt = new AudioFormat(RATE, 16, 1, true, false);
            line = AudioSystem.getSourceDataLine(fmt);
            line.open(fmt, 8192);
            line.start();
        } catch (Throwable t) {
            line = null;
            return;
        }
        running = true;
        thread = new Thread(this::run, "SkyTrainer-Audio");
        thread.setDaemon(true);
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) thread.interrupt();
        if (line != null) { line.close(); line = null; }
    }

    private Voice newVoice(int kind, float f0, float f1, float vol, float attack, float dur, float release, float lp) {
        Voice v = new Voice();
        v.kind = kind; v.f0 = f0; v.f1 = f1; v.vol = vol; v.attack = attack; v.dur = dur; v.release = release; v.lp = lp;
        voices.add(v);
        return v;
    }

    // ------------------------------------------------------------------ SFX
    public void click() {
        if (!running) return;
        synchronized (voices) {
            newVoice(NOISE, 0, 0, 0.5f, 0.001f, 0.02f, 0.02f, 0.25f);
            newVoice(SINE, 520, 360, 0.35f, 0.001f, 0.045f, 0.03f, 1);
        }
    }

    public void touchdown(float hardness) {
        if (!running) return;
        synchronized (voices) {
            newVoice(NOISE, 0, 0, 0.4f + 0.3f * hardness, 0.002f, 0.09f, 0.08f, 0.12f);
            newVoice(SINE, 95, 55, 0.5f, 0.002f, 0.10f, 0.09f, 1);
        }
    }

    public void crash() {
        if (!running) return;
        synchronized (voices) {
            newVoice(NOISE, 0, 0, 1.0f, 0.002f, 0.5f, 0.6f, 0.05f);
            newVoice(SAW, 120, 32, 0.7f, 0.002f, 0.4f, 0.7f, 0.08f);
            newVoice(SINE, 70, 28, 0.8f, 0.002f, 0.5f, 0.8f, 1);
        }
    }

    // ---------------------------------------------------------------- mixer
    private void run() {
        short[] samples = new short[2048];
        byte[] bytes = new byte[samples.length * 2];
        while (running) {
            for (int i = 0; i < samples.length; i++) samples[i] = mixOne();
            for (int i = 0; i < samples.length; i++) {
                bytes[2 * i] = (byte) samples[i];
                bytes[2 * i + 1] = (byte) (samples[i] >> 8);
            }
            int written = line.write(bytes, 0, bytes.length);
            if (written < 0) break;
        }
    }

    private short mixOne() {
        clock += 1.0 / RATE;
        float s = 0;
        s += engine();
        s += wind();
        s += stallBeeper();
        synchronized (voices) {
            for (int i = voices.size() - 1; i >= 0; i--) {
                Voice v = voices.get(i);
                float env;
                if (v.t < v.attack) env = (float) (v.t / Math.max(v.attack, 1e-5f));
                else if (v.t < v.dur) env = 1;
                else env = (float) Math.max(0, 1 - (v.t - v.dur) / Math.max(v.release, 1e-5f));
                if (v.t > v.dur + v.release) { voices.remove(i); continue; }
                float phase = (float) (v.t * (v.f0 + (v.f1 - v.f0) * Math.min(v.t / Math.max(v.dur, 1e-5f), 1f)));
                float sample;
                switch (v.kind) {
                    case NOISE:
                        float white = rng.nextFloat() * 2 - 1;
                        v.lpState += (white - v.lpState) * v.lp;
                        sample = v.lpState * 2.2f;
                        break;
                    case SQUARE:
                        sample = (phase % 1f) < 0.5f ? 0.5f : -0.5f;
                        break;
                    case SAW:
                        sample = (phase % 1f) * 2f - 1f;
                        break;
                    default:
                        sample = (float) Math.sin(2 * Math.PI * phase);
                }
                s += sample * env * v.vol;
                v.t += 1.0 / RATE;
            }
        }
        s += musicTick();
        float out = s * master;
        if (out > 1) out = 1;
        if (out < -1) out = -1;
        return (short) (out * 32000);
    }

    /** Continuous engine: two detuned saw-ish oscillators + blade slap, low-passed. */
    private float engine() {
        float norm = Math.max(0, Math.min(1, (rpm - 500) / 2300f));
        if (norm <= 0.001f) return 0;
        float f0 = 38f + norm * 68f;                 // firing frequency
        enginePhase += f0 / RATE;
        enginePhase2 += (f0 * 1.51f + 2.3f) / RATE;  // harmonic-ish detune
        bladePhase += (f0 * 2f) / RATE;              // prop blade pass
        float saw1 = (enginePhase % 1f) * 2f - 1f;
        float saw2 = (enginePhase2 % 1f) * 2f - 1f;
        float sub = (float) Math.signum(Math.sin(2 * Math.PI * enginePhase * 0.5f)) * 0.4f;
        float mix = saw1 * 0.55f + saw2 * 0.22f + sub;
        // low-pass by rpm (smoother at cruise)
        float lp = 0.055f + norm * 0.16f;
        lfoState += (mix - lfoState) * lp;
        // blade slap tremolo
        float slap = 0.75f + 0.25f * (bladePhase % 1f < 0.5f ? 1f : -1f);
        float vol = 0.045f + norm * 0.075f;
        return lfoState * slap * vol;
    }

    /** Wind rush grows with airspeed. */
    private float wind() {
        float white = rng.nextFloat() * 2 - 1;
        windState += (white - windState) * 0.015f;
        float a = Math.min(1, airspeed / 85f);
        float gain = 0.012f * a * a * (inFlight ? 1.6f : 1f);
        float lfo = 0.7f + 0.3f * (float) Math.sin(clock * 0.9 + Math.sin(clock * 0.23) * 2);
        return windState * 1.6f * gain * lfo;
    }

    /** Stall warning horn: 880 Hz square, beeping ~6 Hz. */
    private float stallBeeper() {
        if (!stallWarn) return 0;
        stallBeepPhase += 6f / RATE;
        boolean on = (stallBeepPhase % 1f) < 0.5f;
        if (!on) return 0;
        return ((clock * 880) % 1f < 0.5f ? 0.4f : -0.4f) * 0.10f;
    }

    /** Sparse pentatonic notes (menus, quiet in flight). */
    private float musicTick() {
        float volScale = musicVol * (inFlight ? 0.25f : 1f);
        if (volScale <= 0.001f) return 0;
        if (clock >= nextNoteAt) {
            int steps = 1 + rng.nextInt(3);
            double t = clock;
            int n = lastNote;
            for (int i = 0; i < steps; i++) {
                n = Math.max(0, Math.min(SCALE.length - 1, n + rng.nextInt(5) - 2));
                float f = SCALE[n];
                float vol = 0.12f * (0.7f + 0.3f * rng.nextFloat());
                float dur = 1.1f + rng.nextFloat() * 1.1f;
                scheduleNote(f, vol, dur, t);
                if (rng.nextBoolean()) scheduleNote(f / 2, vol * 0.45f, dur * 1.4f, t);
                t += 0.35f + rng.nextFloat() * 0.6f;
            }
            lastNote = n;
            nextNoteAt = clock + (t - clock) + 2.2 + rng.nextDouble() * 4.0;
        }
        for (int i = pending.size() - 1; i >= 0; i--) {
            DelayedNote dn = pending.get(i);
            if (clock >= dn.when) {
                synchronized (voices) {
                    newVoice(SINE, dn.f, dn.f, dn.vol * volScale, 0.35f, dn.dur, 1.6f, 1);
                    newVoice(SINE, dn.f * 2, dn.f * 2, dn.vol * 0.22f * volScale, 0.5f, dn.dur * 0.8f, 1.4f, 1);
                }
                pending.remove(i);
            }
        }
        return 0;
    }

    private void scheduleNote(float f, float vol, float dur, double when) {
        DelayedNote dn = new DelayedNote();
        dn.f = f; dn.vol = vol; dn.dur = dur; dn.when = when;
        pending.add(dn);
    }

    private static final class DelayedNote {
        float f, vol, dur;
        double when;
    }

    private final List<DelayedNote> pending = new ArrayList<>();
}
