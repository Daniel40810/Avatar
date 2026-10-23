# Prompt für das Avatar-Werkbuch — Entwurf v0.1 (22.10.2026)

> Zweck: Aus diesem Prompt entsteht das **Avatar Werkbuch** (Artefakt, gleicher Aufbau wie bei Rungholt / Glacier).
> Stellen mit **[OFFEN]** klären wir vor dem Anlegen des Werkbuchs.

---

## 1. Auftrag

Lege ein Werkbuch für das NetBeans-Projekt **Avatar** an
(`C:\Users\Dan40810\Documents\NetBeansProjects\Avatar`). Ziel ist ein **universell einsetzbarer,
fotorealistisch anmutender Mensch als Avatar** in OpenGL (JOGL), männlich wie weiblich, der als
eigenständiges Package (`com.dan.avatar`) in allen bisherigen Szenen-Projekten nutzbar ist –
vom bildfüllenden Porträt bis zur kleinen Figur in der Ferne.

Das Werkbuch enthält zunächst **nur Planung**: Ausgangslage, Zielbild, Eigenschaften und Fähigkeiten,
Grundsatzfragen mit Optionen, Phasenplan. Kein Code, bevor die Grundsatzfragen entschieden sind.

## 2. Ausgangslage

- Java 21 (`javac.source/target=21`), Ant, NetBeans; JOGL mit OpenGL 4.3 wie die übrigen Szenen.
- `lib/Renderer.jar` bringt die Szenen-Packages mit: u. a. `figure` (einfacher Mensch, 15 Teile, Gang,
  Kleidung nach Monat/Rolle, Gruppe `Hike`), `kino` (Kinomodus, Regiebücher), `rain`, `weather`,
  `soundscape`, `sky`, `ocean`, `cloud`, `fog`, `season` u. v. m. – diese werden genutzt, nicht neu gebaut.
- `lib/FStyle.jar` für Demo-Fenster (`FFrame`) und Bedienfelder.
- Rechner: Ryzen 7 7735HS, integrierte Radeon (2 GB geteilter Grafikspeicher), 64 GB RAM.
  Alles muss auf dieser iGPU flüssig laufen (Ziel ≥ 30 fps im Porträt, Qualitätsstufen LOW…ULTRA).
- Vorlage `templates/E_Music.jpg`: Frauenporträt im Regen – nasse Haut mit Tropfen und Sommersprossen,
  nasse Haarsträhnen, türkis-leuchtende Augen, Strickkapuze mit Wasserperlen, Bokeh-Lichter in
  Bernstein und Türkis, filmische Farbgebung. Dieser Look ist der **Maßstab für die Nahaufnahme**.
- Inspiration: YouTube-Kanäle „The Emberheart Project“, „DjVonk“, „MarFluxMusic“
  (stimmungsvolle Musik-Visuals mit Figuren). **[OFFEN: was genau davon übernehmen?]**

## 3. Zielbild

Ein Avatar, der **lebt**, auch wenn er nichts tut: atmet, blinzelt, den Blick wandern lässt,
auf Licht, Wetter und Musik reagiert – und der sich in jede vorhandene Szene setzen lässt,
dort deren Licht, Regen, Wind und Ton übernimmt.

## 4. Eigenschaften (was der Avatar *ist*)

### 4.1 Körper und Gestalt
- Geschlecht als stufenloser Regler (0 = weiblich … 1 = männlich), dazu Alter, Größe, Statur, Gewicht.
- Gesichtsform über Morph-Parameter (Kiefer, Wangen, Nase, Augenabstand, Lippen, Brauen …),
  Hautton als Parameter. Gesichter sind **frei erfunden**, keine Nachbildung realer Personen.
- Zufallsgenerator mit `seed`: gleicher Startwert → gleiche Person (reproduzierbar wie alle Packages).
- Ein Skelett für alle Varianten (Körper, Finger, Gesicht), damit Animationen für jede Person passen.

