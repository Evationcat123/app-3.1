# WebBoard stability patch

## YouTube / close-and-reopen keyboard crash fix

- Keeps the WebView alive when the IME window is temporarily hidden instead of destroying it in `onFinishInputView()`.
- Resumes an existing WebView when the keyboard is shown again.
- Recreates a detached/missing WebView safely and restores the last URL.
- Tracks the last successfully loaded URL so the page does not always reset to Google.
- Removes the old `onPageFinished()` focus request, which could cause focus/IME churn on media-heavy pages such as YouTube.
- Makes WebView teardown tolerant of an already-dead renderer.
- Makes JavaScript injection tolerant of a dead WebView renderer so a renderer failure does not crash the keyboard process.
- Adds a bounded WebView renderer priority policy on Android 8+ rather than trying to solve the issue by arbitrarily increasing heap size.

The main suspected cause was lifecycle/focus handling around a heavy WebView, not simply an undersized Java heap. Android WebView's renderer is a separate process, so increasing `org.gradle.jvmargs` does not directly increase the WebView renderer's available memory.
