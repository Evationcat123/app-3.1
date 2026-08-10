# WebBoard – APK online bauen

Dieses Projekt ist für einen Cloud-Build mit **GitHub Actions** vorbereitet. Du brauchst dafür keinen PC mit Android Studio und auch keinen lokalen Gradle-Wrapper.

## GitHub im Browser

1. Öffne GitHub und erstelle ein neues Repository.
2. Lade den **Inhalt des Ordners `WebBoard`** aus diesem ZIP in das Repository hoch. Die Datei `.github/workflows/build-apk.yml` muss mit hochgeladen werden.
3. Öffne im Repository den Tab **Actions**.
4. Wähle **Build WebBoard APK**.
5. Klicke auf **Run workflow**. Alternativ startet der Build automatisch, sobald du Dateien auf `main` oder `master` hochlädst.
6. Warte, bis der Workflow erfolgreich abgeschlossen ist.
7. Öffne den erfolgreichen Lauf und scrolle zu **Artifacts**.
8. Lade **WebBoard-debug-apk** herunter.
9. Entpacke die ZIP-Datei. Darin befindet sich `app-debug.apk`.
10. Übertrage die APK auf dein Android-Gerät und installiere sie.

## Auf dem Android-Gerät

Nach der Installation:

1. Öffne **Einstellungen → System → Tastatur → Bildschirmtastatur** (die genaue Bezeichnung kann je nach Hersteller abweichen).
2. Aktiviere **WebBoard**.
3. Öffne den Tastatur-Picker und wähle WebBoard aus.

## Build-Umgebung

- Android Gradle Plugin 8.6.1
- Gradle 8.7 (wird automatisch in GitHub Actions eingerichtet)
- JDK 17
- compileSdk / targetSdk 35
- minSdk 26
