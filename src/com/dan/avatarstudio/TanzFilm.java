package com.dan.avatarstudio;

import com.dan.avatar.Body;
import com.dan.avatar.Dance;
import com.dan.avatar.Drum;
import com.dan.avatar.Mocap;
import com.dan.avatar.Person;
import com.dan.beat.Lyrics;
import com.dan.beat.Rhythm;
import com.dan.beat.Track;
import com.dan.gltf.Gltf;

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
 * Statusbild und Video Tanz und Trommel (Abnahme Phase 18): 30 s Tanz zum Lied aus {@code daten/musik} (Charleston aus
 * der CMU-Datenbank, im Takt gedehnt und gemischt), danach 15 s die Handtrommel, im Takt geschlagen. Ohne Fenster, Video
 * mit Ton (das Lied, dazu der Klang jedes Trommelschlags). Das Bild zeigt sechs Augenblicke und die Zeitleiste
 * (Schläge, Stücke der Aufnahmen, Füße, Hand über dem Fell) mit den Messwerten.
 */
final class TanzFilm {

    private TanzFilm() {}

    static final int FPS = 30;
    private static volatile Dance cached;

    /** Charleston aus daten/tanz/cmu (alle AMC zur ASF, nach Namen). */
    static Dance dance() throws java.io.IOException {
        Dance d = cached;
        if (d != null) return new Dance(clipsOf(d));
        File dir = new File("daten/tanz/cmu");
        File[] asf = dir.listFiles((x, n) -> n.toLowerCase(Locale.ROOT).endsWith(".asf"));
        if (asf == null || asf.length == 0) throw new java.io.IOException("keine Aufnahmen in daten/tanz/cmu (ASF/AMC)");
        List<Mocap> ms = new ArrayList<>();
        for (File a : asf) {
            String pre = a.getName().replaceFirst("(?i)\\.asf$", "");
            File[] amc = dir.listFiles((x, n) -> n.startsWith(pre + "_") && n.toLowerCase(Locale.ROOT).endsWith(".amc"));
            if (amc == null) continue;
            java.util.Arrays.sort(amc);
            for (File m : amc) ms.add(Mocap.read(a, m, 120));
        }
        if (ms.isEmpty()) throw new java.io.IOException("keine AMC in daten/tanz/cmu");
        d = new Dance(ms);
        cached = d;
        return new Dance(clipsOf(d));
    }

    private static List<Mocap> clipsOf(Dance d) { List<Mocap> l = new ArrayList<>(); for (Dance.Clip c : d.clips) l.add(c.m); return l; }

    record Sample(double t, double beat, int clip, double footL, double footR, double hand, boolean drum) { }

    /** Klang eines Trommelschlags (Rahmentrommel: tiefer Ton, kurzes Rauschen), mono. */
    static void mixHit(short[] pcm, int rate, double t, double strength) {
        int a = (int) Math.round(t * rate), n = (int) (0.35 * rate);
        java.util.Random rnd = new java.util.Random(Math.round(t * 1000));
        for (int i = 0; i < n && a + i < pcm.length; i++) {
            if (a + i < 0) continue;
            double s = i / (double) rate;
            double v = 0.55 * Math.exp(-s / 0.11) * Math.sin(2 * Math.PI * (82 + 30 * Math.exp(-s / 0.03)) * s)
                    + 0.18 * Math.exp(-s / 0.05) * Math.sin(2 * Math.PI * 190 * s)
                    + 0.10 * Math.exp(-s / 0.012) * rnd.nextGaussian();
            int x = pcm[a + i] + (int) Math.round(v * strength * 9000);
            pcm[a + i] = (short) Math.max(-32768, Math.min(32767, x));
        }
    }

