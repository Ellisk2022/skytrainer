package skytrainer;

import java.util.ArrayList;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Infinite procedural height-field terrain in 1 km tiles with 3 LOD rings,
 * vertex-colored biomes, baked trees, an animated ocean and the home airfield.
 * All heights are pure functions of (x, z) so streaming and collision agree.
 */
public final class Terrain {
    public static final int TILE = 1024;              // meters per tile
    public static final float RUNWAY_H = 40f;         // airfield elevation
    public static final float RUNWAY_X0 = -600, RUNWAY_X1 = 600, RUNWAY_ZHALF = 22.5f;

    private final Noise n;
    public final long seed;

    private static final int[] LOD_GRID = {64, 32, 16}; // quads per tile edge per LOD
    private static final boolean NO_TREES = System.getenv("SKY_NO_TREES") != null;
    private static final boolean NO_SKIRTS = System.getenv("SKY_NO_SKIRTS") != null;
    private static final boolean FLATCOL = System.getenv("SKY_FLATCOL") != null;
    private static final int LOD_FORCE = System.getenv("SKY_LOD_FORCE") != null
            ? Integer.parseInt(System.getenv("SKY_LOD_FORCE")) : -1;

    private static final class Tile {
        int lod;
        int tier; // color-detail tier (lod + ring distance); rebuild when it changes
        Mesh mesh;
        long lastWanted;
        float minH, maxH;
    }

    private final Map<Long, Tile> tiles = new HashMap<>();
    public static boolean NO_GL = false;
    private final List<long[]> buildQueue = new ArrayList<>(); // {tx, tz, lod}
    private final Map<Long, Integer> queuedLod = new HashMap<>();
    private long frame;

    private Mesh waterMesh;
    private Mesh runwayMesh;
    public float runwayMinX, runwayMaxX; // AABB for cull

    private final Shader shader;

    public Terrain(long seed, Shader worldShader) {
        this.seed = seed;
        this.n = new Noise(seed);
        this.shader = worldShader;
    }

    // ------------------------------------------------------------ height field
    /** Terrain elevation in meters. Pure function. */
    public float height(float x, float z) {
        float continent = n.fbm(x / 2600f, z / 2600f, 3);
        float mask = smooth(-0.06f, 0.20f, continent);          // 0 = ocean, 1 = land
        float hills = n.fbm(x / 420f, z / 420f, 4) * 55f;
        float mMask = smooth(0.10f, 0.42f, n.fbm(x / 2300f + 53f, z / 2300f - 81f, 2) + 0.25f);
        float mountains = (float) Math.pow(n.ridged(x / 1500f, z / 1500f, 4), 1.6f) * 820f * mMask;
        float detail = n.fbm(x / 60f, z / 60f, 2) * 3.5f;
        float oceanBed = -18f - 14f * (n.fbm(x / 900f + 9f, z / 900f + 3f, 2) * 0.5f + 0.5f);
        float land = 12f + hills * mask + mountains + detail * (0.4f + 0.6f * mask);
        float h = oceanBed + (land - oceanBed) * mask;

        // guarantee a flat airfield around the home runway; keep the coastline
        // outside the blend zone so the ocean never floods the approach paths
        float dEdge = runwayDist(x, z);
        if (dEdge < 3200f) {
            float t = smooth(420f, 2600f, dEdge);
            float coastGuard = smooth(2400f, 3200f, dEdge);
            float nat = Math.max(h, 2f + (h - 2f) * coastGuard);
            h = RUNWAY_H + (nat - RUNWAY_H) * t;
        }
        return h;
    }

    /** Distance from (x,z) to the runway rectangle. */
    public static float runwayDist(float x, float z) {
        float dx = Math.max(RUNWAY_X0 - x, Math.max(0, x - RUNWAY_X1));
        float dz = Math.max(-RUNWAY_ZHALF - z, Math.max(0, z - RUNWAY_ZHALF));
        return (float) Math.sqrt(dx * dx + dz * dz);
    }

    public boolean onRunway(float x, float z) {
        return x >= RUNWAY_X0 - 2 && x <= RUNWAY_X1 + 2 && Math.abs(z) <= RUNWAY_ZHALF + 2;
    }

    /** Approximate terrain slope magnitude at (x,z): |grad h|. */
    public float slopeAt(float x, float z) {
        float e = 6f;
        float hx = (height(x + e, z) - height(x - e, z)) / (2 * e);
        float hz = (height(x, z + e) - height(x, z - e)) / (2 * e);
        return (float) Math.sqrt(hx * hx + hz * hz);
    }

