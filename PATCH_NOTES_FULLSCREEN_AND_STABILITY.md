# WebBoard – Fullscreen browser + stability patch

## Changes
- Browser WebView now fills the complete IME window.
- Keyboard rows and browser toolbar float above the WebView instead of shrinking it.
- IME window uses `MATCH_PARENT` + `ADJUST_NOTHING` so the browser can extend from top to bottom.
- Last visited URL is persisted and restored after IME recreation/service restart.
- Fixed a potential WebView leak when Android recreates the input view.
- Renderer recovery now reinserts the WebView as the full-screen background.
- WebView media playback is enabled without requiring a second Android keyboard.
- Existing QWERTZ keyboard, theme controls, address bar and browser controls are preserved.

## Build
Use the existing GitHub Actions workflow to build `app-debug.apk`.
