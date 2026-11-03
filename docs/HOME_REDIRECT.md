# HOME: Emulator und Fire OS getrennt

Stand0.5.0 /26.10.2026: **Home on Fire ist vom Benutzer bereits auf dem echten Fire TV installiert, aktiviert und funktionsfähig. Diese Einrichtung bleibt unverändert.** Keine Neuinstallation, Rollen-/Komponentenänderung, Accessibility-Umschaltung oder Berechtigungsvergabe. Der deaktivierte HomeEntry-Alias muss für diesen normalen App-Start nicht aktiviert werden. Emulatorbefunde unten sind historische, getrennte Beobachtungen.

Vorher war Glass nur über LAUNCHER/LEANBACK_LAUNCHER registriert, ohne HOME-Filter. Jetzt existiert `de.glasslauncher.tv/.HomeEntry` als standardmäßig **deaktivierter** MAIN/HOME/DEFAULT-Alias. Installation ändert keine HOME-Auswahl. Die Ziel-Activity ist singleTask; ein expliziter HOME-Intent setzt Overlays/Move/Pending-Launch zurück. Die Rückkehr zum Systemlauncher wählt eine andere HOME-Komponente und verhindert Selbstschleifen. Alias-Filter sind eigenständige Einstiegspunkte; deaktiviert nehmen sie keine Intents an. [Android-Alias-Dokumentation](https://developer.android.com/guide/topics/manifest/activity-alias-element).

## Tatsächlicher Android-TV-Emulatorbefund

Television_1080p, Android11/API30, ursprünglicher HOME: `com.google.android.tvlauncher/.MainActivity`.

- Shell-`pm enable/default-state` verweigert Komponentenänderungen mit `SecurityException: Shell cannot change component state`. Die normale eigene PackageManager-API funktioniert ohne Zusatzrechte.
- Der ausschließlich für ranchu/goldfish erlaubte HomeRoutingTest aktiviert nur unseren Alias temporär. `set-home-activity` meldet Success und Glass erhält tatsächlich ROLE_HOME. Trotzdem löst HOME weiter zu Google TV mit Priorität2 auf; Glass wird als Kandidat mit0 gemeldet. Ein eigener positiver Prioritätswert wurde vom Image nicht übernommen und ist nicht im finalen Manifest enthalten.
- Der echte Home-Tastendruck wurde geprüft und öffnet **Google TV**, nicht Glass. Rolle/Registrierung allein beweisen hier keine funktionierende Taste. Der Test meldet diese Einschränkung ausdrücklich; er behauptet keinen erfolgreichen Home-Ersatz.
- Separat geprüft: expliziter MAIN/HOME-Intent an HomeEntry öffnet Glass, schließt einen offenen Kalender und zeigt das Dashboard. Back erreicht den ursprünglichen Systemlauncher. Das ist eine vorbereitete Entry-Funktion, kein Nachweis für den physischen Knopf.
- Originale HOME-Rolle und Aliasstatus werden im finally-Block wiederhergestellt und kontrolliert. Resultat: `artifacts/emulator-040/home-routing-result.txt`, Rollen-Dumps vorher/nachher. Keine System-App wurde abgeschaltet und keine privilegierte Berechtigung erteilt. Das konkrete Google-TV-Image darf nicht mit einem beliebigen AOSP-/Telefon-Emulator gleichgesetzt werden.

## Fire TV / Fire OS

Aktuell: AFTMA08C15/API30. Die bestehende Umleitung öffnet Glass nach HOME aus einer fremden App. Die V0.5-Messung beobachtet den Vordergrundwechsel und vergleicht die Liste aktiver Accessibility-Dienste vorher/nachher; keine Helfereinstellung wird geändert. Messwerte einschließlich ADB-/Polling-Overhead stehen in PERFORMANCE_050.md. Eine kurze Amazon-Home-Einblendung liegt im Weiterleitungsweg, getrennt vom direkten Glass-Activity-Start.

Der bereits separat eingerichtete Helfer [Home on Fire](https://github.com/toolicious/home-on-fire#compatibility) beschreibt für Fire OS8 die Weiterleitung über einen Accessibility-Dienst: Er erkennt Amazon Home im Vordergrund und startet die gewählte App. Amazon Home bleibt bestehen und kann kurz sichtbar werden. Der Anbieter nennt Fire OS7/8 als unterstützt; unser exakter Gerätebuild ist damit nicht nachgewiesen. Laut [Installationshinweisen](https://github.com/toolicious/home-on-fire#installation) genügt gegebenenfalls der manuelle Accessibility-Dialog; bei defektem Dialog wird WRITE_SECURE_SETTINGS vorgeschlagen. Diese weitreichende Freigabe wird nicht automatisch erteilt. Der Alias allein löst die OEM-Beschränkung nicht.

Historische Vorbereitungshinweise für eine andere/neue Installation (hier nicht ausführen):

1. Konkreten Release-Tag/Quellcode, APK-Hash/Signatur und Berechtigungen prüfen; bisher keine Companion-APK heruntergeladen/geprüft.
2. Einstellungen extern sichern und Stock-Home-Zugriff bestätigen. Ziel bleibt `de.glasslauncher.tv/.MainActivity` über regulären App-Start; dafür muss HomeEntry nicht aktiviert werden.
3. Accessibility und einen eventuell notwendigen Secure-Settings-Grant separat erklären/freigeben. Bootstart zunächst aus.
4. Auf dem echten Gerät Home aus fremden Apps und Glass, Long-/Double-Home, Back, Neustart und Profile testen; Flackern/Verzögerungen dokumentieren.
5. Rückbau prüfen: Companion-Umleitung/Bootstart aus, nur dessen Accessibility-Dienst deaktivieren, gegebenenfalls nur dessen Grant widerrufen und Helfer regulär entfernen. Andere Dienste und Amazon-Pakete unverändert lassen.
