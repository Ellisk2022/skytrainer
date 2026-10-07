package skytrainer;

import java.util.ArrayList;
import java.util.List;

/** Accumulates world-pipeline vertices (pos3/normal3/uv2/col3, 11 floats) as triangle soup. */
public final class MeshBuilder {
    private final List<Float> v = new ArrayList<>(1 << 14);
    public List<Float> raw() { return v; }
    public float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
    public float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

    public void addVert(float x, float y, float z, float nx, float ny, float nz, float u, float vv,
                        float r, float g, float b) {
        v.add(x); v.add(y); v.add(z);
        v.add(nx); v.add(ny); v.add(nz);
        v.add(u); v.add(vv);
        v.add(r); v.add(g); v.add(b);
        if (x < minX) minX = x; if (y < minY) minY = y; if (z < minZ) minZ = z;
        if (x > maxX) maxX = x; if (y > maxY) maxY = y; if (z > maxZ) maxZ = z;
    }

    /** Two triangles from 4 corners given in CCW order when viewed from the normal side. */
    public void addQuad(float ax, float ay, float az, float bx, float by, float bz,
                        float cx, float cy, float cz, float dx, float dy, float dz,
                        float nx, float ny, float nz, float r, float g, float b) {
        addVert(ax, ay, az, nx, ny, nz, 0, 0, r, g, b);
        addVert(bx, by, bz, nx, ny, nz, 0, 0, r, g, b);
        addVert(cx, cy, cz, nx, ny, nz, 0, 0, r, g, b);
        addVert(ax, ay, az, nx, ny, nz, 0, 0, r, g, b);
        addVert(cx, cy, cz, nx, ny, nz, 0, 0, r, g, b);
        addVert(dx, dy, dz, nx, ny, nz, 0, 0, r, g, b);
    }

    /** Axis-aligned box centered at (cx,cy,cz) with full sizes (sx,sy,sz), uniform base color. */
    public void addBox(float cx, float cy, float cz, float sx, float sy, float sz, float r, float g, float b) {
        float x0 = cx - sx / 2, x1 = cx + sx / 2;
        float y0 = cy - sy / 2, y1 = cy + sy / 2;
        float z0 = cz - sz / 2, z1 = cz + sz / 2;
        // top (+Y)
        addQuad(x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, r, g, b);
        // bottom (-Y)
        addQuad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, r, g, b);
        // +Z (south)
        addQuad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, r, g, b);
        // -Z (north)
        addQuad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, r, g, b);
        // +X (east)
        addQuad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, r, g, b);
        // -X (west)
        addQuad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, r, g, b);
    }

    public boolean isEmpty() { return v.isEmpty(); }

    public int vertexCount() { return v.size() / 11; }

    public Mesh build() {
        float[] data = new float[v.size()];
        for (int i = 0; i < data.length; i++) data[i] = v.get(i);
        return Mesh.triangles(data, data.length / 11);
    }
}
