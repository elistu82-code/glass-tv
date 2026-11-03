# Android-TV-Emulator · Stand 0.4.0

Television_1080p / emulator-5554: AOSP TV on x86, Android11/API30,1920×1080 bei320dpi (960×540dp),4vCPUs/2048MiB, GPU aktiviert/auto. Vorhandenes AVD unverändert verwendet; kein Server-Neustart, keine globale Host-Konfiguration. Der echte Fire TV wurde nicht kontaktiert.

```bash
/usr/bin/adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
/usr/bin/adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
/usr/bin/adb -s emulator-5554 shell am start -W -n de.glasslauncher.tv/.MainActivity
/usr/bin/adb -s emulator-5554 shell am instrument -w -e class de.glasslauncher.tv.ui.CalendarScreenTest de.glasslauncher.tv.test/androidx.test.runner.AndroidJUnitRunner
```

0.4.0: 49 lokale Unit-Tests und 19 gezielte Emulatorfälle bestanden (19 Fälle in 32.242s; 5 betroffene Fälle später erneut in 12.041s). Enthalten sind 13 LauncherScreen-Fälle, 3 interaktive Kalenderfälle und 3 Home-Registrierungs-/Capability-Proben. Keine komplette Android-Suite wiederholt. Tagesraster, Schaltjahr/Monatswechsel, rasche Eingaben, Demo-Agenda/Details, leere Tage, Kalender-Back→Uhr und bestehende Dock-/Settings-/Suche-/Kontext-/Startfunktionen geprüft.

Glasflächen sind transparenter und neutraler, das Dashboard hat keinen Footer mehr. Kalender bleibt vor statischem Blur ohne Dashboard. Vier eindeutig markierte lokale Demo-Einträge; keine Synchronisation, Erinnerungen oder Kalenderberechtigung. Screenshots/XML unter artifacts/emulator-040 prüfen Home, Tag-/Agenda-Fokus, beide Demo-Details, leeren Tag, Uhr-Rückkehr und Quick/Full Settings. Für UIAutomator kann das Fokus-Attribut auf dem Elternknoten und die Inhaltsbeschreibung auf einem Kind liegen; Compose-Fokustests und Bounds gemeinsam auswerten.

HOME: Alias deaktiviert ausgeliefert. Temporäre Rollenzuweisung gelingt, der physische Knopf folgt im aktuellen Image dennoch Google TV. Expliziter HOME-Entry in Glass und Stock-Recovery getrennt geprüft. Originalrolle/Aliasstatus nach Test wiederhergestellt. Keine Home-Erfolgsaussage für Fire OS; Details in HOME_REDIRECT.md.

Vorheriger Stand0.3.2:46Unit-/17Emulatorfälle; drei normale Apps Play Games/Play Store/YouTube sichtbar, technische Settings/Remote-Service gefiltert. Reale kontrollierte MediaSession getestet, Discovery injiziert, kein echter Spotify-Nachweis und keine neuen Freigaben. YouTube startet, meldet selbst fehlende Geräteunterstützung. Warme Startprobe39Frames/31janky,Median18ms/P90 20ms; keine60fps-Abnahme. Diese Probe wurde für die Glasänderung nicht als Leistungsnachweis wiederverwendet.

Offen: neue UI/Kalender/Video-Pause auf Fire TV, helle eigene Wallpaper auf Kontrast prüfen, echte Spotify-Sitzung samt Freigabeweg, Framezeitoptimierung auf Hardware. Aktuelle APK und gezielte Nachhol-Liste in TODO.md.