    private static float smooth(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Surface color (biome) for a point; slope = |gradient| magnitude approx. */
    private void colorAt(float x, float z, float h, float slope, float detail, float[] out) {
        if (FLATCOL) { out[0] = 0.5f; out[1] = 0.55f; out[2] = 0.5f; return; }
        // detail: 1 near .. ~0.3 far. Far LODs alias (one quad = a few pixels),
        // so noise contrast is reduced and color transitions widened to keep
        // distant slopes from speckling between grass/rock/snow categories.
        float v = n.fbm(x / 130f + 31f, z / 130f - 17f, 2) * 0.5f * detail;
        float forest = n.fbm(x / 300f + 7f, z / 300f - 13f, 2) * 0.5f + 0.5f;
        if (h < 1.5f) {
            // sea floor / beach sand, darker with depth
            float d = Math.max(0, Math.min(1, -h / 30f));
            out[0] = 0.72f - 0.34f * d;
            out[1] = 0.66f - 0.30f * d;
            out[2] = 0.50f - 0.26f * d;
            return;
        }
        if (h < 6f) { // beach band
            out[0] = 0.85f; out[1] = 0.78f; out[2] = 0.58f;
            return;
        }
        // grass with variation; darker in forests
        float g = 0.93f + 0.14f * v;
        float fr = forest > 0.62f ? 1f - 0.18f * detail : 1.0f;
        out[0] = 0.35f * g * fr;
        out[1] = 0.55f * g * fr;
        out[2] = 0.25f * g * fr;
        // rocky above tree line or on steep slopes
        // far LODs: much wider transitions so 64 m quads on a steep face (which
        // easily straddle the whole band between samples) cannot speckle
        float rockLine = 300f + v * 120f;
        float rockW = 90f + (1f - detail) * 280f;
        float rocky = Math.max(smooth(rockLine, rockLine + rockW, h),
                smooth(0.55f, 0.85f + (1f - detail) * 0.7f, slope));
        if (rocky > 0) {
            out[0] = out[0] * (1 - rocky) + 0.46f * rocky;
            out[1] = out[1] * (1 - rocky) + 0.43f * rocky;
            out[2] = out[2] * (1 - rocky) + 0.40f * rocky;
        }
        // snow caps — band widens with distance; slope assist fades out so the
        // smoothed 25 m slope estimate cannot flap the boundary between neighbors.
        // Far LODs also CAP the snow amount: adjacent 64 m samples on a ridge can
        // differ ~90 m in height, so a narrow near-white band aliased into bright
        // dots over dark rock ("see-through pinholes"). Distant snow becomes a
        // smooth pale haze; near tiles keep crisp white caps.
        float snowLine = 560f + v * 160f;
        float snowW = 70f + (1f - detail) * 900f;
        float snow = smooth(snowLine, snowLine + snowW, h + slope * 120f * detail)
                * (0.3f + 0.7f * detail);
        if (snow > 0) {
            out[0] = out[0] * (1 - snow) + 0.93f * snow;
            out[1] = out[1] * (1 - snow) + 0.94f * snow;
            out[2] = out[2] * (1 - snow) + 0.97f * snow;
        }
        // mowed grass near the airfield
        float dEdge = runwayDist(x, z);
        if (dEdge < 700f) {
            float t = 1 - smooth(250f, 700f, dEdge);
            out[0] = out[0] * (1 - t) + 0.44f * t;
            out[1] = out[1] * (1 - t) + 0.64f * t;
            out[2] = out[2] * (1 - t) + 0.30f * t;
        }
    }

    private boolean treeAt(float x, float z, float h) {
        if (h < 8f || h > 260f) return false;
        if (runwayDist(x, z) < 160f) return false;
        float forest = n.fbm(x / 300f + 7f, z / 300f - 13f, 2) * 0.5f + 0.5f;
        if (forest < 0.60f) return false;
        // scatter grid with jitter
        float gx = x / 55f, gz = z / 55f;
        int cx = (int) Math.floor(gx), cz = (int) Math.floor(gz);
        if (Math.abs(gx - cx - 0.5f) > 0.42f || Math.abs(gz - cz - 0.5f) > 0.42f) return false;
        return Noise.hash2(seed ^ 0x77, cx, cz) < (forest - 0.55f) * 2.2f;
    }

    /** Cone trees baked into near tiles, positions identical to treeAt(). */
    private void addTrees(MeshBuilder mb, float ox, float oz) {
        if (NO_TREES) return; // diagnostic switch (SKY_NO_TREES=1)
        int c0x = (int) Math.floor(ox / 55f), c1x = (int) Math.floor((ox + TILE) / 55f);
        int c0z = (int) Math.floor(oz / 55f), c1z = (int) Math.floor((oz + TILE) / 55f);
        for (int cz = c0z; cz <= c1z; cz++) {
            for (int cx = c0x; cx <= c1x; cx++) {
                float jx = Noise.hash2(seed ^ 0x11, cx, cz);
                float jz = Noise.hash2(seed ^ 0x22, cx, cz);
                float x = (cx + 0.25f + 0.5f * jx) * 55f;
                float z = (cz + 0.25f + 0.5f * jz) * 55f;
                if (x < ox || x >= ox + TILE || z < oz || z >= oz + TILE) continue;
                float h = height(x, z);
                if (!treeAt(x, z, h)) continue;
                float th = 7f + 5f * Noise.hash2(seed ^ 0x33, cx, cz);
                // trunk
                mb.addBox(x, h + th * 0.25f, z, 0.7f, th * 0.5f, 0.7f, 0.34f, 0.25f, 0.15f);
                // canopy: two stacked 4-sided pyramids
                float[] leaf = {0.20f, 0.36f, 0.15f};
                float[] leaf2 = {0.24f, 0.42f, 0.18f};
                pyramid(mb, x, h + th * 0.35f, z, 2.4f, th * 0.45f, leaf);
                pyramid(mb, x, h + th * 0.62f, z, 1.7f, th * 0.45f, leaf2);
            }
        }
    }

    private void pyramid(MeshBuilder mb, float x, float y, float z, float r, float h, float[] c) {
        float apex = y + h;
        // four faces, each a collapsed addQuad (triangle)
        mb.addQuad(x - r, y, z - r, x + r, y, z - r, x, apex, z, x, apex, z, 0, 0.4f, -0.9f, c[0], c[1], c[2]);
        mb.addQuad(x + r, y, z + r, x - r, y, z + r, x, apex, z, x, apex, z, 0, 0.4f, 0.9f, c[0], c[1], c[2]);
        mb.addQuad(x + r, y, z - r, x + r, y, z + r, x, apex, z, x, apex, z, 0.9f, 0.4f, 0, c[0], c[1], c[2]);
        mb.addQuad(x - r, y, z + r, x - r, y, z - r, x, apex, z, x, apex, z, -0.9f, 0.4f, 0, c[0], c[1], c[2]);
    }

    // ------------------------------------------------------------ tile meshing
    private void buildTile(int tx, int tz, int lod) {
        buildTile(tx, tz, lod, -1);
    }

    /** Color-detail tier: lod plus, for near LOD0 tiles, the ring distance. */
    private static int tier(int lod, int ringDist) {
        if (lod == 0) return ringDist <= 1 ? 1 : ringDist == 2 ? 2 : ringDist == 3 ? 3 : 4;
        return lod == 1 ? 5 : 6;
    }

    private void buildTile(int tx, int tz, int lod, int ringDist) {
        int N = LOD_GRID[lod];
        float step = (float) TILE / N;
        float ox = tx * (float) TILE, oz = tz * (float) TILE;

        int P = N + 1;
        float[] hs = new float[P * P];
        float[] nxs = new float[P * P], nys = new float[P * P], nzs = new float[P * P];
        float[] slopes = new float[P * P];
        float minH = Float.MAX_VALUE, maxH = -Float.MAX_VALUE;
        // Smooth per-vertex normals AND slopes sampled at a fixed world-space span.
        // Identical values on shared corners of every tile and LOD, so shading and
        // vertex colors are seamless, and distant rough slopes no longer alias into
        // salt-and-pepper slivers (flat per-triangle lighting/color artifact).
        // The span GROWS with LOD AND with ring distance: at 32/64 m quads a 25 m
        // span still resolves the micro-relief, so neighboring triangles flip
        // between sunlit and shadowed — the "see-through pinholes" artifact.
        // Averaging the gradient over ~2-4 quads at distance shades them like
        // smooth macro slopes.
        float nspan;
        if (lod == 0) {
            nspan = ringDist <= 1 ? 25f : ringDist == 2 ? 35f : ringDist == 3 ? 50f : 70f;
        } else {
            // far tiles show ~1-2 px quads: the gradient span must average out
            // ALL micro-relief octaves (26-60 m) or per-vertex lighting flickers
            // into dark/bright pixel speckle; macro shapes live at 500 m+
            nspan = lod == 1 ? 220f : 320f;
        }
        for (int j = 0; j < P; j++) {
            for (int i = 0; i < P; i++) {
                float x = ox + i * step, z = oz + j * step;
                float h = height(x, z);
                hs[j * P + i] = h;
                if (h < minH) minH = h;
                if (h > maxH) maxH = h;
                float hx = (height(x + nspan, z) - height(x - nspan, z)) / (2 * nspan);
                float hz = (height(x, z + nspan) - height(x, z - nspan)) / (2 * nspan);
                float nx = -hx, ny = 1f, nz = -hz;
                float nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                int nidx = j * P + i;
                nxs[nidx] = nx / nl; nys[nidx] = ny / nl; nzs[nidx] = nz / nl;
                slopes[nidx] = (float) Math.sqrt(hx * hx + hz * hz);
            }
        }
        // Far LODs: drive biome colors from a BLURRED height so rock/snow bands
        // follow the macro surface. True per-sample heights on 32/64 m grids jitter
        // tens of meters between neighbors on ridges, which flickered the snow and
        // rock thresholds into bright/dark micro-triangle speckle ("pinholes").
        // Blurred heights also apply to LOD0 outer rings (their 16 m jitter still
        // flickers the thresholds at 3-4 km).
        boolean blurCol = lod > 0 || (lod == 0 && ringDist >= 3);
        float[] hc = hs;
        if (blurCol) {
            // Small radii only: the blur exists to remove per-sample height JITTER
            // under the (wide) far-LOD rock/snow bands. Large windows would average
            // away the flank's real elevation drop and paint summits grass-green.
            int r = lod == 0 ? (ringDist >= 4 ? 2 : 1) : lod == 1 ? 2 : 2; // blur radius in cells
            float[] tmp = new float[P * P];
            for (int j = 0; j < P; j++) {
                for (int i = 0; i < P; i++) {
                    float s = 0;
                    int n = 0;
                    for (int k = Math.max(0, i - r); k <= Math.min(P - 1, i + r); k++) { s += hs[j * P + k]; n++; }
                    tmp[j * P + i] = s / n;
                }
            }
            hc = new float[P * P];
            for (int j = 0; j < P; j++) {
                for (int i = 0; i < P; i++) {
                    float s = 0;
                    int n = 0;
                    for (int k = Math.max(0, j - r); k <= Math.min(P - 1, j + r); k++) { s += tmp[k * P + i]; n++; }
                    hc[j * P + i] = s / n;
                }
            }
        }
        MeshBuilder mb = new MeshBuilder();
        float[] c0 = new float[3];
        // per-LOD color detail: full near, progressively de-detailed far.
        // LOD0 tiles reach 4.5 km out (ring d<=4), where full-contrast color
        // noise on 16 m quads aliases into speckle — so taper detail by ring
        // distance as well; only the innermost rings render at full richness.
        float colorDetail;
        if (lod == 0) {
            colorDetail = ringDist <= 1 ? 1f : ringDist == 2 ? 0.8f : ringDist == 3 ? 0.65f : 0.5f;
        } else {
            colorDetail = lod == 1 ? 0.55f : 0.3f;
        }
        int myTier = tier(lod, ringDist);
        // Precompute per-vertex biome colors once (shared by the surface quads AND
        // the skirts so a crack pixel shows the same color as the surface).
        float[] cols = new float[P * P * 3];
        for (int j = 0; j < P; j++) {
            for (int i = 0; i < P; i++) {
                int idx = j * P + i;
                colorAt(ox + i * step, oz + j * step, hc[idx], slopes[idx], colorDetail, c0);
                cols[idx * 3] = c0[0]; cols[idx * 3 + 1] = c0[1]; cols[idx * 3 + 2] = c0[2];
            }
        }
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                float x0 = ox + i * step, x1 = x0 + step;
                float z0 = oz + j * step, z1 = z0 + step;
                float h00 = hs[j * P + i], h10 = hs[j * P + i + 1];
                float h01 = hs[(j + 1) * P + i], h11 = hs[(j + 1) * P + i + 1];
                float c00 = hc[j * P + i], c10 = hc[j * P + i + 1];
                float c01 = hc[(j + 1) * P + i], c11 = hc[(j + 1) * P + i + 1];
                int i00 = j * P + i, i10 = j * P + i + 1, i01 = (j + 1) * P + i, i11 = (j + 1) * P + i + 1;
                // consistent diagonal for calm gradients
                float[][] vx = {{x0, x0, x1}, {x0, x1, x1}};
                float[][] vz = {{z0, z1, z1}, {z0, z0, z1}};
                float[][] vh = {{h00, h01, h11}, {h00, h11, h10}};
                float[][] vhc = {{c00, c01, c11}, {c00, c11, c10}};
                int[][] vi = {{i00, i01, i11}, {i00, i11, i10}};
                for (int t = 0; t < 2; t++) {
                    for (int k = 0; k < 3; k++) {
                        int ni = vi[t][k];
                        mb.addVert(vx[t][k], vh[t][k], vz[t][k], nxs[ni], nys[ni], nzs[ni], 0, 0,
                                cols[ni * 3], cols[ni * 3 + 1], cols[ni * 3 + 2]);
                    }
                }
            }
        }

