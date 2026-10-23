# Prompt für das Avatar-Werkbuch — Fassung 1.0 (22.10.2026)

> Zweck: Aus diesem Prompt entsteht das **Avatar Werkbuch** (Artefakt, gleicher Aufbau wie bei Rungholt / Glacier).
> Grundlage: Entwurf v0.1 (`Avatar_Prompt_v0.1.md`) und Daniels Antworten in `Anweisung.txt`.

---

## 1. Auftrag

Lege ein Werkbuch für das NetBeans-Projekt **Avatar** an
(`C:\Users\Dan40810\Documents\NetBeansProjects\Avatar`). Ziel ist ein **universell einsetzbarer,
fotorealistisch anmutender Mensch als Avatar** in OpenGL (JOGL), männlich wie weiblich, der als
eigenständiges Package (`com.dan.avatar`) in allen bisherigen Szenen-Projekten nutzbar ist –
vom bildfüllenden Porträt bis zur kleinen Figur in der Ferne.

Das Werkbuch enthält zunächst **nur Planung**: Ausgangslage, Zielbild, Eigenschaften und Fähigkeiten,
Grundsatzfragen mit Optionen, Phasenplan, Datenbankkonzept. Kein Code und keine Skripte,
bevor die Grundsatzfragen entschieden sind.

## 2. Ausgangslage

- Java 21 (`javac.source/target=21`), Ant, NetBeans; JOGL mit OpenGL 4.3 wie die übrigen Szenen.
- `lib/Renderer.jar` bringt die Szenen-Packages mit: u. a. `figure` (einfacher Mensch, 15 Teile, Gang,
  Kleidung nach Monat/Rolle, Gruppe `Hike`), `kino` (Kinomodus, Regiebücher), `rain`, `weather`,
  `soundscape`, `sky`, `ocean`, `cloud`, `fog`, `season` u. v. m. – diese werden genutzt, nicht neu gebaut.
- `lib/FStyle.jar` für Demo-Fenster (`FFrame`) und Bedienfelder.
- **Blender ist bereit** und über die Blender-Verbindung (MCP) von Claude aus steuerbar.
- Rechner: Ryzen 7 7735HS, integrierte Radeon (2 GB geteilter Grafikspeicher), 64 GB RAM.
  Alles muss auf dieser iGPU flüssig laufen (Ziel ≥ 30 fps im Porträt, Qualitätsstufen LOW…ULTRA).
- Datenbank: Oracle 21c, Service `PDBORCL`, **neues leeres Schema `AVATAR`** (angelegt mit
  `manage_user`, Rolle `DB_CONNECT`; Zugangsdaten siehe `docs/Anweisung.txt`).
- Vorlage `templates/E_Music.jpg`: Frauenporträt im Regen – nasse Haut mit Tropfen und Sommersprossen,
  nasse Haarsträhnen, türkis-leuchtende Augen, Strickkapuze mit Wasserperlen, Bokeh-Lichter in
  Bernstein und Türkis, filmische Farbgebung. Dieser Look ist der **Maßstab für die Nahaufnahme**.
- Inspiration: YouTube-Kanäle „The Emberheart Project“, „DjVonk“, „MarFluxMusic“
  (stimmungsvolle Musik-Visuals mit Figuren).

## 3. Entschiedene Grundsätze

| Nr. | Frage | Entscheidung |
|---|---|---|
| E1 | Herkunft der Geometrie | **Blender**: Grundkörper mit MPFB2 (MakeHuman, CC0) samt Morphs, Skelett und Gesichts-Blendshapes, Export als glTF; eigener glTF-Lader in Java. Haut, Augen, Haare, Animation, Licht und Shader sind eigener Code. |
| E2 | Schwerpunkt | **Porträt zuerst, aber mit ganzem Körper von Anfang an.** Der Körper kommt vollständig aus Blender und wird ab Phase 1 geladen; die Qualitätsarbeit beginnt am Gesicht (Haut, Augen, Haare, Mimik), weil sie am schwersten ist, den Look der Vorlage bestimmt und alle weiteren Detailstufen von dort abgeleitet werden. Danach ganze Figur, Bewegung, Detailstufen, Einbau in Szenen. |
| E3 | Musik (singen, tanzen, Instrument) | **Kür.** Schön, wenn er es kann; eigene späte Phasen. Das Rig wird so angelegt, dass es später ohne Umbau möglich ist. |
| E4 | Tonquelle für Lippen und Musik | **Kür, nicht primär.** Schnittstelle früh vorsehen (Viseme-Kanäle im Gesichtsrig), Umsetzung spät. |
| E5 | Datenbank | **Eigenes Schema `AVATAR` nach dem Vorbild von MDSYS**, projektübergreifend nutzbar (siehe Abschnitt 7). |

## 4. Zielbild

Ein Avatar, der **lebt**, auch wenn er nichts tut: atmet, blinzelt, den Blick wandern lässt,
auf Licht, Wetter und (später) Musik reagiert – und der sich in jede vorhandene Szene setzen lässt,
dort deren Licht, Regen, Wind und Ton übernimmt.

