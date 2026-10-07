package skytrainer;

import java.util.Random;

/** All menu screens: title, options, controls, loading, pause, crash overlay. */
public final class Screens {
    private static final float[] TITLE_COL = {0.95f, 0.95f, 0.98f};
    private static final float[] ACCENT = {1f, 0.72f, 0.16f};

    // ==================================================================== Title
    public static final class TitleScreen {
        private final String splash;
        private final UI.Button[] buttons = {
                new UI.Button(0, 0, 200, 20, "Start Flight", "start"),
                new UI.Button(0, 0, 200, 20, "Air Start (1000 ft)", "airstart"),
                new UI.Button(0, 0, 200, 20, "Options...", "options"),
                new UI.Button(0, 0, 200, 20, "Controls...", "controls"),
                new UI.Button(0, 0, 200, 20, "Quit Game", "quit"),
        };
        private long time;

        public TitleScreen() {
            String[] splashes = {
                    "Now with lift!", "Pull up!", "Blue skies!", "V1... rotate!",
                    "100% procedural!", "Cleared for takeoff!", "Mind the CG!",
                    "Keep it wings level!", "The prop is a fan!", "Wheee!",
            };
            splash = splashes[new Random().nextInt(splashes.length)];
        }

        /** Renders; returns the action of the clicked button or null. */
        public String render(UI ui, Input in, Options opts, float t) {
            float W = ui.uiW, H = ui.uiH;
            float cx = W / 2f;
            int bw = 200;
            for (int i = 0; i < buttons.length; i++) {
                UI.Button b = buttons[i];
                b.x = 20;
                b.y = H * 0.34f + i * 24;
                b.render(ui);
                if (b.hover && in.mousePressed(0)) return b.action;
            }
            // logo — SKY in accent, TRAINER in white
            float logoScl = 3;
            String l1 = "SKY", l2 = "TRAINER";
            float y0 = H * 0.10f;
            float w1 = Font.width(l1, (int) logoScl);
            ui.text(cx - (w1 + Font.width(l2, (int) logoScl)) / 2f, y0, l1, 1f, 0.78f, 0.2f, logoScl, true);
            ui.text(cx - (w1 + Font.width(l2, (int) logoScl)) / 2f + w1, y0, l2, TITLE_COL[0], TITLE_COL[1], TITLE_COL[2], logoScl, true);
            ui.textCenter(cx, y0 + Font.GLYPH_H * logoScl + 4, "A procedural flight simulator", 0.85f, 0.88f, 0.92f, 1, true);
            // splash
            float pulse = 1f + (float) Math.sin(time * 0.14) * 0.05f;
            ui.textRotated(cx + 92, y0 + 14, splash, 1f, 1f, 0.25f, 1,
                    (float) Math.toRadians(-18), pulse);
            time++;
            // footer
            ui.text(4, H - 12, "SkyTrainer 0.1.0", 0.7f, 0.7f, 0.75f, 1, false);
            ui.textRight(W - 4, H - 12, "Procedural Aviation Demo", 0.7f, 0.7f, 0.75f, 1, false);
            return null;
        }
    }

    // ==================================================================== Options
    public static final class OptionsScreen {
        public final boolean fromPause;
        private final UI.Slider fov, dist, sens, vol, dayLen, clock;
        private final UI.Button done = new UI.Button(0, 0, 200, 20, "Done", "done");
        private final UI.Button controls = new UI.Button(0, 0, 200, 20, "Controls...", "controls");
        private final UI.Button music = new UI.Button(0, 0, 98, 20, "", "music");
        private final UI.Button fog = new UI.Button(0, 0, 98, 20, "", "fog");
        private final UI.Button clouds = new UI.Button(0, 0, 98, 20, "", "clouds");
        private final UI.Button invY = new UI.Button(0, 0, 98, 20, "", "inverty");

