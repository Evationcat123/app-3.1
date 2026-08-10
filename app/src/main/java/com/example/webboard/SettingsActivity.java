package com.example.webboard;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.function.IntConsumer;

/**
 * Design settings for WebBoard: colors, corner radius, spacing, key size,
 * font size and the press/touch effect. Every control writes directly to
 * {@link KeyboardTheme}, which persists it via SharedPreferences and lets
 * a running keyboard pick the change up live. A small preview strip at the
 * top mirrors the keyboard's own key styling so users see changes instantly,
 * even without switching to the keyboard.
 */
public class SettingsActivity extends Activity {

    private KeyboardTheme theme;

    private LinearLayout previewNormal, previewSpecial, previewEnter, previewBackspace;
    private TextView previewNormalText, previewSpecialText, previewEnterText, previewBackspaceText;
    private View previewRoot;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        theme = KeyboardTheme.load(this);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText(R.string.settings_title);
        title.setTextSize(28);
        root.addView(title);

        TextView info = new TextView(this);
        info.setText(R.string.settings_intro);
        info.setTextSize(16);
        info.setPadding(0, dp(8), 0, dp(20));
        root.addView(info);

        Button enable = new Button(this);
        enable.setText(R.string.btn_manage_keyboards);
        enable.setOnClickListener(v -> startActivity(new Intent("android.settings.INPUT_METHOD_SETTINGS")));
        root.addView(enable);

        Button picker = new Button(this);
        picker.setText(R.string.btn_choose_keyboard);
        picker.setOnClickListener(v -> ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showInputMethodPicker());
        root.addView(picker);

        addSectionTitle(root, R.string.section_design);
        root.addView(buildPreviewPanel());

        addColorSection(root, R.string.section_key_color,
                () -> theme.keyColor,
                color -> { theme.keyColor = color; KeyboardTheme.setKeyColor(this, color); refreshPreview(); });

        addColorSection(root, R.string.section_background_color,
                () -> theme.backgroundColor,
                color -> { theme.backgroundColor = color; KeyboardTheme.setBackgroundColor(this, color); refreshPreview(); });

        addColorSection(root, R.string.section_text_color,
                () -> theme.textColor,
                color -> { theme.textColor = color; KeyboardTheme.setTextColor(this, color); refreshPreview(); });

        addColorSection(root, R.string.section_special_color,
                () -> theme.specialKeyColor,
                color -> { theme.specialKeyColor = color; KeyboardTheme.setSpecialKeyColor(this, color); refreshPreview(); });

        addColorSection(root, R.string.section_enter_color,
                () -> theme.enterKeyColor,
                color -> { theme.enterKeyColor = color; KeyboardTheme.setEnterKeyColor(this, color); refreshPreview(); });

        addColorSection(root, R.string.section_backspace_color,
                () -> theme.backspaceKeyColor,
                color -> { theme.backspaceKeyColor = color; KeyboardTheme.setBackspaceKeyColor(this, color); refreshPreview(); });

        addSectionTitle(root, R.string.section_appearance);

        addSeekBar(root, R.string.label_transparency, 40, 255, theme.backgroundAlpha,
                value -> { theme.backgroundAlpha = value; KeyboardTheme.setBackgroundAlpha(this, value); refreshPreview(); });

        addSeekBar(root, R.string.label_corner_radius,
                (int) KeyboardTheme.MIN_CORNER_RADIUS_DP, (int) KeyboardTheme.MAX_CORNER_RADIUS_DP, Math.round(theme.cornerRadiusDp),
                value -> { theme.cornerRadiusDp = value; KeyboardTheme.setCornerRadiusDp(this, value); refreshPreview(); });

        addSeekBar(root, R.string.label_spacing,
                (int) KeyboardTheme.MIN_SPACING_DP, (int) KeyboardTheme.MAX_SPACING_DP, Math.round(theme.spacingDp),
                value -> { theme.spacingDp = value; KeyboardTheme.setSpacingDp(this, value); refreshPreview(); });

        addSeekBar(root, R.string.label_key_size,
                Math.round(KeyboardTheme.MIN_SIZE_SCALE * 100), Math.round(KeyboardTheme.MAX_SIZE_SCALE * 100), Math.round(theme.sizeScale * 100),
                value -> { theme.sizeScale = value / 100f; KeyboardTheme.setSizeScale(this, theme.sizeScale); refreshPreview(); });

        addSeekBar(root, R.string.label_font_size,
                (int) KeyboardTheme.MIN_FONT_SIZE_SP, (int) KeyboardTheme.MAX_FONT_SIZE_SP, Math.round(theme.fontSizeSp),
                value -> { theme.fontSizeSp = value; KeyboardTheme.setFontSizeSp(this, value); refreshPreview(); });

        addPressEffectSwitch(root);

        Button reset = new Button(this);
        reset.setText(R.string.btn_reset_defaults);
        reset.setOnClickListener(v -> {
            KeyboardTheme.resetToDefaults(this);
            Toast.makeText(this, R.string.msg_reset_done, Toast.LENGTH_SHORT).show();
            recreate();
        });
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resetParams.topMargin = dp(24);
        resetParams.bottomMargin = dp(24);
        root.addView(reset, resetParams);

