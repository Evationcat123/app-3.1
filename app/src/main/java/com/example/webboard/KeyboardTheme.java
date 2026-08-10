package com.example.webboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

/**
 * Holds every design/appearance setting of WebBoard (colors, corner radius,
 * spacing, key size, font size, press effect) and persists them via
 * SharedPreferences so they survive keyboard/app restarts.
 *
 * SettingsActivity writes values here, WebBoardIme reads them (and listens
 * for changes) to render the keyboard. Keeping all theme logic in one place
 * avoids duplicating drawable/color code in both components.
 */
public class KeyboardTheme {

    public static final String PREFS_NAME = "webboard_theme_prefs";

    private static final String KEY_KEY_COLOR = "key_color";
    private static final String KEY_BACKGROUND_COLOR = "background_color";
    private static final String KEY_TEXT_COLOR = "text_color";
    private static final String KEY_SPECIAL_COLOR = "special_key_color";
    private static final String KEY_ENTER_COLOR = "enter_key_color";
    private static final String KEY_BACKSPACE_COLOR = "backspace_key_color";
    private static final String KEY_BACKGROUND_ALPHA = "background_alpha";
    private static final String KEY_CORNER_RADIUS = "corner_radius_dp";
    private static final String KEY_SPACING = "key_spacing_dp";
    private static final String KEY_SIZE_SCALE = "key_size_scale";
    private static final String KEY_FONT_SIZE = "font_size_sp";
    private static final String KEY_PRESS_EFFECT = "press_effect_enabled";

    // Reasonable, safe ranges so user-chosen values never break the layout.
    public static final float MIN_CORNER_RADIUS_DP = 0f;
    public static final float MAX_CORNER_RADIUS_DP = 24f;
    public static final float MIN_SPACING_DP = 0f;
    public static final float MAX_SPACING_DP = 10f;
    public static final float MIN_SIZE_SCALE = 0.8f;
    public static final float MAX_SIZE_SCALE = 1.3f;
    public static final float MIN_FONT_SIZE_SP = 12f;
    public static final float MAX_FONT_SIZE_SP = 22f;

    public int keyColor;
    public int backgroundColor;
    public int textColor;
    public int specialKeyColor;
    public int enterKeyColor;
    public int backspaceKeyColor;
    public int backgroundAlpha;
    public float cornerRadiusDp;
    public float spacingDp;
    public float sizeScale;
    public float fontSizeSp;
    public boolean pressEffectEnabled;

    public enum KeyKind { NORMAL, SPECIAL, ENTER, BACKSPACE }

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Reads the current theme, falling back to the modern default palette. */
    public static KeyboardTheme load(Context context) {
        SharedPreferences p = prefs(context);
        KeyboardTheme t = new KeyboardTheme();
        t.keyColor = p.getInt(KEY_KEY_COLOR, defaultColor(context, R.color.default_key_color));
        t.backgroundColor = p.getInt(KEY_BACKGROUND_COLOR, defaultColor(context, R.color.default_background_color));
        t.textColor = p.getInt(KEY_TEXT_COLOR, defaultColor(context, R.color.default_text_color));
        t.specialKeyColor = p.getInt(KEY_SPECIAL_COLOR, defaultColor(context, R.color.default_special_key_color));
        t.enterKeyColor = p.getInt(KEY_ENTER_COLOR, defaultColor(context, R.color.default_enter_key_color));
        t.backspaceKeyColor = p.getInt(KEY_BACKSPACE_COLOR, defaultColor(context, R.color.default_backspace_key_color));
        t.backgroundAlpha = p.getInt(KEY_BACKGROUND_ALPHA, 255);
        t.cornerRadiusDp = clamp(p.getFloat(KEY_CORNER_RADIUS, 12f), MIN_CORNER_RADIUS_DP, MAX_CORNER_RADIUS_DP);
        t.spacingDp = clamp(p.getFloat(KEY_SPACING, 4f), MIN_SPACING_DP, MAX_SPACING_DP);
        t.sizeScale = clamp(p.getFloat(KEY_SIZE_SCALE, 1.0f), MIN_SIZE_SCALE, MAX_SIZE_SCALE);
        t.fontSizeSp = clamp(p.getFloat(KEY_FONT_SIZE, 16f), MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP);
        t.pressEffectEnabled = p.getBoolean(KEY_PRESS_EFFECT, true);
        return t;
    }