        public OptionsScreen(boolean fromPause, Options opts) {
            this.fromPause = fromPause;
            fov = new UI.Slider(0, 0, 200, 20, "fov", (opts.fov - 50) / 50f,
                    v -> Options.formatFov(50 + v * 50));
            dist = new UI.Slider(0, 0, 200, 20, "dist", (opts.renderDistance - 4) / 16f,
                    v -> "Render Distance: " + Math.round(4 + v * 16) + " tiles");
            sens = new UI.Slider(0, 0, 200, 20, "sens", opts.sensitivity / 2f,
                    v -> "Sensitivity: " + (v < 0.05 ? "min" : Math.round(v * 200) + "%"));
            vol = new UI.Slider(0, 0, 200, 20, "vol", opts.masterVolume,
                    v -> "Volume: " + (v <= 0.001 ? "OFF" : Math.round(v * 100) + "%"));
            dayLen = new UI.Slider(0, 0, 200, 20, "daylen", (opts.dayLength - 1) / 39f,
                    v -> String.format("Day Length: %.0f min", 1 + v * 39));
            clock = new UI.Slider(0, 0, 200, 20, "clock", opts.startClock / 24f,
                    v -> String.format("Time: %02d:%02d", (int) (v * 24), (int) ((v * 24 % 1) * 60)));
        }

        public String render(UI ui, Input in, Options opts, float t) {
            float W = ui.uiW, H = ui.uiH;
            float cx = W / 2f;
            int row = 0;
            UI.Slider[] sliders = {fov, dist, sens, vol, dayLen, clock};
            for (UI.Slider s : sliders) {
                s.x = cx - 100;
                s.y = 42 + row * 24;
                boolean drag = s.dragging && in.mouseDown(0);
                s.render(ui);
                if (s.contains(ui.mx, ui.my) && in.mouseDown(0)) s.setFromMouse(ui.mx);
                s.dragging = s.contains(ui.mx, ui.my) && in.mouseDown(0);
                row++;
            }
            apply(opts);
            // toggles grid
            music.label = "Music: " + (opts.music ? "ON" : "OFF");
            fog.label = "Fog: " + (opts.fog ? "ON" : "OFF");
            clouds.label = "Clouds: " + (opts.clouds ? "ON" : "OFF");
            invY.label = "Invert Y: " + (opts.invertY ? "ON" : "OFF");
            UI.Button[] toggles = {music, fog, clouds, invY};
            for (int i = 0; i < toggles.length; i++) {
                UI.Button b = toggles[i];
                b.x = cx - 100 + (i % 2) * 102;
                b.y = 42 + row * 24 + (i / 2) * 24;
                b.render(ui);
                if (b.hover && in.mousePressed(0)) return b.action;
            }
            row += 2;
            controls.x = cx - 100;
            controls.y = 42 + row * 24 + 4;
            controls.render(ui);
            if (controls.hover && in.mousePressed(0)) return "controls";
            done.x = cx - 100;
            done.y = H - 40;
            done.render(ui);
            if (done.hover && in.mousePressed(0)) return "done";
            ui.textCenter(cx, 24, "Options", 1, 1, 1, 2, true);
            return null;
        }

        private void apply(Options opts) {
            opts.fov = 50 + fov.value * 50;
            opts.renderDistance = Math.round(4 + dist.value * 16);
            opts.sensitivity = sens.value * 2f;
            opts.masterVolume = vol.value;
            opts.dayLength = 1 + dayLen.value * 39f;
            opts.startClock = clock.value * 24f;
        }
    }

    // ==================================================================== Controls
    public static final class ControlsScreen {
        private static final String[][] KEYS = {
                {"Mouse", "Pitch (stick) and roll (yoke)"},
                {"Mouse down", "Pull nose up (stick style)"},
                {"W / S", "Throttle up / down"},
                {"A / D", "Rudder — ground steering"},
                {"Arrow keys", "Elevator / ailerons (backup)"},
                {"B or Space", "Wheel brakes"},
                {"C or F5", "Toggle chase / cockpit view"},
                {"R", "Reset to runway"},
                {"T", "Skip time +3 hours"},
                {"F1", "Hide HUD"},
                {"F2", "Screenshot"},
                {"F3", "Debug overlay"},
                {"F11", "Fullscreen"},
                {"Esc", "Pause menu"},
        };
        private final UI.Button done = new UI.Button(0, 0, 200, 20, "Done", "done");