## 5. Eigenschaften (was der Avatar *ist*)

### 5.1 Körper und Gestalt
- Geschlecht als stufenloser Regler (0 = weiblich … 1 = männlich), dazu Alter, Größe, Statur, Gewicht –
  über die Morphs aus MPFB2.
- Gesichtsform über Morph-Parameter (Kiefer, Wangen, Nase, Augenabstand, Lippen, Brauen …),
  Hautton als Parameter. Gesichter sind **frei erfunden**, keine Nachbildung realer Personen.
- Zufallsgenerator mit `seed`: gleicher Startwert → gleiche Person (reproduzierbar wie alle Packages).
- Ein Skelett für alle Varianten (Körper, Finger, Gesicht), damit Animationen für jede Person passen.

### 5.2 Haut
- Subsurface-Scattering (Licht schimmert durch Ohren, Nasenflügel, Lippen).
- Poren, feine Fältchen, Sommersprossen, Leberflecken, Rötung, Bartschatten – als Parameter.
- **Nässe** als Zustand 0…1: dunklere, glänzendere Haut, Tropfen und Rinnsale (Vorlage!),
  gespeist aus `com.dan.rain`.
- Kälte (Röte an Nase/Wangen, Atemhauch), Wärme (leichter Glanz).

### 5.3 Augen
- Hornhaut mit Brechung, Iris mit Tiefe und Farbe als Parameter, Lichtreflex (Catchlight),
  feuchter Lidrand, Wimpern; Pupille reagiert auf Helligkeit.

### 5.4 Haare
- Kopfhaar, Brauen, Wimpern, Bart (männlich) – Frisuren als Bibliothek (lang, kurz, Zopf, Locken …).
- Bewegt sich im Wind (`weather`), klebt bei Nässe in Strähnen (Vorlage!).
- Technik (Strähnen oder Haarkarten) ist eine Grundsatzfrage im Werkbuch – Qualität gegen iGPU-Leistung.

### 5.5 Kleidung und Zubehör
- Kleidungsschichten (Hemd, Pullover/Kapuze, Jacke, Hose, Schuhe), Stoffarten mit eigenem Material
  (Strick, Leder, Ölzeug, Leinen, Wolle) – auch historische Kleidung wie `Outfit.medieval`.
- Nässe, Wasserperlen auf Wolle (Vorlage!), Wind bewegt Stoff.
- Zubehör: Mütze, Kapuze auf/ab, Brille, Schmuck, Kopfhörer, (Kür) Instrument.

## 6. Fähigkeiten (was der Avatar *kann*)

### Pflicht
1. **Leben im Ruhezustand:** Atmen, Blinzeln (natürliche Verteilung), Mikrobewegungen von Kopf und
   Schultern, Blicksprünge (Sakkaden), Gewichtsverlagerung im Stand.
2. **Blick:** zur Kamera, zu einem Punkt in der Szene oder zu einem anderen Avatar; Kopf und Augen
   teilen sich die Drehung natürlich auf.
3. **Mimik und Gefühl:** Ausdrücke nach FACS bzw. Blendshape-Satz; Gefühle als weiche Mischung
   (ruhig, nachdenklich, freudig, traurig, überrascht, entschlossen …) mit Übergängen und Trägheit
   wie bei der Wetterregie.
4. **Haltung, Gestik, Bewegung:** Stehen, Sitzen, Gehen (Nachfolger von `figure`), Gesten (zeigen,
   winken, Hand ans Gesicht, Kapuze aufsetzen), Übergänge zwischen Posen; Füße bleiben am Boden (Fuß-IK).
5. **Reaktion auf die Umwelt:** Licht (Sonne, Mond, Himmel, Feuer), Regen → Nässe, Wind → Haar/Stoff,
   Temperatur → Atemhauch/Röte, Jahreszeit → Kleidung (`season`).
6. **Kamera und Bild:** Porträt-Kameras (Nah, Halbnah, Halbtotale), Schärfentiefe mit Bokeh,
   filmische Farbgebung, Filmkorn – passend zum Kinomodus (`kino`).

### Kür (späte Phasen, Schnittstellen früh vorsehen)
7. **Sprechen:** Lippenbewegung (Viseme) aus Text/Phonemen oder aus einer Tonspur.
8. **Singen:** Mund, Atmung, Kopfbewegung im Takt.
9. **Musik fühlen:** Takterkennung, Lautstärke, Stimmung → Wippen, Augen schließen, Lächeln;
   Musik-Visual-Modus wie in der Vorlage (Porträt, Bokeh, Regen, Licht im Takt).
10. **Tanzen** und **Instrument spielen.**
11. **Tonquelle:** eigene Audiodateien, erzeugter Ton (`soundscape`) oder Sprachausgabe –
    Auswahl als Grundsatzfrage in der Kür.

## 7. Datenbank: Schema `AVATAR` nach MDSYS-Vorbild

Nur Konzept im Werkbuch, Skripte erst nach Abstimmung.

