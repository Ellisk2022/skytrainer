package skytrainer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

/**
 * Day/night cycle state + screen-space sky renderer.
 * dayNorm: 0 = midnight, 0.5 = noon. Sun travels east (+X) to west (-X), tilted toward +Z.
 */
public final class Sky {
    // ---- time of day ------------------------------------------------------
    public float dayNorm = 9.5f / 24f;   // start mid morning
    public float dayLengthMin = 12f;     // real minutes per full day

    // ---- computed lighting (updated by update(), read by renderer) --------
    public final float[] sunDir = {0, 1, 0};      // toward the sun
    public final float[] sunColor = {1, 1, 1};    // direct light
    public final float[] ambTop = {0.4f, 0.5f, 0.7f};
    public final float[] ambBot = {0.25f, 0.27f, 0.3f};
    public final float[] zenith = {0.25f, 0.5f, 0.85f};
    public final float[] horizon = {0.7f, 0.8f, 0.95f};
    public final float[] sunTint = {1f, 0.95f, 0.8f};
    public float night = 0;                        // 0 day .. 1 night

    private Shader shader;
    private int vao = -1;
    private int uFwd, uRight, uUp, uTan, uSun, uZen, uHor, uTint, uNight;

    public void update(float dt, float daySpeedScale) {
        dayNorm += dt * daySpeedScale / Math.max(1f, dayLengthMin * 60f);
        if (dayNorm >= 1) dayNorm -= 1;
        if (dayNorm < 0) dayNorm += 1;
        compute();
    }

    public void setTimeOfDay(float hours) {
        dayNorm = hours / 24f;
        compute();
    }

    public float clockHours() { return dayNorm * 24f; }

    private static float smooth(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private static void mix3(float[] out, float[] nightC, float[] duskC, float[] dayC, float e) {
        float t1 = smooth(-0.10f, 0.10f, e); // night -> dusk
        float t2 = smooth(0.08f, 0.45f, e);  // dusk -> day
        for (int i = 0; i < 3; i++) {
            float v = nightC[i] * (1 - t1) + duskC[i] * t1 * (1 - t2) + dayC[i] * t2;
            out[i] = v;
        }
    }

    private void compute() {
        float theta = (dayNorm - 0.25f) * 2f * (float) Math.PI; // 0 at sunrise
        float el = (float) Math.sin(theta);
        float az = (float) Math.cos(theta);
        // east (+X) at sunrise, nearly overhead at noon, west (-X) at sunset; tilted toward +Z
        sunDir[0] = az;
        sunDir[1] = Math.max(el * 0.92f, -0.45f);
        sunDir[2] = 0.35f * (float) Math.cos(theta) + 0.12f;
        float l = (float) Math.sqrt(sunDir[0] * sunDir[0] + sunDir[1] * sunDir[1] + sunDir[2] * sunDir[2]);
        sunDir[0] /= l; sunDir[1] /= l; sunDir[2] /= l;

        float e = sunDir[1];
        night = 1 - smooth(-0.06f, 0.22f, e);

        float[] daySun = {0.88f, 0.86f, 0.80f};
        float[] duskSun = {1.00f, 0.50f, 0.22f};
        float[] nightSun = {0.10f, 0.13f, 0.24f};   // moonlight
        mix3(sunColor, nightSun, duskSun, daySun, e);

        float[] dayTop = {0.42f, 0.53f, 0.74f}, duskTop = {0.30f, 0.26f, 0.40f}, nightTop = {0.055f, 0.075f, 0.16f};
        float[] dayBot = {0.26f, 0.29f, 0.32f}, duskBot = {0.22f, 0.19f, 0.22f}, nightBot = {0.028f, 0.032f, 0.06f};
        mix3(ambTop, nightTop, duskTop, dayTop, e);
        mix3(ambBot, nightBot, duskBot, dayBot, e);

        float[] dayZen = {0.24f, 0.49f, 0.88f}, duskZen = {0.16f, 0.18f, 0.40f}, nightZen = {0.012f, 0.020f, 0.055f};
        float[] dayHor = {0.68f, 0.80f, 0.94f}, duskHor = {0.98f, 0.52f, 0.26f}, nightHor = {0.05f, 0.07f, 0.14f};
        mix3(zenith, nightZen, duskZen, dayZen, e);
        mix3(horizon, nightHor, duskHor, dayHor, e);

        if (e > 0.35) copy3(sunTint, daySun);
        else if (e > -0.05) mix3(sunTint, duskSun, duskSun, daySun, e);
        else copy3(sunTint, nightSun);
    }

    private static void copy3(float[] dst, float[] src) {
        dst[0] = src[0]; dst[1] = src[1]; dst[2] = src[2];
    }

    public float[] fogColor() {
        return new float[]{horizon[0], horizon[1], horizon[2]};
    }

    // ---- rendering ---------------------------------------------------------
    public void initGl() {
        shader = new Shader(Shader.SKY_VERTEX, Shader.SKY_FRAGMENT);
        uFwd = shader.loc("uCamFwd"); uRight = shader.loc("uCamRight"); uUp = shader.loc("uCamUp");
        uTan = shader.loc("uTanHalfFov"); uSun = shader.loc("uSunDir"); uZen = shader.loc("uZenith");
        uHor = shader.loc("uHorizon"); uTint = shader.loc("uSunTint"); uNight = shader.loc("uNight");
        vao = GL30.glGenVertexArrays();
        int vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = BufferUtils.createFloatBuffer(6);
        fb.put(new float[]{-1, -1, 3, -1, -1, 3}).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 8, 0);
        GL30.glBindVertexArray(0);
        GL15.glDeleteBuffers(vbo);
    }

    public void render(float[] camFwd, float[] camRight, float[] camUp, float fovYDeg, float aspect) {
        shader.use();
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        float th = (float) Math.tan(Math.toRadians(fovYDeg) * 0.5);
        GL20.glUniform3f(uFwd, camFwd[0], camFwd[1], camFwd[2]);
        GL20.glUniform3f(uRight, camRight[0], camRight[1], camRight[2]);
        GL20.glUniform3f(uUp, camUp[0], camUp[1], camUp[2]);
        GL20.glUniform2f(uTan, th * aspect, th);
        GL20.glUniform3f(uSun, sunDir[0], sunDir[1], sunDir[2]);
        GL20.glUniform3f(uZen, zenith[0], zenith[1], zenith[2]);
        GL20.glUniform3f(uHor, horizon[0], horizon[1], horizon[2]);
        GL20.glUniform3f(uTint, sunTint[0], sunTint[1], sunTint[2]);
        GL20.glUniform1f(uNight, night);
        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
        GL30.glBindVertexArray(0);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(true);
    }

    public void dispose() {
        if (vao != -1) GL30.glDeleteVertexArrays(vao);
    }
}
