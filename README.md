# Glass TV Launcher

Minimalistischer Kotlin-/Compose-Launcher für Fire TV ab Android 11 (API 30). Verbindliche Spezifikation: [Architektur](docs/ARCHITECTURE.md).

Implementiert sind App-Erkennung, eine gemeinsame milchige App-Leiste, Fernbedienungsfokus und langes OK, Verschieben/Anheften/Ausblenden, System-App-Informationen und bestätigte Deinstallation, Quick/Full Settings, persistente Einstellungen, vier originale Hintergrund-Szenen, lokale Bild-/MP4-Imports, Kalender und Konfigurationssicherung. App-Suche öffnet über die Menü-Taste oder Apps verwalten. Video pausiert mit Decoderfreigabe und setzt an der gespeicherten Position fort. Der Visualizer ist ausdrücklich eine Simulation. Wetter bleibt offline. Ein optionaler Spotify-MediaSession-Adapter mit separatem Albumcover, weichgezeichnetem normalem Wallpaper und action-geprüften Musiksteuerungen ist implementiert; echte Spotify-Steuerung und Albumcover sind mit0.6.1 auf diesem Fire TV geprüft. Details und Web-API-Alternative: [Spotify](docs/SPOTIFY.md).

## Bauen und testen

Getestete Werkzeugversionen: JDK 25, Gradle Wrapper 9.3.1, AGP 9.1.1, Kotlin 2.2.10, SDK-Plattform 37.0 und Build Tools 36.0.0. Die App verwendet `minSdk 30` und `targetSdk 35`. Keine globale PATH-Änderung erforderlich.

Repository klonen und Android SDK installieren. Der SDK-Pfad unten ist ein portables Beispiel; bei einer anderen Installation anpassen. Der erste Build benötigt Zugriff auf die offiziellen Gradle-/Maven-/Google-Repositories.

```sh
git clone https://github.com/elistu82-code/glass-tv.git
cd glass-tv
```

Für Bash und Fish:

```sh
env ANDROID_HOME="$HOME/Android/Sdk" GRADLE_USER_HOME="$PWD/.gradle-user-home" ./gradlew :app:testDebugUnitTest
env ANDROID_HOME="$HOME/Android/Sdk" GRADLE_USER_HOME="$PWD/.gradle-user-home" ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Reguläre APK: `app/build/outputs/apk/debug/app-debug.apk`. GitHub Actions führt Unit-Tests, Lint sowie Debug-/Release-Build aus und archiviert APK sowie Berichte. Wrapper und dessen SHA256-Prüfsumme sind versioniert; `/tmp/glass-tools` ist nicht erforderlich.

## Aktueller Nachweis

Version0.6.5 optimiert Fokus-State-Lesezugriffe und den Release-Build (R8), ohne das Design zu vereinfachen. Das Spotify-Cover ist per D-pad fokussierbar und öffnet Spotify ohne Wiedergabebefehl. Kontrollierte Scope-Arbeit und reale Release-Framezeiten/PSS sind verbessert; Warmstart und60FPS bleiben bewusst differenziert bewertet. Performance-Messwerte (interne Entwicklungsdokumentation), [UI-Sounds](docs/UI_SOUNDS.md), [Spotify](docs/SPOTIFY.md).

Home on Fire wurde vom Benutzer bereits auf dem echten Gerät aktiviert und bleibt unverändert. Emulator-HOME ist davon getrennt: [Home-Details](docs/HOME_REDIRECT.md).

Vorheriger Hardwarestand0.3.0: regulärer Gradle-Wrapper-Lauf erfolgreich, **41 Unit-Tests**, Debug-APK, AndroidTest-APK und Lint ohne Fehler. Auf Fire TV AFTMA08C15 / API30 installiert und gestartet; **21 gezielte UI-/Medien-Gerätefälle bestanden**: D-pad/Suche/Back, Kontextmenüs, Settings, echte Decoderfreigabe/Resume, Fehler-Fallback und Dateipersistenz. Echte PNG-/MP4-/JSON-Transfers samt externem Rechner-Backup geprüft. Eine vollständige Performance-Abnahme steht noch aus.

Aktuelle Debug-APK nach einem Build: `app/build/outputs/apk/debug/app-debug.apk`. Lokale Diagnoseartefakte und APK-Archive sind kein Bestandteil des Repositorys.

Ohne System-Dateiauswahl funktioniert der private Import-/Export-Fallback mit `tools/transfer.py`. Wichtig: Exportierte Backups anschließend tatsächlich auf den Rechner kopieren, da die App-Ordner bei Deinstallation gelöscht werden. [Anleitung und Gerätenachweise](docs/FILE_TRANSFER.md). [Späterer QR-/Einmalcode-Netzwerktransfer](docs/NETWORK_IMPORT_PLAN.md) ist geplant, noch nicht aktiv.

## Installation / Download

**[Download Glass TV v0.6.5 Beta (APK)](https://github.com/elistu82-code/glass-tv/releases/download/v0.6.5/glass-tv-v0.6.5.apk)**

The APK is available directly from the link above. You do **not** need to build the source code to install Glass TV.

1. Download the APK to your Android TV / Fire TV device, or transfer it from your computer.
2. Enable installation from the app used to open the APK, if the TV prompts you.
3. Open the APK and follow the installation dialog.
4. Launch **Glass TV** from your apps. It does not automatically replace your current home screen.

**Requirements:** Android 11 (API 30) or later. Device compatibility varies; this is a beta release. If an older build with a different signing certificate is already installed, you may need to back up its settings and uninstall it first.

[All releases](https://github.com/elistu82-code/glass-tv/releases)

## Installation auf Fire TV / Android TV (ADB / development)

Auf einem eigenen, bereits für ADB autorisierten Gerät installieren. `FIRE_TV_IP:ADB_PORT` durch den eigenen ADB-Endpunkt ersetzen; für einen Emulator dessen Gerätekennung verwenden. ADB verwendet ausschließlich den bestehenden Server mit `/usr/bin/adb`; keinen zweiten Server starten und den vorhandenen nicht unnötig beenden.

```sh
/usr/bin/adb -s FIRE_TV_IP:ADB_PORT install -r app/build/outputs/apk/debug/app-debug.apk
/usr/bin/adb -s FIRE_TV_IP:ADB_PORT shell am start -n de.glasslauncher.tv/.MainActivity
```

Glass ändert bei der Installation keine HOME-Zuordnung, Amazon-Systempakete oder sensiblen Berechtigungen. Der optionale HOME-Alias ist standardmäßig deaktiviert. Eine vorhandene externe Home-Weiterleitung bleibt unabhängig von der App; Kompatibilität und Rückweg sind in [Home auf Fire TV](docs/HOME_REDIRECT.md) dokumentiert.

Debug-APKs sind Entwicklungsversionen. Lokale und CI-Builds können unterschiedliche Debug-Signaturen haben. Vor einer erforderlichen Deinstallation Einstellungen und eigene Dateien exportieren und auf einen Rechner kopieren. Private Signaturschlüssel gehören niemals ins Repository.

Weitere Dokumentation: [Architektur](docs/ARCHITECTURE.md), [Design](docs/DESIGN.md), [Aufgaben und Grenzen](docs/TASKS.md), [Sicherheit](docs/SECURITY.md).

## Bedienung

- **Links/Rechts:** durch die App-Leiste oder die aktuell fokussierten Bedienelemente navigieren.
- **OK:** App starten oder Aktion ausführen. **Langes OK** auf einer App öffnet Verschieben, Anheften, Ausblenden und App-Aktionen.
- **Menü:** App-Suche öffnen. **Zurück:** Suche, Kontextmenü oder Overlay schließen und zum vorherigen Fokus zurückkehren.
- **Nach oben aus dem Dock:** Uhr/Kalender erreichen; im aktiven Spotify-Modus zuerst Musiksteuerung bzw. Cover. Von dort sind Uhr und Einstellungen erreichbar.
- **Uhr + OK:** Kalender öffnen. **Zahnrad:** Quick Settings und vollständige Einstellungen öffnen.

## Spotify und bekannte Einschränkungen

Spotify ist optional. Glass verarbeitet zugängliche lokale MediaSessions einer installierten Spotify-App bei aktiviertem Musikmodus. Es enthält keinen Spotify-Web-API-/OAuth-Client und keine Zugangsdaten. Die auf manchen Geräten nötige Notification Access ist eine weitreichende Systemfreigabe: nur nach Lesen der Erklärung manuell aktivieren. Glass erteilt diese Freigabe nicht selbst. Das Cover öffnet Spotify, ohne Wiedergabe auf ein anderes Gerät zu übertragen. Details und Fire-OS-Grenzen: [Spotify](docs/SPOTIFY.md).

- Wetter bleibt offline; Kalenderdaten sind lokale Demo-Einträge. Kein CalDAV-Sync und keine Erinnerungen.
- Der Visualizer ist eine Simulation und analysiert kein Audio.
- Kein aktiver Netzwerkimport-/QR-Code-Server; diese Erweiterung ist nur geplant.
- URI-only-Albumcover sind noch nicht implementiert. MediaSession- und Systemdialog-Zugriff unterscheiden sich je nach Gerät.
- Eigene Baseline Profiles sowie eine vollständige Langzeit-/Performance-Abnahme stehen aus. Konstante 60 FPS sind nicht nachgewiesen.
- Fire OS und externe Apps kontrollieren ihre eigenen Startfenster und HOME-Weiterleitungen. Historische Hardwareergebnisse gelten für die jeweils dokumentierte Testumgebung. Ein CI-Build ist kein Gerätetest.

## Sicherheit

Die App benötigt kein Root, keinen Internetzugriff und keine Mikrofon-/Audioaufnahmeberechtigung. Importe bleiben nicht vertrauenswürdige Eingaben und werden begrenzt und validiert. Keine Amazon-Systempakete deaktivieren oder entfernen; ADB nicht über das Internet erreichbar machen.

Keine privaten Konfigurationen, `.env`-Dateien, Zugangsdaten, Keystores, Gerätemessprotokolle oder Backups committen. `.gitignore` schützt typische lokale Dateien; vor einem Commit den Index prüfen. Weitere Details: [Sicherheit und Wiederherstellung](docs/SECURITY.md).

## Lizenzen und Assets

Für den eigenen Projektcode wird derzeit keine allgemeine Wiederverwendungslizenz erteilt. Die Sichtbarkeit auf GitHub ist keine Erlaubnis, den Code beliebig weiterzuverbreiten. Die bereits dokumentierten eigenen UI-Sounds bleiben CC0-1.0. Asset-Herkunft, Gradle-Lizenz und Hinweise zu zur Laufzeit angezeigten fremden Inhalten stehen in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

CI erzeugt einen unsignierten optimierten Release-Build samt R8-Mapping. Lokal mit `:app:assembleRelease` bauen; die erzeugte Release-APK benötigt vor einer Installation eine eigene lokale Signierung. Kein Produktions-Schlüssel oder offizieller Store-Release. Android/media-Dateiaustausch erhält ADB-Transfer auch ohne run-as.