### 4.2 Haut
- Subsurface-Scattering (Licht schimmert durch Ohren, Nasenflügel, Lippen).
- Poren, feine Fältchen, Sommersprossen, Leberflecken, Rötung, Bartschatten – als Parameter.
- **Nässe** als Zustand 0…1: dunklere, glänzendere Haut, Tropfen und Rinnsale (Vorlage!),
  gespeist aus `com.dan.rain`.
- Kälte (Röte an Nase/Wangen, Atemhauch), Wärme (leichter Glanz).

### 4.3 Augen
- Hornhaut mit Brechung, Iris mit Tiefe und Farbe als Parameter, Lichtreflex (Catchlight),
  feuchter Lidrand, Wimpern; Pupille reagiert auf Helligkeit.

### 4.4 Haare
- Kopfhaar, Brauen, Wimpern, Bart (männlich) – Frisuren als Bibliothek (lang, kurz, Zopf, Locken …).
- Bewegt sich im Wind (`weather`), klebt bei Nässe in Strähnen (Vorlage!).
- **[OFFEN: Haarsträhnen (Strands) oder Haarkarten – Qualität gegen iGPU-Leistung]**

### 4.5 Kleidung und Zubehör
- Kleidungsschichten (Hemd, Pullover/Kapuze, Jacke, Hose, Schuhe), Stoffarten mit eigenem Material
  (Strick, Leder, Ölzeug, Leinen, Wolle) – auch historische Kleidung wie `Outfit.medieval`.
- Nässe, Wasserperlen auf Wolle (Vorlage!), Wind bewegt Stoff.
- Zubehör: Mütze, Kapuze auf/ab, Brille, Schmuck, Kopfhörer, Instrument.

## 5. Fähigkeiten (was der Avatar *kann*)

### 5.1 Leben im Ruhezustand
Atmen, Blinzeln (mit natürlicher Verteilung), Mikrobewegungen von Kopf und Schultern,
Blicksprünge (Sakkaden), Gewichtsverlagerung im Stand.

### 5.2 Blick
Blick zur Kamera, zu einem Punkt in der Szene oder zu einem anderen Avatar; Kopf und Augen
teilen sich die Drehung natürlich auf.

### 5.3 Mimik und Gefühl
- Gesichtsausdrücke nach FACS (Action Units) bzw. einem Blendshape-Satz.
- Gefühle als weiche Mischung (ruhig, nachdenklich, freudig, traurig, überrascht, entschlossen …)
  mit Übergängen und Trägheit wie bei der Wetterregie.

### 5.4 Sprechen und Singen
- Lippenbewegung (Viseme) aus Text/Phonemen oder aus einer Tonspur.
- Singen zu Musik: Mund, Atmung, Kopfbewegung im Takt.
- **[OFFEN: Woher kommt der Ton – eigene WAV-/MP3-Dateien, erzeugter Ton (`soundscape`), Sprachausgabe?]**

### 5.5 Musik
- Reaktion auf Musik: Takterkennung (Beat), Lautstärke, Stimmung → Wippen, Augen schließen, Lächeln.
- Musik-Visual-Modus wie in der Vorlage: Porträt, Bokeh, Regen, Licht im Takt, Kinomodus (`kino`).
- **[OFFEN: Soll der Avatar tanzen / ein Instrument spielen oder „nur“ fühlen und singen?]**

### 5.6 Haltung, Gestik, Bewegung
Stehen, Sitzen, Gehen (Nachfolger von `figure`), Gesten (zeigen, winken, Hand ans Gesicht,
Kapuze aufsetzen), Übergänge zwischen Posen; Füße bleiben auf dem Boden (Fuß-IK).

### 5.7 Reaktion auf die Umwelt
Übernimmt aus der Szene: Licht (Sonne, Mond, Himmel, Feuer), Regen → Nässe, Wind → Haar/Stoff,
Temperatur → Atemhauch/Röte, Jahreszeit → Kleidung (`season`).

