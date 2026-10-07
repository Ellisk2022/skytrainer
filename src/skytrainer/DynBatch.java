package skytrainer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

/** Dynamic immediate-style vertex batch (pos3/uv2/col3, triangle soup). Used by the GUI. */
public final class DynBatch {
    private final FloatBuffer buf;
    private final int vao, vbo;
    private final int capacity; // floats
    private int n;              // floats used
    private int mode = GL11.GL_TRIANGLES;

    public DynBatch(int maxQuads) {
        capacity = maxQuads * 6 * 8;
        buf = BufferUtils.createFloatBuffer(capacity);
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, (long) capacity * 4, GL15.GL_STREAM_DRAW);
        int stride = 8 * 4;
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 2, GL11.GL_FLOAT, false, stride, 12);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 3, GL11.GL_FLOAT, false, stride, 20);
        GL30.glBindVertexArray(0);
    }

    public void begin() { mode = GL11.GL_TRIANGLES; n = 0; }

    private void vert(float x, float y, float z, float u, float v, float r, float g, float b) {
        if (n + 8 > capacity) return; // drop excess
        buf.put(n, x).put(n + 1, y).put(n + 2, z).put(n + 3, u).put(n + 4, v)
           .put(n + 5, r).put(n + 6, g).put(n + 7, b);
        n += 8;
    }

    /** Axis-aligned quad from two corners; single tile UV rect; flat color. */
    public void quad(float x0, float y0, float x1, float y1, float[] uv, float z, float r, float g, float b) {
        tri(x0, y0, x1, y1, uv, z, r, g, b, false);
        tri(x0, y0, x1, y1, uv, z, r, g, b, true);
    }

    private void tri(float x0, float y0, float x1, float y1, float[] uv, float z, float r, float g, float b, boolean second) {
        float u0 = uv[0], v0 = uv[1], u1 = uv[2], v1 = uv[3];
        if (!second) {
            vert(x0, y0, z, u0, v0, r, g, b);
            vert(x0, y1, z, u0, v1, r, g, b);
            vert(x1, y1, z, u1, v1, r, g, b);
        } else {
            vert(x0, y0, z, u0, v0, r, g, b);
            vert(x1, y1, z, u1, v1, r, g, b);
            vert(x1, y0, z, u1, v0, r, g, b);
        }
    }

    /** Arbitrary-vertex quad (for rotated glyphs). corners: 4 points. */
    public void poly(float[] cx, float[] cy, float[] uvx, float[] uvy, float z, float r, float g, float b) {
        int[] order = {0, 1, 2, 0, 2, 3};
        for (int i : order) {
            vert(cx[i], cy[i], z, uvx[i], uvy[i], r, g, b);
        }
    }

    /** Uploads and draws everything batched so far. */
    public void draw() {
        if (n == 0) return;
        buf.limit(n).position(0);
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, buf);
        GL11.glDrawArrays(mode, 0, n / 8);
        buf.clear();
        n = 0;
    }
}
