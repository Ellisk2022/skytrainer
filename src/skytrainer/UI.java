package skytrainer;

import org.lwjgl.opengl.GL11;

/** Aviation-flavored GUI toolkit on the Minecraft-style widget skin: buttons, sliders, panels, text. */
public final class UI {
    public final Shader shader;
    public final Atlas atlas;
    public final Font font;
    public final DynBatch batch = new DynBatch(4096);

    public int fbW, fbH;      // framebuffer pixels
    public int scale;         // GUI scale
    public int uiW, uiH;      // GUI-space size
    public float mx, my;      // mouse in GUI space

    private static final float[] WHITE_UV = Atlas.tileUV(Atlas.T_WHITE);
    private static final float[] PANEL_UV = Atlas.tileUV(Atlas.T_PANEL);
    private static final float[] RIVET_UV = Atlas.tileUV(Atlas.T_RIVET);

    public UI(Shader shader, Atlas atlas, Font font) {
        this.shader = shader;
        this.atlas = atlas;
        this.font = font;
    }

    public void begin(int fbWidth, int fbHeight, float mouseX, float mouseY) {
        fbW = fbWidth; fbH = fbHeight;
        int s = (int) Math.min(fbW / 320f, fbH / 240f);
        scale = Math.max(1, Math.min(4, s));
        uiW = fbW / scale;
        uiH = fbH / scale;
        mx = mouseX;
        my = mouseY;

        shader.use();
        shader.setMat4(shader.loc("uProj"), Main.MAT_BUF.put(Mat4.orthoGui(uiW, uiH).m).flip());
        shader.setMat4(shader.loc("uView"), Main.MAT_BUF.put(new Mat4().identity().m).flip());
        shader.setVec4(shader.loc("uTint"), 1, 1, 1, 1);
        shader.setInt(shader.loc("uTex"), 0);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
    }

    public void end() {
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
    }

    public void tint(float r, float g, float b, float a) {
        shader.use();
        shader.setVec4(shader.loc("uTint"), r, g, b, a);
    }

    // ------------------------------------------------------------ primitives
    public void rect(float x, float y, float w, float h, float r, float g, float b, float a) {
        tint(r, g, b, a);
        atlas.bind(0);
        batch.begin();
        batch.quad(x, y, x + w, y + h, WHITE_UV, 0, 1, 1, 1);
        batch.draw();
    }

    public void border(float x, float y, float w, float h, float t, float r, float g, float b, float a) {
        rect(x, y, w, t, r, g, b, a);
        rect(x, y + h - t, w, t, r, g, b, a);
        rect(x, y + t, t, h - 2 * t, r, g, b, a);
        rect(x + w - t, y + t, t, h - 2 * t, r, g, b, a);
    }

    public void textured(float x, float y, float w, float h, float[] uv, float r, float g, float b, float a) {
        tint(r, g, b, a);
        atlas.bind(0);
        batch.begin();
        batch.quad(x, y, x + w, y + h, uv, 0, 1, 1, 1);
        batch.draw();
    }

