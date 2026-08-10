package com.example.webboard;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.RenderProcessGoneDetail;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import java.util.Locale;
import org.json.JSONObject;

/**
 * WebBoard: QWERTZ keyboard with an embedded browser.
 * The URL/search field is edited by WebBoard's own keys, so Android does not
 * need to open a second keyboard when the address field is selected.
 *
 * Visual appearance (colors, corner radius, spacing, key size, font size,
 * press effect) is fully configurable through {@link SettingsActivity} and
 * stored via {@link KeyboardTheme}. Changes are picked up live through a
 * SharedPreferences listener and are always re-applied when the keyboard
 * is shown.
 */
public class WebBoardIme extends InputMethodService
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    /** Base height (dp) of the key rows area at the default size scale (1.0). */
    private static final int BASE_KEYS_HEIGHT_DP = 200;
    private static final int BROWSER_BAR_HEIGHT_DP = 44;
    private static final String PREF_LAST_URL = "last_web_url";

    private FrameLayout root;
    private LinearLayout keys;
    private WebView web;
    private EditText url;
    private Button goButton, backButton, forwardButton, reloadButton;
    private ImageButton settingsButton;
    private boolean shift = false;
    private boolean symbols = false;
    private boolean inputViewActive = false;
    private String lastWebUrl = "https://www.google.com/";

    private KeyboardTheme theme;

    @Override public void onCreate() {
        super.onCreate();
        Window w = getWindow().getWindow();
        if (w != null) {
            // Make the IME window use the full available display. The browser is
            // then rendered behind the keyboard instead of being squeezed into
            // a small panel above it.
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            w.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                w.setDecorFitsSystemWindows(false);
            }
        }
        lastWebUrl = getSharedPreferences("webboard_state", MODE_PRIVATE)
                .getString(PREF_LAST_URL, "https://www.google.com/");
        theme = KeyboardTheme.load(this);
        KeyboardTheme.prefs(this).registerOnSharedPreferenceChangeListener(this);
    }

    @Override public void onFinishInputView(boolean finishingInput) {
        inputViewActive = false;
        // Keep the WebView instance, but pause it while the IME window is hidden.
        // This is important for video-heavy pages such as YouTube: the WebView
        // renderer may otherwise be killed while the keyboard is closed.
        if (web != null) {
            try { web.onPause(); } catch (Throwable ignored) {}
        }
        super.onFinishInputView(finishingInput);
    }


    @Override public void onWindowHidden() {
        try { if (web != null) web.onPause(); } catch (Throwable ignored) {}
        super.onWindowHidden();
    }

    @Override public void onWindowShown() {
        super.onWindowShown();
        if (inputViewActive && web != null) {
            try { web.onResume(); } catch (Throwable ignored) {}
        }
    }

    @Override public void onDestroy() {
        KeyboardTheme.prefs(this).unregisterOnSharedPreferenceChangeListener(this);
        if (web != null) {
            try {
                web.stopLoading();
                web.onPause();
                web.setWebViewClient(null);
                web.removeAllViews();
                web.destroy();
            } catch (Throwable ignored) {
                // Renderer/process may already have gone away.
            }
            web = null;
        }
        url = null;
        keys = null;
        root = null;
        super.onDestroy();
    }

    /** Live-preview hook: settings changes are applied immediately while the keyboard is visible. */
    @Override public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        theme = KeyboardTheme.load(this);
        applyTheme();
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        inputViewActive = true;
        // Always reload in case the theme changed while the keyboard was hidden.
        theme = KeyboardTheme.load(this);
        if (root == null || web == null || web.getParent() == null) {
            // The system may recreate the IME view after hiding it. Rebuild the
            // browser safely instead of touching a detached WebView instance.
            if (web != null) {
                try {
                    web.stopLoading();
                    web.setWebViewClient(null);
                    web.removeAllViews();
                    web.destroy();
                } catch (Throwable ignored) {}
            }
            root = null;
            web = null;
            url = null;
            keys = null;
            setInputView(onCreateInputView());
        } else {
            applyTheme();
            try {
                web.setVisibility(View.VISIBLE);
                web.onResume();
            } catch (Throwable ignored) {
                // A renderer that died between IME sessions will be handled by
                // onRenderProcessGone; avoid propagating a WebView exception.
            }
        }
    }

    @Override public View onCreateInputView() {
        // FrameLayout lets the browser occupy the entire IME window while the
        // toolbar and keyboard float above it. This gives the browser the
        // maximum possible height instead of restricting it to the area above
        // the keyboard.
        root = new FrameLayout(this);
        root.setPadding(0, 0, 0, 0);

        createWebView();
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        View browserBar = buildBrowserBar();
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(BROWSER_BAR_HEIGHT_DP));
        barParams.gravity = android.view.Gravity.TOP;
        barParams.setMargins(dp(4), dp(4), dp(4), 0);
        root.addView(browserBar, barParams);

        keys = new LinearLayout(this);
        keys.setOrientation(LinearLayout.VERTICAL);
        keys.setPadding(0, dp(2), 0, dp(2));
        FrameLayout.LayoutParams keyParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, keysHeightPx());
        keyParams.gravity = android.view.Gravity.BOTTOM;
        root.addView(keys, keyParams);

        applyTheme();
        return root;
    }

    /** Creates a fresh WebView and restores the last page without leaking the old instance. */
    private void createWebView() {
        if (web != null) {
            try {
                web.stopLoading();
                web.setWebViewClient(null);
                web.removeAllViews();
                web.destroy();
            } catch (Throwable ignored) {
                // WebView teardown can throw when its renderer already died.
            }
            web = null;
        }

        web = new WebView(this);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                v.requestFocus();
                v.requestFocusFromTouch();
            }
            return false;
        });

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }
        // Avoid retaining unnecessary renderer/cache state while the IME is hidden.
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            web.setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, false);
        }

        web.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String pageUrl) {
                super.onPageFinished(view, pageUrl);
                if (pageUrl != null && !pageUrl.isEmpty()) {
                    lastWebUrl = pageUrl;
                    getSharedPreferences("webboard_state", MODE_PRIVATE)
                            .edit().putString(PREF_LAST_URL, lastWebUrl).apply();
                    if (url != null && !url.hasFocus()) {
                        url.setText(pageUrl);
                    }
                }
            }

            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // Android may kill the Chromium renderer while the IME is hidden,
                // especially on memory-constrained devices or video-heavy pages.
                // Returning true prevents that renderer crash from taking down
                // the entire keyboard service. Recreate the WebView on the main
                // thread and restore the last known URL.
                final String restoreUrl = lastWebUrl;
                if (root != null) {
                    root.post(() -> {
                        if (!inputViewActive || root == null) return;
                        try {
                            if (web != null && web.getParent() instanceof android.view.ViewGroup) {
                                ((android.view.ViewGroup) web.getParent()).removeView(web);
                            }
                        } catch (Throwable ignored) {}
                        web = null;
                        createWebView();
                        if (web != null && root != null) {
                            root.addView(web, 0, new FrameLayout.LayoutParams(-1, -1));
                            if (restoreUrl != null && !restoreUrl.isEmpty()) {
                                try { web.loadUrl(restoreUrl); } catch (Throwable ignored) {}
                            }
                        }
                    });
                }
                return true;
            }
        });
        web.setBackgroundColor(Color.WHITE);

        try {
            web.loadUrl(lastWebUrl);
        } catch (Throwable ignored) {
            // If the WebView renderer is unavailable, leave a valid WebView
            // instance so the keyboard itself remains usable.
        }
    }

    private int keysHeightPx() {
        return dp(Math.round(BASE_KEYS_HEIGHT_DP * theme.sizeScale));
    }

    /** Re-applies the current theme to every part of the UI without rebuilding the URL field. */
    private void applyTheme() {
        if (root == null) return;
        root.setBackgroundColor(KeyboardTheme.withAlpha(theme.backgroundColor, theme.backgroundAlpha));

        styleUrlField();
        styleSmallButton(goButton);
        styleSmallButton(backButton);
        styleSmallButton(forwardButton);
        styleSmallButton(reloadButton);
        styleSettingsButton();

        if (keys != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) keys.getLayoutParams();
            if (lp != null) {
                lp.height = keysHeightPx();
                keys.setLayoutParams(lp);
            }
            buildKeys();
        }
    }

    private View buildBrowserBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(dp(1), dp(1), dp(1), dp(1));

        url = new EditText(this);
        url.setSingleLine(true);
        url.setText(lastWebUrl);
        url.setTextSize(14);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setShowSoftInputOnFocus(false);
        url.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                int end = url.length();
                url.setSelection(Math.max(0, Math.min(end, url.getSelectionStart() < 0 ? end : url.getSelectionStart())));
            }
        });
        url.setOnEditorActionListener((v, actionId, event) -> { navigate(); return true; });
        url.setPadding(dp(12), 0, dp(8), 0);
        bar.addView(url, new LinearLayout.LayoutParams(0, -1, 1f));

        goButton = smallButton("GO");
        goButton.setContentDescription("Go");
        goButton.setOnClickListener(v -> navigate());
        bar.addView(goButton, new LinearLayout.LayoutParams(dp(44), -1));

        backButton = smallButton("‹");
        backButton.setContentDescription("Back");
        backButton.setOnClickListener(v -> { if (web != null && web.canGoBack()) web.goBack(); });
        bar.addView(backButton, new LinearLayout.LayoutParams(dp(34), -1));

        forwardButton = smallButton("›");
        forwardButton.setContentDescription("Forward");
        forwardButton.setOnClickListener(v -> { if (web != null && web.canGoForward()) web.goForward(); });
        bar.addView(forwardButton, new LinearLayout.LayoutParams(dp(34), -1));

        reloadButton = smallButton("↻");
        reloadButton.setContentDescription("Reload website");
        reloadButton.setOnClickListener(v -> {
            if (web != null) web.reload();
        });
        bar.addView(reloadButton, new LinearLayout.LayoutParams(dp(34), -1));

        settingsButton = new ImageButton(this);
        settingsButton.setImageResource(R.drawable.ic_settings);
        settingsButton.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        settingsButton.setPadding(dp(8), dp(8), dp(8), dp(8));
        settingsButton.setContentDescription(getString(R.string.ime_settings));
        settingsButton.setOnClickListener(v -> openSettings());
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(dp(34), -1);
        settingsParams.setMarginStart(dp(4));
        bar.addView(settingsButton, settingsParams);

        return bar;
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(11);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private void styleUrlField() {
        if (url == null) return;
        url.setBackground(KeyboardTheme.roundedRect(this, Color.WHITE, Math.max(theme.cornerRadiusDp, 10f)));
        url.setTextColor(theme.textColor);
    }

    private void styleSmallButton(Button b) {
        if (b == null) return;
        b.setBackground(KeyboardTheme.keyBackground(this, theme.specialKeyColor, Math.max(theme.cornerRadiusDp - 2f, 4f)));
        b.setTextColor(theme.textColor);
        attachPressAnimation(b);
    }

    private void styleSettingsButton() {
        if (settingsButton == null) return;
        settingsButton.setBackground(KeyboardTheme.keyBackground(this, theme.specialKeyColor, Math.max(theme.cornerRadiusDp - 2f, 4f)));
        settingsButton.setImageTintList(android.content.res.ColorStateList.valueOf(theme.textColor));
        attachPressAnimation(settingsButton);
    }

    private void navigate() {
        if (url == null || web == null) return;
        String q = url.getText().toString().trim();
        if (q.isEmpty()) return;
        if (!q.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            q = "https://www.google.com/search?q=" + android.net.Uri.encode(q);
        }
        lastWebUrl = q;
        getSharedPreferences("webboard_state", MODE_PRIVATE)
                .edit().putString(PREF_LAST_URL, lastWebUrl).apply();
        web.loadUrl(q);
        url.clearFocus();
        web.requestFocus();
    }

    private void buildKeys() {
        keys.removeAllViews();
        if (symbols) {
            addRow("1234567890");
            addRow("@#$%&*+-=/");
            LinearLayout r = row();
            addKey(r, "ABC", 1.25f, v -> { symbols = false; buildKeys(); }, KeyboardTheme.KeyKind.SPECIAL);
            addKey(r, "()[]{}", 2.0f, v -> type(((Button) v).getText().toString()), KeyboardTheme.KeyKind.NORMAL);
            addKey(r, "!?;:'", 2.0f, v -> type(((Button) v).getText().toString()), KeyboardTheme.KeyKind.NORMAL);
            addKey(r, "⌫", 1.25f, v -> delete(), KeyboardTheme.KeyKind.BACKSPACE);
            keys.addView(r, new LinearLayout.LayoutParams(-1, 0, 1f));
        } else {
            addRow("qwertzuiopü");
            addRow("asdfghjklöä");
            LinearLayout r = row();
            addKey(r, "⇧", 1.35f, v -> { shift = !shift; buildKeys(); }, KeyboardTheme.KeyKind.SPECIAL);
            addKey(r, "y", 1f, v -> type(keyText((Button) v)), KeyboardTheme.KeyKind.NORMAL);
            addKey(r, "x", 1f, v -> type(keyText((Button) v)), KeyboardTheme.KeyKind.NORMAL);
            addKey(r, "c", 1f, v -> type(keyText((Button) v)), KeyboardTheme.KeyKind.NORMAL);
            addKey(r, "v", 1f, v -> type(keyText((Button) v)), KeyboardTheme.KeyKind.NORMAL);
            addKey(r, "b", 1f, v -> type(keyText((Button) v)), KeyboardTheme.KeyKind.NORMAL);
            addKey(r, "n", 1f, v -> type(keyText((Button) v)), KeyboardTheme.KeyKind.NORMAL);
            addKey(r, "m", 1f, v -> type(keyText((Button) v)), KeyboardTheme.KeyKind.NORMAL);
            addKey(r, ",", 1f, v -> type(","), KeyboardTheme.KeyKind.NORMAL);
            addKey(r, ".", 1f, v -> type("."), KeyboardTheme.KeyKind.NORMAL);
            addKey(r, "⌫", 1.35f, v -> delete(), KeyboardTheme.KeyKind.BACKSPACE);
            keys.addView(r, new LinearLayout.LayoutParams(-1, 0, 1f));
        }

        LinearLayout bottom = row();
        addKey(bottom, symbols ? "ABC" : "?123", 1.25f, v -> { symbols = !symbols; shift = false; buildKeys(); }, KeyboardTheme.KeyKind.SPECIAL);
        addKey(bottom, "🌐", 1f, v -> { if (url != null) { url.requestFocus(); url.setSelection(url.length()); } }, KeyboardTheme.KeyKind.SPECIAL);
        addKey(bottom, ",", 1f, v -> type(","), KeyboardTheme.KeyKind.NORMAL);
        addKey(bottom, "Leertaste", 4.2f, v -> type(" "), KeyboardTheme.KeyKind.NORMAL);
        addKey(bottom, ".", 1f, v -> type("."), KeyboardTheme.KeyKind.NORMAL);
        addKey(bottom, "↵", 1.25f, v -> enter(), KeyboardTheme.KeyKind.ENTER);
        keys.addView(bottom, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private String keyText(Button b) { return b.getText().toString(); }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private void addRow(String chars) {
        LinearLayout r = row();
        for (int i = 0; i < chars.length(); i++) {
            String c = String.valueOf(chars.charAt(i));
            addKey(r, c, 1f, v -> type(((Button) v).getText().toString()), KeyboardTheme.KeyKind.NORMAL);
        }
        keys.addView(r, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private void addKey(LinearLayout row, String label, float weight, View.OnClickListener listener, KeyboardTheme.KeyKind kind) {
        Button b = new Button(this);
        String shown = label;
        if (shift && label.length() == 1 && Character.isLetter(label.charAt(0))) {
            shown = label.toUpperCase(Locale.GERMANY);
        }
        b.setText(shown);
        b.setTextSize(label.equals("Leertaste") ? theme.fontSizeSp * 0.75f : theme.fontSizeSp);
        b.setAllCaps(false);
        b.setPadding(0, 0, 0, 0);

        int baseColor = theme.colorForKind(kind);
        b.setBackground(KeyboardTheme.keyBackground(this, baseColor, theme.cornerRadiusDp));
        b.setTextColor(kind == KeyboardTheme.KeyKind.ENTER ? KeyboardTheme.contrastText(baseColor) : theme.textColor);

        b.setOnClickListener(listener);
        attachPressAnimation(b);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, weight);
        int spacing = dp(Math.round(theme.spacingDp));
        p.setMargins(spacing, spacing, spacing, spacing);
        row.addView(b, p);
    }

    /** Adds a quick, subtle scale-down effect on press for a more modern, responsive feel. */
    private void attachPressAnimation(View v) {
        v.setOnTouchListener((view, event) -> {
            if (!theme.pressEffectEnabled) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    view.animate().scaleX(0.93f).scaleY(0.93f).setDuration(60).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    view.animate().scaleX(1f).scaleY(1f).setDuration(90).start();
                    break;
            }
            return false;
        });
    }

    private boolean isUrlFocused() {
        return url != null && url.hasFocus();
    }

    private boolean isWebFocused() {
        return web != null && web.hasFocus() && !isUrlFocused();
    }

    private void evaluateWebScript(String script) {
        if (web == null || TextUtils.isEmpty(script)) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                web.evaluateJavascript(script, null);
            } else {
                web.loadUrl("javascript:" + script);
            }
        } catch (Throwable ignored) {
            // A dead WebView renderer must never take the keyboard down with it.
        }
    }

    private String jsString(String text) {
        try {
            return JSONObject.quote(text);
        } catch (Exception e) {
            return "\"\"";
        }
    }

    private void injectTextIntoWeb(String text) {
        if (TextUtils.isEmpty(text)) return;
        evaluateWebScript("(function(t){"
                + "var el=document.activeElement;"
                + "if(!el) return;"
                + "if(el.isContentEditable){document.execCommand('insertText',false,t);return;}"
                + "var tag=(el.tagName||'').toLowerCase();"
                + "if(tag==='input'||tag==='textarea'){"
                + "var start=typeof el.selectionStart==='number'?el.selectionStart:(el.value||'').length;"
                + "var end=typeof el.selectionEnd==='number'?el.selectionEnd:start;"
                + "var value=el.value||'';"
                + "el.value=value.slice(0,start)+t+value.slice(end);"
                + "var pos=start+t.length;"
                + "if(el.setSelectionRange){el.setSelectionRange(pos,pos);}else{el.selectionStart=el.selectionEnd=pos;}"
                + "el.dispatchEvent(new Event('input',{bubbles:true,cancelable:true}));"
                + "el.dispatchEvent(new Event('change',{bubbles:true}));"
                + "return;"
                + "}"
                + "})(%s);".formatted(jsString(text)));
    }

    private void injectBackspaceIntoWeb() {
        evaluateWebScript("(function(){"
                + "var el=document.activeElement;"
                + "if(!el) return;"
                + "if(el.isContentEditable){document.execCommand('delete');return;}"
                + "var tag=(el.tagName||'').toLowerCase();"
                + "if(tag==='input'||tag==='textarea'){"
                + "var value=el.value||'';"
                + "var start=typeof el.selectionStart==='number'?el.selectionStart:value.length;"
                + "var end=typeof el.selectionEnd==='number'?el.selectionEnd:start;"
                + "if(start!==end){"
                + "el.value=value.slice(0,start)+value.slice(end);"
                + "el.setSelectionRange(start,start);"
                + "}else if(start>0){"
                + "el.value=value.slice(0,start-1)+value.slice(end);"
                + "el.setSelectionRange(start-1,start-1);"
                + "}"
                + "el.dispatchEvent(new Event('input',{bubbles:true,cancelable:true}));"
                + "el.dispatchEvent(new Event('change',{bubbles:true}));"
                + "}"
                + "})() ;");
    }

    private void injectEnterIntoWeb() {
        evaluateWebScript("(function(){"
                + "var el=document.activeElement;"
                + "if(!el) return;"
                + "var evOpts={key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true,cancelable:true};"
                + "el.dispatchEvent(new KeyboardEvent('keydown',evOpts));"
                + "el.dispatchEvent(new KeyboardEvent('keypress',evOpts));"
                + "el.dispatchEvent(new KeyboardEvent('keyup',evOpts));"
                + "if(el.form){"
                + "if(typeof el.form.requestSubmit==='function'){el.form.requestSubmit();}"
                + "else{el.form.submit();}"
                + "}"
                + "})() ;");
    }

    /** Sends text either to the browser's address/search field, a webpage input, or to the app using the IME. */
    private void type(String text) {
        if (TextUtils.isEmpty(text)) return;

        if (shift) {
            // Shift should affect letters, not arbitrary strings such as
            // punctuation groups or the space bar.
            if (text.length() == 1 && Character.isLetter(text.charAt(0))) {
                text = text.toUpperCase(Locale.GERMANY);
            }
            shift = false;
            buildKeys();
        }

        if (isUrlFocused()) {
            int start = Math.max(0, url.getSelectionStart());
            int end = Math.max(0, url.getSelectionEnd());
            url.getText().replace(Math.min(start, end), Math.max(start, end), text);
            url.setSelection(Math.min(start, end) + text.length());
            return;
        }

        if (isWebFocused()) {
            injectTextIntoWeb(text);
            return;
        }

        InputConnection ic = getCurrentInputConnection();
        if (ic != null) ic.commitText(text, 1);
    }

    private void delete() {
        if (isUrlFocused()) {
            int start = Math.max(0, url.getSelectionStart());
            int end = Math.max(0, url.getSelectionEnd());
            int min = Math.min(start, end);
            int max = Math.max(start, end);
            if (min != max) {
                url.getText().delete(min, max);
                url.setSelection(min);
            } else if (start > 0) {
                // Delete one Unicode code point rather than half of a surrogate pair.
                int deleteStart = Character.offsetByCodePoints(url.getText(), start, -1);
                url.getText().delete(deleteStart, start);
                url.setSelection(deleteStart);
            }
            return;
        }

        if (isWebFocused()) {
            injectBackspaceIntoWeb();
            return;
        }

        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                ic.deleteSurroundingTextInCodePoints(1, 0);
            } else {
                ic.deleteSurroundingText(1, 0);
            }
        }
    }

    private void enter() {
        if (isUrlFocused()) {
            navigate();
            return;
        }

        if (isWebFocused()) {
            injectEnterIntoWeb();
            return;
        }

        InputConnection ic = getCurrentInputConnection();
        if (ic != null) {
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
        }
    }

    private int dp(int x) {
        return (int) (x * getResources().getDisplayMetrics().density + 0.5f);
    }
}
