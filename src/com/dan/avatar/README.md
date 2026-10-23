# com.dan.avatar – der Avatar

**Stand: Avatar Phase 15 – Rückfluss nach Lighthouse.** Der Avatar steht als Wärter auf der Galerie des Roten Sands (Projekt Lighthouse); Personen liegen seit Phase 14 in der Datenbank AVATAR. Die Schnittstelle zur Szene (`World`, `Snapshot`), Ziele für die Bewegung, die Kapuzen-Geste und die Regie (`Regie`, `Actor`) kamen mit Phase 13; Haut seit Phase 4, Augen und Mund 5, Haare 6, Verhalten 7, Nässe 8, Kleidung 10, Bewegung 11, Detailstufen und Mengen 12.

Ein Mensch für alle Szenen, männlich oder weiblich, jung oder alt, der aus der Nähe trägt und auch dann lebt, wenn er nichts tut. Gebaut in sechs Schichten: **Person** (eine Handvoll Zahlen mit `seed`), **Körper** (Grundnetz aus Blender, Morphs, Detailstufen), **Rig** (ein Skelett für alle, Gesichtsformen, IK), **Verhalten** (Leben, Blick, Gefühl, Gang, Gesten), **Umwelt** (Licht, Nässe, Wind, Kälte) und **Darstellung** (`gl/`: Haut, Augen, Zähne, Stoff, Tropfen, Schatten).

Der Kern braucht nur Java 21. `gl/` braucht JOGL (OpenGL 4.3). Das Paket darf `com.dan.gltf` nutzen, sonst nichts. Zum Herauskopieren reichen `src/com/dan/avatar/` und `src/com/dan/gltf/`.

## Benutzung

```java
Gltf model = GltfCache.load(bytesVonAvatarBaseGlb, Path.of("daten/cache"), "avatar_base");
Body body = new Body(model);
Person p = Person.wuerfeln(4711);          // oder new Person() = Mitte aller Regler
p.gender = 0.1; p.age = Person.ageFromYears(31);
body.mix(p);                                // alle Netze, Normalen neu, rund 10 ms

Skeleton sk = new Skeleton(model, rigJson);  // avatar_base.rig.json
Map<String, Double> w = p.weights();
double[][] rest = sk.restFor(w);             // Gelenke folgen den Morphs
double[][] pose = sk.pose(sk.poses.get("sitzen"), rest);   // mit Boden und Fuß-IK
double[][] skin = Skeleton.skin(pose, rest);

AvatarGl g = new AvatarGl();
g.init(gl, body);                            // im GL-Faden
g.setWeights(gl, w);                         // Morphs und Normalen (Compute), nur bei Änderung
g.setSkin(gl, Skeleton.toDq(skin), Skeleton.toMat(skin), true);   // Dual-Quaternion (Compute)
g.setPerson(p);                              // Haut: Ton, Grundhäute, Zonen, Versatz der Detailkarten

// Schattenkarte des Führungslichts, dann zeichnen; diffuses Hautlicht in Ziel 1, danach streuen
ShadowMap sm = new ShadowMap();
sm.fit(light.keyPos, mitteDerPersonen, radius);
sm.begin(gl, 2048); g.drawDepth(gl, sm.viewProj, x, -body.footY(w), z); sm.end(gl);
AvatarGl.Shading sh = new AvatarGl.Shading();
sh.shadow = sm; sh.split = true;             // „Niedrig“: sh.low = true, ohne Streuung
g.draw(gl, viewProj, eye, light, sh, x, -body.footY(w), z);     // Füße auf y = 0
new SkinScatter().apply(gl, diffuseTex, depthTex, fbo, w, h, focalPx, near, far);
```

## Person

| Feld | Bereich | Bedeutung |
|---|---|---|
| `gender` | 0…1 | weiblich … männlich |
| `age` | 0…1 | 1, 11, 25, 90 Jahre an 0 / 0,1875 / 0,5 / 1 (`ageYears()`, `ageFromYears()`) |
| `muscle`, `weight`, `height`, `proportions`, `cupsize`, `firmness` | 0…1 | Mitte 0,5 |
| `face` | −1…+1 | 33 Gesichtsregler (`Person.FACE`) |
| `forms` | 0…1 | ARKit-Formen (`arkit.jawOpen` …) und Viseme (`visem.viseme_O` …) |

`wuerfeln(seed)` erfindet eine erwachsene Person (18–75 Jahre, Geschlecht eher deutlich, Gewicht steigt mit dem Alter, Gesicht leicht gestreut). Gleicher `seed`, gleiche Person.

`weights()` rechnet die Gewichte der Morphziele **genau wie MPFB**: Stützkurven aus `macro.json` (Bereiche −0,01…1,01, auf vier Stellen gerundet), Ecken Geschlecht × Alter, darin Muskeln und Gewicht mit Kreuzterm exakt. Größe, Proportionen und Brust gelten für mittlere Muskeln und mittleres Gewicht; gegen MPFB weicht das bei den fünf Referenzpersonen um höchstens 11,6 mm ab (Protokoll, `linearisierung`). Die Blender-Kette rechnet dieselben Gewichte in Python; die Prüfung vergleicht beide.

## Grundkörper (`res/avatar_base.glb`)

| Teil | Inhalt |
|---|---|
| Netze | Körper (`base`, 13 380 Ecken) und Ausstattung: Augen `high-poly`, Zähne `teeth_base`, Zunge `tongue01`, Brauen `eyebrow001`, Wimpern `eyelashes01`, Frisur `bob02`, Outfit `male_casualsuit01`; 8 Bilder |
| Rig | `game_engine` aus MPFB2, 53 Knochen mit Fingern (wird ab Phase 3 genutzt) |
| Makro-Ziele | `macro.basis` (immer −1), je Ecke `macro.<w|m>.<baby|kind|jung|alt>` mit `.muscle.min/max`, `.weight.min/max`, `.mw.<min|max>.<min|max>`, `.height.min/max`, `.prop.uncommon/ideal`; `macro.brust.<alter>.<cup>.<festigkeit>` |
| Gesichtsregler | `face.<regler>.decr` / `.incr`, 33 Regler, links und rechts zusammengefasst |
| Gesichtsformen | 52 nach ARKit (`arkit.*`), 15 Viseme (`visem.*`) |
| Ausstattung | trägt dieselben Ziele (soweit sie wirken): in Blender je Ziel neu angepasst, damit Augen, Haar und Kleidung jeder Person folgen |
| `_BLIDX` | Blender-Nummer jeder Ecke: glatte Normalen über Nähte, Abgleich mit Blender |

