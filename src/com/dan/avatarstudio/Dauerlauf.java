package com.dan.avatarstudio;

import com.dan.avatar.Body;
import com.dan.avatar.Climate;
import com.dan.avatar.Person;
import com.dan.avatar.Regie;
import com.dan.gltf.Gltf;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dauerlauf (Abnahme Phase 19): das Studio ohne Fenster eine Stunde Studiozeit lang (108 000 Bilder zu 1/30 s), mit
 * allem, was es kann: alle zwei Minuten eine neu gewürfelte Person in anderer Kleidung und Frisur, alle drei Minuten
 * anderes Wetter, alle 45 s ein gesprochener Satz, in den Minuten 10, 30 und 50 Musik mit Tanz und Trommel, in den
 * Minuten 20 und 40 das Regiebuch „Am Steg“ im Kino, dazu die Qualitätsstufen reihum und das Porträt an und aus.
 *
 * <p>Gemessen je Minute: Zeit je Bild (Prozessor und Grafikkarte), Speicher nach dem Aufräumen, Fehler und Ausnahmen,
 * ungültige Zahlen in den Gelenken. Bestanden, wenn keine Ausnahme, keine ungültige Zahl, kein Fehler der Regie und der
 * Speicher nach Minute 10 um höchstens ein Viertel (plus 64 MB) wächst. Das Protokoll endet mit „Dauerlauf bestanden“.
 */
final class Dauerlauf {

    private Dauerlauf() {}

    static final int FPS = 30;

