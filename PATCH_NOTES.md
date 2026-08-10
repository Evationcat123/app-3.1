# WebBoard patch

This patch focuses on ordinary keyboard/UI reliability and does not add or improve any mechanism intended to circumvent Android parental-control or device-management restrictions.

## Fixes
- Properly destroys the WebView when the IME service is destroyed to reduce lifecycle leaks.
- Shift now only transforms single-letter keys; punctuation/space/grouped symbol keys are not unexpectedly uppercased.
- Backspace uses Unicode code-point deletion where supported, avoiding broken surrogate pairs for emoji and other supplementary characters.
- URL-field deletion now clamps selection values and restores the cursor after deletion.
- Navigation buttons have content descriptions for accessibility.
- URL focus no longer blindly jumps the cursor to the end when a valid cursor position already exists.

## Build
The project uses Android Gradle Plugin 8.6.1 and compileSdk 35. A Gradle wrapper is not included in the supplied project, so this environment could not perform a full APK build.