Daneben: `avatar_base.protokoll.json` (Werkzeug, Versionen, Zahlen, Quellen, Lizenzen, Prüfsummen) und `avatar_base.referenz.bin` (fünf Personen aus Blender, Ecke für Ecke, für den Abgleich auf 0,1 mm).

## Rig, Posen, Skinning (Phase 3)

- **Skelett:** `game_engine` aus MPFB2, 53 Knochen (mit Fingern, ohne Augen- und Kieferknochen; Kiefer und Lider laufen über die ARKit-Formen). Ruhelage aus den inversen Bindematrizen der `.glb`.
- **Gelenke folgen den Morphs:** Die Blender-Kette passt das Rig je Morphziel neu an (MPFB `refit`) und speichert, wie sich die Gelenkköpfe verschieben (78 Ziele bewegen Gelenke). `Skeleton.restFor(gewichte)` addiert das; die Ausrichtung bleibt die der Mitte. Abgleich mit Blender für fünf Personen.
- **Posen:** In Blender werden Knochen auf Zielrichtungen ausgerichtet (`stehen`, `sitzen`, `lehnen`, `hand_ans_gesicht`, `unterarm_drehen`); exportiert wird je Knochen D = Pose · Ruhe⁻¹. `Pose` macht daraus eine örtliche Bewegung je Gelenk im Ruherahmen, die auf jede Statur passt.
- **Boden und Fuß-IK:** Sitzen senkt die ganze Pose, bis die Fußgelenke auf Ruhehöhe stehen. Dann setzt eine Zwei-Knochen-IK (Oberschenkel, Wade) die Fußgelenke der Standbeine auf Ruhehöhe und richtet den Fuß wie in Ruhe aus: Sohle flach am Boden, bei jeder Größe. `lehnen` hat nur das linke Bein als Standbein.
- **Skinning:** je Gelenk S = Pose · Ruhe⁻¹. Standard ist Dual-Quaternion (Volumenerhalt, Kavan 2007, Vorzeichen nach der laufenden Summe wie Blender); linear zum Vergleich. Höchstens 4 Knochen je Ecke, normalisiert (so auch in Blender).
- **Grafikkarte:** `gl/morph.comp` (Morphs je Ecke, Einträge nach Ziel geordnet), `gl/trinorm.comp` und `gl/vnorm.comp` (Normalen, über Blender-Ecken geglättet), `gl/skin.comp` (Skinning direkt in die Zeichenpuffer). Gleiche Rechnung wie `Body.mix` und `Skinning` in Java.

## Haut (Phase 4)

- **Grundhäute (F6 A):** sechs CC0-Häute aus den MakeHuman-Systempaketen (jung, mittel, alt × Frau, Mann), 2048², vorbereitet von `tools/blender/haut_build.py` nach `res/haut/` (mit SHA-256 in `haut.json`). Gemischt nach Alter und Geschlecht; jede durch ihre mittlere Hautfarbe geteilt, so bleibt nur das Muster (Lippen, Lider, Adern, Flecken). Den Ton gibt `Skin.tone(melanin, rötung)` an fünf Stützstellen von sehr hell bis sehr dunkel.
- **Detailkarten (`SkinDetail`):** aus Code und `seed`, kachelbar, 512²: Poren (Mulden), Fältchen (zwei Scharen Rillen), Flecken, Haarbälge, Marmorierung. Jede Person verschiebt sie um einen Versatz aus ihrem `seed`. Sommersprossen und Altersflecken nutzen dieselbe Karte in gröberer Kachel.
- **Zonen (`SkinMasks`):** je Körperecke aus der Grundform und Landmarken (Augen, Mund, Nasenspitze, Kinn, Ohren): Bart, Rötung, Sommersprossen, T-Zone, Falten, Lippen, Ohr und die Dicke (kürzester von fünf Strahlen nach innen, Gitter über die Dreiecke). Einmal je Modell, rund 0,1–0,4 s.
- **Licht (F8 A):** Detailnormalen über den Oberflächengradienten (ohne Tangenten), zwei Glanzlagen (GGX, Haut und Fettfilm, Fresnel 0,028), weicher Schatten aus der Schattenkarte (12 Abgriffe Poisson), Durchleuchtung nach Jimenez 2010: Weg durch den Körper aus der Schattenkarte und Eckendicke, beide müssen dünn sein; nur wo das Licht nicht direkt trifft.
- **Streuung im Bildraum (`SkinScatter`):** trennbarer Kern nach Jimenez 2015 aus dem Profil von d'Eon und Luebke (sechs Glocken), 11/17/25 Proben je Stufe, Kern für helle und dunkle Haut je Pixel nach Melanin gemischt, folgt der Oberfläche.
- **„Niedrig“:** vorab integrierte Streuung (Penner 2011) als Tabelle 64 × 32 aus demselben Profil, Krümmung aus den Bildableitungen; kein eigener Durchgang.
- **Altern:** Grundhaut „mittel“ und „alt“, mehr Fältchen (besonders in der Faltenzone), Altersflecken ab 45, Haut matter, weniger Fett.

## Augen, Zähne, Mund (Phase 5)

- **Augäpfel (`EyeRig`):** Das MPFB-Auge hat je Seite zwei Schalen. Der Augapfel wird in der Grundform als Kugel angepasst (r ≈ 15 mm, ohne die Irismulde), die Blickachse geht durch die Mulde. Zur Laufzeit folgen Mitte, Achsen und Radius dem gemischten und verformten Augennetz (Blickformen `eyeLook…`, Gesichtsregler, Kopf); `frames(body, gewichte, dq)` rechnet dafür nur das Augennetz auf dem Prozessor. Die Hornhaut-Schale wird nicht gezeichnet.
- **Shader (F9 A):** je Bildpunkt analytisch: Hornhaut als Kugelkappe (r = 0,64 R, Scheitel 1,04 R), Brechung n = 1,376 auf die Irisebene (0,80 R, Radius 0,36 R), daher Tiefe beim Drehen des Kopfes. Iris aus Parametern und `seed`: Grundfarbe von blau bis dunkelbraun (`Person.iris`), Fasern, Krause, Krypten, Pupillensaum, Limbusring; Lederhaut mit Adern und Rötung in den Winkeln; Schatten der Lider (Abdunklung oben und in den Winkeln) und die Schattenkarte; Glanz der Hornhaut (GGX, kleine Kugellichter) und eine gespiegelte dunkle Umgebung.
- **Pupille:** `EyeRig.pupilMm(L)` nach Moon und Spencer (1944); das Studio rechnet die Beleuchtung am Auge aus den drei Lichtern und passt den Durchmesser an (enger in 0,25 s, weiter in 1,5 s).
- **Tränenlinie:** als nasse Zone der Haut (Lidrand: Haut höchstens einige mm vor dem Augapfel, sehr glatter Glanz) statt eigenem Netz; folgt so Lidern, Blinzeln und Morphs ohne eigenes Rig.
- **Mund:** Zähne und Zunge tragen die Tiefe hinter der Lippenfront, das Mundinnere der Haut ebenso (`SkinMasks.extra`). Abgedunkelt wird nach Tiefe und Öffnung (`AvatarGl.mouthOpening(person)` aus `jawOpen`, Lächeln, Visemen); Zähne höchstens 72 % Albedo, leicht getönt: kein „leuchtendes Gebiss“.