        // skirts to hide LOD seams — colored/normals sampled from their top edge
        // so pixels seen through a T-junction crack blend with the surface
        if (!NO_SKIRTS) {
            float drop = 90f; // generous: covers T-junction gaps even on 800 m ridges
            skirt(mb, ox, oz, hs, P, step, minH, drop, cols, nxs, nys, nzs);
        }

        // near rings get baked cone trees; beyond ~2.5 km a 12 m tree is 2 px
        // of aliasing speckle on the slopes
        if (lod == 0 && (ringDist < 0 || ringDist <= 2)) addTrees(mb, ox, oz);

        if (NO_GL) {
            System.out.println("tile(" + tx + "," + tz + ",lod" + lod + "): Y " + mb.minY + " .. " + mb.maxY
                    + "  X " + mb.minX + " .. " + mb.maxX + "  verts " + mb.vertexCount());
            if (mb.minY < -10) {
                // print every vertex below -10 with its neighborhood in the float list
                for (int i = 0; i + 10 < mb.raw().size(); i += 11) {
                    float vy = mb.raw().get(i + 1);
                    if (vy < -10) {
                        System.out.println("   lowVert xyz=(" + mb.raw().get(i) + "," + vy + "," + mb.raw().get(i + 2)
                                + ") nrm=(" + mb.raw().get(i + 3) + "," + mb.raw().get(i + 4) + "," + mb.raw().get(i + 5) + ")"
                                + " col=(" + mb.raw().get(i + 8) + "," + mb.raw().get(i + 9) + "," + mb.raw().get(i + 10) + ")");
                        if (i > 11 * 40) { System.out.println("   ..."); break; }
                    }
                }
            }
            return;
        }
        Tile t = new Tile();
        t.lod = lod;
        t.tier = myTier;
        t.mesh = mb.build();
        t.lastWanted = frame;
        t.minH = Math.min(minH, minH - 90f);
        t.maxH = maxH;
        long key = ckey(tx, tz);
        Tile old = tiles.put(key, t);
        if (old != null && old.mesh != null) old.mesh.dispose();
    }

    /** Full-perimeter skirt quads (drop straight down from every edge vertex).
     *  Each wall inherits the COLOR and smoothed NORMAL of its top edge vertices,
     *  so a wall seen through a T-junction crack reads as continued terrain
     *  instead of a dark slit (the old hardcoded dark green = speckle dots). */
    private void skirt(MeshBuilder mb, float ox, float oz, float[] hs, int P, float step,
                       float minH, float drop, float[] cols, float[] nxs, float[] nys, float[] nzs) {
        // north edge (j=0), south (j=P-1), west (i=0), east (i=P-1)
        for (int i = 0; i < P - 1; i++) {
            addSkirtQuad(mb, ox + i * step, oz, ox + (i + 1) * step, oz,
                    hs[i], hs[i + 1], i, i + 1, drop, cols, nxs, nys, nzs, P);
            float j = (P - 1) * step;
            addSkirtQuad(mb, ox + i * step, oz + j, ox + (i + 1) * step, oz + j,
                    hs[(P - 1) * P + i], hs[(P - 1) * P + i + 1], (P - 1) * P + i, (P - 1) * P + i + 1,
                    drop, cols, nxs, nys, nzs, P);
        }
        for (int j = 0; j < P - 1; j++) {
            addSkirtQuad(mb, ox, oz + j * step, ox, oz + (j + 1) * step,
                    hs[j * P], hs[(j + 1) * P], j * P, (j + 1) * P, drop, cols, nxs, nys, nzs, P);
            float i = (P - 1) * step;
            addSkirtQuad(mb, ox + i, oz + j * step, ox + i, oz + (j + 1) * step,
                    hs[j * P + P - 1], hs[(j + 1) * P + P - 1], j * P + P - 1, (j + 1) * P + P - 1,
                    drop, cols, nxs, nys, nzs, P);
        }
    }

    /** One skirt wall with per-vertex color/normal inherited from the edge. */
    private void addSkirtQuad(MeshBuilder mb, float x0, float z0, float x1, float z1,
                              float h0, float h1, int vi0, int vi1, float drop,
                              float[] cols, float[] nxs, float[] nys, float[] nzs, int P) {
        float r0 = cols[vi0 * 3], g0 = cols[vi0 * 3 + 1], b0 = cols[vi0 * 3 + 2];
        float r1 = cols[vi1 * 3], g1 = cols[vi1 * 3 + 1], b1 = cols[vi1 * 3 + 2];
        float n0x = nxs[vi0], n0y = nys[vi0], n0z = nzs[vi0];
        float n1x = nxs[vi1], n1y = nys[vi1], n1z = nzs[vi1];
        // two triangles: (top0, top1, bot1) and (top0, bot1, bot0)
        mb.addVert(x0, h0, z0, n0x, n0y, n0z, 0, 0, r0, g0, b0);
        mb.addVert(x1, h1, z1, n1x, n1y, n1z, 0, 0, r1, g1, b1);
        mb.addVert(x1, h1 - drop, z1, n1x, n1y, n1z, 0, 0, r1, g1, b1);
        mb.addVert(x0, h0, z0, n0x, n0y, n0z, 0, 0, r0, g0, b0);
        mb.addVert(x1, h1 - drop, z1, n1x, n1y, n1z, 0, 0, r1, g1, b1);
        mb.addVert(x0, h0 - drop, z0, n0x, n0y, n0z, 0, 0, r0, g0, b0);
    }

    private static long key(int tx, int tz, int lod) {
        return (long) (tx & 0xFFFFF) << 44 | (long) (tz & 0xFFFFF) << 24 | (long) lod;
    }

    /** Tile-map key: coordinates only — exactly one LOD version per tile at a
     *  time, so a replaced LOD can never render alongside its replacement
     *  (simultaneous near-coplanar surfaces used to z-fight into speckles). */
    private static long ckey(int tx, int tz) {
        return (long) (tx & 0xFFFFF) << 44 | (long) (tz & 0xFFFFF) << 24;
    }

    // ------------------------------------------------------------ streaming
    /** Queue/build tiles around the focus point. Returns remaining builds. */
    public int stream(float fx, float fz, int renderDistance, int buildsPerFrame) {
        frame++;
        int ctx = (int) Math.floor(fx / TILE), ctz = (int) Math.floor(fz / TILE);
        buildQueue.clear();
        queuedLod.clear();
        for (int dz = -renderDistance; dz <= renderDistance; dz++) {
            for (int dx = -renderDistance; dx <= renderDistance; dx++) {
                int d = Math.max(Math.abs(dx), Math.abs(dz));
                int lod = LOD_FORCE >= 0 ? Math.min(LOD_FORCE, LOD_GRID.length - 1)
                        : d <= 4 ? 0 : d <= 8 ? 1 : 2;
                if (lod >= LOD_GRID.length) lod = LOD_GRID.length - 1;
                int tx = ctx + dx, tz = ctz + dz;
                long k = key(tx, tz, lod);
                Tile t = tiles.get(ckey(tx, tz));
                if (t != null) {
                    // existing tile stays alive (it may be a fallback LOD)
                    t.lastWanted = frame;
                    boolean lodChanged = t.lod != lod;
                    boolean tierChanged = lod == 0 && t.tier != tier(lod, d);
                    if ((lodChanged || tierChanged) && !queuedLod.containsKey(k)) {
                        // a different LOD or color tier is wanted here: build it;
                        // buildTile atomically replaces the fallback version
                        buildQueue.add(new long[]{tx, tz, lod, d});
                        queuedLod.put(k, lod);
                    }
                } else if (!queuedLod.containsKey(k)) {
                    buildQueue.add(new long[]{tx, tz, lod, d});
                    queuedLod.put(k, lod);
                }
            }
        }
        buildQueue.sort((a, b) -> {
            int da = Math.max(Math.abs((int) a[0] - ctx), Math.abs((int) a[1] - ctz));
            int db = Math.max(Math.abs((int) b[0] - ctx), Math.abs((int) b[1] - ctz));
            return Integer.compare(da, db);
        });
        int built = Math.min(buildsPerFrame, buildQueue.size());
        for (int i = 0; i < built; i++) {
            long[] q = buildQueue.get(i);
            buildTile((int) q[0], (int) q[1], (int) q[2], (int) q[3]);
        }
        // reap stale tiles
        Iterator<Map.Entry<Long, Tile>> it = tiles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Tile> e = it.next();
            Tile t = e.getValue();
            if (frame - t.lastWanted > 90) {
                if (t.mesh != null) t.mesh.dispose();
                it.remove();
            }
        }
        return buildQueue.size() - built;
    }

    public int pendingCount() { return buildQueue.size(); }

    public int tileCount() { return tiles.size(); }

    // ------------------------------------------------------------ water + airfield
    public void buildStatics() {
        // ocean: big grid that will follow the camera in shader space via uModel
        MeshBuilder wb = new MeshBuilder();
        int N = 48;                       // 48x48 quads
        float span = 24000f;              // covers the whole render distance
        float step = span / N;
        for (int j = 0; j < N; j++) {
            for (int i = 0; i < N; i++) {
                float x0 = -span / 2 + i * step, x1 = x0 + step;
                float z0 = -span / 2 + j * step, z1 = z0 + step;
                wb.addQuad(x0, 0, z1, x1, 0, z1, x1, 0, z0, x0, 0, z0,
                        0, 1, 0, 0.09f, 0.30f, 0.44f);
            }
        }
        waterMesh = wb.build();

        // airfield
        MeshBuilder rb = new MeshBuilder();
        float y = RUNWAY_H + 0.15f;
        float r = 0.235f, g = 0.235f, b = 0.25f; // asphalt
        // runway slab
        rb.addQuad(RUNWAY_X0, y, RUNWAY_ZHALF, RUNWAY_X1, y, RUNWAY_ZHALF,
                RUNWAY_X1, y, -RUNWAY_ZHALF, RUNWAY_X0, y, -RUNWAY_ZHALF, 0, 1, 0, r, g, b);
        // edge lines
        float[] white = {0.92f, 0.92f, 0.90f};
        line(rb, RUNWAY_X0, RUNWAY_X1, -RUNWAY_ZHALF + 0.8f, 0.7f, y + 0.03f, white);
        line(rb, RUNWAY_X0, RUNWAY_X1, RUNWAY_ZHALF - 1.5f, 0.7f, y + 0.03f, white);
        // centerline dashes
        for (float x = RUNWAY_X0 + 60; x < RUNWAY_X1 - 50; x += 60) {
            line(rb, x, x + 30, -0.6f, 1.2f, y + 0.03f, white);
        }
        // threshold stripes (piano keys) both ends
        for (int i = 0; i < 8; i++) {
            float zc = -RUNWAY_ZHALF + 3.5f + i * 5.4f;
            line(rb, RUNWAY_X0 + 4, RUNWAY_X0 + 34, zc - 0.9f, 1.8f, y + 0.03f, white);
            line(rb, RUNWAY_X1 - 34, RUNWAY_X1 - 4, zc - 0.9f, 1.8f, y + 0.03f, white);
        }
        // taxiway + apron west of threshold
        float ax = RUNWAY_X0 - 130;
        rb.addQuad(ax, y, 60, RUNWAY_X0 - 10, y, 60, RUNWAY_X0 - 10, y, 14, ax, y, 14, 0, 1, 0, 0.26f, 0.26f, 0.28f);
        rb.addQuad(ax, y, 150, ax + 120, y, 150, ax + 120, y, 60, ax, y, 60, 0, 1, 0, 0.27f, 0.27f, 0.30f);
        // hangars
        rb.addBox(ax + 30, y + 5.5f, 105, 44, 11, 26, 0.55f, 0.58f, 0.60f);
        rb.addBox(ax + 30, y + 11.5f, 105, 46, 2.5f, 28, 0.42f, 0.46f, 0.52f);
        rb.addBox(ax + 82, y + 4.5f, 105, 32, 9, 22, 0.58f, 0.52f, 0.46f);
        // tower
        rb.addBox(ax + 60, y + 12, 170, 7, 24, 7, 0.62f, 0.62f, 0.64f);
        rb.addBox(ax + 60, y + 26, 170, 13, 5, 13, 0.35f, 0.42f, 0.50f);
        rb.addBox(ax + 60, y + 29.2f, 170, 14, 1.4f, 14, 0.30f, 0.30f, 0.34f);
        // windsock pole + sock
        rb.addBox(RUNWAY_X1 + 60, y + 6, 0, 0.5f, 12, 0.5f, 0.8f, 0.8f, 0.82f);
        rb.addBox(RUNWAY_X1 + 62.5f, y + 11.4f, 0, 4.5f, 1.2f, 0.8f, 0.95f, 0.55f, 0.15f);
        runwayMesh = rb.build();
        runwayMinX = (float) rb.minX;
        runwayMaxX = (float) rb.maxX;
    }

    private void line(MeshBuilder rb, float x0, float x1, float z0, float zw, float y, float[] c) {
        rb.addQuad(x0, y, z0 + zw, x1, y, z0 + zw, x1, y, z0, x0, y, z0, 0, 1, 0, c[0], c[1], c[2]);
    }

    // ------------------------------------------------------------ rendering
    public void render(float camX, float camY, float camZ, Mat4 proj, Mat4 view,
                       Options opts, Sky sky, float time) {
        if (waterMesh == null || runwayMesh == null) buildStatics();

        int uProj = shader.loc("uProj"), uView = shader.loc("uView"), uModel = shader.loc("uModel");
        shader.use();
        shader.setMat4(uProj, Main.MAT_BUF.put(proj.m).flip());
        shader.setMat4(uView, Main.MAT_BUF.put(view.m).flip());
        Mat4 ident = new Mat4().identity();
        shader.setMat4(uModel, Main.MAT_BUF.put(ident.m).flip());
        shader.setFloat(shader.loc("uTime"), time);
        shader.setVec3(shader.loc("uCamPos"), camX, camY, camZ);
        setLightingUniforms(sky, opts);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE); // terrain quads mix windings; depth test is enough
        GL11.glDisable(GL11.GL_BLEND);

        // terrain tiles
        boolean drawTiles = System.getenv("SKY_NO") == null || !System.getenv("SKY_NO").contains("tiles");
        boolean drawRunway = System.getenv("SKY_NO") == null || !System.getenv("SKY_NO").contains("runway");
        boolean drawWater = System.getenv("SKY_NO") == null || !System.getenv("SKY_NO").contains("water");
        float far = opts.renderDistance * (float) TILE + TILE;
        shader.setFloat(shader.loc("uFogStart"), opts.fog ? far * 0.55f : 1e9f);
        shader.setFloat(shader.loc("uFogEnd"), opts.fog ? far * 0.98f : 2e9f);
        shader.setFloat(shader.loc("uWave"), 0);
        shader.setFloat(shader.loc("uSpecular"), 0);
        int drawn = 0;
        if (drawTiles) {
            float[][] planes = Mat4.frustumPlanes(Mat4.mul(proj, view));
            for (Tile t : tiles.values()) {
                // DEBUG: culling disabled to diagnose missing-tile checkerboard
                if (System.getenv("SKY_NOCULL") == null
                        && !Mat4.aabbInFrustum(planes, t.mesh.minX(), t.mesh.minY(), t.mesh.minZ(),
                        t.mesh.maxX(), t.mesh.maxY(), t.mesh.maxZ())) continue;
                t.mesh.draw();
                drawn++;
            }
        }
        drawnTiles = drawn;

        // runway (always drawn, cheap)
        if (drawRunway) runwayMesh.draw();

        // ocean (translucent, animated, follows camera)
        if (drawWater) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            shader.setFloat(shader.loc("uWave"), 1);
            shader.setFloat(shader.loc("uSpecular"), 0.55f);
            Mat4 wt = new Mat4().identity();
            wt.m[12] = (float) (Math.floor(camX / 256) * 256);
            wt.m[14] = (float) (Math.floor(camZ / 256) * 256);
            shader.setMat4(uModel, Main.MAT_BUF.put(wt.m).flip());
            shader.setVec4(shader.loc("uTint"), 1, 1, 1, 0.82f);
            waterMesh.draw();
            shader.setVec4(shader.loc("uTint"), 1, 1, 1, 1);
            GL11.glDepthMask(true);
            GL11.glEnable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_BLEND);
        }
    }

    /** DEBUG: print stats of a built tile mesh (vertex Y distribution). */
    public void debugDumpTile(int tx, int tz) {
        for (int lod = 0; lod < 3; lod++) {
            Tile t = tiles.get(ckey(tx, tz));
            if (t == null) { System.out.println("tile(" + tx + "," + tz + ",lod" + lod + "): MISSING"); continue; }
            Mesh m = t.mesh;
            System.out.println("tile(" + tx + "," + tz + ",lod" + lod + "): Y " + m.minY() + " .. " + m.maxY()
                    + "  X " + m.minX() + " .. " + m.maxX());
        }
    }

    public int drawnTiles;

    public void setLightingUniforms(Sky sky, Options opts) {
        if (System.getenv("SKY_FLATLIGHT") != null) {
            // diagnostic: constant color + constant light => every terrain pixel
            // must render the SAME gray; any dot/variation is a raster/mesh issue
            shader.setVec3(shader.loc("uSunDir"), 0.3f, 0.9f, 0.3f);
            shader.setVec3(shader.loc("uSunColor"), 0, 0, 0);
            shader.setVec3(shader.loc("uAmbTop"), 0.5f, 0.5f, 0.5f);
            shader.setVec3(shader.loc("uAmbBot"), 0.5f, 0.5f, 0.5f);
            float[] fc = sky.fogColor();
            shader.setVec3(shader.loc("uFogColor"), fc[0], fc[1], fc[2]);
            return;
        }
        shader.setVec3(shader.loc("uSunDir"), sky.sunDir[0], sky.sunDir[1], sky.sunDir[2]);
        shader.setVec3(shader.loc("uSunColor"), sky.sunColor[0], sky.sunColor[1], sky.sunColor[2]);
        shader.setVec3(shader.loc("uAmbTop"), sky.ambTop[0], sky.ambTop[1], sky.ambTop[2]);
        shader.setVec3(shader.loc("uAmbBot"), sky.ambBot[0], sky.ambBot[1], sky.ambBot[2]);
        float[] fc = sky.fogColor();
        shader.setVec3(shader.loc("uFogColor"), fc[0], fc[1], fc[2]);
    }

    public void dispose() {
        for (Tile t : tiles.values()) if (t.mesh != null) t.mesh.dispose();
        tiles.clear();
        if (waterMesh != null) waterMesh.dispose();
        if (runwayMesh != null) runwayMesh.dispose();
    }
}