    static void run(File out, File video, int w, int h) throws Exception {
        File wf = MusikFilm.pick(new File("daten/musik"));
        if (wf == null) throw new IllegalStateException("kein Lied in daten/musik");
        Track tr = Track.read(wf);
        Rhythm rh = Rhythm.analyse(tr);
        Lyrics ly = MusikFilm.lrcOf(wf).isFile() ? Lyrics.read(MusikFilm.lrcOf(wf)) : null;
        double start = Double.parseDouble(System.getProperty("tanz.von", "43.0"));
        double danceLen = 30, drumLen = 15, len = danceLen + drumLen;
        start = Math.max(0, Math.min(start, tr.seconds() - len - 0.5));
        Dance dance = dance();
        dance.beatS = 60 / rh.bpm;

        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        scene.exposure = 1.3;
        scene.fixedDt = 1.0 / FPS;
        Gltf model = loadModel();
        Person per = Person.wuerfeln(Long.getLong("tanz.seed", 19L)).copy();
        StudioScene.Figure f = new StudioScene.Figure(new Body(model), loadSkeleton(model), per, "stehen", 0, 0);
        f.outfit = com.dan.avatar.Outfit.preset("Alltag");
        scene.figures.add(f);
        int vw = 960, vh = 540;
        Offscreen off = new Offscreen(scene, vw, vh);
        scene.hairTime = 0;
        off.shot(3);
        com.dan.avatar.Behavior b = f.behavior;
        b.feel("freudig", 0.5);
        b.look(com.dan.avatar.Behavior.Look.CAMERA);
        MusikFilm.setUp(scene);
        scene.climate = new com.dan.avatar.Climate();                     // trocken: getanzt wird unter Dach
        scene.lens.focalMm = 35; scene.lens.fNumber = 2.8;
        // ganze Person: Kamera auf die Mitte des Körpers, 4,3 m, 35 mm (mit den Balken des Kinos rund 2 m Bildhöhe)
        java.util.function.DoubleConsumer full = yaw -> {
            scene.portraitOn = true;
            scene.lensFov = true;
            scene.cam.fovyDeg = scene.lens.fovyDeg();
            scene.cam.tx = f.x; scene.cam.ty = 0.98; scene.cam.tz = f.z;
            scene.cam.yawDeg = yaw; scene.cam.pitchDeg = 4; scene.cam.dist = 4.3;
            scene.lens.focusM = scene.cam.dist;
            com.dan.avatarstudio.LightRig.applySet(scene.light, "Regennacht", headOf(f), scene.cam.eye());
            scene.exposure = 1.6;
        };
        Runnable wide = () -> full.accept(8);
        Runnable wide2 = () -> full.accept(-24);
        Runnable drumShot = () -> {
            portraitShot(scene, f, com.dan.portrait.Shot.HALBNAH, 18, "Regennacht");
            scene.cam.ty -= 0.16; scene.cam.dist *= 1.3; scene.lens.focusM = scene.cam.dist;
            scene.lens.fNumber = 2.8; scene.exposure = 1.3;
        };
        wide.run();
        for (int i = 0; i < 20; i++) off.shot(1);
        scene.kino.titelS = 1.8;
        scene.kino.beginnen("Avatar · Tanz", "Charleston", (ly != null && !ly.title().isEmpty() ? ly.title() : wf.getName()));
        f.play(rh, null, null);
        f.speechT = start - 1.0 / FPS;
        f.dance = dance;
        short[] pcm = tr.pcm16();
        MjpegAvi avi = video == null ? null : new MjpegAvi(video, vw, vh, FPS, 0.85f, tr.rate);
        double[] snapAt = {4.2, 11.0, 18.5, 26.0, 33.2, 41.3};
        BufferedImage[] snaps = new BufferedImage[snapAt.length];
        String[] cap = new String[snapAt.length];
        List<Sample> series = new ArrayList<>();
        List<Double> strikeT = new ArrayList<>();
        int frames = (int) Math.round(len * FPS), lastBar = Integer.MIN_VALUE, cuts = 0;
        double gpu = 0, dirSum = 0, dirMax = 0, gripSum = 0;
        int dirN = 0, gripN = 0;
        Drum drum = null;
        com.dan.avatar.Skeleton sk = f.skeleton;
        try {
            for (int j = 0; j < frames; j++) {
                double tl = j / (double) FPS, t = start + tl;
                if (drum == null && tl >= danceLen) {
                    drum = new Drum();
                    Drum dd = drum;
                    scene.cut(() -> { f.dance = null; f.drum = dd; drumShot.run(); });
                }
                int bar = (int) Math.floor((rh.beatAt(t) - rh.downbeat) / rh.meter);
                if (drum == null && lastBar != Integer.MIN_VALUE && bar != lastBar && Math.floorMod(bar, 4) == 0) {
                    int k = ++cuts;
                    scene.cut(k % 2 == 1 ? wide2 : wide);
                }
                lastBar = bar;
                scene.hairTime = b.time();
                BufferedImage fr = off.shot(1);
                gpu += scene.gpuMs;
                double[][] jw = f.jointWorld;
                double beat = f.beatNow();
                Dance.Place pl = dance.place(beat);
                double fl = jw == null ? 0 : jw[sk.joint("foot_l")][13], frr = jw == null ? 0 : jw[sk.joint("foot_r")][13];
                double hand = Double.NaN;
                Drum dr = f.drum;
                if (dr != null && jw != null) {
                    hand = dr.fingerGap;
                    double[] hl = com.dan.avatar.Skeleton.position(jw, sk.joint("hand_l"));
                    double[] rim = {dr.center[0] + dr.right[0] * dr.radius, dr.center[1] + dr.right[1] * dr.radius, dr.center[2] + dr.right[2] * dr.radius};
                    gripSum += Math.sqrt((hl[0] - rim[0]) * (hl[0] - rim[0]) + (hl[1] - rim[1]) * (hl[1] - rim[1]) + (hl[2] - rim[2]) * (hl[2] - rim[2]));
                    gripN++;
                }
                if (f.dance != null && jw != null) {
                    Dance.Clip c = dance.clips.get(pl.clip());
                    double fm = pl.time() * c.m.fps;
                    for (String[] aim : DANCE_AIM) {
                        int a0 = sk.joint(aim[0]), a1 = sk.joint(aim[1]);
                        double[] p0 = com.dan.avatar.Skeleton.position(jw, a0), p1 = com.dan.avatar.Skeleton.position(jw, a1);
                        double[] e0 = c.m.start(c.m.bone(aim[2]), fm), e1 = c.m.end(c.m.bone(aim[2]), fm);
                        double ang = angle(sub(p1, p0), sub(e1, e0));
                        if (beat - 4 * Math.floor(beat / 4) < 3.6) { dirSum += ang; dirMax = Math.max(dirMax, ang); dirN++; }
                    }
                }
                series.add(new Sample(tl, beat, f.dance != null ? pl.clip() : -1, fl, frr, hand, dr != null));
                for (int i = 0; i < snapAt.length; i++)
                    if (snaps[i] == null && tl >= snapAt[i]) {
                        snaps[i] = copy(fr);
                        cap[i] = f.dance != null
                                ? String.format(Locale.GERMANY, "TANZ %.1f S · SCHLAG %.1f · %s, %.0f %% TEMPO", tl, beat + 1, dance.clips.get(pl.clip()).m.name, 100 / pl.stretch())
                                : String.format(Locale.GERMANY, "TROMMEL %.1f S · SCHLAG %.1f · FINGER %.0f CM ÜBER DEM FELL", tl, beat + 1, hand * 100);
                    }
                if (dr != null) {
                    // Klang jedes Trommelschlags, der in dieses Bild fällt (vor dem Schreiben des Tons)
                    for (int i = 0; i < rh.beats.length; i++) {
                        double tb = rh.beats[i];
                        if (tb >= t && tb < t + 1.0 / FPS) {
                            strikeT.add(tb - start);
                            mixHit(pcm, tr.rate, tb, rh.isDownbeat(i) ? 1 : 0.7);
                        }
                    }
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
        String glInfo = scene.glInfo;
        off.d.destroy();
        for (int i = 0; i < snaps.length; i++) if (snaps[i] == null) { snaps[i] = copy(off.last); cap[i] = ""; }
        // Anschläge: Fingerspitzen unter 12 mm über dem Fell; Zeit der tiefsten Stelle gegen den Schlag des Lieds
        double errSum = 0;
        int errN = 0;
        for (int i = 0; i < series.size(); ) {
            Sample m = series.get(i);
            if (!m.drum() || Double.isNaN(m.hand()) || m.hand() >= 0.012) { i++; continue; }
            int best = i;
            while (i < series.size() && series.get(i).drum() && !Double.isNaN(series.get(i).hand()) && series.get(i).hand() < 0.012) {
                if (series.get(i).hand() < series.get(best).hand()) best = i;
                i++;
            }
            double bt = series.get(best).beat();
            errSum += Math.abs(bt - Math.round(bt)) * 60 / rh.bpm;
            errN++;
        }

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
        g.drawString("AVATAR STUDIO · PHASE 18 · TANZEN UND TROMMEL", 14, 28);
        g.setColor(MUTED);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.drawString(String.format(Locale.GERMANY, "30 s Charleston (CMU 93) und 15 s Rahmentrommel zu „%s“ ab %.0f s · %.1f Schläge/min", ly != null ? ly.title() : wf.getName(), start, rh.bpm), 470, 28);
        int tw = 520, th = 293, top = 42;
        for (int i = 0; i < 6; i++) {
            int x = 14 + (i % 3) * (tw + 10), y = top + (i / 3) * (th + 26);
            drawCrop(g, snaps[i], x, y, tw, th);
            caption(g, cap[i], x, y + th + 15, i < 4 ? TEAL : AMBER);
        }
        int py = top + 2 * (th + 26) + 6;
        timeline(g, series, rh, start, len, dance, 14, py, w - 28, h - py - 58);
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.GERMANY, "Tanz: %d Aufnahmen, je Takt ein Stück, %d Schnitte · Richtung der Knochen wie in der Aufnahme: im Mittel %.1f°, höchstens %.1f° · GPU %.2f ms",
                dance.clips.size(), cuts, dirSum / Math.max(1, dirN), dirMax, gpu / Math.max(1, frames)));
        lines.add(String.format(Locale.GERMANY, "Trommel: %d Schläge im Ton · Finger am Fell %.0f ms neben dem Schlag (Mittel aus %d Anschlägen) · linke Hand %.1f cm vom Rand · %s",
                strikeT.size(), 1000 * errSum / Math.max(1, errN), errN, 100 * gripSum / Math.max(1, gripN), video == null ? glInfo : "Video mit Ton: " + video.getName()));
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        for (int i = 0; i < lines.size(); i++) { g.setColor(i == 0 ? TEAL : AMBER); g.drawString(lines.get(i), 14, h - 22 + i * 16); }
        g.dispose();
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Statusbild Tanz: " + out.getAbsolutePath());
        System.out.println("  " + dance.describe());
        for (String s : lines) System.out.println("  " + s);
    }

    static final String[][] DANCE_AIM = {{"upperarm_l", "lowerarm_l", "lhumerus"}, {"lowerarm_l", "hand_l", "lradius"}, {"upperarm_r", "lowerarm_r", "rhumerus"},
            {"lowerarm_r", "hand_r", "rradius"}, {"thigh_l", "calf_l", "lfemur"}, {"calf_l", "foot_l", "ltibia"}, {"thigh_r", "calf_r", "rfemur"}, {"calf_r", "foot_r", "rtibia"}};

    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }

    static double angle(double[] a, double[] b) {
        double d = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / Math.max(1e-12, Math.sqrt((a[0] * a[0] + a[1] * a[1] + a[2] * a[2]) * (b[0] * b[0] + b[1] * b[1] + b[2] * b[2])));
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, d))));
    }

    static final Color[] CLIP = {new Color(79, 198, 201), new Color(232, 176, 96), new Color(208, 122, 130), new Color(150, 200, 110), new Color(147, 160, 210)};

    static void timeline(Graphics2D g, List<Sample> s, Rhythm rh, double start, double len, Dance dance, int x, int y, int w, int h) {
        g.setColor(PANEL);
        g.fillRect(x, y, w, h);
        int lane = (h - 6) / 4;
        String[] names = {"STÜCKE DER AUFNAHMEN · SCHLÄGE", "FUSS LINKS (TÜRKIS) · RECHTS (BERNSTEIN)", "FINGER ÜBER DEM FELL · SCHLÄGE", ""};
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        for (int k = 0; k < 3; k++) { g.setColor(MUTED); g.drawString(names[k], x + 4, y + k * lane + 11); g.setColor(LINE); g.drawLine(x, y + (k + 1) * lane, x + w, y + (k + 1) * lane); }
        java.util.function.DoubleUnaryOperator X = t -> x + t / len * w;
        // Stücke
        int prev = -2, from = 0;
        for (int i = 0; i <= s.size(); i++) {
            int c = i < s.size() ? s.get(i).clip() : -3;
            if (c != prev) {
                if (prev >= 0) {
                    int a = (int) X.applyAsDouble(s.get(from).t()), e = (int) X.applyAsDouble(s.get(i - 1).t());
                    g.setColor(new Color(CLIP[prev % CLIP.length].getRed(), CLIP[prev % CLIP.length].getGreen(), CLIP[prev % CLIP.length].getBlue(), 90));
                    g.fillRect(a, y + 14, Math.max(1, e - a), lane - 16);
                    g.setColor(INK);
                    String nm = dance.clips.get(prev).m.name;
                    if (e - a > g.getFontMetrics().stringWidth(nm) + 4) g.drawString(nm, a + 3, y + lane - 5);
                }
                prev = c; from = i;
            }
        }
        // Schläge
        for (int i = 0; i < rh.beats.length; i++) {
            double t = rh.beats[i] - start;
            if (t < 0 || t > len) continue;
            int px = (int) X.applyAsDouble(t);
            boolean one = rh.isDownbeat(i);
            g.setColor(one ? AMBER : new Color(232, 176, 96, 110));
            g.drawLine(px, y + lane - (one ? 14 : 7), px, y + lane);
            g.drawLine(px, y + 3 * lane - (one ? 14 : 7), px, y + 3 * lane);
        }
        double fmax = 0.3;
        curve(g, s, X, y + lane, lane, smp -> smp.footL() / fmax, TEAL);
        curve(g, s, X, y + lane, lane, smp -> smp.footR() / fmax, AMBER);
        curve(g, s, X, y + 2 * lane, lane, smp -> Double.isNaN(smp.hand()) ? -1 : smp.hand() / 0.2, new Color(208, 122, 130));
        g.setColor(MUTED);
        for (double t = 0; t <= len; t += 5) {
            int px = (int) X.applyAsDouble(t);
            g.drawLine(px, y + h - 4, px, y + h);
            g.drawString(String.format(Locale.GERMANY, "%.0f s", t), px + 2, y + h + 11);
        }
    }

    static void curve(Graphics2D g, List<Sample> s, java.util.function.DoubleUnaryOperator X, int y, int lane, java.util.function.ToDoubleFunction<Sample> v, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.3f));
        int px0 = -1, py0 = 0;
        for (Sample smp : s) {
            double val = v.applyAsDouble(smp);
            if (val < 0) { px0 = -1; continue; }
            int px = (int) X.applyAsDouble(smp.t());
            int py = y + lane - 3 - (int) Math.round(Math.max(0, Math.min(1, val)) * (lane - 16));
            if (px0 >= 0 && px != px0) g.drawLine(px0, py0, px, py);
            if (px != px0) { px0 = px; py0 = py; }
        }
        g.setStroke(new BasicStroke(1f));
    }

    static void caption(Graphics2D g, String s, int x, int y, Color c) { AvatarStudio.caption(g, s, x, y, c); }
}
