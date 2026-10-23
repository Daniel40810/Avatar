# com.dan.gltf – glTF 2.0 lesen

**Stand: Avatar Phase 2 – fertig.**

Das Paket liest glTF 2.0 als `.gltf` (Puffer als data:-URI) und binär als `.glb`, ohne Fremdbibliothek: Szenengraph mit Weltmatrizen, Netze, Normalen, UV, Farben, Skins (Gelenke, Gewichte, inverse Bindematrizen), Morphziele mit Namen, eigene Attribute (`_NAME`), Materialwerte (PBR, Alpha), Texturen und eingebettete Bilder. Es prüft die Datei gegen die Spezifikation und meldet Auffälliges in `warnings`. Beim ersten Laden legt `GltfCache` ein Abbild in eigenem Binärformat an. Es kennt keinen Menschen und keine Szene: wiederverwendbar für jede Geometrie aus Blender (FWaterfall, Kogge, Gebäude, Tiere).

Der Kern braucht nur Java 21. `gl/` lädt Teile mit JOGL (OpenGL 4.3) in Puffer. `demo/GltfCheck` prüft ohne Fenster. Zum Herauskopieren reicht `src/com/dan/gltf/`.

## Benutzung

```java
Gltf g = Gltf.load(Path.of("modell.glb"));                       // oder Gltf.read(bytes)
Gltf g = GltfCache.load(bytes, Path.of("daten/cache"), "modell");  // mit Abbild
for (Gltf.Mesh m : g.meshes)
    for (Gltf.Primitive p : m.primitives) { p.position; p.normal; p.uv0; p.tangent; p.targets; p.extra.get("_BLIDX"); }

GltfGl.PrimitiveGl pg = GltfGl.upload(gl4, p);   // Attribute 0 Lage, 1 Normale, 2 UV, 3 Tangente, 4 Gelenke, 5 Gewichte
pg.update(gl4, neueLagen, neueNormalen);
pg.draw(gl4);
int tex = GltfGl.texture(gl4, g.images.get(i), g.textures.get(t), true);   // sRGB für Farbe
```

## Was es kann

- Kopf und Abschnitte der `.glb` (JSON, BIN), Ausrichtung, Längen; Puffer, Pufferansichten mit Schrittweite (verschachtelt), Zugriffe aller Komponententypen, **normalisiert** und **dünn besetzt** (`sparse`); Indizes 8, 16, 32 Bit
- Morphziele werden **dünn** gehalten (nur Ecken mit Änderung): Der Grundkörper des Avatars hat so 258 Ziele bei wenig Speicher
- **Tangenten rechnet der Lader selbst** (nach Lengyel, feste Reihenfolge, bitgleich bei jedem Lauf), wenn die Datei keine hat: Blenders Export lieferte sie nicht bitgleich, darum exportiert die Kette ohne
- Knoten mit TRS oder Matrix, Weltmatrizen aus der Hierarchie, Standardszene
- Materialien nach `pbrMetallicRoughness`, Normal-, Okklusions-, Emissionstexturen, `alphaMode`, `doubleSided`; Sampler
- Prüfung: Indizes im Bereich, Normalen Einheitslänge, Gewichtssummen 1, Materialien und Texturen vorhanden, gleiche Zahl Morphziele je Teil, `targetNames` passend
- `GltfCache`: Abbild mit Prüfsumme der Quelle; stimmt sie nicht, wird neu gelesen und ältere Abbilder desselben Namens werden gelöscht

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Gltf` | das Modell als öffentliche Felder; `Mat` für Matrizen |
| `GltfReader` | Lesen und Prüfen |
| `GltfCache` | Abbild schreiben und lesen |
| `Tangents` | Tangenten aus Lage, Normale, UV |
| `Json` | JSON-Leser (eigene Kopie) |
| `gl/GltfGl` | Teile in Puffer, Bilder in Texturen |
| `demo/GltfCheck` | Selbsttest mit einer im Speicher gebauten .glb (20 Prüfungen); mit Dateiname: Steckbrief |

## Grenzen

- Kein Schreiben. Keine Erweiterungen (`extensionsRequired` wird gemeldet). Draco- und Meshopt-Kompression werden nicht gelesen; die Kette exportiert ohne.
- Externe Puffer und Bilder (Dateien neben einer `.gltf`) werden nicht gelesen, nur eingebettete.
- Animationen werden noch nicht gelesen (kommen mit der Kür, Phase 18).