## Haare (Phase 6)

- **Person:** `hair` (bob, lang, zopf, dutt, kurz, locken, glatze), `hairColor` (Eumelanin), `hairRed`, `grey`, `beardLength`; der Würfel zieht sie nach allen anderen Werten (Körper und Haut bleiben gleich). Frauen ohne Bart und Glatze, dunkle Haut fast immer dunkles Haar, Grau nach Alter.
- **Grundkörper:** sechs Frisuren der Bibliothek im Modell (`bob02`, `long01`, `braid01`, `rev_fr_braidbun1`, `short02`, `afro01`), alle folgen den Morphs; die Lizenzen stehen im Protokoll.
- **Paketgrenze:** dieses Paket kennt `com.dan.hair` nicht. `AvatarGl` gibt Teile an einen fremden Zeichner ab (`external`) und reicht dafür VAO, Zeichenpuffer, Textur und Attribut 8 heraus (`primGl`, `baseTexture`, `attrib8`). Zusammengesteckt wird im Studio (`HairRig`).
- **Grundhäute:** Das Geschlecht mischt die Häute nur noch in der Mitte (0,3 … 0,7); die Männerhäute tragen gemalten Bartschatten, der seit Phase 4 einen eigenen Regler hat.
- **Mundinneres:** nur noch in der Öffnung zwischen den Lippen (tiefer die ganze Mundhöhle) und nicht auf nach vorn gewandter Haut; vorher wurden Oberlippe und Kinn seitlich mit abgedunkelt.

## Leben, Blick, Gefühl (Phase 7)

```java
Behavior b = new Behavior(person.seed);
b.calibrate(eyeRig, body, gewichteOhneGesichtsformen);   // einmal: Augenmitten, Reichweite der Blickformen
b.feel("freudig", 0.8);                                 // oder b.feelAt(valenz, erregung, stärke)
b.look(Behavior.Look.CAMERA);                           // CAMERA, FREE, HORIZON oder b.lookAt(punkt)
// je Bild:
b.camera = kameraInFigurKoordinaten;
b.step(dt);
b.additive(add);                                        // Kopf, Atem, Becken
double[][] w = skeleton.pose(pose, rest, true, add);    // Füße bleiben stehen
Map<String, Double> formen = b.forms(w, rest, skeleton);// "arkit.…" 0…1, zu den Gewichten der Person addieren
```

- **Leben:** Atmen (12–20 Züge/min nach Erregung, Einatmen 38 %, Ausatmen 50 %, Pause, ab und zu ein Seufzer) über Brustwirbel und Schlüsselbeine; Lidschlag nach Verteilung (verschobene Lognormalverteilung, im Mittel gut 16/min, Schließen 75–100 ms, Öffnen 140–220 ms, jeder zehnte halb), nach großen Blicksprüngen oft ein Lidschlag; Mikrobewegungen des Kopfes aus glattem Rauschen; im Stand alle 5–14 s eine Gewichtsverlagerung (Becken 2 cm zur Seite und gekippt, Wirbelsäule gleicht aus, Fuß-IK hält die Füße).
- **Blick:** Ziel mit Ablage: kleine Sprünge zwischen Augen und Mund des Gegenübers, ab und zu ein Wegschauen (nachdenklich nach oben, traurig nach unten). Sakkaden nach der Hauptfolge (21 ms + 2,2 ms/°), Profil mit kleinstem Ruck, Reaktionszeit 170–240 ms bei Sprüngen des Ziels, Folgebewegung bis 40°/s. Der Kopf folgt als gedämpfte Feder, sein Anteil wächst mit der Weite und ist mindestens so groß, dass die Augen in 80 % ihrer Reichweite bleiben. Die Augen werden aus der wirklichen Lage des Kopfes auf den Blickpunkt gerechnet (beide Augen, also mit Vergenz) und über die ARKit-Blickformen gestellt; die Reichweite der Formen wird beim Einmessen gemessen (MPFB: etwa ±19° seitlich, +12°/−14° senkrecht). Die Lider folgen dem Blick nach unten.
- **Gefühl:** `Emotion` mit elf benannten Gefühlen im Raum aus Valenz und Erregung (neutral, ruhig, nachdenklich, freudig, überrascht, entschlossen, traurig, müde, frierend, ärgerlich, ängstlich), gemischt mit einem Gauß-Kern. Jedes Gefühl: ARKit-Formen, dazu Kopfhaltung, Blick nach unten, Neigung, Schultern, Takt von Lidschlag, Atem und Blick. Jede Form hat eigene Trägheit (Brauen 0,10 s, Augen 0,16 s, Mund 0,26 s; Abklingen drei- bis viermal langsamer), die Seiten sind je Person verschieden stark und versetzt. Mikroausdrücke: alle 20–65 s für 140–320 ms ein anderes Gefühl, schwach.
- **Regie:** `done("blick")`, `done("gefuehl")` für `warte`. **Leben aus** (`alive = false`): Standbild mit Gefühl und Blick, ohne Lidschlag. **Zeitlupe** über `timeScale`.
- **Skelett:** `Skeleton.Additive` (Becken verschieben, Gelenke um Weltachsen drehen) wirkt nach der Pose und vor der Fuß-IK; `Skeleton.skinRotation` für fremde Zeichner (die Haarrichtung der Karten dreht mit dem Kopf).
- **Grafikkarte:** `AvatarGl.idle` (nicht gebrauchte Teile, z. B. die übrigen Frisuren) werden weder gemischt noch verformt; `setWeights` rechnet nur Teile, deren Ziele sich geändert haben. `setMouthOpen` für das Mundinnere in jedem Bild.

