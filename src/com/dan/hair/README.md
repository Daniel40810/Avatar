# com.dan.hair – Haare

**Stand: Avatar Phase 6 – Haare.** Nässe (Strähnen kleben, dunkler, glänzender) folgt in Phase 8.

Haare als Mischform (F10 A): **Haarkarten** für die Masse, dazu **einzelne Haare** als Linien im Gesicht und am Rand (Fliegenhaare) und für den Bart. Licht nach **Marschner** (R, TT, TRT) in der Näherung von Karis, Selbstschatten als tiefe Deckung, Durchsicht **ohne Sortieren**. Bewegung durch Kopf und Wind.

Das Paket kennt keinen Menschen: Es bekommt Netze (verformte Lagen und Normalen in GL-Puffern), Licht und einen Wirbelpunkt von außen und ist damit auch für Fell, Mähnen und Gräser in Nahaufnahme zu gebrauchen. Der Kern braucht nur Java 21, `gl/` JOGL (OpenGL 4.3). Zum Herauskopieren reicht `src/com/dan/hair/`.

## Benutzung

```java
HairGl h = new HairGl(); h.init(gl);
HairGl.Card card = new HairGl.Card();          // VAO mit Attribut 0 Lage, 1 Normale, 2 UV, 8 (Biegsamkeit, Richtung)
card.vao = …; card.indexCount = …; card.texture = …;
HairGl.Lighting li = …;                        // Lichter, Schattenkarte, Auge, Zeit
h.look.albedo = HairColor.albedo(0.6, 0.1);    // Eumelanin, Rot
h.drawCards(gl, card, li, h.look, viewProj, 0); // deckend, schreibt Tiefe
h.drawCards(gl, card, li, h.look, viewProj, 1); // Saum, gemischt
Strands s = Strands.grow(pos, nrm, uv, idx, dichte, params);
HairGl.StrandSet set = HairGl.upload(gl, s, idx, posBuf, nrmBuf, 8);
h.drawStrands(gl, set, li, viewProj);
dyn.step(dt, kopf, wind, t); HairGl.motion(h.look, dyn);
```

## Bausteine

| Klasse | Aufgabe |
|---|---|
| `HairColor` | lineare Albedo aus Eumelanin (platin … schwarz) und Phäomelanin (Rot, bei hellem Haar stark) |
| `Strands` | einzelne Haare wachsen lassen: Wurzel baryzentrisch in einem Dreieck, Richtung im Rahmen des Dreiecks (folgt Morphs und Skinning), Länge, Locke, Biegsamkeit, Grau; Haarrichtung einer Karte (`cardAxis`, `cardTangents`) und Fluss vom Wirbel (`flowTangents`) für Kappen ohne Kartenaufteilung |
| `HairDynamics` | Feder am Kopf (Trägheit bei Kopfbewegung, kommt zur Ruhe) und Wind mit Böen; Ergebnis ist ein Versatz der Spitzen, verteilt mit Biegsamkeit² (Wurzel fest) |
| `gl/HairGl` | Programme, Zeichnen der Karten (zwei Durchgänge) und Linien, Tiefe für die Schattenkarte, Prüfansicht `-Dhair.debug=1/2/3` (Richtung, Textur, Selbstschatten) |
| `gl/hairlight.glsl` | Haar-BSDF (R zur Wurzel verschoben, TT im Gegenlicht, TRT farbig zur Spitze), Mehrfachstreuung nach Kajiya-Kay, tiefe Deckung exp(−Weg/15 mm) aus der Schattenkarte mit gedrehtem Kern |
| `gl/hair.vert`, `gl/hair.frag`, `gl/hair_depth.frag` | Karten: Biegen durch Kopf und Wind, Strähnenvielfalt in schmalen Zellen längs der Haare, graue Haare als Salz und Pfeffer |
| `gl/strand.vert`, `gl/strand.frag` | Linien ohne eigene Puffer: Lage aus Dreieck und Daten (SSBO), Schwerkraft, Locke, Wind; Deckung nach Pixelbreite und geglättete Linien |

## Durchsicht ohne Sortieren

Statt Alpha-to-Coverage (das Studio zeichnet in ein HDR-Ziel ohne MSAA) zwei Durchgänge: **deckend** (Alpha ≥ 0,5, schreibt Tiefe) und **Saum** (0,12 ≤ Alpha < 0,5, gemischt, ohne Tiefe zu schreiben). Einzelne Haare danach gemischt; ein Haar dünner als ein Pixel wird durchsichtiger statt flimmernd. Fehler der Reihenfolge bleiben auf den dünnen Saum beschränkt.

## Flimmern

Gemessen als zweite zeitliche Ableitung |I(t+1) − 2I(t) + I(t−1)| auf den Bildpunkten der einzelnen Haare bei Bewegung unter einem Pixel je Bild: Deckung nach Pixelbreite ≈ 1,4, harte 1-Pixel-Linien ≈ 11,4, Bild ohne einzelne Haare ≈ 0,8 (Statusbild Phase 6).

## Grenzen (Stand Phase 6)

- Keine Federketten je Strähne: eine Feder je Kopf biegt alle Karten gleich (mit Biegsamkeit²). Strähnen schlagen nicht durch, kollidieren aber auch nicht mit Schultern und Gesicht.
- Haarrichtung je Ecke in der Grundform; bei starker Kopfdrehung folgt sie der Karte nicht (Phase 7 dreht den Kopf mehr).
- Selbstschatten nur aus dem Führungslicht, eine Schicht tief (keine Opacity-Map in Scheiben).
- Fliegenhaare wachsen entlang der Karten; ausgesuchte Strähnen vor den Augen (Stilmittel) gibt es noch nicht.
