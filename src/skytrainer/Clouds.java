package skytrainer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.lwjgl.BufferUtils;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Random;

/** One huge translucent cloud sheet at fixed altitude, tiled with a seamless noise texture. */
public final class Clouds {
    public static final float ALT = 1800f;

    private Mesh mesh;
    private int tex = -1;
    private final Shader shader;

    public Clouds(Shader worldShader) {
        this.shader = worldShader;
    }

    public void build() {
        float span = 40960f;
        float step = span / (16f * 8f);
        int n = (int) (span / step);
        mesh = buildWithUv(span, step, n);
    }

    private Mesh buildWithUv(float span, float step, int n) {
        FloatBuffer fb = BufferUtils.createFloatBuffer(n * n * 6 * 11);
        float x0 = -span / 2, y = ALT;
        for (int j = 0; j < n; j++) {
            for (int i = 0; i < n; i++) {
                float ax = x0 + i * step, bx = ax + step;
                float az = x0 + j * step, bz = az + step;
                put(fb, ax, y, az); put(fb, bx, y, bz); put(fb, bx, y, az);
                put(fb, ax, y, az); put(fb, ax, y, bz); put(fb, bx, y, bz);
            }
        }
        fb.flip();
        int vao = GL30.glGenVertexArrays();
        int vbo = org.lwjgl.opengl.GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, vbo);
        org.lwjgl.opengl.GL15.glBufferData(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER, fb, org.lwjgl.opengl.GL15.GL_STATIC_DRAW);
        int stride = 11 * 4;
        org.lwjgl.opengl.GL20.glEnableVertexAttribArray(0);
        org.lwjgl.opengl.GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, stride, 0);
        org.lwjgl.opengl.GL20.glEnableVertexAttribArray(1);
        org.lwjgl.opengl.GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, stride, 12);
        org.lwjgl.opengl.GL20.glEnableVertexAttribArray(2);
        org.lwjgl.opengl.GL20.glVertexAttribPointer(2, 2, GL11.GL_FLOAT, false, stride, 24);
        org.lwjgl.opengl.GL20.glEnableVertexAttribArray(3);
        org.lwjgl.opengl.GL20.glVertexAttribPointer(3, 3, GL11.GL_FLOAT, false, stride, 32);
        GL30.glBindVertexArray(0);
        mesh = Mesh.wrapped(vao, vbo, n * n * 6);
        return mesh;
    }

    private static void put(FloatBuffer fb, float x, float y, float z) {
        fb.put(x).put(y).put(z);
        fb.put(0).put(1).put(0);          // normal
        fb.put(x / 4096f).put(z / 4096f); // uv anchored to world coords
        fb.put(1).put(1).put(1);          // color
    }

    /** Periodic seamless cloud alpha texture. */
    private int buildTexture() {
        int S = 128;
        float[] a = new float[S * S];
        Random r = new Random(99);
        float[][] lattice = new float[33][33];
        for (int j = 0; j < 33; j++)
            for (int i = 0; i < 33; i++) lattice[j][i] = r.nextFloat();
        // 3 octaves of wrapped value noise
        for (int y = 0; y < S; y++) {
            for (int x = 0; x < S; x++) {
                float v = 0, amp = 0.55f;
                int cells = 4;
                for (int o = 0; o < 3; o++) {
                    float fx = x / (float) S * cells, fy = y / (float) S * cells;
                    int ix = (int) fx, iy = (int) fy;
                    float tx = fx - ix, ty = fy - iy;
                    tx = tx * tx * (3 - 2 * tx);
                    ty = ty * ty * (3 - 2 * ty);
                    int m = cells;
                    float v00 = lattice[iy % m][ix % m] / 1f;
                    float v10 = lattice[iy % m][(ix + 1) % m];
                    float v01 = lattice[(iy + 1) % m][ix % m];
                    float v11 = lattice[(iy + 1) % m][(ix + 1) % m];
                    // reuse coarse lattice by sampling wrapped (cheap approximation)
                    v00 = wrapNoise(lattice, ix, iy, m);
                    v10 = wrapNoise(lattice, ix + 1, iy, m);
                    v01 = wrapNoise(lattice, ix, iy + 1, m);
                    v11 = wrapNoise(lattice, ix + 1, iy + 1, m);
                    float vv = (v00 * (1 - tx) + v10 * tx) * (1 - ty) + (v01 * (1 - tx) + v11 * tx) * ty;
                    v += vv * amp;
                    amp *= 0.55f;
                    cells *= 2;
                }
                a[y * S + x] = v;
            }
        }
        ByteBuffer buf = BufferUtils.createByteBuffer(S * S * 4);
        float mn = Float.MAX_VALUE, mx = -Float.MAX_VALUE;
        for (float v : a) { mn = Math.min(mn, v); mx = Math.max(mx, v); }
        for (int i = 0; i < S * S; i++) {
            float t = (a[i] - mn) / (mx - mn);
            float alpha = Math.max(0, (t - 0.58f)) / 0.42f;
            alpha = alpha * alpha * (3 - 2 * alpha);
            alpha = Math.min(1, alpha * 1.0f);
            buf.put((byte) 255).put((byte) 255).put((byte) 255).put((byte) (alpha * 255));
        }
        buf.flip();
        tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, S, S, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_REPEAT);
        return tex;
    }

    private static float wrapNoise(float[][] lat, int x, int y, int m) {
        return lat[y % 33][x % 33];
    }

    public void render(float camX, float camZ, Sky sky, boolean fog, float far) {
        if (mesh == null) build();
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        if (tex == -1) buildTexture();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        shader.use();
        Mat4 m = new Mat4().identity();
        m.m[12] = (float) (Math.floor(camX / 4096) * 4096);
        m.m[14] = (float) (Math.floor(camZ / 4096) * 4096);
        shader.setMat4(shader.loc("uModel"), Main.MAT_BUF.put(m.m).flip());
        shader.setFloat(shader.loc("uUnlit"), 1);
        shader.setFloat(shader.loc("uWave"), 0);
        shader.setFloat(shader.loc("uSpecular"), 0);
        shader.setFloat(shader.loc("uFogStart"), 1e9f);
        shader.setFloat(shader.loc("uFogEnd"), 2e9f);
        // clouds tinted by ambient+sun mix for day/night
        float tr = Math.min(1, sky.sunColor[0] * 0.55f + sky.ambTop[0] * 1.5f);
        float tg = Math.min(1, sky.sunColor[1] * 0.55f + sky.ambTop[1] * 1.5f);
        float tb = Math.min(1, sky.sunColor[2] * 0.55f + sky.ambTop[2] * 1.5f);
        shader.setVec4(shader.loc("uTint"), tr, tg, tb, 0.38f);
        mesh.draw();
        shader.setVec4(shader.loc("uTint"), 1, 1, 1, 1);
        shader.setFloat(shader.loc("uUnlit"), 0);
        GL11.glDepthMask(true);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
    }

    public void dispose() {
        if (mesh != null) mesh.dispose();
        if (tex != -1) GL11.glDeleteTextures(tex);
    }
}
