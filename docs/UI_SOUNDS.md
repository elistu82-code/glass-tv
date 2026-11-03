# UI-Sounds ·0.6.4

Native AudioManager-Effekte bleiben die erste Wahl, wenn Systemtöne eingeschaltet und STREAM_SYSTEM hörbar sind. Auf dem getesteten Fire TV ist dieser Stream stumm; Glass nutzt dann exklusiv SoundPool mit USAGE_MEDIA / CONTENT_TYPE_SONIFICATION. Sind auch Medien stumm/auf0 oder der System-Soundschalter aus, bleibt Glass still. Keine globale Lautstärke-, Audiofokus- oder Berechtigungsänderung.

Die bestehenden Einstellungen UI-Sounds Ein/Aus und Intensität25/50/75/100% bleiben erhalten. Medienlautstärke gilt im Fallback zusätzlich; relative, quadratische Dämpfung begrenzt die Lautheit. Keine native/parallele SoundPool-Doppelausgabe. Die Compose-View unterdrückt weiterhin nur ihre impliziten Framework-Effekte.

Zwei eigene kurze PCM-WAVs (38ms Navigation,58ms Bestätigung; mono22050Hz/16bit) werden asynchron vorgeladen, nach dem ersten Frame. Android stellt dafür höchstens eine SoundPool-Stimme bereit; neue Eingaben stoppen die vorherige Stimme, statt zu überlagern. Jeder tatsächliche D-pad-Fokuswechsel zählt, einschließlich schneller/gehaltener Eingaben. Blockierte, doppelte oder programmatische Fokusereignisse bleiben still. Ein kleiner begrenzter IO-Puffer verwirft alte Einträge nach150ms, statt verspätet Geräusche nachzuspielen. Keine Sperre/AudioService-Abfrage auf dem Eingabethread. Kanalzustände höchstens alle250ms abfragen; die Plattform setzt Mute/Volume unmittelbar durch.

## Herkunft

`app/src/main/res/raw/ui_navigation.wav` und `ui_confirm.wav` sind vollständig synthetische Originale aus `tools/generate_ui_sounds.py`, ohne Aufnahmen, Downloads oder Amazon-Material. Diese beiden Audiodateien werden unter **CC0-1.0** bereitgestellt. Reproduzierbar mit `python3 tools/generate_ui_sounds.py` (Python-Standardbibliothek).

Technische Gerätedaten und tatsächlicher Testumfang stehen in VERSION_064.md. Erfolgreiche SoundPool-Streams/AudioService-Routing sind kein akustischer Nachweis am Lautsprecher; subjektive Lautstärke/Klangqualität muss am TV gehört werden.