## Nässe, Regen, Kälte (Phase 8)

```java
Climate c = Climate.regennacht();                 // oder sturm(), wintermorgen(), sommerabend(), of(...)
Wetness w = new Wetness();                        // Nässe je Zone
SkinDrops d = new SkinDrops(new DropField(body), person.seed);
BreathFog fog = new BreathFog(person.seed);
// je Bild
w.step(dt, c);
d.film = w.wet(Wetness.FACE);
d.step(dt, c.rain * (1 - 0.78 * c.hood), regenAusRichtungImKopfRuherahmen, verdunstung, true);
fog.step(dt, behavior.breathFlow(), nase, richtung, c.breathFog(), c.wind);
behavior.cold = c.coldness(w.wet(Wetness.FACE));
behavior.rainOnFace = Math.min(1, w.inflow(Wetness.FACE) / 5);
```

- **Klima** (`Climate`): Regen (mm/h), Wind (m/s, Figur-Koordinaten), Temperatur, Feuchte, Kapuze; gefühlte Temperatur nach der Windchill-Formel, Kälte 0…1 (nasse Haut im Wind zählt dazu), Sichtbarkeit des Atemhauchs (bei feuchter Luft ab etwa 10 °C, bei trockener erst um 2 °C) [G]. Presets „Regennacht“ (5 mm/h, 3 m/s, 9 °C, 95 %), „Sturm“ (18 mm/h, 17 m/s), „Wintermorgen“ (−6 °C), „Sommerabend“ (24 °C).
- **Nässe** (`Wetness`): acht Zonen (Kopfhaut, Gesicht, Haar, Schultern, Brust, Rücken, Arme, Beine) mit eigener Aufnahme (Gesicht 0,07 mm Film, Haar 0,4–1,6 mm je Frisur). Regen trifft jede Fläche nach ihrer Lage zur Fallrichtung (2-mm-Tropfen mit 6,5 m/s, der Wind treibt sie schräg); was nicht mehr hält, läuft ab (Haar → Gesicht, Nacken, Schultern; Gesicht → Brust …). Die Kapuze schützt Kopfhaut und Haar zu 95 %, das Gesicht zu 78 %. Trocknen nach Wind, Temperatur und Feuchte; Haut trocknet schneller als Haar und Stoff [G].
- **Tropfen** (`DropField`, `SkinDrops`): ein Gitter von 256² Zellen über dem UV-Ausschnitt von Gesicht, Ohren und Hals, je Zelle Normale, Fläche, Steilheit und die Richtung bergab im Texturraum. Einschläge nach Marshall und Palmer und Atlas (bei 5 mm/h gut 2 100 Tropfen über 0,5 mm je m² und s), die Hälfte bleibt als Perle; Perlen wachsen und fließen zusammen (auf nasser Haut leichter), ab etwa 1,6 mm Halbmesser (geteilt durch die Steilheit) laufen sie bergab, ziehen eine Spur und tropfen am Rand ab. Über dem Haaransatz hält das Haar den Regen.
- **Atemhauch** (`BreathFog`): beim Ausatmen (`Behavior.breathFlow`) Wölkchen aus der Nase, bei offenem Mund aus dem Mund; sie werden gebremst, weiten sich, steigen etwas und treiben mit dem Wind, nach 1–2 s fort.
- **Verhalten**: `cold` hebt die Schultern, macht das Atmen schneller, bringt Muskelzittern in Schüben um 10 Hz und bei großer Kälte Zähneklappern; `rainOnFace` kneift die Augen zusammen und lässt öfter blinzeln.
- **Grafikkarte**: `gl/DropMapGl` zeichnet Perlen (Höhe in mm, 2048², in jedem Bild neu) und Spuren (1024², trocknen langsam) im Texturraum; `avatar.frag` legt daraus Wasser auf die Haut: Film nach Zone, Perlen mit eigener Normale, Glanz für Wasser (F0 0,02, sehr glatt), Spiegelung der Bühne, dunkler Rand und gebündeltes Licht in der Perle. Stoff wird nass dunkler, Schultern und die Seite zum Regen zuerst. Kälte rötet Nase, Wangen und Ohren, die Lippen werden bläulich. `gl/BreathGl` zeichnet den Hauch als weiche Punkte, die nach vorn streuen.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Person` | Regler (auch Haut: Melanin, Rötung, Sommersprossen, Bart, Poren, Glanz; Iris; Frisur, Haarfarbe, Grau, Bartlänge), Würfel (SplitMix64), Gewichte wie MPFB |
| `Skin` | Ton, Mischung der Grundhäute, Streuprofil, trennbarer Kern, Durchlässigkeit, vorab integrierte Tabelle |
| `SkinDetail` | kachelbare Detailkarten aus `seed` |
| `SkinMasks` | Hautzonen und Dicke je Körperecke |
| `SkinTextures` | Grundhäute laden, mittlere Hautfarbe über die UV-Fläche |
| `EyeRig` | Augäpfel als Kugeln, Lage zur Laufzeit, Pupille nach Licht |
| `Body` | alle Netze eines Modells, Mischen auf dem Prozessor (dünn besetzt, parallel je Netz), Normalen über `_BLIDX` geglättet, Fußhöhe |
| `Skeleton` | Gelenke, Ruhe nach Morphs, Pose vorwärts, Zugabe des Verhaltens (`Additive`), Boden, Zwei-Knochen-IK, Skin-Matrizen, DQ |
| `Behavior` | Leben, Blick, Gefühl: Atem, Lidschlag, Sakkaden, Kopf, Gewicht, Mimik mit Trägheit, Augenformen aus der Kopflage |
| `Emotion` | Gefühlsraum: benannte Gefühle, Gauß-Mischung, ARKit-Namen |
| `Noise` | glattes Rauschen in der Zeit für Mikrobewegungen |
| `Climate` | Umwelt am Ort: Regen, Wind, Temperatur, Feuchte, Kapuze; Windchill, Kälte, Atemhauch; Presets |
| `Wetness` | Nässe je Zone mit Aufnahme, Ablauf und Trocknen |
| `DropField`, `SkinDrops` | Gitter über dem Kopf im Texturraum; Perlen, die wachsen, laufen und Spuren ziehen |
| `BreathFog` | Atemhauch als Wölkchen |
| `gl/DropMapGl`, `gl/drop.vert`, `gl/drop.frag` | Perlen- und Spurenkarte auf der Grafikkarte |
| `gl/BreathGl`, `gl/breath.vert`, `gl/breath.frag` | Atemhauch zeichnen |
| `Pose` | örtliche Bewegungen je Gelenk aus Blenders D = Pose · Ruhe⁻¹ |
| `Dq` | Dual-Quaternion aus Matrix, Blenden und Verformen wie `skin.comp`; lineares Skinning |
| `Skinning` | Skinning auf dem Prozessor (Referenz, Treffer, Prüfung) |
| `M4` | 4×4-Matrizen in double |
| `gl/AvatarGl` | Puffer, Compute (Morph, Normalen, Skin), Texturen, Zonen als Eckattribute, Zeichnen (`Light`, `Shading`), Tiefe für die Schattenkarte, Rücklesen zur Prüfung, Teile für fremde Zeichner (`external`) |
| `gl/ShadowMap` | Schattenkarte des Führungslichts, auf die Personen zugeschnitten |
| `gl/SkinScatter` | Streuung im Bildraum, zwei Durchgänge |
| `gl/SkinGl` | geteilte Hautressourcen je GL-Kontext (Grundhäute DXT1, Detailkarten, Tabelle) |
| `gl/morph.comp`, `gl/trinorm.comp`, `gl/vnorm.comp`, `gl/skin.comp` | Compute-Shader |
| `gl/avatar.vert`, `gl/avatar.frag` | Zeichnen: Haut (siehe oben), sonst einfaches Licht mit Schatten und Masken für Haar, Brauen, Wimpern |
| `gl/depth.frag`, `gl/screen.vert`, `gl/sss.frag` | Schattenkarte, Bilddurchgänge, Streuung |

## Detailstufen und Mengen (Phase 12)

| Klasse | Inhalt |
|---|---|
| `LodMesh` | Netz einer Person für die fernen Stufen aus ihrer gemischten Ruhelage (Körper ohne Verdecktes, getragene Kleidung, Haar), Ecken je Teil in einem Gitter zusammengefasst (Rossignac und Borrel 1993): L2 mit 3,5 cm Zellen (Körper allein rund 3 800, mit Kleidung rund 4 600 Dreiecke), L3 mit 9 cm (rund 650 / 830). Farbe je Teil (Haut aus Melanin und Rötung, Stoff aus dem Outfit, Haar aus Eumelanin), Gelenke und Gewichte der Ecke, die dem Mittel der Zelle am nächsten liegt. |
| `Crowd` | Gruppe wie `figure.Hike`: bis zu fünf nebeneinander, Reihen im Abstand von 1,3 m, gemeinsames Tempo (das der Langsamsten), Kleidung nach Monat; jede Person mit eigener `Motion` (Weg seitlich versetzt, `Motion.offset`), `calibrate`, `step`, `centre`. |
| `gl/CrowdGl` | Netze der Menge auf der Grafikkarte, Gelenke aller Personen in einem Puffer (Dual-Quaternionen), Skinning im Scheitelshader (`crowd.vert`), einfaches Licht (`crowd.frag`), ein Aufruf je Person; Übergänge als Rasterblende (Bayer 4 × 4) im Fenster [lo, hi). |

Stufen nach der Größe der Person im Bild: **L0** ab 380 px (alles), **L1** ab 140 px (Figur ohne Streuung und ohne einzelne Haare), **L2** ab 40 px, **L3** darunter. An den Grenzen L1/L2 und L2/L3 blenden die Stufen über ±10 % ineinander: Figur [0, a), L2 [a, s), L3 [s, 1) teilen die Rasterblende ohne Lücke. `AvatarGl.dither` und `HairGl.dither` tragen das Fenster in Haut, Stoff und Haar (`ditherOut()` in `avatar.frag`, `hair.frag`, `strand.frag`). Die zwei größten Personen bekommen die vollen Figuren des Studios (F20); eine Figur erscheint erst, wenn die neue Person gemischt ist. Die Gelenke der fernen Personen rechnet der Prozessor (L2 jedes ein bis zwei Bilder, L3 jedes vierte).

## Bewegung (Phase 11)

| Klasse | Inhalt |
|---|---|
| `Motion` | Gehen aus Formeln, Drehen auf der Stelle, Sitzen und Aufstehen, Gesten (Winken, Zeigen, Bücken), Finger als Griff, als Folge von Befehlen (`walk`, `stand`, `turn`, `sit`, `standUp`, `wave`, `point`, `bend`; `interrupt()`). Weg als `Path` (Kreis, Gerade). Je Person aus der Ruhelage: Beinlänge → Doppelschritt ≈ 1,6 × Bein, Tempo ≈ 1,33 m/s · √(Bein/0,88 m), langsamer ab 60 Jahren, Stimmung (Valenz, Erregung) ändert Tempo, Armschwung und Haltung [G]. **Füße in Weltkoordinaten:** beim Aufsetzen fest, im Schwung vom Abheben zum vorhergesagten Aufsetzpunkt (Becken und Richtung von dann), mit Hub, Abrollen und Fersenheben; beim Anhalten setzt der Fuß neben den anderen. Becken mit Senkung, Seitenschwung und Drehung, Rumpf gegen, Arme gegen die Beine. `apply(...)` verändert die Weltmatrizen nach der Pose (Becken tiefer, wenn ein Fuß sonst nicht erreichbar wäre; Zwei-Knochen-IK mit Fußneigung und -richtung), danach die Richtung der Person. `measure(...)` misst das Gleiten der tragenden Füße, `missMax` ein verfehltes Ziel. Bänke: `placeBench`, `benchAt`. |
| `Pose.blend(a, b, t)` | Übergang zweier Posen (Drehung sphärisch, Verschiebung linear), etwa Stehen → Sitzen; die Füße bleiben, der Körper rückt nach hinten. |
| `Skeleton` | neu: `footTo` (Fuß an ein Ziel mit Neigung und Richtung), `aim` (Knochen zielt in eine Richtung, Anteil), `rotateAt`, `moveSubtree`, `turn` (ganze Person drehen). |
| `FigureTerms` | Adapter zu den Begriffen von `com.dan.figure` (F18): Azimut ↔ Richtung (`yaw = π − heading`), `follow` (Ort, Azimut, Bücken, Zeigen; der Gang folgt über `Motion.drive`), `forMonth`, `medieval`, `archaeologist`. |

## Kleidung (Phase 10)

| Klasse | Inhalt |
|---|---|
| `Wardrobe` | Kleiderschrank aus `res/avatar_base.kleidung.json` (Blender-Kette ab 1.7): Netz, Bit im Merkmal `_VERDECKT`, Quelle, Lizenz. `mask(outfit)` gibt die Bits der getragenen Teile. `cover(body, teil)` rechnet die Deckung für Teile ohne MPFB-Gruppe „Delete.…“ (Strahl entlang der Körpernormale trifft das Teil innerhalb von 2,5 cm; an Säumen bleibt die Haut). |
| `Fabric` | Stoffe Baumwolle, Strick, Wolle (Loden), Leinen, Denim, Leder, Ölzeug, Gummi: Rauheit, Flaum, Bindung (Strick, Köper, Leinwand, Narben), wie viel Wasser einzieht, wie viel perlt, Glanz des Wasserfilms. |
| `Outfit` | Teile mit Stoff und Farbe; Presets „Strickkapuze“ (wie die Vorlage), „Regenjacke“ (Ölzeug mit Kapuze, Gummistiefel), „Winter“, „Sommer“, „1362“ (Kutte mit Kapuze), „Wikingerzeit“ (Tunika, Wollhose, Lederschuhe), „Alltag“, „ohne“; `forMonth(monat, rolle, epoche, seed)` wie `com.dan.figure`. `hoodUp()`: Kapuze auf. |
| `ClothSway` | Nachbewegung loser Teile ohne Stoffsimulation (F11 A): gedämpfte Feder (ω 7/s, ζ 0,35) aus Wind (Staudruck ∝ v², höchstens 6 cm) und Beschleunigung des Oberkörpers, Flattern im Wind; `flex(d)` die Lose einer Ecke aus ihrem Abstand zum Körper. |

`AvatarGl`: `worn` und `setWorn(gl, bits)` laden die Dreiecke des Körpers neu, `setFabric(teil, stoff, farbe)`, `setFlex(gl, teil, lose)`; `sway`, `flutter`, `swayTime` gehen ins Skinning (`skin.comp`, Bindung 8). `avatar.frag`: Stoffe mit Bindung als Höhe im UV-Raum (Normale über den Oberflächengradienten, blendet unter einem Bildpunkt aus), Flaum im Streiflicht, Farbe aus der Textur der Kleidung umgefärbt (Mittelwert aus der kleinsten Mip-Stufe), Nässe: saugende Stoffe dunkeln, dichte bleiben außen nass mit Wasserfilm, Wolle und Strick tragen Perlen, die Lichter und Umgebung spiegeln.

Haar unter der Kapuze: `com.dan.hair` drückt Karten und einzelne Haare oberhalb der Ohren in eine Kugel um den Kopf (`HairGl.hood`); darunter fällt das Haar frei.

## Spiegelungen der Szene (Phase 9)

`AvatarGl.envN`, `envDir[16]`, `envCol[16]`, `envCos[16]`: bis zu 16 Lichter als Scheiben (Richtung vom Kopf, Farbe, Kosinus des halben Öffnungswinkels). Sie erscheinen in der Hornhaut, in den Perlen und im Wasserfilm der Haut; ohne Lichter bleibt die dunkle Umgebung mit zwei Lichtflecken wie bis Phase 8. Das Studio füllt sie aus `com.dan.portrait.BokehLights.brightest(12)`; `com.dan.avatar` kennt `portrait` nicht.

## Schnittstelle zur Szene und Regie (Phase 13)

Seit Phase 13 ist die Schnittstelle fest. Eingänge sind Werte und ein Objekt der Szene, Ausgang ist ein unveränderlicher Zustand:

| Teil | Richtung | Inhalt |
|---|---|---|
| `World` | Szene → Avatar | `ground(x, z)` (Boden), `climateAt(x, y, z)` (Regen, Wind, Temperatur, Schutz am Ort; `null` = gemeinsames Klima), `place(name)` und `places()` (benannte Orte für die Regie), `camera()` (Auge). Alles mit Vorgabe; `World.FLAT` ist eine leere Szene. |
| `AvatarGl.Light` | Szene → Avatar | Führungs-, Kanten- und Aufhelllicht wie bisher; eine Szene mit Laternen setzt es je Bild aus ihren Lichtern (siehe Steg im Studio). Dazu bis zu 16 Spiegelungen (`envDir`, `envCol`). |
| `Climate` | Szene → Avatar | Regen (mm/h), Wind, Temperatur, Feuchte, Schutz; Nässe, Kälte und Hauch folgen. |
| Ansicht | Szene → Avatar | `viewProj`, Auge; der Blick „Kamera“ und der Fokus nehmen das Auge. |
| `Snapshot` | Avatar → Szene | Name, Zeit, Ort, Boden, Richtung, Kopf, was die Person tut (`GEHEN`, `DREHEN`, `KAPUZE`, `steht`, `sitzt` …), Tempo, Gefühl (Name, Valenz, Erregung), Blick und Blickziel, Kapuze, Outfit, Nässe je Zone, Lidschläge und Schritte bisher (für Ton und Spuren). Unveränderlich, ohne Sperre weiterzugeben. |

### Bewegung mit Zielen

`Motion.goTo(x, z, tempo)` geht zu einem Ort: weicht die Richtung um mehr als 6° ab, dreht sich die Person erst auf der Stelle, dann geht sie gerade und hält mit ihrem Bremsweg am Ziel (in der Prüfung auf wenige Zentimeter, ohne Gleiten). `Motion.turnTo(x, z)` dreht zu einem Ort. `Motion.hood(auf)` greift mit beiden Händen an die Kapuze (1,8 s); in der Mitte meldet `takeHoodEvent()` einmal +1 oder −1, die Szene tauscht dann das Netz mit `Outfit.withHood(auf)`. `pending(cmd)` sagt, ob ein Befehl noch läuft.

### Regie

Ein Regiebuch ist Text, jede Zeile ein Stichwort, gleiche Form wie in Glacier und Rungholt. `Regie` liest es (Kopfzeilen `titel … | Unterzeile` und `abspann Rolle | Name`, `#` für Kommentare, „ | “ trennt den Untertitel) und führt es aus, bis eine `warte`-Zeile hält:

```
person  mara Strickkapuze vorlage=frau ort=Ufer dreh=Steg_Ende    # Szene
kamera  Totale                                                     # Szene
warte   vorspann                                                   # Szene meldet fertig
mara.gehe      Steg_Ende 0.85 | Jeden Abend kommt sie hierher.     # Person
warte   mara.gehe
mara.geste     Kapuze_ab
mara.blick     Wasser
mara.gefuehl   nachdenklich 0.7
warte   mara.blick
mara.sage      "Heute Nacht bleibt das Licht an."
warte   8 s
```

Befehle der Person (`Actor`): `blick` (Kamera, Horizont, frei, Ort, Person, x z), `gefuehl` (Name, Stärke, Dauer), `geste` (winken, zeigen, buecken, Kapuze_auf, Kapuze_ab), `pose` (stehen, sitzen), `gehe` (Ort, Person oder x z, Tempo), `dreh` (Ort, Person, Kamera oder Grad), `halt`, `outfit` (Preset), `kapuze` (auf, ab, sofort), `sage` ("Text": als Untertitel, Dauer nach der Zahl der Wörter; Lippen in der Kür). `warte` hält auf Sekunden (`warte 8 s`), auf einen Befehl (`warte mara.gehe`), auf alles einer Person (`warte mara`), auf die Szene (`warte kamera`, `warte vorspann`) oder auf alle (`warte alle`). Alle übrigen Befehle gehen an die Szene (`Regie.Stage`). Fehler stehen mit Zeilennummer in `errors`; das Regiebuch läuft weiter.

