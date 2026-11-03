# Spotify · aktueller Stand0.6.5

26.10.2026, echter Fire TV AFTMA08C15/API30. `GlassMediaListener` ist bereits freigegeben und LIVE. **Nicht erneut anfordern, umschalten oder per ADB grantieren.** Home on Fire bleibt unverändert.

## Spotify-UI0.6.4

Aktuelle Präsentation: links Titel/Interpret und drei64×56dp-Einzelbuttons mit24dp Abstand, weiter links/oben; rechts170dp-Cover,12dp höher als0.6.2. Ausschließlich das normale gewählte Wallpaper wird im Musikmodus gecacht weichgezeichnet; Albumcover erscheinen niemals im Hintergrund. Full Settings bleibt stärker verwischt. Deutlich organischere, gecachte Fortschrittslichtlinie mit ruhigem Lichtpunkt aus vorhandenen Callback-Zeitwerten, kein Seek/Polling. Dock-Up führt im aktiven Musikmodus zuerst zu Play/Pause/zuletzt verwendetem Musikbutton, erst Music-Up zur Uhr. Music-Down restauriert die letzte App, auch nach Sitzungsende. Ohne Musikmodus normale Navigation unverändert. [UI-Abnahme/Feinjustierung](VERSION_064.md).

## Cover-Schnellzugriff0.6.5

Rechts vom letzten verfügbaren Musikbutton erreicht das Cover; Links zurück, Hoch Zahnrad, Runter letzte App. Ohne Transportaktionen führt Dock-Up direkt zum Cover. OK löst ausschließlich den normalen Spotify-TV-/Launcher-Intent aus (IO-Auflösung), keine Transport-/Autoplay-/Transferaktion. Overlay-Sperre und Warm-Return-Fokus geprüft. Echter App-Start mit pausierter Sitzung2→2 und Fokus-Rückkehr auf dem Fire TV bestanden; aktueller R8-Release hatte STATE_NONE, daher dessen Live-Cover-Prüfung ausdrücklich offen. [Details](PERFORMANCE_065.md).

## Ursache und Reparatur

Die bestehende Konfiguration hatte `musicMode=false`. Damit blieb der Adapter trotz korrekter Systemfreigabe ausgeschaltet. Direkte Diagnose mit dem App-UID zeigte: Listener verbunden, korrekte ComponentName, `getActiveSessions` erfolgreich, genau eine passende `com.spotify.tv.android`-Sitzung, Controller-Callbacks registriert, Titel/Interpret/Bitmap lesbar. Kein Paketfilter- oder Berechtigungsfehler. Der vorhandene Albumcover-Modus wurde regulär über die Einstellungen eingeschaltet; alle anderen gespeicherten Werte blieben erhalten. Bei ausgeschaltetem Modus erklärt die Musikseite jetzt diesen Zustand, statt nur fehlende Verbindung anzuzeigen.

Zusätzliche Codekorrekturen:
- Warmes `foreground()` synchronisiert Medien wieder direkt; kein erneuter Pre-Draw nötig. Negativer echter Geräte-Regressionstest vor der Korrektur, anschließend bestanden.
- Playback-Positionsupdates hatten Metadaten und Bitmap wiederholt über Binder gelesen. Ein negativer Test belegte neue Bitmap-Objekte bei identischen Daten. Jetzt hält jeder Controller einen Snapshot; nur Metadaten-Callbacks ersetzen Metadaten/Artwork. Playback-Callbacks ändern nur PlaybackState.
- Visualizer berücksichtigt Opt-in, Verbindung, PLAYING und reduzierte Bewegung. Bei Pause statisch, bei fehlender Sitzung nicht aktiv. Diese0.6.1-Anordnung wird in0.6.2 durch eine kompakte Simulation neben dem Interpreten ersetzt. Übriges Layout/Sounds unverändert.

## Auf echtem Spotify geprüft

Drei ausdrücklich aktivierte Live-Testfälle verwenden die echte `com.spotify.tv.android`-Sitzung, keine injizierte Discovery. Glass-D-pad/OK sendet tatsächliche MediaController-Befehle; Tests warten auf anschließend beobachtete Wiedergabe-/Metadatenänderungen:

| Funktion | Nachweis |
|---|---|
| Listener/Discovery/Callbacks/UI-State | erfolgreich, bestehende Freigabe benutzt |
| Titel und Interpret | echte Daten sichtbar im Dashboard |
| Albumcover | eingebettetes640×640-Bitmap; echte Cover/Trackwechsel und dunkler Hintergrund |
| Play/Pause | PLAYING3↔PAUSED2 nach Glass-Buttons |
| Vorheriger/Nächster Titel | echte Metadatenwechsel nach Glass-Buttons; Spotify kann bei Zurück zunächst zum Trackanfang springen |
| Pausen-Cover | erhalten; tatsächliche Bitmap-Inhalte gleich |
| Visualizer | Lauf/Pause-State im echten UI geprüft, danach vorherige Option restauriert |
| Warmstart | direkter VM-Wiederanlauf ohne Pre-Draw geprüft; Home-Rückkehr auch nach Prozessneuanlage separat im Release erfolgreich |

