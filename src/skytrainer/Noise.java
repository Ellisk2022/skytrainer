package skytrainer;

import java.util.Random;

/** Seeded 2D Perlin noise + fractal helpers used by the terrain generator. */
public final class Noise {
    private final int[] perm = new int[512];
    public final long seed;

    public Noise(long seed) {
        this.seed = seed;
        Random r = new Random(seed);
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = p[i]; p[i] = p[j]; p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    private static float fade(float t) { return t * t * t * (t * (t * 6 - 15) + 10); }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private float grad(int hash, float x, float y) {
        switch (hash & 7) {
            case 0: return x + y;
            case 1: return -x + y;
            case 2: return x - y;
            case 3: return -x - y;
            case 4: return x;
            case 5: return -x;
            case 6: return y;
            default: return -y;
        }
    }

    public float noise(float x, float y) {
        int xi = (int) Math.floor(x) & 255, yi = (int) Math.floor(y) & 255;
        float xf = x - (float) Math.floor(x), yf = y - (float) Math.floor(y);
        float u = fade(xf), v = fade(yf);
        int aa = perm[perm[xi] + yi], ab = perm[perm[xi] + yi + 1];
        int ba = perm[perm[xi + 1] + yi], bb = perm[perm[xi + 1] + yi + 1];
        float x1 = lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u);
        float x2 = lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u);
        return lerp(x1, x2, v);
    }

    public float fbm(float x, float y, int octaves) {
        float sum = 0, amp = 1, freq = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += noise(x * freq, y * freq) * amp;
            norm += amp;
            amp *= 0.5f;
            freq *= 2f;
        }
        return sum / norm;
    }

    /** Ridged fbm in [0,1]: sharp mountain crests. */
    public float ridged(float x, float y, int octaves) {
        float sum = 0, amp = 0.55f, freq = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            float n = 1f - Math.abs(noise(x * freq, y * freq));
            sum += n * n * amp;
            norm += amp;
            amp *= 0.55f;
            freq *= 2.1f;
        }
        return sum / norm;
    }

    /** Deterministic hash -> [0,1) from two coords + seed. */
    public static float hash2(long seed, int x, int y) {
        long h = x * 341873128712L + y * 132897987541L + seed;
        h ^= h >> 13;
        h *= 0x5BD1E995;
        h ^= h >> 15;
        return (h & 0x7FFFFFFF) / (float) 0x80000000;
    }
}
