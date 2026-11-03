# Optionaler lokaler Netzwerktransfer · spätere Version

Status: geplanter Ausbau, nicht implementiert und standardmäßig deaktiviert. Der lokale Ordner-/ADB-Weg funktioniert unabhängig davon. Keine neue Netzwerkberechtigung für diesen Plan.

## Ablauf
1. Nutzer öffnet in Glass TV ausdrücklich „Dateien vom Handy/Laptop übertragen“.
2. Nur für diese Sitzung startet ein kleiner lokaler HTTP-Dienst auf der ausgewählten LAN-Schnittstelle. Glass TV zeigt lokale Adresse, Ablaufzeit, QR-Code und Einmalcode. Der QR-Code wird vollständig lokal erzeugt.
3. Handy/Laptop öffnet die Adresse oder scannt den QR-Code. Ein zufälliges Sitzungstoken und ein kurzlebiger Einmalcode schützen den Zugriff; begrenzte Versuche, kurze Ablaufzeit, kein wiederverwendbares Passwort.
4. Browser kann ein unterstütztes Wallpaper oder ein JSON-Backup hochladen. Import landet zunächst in einem begrenzten temporären Bereich; Typ, Größe, Bild-/Videoabmessungen beziehungsweise komplette JSON-Struktur und Version werden mit denselben Regeln wie beim lokalen Import geprüft.
5. Glass TV zeigt Dateiname/Typ und Vorschau beziehungsweise Restore-Zusammenfassung. Erst die Bestätigung auf der Fernbedienung übernimmt das Wallpaper oder die Konfiguration. Fehler lassen bestehende Einstellungen unverändert.
6. Ein geprüfter JSON-Export kann im Browser direkt auf Handy/Laptop heruntergeladen werden und bleibt dort nach einer Deinstallation von Glass TV erhalten.
7. Schließen, Inaktivität/Ablaufzeit oder Verlassen der App beendet den Dienst, verwirft Sitzungstoken und löscht nicht übernommene temporäre Dateien.

## Umsetzungsvorgaben
- Keine dauerhafte Hintergrundfreigabe, Router-Portfreigabe, Cloud, Konten, Mikrofon- oder umfassenden Speicherberechtigungen.
- Kein allgemeiner Dateiserver: nur definierte Upload-/Download-Endpunkte, keine frei wählbaren Serverpfade. Serverseitig erzeugte Dateinamen; begrenzte Anfragen und gleichzeitige Transfers.
- Zufallswerte aus SecureRandom, Sitzungstoken mindestens128Bit, Einmalcode zum Abtippen, maximal5 Fehlversuche, Sitzung beispielsweise5Minuten. Token/Code/Dateiinhalte nicht protokollieren.
- Gegen fremde Webseiten absichern: Origin-/Host-/CSRF-Prüfung, keine offene CORS-Freigabe. Vor Implementierung HTTP-/HTTPS-Verhalten und Schutz auf gemeinsam genutzten WLANs konkret prüfen.
- INTERNET erst hinzufügen, wenn der aktiv gewählte Transfer tatsächlich implementiert wird. Kein Discovery-Scan oder automatische Kommunikation.
- Prüfen: falscher/abgelaufener Code, zu große/abgebrochene Dateien, manipulierte Namen/Pfade, ungültige Backups, parallele Anfragen, Home/Resume und Dienstende. Anschließend expliziter Geräte-/Netzwerkreview.

Abnahmekriterium: echter Upload vom Handy und Laptop, bestätigter Import auf Fire TV sowie dauerhaft außerhalb der App gespeicherter Backup-Download; anschließend kein offener Server mehr.