        refreshPreview();
    }

    // --- Section helpers -------------------------------------------------

    private void addSectionTitle(LinearLayout parent, int stringRes) {
        TextView t = new TextView(this);
        t.setText(stringRes);
        t.setTextSize(20);
        t.setPadding(0, dp(24), 0, dp(8));
        parent.addView(t);
    }

    private View buildPreviewPanel() {
        previewRoot = new LinearLayout(this);
        LinearLayout row = (LinearLayout) previewRoot;
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));
        row.setGravity(Gravity.CENTER_VERTICAL);

        previewNormal = previewKeyContainer();
        previewNormalText = previewKeyLabel(getString(R.string.preview_key_a));
        previewNormal.addView(previewNormalText);

        previewSpecial = previewKeyContainer();
        previewSpecialText = previewKeyLabel(getString(R.string.preview_key_special));
        previewSpecial.addView(previewSpecialText);

        previewEnter = previewKeyContainer();
        previewEnterText = previewKeyLabel(getString(R.string.preview_key_enter));
        previewEnter.addView(previewEnterText);

        previewBackspace = previewKeyContainer();
        previewBackspaceText = previewKeyLabel(getString(R.string.preview_key_backspace));
        previewBackspace.addView(previewBackspaceText);

        for (LinearLayout key : new LinearLayout[]{previewNormal, previewSpecial, previewEnter, previewBackspace}) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(56), 1f);
            p.setMargins(dp(4), dp(4), dp(4), dp(4));
            row.addView(key, p);
        }
        return row;
    }

    private LinearLayout previewKeyContainer() {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER);
        return l;
    }

    private TextView previewKeyLabel(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void refreshPreview() {
        if (previewRoot == null) return;
        previewRoot.setBackground(KeyboardTheme.roundedRect(this,
                KeyboardTheme.withAlpha(theme.backgroundColor, theme.backgroundAlpha), Math.max(theme.cornerRadiusDp, 4f)));

        styleForPreview(previewNormal, previewNormalText, theme.keyColor, theme.textColor);
        styleForPreview(previewSpecial, previewSpecialText, theme.specialKeyColor, theme.textColor);
        styleForPreview(previewEnter, previewEnterText, theme.enterKeyColor, KeyboardTheme.contrastText(theme.enterKeyColor));
        styleForPreview(previewBackspace, previewBackspaceText, theme.backspaceKeyColor, theme.textColor);
    }

    private void styleForPreview(LinearLayout container, TextView label, int color, int textColor) {
        container.setBackground(KeyboardTheme.roundedRect(this, color, theme.cornerRadiusDp));
        label.setTextColor(textColor);
        label.setTextSize(theme.fontSizeSp);
    }

    private void addColorSection(LinearLayout parent, int titleRes, java.util.function.IntSupplier current, IntConsumer onChange) {
        TextView title = new TextView(this);
        title.setText(titleRes);
        title.setTextSize(16);
        title.setPadding(0, dp(16), 0, dp(6));
        parent.addView(title);

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout swatches = new LinearLayout(this);
        swatches.setOrientation(LinearLayout.HORIZONTAL);
        int[] palette = getResources().getIntArray(R.array.preset_palette);
        for (int color : palette) {
            swatches.addView(createSwatch(color, onChange));
        }
        scroll.addView(swatches);
        parent.addView(scroll);

        LinearLayout customRow = new LinearLayout(this);
        customRow.setOrientation(LinearLayout.HORIZONTAL);
        customRow.setGravity(Gravity.CENTER_VERTICAL);
        customRow.setPadding(0, dp(6), 0, 0);

        EditText hex = new EditText(this);
        hex.setHint(R.string.hint_hex_color);
        hex.setText(String.format("#%06X", (0xFFFFFF & current.getAsInt())));
        customRow.addView(hex, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button apply = new Button(this);
        apply.setText(R.string.btn_apply_custom_color);
        apply.setOnClickListener(v -> {
            String value = hex.getText().toString().trim();
            try {
                int color = Color.parseColor(value.startsWith("#") ? value : "#" + value);
                onChange.accept(color);
            } catch (IllegalArgumentException e) {
                Toast.makeText(this, R.string.msg_invalid_color, Toast.LENGTH_SHORT).show();
            }
        });
        customRow.addView(apply);
        parent.addView(customRow);
    }

    private View createSwatch(int color, IntConsumer onChange) {
        View swatch = new View(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        bg.setStroke(dp(1), Color.parseColor("#33000000"));
        swatch.setBackground(bg);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(40), dp(40));
        p.setMargins(dp(4), dp(4), dp(4), dp(4));
        swatch.setLayoutParams(p);
        swatch.setOnClickListener(v -> onChange.accept(color));
        return swatch;
    }

    private void addSeekBar(LinearLayout parent, int labelRes, int min, int max, int current, IntConsumer onChange) {
        TextView label = new TextView(this);
        label.setText(labelRes);
        label.setTextSize(16);
        label.setPadding(0, dp(16), 0, dp(4));
        parent.addView(label);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(Math.max(1, max - min));
        seekBar.setProgress(Math.max(0, current - min));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) onChange.accept(progress + min);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        parent.addView(seekBar);
    }

    private void addPressEffectSwitch(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(16), 0, dp(4));

        TextView label = new TextView(this);
        label.setText(R.string.label_press_effect);
        label.setTextSize(16);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(theme.pressEffectEnabled);
        toggle.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            theme.pressEffectEnabled = isChecked;
            KeyboardTheme.setPressEffectEnabled(this, isChecked);
        });
        row.addView(toggle);

        parent.addView(row);
    }

    private int dp(int x) {
        return (int) (x * getResources().getDisplayMetrics().density + 0.5f);
    }
}