### 5.8 Kamera und Bild
Porträt-Kameras (Nah, Halbnah, Halbtotale), Schärfentiefe mit Bokeh, filmische Farbgebung,
Filmkorn – passend zum Kinomodus.

## 6. Universell einsetzbar

- Package-Aufbau wie alle anderen: Kern nur Java 21, `gl/` mit JOGL, `demo/` mit FStyle,
  `README.md`, Prüfung ohne Fenster (`AvatarCheck`, endet mit „Alles in Ordnung.“),
  zum Herauskopieren reicht `src/com/dan/avatar/`.
- Detailstufen (LOD): Porträt (volle Qualität) → Halbtotale → Figur in der Szene → ferne Figur;
  langfristig Ersatz oder Ausbau von `com.dan.figure` (auch Gruppen wie `Hike`).
- Schnittstellen wie in den anderen Packages: `View`, `Light` (vorbelichtet), Snapshot-Zustand,
  Befehle für Regiebücher (z. B. „schaut zur Kamera“, „lächelt“, „singt Zeile …“).
- Vorschlag im Werkbuch: **in welche bisherigen Projekte** (Lighthouse, Rungholt, Haithabu, Glacier …)
  der Avatar zuerst zurückfließt und in welcher Rolle (Erzähler, Wattführer, Leuchtturmwärter …).

## 7. Leitplanken

- Physikalisch begründete Werte, wo es geht; Geschätztes mit **[G]** markieren (wie bisher).
- Leistungsbudget je Qualitätsstufe (Dreiecke, Texturgrößen, Grafikspeicher ≤ ~1 GB für den Avatar).
- Reproduzierbar über `seed`; Prozessor und Grafikkarte rechnen dieselben Formeln, wo sinnvoll.
- Statusbilder je Phase in `docs/status`, Abnahme je Phase durch Daniel („PhaseN, los!“).
- `templates/` kommt ins `.gitignore`.

## 8. Was das Werkbuch enthalten soll

1. Ausgangslage und Zielbild (mit Bezug auf die Vorlage).
2. Katalog Eigenschaften und Fähigkeiten (Abschnitte 4–5, verfeinert).
3. **Grundsatzfragen F1…Fn** mit Optionen A/B/C, A jeweils empfohlen, mit Begründung –
   mindestens zu: Herkunft der Geometrie, Haar-Technik, Hautshader, Skelett und Gesichtsrig,
   Tonquelle für Lippen/Musik, Bedeutung des Musik-Modus, Rolle von `com.dan.figure`,
   Datenbank ja/nein, erste Zielszene.
4. Phasenplan (Phase 1 … n) mit Ergebnis, Prüfung und Statusbild je Phase.
5. Datenbankteil als eigener, späterer Fragenblock.
6. Rückfluss in bestehende Projekte.

## 9. Offene Punkte vor dem Werkbuch

- **[OFFEN] Herkunft der Geometrie** – das ist die größte Weichenstellung:
  - A) Grundkörper aus Blender (MPFB2/MakeHuman, CC0) mit Morphs und Skelett, Export als glTF,
    eigener Lader in Java; Haut, Augen, Haare, Animation, Licht = eigener Code.
  - B) Alles prozedural in Java wie `figure` – ganz eigen, erreicht aber den Vorlagen-Look kaum.
  - C) Fremde fertige Figuren (z. B. Character-Creator-Assets) – Lizenzfragen, wenig „eigen“.
- **[OFFEN] Schwerpunkt:** zuerst das Porträt (Vorlage) oder zuerst die ganze Figur in einer Szene?
- **[OFFEN] Musik:** fühlen und singen, tanzen, Instrument?
- **[OFFEN] Tonquelle** für Lippen und Musik.
- **[OFFEN] Datenbank:** eigenes Schema (z. B. AVATAR) für Personen, Kleidung, Ausdrücke, Liedtexte?
