# tools/blender – die Werkzeugkette

`avatar_build.py` baut den Grundkörper des Avatars aus MPFB2 und legt ihn nach `src/com/dan/avatar/res/` (F24 A: jeder Schritt als Skript, wiederholbar und versioniert).

```
"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe" -b -P tools/blender/avatar_build.py -- --twice
```

oder aus NetBeans / Ant: `ant blender-glb`.

| Schalter | Wirkung |
|---|---|
| `--out DIR` | Zielordner, Vorgabe `src/com/dan/avatar/res` |
| `--name NAME` | Dateiname ohne Endung, Vorgabe `avatar_base` |
| `--twice` | baut danach in einem frischen Blender-Prozess noch einmal und vergleicht die SHA-256 (Abnahme Phase 1) |

Ergebnis: `avatar_base.glb`, `avatar_base.protokoll.json` (Blender- und MPFB-Version, Pakete, Ecken, Dreiecke, Knochen mit Namen, Morphziele nach Gruppen, Ausstattung, was fehlt, Abweichung der Makro-Mischung gegen MPFB, Quellen und Lizenzen, Prüfsummen, Zeitprotokoll) und `avatar_base.referenz.bin` (fünf Personen, jede Blender-Ecke aller Netze, für den Abgleich mit dem Lader).

## Fassung 1.7 (Phase 10): Kleiderschrank

Fünfzehn Kleidungsnetze statt einem, alle **CC0** (Lizenz je Teil aus der `.mhclo` im Protokoll): `male_casualsuit01` (wie bisher), `toigo_fisherman_sweater`, `donitz_monk_robe` mit `donitz_monk_robe_hood` und `_hood_down`, `toigo_wool_pants`, `cortu_cargo_pants`, `cortu_jeans_shorts`, `toigo_basic_tucked_t-shirt`, `toigo_ankle_boots_female`, `culturalibre_hero_boots_2`, `toigo_mj_cloth_shoes`, `rehmanpolanski_viking_tunic`, `_pants`, `_boots`. Dafür sind die CC0-Pakete `suits02`, `masks01` und `hats01` aus `Documents/Assets` installiert. Zwei Kandidaten aus CC0-Paketen tragen in ihrer `.mhclo` AGPL3 (`scailman_semitransparent_water_boots`, `jujube_newsboy_cap`) und bleiben draußen.

- Kleidung folgt nur den Makro-Zielen (am Kopf auch den Gesichtsreglern); Haar, Augen und das alte Outfit wie bisher allen.
- `_VERDECKT` trägt jetzt ein Bit je Kleidungsstück (die Gruppe „Delete.…“ genau zum Teil); die Zuordnung steht in `avatar_base.kleidung.json`. Teile ohne Gruppe verdeckt der Lader selbst (`Wardrobe.cover`).
- Kleidung nur mit Farbtextur (Normalen- und Glanzkarten fallen weg, der Stoff kommt aus dem Shader), höchstens 1024 Bildpunkte.
- Ein Lauf gut 8 min, mit `--twice` 17 min; zweimal bitgleich. Die Datei wächst auf 140 MB (Morphziele der Kleidung rund 60 MB, Bilder 50 MB).

`kleidung_vorschau.py` rendert Kandidaten mit Workbench (nur zum Ansehen).

## Fassung 1.6 (Phase 6): Frisuren

Fünf Frisuren mehr in der Ausstattung, zusammen sechs: `bob02`, `long01`, `braid01`, `short02`, `afro01` (MakeHuman-Systempakete, CC0) und `elvs_reverse_french_braid_bun` (Netz `rev_fr_braidbun1`, Community-Paket, **CC BY** – Namensnennung im Protokoll und im Werkbuch). Jede folgt den Morphs wie die übrige Ausstattung. `mhclo_license()` liest die Lizenz aus der `.mhclo`-Datei und schreibt sie je Teil ins Protokoll (`ausstattung[].lizenz`, `quellen`). Ein Lauf dauert jetzt gut 5 min (Anpassen der Ausstattung an 78 Ziele), mit `--twice` gut 10 min; zweimal bitgleich. Die Datei wächst auf 82,3 MB.

## Hauttexturen (Phase 4): `haut_build.py`

```
"C:\Program Files\Blender Foundation\Blender 5.2\blender.exe" -b -P tools/blender/haut_build.py -- --twice
```

oder `ant blender-haut`. Nimmt die sechs hellen Grundhäute aus den MakeHuman-Systempaketen (CC0; `young/middleage/old_caucasian_female/male`), speichert sie als JPEG 2048² (Güte 92, ohne Tonkurve) nach `src/com/dan/avatar/res/haut/` und schreibt `haut.json` (Quelle, Lizenz, SHA-256 von Quelle und Ziel). `--twice` baut in einem frischen Blender noch einmal und vergleicht: bitgleich. Zusammen 3,4 MB.

## Fassung 1.4 (Phase 2)

- **Makros wie MPFB:** Ziele direkt aus `macrodetails/` (universal, Volksgruppen, Größe, Proportionen) und `breast/`, je Ecke Geschlecht × Alter mit Muskeln, Gewicht und Kreuzterm; Gewichte nach den Stützkurven aus `macro.json`. `gewichte()` hier und `Person.weights()` in Java rechnen gleich.
- **Ausstattung folgt den Morphs:** je Ziel in Blender neu angepasst (`ClothesService.fit_clothes_to_human`), als Shape Keys mit gleichem Namen.
- **Merkmale:** `_BLIDX` (Blender-Nummer jeder Ecke), `_VERDECKT` (Körper unter der Kleidung, aus den Gruppen `Delete.…`).
- Keine Morph-Normalen (der Lader rechnet Normalen), keine Tangenten.
- Dauer rund 2,5 min je Lauf, mit `--twice` gut 5 min.

Das Skript nimmt die MPFB-Installation, die die meisten Inhaltspakete kennt. Augen, Zähne, Zunge, Brauen, Wimpern, Frisur, Outfit, die 52 ARKit-Formen und die 15 Viseme kommen automatisch dazu, sobald die Pakete installiert sind; vorher stehen sie unter `fehlt`.