    private static int defaultColor(Context context, int colorRes) {
        return context.getResources().getColor(colorRes, context.getTheme());
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    // --- Setters used by SettingsActivity. Each writes immediately so the
    // running keyboard (which listens for SharedPreferences changes) can
    // update live. ---

    public static void setKeyColor(Context c, int color) { prefs(c).edit().putInt(KEY_KEY_COLOR, color).apply(); }
    public static void setBackgroundColor(Context c, int color) { prefs(c).edit().putInt(KEY_BACKGROUND_COLOR, color).apply(); }
    public static void setTextColor(Context c, int color) { prefs(c).edit().putInt(KEY_TEXT_COLOR, color).apply(); }
    public static void setSpecialKeyColor(Context c, int color) { prefs(c).edit().putInt(KEY_SPECIAL_COLOR, color).apply(); }
    public static void setEnterKeyColor(Context c, int color) { prefs(c).edit().putInt(KEY_ENTER_COLOR, color).apply(); }
    public static void setBackspaceKeyColor(Context c, int color) { prefs(c).edit().putInt(KEY_BACKSPACE_COLOR, color).apply(); }
    public static void setBackgroundAlpha(Context c, int alpha) { prefs(c).edit().putInt(KEY_BACKGROUND_ALPHA, alpha).apply(); }
    public static void setCornerRadiusDp(Context c, float dp) { prefs(c).edit().putFloat(KEY_CORNER_RADIUS, dp).apply(); }
    public static void setSpacingDp(Context c, float dp) { prefs(c).edit().putFloat(KEY_SPACING, dp).apply(); }
    public static void setSizeScale(Context c, float scale) { prefs(c).edit().putFloat(KEY_SIZE_SCALE, scale).apply(); }
    public static void setFontSizeSp(Context c, float sp) { prefs(c).edit().putFloat(KEY_FONT_SIZE, sp).apply(); }
    public static void setPressEffectEnabled(Context c, boolean enabled) { prefs(c).edit().putBoolean(KEY_PRESS_EFFECT, enabled).apply(); }

    public static void resetToDefaults(Context c) { prefs(c).edit().clear().apply(); }

    // --- Drawable / color helpers shared by keyboard and settings preview ---

    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int darken(int color, float factor) {
        int a = Color.alpha(color);
        int r = Math.round(Color.red(color) * factor);
        int g = Math.round(Color.green(color) * factor);
        int b = Math.round(Color.blue(color) * factor);
        return Color.argb(a, Math.min(r, 255), Math.min(g, 255), Math.min(b, 255));
    }

    /** Picks readable black/white text for an arbitrary (user-chosen) background color. */
    public static int contrastText(int backgroundColor) {
        double luminance = (0.299 * Color.red(backgroundColor)
                + 0.587 * Color.green(backgroundColor)
                + 0.114 * Color.blue(backgroundColor)) / 255.0;
        return luminance > 0.6 ? Color.BLACK : Color.WHITE;
    }

    public static GradientDrawable roundedRect(Context context, int color, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusDp * context.getResources().getDisplayMetrics().density);
        return d;
    }

    /** Normal + pressed state drawable (darker shade) for a modern touch effect. */
    public static StateListDrawable keyBackground(Context context, int baseColor, float radiusDp) {
        GradientDrawable normal = roundedRect(context, baseColor, radiusDp);
        GradientDrawable pressed = roundedRect(context, darken(baseColor, 0.82f), radiusDp);
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[]{}, normal);
        return sld;
    }

    public int colorForKind(KeyKind kind) {
        switch (kind) {
            case SPECIAL: return specialKeyColor;
            case ENTER: return enterKeyColor;
            case BACKSPACE: return backspaceKeyColor;
            default: return keyColor;
        }
    }
}