### Einbau in eine Szene

1. **Umwelt:** eine `World` geben (Boden, Orte, Kamera); die Höhe des Bodens am Ort hebt die Person (`Figure.groundY` im Studio).
2. **Person als Rolle:** die Szene erfüllt `Actor.Host` (Verhalten, Bewegung, Outfit, Kopf, Bank) und trägt `new Actor(name, host)` in `regie.cast` ein, meist beim Befehl `person`.
3. **Je Bild:** `regie.step(dt)`, dann je Person `motion.step(dt)`; ein `takeHoodEvent()` tauscht das Outfit; `actor.gazeTarget()` gibt das Blickziel der Regie in Weltkoordinaten (in Figur-Koordinaten umrechnen und an `Behavior.lookAt` geben), sonst schaut die Person beim Gehen voraus.
4. **Kino:** `regie.onText` an `kino.untertitel` geben; Schnitte (Kamera) über `kino.schnitt()`, am Ende `kino.beenden(regie.credits)`.
5. **Ausgang:** `Snapshot` je Person für Ton (Schritte, Lidschlag), Spuren und Kamera.

Das Studio zeigt das ganz: `com.dan.avatarstudio.StudioStage` (Szene der Regie), `JettySet` (Steg im Regen mit Laternen als `World`), `CameraRig` (Einstellungen, folgen) und das Regiebuch `regie/am_steg.txt`.

