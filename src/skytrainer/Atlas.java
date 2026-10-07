package skytrainer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.BufferUtils;

import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Procedurally generated texture atlas (256x256).
 * Layout: tile (0,0) white, tile (1,0) dark panel, tile (2,0) rivet panel;
 * the classic 200x20 stone button sits at (0,64).
 */
public final class Atlas {
    public static final int SIZE = 256;
    public static final int TILE = 16;

    public static final int T_WHITE = 0;    // tile col 0 row 0
    public static final int T_PANEL = 1;    // dark flat panel (menu bg)
    public static final int T_RIVET = 2;    // panel with rivets (HUD backing)

    private final BufferedImage img;
    private int texId = -1;

    public Atlas() {
        img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Random r = new Random(0x51CE);
        tile(T_PANEL, r).fill(34, 36, 40).jitter(0.10f).speckle(0.05f, -10, -10, -12);
        rivets(r);
        white();
        button(r);
    }

    private TilePainter tile(int index, Random r) {
        return new TilePainter(img, (index & 15) * TILE, (index >> 4) * TILE, r);
    }

    private void rivets(Random r) {
        TilePainter p = tile(T_RIVET, r);
        for (int y = 0; y < TILE; y++) for (int x = 0; x < TILE; x++) p.set(x, y, 40, 43, 48, 255);
        int[][] pts = {{2, 2}, {13, 2}, {2, 13}, {13, 13}, {7, 7}};
        for (int[] q : pts) {
            p.set(q[0], q[1], 78, 82, 90, 255);
            p.set(q[0] + 1, q[1], 26, 28, 32, 255);
            p.set(q[0], q[1] + 1, 26, 28, 32, 255);
        }
    }

    private void white() {
        TilePainter p = tile(T_WHITE, new Random(1));
        for (int y = 0; y < TILE; y++) for (int x = 0; x < TILE; x++) p.set(x, y, 255, 255, 255, 255);
    }

    /** Minecraft-style 200x20 stone button at region (0,64)-(200,84). */
    private void button(Random r) {
        int bw = 200, bh = 20;
        for (int y = 0; y < bh; y++) {
            for (int x = 0; x < bw; x++) {
                float j = 1f + (r.nextFloat() - 0.5f) * 0.10f;
                int v = clamp8((int) (122 * j));
                int c;
                boolean border = (x == 0 || y == 0 || x == bw - 1 || y == bh - 1);
                boolean bevelLight = (x == 1 || y == 1);
                boolean bevelDark = (x == bw - 2 || y == bh - 2);
                if (border) c = 0xFF000000;
                else if (bevelLight) c = rgb(168, 168, 168);
                else if (bevelDark) c = rgb(66, 66, 66);
                else c = rgb(v, v, v);
                img.setRGB(x, 64 + y, c);
            }
        }
    }

    public void upload() {
        texId = uploadImage(img);
    }

    public void bind(int unit) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texId);
    }

    /** UV rect for a 16px tile with a tiny inset to avoid bleeding: {u0,v0,u1,v1}. */
    public static float[] tileUV(int index) {
        float s = 1f / 16f;
        float u0 = (index & 15) * s, v0 = (index >> 4) * s;
        float inset = 0.15f * s / TILE;
        return new float[]{u0 + inset, v0 + inset, u0 + s - inset, v0 + s - inset};
    }

    /** UV rect for the 200x20 button region. */
    public static float[] buttonUV() {
        float inset = 0.5f / SIZE;
        return new float[]{inset, 64f / SIZE + inset, 200f / SIZE - inset, 84f / SIZE - inset};
    }

    /** Uploads an image as an RGBA byte texture; returns the GL texture id. */
    public static int uploadImage(BufferedImage image) {
        int w = image.getWidth(), h = image.getHeight();
        ByteBuffer buf = BufferUtils.createByteBuffer(w * h * 4);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = image.getRGB(x, y);
                buf.put((byte) ((c >> 16) & 0xFF)); // R
                buf.put((byte) ((c >> 8) & 0xFF));  // G
                buf.put((byte) (c & 0xFF));         // B
                buf.put((byte) ((c >> 24) & 0xFF)); // A
            }
        }
        buf.flip();
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return tex;
    }

    private static int rgb(int r, int g, int b) { return 0xFF000000 | (r << 16) | (g << 8) | b; }

    private static int clamp8(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    /** Small fluent helper that paints one 16px tile. */
    private static final class TilePainter {
        private final BufferedImage img;
        private final int ox, oy;
        private final Random r;

        TilePainter(BufferedImage img, int ox, int oy, Random r) {
            this.img = img; this.ox = ox; this.oy = oy; this.r = r;
        }

        TilePainter fill(int r8, int g8, int b8) {
            for (int y = 0; y < TILE; y++) for (int x = 0; x < TILE; x++) set(x, y, r8, g8, b8, 255);
            return this;
        }

        TilePainter jitter(float amt) {
            for (int y = 0; y < TILE; y++) {
                for (int x = 0; x < TILE; x++) {
                    int c = img.getRGB(ox + x, oy + y);
                    float j = 1f + (r.nextFloat() - 0.5f) * 2f * amt;
                    set(x, y, clamp8((int) (((c >> 16) & 0xFF) * j)), clamp8((int) (((c >> 8) & 0xFF) * j)),
                            clamp8((int) ((c & 0xFF) * j)), 255);
                }
            }
            return this;
        }

        TilePainter speckle(float chance, int dr, int dg, int db) {
            for (int y = 0; y < TILE; y++) {
                for (int x = 0; x < TILE; x++) {
                    if (r.nextFloat() < chance) {
                        int c = img.getRGB(ox + x, oy + y);
                        set(x, y, clamp8(((c >> 16) & 0xFF) + dr), clamp8(((c >> 8) & 0xFF) + dg), clamp8((c & 0xFF) + db), 255);
                    }
                }
            }
            return this;
        }

        void set(int x, int y, int r8, int g8, int b8, int a8) {
            img.setRGB(ox + x, oy + y, (a8 << 24) | (r8 << 16) | (g8 << 8) | b8);
        }
    }
}
