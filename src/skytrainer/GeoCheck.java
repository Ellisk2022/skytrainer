package skytrainer;

/** Headless geometry sanity dump: height map + built-tile stats. */
public final class GeoCheck {
    public static void main(String[] args) {
        long seed = 0xA17C0AF7L;
        Terrain.NO_GL = true;
        Terrain t = new Terrain(seed, null);
        int step = 200;
        for (int z = -3000; z <= 3000; z += step) {
            StringBuilder sb = new StringBuilder();
            for (int x = -3000; x <= 3000; x += step) {
                float h = t.height(x, z);
                char c;
                if (t.onRunway(x, z)) c = 'R';
                else if (h < -0.5f) c = '~';
                else if (h < 6) c = '.';
                else if (h < 60) c = 'g';
                else if (h < 200) c = 'h';
                else if (h < 450) c = 'm';
                else c = '^';
                sb.append(c);
            }
            System.out.println(sb);
        }
        // build tiles around origin like the game does, then dump stats
        for (int i = 0; i < 40; i++) t.stream(0, 0, 4, 9);
        StringBuilder sb2 = new StringBuilder();
        for (int z = -128; z <= 128; z += 16) sb2.append(String.format("%.1f ", t.height(0, z)));
        System.out.println("heights along z at x=0: " + sb2);
        StringBuilder sb3 = new StringBuilder();
        for (int x = -128; x <= 128; x += 16) sb3.append(String.format("%.1f ", t.height(x, 500)));
        System.out.println("heights along x at z=500: " + sb3);
    }
}
