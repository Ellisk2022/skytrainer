package skytrainer;

/** Aviation-style HUD: heading tape, airspeed/altitude/VSI boxes, throttle bar, stall warning. */
public final class Hud {
    private final UI ui;
    private float flash;

    public Hud(UI ui) {
        this.ui = ui;
    }

    public void render(Aircraft ac, Options opts, int fps, Sky sky, boolean cockpit, boolean showFps) {
        if (ac == null) return;
        float W = ui.uiW, H = ui.uiH;
        float cx = W / 2f;

        if (cockpit) {
            ui.cockpitPanel(46);
        }

        // ---------------- heading tape (top center) ----------------
        float tapeW = 160, tapeH = 14;
        float tapeX = cx - tapeW / 2f, tapeY = 6;
        ui.rect(tapeX, tapeY, tapeW, tapeH, 0, 0, 0, 0.45f);
        float hdg = ac.headingDeg();
        // ticks every 5 deg: 160 px covers 60 deg -> 2.667 px/deg
        float pxPerDeg = tapeW / 60f;
        ui.border(tapeX, tapeY, tapeW, tapeH, 1, 0.65f, 0.7f, 0.7f, 0.6f);
        for (int d = -35; d <= 35; d += 5) {
            float hpos = cx + d * pxPerDeg - ((hdg % 5) + 5) % 5 * pxPerDeg;
            float deg = (hdg - ((hdg % 5) + 5) % 5) + d + 5;
            if (hpos < tapeX + 2 || hpos > tapeX + tapeW - 2) continue;
            int dd = (int) Math.floor(deg) % 360;
            boolean major = dd % 30 == 0;
            ui.rect(hpos, tapeY + (major ? 2 : 6), 1, major ? 6 : 3, 0.85f, 0.9f, 0.9f, 0.9f);
            if (major) {
                String lbl = dd == 0 ? "N" : dd == 90 ? "E" : dd == 180 ? "S" : dd == 270 ? "W"
                        : String.valueOf(dd / 10);
                ui.text(hpos - Font.width(lbl, 1) / 2f, tapeY + 8, lbl, 0.85f, 0.9f, 0.9f, 1, false);
            }
        }
        // heading readout box
        String hdgTxt = String.format("%03d", (int) hdg);
        ui.rect(cx - 10, tapeY + tapeH, 20, 10, 0, 0, 0, 0.6f);
        ui.text(cx - Font.width(hdgTxt, 1) / 2f, tapeY + tapeH + 2, hdgTxt, 1, 0.85f, 0.2f, 1, true);

        // ---------------- airspeed box (left) ----------------
        float boxY = H / 2f - 24;
        instrumentBox(10, boxY, 62, "IAS", String.format("%d KT", Math.round(ac.speedKt())),
                ac.speedKt() < 52 && !ac.onGround ? 1f : 0.9f, 0.95f, 0.9f);
        if (ac.onGround) {
            ui.text(12, boxY + 24, ac.speed() < Aircraft.ROTATE_SPEED ? "ROTATE " + Math.round(Aircraft.ROTATE_SPEED * 1.94384f) + " KT"
                    : "PULL UP", 0.75f, 0.85f, 0.75f, 1, true);
        }

        // ---------------- altitude box (right) ----------------
        float altW = 74;
        instrumentBox(W - altW - 10, boxY, altW, "ALT", String.format("%d FT", Math.round(ac.altFieldFt())),
                0.9f, 0.95f, 0.9f);
        float vs = ac.vsiFpm();
        String vsTxt = String.format("%s%d", vs >= 0 ? "+" : "", Math.round(vs / 10f) * 10);
        ui.text(W - altW - 8, boxY + 24, "VS " + vsTxt, 0.8f, 0.9f, 0.8f, 1, true);
        float agl = ac.agl();
        if (agl < 150) {
            String aTxt = "AGL " + Math.max(0, Math.round(agl * 3.28084f));
            ui.text(W - altW - 8, boxY + 34, aTxt, agl < 40 ? 1f : 0.8f, agl < 40 ? 0.5f : 0.9f, 0.8f, 1, true);
        }

        // ---------------- throttle / rpm (bottom left) ----------------
        float tbX = 10, tbY = H - 26, tbW = 70;
        ui.text(tbX, tbY - 10, "THR", 0.8f, 0.85f, 0.8f, 1, true);
        ui.rect(tbX, tbY, tbW, 7, 0, 0, 0, 0.5f);
        ui.border(tbX, tbY, tbW, 7, 1, 0.7f, 0.75f, 0.75f, 0.7f);
        ui.rect(tbX + 1, tbY + 1, (tbW - 2) * ac.throttle, 5, 0.2f, 0.85f, 0.3f, 0.9f);
        String rpmTxt = Math.round(ac.rpm) + " RPM";
        ui.text(tbX + tbW + 6, tbY - 1, rpmTxt, 0.85f, 0.9f, 0.85f, 1, true);
        if (ac.brake) {
            ui.text(tbX + tbW + 6, tbY + 8, "BRK", 1f, 0.6f, 0.2f, 1, true);
        }

        // ---------------- clock (top right) ----------------
        float hrs = sky.clockHours();
        int hh = (int) hrs, mm = (int) ((hrs - hh) * 60);
        String clk = String.format("%02d:%02d", hh, mm);
        ui.text(W - Font.width(clk, 1) - 10, 8, clk, 0.9f, 0.92f, 0.95f, 1, true);

        // ---------------- stall warning ----------------
        flash += 0.06f;
        if (ac.stalled) {
            boolean on = (flash % 1f) < 0.55f;
            if (on) {
                ui.textCenter(cx, H * 0.32f, "S T A L L", 1f, 0.25f, 0.2f, 2, true);
            }
        }
        // over-speed dive hint
        if (ac.speed() > 120) {
            ui.textCenter(cx, H * 0.32f - 18, "OVERSPEED", 1f, 0.7f, 0.2f, 1, true);
        }
        // ground-roll takeoff hint
        if (ac.onGround && ac.speed() > 20f) {
            ui.textCenter(cx, H * 0.62f, "HOLD THE PULL (MOUSE DOWN) TO ROTATE AT 52 KT",
                    1f, 0.9f, 0.5f, 1, true);
        }

        if (showFps) {
            ui.text(6, H - 10, fps + " FPS  X " + Math.round(ac.x) + " Y " + Math.round(ac.y)
                    + " Z " + Math.round(ac.z), 0.7f, 0.75f, 0.7f, 1, false);
        }
    }

    private void instrumentBox(float x, float y, float w, String label, String value,
                               float r, float g, float b) {
        ui.rect(x, y, w, 20, 0, 0, 0, 0.45f);
        ui.border(x, y, w, 20, 1, 0.65f, 0.7f, 0.7f, 0.6f);
        ui.text(x + 3, y + 2, label, 0.6f, 0.7f, 0.7f, 1, false);
        ui.text(x + 3, y + 10, value, r, g, b, 1, true);
    }
}
