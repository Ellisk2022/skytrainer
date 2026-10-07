package skytrainer;

/** Minimal column-major 4x4 matrix math (OpenGL convention). */
public final class Mat4 {
    public final float[] m = new float[16];

    public Mat4 identity() {
        java.util.Arrays.fill(m, 0f);
        m[0] = m[5] = m[10] = m[15] = 1f;
        return this;
    }

    public static Mat4 perspective(float fovYDeg, float aspect, float near, float far) {
        Mat4 r = new Mat4();
        java.util.Arrays.fill(r.m, 0f);
        float f = 1f / (float) Math.tan(Math.toRadians(fovYDeg) * 0.5f);
        r.m[0] = f / aspect;
        r.m[5] = f;
        r.m[10] = (far + near) / (near - far);
        r.m[11] = -1f;
        r.m[14] = (2f * far * near) / (near - far);
        return r;
    }

    /** Orthographic with y flip so GUI uses top-left origin: left=0, top=0, right=w, bottom=h. */
    public static Mat4 orthoGui(float w, float h) {
        Mat4 r = new Mat4();
        java.util.Arrays.fill(r.m, 0f);
        r.m[0] = 2f / w;
        r.m[5] = -2f / h;
        r.m[10] = -1f;
        r.m[12] = -1f;
        r.m[13] = 1f;
        r.m[15] = 1f;
        return r;
    }

    public static Mat4 lookAt(float ex, float ey, float ez,
                              float tx, float ty, float tz,
                              float upx, float upy, float upz) {
        float zx = ex - tx, zy = ey - ty, zz = ez - tz;
        float zl = (float) Math.sqrt(zx * zx + zy * zy + zz * zz);
        if (zl < 1e-6f) { zx = 0; zy = 0; zz = 1; zl = 1; }
        zx /= zl; zy /= zl; zz /= zl;
        float xx = upy * zz - upz * zy;
        float xy = upz * zx - upx * zz;
        float xz = upx * zy - upy * zx;
        float xl = (float) Math.sqrt(xx * xx + xy * xy + xz * xz);
        if (xl < 1e-6f) { xx = 1; xy = 0; xz = 0; xl = 1; }
        xx /= xl; xy /= xl; xz /= xl;
        float yx = zy * xz - zz * xy;
        float yy = zz * xx - zx * xz;
        float yz = zx * xy - zy * xx;

        Mat4 r = new Mat4();
        r.m[0] = xx; r.m[1] = yx; r.m[2] = zx; r.m[3] = 0f;
        r.m[4] = xy; r.m[5] = yy; r.m[6] = zy; r.m[7] = 0f;
        r.m[8] = xz; r.m[9] = yz; r.m[10] = zz; r.m[11] = 0f;
        r.m[12] = -(xx * ex + xy * ey + xz * ez);
        r.m[13] = -(yx * ex + yy * ey + yz * ez);
        r.m[14] = -(zx * ex + zy * ey + zz * ez);
        r.m[15] = 1f;
        return r;
    }

    /** out = a * b (a applied last, i.e. transform b then a). */
    public static Mat4 mul(Mat4 a, Mat4 b) {
        Mat4 r = new Mat4();
        for (int c = 0; c < 4; c++) {
            for (int row = 0; row < 4; row++) {
                float s = 0f;
                for (int k = 0; k < 4; k++) s += a.m[k * 4 + row] * b.m[c * 4 + k];
                r.m[c * 4 + row] = s;
            }
        }
        return r;
    }

    public Mat4 translate(float x, float y, float z) {
        Mat4 t = new Mat4().identity();
        t.m[12] = x; t.m[13] = y; t.m[14] = z;
        return mul(this, t);
    }

    public Mat4 scale(float sx, float sy, float sz) {
        Mat4 t = new Mat4().identity();
        t.m[0] = sx; t.m[5] = sy; t.m[10] = sz;
        return mul(this, t);
    }

    public static Mat4 rotateX(float rad) {
        Mat4 r = new Mat4().identity();
        float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
        r.m[5] = c; r.m[6] = s; r.m[9] = -s; r.m[10] = c;
        return r;
    }

    public static Mat4 rotateY(float rad) {
        Mat4 r = new Mat4().identity();
        float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
        r.m[0] = c; r.m[2] = -s; r.m[8] = s; r.m[10] = c;
        return r;
    }

    public static Mat4 rotateZ(float rad) {
        Mat4 r = new Mat4().identity();
        float c = (float) Math.cos(rad), s = (float) Math.sin(rad);
        r.m[0] = c; r.m[1] = s; r.m[4] = -s; r.m[5] = c;
        return r;
    }

    /** Extract 6 frustum planes from view-projection matrix. Each plane: a,b,c,d with ax+by+cz+d>=0 inside. */
    public static float[][] frustumPlanes(Mat4 vp) {
        float[] m = vp.m;
        float[][] p = new float[6][4];
        p[0][0] = m[3] + m[0]; p[0][1] = m[7] + m[4]; p[0][2] = m[11] + m[8];  p[0][3] = m[15] + m[12];
        p[1][0] = m[3] - m[0]; p[1][1] = m[7] - m[4]; p[1][2] = m[11] - m[8];  p[1][3] = m[15] - m[12];
        p[2][0] = m[3] + m[1]; p[2][1] = m[7] + m[5]; p[2][2] = m[11] + m[9];  p[2][3] = m[15] + m[13];
        p[3][0] = m[3] - m[1]; p[3][1] = m[7] - m[5]; p[3][2] = m[11] - m[9];  p[3][3] = m[15] - m[13];
        p[4][0] = m[3] + m[2]; p[4][1] = m[7] + m[6]; p[4][2] = m[11] + m[10]; p[4][3] = m[15] + m[14];
        p[5][0] = m[3] - m[2]; p[5][1] = m[7] - m[6]; p[5][2] = m[11] - m[10]; p[5][3] = m[15] - m[14];
        for (float[] pl : p) {
            float l = (float) Math.sqrt(pl[0] * pl[0] + pl[1] * pl[1] + pl[2] * pl[2]);
            if (l > 1e-6f) { pl[0] /= l; pl[1] /= l; pl[2] /= l; pl[3] /= l; }
        }
        return p;
    }

    /** AABB vs frustum test. */
    public static boolean aabbInFrustum(float[][] planes, float x0, float y0, float z0, float x1, float y1, float z1) {
        for (float[] pl : planes) {
            float px = pl[0] > 0 ? x1 : x0;
            float py = pl[1] > 0 ? y1 : y0;
            float pz = pl[2] > 0 ? z1 : z0;
            if (pl[0] * px + pl[1] * py + pl[2] * pz + pl[3] < 0) return false;
        }
        return true;
    }
}