- **Rolle wie MDSYS:** `AVATAR` ist ein Dienst-Schema, das allgemein nutzbare Typen, Packages,
  Kataloge und Metadaten-Sichten bereitstellt; die Projekt-Schemata (DEMO, RUNGHOLT, GLACIER,
  HAITHABU …) nutzen es, ohne eigene Kopien zu halten.
- **Bausteine (Vorschlag, im Werkbuch zu verfeinern):**
  - Objekttypen für Personen und Zustände (z. B. Person mit Morph-Parametern und `seed`,
    Kleidung, Ausdruck, Pose) – ähnlich wie `SDO_GEOMETRY` in fremden Tabellen verwendbar.
  - Gemeinsame Kataloge: Frisuren, Kleidungsstücke und Stoffe, Ausdrücke/Gefühle, Posen und Gesten,
    Rollen (Erzähler, Wattführer, Leuchtturmwärter …), (Kür) Liedtexte und Viseme.
  - PL/SQL-API (z. B. `AVATAR_API`) zum Anlegen, Würfeln (`seed`), Ableiten und Prüfen.
  - Metadaten-Sichten im Stil `USER_/ALL_` (welches Projekt nutzt welche Avatare).
  - Zugriff über Grants an eine Rolle und öffentliche Synonyme; Java-Zugriff wie bisher (FDAL).
- **Grundsatzfragen dazu im Werkbuch**, u. a.: eigenes Daten-Schema nach MDDATA-Vorbild ja/nein,
  wo projekteigene Avatare liegen (im Projekt-Schema mit AVATAR-Typen oder zentral in AVATAR),
  Rolle und Synonyme, Versionierung der Kataloge, Bezug zur Vorlagen-PDB.
- Konventionen: Autoincrement über Sequence + Trigger (nie `GENERATED ALWAYS AS IDENTITY`).

## 8. Universell einsetzbar

- Package-Aufbau wie alle anderen: Kern nur Java 21, `gl/` mit JOGL, `demo/` mit FStyle,
  `README.md`, Prüfung ohne Fenster (`AvatarCheck`, endet mit „Alles in Ordnung.“),
  zum Herauskopieren reicht `src/com/dan/avatar/`. Die glTF-Dateien liegen als Ressourcen im Package.
- Detailstufen (LOD): Porträt (volle Qualität) → Halbtotale → Figur in der Szene → ferne Figur;
  langfristig Ersatz oder Ausbau von `com.dan.figure` (auch Gruppen wie `Hike`).
- Schnittstellen wie in den anderen Packages: `View`, `Light` (vorbelichtet), Snapshot-Zustand,
  Befehle für Regiebücher (z. B. „schaut zur Kamera“, „lächelt“, „setzt die Kapuze auf“).
- Vorschlag im Werkbuch: **in welche bisherigen Projekte** der Avatar zuerst zurückfließt und in
  welcher Rolle.

## 9. Leitplanken

- Physikalisch begründete Werte, wo es geht; Geschätztes mit **[G]** markieren (wie bisher).
- Leistungsbudget je Qualitätsstufe (Dreiecke, Texturgrößen, Grafikspeicher ≤ ~1 GB für den Avatar).
- Reproduzierbar über `seed`; Prozessor und Grafikkarte rechnen dieselben Formeln, wo sinnvoll.
- Blender-Schritte werden als Skript festgehalten, damit jede Figur wiederholbar entsteht.
- Statusbilder je Phase in `docs/status`, Abnahme je Phase durch Daniel („PhaseN, los!“).
- `templates/` kommt ins `.gitignore`.

## 10. Was das Werkbuch enthalten soll

1. Ausgangslage und Zielbild (mit Bezug auf die Vorlage), die entschiedenen Grundsätze E1–E5.
2. Katalog Eigenschaften und Fähigkeiten (Abschnitte 5–6, verfeinert), Pflicht und Kür getrennt.
3. **Grundsatzfragen F1…Fn** mit Optionen A/B/C, A jeweils empfohlen, mit Begründung –
   mindestens zu: Blender-Ablauf (MPFB2-Einstellungen, Topologie, Blendshape-Satz, Export),
   glTF-Lader, Skinning-Verfahren, Hautshader, Augen, Haar-Technik, Kleidung (fest oder simuliert),
   Animationsquelle (eigen/prozedural oder Clips), Detailstufen, Rolle von `com.dan.figure`,
   erste Zielszene für den Rückfluss.
4. **Datenbankteil** als eigener Fragenblock (Abschnitt 7).
5. **Phasenplan** (Phase 1 … n) mit Ergebnis, Prüfung und Statusbild je Phase; Reihenfolge nach E2,
   Kür-Phasen am Ende.
6. Rückfluss in bestehende Projekte.

---

**22.10.2026:** Werkbuch angelegt: Artefakt „Avatar Werkbuch“ (https://claude.ai/artifact/HejoaJyDcUts4ddcoiqnzZ), Grundsatzfragen F1–F28 und Datenbankfragen F29–F40 offen, 19 Phasen.
