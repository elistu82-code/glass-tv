# Milky glass design

Approved source: `../GLASS_TV_PROJECT.md`. Neutral translucent milky material, rounded corners, restrained outlines; wallpaper carries color. One shared app container near the bottom. Weather left, small clock/date centered, gear right. Focus is visible and D-pad input remains immediate while decoration animates.

Quick settings is anchored top-right with home visible. Full settings hides the app row and presents a strongly blurred cached wallpaper. Submenus remain within this overlay. Starter forest, zen, liquid and neon scenes are generated artwork; final licensed art remains a user choice.

Central animation durations begin at focus 300 ms, quick settings 450 ms, submenu 350 ms, home 600 ms, wallpaper 1200 ms and music 1500 ms. Speed, easing, reduced motion and glass opacity are configuration values. More elaborate wallpaper reveals and realtime blur are deferred until measured on device.

Version0.3: Dock84%breit,88dpunten;128×80dp-Kacheln,55dp-Logo-Fit und4dpvertikales Dockpadding. Die20%-Grenze wird auf die Kachelbreite bezogen (+18.5%); das16:10-Format verringert die frühere100dpHöhe auf80dp.550msinterner Startübergang von Kachel zur neutralen Vollfläche, abbrechbar und ohne Dock-Reflow. Fokus: Up→Wetter, WetterLeft→Gear, obereDown→letzteApp; Dockkanten geklemmt. Spotify-Cover im16:9Crop mit dunklem Verlauf,1500msCrossfade und optionalen Metadaten/Controls, ausschließlich bei zugänglicher Sitzung; Simulation explizit beschriftet.

Version0.3.1 ersetzt die0.3-Geometrie:144×81dp (16:9),82% Dockbreite,104dp Bodenabstand und2dp vertikales Padding. Dock-Up fokussiert Uhr/Datum, OK öffnet Kalender; Uhr-Right erreicht Einstellungen. Horizontale Dock-Grenzen und Down-Fokusmemory bleiben verbindlich.

0.4.0: Homescreen ohne Bedien-Footer. Glas neutraler (#F3F4F6), Grund-Deckkraft0.8× gespeicherte Intensität, Overlay-Zuschlag0.04 statt0.08; Fokusfläche0.20 statt0.25 mit unverändert klarer Kontur. Vorbereiteter statischer Blur bleibt auf API30 bestehen, kein Echtzeit-Blur pro Frame. Kalender mit sechswöchigem Raster und Agenda, gerundeten Tagesfeldern, Ereignispunkten und expliziten Demo-Labels; Back/Schließen zur Uhr.

V0.5 supersedes tile dimensions above:202×92dp (~2.2:1), logo Fit up to68dp, tile padding4dp. Dock remains82% wide,104dp bottom inset,2dp vertical padding and one horizontal scrolling row. No tutorial footer.550ms launch geometry uses the new tile/icon tokens; return is a220ms0.94→1 alpha transition instead of a fade from black.

V0.6 ersetzt erneut die Kachelgeometrie:232×106dp (~2.2:1),82dp-Logo-Fit. Äußerer Dock82%/104dpunten/2dp Padding unverändert; Viewport für ganze Kacheln (drei auf echtem320dpi-1080p-TV),8dp Zwischenraum/Innenrand, BringIntoView erhält beide Innenränder. Native Richtungstöne nur bei Fokuswechsel, Klick/Langdruck einmalig; UISounds/Intensität in Animationen & Glas, Systemmute respektieren.
