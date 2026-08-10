# WebBoard

WebBoard is an Android InputMethodService keyboard with an embedded browser.

## Features
- QWERTZ German layout (including ü, ö, ä, ß support through the symbol layer)
- Numbers and special characters via `?123`
- Browser is inside the keyboard window
- Browser expands to all available space above the keyboard rows
- URL/search field can be edited with WebBoard itself; it does **not** invoke a second Android keyboard
- Back/forward navigation
- Standard keyboard input is sent to the currently selected app text field

## Cloud build
Use `.github/workflows/android.yml`. It installs JDK 17, Android SDK 35 and Gradle 8.7 and produces `app-debug.apk` as a GitHub Actions artifact.