Die Session liefert **Bitmap und HTTPS-ALBUM_ART_URI**. Der Adapter verwendet das vorhandene begrenzte Bitmap (max1024×1024/4MiB). Eine URI-Ladefunktion ist für die getesteten Tracks nicht erforderlich; URI-only-Cover bleiben bewusst unimplementiert. Keine INTERNET-/Speicherrechte oder Netzwerkabfragen ergänzt. Sollte künftig nur eine URI geliefert werden: Zugriffsrechte/Scheme prüfen, Downloads/Decoding begrenzen und niemals fremde private Dateien oder unbeschränkte URLs öffnen.

## Sitzungsende und ehrliche Grenzen

Dieser Spotify-Client meldet nach einem STOP-Befehl PAUSED2, weiterhin aktiv, statt STOPPED/NONE. Das ist kein Sitzungsende; Glass behält dann Cover und Resume-Button. Der erste Test mit der Annahme „STOP beendet zwingend Spotify“ scheiterte. Die Live-Probe startet nun vorher PLAYING und zeichnet den tatsächlichen Antwortzustand auf; sie prüft die passende UI-Reaktion, statt künstlich einen Terminalzustand zu erfinden.

STOPPED/NONE/ERROR, Callback-Destroy und Entfernen aus der aktiven Liste leeren den Adapter und stellen das gewählte Wallpaper wieder her. Diese dynamischen Endübergänge sind mit kontrollierten echten Android-MediaSessions/Render-Fixtures geprüft, nicht als über STOP ausgelöstes echtes Spotify-Ende behauptet. Im Release wurde eine inaktive NONE-Sitzung mit normalem Wallpaper ohne aktive Musikbuttons beobachtet. URI-only-Artwork und ein verlässlich per Spotify-STOP ausgelöster Abschluss bleiben Einschränkungen.

## Architektur, Datenschutz und Wiederholen

`SpotifySessionAdapter` nutzt `MediaSessionManager`/`MediaController` auf dem Hauptthread mit Callbacks; kein Polling im Launcher. AndroidSpotifySessions übergibt die eigene Listener-ComponentName und filtert ausschließlich `com.spotify.tv.android`, `com.spotify.music`, `com.amazon.spotify.mediabrowserservice`. Transport folgt tatsächlichen Action-Bits; keine aktiven Befehle ohne ausgewählte Sitzung. Stop/Close lösen eigene Controller-/Session-Listener und Referenzen. Keine Benachrichtigungsinhalte, Tokens oder Mikrofon-/Audiodaten werden verarbeitet.

Debug-Tag `GlassMedia`: Listener-Verbindung, Konfigurations-/Foreground-Gates, Sessionanzahlen, Callback-Registrierung, Metadaten-Präsenz, PlaybackState, Versand von Befehlen und UI-Booleans. Keine Titel/Interpreten/URI-Werte oder Session-/OAuth-Tokens im Log. Release protokolliert diese Diagnose nicht. Für vollständige Live-Protokolle während der Tests einen gefilterten Logcat-Reader laufen lassen; der Fire-TV-Ringpuffer überschreibt frühe Phasen schnell.

Live-Tests erfordern ausdrücklich `-e liveSpotify true` sowie vorhandene freigegebene Sitzung und eingeschalteten Albumcover-Modus; sie vergeben keine Rechte. Beispiele/Nachweise in [VERSION_061.md](VERSION_061.md). Die frühere Freigabegrenze bleibt nur historische Dokumentation in VERSION_060.md.

Android: [MediaSessionManager/ComponentName](https://developer.android.com/reference/android/media/session/MediaSessionManager), [NotificationListenerService](https://developer.android.com/reference/android/service/notification/NotificationListenerService).

## Optionale Web-API-Alternative

Für die funktionierende lokale Integration nicht notwendig und nicht implementiert: OAuth Authorization Code/PKCE ohne eingebettetes Client Secret, Playback-Scopes, sichere Tokenablage und Netzwerk wären nötig. Development Mode verlangt Premium beim App-Inhaber und bis5Allowlist-Nutzer; seitJuli2026 bis25Client-IDs mit gemeinsamer Kontoquote. Wiedergabesteuerung verlangt Premium beim Nutzer. Keine Anmeldung/Tokenfreigabe durchgeführt. Quellen (zuletzt26.10.2026 geprüft): [PKCE](https://developer.spotify.com/documentation/web-api/concepts/authorization), [Quoten](https://developer.spotify.com/documentation/web-api/concepts/quota-modes), [Juli-Update](https://developer.spotify.com/blog/2026-07-23-web-api-quota-updates), [Player](https://developer.spotify.com/documentation/web-api/reference/start-a-users-playback).
