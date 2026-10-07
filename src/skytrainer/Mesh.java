package skytrainer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

/**
 * Static GPU mesh for the world pipeline: interleaved pos3/normal3/uv2/col3 (11 floats/vertex),
 * raw triangles via drawArrays.
 */
public final class Mesh {
    private int vao = -1, vbo = -1;
    private int count; // vertices
    private float aabbMinX, aabbMinY, aabbMinZ, aabbMaxX, aabbMaxY, aabbMaxZ;

    public static Mesh triangles(float[] data, int vertexCount) {
        return new Mesh(data, vertexCount);
    }

    /** Wrap an already-allocated VAO/VBO (custom geometry pipelines). */
    public static Mesh wrapped(int vao, int vbo, int vertexCount) {
        Mesh m = new Mesh(new float[0], 0);
        m.vao = vao; m.vbo = vbo; m.count = vertexCount;
        return m;
    }

    private Mesh(float[] data, int vertexCount) {
        this.count = vertexCount;
        aabbMinX = aabbMinY = aabbMinZ = Float.MAX_VALUE;
        aabbMaxX = aabbMaxY = aabbMaxZ = -Float.MAX_VALUE;
        for (int i = 0; i < vertexCount; i++) {
            int o = i * 11;
            if (data[o] < aabbMinX) aabbMinX = data[o];
            if (data[o + 1] < aabbMinY) aabbMinY = data[o + 1];
            if (data[o + 2] < aabbMinZ) aabbMinZ = data[o + 2];
            if (data[o] > aabbMaxX) aabbMaxX = data[o];
            if (data[o + 1] > aabbMaxY) aabbMaxY = data[o + 1];
            if (data[o + 2] > aabbMaxZ) aabbMaxZ = data[o + 2];
        }
        FloatBuffer fb = BufferUtils.createFloatBuffer(data.length);
        fb.put(data).flip();
        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_STATIC_DRAW);
        int stride = 11 * 4;
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, 12);
        GL20.glEnableVertexAttribArray(2);
        GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, stride, 24);
        GL20.glEnableVertexAttribArray(3);
        GL20.glVertexAttribPointer(3, 3, GL11.GL_FLOAT, false, stride, 32);
        GL30.glBindVertexArray(0);
    }

    public float minX() { return aabbMinX; }
    public float minY() { return aabbMinY; }
    public float minZ() { return aabbMinZ; }
    public float maxX() { return aabbMaxX; }
    public float maxY() { return aabbMaxY; }
    public float maxZ() { return aabbMaxZ; }

    public void draw() {
        if (vao == -1 || count == 0) return;
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, count);
    }

    public void dispose() {
        if (vao != -1) { GL30.glDeleteVertexArrays(vao); GL15.glDeleteBuffers(vbo); vao = -1; }
    }
}
