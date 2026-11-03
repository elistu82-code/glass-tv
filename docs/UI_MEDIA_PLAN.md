# Version0.3 · gezielter Umbau

- Bestehende0.2-Basis erhalten. Dock84%breit,128×80dp (16:10; Breite+18.5% gegenüber108dp), weniger Innenabstand und88dp Abstand unten. Logos unverzerrt per Fit; keine Layoutverschiebung beim Fokus.
- Explizite Dock-Grenzen, Up→Wetter, WetterLeft→Zahnrad, obere Down→letzte App. Kontextmenüs/Overlays beibehalten; gezielte UI-Regressionen.
- Launcher-interner550ms-Startübergang von gemessener Kachelposition zur Vollfläche; Back kann vor App-Start abbrechen. Cover bleibt während Übergabe, Dashboard kommt bei Resume weich zurück. Fremde Fenster bleiben Fire OS überlassen.
- Exakte, nachvollziehbare Default-Filterliste nur für bekannte Systemwerkzeuge; normale Amazon-Apps/Streaming und Benutzerinstallationen bleiben. Sichtbarkeits-Overrides werden gesichert.
- Optionaler callbackbasierter Spotify-MediaSession-Adapter, kein automatischer Notification-/ADB-Zugriff. Deckblatt, Metadaten und action-geprüfte Controls nur bei gültiger Sitzung. Cover hält Pause aus; keine Geheimnisse/Netzwerkcover-Downloads. Bestehendes Wallpaper und dessen Video-Lifecycle bleiben erhalten.
- Backend-Agent besitzt ausschließlich media/**, zugehörige Tests und Spotify/Home-Dokumente; Hauptagent UI, Integration, Filter/Config und Build/Hardware. Keine überlappenden Änderungen.
- Gezielte Unit-/UI-Tests, Wrapper-Build/Lint, APK installieren und Hardware prüfen; nach erfolgreicher Prüfung regulär committen/pushen. Spotify/Home-Kompatibilitätsgrenzen konkret dokumentieren.

Abschluss: Implementierung und Review abgeschlossen;41Unit-Tests,21gezielte Gerätefälle sowie Wrapper-Build/Lint erfolgreich. Hardwarebelege und Einschränkungen in VALIDATION.md; offene Performance-/Spotify-Prüfungen in TODO.md.
