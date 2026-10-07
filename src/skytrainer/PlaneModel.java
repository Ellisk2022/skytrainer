package skytrainer;

/** Procedural high-wing prop trainer (Cessna-style), built from colored boxes. Body axes: +X nose, +Y up, +Z right. */
public final class PlaneModel {
    public final Mesh body;
    public final Mesh prop;
    public final Mesh disc;   // translucent prop disc while running

    public static final float GEAR_H = 1.35f;      // CG height above ground on wheels
    public static final float PROXIMITY = 1.1f;    // belly clearance

    private static final float[] WHITE = {0.93f, 0.93f, 0.95f};
    private static final float[] RED = {0.78f, 0.13f, 0.13f};
    private static final float[] GLASS = {0.20f, 0.34f, 0.52f};
    private static final float[] DARK = {0.16f, 0.16f, 0.18f};
    private static final float[] SILVER = {0.75f, 0.76f, 0.80f};

    public PlaneModel() {
        MeshBuilder mb = new MeshBuilder();
        buildFuselage(mb);
        buildWing(mb);
        buildTail(mb);
        buildGear(mb);
        body = mb.build();

        MeshBuilder pb = new MeshBuilder();
        // two blades along Z, spinner hub
        pb.addBox(0, 0, 0.75f, 0.16f, 0.10f, 1.55f, DARK[0], DARK[1], DARK[2]);
        pb.addBox(0.02f, 0, 0, 0.22f, 0.22f, 0.24f, SILVER[0], SILVER[1], SILVER[2]);
        prop = pb.build();

        MeshBuilder db = new MeshBuilder();
        // prop disc: quad facing +X (normal along X), semi-transparent gray
        float d = 1.85f;
        db.addQuad(0, -d, -d, 0, -d, d, 0, d, d, 0, d, -d, 1, 0, 0, 0.75f, 0.78f, 0.82f);
        disc = db.build();
    }

    private void box(MeshBuilder mb, float cx, float cy, float cz, float sx, float sy, float sz, float[] c) {
        mb.addBox(cx, cy, cz, sx, sy, sz, c[0], c[1], c[2]);
    }

    private void buildFuselage(MeshBuilder mb) {
        // cabin (widest)
        box(mb, 0.3f, 0, 0, 2.6f, 1.30f, 1.14f, WHITE);
        // nose / engine cowling, slightly smaller and lower
        box(mb, 2.0f, -0.08f, 0, 1.4f, 1.06f, 0.98f, WHITE);
        // red cowl stripe
        box(mb, 2.68f, -0.08f, 0, 0.10f, 1.07f, 0.99f, RED);
        // tail boom tapering back
        box(mb, -1.9f, 0.05f, 0, 2.6f, 0.72f, 0.62f, WHITE);
        box(mb, -3.6f, 0.14f, 0, 1.1f, 0.44f, 0.36f, WHITE);
        // side stripe (two long thin boxes slightly proud of the skin)
        box(mb, -0.8f, -0.42f, 0.585f, 5.4f, 0.16f, 0.03f, RED);
        box(mb, -0.8f, -0.42f, -0.585f, 5.4f, 0.16f, 0.03f, RED);
        // windshield + cabin windows
        box(mb, 1.15f, 0.52f, 0, 0.5f, 0.55f, 1.16f, GLASS);
        box(mb, 0.15f, 0.35f, 0.578f, 1.1f, 0.42f, 0.03f, GLASS);
        box(mb, 0.15f, 0.35f, -0.578f, 1.1f, 0.42f, 0.03f, GLASS);
    }

    private void buildWing(MeshBuilder mb) {
        // high wing with slight dihedral: two halves
        for (int s = -1; s <= 1; s += 2) {
            MeshBuilder w = new MeshBuilder();
            float halfSpan = 5.5f, chord = 1.55f, thick = 0.17f;
            float y0 = 0.78f;
            // approximate dihedral with a rotated slab: center root at (0.35, y0, 0), tip at (0.35, y0+0.55, s*halfSpan)
            float tipY = y0 + 0.55f;
            float rootY = y0;
            float midY = (rootY + tipY) / 2f;
            // slab as a flattened box rotated about X — approximate with 3 segments
            int seg = 3;
            for (int i = 0; i < seg; i++) {
                float t0 = i / (float) seg, t1 = (i + 1) / (float) seg;
                float za = s * halfSpan * t0, zb = s * halfSpan * t1;
                float ya = rootY + (tipY - rootY) * t0, yb = rootY + (tipY - rootY) * t1;
                float zc = (za + zb) / 2f;
                float yc = (ya + yb) / 2f;
                float len = Math.abs(zb - za);
                box(mb, 0.35f, yc + 0.06f, zc, chord, thick, len, WHITE);
                // red wingtip on last segment
                if (i == seg - 1) box(mb, 0.35f, yb + 0.065f, zb - s * 0.28f, chord * 0.92f, thick * 1.06f, 0.56f, RED);
            }
            // strut
            box(mb, 0.1f, -0.35f, s * 1.95f, 0.09f, 1.5f, 0.09f, SILVER);
            box(mb, 0.1f, 0.12f, s * 2.6f, 0.08f, 1.35f, 0.08f, SILVER);
        }
    }

    private void buildTail(MeshBuilder mb) {
        // horizontal stabilizer
        box(mb, -4.0f, 0.30f, 0, 1.05f, 0.10f, 3.4f, WHITE);
        // vertical fin
        box(mb, -4.05f, 0.85f, 0, 1.0f, 1.25f, 0.10f, WHITE);
        // rudder tip stripe
        box(mb, -4.45f, 1.28f, 0, 0.24f, 0.42f, 0.11f, RED);
    }

    private void buildGear(MeshBuilder mb) {
        // nose gear
        box(mb, 1.7f, -0.78f, 0, 0.10f, 0.55f, 0.10f, SILVER);
        box(mb, 1.7f, -1.12f, 0, 0.14f, 0.36f, 0.14f, DARK);
        // main gear (sprung legs angled out)
        for (int s = -1; s <= 1; s += 2) {
            box(mb, 0.35f, -0.72f, s * 0.75f, 0.12f, 0.5f, 0.12f, SILVER);
            box(mb, 0.35f, -1.08f, s * 1.05f, 0.16f, 0.42f, 0.16f, DARK);
        }
    }
}