## Grenzen (Stand Phase 13, ergänzt in Phase 19 unten)

- Die Ausrichtung der Gelenke bleibt bei Morphs die der Mitte; nur die Köpfe wandern.
- Größe, Proportionen und Brust sind über Muskeln × Gewicht genähert (bis 11,6 mm gegen MPFB); exakt wären rund 220 weitere Ziele und gut 40 MB mehr.
- Detailstufen: L2 und L3 entstehen durch Zusammenfassen im Gitter, nicht durch Kantenkollaps; nah vor der Kamera (mehr als zwei Personen über 140 px) wirken L2-Personen grob. Keine Gesichter und Augen unter L1, Kleidung ohne Stoffe und Nässe, Haar als Fläche. Die Gelenke der Menge rechnet der Prozessor.
- Bewegung: ein Gang aus Formeln (kein Laufen, keine Treppen, kein unebener Boden), Drehen nur auf der Stelle, Sitzen nur auf eine Bank hinter der Person; Gesten mit dem rechten Arm. Füße durchdringen sich nicht, Personen weichen einander nicht aus. Kleidung folgt dem Skelett und der Feder, nicht den Beinen im Schritt (Kutte und Rock durchdringen sich beim Gehen).
- Die Blickformen drehen das Auge nur rund ±19° seitlich und +12°/−14° senkrecht; weiter dreht der Kopf. Keine Mikrosakkaden (unter 0,5°, im Bild nicht zu sehen). Die Gefühle sind von Hand gemischte ARKit-Formen, keine gemessenen FACS-Profile; „ärgerlich“ und „ängstlich“ wirken an der MPFB-Mimik schwach.
- Kleidung: keine Stoffsimulation; lose Teile schwingen als Ganzes mit einer Feder. Teile durchdringen sich, wo MPFB-Netze übereinanderliegen (Kapuze über Pullover), und in starken Posen. Die Stoffmuster liegen im UV-Raum der MPFB-Teile; ihr Maßstab ist je Teil nur genähert.
- Nässe: Die Kapuze schützt als Faktor (aus dem Outfit). Tropfen nur auf Gesicht, Ohren und Hals (nicht auf Händen); die Richtung bergab gilt für die Ruhehaltung des Kopfes. Sehr kleine Perlen sind im Porträt kleiner als ein paar Bildpunkte und erst in der Nahaufnahme zu sehen. Nasse Strähnen kleben nicht als eigene Form, das Haar wird dunkler und glatter, die Fliegenhaare hängen.
- Das Verhalten dreht Kopf und Hals um Weltachsen; in stark gedrehten Posen (sitzen, lehnen) ist die Aufteilung nur genähert.
- Eine Schattenkarte nur für das Führungslicht; Kantenlicht ohne Schatten und ohne Durchleuchtung. Keine Kontaktschatten im Bildraum.
- Die Durchleuchtung am Ohr ist fleckig, wo die Schattenkarte grob ist; Schatten mit Luftspalt werden über die Eckendicke gedämpft, nicht ganz vermieden.
- Fältchen sind ein Netz ohne Richtung; Stirnfalten und Krähenfüße als gerichtete Linien fehlen noch.
- Die Grundhäute sind hell; dunkle Töne entstehen über den Ton, nicht über eigene dunkle Häute.
- Die Streuung läuft in voller Auflösung (auf „Mittel“ 11 Proben); halbe Auflösung war nicht nötig.
- Das Auge: keine Kaustik auf der Iris; die Szene spiegelt sich nur als Lichter (keine Umgebungskarte); die Blickformen drehen den Augapfel nicht ganz starr (Mitte wandert bis 1,4 mm).
- Die Abdunklung unter den Lidern ist eine Annahme über die Lidspalte, kein Schatten der Lidgeometrie selbst (die Schattenkarte ist dafür zu grob).
- Regie: Befehle laufen der Reihe nach je Person; eine Person kann nicht zugleich gehen und eine Geste machen (die Geste wartet). `gehe` geht gerade (erst drehen), ohne Wegsuche und ohne Ausweichen. `sage` zeigt Untertitel; seit Phase 16 bewegt die Szene dazu den Mund (`Actor.Host.say` gibt die Dauer zurück). Die Kapuzen-Geste tauscht das Netz in der Mitte der Bewegung, ohne Übergang im Stoff.

