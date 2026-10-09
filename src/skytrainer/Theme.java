package skytrainer;

/** Centralized color and bevel palette for every menu in the game.
 *  To retheme the entire UI, edit values here and rebuild. */
public final class Theme {
    // ---------- Panel ----------
    public static float PANEL_OUTER_R = 0.28f, PANEL_OUTER_G = 0.28f, PANEL_OUTER_B = 0.30f;
    public static float PANEL_INSET_R = 0.98f, PANEL_INSET_G = 0.98f, PANEL_INSET_B = 1.00f;
    public static float PANEL_FILL_R  = 0.78f, PANEL_FILL_G  = 0.78f, PANEL_FILL_B  = 0.80f;

    // ---------- Button normal (raised) ----------
    public static float BTN_OUTLINE_R = 0.78f, BTN_OUTLINE_G = 0.78f, BTN_OUTLINE_B = 0.80f;
    public static float BTN_FILL_R    = 0.78f, BTN_FILL_G    = 0.78f, BTN_FILL_B    = 0.80f;
    public static float BTN_HILITE_R  = 1.00f, BTN_HILITE_G  = 1.00f, BTN_HILITE_B  = 1.00f;
    public static float BTN_SHADOW_R  = 0.10f, BTN_SHADOW_G  = 0.10f, BTN_SHADOW_B  = 0.12f;
    public static float BTN_TEXT_R    = 0.06f, BTN_TEXT_G    = 0.26f, BTN_TEXT_B    = 0.24f;

    // ---------- Button hovered (selected) ----------
    public static float BTN_HOVER_FILL_R = 0.55f, BTN_HOVER_FILL_G = 0.55f, BTN_HOVER_FILL_B = 0.58f;
    public static float BTN_HOVER_TEXT_R = 0.40f, BTN_HOVER_TEXT_G = 0.92f, BTN_HOVER_TEXT_B = 0.88f;

    // ---------- Button pressed (depressed) ----------
    public static float BTN_PRESS_FILL_R = 0.65f, BTN_PRESS_FILL_G = 0.65f, BTN_PRESS_FILL_B = 0.67f;
    public static float BTN_PRESS_TEXT_R = 0.06f, BTN_PRESS_TEXT_G = 0.26f, BTN_PRESS_TEXT_B = 0.24f;

    // ---------- Text ----------
    public static float TEXT_DARK_R  = 0.10f, TEXT_DARK_G  = 0.10f, TEXT_DARK_B  = 0.12f;
    public static float TEXT_LIGHT_R = 0.92f, TEXT_LIGHT_G = 0.92f, TEXT_LIGHT_B = 0.94f;

    // ---------- Title / logo ----------
    public static float TITLE_WHITE_R  = 0.95f, TITLE_WHITE_G  = 0.95f, TITLE_WHITE_B  = 0.98f;
    public static float TITLE_ACCENT_R = 1.00f, TITLE_ACCENT_G = 0.78f, TITLE_ACCENT_B = 0.20f;

    // ---------- Dimensions ----------
    public static final int BEVEL_PX = 1;
    public static final int OUTER_FRAME_PX = 2;
    public static final int INSET_FRAME_PX = 2;

    private Theme() {}
}