    // ----------------------------------------------------------------- text
    public static String sanitize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append((c >= 32 && c < 127) ? c : ' ');
        }
        return sb.toString();
    }

    public void text(float x, float y, String s, float r, float g, float b, float scl, boolean shadow) {
        String t = sanitize(s);
        if (t.isEmpty()) return;
        font.bind(0);
        if (shadow) drawGlyphs(x + scl, y + scl, t, r * 0.25f, g * 0.25f, b * 0.25f, scl);
        drawGlyphs(x, y, t, r, g, b, scl);
    }

    private void drawGlyphs(float x, float y, String t, float r, float g, float b, float scl) {
        tint(r, g, b, 1);
        batch.begin();
        float cx = (float) Math.floor(x);
        float ty = (float) Math.floor(y);
        for (int i = 0; i < t.length(); i++) {
            float[] uv = Font.glyphUV(t.charAt(i));
            batch.quad(cx, ty, cx + Font.ADVANCE * scl, ty + Font.GLYPH_H * scl, uv, 0, 1, 1, 1);
            cx += Font.ADVANCE * scl;
        }
        batch.draw();
    }

    /** Rotated per-glyph text (used for the splash). */
    public void textRotated(float pivotX, float pivotY, String s, float r, float g, float b, float scl,
                            float angle, float scalePulse) {
        String t = sanitize(s);
        if (t.isEmpty()) return;
        font.bind(0);
        tint(r, g, b, 1);
        float totalW = t.length() * Font.ADVANCE * (scl * scalePulse);
        float totalH = Font.GLYPH_H * (scl * scalePulse);
        float ca = (float) Math.cos(angle), sa = (float) Math.sin(angle);
        batch.begin();
        float cx = -totalW / 2f;
        for (int i = 0; i < t.length(); i++) {
            float[] uv = Font.glyphUV(t.charAt(i));
            float gw = Font.ADVANCE * scl * scalePulse, gh = Font.GLYPH_H * scl * scalePulse;
            float[][] local = {{cx, 0}, {cx, gh}, {cx + gw, gh}, {cx + gw, 0}};
            float[] px = new float[4], py = new float[4];
            for (int k = 0; k < 4; k++) {
                float dx = local[k][0];
                float dy = local[k][1] - totalH / 2f;
                px[k] = pivotX + dx * ca - dy * sa;
                py[k] = pivotY + dx * sa + dy * ca;
            }
            float[] ux = {uv[0], uv[0], uv[2], uv[2]};
            float[] vy = {uv[1], uv[3], uv[3], uv[1]};
            batch.poly(px, py, ux, vy, 0, 1, 1, 1);
            cx += gw;
        }
        batch.draw();
    }

    public void textShadow(float x, float y, String s, float r, float g, float b, float scl) {
        text(x, y, s, r, g, b, scl, true);
    }

    public void textCenter(float cx, float y, String s, float r, float g, float b, float scl, boolean shadow) {
        text((float) Math.floor(cx - Font.width(sanitize(s), (int) scl) / 2f), y, s, r, g, b, scl, shadow);
    }

    public void textRight(float rx, float y, String s, float r, float g, float b, float scl, boolean shadow) {
        text((float) Math.floor(rx - Font.width(sanitize(s), (int) scl)), y, s, r, g, b, scl, shadow);
    }

    // ------------------------------------------------------------- widgets
    public static final class Button {
        public float x, y, w, h;
        public String label, action;
        public boolean enabled = true, hover;

        public Button(float x, float y, float w, float h, String label, String action) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.label = label; this.action = action;
        }

        public boolean contains(float mx, float my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        public void render(UI ui) {
            hover = enabled && contains(ui.mx, ui.my);
            if (enabled) {
                ui.textured(x, y, w, h, Atlas.buttonUV(), hover ? 1.25f : 1f, hover ? 1.3f : 1f, hover ? 1.45f : 1f, 1);
            } else {
                ui.textured(x, y, w, h, Atlas.buttonUV(), 0.5f, 0.5f, 0.5f, 1);
            }
            float tr = enabled ? (hover ? 1f : 0.875f) : 0.62f;
            float tg = enabled ? (hover ? 1f : 0.875f) : 0.62f;
            float tb = enabled ? (hover ? 0.62f : 0.875f) : 0.62f;
            ui.textCenter(x + w / 2f, y + (h - Font.GLYPH_H) / 2f + 1, label, tr, tg, tb, 1, true);
        }
    }

    public static final class Slider {
        public interface Format { String format(float v); }

        public float x, y, w, h;
        public String action;
        public float value;          // 0..1
        public Format fmt;
        public boolean hover, dragging;

        public Slider(float x, float y, float w, float h, String action, float value, Format fmt) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.action = action; this.value = value; this.fmt = fmt;
        }

        public boolean contains(float mx, float my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        public void setFromMouse(float mx) {
            float handleW = 8;
            float t = (mx - x - handleW / 2f) / (w - handleW);
            value = Math.max(0, Math.min(1, t));
        }

        public void render(UI ui) {
            hover = contains(ui.mx, ui.my);
            ui.textured(x, y, w, h, Atlas.buttonUV(), 0.35f, 0.35f, 0.38f, 1);
            float handleW = 8;
            float hx = x + value * (w - handleW);
            ui.textured(hx, y, handleW, h, Atlas.buttonUV(), 1.35f, 1.4f, 1.55f, 1);
            String label = fmt.format(value);
            ui.textCenter(x + w / 2f, y + (h - Font.GLYPH_H) / 2f + 1, label, hover ? 1f : 0.875f, hover ? 1f : 0.875f, hover ? 0.62f : 0.875f, 1, true);
        }
    }

    // ------------------------------------------------------------- surfaces
    /** Tiled dark metal panel background (menus). */
    public void panelBackground() {
        float[] uv = PANEL_UV;
        tint(1, 1, 1, 1);
        atlas.bind(0);
        batch.begin();
        for (int y = 0; y < uiH; y += 16) {
            for (int x = 0; x < uiW; x += 16) {
                batch.quad(x, y, Math.min(x + 16, uiW), Math.min(y + 16, uiH), uv, 0, 1, 1, 1);
            }
        }
        batch.draw();
        // subtle vignette
        rect(0, 0, uiW, uiH, 0, 0, 0, 0.35f);
    }

    /** CYAC-style framed menu panel using Theme colors. Dark outer frame, light inset line, gray fill, underlined header. */
    public void cyacPanel(float x, float y, float w, float h, String header) {
        int o = Theme.OUTER_FRAME_PX;
        int i = Theme.INSET_FRAME_PX;
        rect(x - o - i, y - o - i, w + 2*(o+i), h + 2*(o+i),
                Theme.PANEL_OUTER_R, Theme.PANEL_OUTER_G, Theme.PANEL_OUTER_B, 1f);
        rect(x - i, y - i, w + 2*i, h + 2*i,
                Theme.PANEL_INSET_R, Theme.PANEL_INSET_G, Theme.PANEL_INSET_B, 1f);
        rect(x, y, w, h,
                Theme.PANEL_FILL_R, Theme.PANEL_FILL_G, Theme.PANEL_FILL_B, 1f);
        float htx = x + w / 2f;
        float hty = y + 6;
        textCenter(htx, hty, header, Theme.TEXT_DARK_R, Theme.TEXT_DARK_G, Theme.TEXT_DARK_B, 1, true);
        float hw = Font.width(sanitize(header), 1);
        rect(htx - hw / 2f, hty + Font.GLYPH_H + 2, hw, 1,
                Theme.TEXT_DARK_R, Theme.TEXT_DARK_G, Theme.TEXT_DARK_B, 1f);
    }

    /** CYAC-style button: 1px dark outline, beveled fill with inverted bevels on hover/press,
     *  and ►/◄ arrow markers on the sides when hovered. */
    public void cyacButton(float x, float y, float w, float h, String label, boolean hover, boolean pressed) {
        int b = Theme.BEVEL_PX;
        float fr, fg, fb, tr, tg, tb;
        if (pressed) {
            fr = Theme.BTN_PRESS_FILL_R; fg = Theme.BTN_PRESS_FILL_G; fb = Theme.BTN_PRESS_FILL_B;
            tr = Theme.BTN_PRESS_TEXT_R; tg = Theme.BTN_PRESS_TEXT_G; tb = Theme.BTN_PRESS_TEXT_B;
        } else if (hover) {
            fr = Theme.BTN_HOVER_FILL_R; fg = Theme.BTN_HOVER_FILL_G; fb = Theme.BTN_HOVER_FILL_B;
            tr = Theme.BTN_HOVER_TEXT_R; tg = Theme.BTN_HOVER_TEXT_G; tb = Theme.BTN_HOVER_TEXT_B;
        } else {
            fr = Theme.BTN_FILL_R; fg = Theme.BTN_FILL_G; fb = Theme.BTN_FILL_B;
            tr = Theme.BTN_TEXT_R; tg = Theme.BTN_TEXT_G; tb = Theme.BTN_TEXT_B;
        }
        if (hover || pressed) {
            rect(x - 1, y - 1, w + 2, h + 2, 0.10f, 0.10f, 0.12f, 1f);
        }
        rect(x, y, w, h, fr, fg, fb, 1f);
        float hlR, hlG, hlB, shR, shG, shB;
        if (hover || pressed) {
            // inverted bevels = sunken look
            hlR = Theme.BTN_SHADOW_R; hlG = Theme.BTN_SHADOW_G; hlB = Theme.BTN_SHADOW_B;
            shR = Theme.BTN_HILITE_R; shG = Theme.BTN_HILITE_G; shB = Theme.BTN_HILITE_B;
        } else {
            hlR = Theme.BTN_HILITE_R; hlG = Theme.BTN_HILITE_G; hlB = Theme.BTN_HILITE_B;
            shR = Theme.BTN_SHADOW_R; shG = Theme.BTN_SHADOW_G; shB = Theme.BTN_SHADOW_B;
        }
        rect(x, y, w, b, hlR, hlG, hlB, 1f);
        rect(x, y + b, b, h - 2*b, hlR, hlG, hlB, 1f);
        rect(x, y + h - b, w, b, shR, shG, shB, 1f);
        rect(x + w - b, y + b, b, h - 2*b, shR, shG, shB, 1f);
        float tx = x + w / 2f;
        float ty = y + (h - Font.GLYPH_H) / 2f + 1;
        if (pressed) { tx += 1; ty += 1; }
        textCenter(tx, ty, label, tr, tg, tb, 1, false);
        if (hover) {
            float cy = y + h / 2f;
            float size = 4f;
            float leftX = x + 8f;
            float rightX = x + w - 8f;
            drawTriangleRight(leftX, cy, size, tr, tg, tb);
            drawTriangleLeft(rightX, cy, size, tr, tg, tb);
        }
    }

    /** Filled right-pointing triangle for the selected-item marker. */
    public void drawTriangleRight(float tipX, float cy, float size, float r, float g, float b) {
        tint(r, g, b, 1);
        atlas.bind(0);
        batch.begin();
        float h = size;
        float w = size * 0.9f;
        float[] px = {tipX, tipX - w, tipX - w, tipX};
        float[] py = {cy, cy - h, cy + h, cy};
        float[] ux = {WHITE_UV[0], WHITE_UV[2], WHITE_UV[2], WHITE_UV[0]};
        float[] vy = {WHITE_UV[1], WHITE_UV[3], WHITE_UV[1], WHITE_UV[1]};
        batch.poly(px, py, ux, vy, 0, 1, 1, 1);
        batch.draw();
    }

    /** Filled left-pointing triangle for the selected-item marker. */
    public void drawTriangleLeft(float tipX, float cy, float size, float r, float g, float b) {
        tint(r, g, b, 1);
        atlas.bind(0);
        batch.begin();
        float h = size;
        float w = size * 0.9f;
        float[] px = {tipX, tipX + w, tipX + w, tipX};
        float[] py = {cy, cy - h, cy + h, cy};
        float[] ux = {WHITE_UV[0], WHITE_UV[2], WHITE_UV[2], WHITE_UV[0]};
        float[] vy = {WHITE_UV[1], WHITE_UV[3], WHITE_UV[1], WHITE_UV[1]};
        batch.poly(px, py, ux, vy, 0, 1, 1, 1);
        batch.draw();
    }

    /** Riveted instrument-panel strip along the bottom (cockpit view). */
    public void cockpitPanel(float height) {
        float[] uv = RIVET_UV;
        tint(1, 1, 1, 1);
        atlas.bind(0);
        batch.begin();
        for (int y = uiH - (int) height; y < uiH; y += 16) {
            for (int x = 0; x < uiW; x += 16) {
                batch.quad(x, y, Math.min(x + 16, uiW), Math.min(y + 16, uiH), uv, 0, 1, 1, 1);
            }
        }
        batch.draw();
        rect(0, uiH - height, uiW, 1, 0, 0, 0, 0.8f);
    }

    public void dim(float a) {
        rect(0, 0, uiW, uiH, 0, 0, 0, a);
    }
}