## Personen in der Datenbank (Phase 14)

`Person` kennt jetzt die Form, in der die Datenbank AVATAR sie speichert (Spalte `REGLER` von `AV_PERSON_T`):

- `toJson()` / `Person.fromJson(json, seed, name)` – alle Regler als JSON, verlustfrei hin und zurück (`sameAs`).
- `NUMBERS`, `number(n)`, `number(n, v)` – die Zahlenregler nach Namen, in fester Reihenfolge.
- `aelter(jahre)` – dieselbe Person älter (oder jünger), Grenzen 1 und 90 Jahre; grauer und schwerer, Gesicht und Frisur bleiben.
- `geschwister(seed)` – Gesicht halb und halb mit der gewürfelten Person `seed`, Haut und Augen zu 70 % von hier.

`AV_API.WUERFELN`, `AELTER` und `GESCHWISTER` in PL/SQL rechnen genau so (SplitMix64, gleiche Reihenfolge,
gleiches Runden); `ant db-pruefung` vergleicht 1009 seeds. Siehe `db/README.md`.

## Rückfluss nach Lighthouse (Phase 15)

Die erste Szene, in die der Avatar zurückfließt (F22 A): Lighthouse. Dort liegen `gltf`, `avatar`, `hair` und `portrait`
als Kopie (`ant lighthouse-kopie` erneuert sie, `ant check` vergleicht Datei für Datei). Die App verbindet sie in
`com.dan.lighthouse.person` nach den fünf Schritten oben:

1. **Umwelt:** `Galerie` ist die `World` (Boden 23 m, Orte rund um die Laterne, Windschatten, der hohe Erker als Hindernis).
2. **Rolle:** `Figur` erfüllt `Actor.Host`; die Person kommt aus der Datenbank (`AV_API.WUERFELN(19)`, ohne Datenbank
   derselbe Würfel in Java).
3. **Je Bild:** Regie, Bewegung, Verhalten, Kleidung, Haare, Nässe; gezeichnet in das HDR-Ziel von Lighthouse nach dem
   Leben, mit dem Licht der Szene (Feuer im Nahfeld, Glas der Laterne, Himmel, Mond, Blitze), vorbelichtet.
4. **Kino:** das Kino von Lighthouse (`com.dan.kino`), Schnitte über dessen Warteschlange.
5. **Ausgang:** `Snapshot` für Ton und Spuren (noch nicht an den Klang von Lighthouse angeschlossen).

Neu in der Regie der Galerie: `rund <Person> <Ort>` geht im Kreis um die Laterne (`Motion.circle`), `warte rund`.
Was die Szene dafür über den Avatar wissen muss, steht in `com.dan.lighthouse.person/README.md`.

## Sprechen und Musik im Verhalten (Phase 16, 17)

`Behavior` bekommt Mund und Musik als Zahlen von der Szene; es kennt `com.dan.voice` und `com.dan.beat` nicht.

| Eingang | Wirkung |
|---|---|
| `speech` (Formname → Gewicht, z. B. aus `voice.Speech.forms(t)`) | Viseme in die Gesichtsformen; die Mundformen des Gefühls treten zur Hälfte zurück |
| `talking` | Lidschläge 1,5-mal so oft |
| `inhale()`, `blinkSoon(s)` | Atemzug vor einem Satz, Lidschlag am Phrasenende |
| `musicBeat` (Schläge stetig, NaN = keine Musik), `musicDown` | Nicken kurz nach jedem Schlag, Wiegen von Kopf, Brust und Schultern über zwei Schläge, Becken im Takt |
| `groove` (0 … 1) | wie stark die Person mitgeht |
| `musicCalm` (0 … 1) | in ruhigen Stellen schließen sich die Augen (geglättet, `calmLid()`) |

## Tanz und Trommel (Phase 18, 19)

| Teil | Aufgabe |
|---|---|
| `Mocap` | ASF/AMC der CMU-Datenbank lesen: Skelett, je Bild die Knochenenden in Metern (y oben), nach +z gedreht |
| `Dance` | Takt jeder Aufnahme (Autokorrelation der Fußgeschwindigkeit), je Takt des Lieds ein Stück, gedehnt (½, 1 oder 2 Schritte je Schlag) und an der Taktgrenze gemischt; Übertragung über Richtungen (Becken nach Hüften und Kreuz, dann jeder Knochen wie in der Aufnahme, Brustkorb mit der Drehung der Schultern), Längen der Person, Füße auf dem Boden |
| `Drum` | Rahmentrommel vor der Brust: linke Hand am Rand (Finger hinter dem Fell), rechte Hand flach auf dem Fell zu jedem Schlag (Eins höher ausgeholt); Zwei-Knochen-IK der Arme; die Finger bleiben über dem Fell (`fingerGap`) |

```java
List<Mocap> takes = List.of(Mocap.read(asf, new File("93_03.amc"), 120), ...);
Dance dance = new Dance(takes);
dance.beatS = 60 / rhythm.bpm;
double[][] world = dance.world(skeleton, rest, rhythm.beatAt(t) - rhythm.downbeat, additive);
drum.apply(skeleton, world, beat, 0, beatS);       // danach: center, normal, right, hit für die Zeichnung
```

Die Szene zeichnet die Trommel als Zubehör (wie die Bank). Daten: `daten/tanz/cmu` (CMU Graphics Lab Motion Capture
Database, frei für alle Zwecke außer dem Weiterverkauf der Daten).

## Grenzen (Stand Phase 19)

- Haare ohne Kollision mit Schultern und Kragen (eine Feder je Kopf statt Strähnen-Physik).
- `gehe` geht gerade auf ein Ziel (erst drehen), ohne Wegsuche und Ausweichen; Gesten im Stehen, nicht im Gehen.
- Stoff ohne Simulation (Nachbewegung über Federn je Ecke); die Kapuze des Ölzeugs schlägt an der Stirn eine Falte.
- Tanz aus kurzen Aufnahmen (3–5 s): die Stücke wiederholen sich nach vier Takten; Takt immer 4/4.
- An der Trommel liegen die Finger als Ganzes auf, nicht einzeln gebogen.