    static void run(File out, double minutes) throws Exception {
        long wall0 = System.nanoTime();
        List<String> log = new ArrayList<>();
        java.util.function.Consumer<String> say = s -> { log.add(s); System.out.println(s); };
        say.accept(String.format(Locale.GERMANY, "Dauerlauf Avatar Studio · %.0f Minuten Studiozeit · %s", minutes, java.time.LocalDateTime.now().withNano(0)));
        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        scene.fixedDt = 1.0 / FPS;
        Gltf model = loadModelQuiet();
        Person p0 = Person.wuerfeln(4711);
        StudioScene.Figure f = new StudioScene.Figure(new Body(model), AvatarStudio.loadSkeleton(model), p0, "stehen", 0, 0);
        scene.figures.add(f);
        AvatarStudio.Offscreen off = new AvatarStudio.Offscreen(scene, 960, 540);
        off.shot(3);
        java.util.Random rnd = new java.util.Random(19);
        // Musik, Tanz
        File song = MusikFilm.pick(new File("daten/musik"));
        com.dan.beat.Track tr = song == null ? null : com.dan.beat.Track.read(song);
        com.dan.beat.Rhythm rh = tr == null ? null : com.dan.beat.Rhythm.analyse(tr);
        com.dan.voice.Speech songSpeech = song == null || !MusikFilm.lrcOf(song).isFile() ? null : MusikFilm.song(com.dan.beat.Lyrics.read(MusikFilm.lrcOf(song)));
        com.dan.avatar.Dance dance = null;
        try { dance = TanzFilm.dance(); } catch (Exception e) { say.accept("  (kein Tanz: " + e.getMessage() + ")"); }
        String[] sentences = {"Oben im Fischerboot bin ich frei.", "Heute Nacht kommt keiner mehr raus.", "Jede Stunde geht er einmal um die Laterne.",
                "Bald brennt das Feuer, ihr Schiffer!", "Am 22. Oktober um 20 Uhr."};
        Climate[] weather = {Climate.regennacht(), Climate.sturm(), Climate.wintermorgen(), Climate.sommerabend(), Climate.of(0, 1, 14, 0.7, 0)};
        String[] weatherName = {"Regennacht", "Sturm", "Wintermorgen", "Sommerabend", "trocken"};
        Quality[] qs = Quality.values();
        int frames = (int) Math.round(minutes * 60 * FPS);
        int exceptions = 0, nan = 0, persons = 1, sentencesSaid = 0, films = 0, regieErrors = 0, musics = 0;
        double minuteCpu = 0, minuteCpuMax = 0, minuteGpu = 0;
        int minuteN = 0;
        long heap10 = -1, heapEnd = 0, heapMax = 0;
        StudioStage stage = null;
        Regie regie = null;
        String phase = "frei";
        AvatarStudio.portraitCam(scene, new double[]{0, 1.62, 0}, 10, 0.95);
        for (int i = 0; i < frames; i++) {
            double t = i / (double) FPS;
            int sec = (int) Math.floor(t);
            boolean newSecond = i % FPS == 0;
            try {
                if (newSecond) {
                    int min = sec / 60;
                    // Regiebuch in den Minuten 20 und 40
                    if (regie == null && (sec == 20 * 60 || sec == 40 * 60) && sec < minutes * 60) {
                        regie = Regie.resource(AvatarStudio.class, "regie/am_steg.txt");
                        stage = new StudioStage(scene, model);
                        f.visible = false;
                        f.hush(); f.dance = null; f.drum = null;
                        scene.startFilm(regie, stage);
                        films++;
                        phase = "Regiebuch";
                    }
                    if (regie != null && scene.regie == null && !scene.kino.laeuft()) {
                        regieErrors += regie.errors.size();
                        for (String e : regie.errors) say.accept("  FEHLER Regie: " + e);
                        scene.figures.removeAll(stage.created);
                        scene.set = null;
                        f.visible = true;
                        regie = null;
                        stage = null;
                        phase = "frei";
                        AvatarStudio.portraitCam(scene, AvatarStudio.headOf(f), 10, 0.95);
                    }
                    if (regie == null) {
                        // neue Person alle zwei Minuten
                        if (sec > 0 && sec % 120 == 0) {
                            Person p = Person.wuerfeln(rnd.nextInt(1_000_000)).copy();
                            p.hair = Person.HAIR_STYLES[rnd.nextInt(Person.HAIR_STYLES.length)];
                            f.set(p);
                            java.util.List<String> av = com.dan.avatar.Outfit.available(scene.wardrobe);
                            f.outfit = com.dan.avatar.Outfit.preset(av.get(rnd.nextInt(av.size())));
                            persons++;
                        }
                        // Wetter alle drei Minuten
                        if (sec % 180 == 0) {
                            int k = (sec / 180) % weather.length;
                            scene.climate = weather[k];
                            scene.wind = new double[]{weather[k].wind[0], 0.4, weather[k].wind[2]};
                            if (k == 4) f.dry();
                        }
                        // Qualität alle 7,5 Minuten, Porträt alle 5 Minuten
                        scene.quality = qs[(int) (t / 450) % qs.length];
                        scene.portraitOn = (sec / 300) % 2 == 0;
                        // Musik mit Tanz und Trommel in den Minuten 10, 30, 50 (je 3 Minuten)
                        if (tr != null && (min == 10 || min == 30 || min == 50) && sec % 60 == 0) {
                            f.play(rh, songSpeech, null);
                            f.dance = dance;
                            f.drum = null;
                            musics++;
                            phase = "Musik";
                        }
                        if (tr != null && (min == 11 || min == 31 || min == 51) && sec % 60 == 0) { f.dance = null; f.drum = new com.dan.avatar.Drum(); }
                        if (tr != null && (min == 13 || min == 33 || min == 53) && sec % 60 == 0) { f.hush(); f.dance = null; f.drum = null; phase = "frei"; }
                        // Sprechen alle 45 s, wenn keine Musik läuft
                        if (f.rhythm == null && sec % 45 == 0 && sec > 0) { f.say(sentences[sentencesSaid % sentences.length]); sentencesSaid++; }
                        if (sec % 30 == 0) AvatarStudio.portraitCam(scene, f.jointWorld != null ? AvatarStudio.headOf(f) : new double[]{0, 1.62, 0}, 10 + 25 * Math.sin(sec / 97.0), f.dance != null ? 3.2 : 0.95);
                    }
                }
                scene.hairTime = t;
                long c0 = System.nanoTime();
                off.shot(1);
                double cpu = (System.nanoTime() - c0) / 1e6;
                minuteCpu += cpu; minuteCpuMax = Math.max(minuteCpuMax, cpu); minuteGpu += scene.gpuMs; minuteN++;
                for (StudioScene.Figure g : scene.figures) {
                    double[][] w = g.jointWorld;
                    if (w == null) continue;
                    for (double[] m : w) for (double v : m) if (!Double.isFinite(v)) { nan++; break; }
                }
            } catch (Throwable e) {
                exceptions++;
                if (exceptions <= 10) {
                    java.io.StringWriter sw = new java.io.StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    say.accept(String.format(Locale.GERMANY, "  AUSNAHME bei %.1f s: %s", t, sw.toString().split("\\R")[0] + " / " + sw.toString().split("\\R")[1].trim()));
                }
            }
            if ((i + 1) % (60 * FPS) == 0) {
                int min = (i + 1) / (60 * FPS);
                long heap = -1;
                if (min % 5 == 0) {
                    System.gc();
                    Runtime rt = Runtime.getRuntime();
                    heap = rt.totalMemory() - rt.freeMemory();
                    heapMax = Math.max(heapMax, heap);
                    if (min == 10) heap10 = heap;
                    heapEnd = heap;
                }
                say.accept(String.format(Locale.GERMANY, "  Minute %2d · %-10s · %-6s · Bild %5.1f ms (höchstens %6.1f) · GPU %5.2f ms · %s%s",
                        min, phase, scene.quality, minuteCpu / Math.max(1, minuteN), minuteCpuMax, minuteGpu / Math.max(1, minuteN),
                        scene.figures.size() + " Figur(en)", heap >= 0 ? String.format(Locale.GERMANY, " · Speicher %,d MB", heap >> 20) : ""));
                minuteCpu = minuteCpuMax = minuteGpu = 0;
                minuteN = 0;
            }
        }
        off.d.destroy();
        double wall = (System.nanoTime() - wall0) / 1e9;
        boolean memOk = heap10 < 0 || heapEnd <= heap10 * 1.25 + (64L << 20);
        boolean ok = exceptions == 0 && nan == 0 && regieErrors == 0 && memOk;
        say.accept(String.format(Locale.GERMANY, "Bilder %,d · %d Personen gewürfelt · %d Sätze · %d-mal Musik mit Tanz und Trommel · %d-mal Regiebuch · Wetter und Qualität reihum",
                frames, persons, sentencesSaid, musics, films));
        say.accept(String.format(Locale.GERMANY, "Ausnahmen %d · ungültige Zahlen %d · Fehler der Regie %d · Speicher Minute 10: %,d MB, Ende: %,d MB, höchstens %,d MB · Laufzeit %.1f min",
                exceptions, nan, regieErrors, heap10 >> 20, heapEnd >> 20, heapMax >> 20, wall / 60));
        say.accept(ok ? "Dauerlauf bestanden" : "Dauerlauf NICHT bestanden");
        out.getAbsoluteFile().getParentFile().mkdirs();
        java.nio.file.Files.write(out.toPath(), log, StandardCharsets.UTF_8);
        if (!ok) System.exit(1);
    }

    static Gltf loadModelQuiet() throws java.io.IOException { return AvatarStudio.loadModel(); }
}