        public String render(UI ui, Input in, Options opts, float t) {
            float W = ui.uiW, H = ui.uiH;
            float cx = W / 2f;
            ui.textCenter(cx, 22, "Controls", 1, 1, 1, 2, true);
            float y = 52;
            for (String[] k : KEYS) {
                ui.text(cx - 150, y, k[0], ACCENT[0], ACCENT[1], ACCENT[2], 1, true);
                ui.text(cx - 40, y, k[1], 0.88f, 0.9f, 0.92f, 1, true);
                y += 14;
            }
            done.x = cx - 100;
            done.y = Math.min(H - 40, y + 12);
            done.render(ui);
            if (done.hover && in.mousePressed(0)) return "done";
            return null;
        }
    }

    // ==================================================================== Loading
    public static final class LoadingScreen {
        public float progress;
        public String label = "Preparing airfield";

        public void render(UI ui, Input in, Options opts, float t) {
            float W = ui.uiW, H = ui.uiH;
            float cx = W / 2f;
            ui.textCenter(cx, H / 2f - 22, label + "...", 1, 1, 1, 1, true);
            float barW = 200, barH = 8;
            ui.rect(cx - barW / 2f, H / 2f, barW, barH, 0, 0, 0, 0.6f);
            ui.border(cx - barW / 2f, H / 2f, barW, barH, 1, 0.5f, 0.55f, 0.55f, 0.8f);
            ui.rect(cx - barW / 2f + 1, H / 2f + 1, (barW - 2) * progress, barH - 2, ACCENT[0], ACCENT[1], ACCENT[2], 1);
            int pct = Math.round(progress * 100);
            ui.textCenter(cx, H / 2f + 12, pct + "%", 0.85f, 0.88f, 0.9f, 1, true);
        }
    }

    // ==================================================================== Pause
    public static final class PauseScreen {
        private final UI.Button[] buttons = {
                new UI.Button(0, 0, 200, 20, "Back to Flight", "back"),
                new UI.Button(0, 0, 200, 20, "Restart on Runway", "restart"),
                new UI.Button(0, 0, 200, 20, "Air Start (1000 ft)", "airstart"),
                new UI.Button(0, 0, 200, 20, "Options...", "options"),
                new UI.Button(0, 0, 200, 20, "Quit to Title", "quit"),
        };

        public String render(UI ui, Input in, Options opts, float t) {
            float W = ui.uiW, H = ui.uiH;
            float cx = W / 2f;
            ui.textCenter(cx, H * 0.18f, "Paused", 1, 1, 1, 2, true);
            for (UI.Button b : buttons) {
                b.x = cx - 100;
                b.y = H * 0.28f + indexOf(b) * 24;
                b.render(ui);
                if (b.hover && in.mousePressed(0)) return b.action;
            }
            return null;
        }

        private int indexOf(UI.Button b) {
            for (int i = 0; i < buttons.length; i++) if (buttons[i] == b) return i;
            return 0;
        }
    }

    // ==================================================================== Crash
    public static final class CrashScreen {
        public String render(UI ui, Input in, Options opts, float t) {
            float W = ui.uiW, H = ui.uiH;
            float cx = W / 2f;
            boolean blink = (int) (t * 2) % 2 == 0;
            ui.textCenter(cx, H * 0.34f, "YOU CRASHED", 1f, 0.3f, 0.25f, 3, true);
            if (blink) ui.textCenter(cx, H * 0.34f + 34, "Press R to reset to the runway", 0.95f, 0.95f, 0.95f, 1, true);
            return null;
        }
    }
}
