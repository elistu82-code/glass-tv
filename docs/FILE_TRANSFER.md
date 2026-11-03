# Wallpaper und Backups ohne Android-Dateiauswahl

Auf dem getesteten Fire TV gibt es keinen Handler für ACTION_OPEN_DOCUMENT. Glass TV erkennt den fehlenden Dialog und zeigt stattdessen eigene Importdateien an. Es werden keine umfassenden Speicherberechtigungen benötigt.

Debug: Der Austauschordner liegt privat unter `files/exchange/`. Das ist bewusst so: Fire OS verweigerte dem ADB-Shell-Benutzer den externen Android/data-Ordner. Die installierbare Debug-APK erlaubt den begrenzten Zugriff auf den eigenen Speicher über `run-as`, ohne Root oder Berechtigungsänderungen. Ein nicht exportierter FileProvider stellt ausschließlich die Import-/Export-Unterordner intern bereit.

Ab0.5 bietet die nicht debuggte APK zusätzlich `/sdcard/Android/media/de.glasslauncher.tv/exchange/{imports,exports}`. `tools/transfer.py` erkennt Debug/run-as oder verwendet diesen Medienordner. Glass liest beide Importordner; Debug exportiert privat, der nicht debuggte Build in Android/media. Keine allgemeine Speicherberechtigung. Dies ist weiterhin app-spezifischer Speicher und kein dauerhaftes Backup. Wallpaper werden nach Validierung privat kopiert; JSON wird vor Restore vollständig geprüft.

## Datei vom Rechner übertragen

Voraussetzung: vorhandener autorisierter ADB-Server und installiertes Glass-TV-APK. Das Werkzeug verwendet ausschließlich `/usr/bin/adb`; es beendet oder startet keinen Server explizit.

```sh
python3 tools/transfer.py --device FIRE_TV_ENDPOINT import /pfad/wallpaper.png
python3 tools/transfer.py --device FIRE_TV_ENDPOINT import /pfad/wallpaper.mp4
python3 tools/transfer.py --device FIRE_TV_ENDPOINT import /pfad/sicherung.json
```

`FIRE_TV_ENDPOINT` ersetzen. Dateinamen: Buchstaben/Ziffern/Punkt/Bindestrich/Unterstrich, maximal128Zeichen, keine Pfade oder Sonderzeichen im Namen. Zulässig: JPG/PNG/WebP bis32MiB, MP4 bis128MiB, JSON bis256KiB.

Danach in Glass TV „Hintergrund → Lokales Bild oder MP4 importieren“ beziehungsweise „System & Sicherung → Konfiguration wiederherstellen“ auswählen. Übertragen allein übernimmt noch nichts. Bilder werden geprüft und verkleinert; Videos müssen maximal1920×1080 sein. Übernommene Wallpaper erhalten eigene private Dateinamen. Backups werden vollständig vor dem Speichern geprüft; externe Wallpaper-Pfade werden nicht restauriert.

## Dauerhaft außerhalb der App sichern

Zuerst in Glass TV „System & Sicherung → Konfiguration exportieren“ wählen. Der App-Exportordner ist nur eine Zwischenablage und wird bei Deinstallation gelöscht. Anschließend auf dem Rechner:

```sh
python3 tools/transfer.py --device FIRE_TV_ENDPOINT backup artifacts/backups
```

Das Werkzeug kopiert exportierte JSON-Dateien tatsächlich auf diesen Rechner, prüft Format/Version und zeigt Speicherpfad sowie SHA256. Bestehende abweichende Dateien werden nicht überschrieben. Dieser Rechnerordner bleibt nach Deinstallation der Android-App bestehen; für weitere Sicherheit die Dateien zusätzlich auf ein anderes Laufwerk sichern. `artifacts/` ist Git-ignoriert.

Ohne Hilfswerkzeug kann ein bekannter Export auch direkt mit `/usr/bin/adb exec-out run-as de.glasslauncher.tv cat files/exchange/exports/DATEINAME.json` in eine lokale Datei kopiert werden. `run-as` funktioniert bewusst nur mit Debug-APKs; nicht debuggte APKs verwenden den oben beschriebenen Android/media-Fallback.

## Auf dem realen Fire TV geprüft
- PNG und H.264-MP4 vom Rechner übertragen, per Fernbedienung ausgewählt, im privaten Wallpaper-Speicher übernommen.
- Gültiges JSON über die App restauriert; ungültige Version99 abgewiesen, bestehende Konfigurationsdatei blieb bytegleich.
- JSON über die App exportiert und auf den Rechner kopiert: `artifacts/backups/glass-tv-settings-da0e65a6.json`,247Bytes, SHA256 `7b7feaed8592c3a7637ca71b63987314ec2b5771a57430a7481391c1acf51d0c`.
- Kein Root, keine Speicherberechtigung, keine Änderungen an Amazon-Paketen, keine Home-Umleitung.

Optionaler Netzwerkimport mit lokalem QR-Code und Einmalcode: [Plan](NETWORK_IMPORT_PLAN.md). Dieser Dienst ist noch nicht implementiert.

V0.5: Android/media auf echtem API30-Fire-TV ohne Zusatzrechte geprüft, auch gegen nicht debuggable APK. Instrumentierung validiert PNG→private Kopie und JSON→FileProvider→Codec. Echter Host-PNG-Transfer bytegleich, drei JSON-Backups auf Rechner gesichert; ursprüngliche Nutzerkonfiguration unverändert. Listen verwenden `ls -1`, damit mehrspaltiges Fire-OS-ls keine Exporte überspringt. Belege: `artifacts/fire-050/release-{import,backup}-transfer.txt`, `release-backups/`.
