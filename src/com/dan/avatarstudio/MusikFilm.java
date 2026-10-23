package com.dan.avatarstudio;

import com.dan.avatar.Body;
import com.dan.avatar.Person;
import com.dan.beat.Lyrics;
import com.dan.beat.Rhythm;
import com.dan.beat.Track;
import com.dan.gltf.Gltf;
import com.dan.voice.Speech;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

import static com.dan.avatarstudio.AvatarStudio.*;

/**
 * Musik-Visual (Abnahme Phase 17): eine Minute eines Lieds aus {@code daten/musik} (WAV mit LRC daneben) ohne Fenster,
 * im Kino mit Porträt, Regen und Lichtern im Hintergrund. Die Person nickt im Takt, wiegt Kopf und Schultern, singt die
 * Zeilen (Viseme aus der Lautung der LRC), atmet vor den Zeilen, schließt in ruhigen Stellen die Augen; die Lichter
 * pulsieren auf dem Schlag, geschnitten wird alle vier Takte auf der Eins. Video mit Ton, Statusbild mit sechs
 * Augenblicken und der Zeitleiste (Lautheit, Schläge, Zeilen, Mund, Nicken, Ruhe).
 */
final class MusikFilm {

    private MusikFilm() {}

    static final int FPS = 30;

    /** Lied wählen: die erste WAV in daten/musik mit LRC daneben (oder die aus -Dmusik.datei). */
    static File pick(File dir) {
        String p = System.getProperty("musik.datei");
        if (p != null) return new File(p);
        File[] all = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".wav"));
        if (all == null) return null;
        java.util.Arrays.sort(all);
        for (File f : all) if (lrcOf(f).isFile()) return f;
        return all.length > 0 ? all[0] : null;
    }

    static File lrcOf(File wav) { return new File(wav.getParentFile(), wav.getName().replaceFirst("(?i)\\.wav$", ".lrc")); }

    /** Gesungene Spur aus den Zeilen (Lautung). */
    static Speech song(Lyrics ly) {
        if (ly == null || ly.lines.isEmpty()) return null;
        List<String> t = new ArrayList<>();
        double[] a = new double[ly.lines.size()], e = new double[ly.lines.size()];
        for (int i = 0; i < ly.lines.size(); i++) { Lyrics.Line l = ly.lines.get(i); t.add(l.sung()); a[i] = l.t0(); e[i] = l.t1(); }
        return Speech.sung(t, a, e);
    }

    /** Einstellungen der Kamera, reihum alle vier Takte (Bildgröße, Winkel). */
    static final Object[][] SHOTS = {
            {com.dan.portrait.Shot.NAH, 12.0}, {com.dan.portrait.Shot.HALBNAH, -22.0}, {com.dan.portrait.Shot.NAH, -6.0},
            {com.dan.portrait.Shot.HALBNAH, 26.0}, {com.dan.portrait.Shot.NAH, 22.0}, {com.dan.portrait.Shot.HALBNAH, -8.0}};

    /** Porträt im Regen, Kino-Licht, Lichter im Hintergrund. */
    static void setUp(StudioScene scene) {
        com.dan.avatar.Climate rn = com.dan.avatar.Climate.regennacht();
        scene.climate = rn;
        scene.wind = new double[]{rn.wind[0], 0.45, rn.wind[2]};
        scene.lens.focalMm = 85; scene.lens.fNumber = 2.0;
        scene.grade = "Regennacht"; scene.bokehStyle = "Regennacht";
        scene.grain = 0.6f;
        scene.musicLight = 1;
    }

    static void shot(StudioScene scene, StudioScene.Figure f, int k) {
        Object[] s = SHOTS[Math.floorMod(k, SHOTS.length)];
        portraitShot(scene, f, (com.dan.portrait.Shot) s[0], (double) s[1], "Regennacht");
    }

    record Sample(double t, double energy, double onset, double mouth, double nod, double calm, double lid, double pulse) { }

    static void run(File out, File video, int w, int h) throws Exception {
        File wf = pick(new File("daten/musik"));
        if (wf == null) throw new IllegalStateException("kein Lied in daten/musik (WAV, LRC daneben)");
        Track tr = Track.read(wf);
        long ta = System.nanoTime();
        Rhythm rh = Rhythm.analyse(tr);
        double msRhythm = (System.nanoTime() - ta) / 1e6;
        Lyrics ly = lrcOf(wf).isFile() ? Lyrics.read(lrcOf(wf)) : null;
        Speech song = song(ly);
        double start = Double.parseDouble(System.getProperty("musik.von", "4.0")), len = Double.parseDouble(System.getProperty("musik.dauer", "60"));
        start = Math.max(0, Math.min(start, tr.seconds() - 5));
        len = Math.min(len, tr.seconds() - start);

        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        scene.exposure = 1.3;
        scene.fixedDt = 1.0 / FPS;
        Gltf model = loadModel();
        // ein Sänger mit freiem Gesicht: der Wärter aus Lighthouse (seed 19, kurzes graues Haar, Bart)
        Person per = Person.wuerfeln(Long.getLong("musik.seed", 19L)).copy();
        StudioScene.Figure f = new StudioScene.Figure(new Body(model), loadSkeleton(model), per, "stehen", 0, 0);
        f.pupilInstant = false;
        f.outfit = com.dan.avatar.Outfit.preset("Alltag");
        scene.figures.add(f);
        int vw = 960, vh = 540;
        Offscreen off = new Offscreen(scene, vw, vh);
        scene.hairTime = 0;
        off.shot(3);
        com.dan.avatar.Behavior b = f.behavior;
        b.feel("ruhig", 0.5);
        b.look(com.dan.avatar.Behavior.Look.CAMERA);
        setUp(scene);
        shot(scene, f, 0);
        simulate(off, scene, 60, 60);                       // eine Minute Regen vorab: nass, Tropfen
        shot(scene, f, 0);
        String title = ly != null && !ly.title().isEmpty() ? ly.title() : wf.getName();
        String artist = ly != null ? ly.artist() : "";
        scene.kino.titelS = 2.2;                               // kurzer Vorspann: die ruhige Einleitung soll man sehen
        scene.kino.beginnen("Avatar · Musik", title, artist.isEmpty() ? "Musik-Visual" : artist);
        f.play(rh, song, null);
        f.speechT = start - 1.0 / FPS;                       // Bild j zeigt die Spur bei start + j / FPS (wie der Ton)
        short[] pcm = tr.pcm16();
        MjpegAvi avi = video == null ? null : new MjpegAvi(video, vw, vh, FPS, 0.85f, tr.rate);
        double[] snapAt = {4.6, 12.5, 21.5, 30, 44, 59.2};
        BufferedImage[] snaps = new BufferedImage[snapAt.length];
        String[] snapCap = new String[snapAt.length];
        List<Sample> series = new ArrayList<>();
        int frames = (int) Math.round(len * FPS);
        int cuts = 0, lastBar = Integer.MIN_VALUE, nb0 = b.blinks.size();
        double gpu = 0, inLine = 0, inLineOpen = 0, outLine = 0, outLineOpen = 0, calmS = 0;
        Lyrics.Line shown = null;
        int linesSung = 0;
        try {
            for (int j = 0; j < frames; j++) {
                double t = start + j / (double) FPS;
                // Untertitel aus dem Liedtext
                Lyrics.Line l = ly == null ? null : ly.at(t);
                if (l != shown) { scene.kino.untertitel(l == null ? "" : l.text()); if (l != null) linesSung++; shown = l; }
                // Schnitt alle vier Takte auf der Eins
                int bar = (int) Math.floor((rh.beatAt(t) - rh.downbeat) / rh.meter);
                if (lastBar != Integer.MIN_VALUE && bar != lastBar && Math.floorMod(bar, 4) == 0) {
                    int k = ++cuts;
                    scene.cut(() -> shot(scene, f, k));
                }
                lastBar = bar;
                scene.hairTime = b.time();
                BufferedImage fr = off.shot(1);
                gpu += scene.gpuMs;
                double mouth = com.dan.avatar.gl.AvatarGl.mouthOpening(f.lastForms);
                double mb = b.musicBeat, gv = Math.min(1, b.groove);
                double nod = Double.isNaN(mb) ? 0 : gv * 4.0 * (0.5 + 0.5 * Math.cos(2 * Math.PI * ((mb - Math.floor(mb)) - 0.12))) - gv * 2.0;
                series.add(new Sample(t, rh.energy(t), rh.onset(t), mouth, nod, rh.calm(t), b.calmLid(), f.musicPulse));
                if (l != null) { inLine++; inLineOpen += mouth; } else { outLine++; outLineOpen += mouth; }
                if (b.calmLid() > 0.5) calmS += 1.0 / FPS;
                for (int i = 0; i < snapAt.length; i++)
                    if (snaps[i] == null && t - start >= snapAt[i]) {
                        snaps[i] = copy(fr);
                        snapCap[i] = String.format(Locale.GERMANY, "%.1f S · SCHLAG %.1f · %s", t, rh.beatAt(t) + 1, l == null ? "ZWISCHENSPIEL" : "„" + l.text().toUpperCase(Locale.ROOT) + "“");
                    }
                if (avi != null) {
                    avi.add(fr);
                    int a0 = (int) Math.round(t * tr.rate), a1 = (int) Math.round((t + 1.0 / FPS) * tr.rate);
                    a0 = Math.min(a0, pcm.length); a1 = Math.min(a1, pcm.length);
                    if (a1 > a0) avi.addAudio(pcm, a0, a1 - a0);
                    else avi.addAudio(new short[tr.rate / FPS], 0, tr.rate / FPS);
                }
            }
        } finally { if (avi != null) avi.close(); }
        f.hush();
        int blinks = b.blinks.size() - nb0;
        String glInfo = scene.glInfo;
        off.d.destroy();
        for (int i = 0; i < snaps.length; i++) if (snaps[i] == null) { snaps[i] = copy(off.last); snapCap[i] = ""; }

        // ---- Bild
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(TEAL);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        g.drawString("AVATAR STUDIO · PHASE 17 · MUSIK FÜHLEN UND SINGEN", 14, 28);
        g.setColor(MUTED);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.drawString(String.format(Locale.GERMANY, "Musik-Visual „%s“ · %s · %.0f s ab %.1f s · com.dan.beat", title, artist, len, start), 520, 28);
        int tw = 520, th = 293, top = 42;
        for (int i = 0; i < 6; i++) {
            int x = 14 + (i % 3) * (tw + 10), y = top + (i / 3) * (th + 26);
            drawCrop(g, snaps[i], x, y, tw, th);
            String c = snapCap[i];
            if (c.length() > 70) c = c.substring(0, 69) + "…“";
            caption(g, c, x, y + th + 15, i == 0 ? TEAL : INK);
        }
        int py = top + 2 * (th + 26) + 6;
        timeline(g, series, rh, ly, start, len, 14, py, w - 28, h - py - 58);
        List<String> lines = new ArrayList<>();
        double openIn = inLineOpen / Math.max(1, inLine), openOut = outLineOpen / Math.max(1, outLine);
        lines.add(String.format(Locale.GERMANY, "%s · Analyse %.0f ms · Video %d Bilder mit Ton, GPU %.2f ms · %d Schnitte auf der Eins",
                rh.describe(), msRhythm, frames, gpu / Math.max(1, frames), cuts));
        lines.add(String.format(Locale.GERMANY, "Singen: %d Zeilen · Mund offen %.2f in den Zeilen, %.2f dazwischen · Augen zu %.1f s · %d Lidschläge · %s",
                linesSung, openIn, openOut, calmS, blinks, video == null ? glInfo : "Video: " + video.getName()));
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        for (int i = 0; i < lines.size(); i++) { g.setColor(i == 0 ? TEAL : INK); g.drawString(lines.get(i), 14, h - 22 + i * 16); }
        g.dispose();
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Statusbild Musik: " + out.getAbsolutePath());
        for (String s : lines) System.out.println("  " + s);
    }

    /** Zeitleiste: Zeilen, Lautheit und Einsätze mit Schlägen, Mund, Nicken, Ruhe und Augen. */
    static void timeline(Graphics2D g, List<Sample> s, Rhythm rh, Lyrics ly, double start, double len, int x, int y, int w, int h) {
        g.setColor(PANEL);
        g.fillRect(x, y, w, h);
        int lane = (h - 10) / 5;
        String[] names = {"ZEILEN", "LAUTHEIT · EINSÄTZE · SCHLÄGE", "MUND", "NICKEN", "RUHE · AUGEN ZU"};
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        for (int k = 0; k < 5; k++) { g.setColor(MUTED); g.drawString(names[k], x + 4, y + k * lane + 11); g.setColor(LINE); g.drawLine(x, y + (k + 1) * lane, x + w, y + (k + 1) * lane); }
        java.util.function.DoubleUnaryOperator X = t -> x + (t - start) / len * w;
        // Zeilen
        if (ly != null) for (Lyrics.Line l : ly.lines) {
            if (l.t1() < start || l.t0() > start + len) continue;
            int a = (int) X.applyAsDouble(Math.max(start, l.t0())), e = (int) X.applyAsDouble(Math.min(start + len, l.t1()));
            g.setColor(new Color(79, 198, 201, 70));
            g.fillRect(a, y + 13, Math.max(1, e - a), lane - 15);
            g.setColor(INK);
            String txt = l.text();
            while (txt.length() > 3 && g.getFontMetrics().stringWidth(txt) > e - a - 4) txt = txt.substring(0, txt.length() - 2);
            g.drawString(txt, a + 3, y + lane - 6);
        }
        // Schläge
        for (int i = 0; i < rh.beats.length; i++) {
            double t = rh.beats[i];
            if (t < start || t > start + len) continue;
            int px = (int) X.applyAsDouble(t);
            boolean one = rh.isDownbeat(i);
            g.setColor(one ? AMBER : new Color(232, 176, 96, 110));
            g.drawLine(px, y + 2 * lane - (one ? lane - 14 : 10), px, y + 2 * lane);
        }
        curve(g, s, X, y + lane, lane, Sample::energy, 1, TEAL);
        curve(g, s, X, y + lane, lane, smp -> Math.min(1, smp.onset() / 4), 1, new Color(147, 160, 170, 160));
        curve(g, s, X, y + 2 * lane, lane, Sample::mouth, 0.7, new Color(208, 122, 130));
        curve(g, s, X, y + 3 * lane, lane, smp -> (smp.nod() + 2.5) / 5, 1, AMBER);
        curve(g, s, X, y + 4 * lane, lane, Sample::calm, 1, MUTED);
        curve(g, s, X, y + 4 * lane, lane, Sample::lid, 1, TEAL);
        g.setColor(MUTED);
        for (double t = Math.ceil(start / 5) * 5; t <= start + len; t += 5) {
            int px = (int) X.applyAsDouble(t);
            g.drawLine(px, y + h - 4, px, y + h);
            g.drawString(String.format(Locale.GERMANY, "%.0f s", t), px + 2, y + h + 11);
        }
    }

    static void curve(Graphics2D g, List<Sample> s, java.util.function.DoubleUnaryOperator X, int y, int lane, java.util.function.ToDoubleFunction<Sample> v, double max, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.3f));
        int px0 = -1, py0 = 0;
        for (Sample smp : s) {
            int px = (int) X.applyAsDouble(smp.t());
            int py = y + lane - 3 - (int) Math.round(Math.max(0, Math.min(1, v.applyAsDouble(smp) / max)) * (lane - 16));
            if (px0 >= 0 && px != px0) g.drawLine(px0, py0, px, py);
            if (px != px0) { px0 = px; py0 = py; }
        }
        g.setStroke(new BasicStroke(1f));
    }

    static void caption(Graphics2D g, String s, int x, int y, Color c) { AvatarStudio.caption(g, s, x, y, c); }
}
