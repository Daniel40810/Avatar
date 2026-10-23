# com.dan.portrait – das Bild

**Stand: Avatar Phase 9 – „Ein Gesicht im Regen“.**

Alles, was aus einer gerenderten Szene ein Porträt macht: Objektiv mit Schärfentiefe, Hintergrund aus Lichtern, Lichtsetzung, Farbgebung. Eingang ist das HDR-Bild mit Tiefe, Ausgang das Bild vor Tonkurve und Korn. Kennt keinen Avatar und keine Szene; der Kern braucht nur Java 21, `gl/` JOGL (OpenGL 4.3). Zum Herauskopieren reicht `src/com/dan/portrait/`. Wiederverwendbar als Fotomodus jeder Szene.

## Kern (reines Java)

| Klasse | Inhalt |
|---|---|
| `Lens` | Brennweite, Blende, Fokus, Blendenform (rund oder 5–9 Lamellen). Bildwinkel `fovy = 2·atan(12 mm / f)` (Kleinbild), Zerstreuungskreis `c(z) = K·|z − s| / z` mit `K = f² / (N·(s − f))` auf die Sensorhöhe 24 mm bezogen, Schärfentiefe nach der hyperfokalen Distanz. |
| `Shot` | Einstellungen Groß, Nah, Halbnah, Amerikanisch, Halbtotale: gezeigte Höhe am Ort der Person und Versatz der Bildmitte; der Abstand folgt aus dem Bildwinkel. |
| `LightSet` | Lichtsetzungen „Regennacht“, „Porträt“, „Rembrandt“, „Abendsonne“, „Nebel“, „Gegenlicht“: Führungslicht (Spot), Kante, Aufhellung, im Rahmen der Kamera gesetzt (das Licht geht beim Umkreisen mit). |
| `BokehLights` | Hintergrund aus Lichtern einer nächtlichen Stadt (Natriumdampf und Türkis, 6–30 m, Fenster bis Laternenhöhe), aus einem Samen; als Fächer der Kamera gegenüber, mit Spiegelbildern auf nassem Boden. `brightest(n)` für Spiegelungen in Augen und Wasser. |
| `Grade` | Farbstile „Regennacht“ (Schatten Türkis, Lichter Bernstein, Mitten leicht entsättigt, S-Kurve), „Abendsonne“, „Nebel“, „neutral“; gerechnet im Gamma-Raum, als 3D-Tabelle n³. |

## Grafikkarte (`gl/PortraitGl`)

* **Hintergrundlichter** als additive Sprites (ein SSBO, 8 Werte je Licht): Größe = Zerstreuungskreis oder Bildgröße des Lichts, Helligkeit mit der Fläche verteilt (Energie bleibt), Form der Blende mit weichem Rand (`bokeh.vert/.frag`).
* **Schärfentiefe** in drei Durchgängen: halbe Auflösung mit Kreis aus der Tiefe (`dof_coc`), Sammeln über 40 Proben auf der goldenen Spirale mit Abdeckungstest (`dof_gather`), Zusammensetzen in voller Auflösung (`dof_comp`). Hintergrund ohne Tiefe gilt als unendlich fern.
* **Farbtabelle** als 3D-Textur RGB16F 32³ für die Nachbearbeitung der Szene.

## Einbau im Studio

`StudioScene.portraitOn` schaltet alles (aus = Bild wie Phase 8). Fokus auf das nähere Auge (weich nachgeführt, 0,12 s), die zwölf hellsten Lichter spiegeln sich in Augen, Perlen und nasser Haut (`AvatarGl.envDir/envCol/envCos`), Fotos in 4K über ein eigenes Ziel (`photoRequest`, PNG oder JPEG). Block BILD im Studio, Statusbild `ant statusbild` (`--portrait`).

Quellen: Kleinbild-Optik und hyperfokale Distanz (Lehrbuchformeln); Separable/Gather-DOF nach Jimenez (2014) und Abadie (2018) vereinfacht; Tonkurve ACES nach Narkowicz (2015).
