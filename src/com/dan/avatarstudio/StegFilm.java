package com.dan.avatarstudio;

import com.dan.avatar.Regie;
import com.dan.gltf.Gltf;

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
 * Statusbild der Regie (Abnahme Phase 13): das Regiebuch „Am Steg“ läuft ohne Fenster im Kino durch
 * (Vorspann, Ankommen, Kapuze ab, Blick zum Wasser, zur Kamera, Abgang, Abspann) und wird als Video
 * aufgezeichnet. Das Statusbild zeigt sechs Augenblicke, den Ablauf der Zeilen mit ihren Zeiten und die
 * Messwerte (Ankunft, Drehung, Kapuze, Blick, Fehler der Regie, GPU).
 */
final class StegFilm {

    private StegFilm() {}

    /** Augenblicke: Zeile (Anfang), Verzögerung (s), Beschriftung. */
    static final Object[][] MOMENTS = {
            {"mara.gehe Steg_Ende", 2.0, "ANKOMMEN"},
            {"kamera Amerikanisch", 3.0, "AUF DEM STEG"},
            {"mara.geste Kapuze_ab", 0.95, "KAPUZE AB"},
            {"kamera Nah mara winkel=-30", 3.0, "BLICK ZUM WASSER"},
            {"mara.sage", 1.0, "ZUR KAMERA"},
            {"kamera Halbtotale mara winkel=165", 3.0, "ABGANG"}};

    static void run(File out, File video, int w, int h) throws Exception {
        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        scene.exposure = 1.0;
        scene.fixedDt = 1 / 30.0;
        Gltf model = loadModel();
        Regie r = Regie.resource(AvatarStudio.class, "regie/am_steg.txt");
        StudioStage stage = new StudioStage(scene, model);
        int vw = 960, vh = 540;
        Offscreen off = new Offscreen(scene, vw, vh);
        scene.startFilm(r, stage);
        // Zeilen mit ihren Zeiten mitschreiben
        List<String> log = new ArrayList<>();
        List<double[]> lineT = new ArrayList<>();
        List<String> lineS = new ArrayList<>();
        java.util.function.Consumer<Regie.Line> was = r.onLine;
        r.onLine = l -> {
            was.accept(l);
            lineT.add(new double[]{scene.filmClock});
            lineS.add(l.toString());
        };
        MjpegAvi avi = video == null ? null : new MjpegAvi(video, vw, vh, 30, 0.85f);
        BufferedImage[] snaps = new BufferedImage[MOMENTS.length];
        double[] snapAt = new double[MOMENTS.length];
        java.util.Arrays.fill(snapAt, -1);
        double t = 0, gpu = 0;
        int frames = 0;
        // Messwerte
        double arriveErr = -1, turnErr = -1, hoodOffAt = -1, hoodOnAt = -1, lookWaterAt = -1, lookCamAt = -1, lineWater = -1, lineCam = -1;
        String hoodBefore = null;
        com.dan.avatar.Snapshot last = null;
        try {
            while (t < 240 && (scene.regie != null || scene.kino.laeuft())) {
                scene.hairTime = t;
                off.shot(1);
                t += 1 / 30.0;
                frames++;
                if (frames > 10) gpu += scene.gpuMs;
                if (avi != null) avi.add(off.last);
                // Augenblicke
                for (int i = 0; i < MOMENTS.length; i++) {
                    if (snaps[i] != null) continue;
                    if (snapAt[i] < 0) {
                        for (int k = 0; k < lineS.size(); k++) if (lineS.get(k).startsWith((String) MOMENTS[i][0])) { snapAt[i] = lineT.get(k)[0] + (double) MOMENTS[i][1]; break; }
                    }
                    if (snapAt[i] >= 0 && scene.filmClock >= snapAt[i]) snaps[i] = copy(off.last);
                }
                // Messen an der Person
                StudioScene.Figure f = stage.created.isEmpty() ? null : stage.created.get(0);
                if (f != null && f.actor != null && f.motion != null) {
                    com.dan.avatar.Snapshot s = f.snapshot(scene.world);
                    if (hoodBefore == null) hoodBefore = s.hoodUp() ? "auf" : "ab";
                    if (last != null && last.hoodUp() && !s.hoodUp() && hoodOffAt < 0) hoodOffAt = scene.filmClock;
                    if (last != null && !last.hoodUp() && s.hoodUp() && hoodOnAt < 0) hoodOnAt = scene.filmClock;
                    for (int k = 0; k < lineS.size(); k++) {
                        if (lineS.get(k).startsWith("mara.blick Wasser") && lineWater < 0) lineWater = lineT.get(k)[0];
                        if (lineS.get(k).startsWith("mara.blick Kamera") && lineCam < 0) lineCam = lineT.get(k)[0];
                    }
                    if (lineWater >= 0 && lookWaterAt < 0 && f.actor.done("blick") && scene.filmClock > lineWater + 0.2) lookWaterAt = scene.filmClock - lineWater;
                    if (lineCam >= 0 && lookCamAt < 0 && f.actor.done("blick") && scene.filmClock > lineCam + 0.2) lookCamAt = scene.filmClock - lineCam;
                    // Ankunft am Steg_Ende: wenn GEHEN dort endet
                    if (arriveErr < 0 && last != null && last.doing().equals("GEHEN") && !s.doing().equals("GEHEN") && s.z() > 0) {
                        double[] p = scene.world.place("Steg_Ende");
                        arriveErr = Math.hypot(s.x() - p[0], s.z() - p[2]);
                    }
                    if (turnErr < 0 && last != null && last.doing().equals("DREHEN") && !s.doing().equals("DREHEN") && s.z() > 0) {
                        double[] p = scene.world.place("Wasser");
                        double want = Math.atan2(p[0] - s.x(), p[2] - s.z());
                        turnErr = Math.abs(Math.toDegrees(Math.atan2(Math.sin(want - s.yaw()), Math.cos(want - s.yaw()))));
                    }
                    last = s;
                }
            }
        } finally { if (avi != null) avi.close(); }
        for (int i = 0; i < snaps.length; i++) if (snaps[i] == null) snaps[i] = copy(off.last);
        StudioScene.Figure f = stage.created.isEmpty() ? null : stage.created.get(0);
        com.dan.avatar.Motion m = f == null ? null : f.motion;
        String glInfo = scene.glInfo;
        off.d.destroy();

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(TEAL);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        g.drawString("AVATAR STUDIO · PHASE 13 · SCHNITTSTELLE UND REGIE", 14, 28);
        g.setColor(MUTED);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.drawString("Regiebuch „Am Steg“ im Kino: Ankommen, Kapuze ab, Blick zum Wasser, zur Kamera, Abgang · ohne Fenster, " + vw + " × " + vh, 520, 28);
        int tw = 520, th = 293, top = 42;
        for (int i = 0; i < 6; i++) {
            int x = 14 + (i % 3) * (tw + 10), y = top + (i / 3) * (th + 26);
            drawCrop(g, snaps[i], x, y, tw, th);
            caption(g, String.format(Locale.GERMANY, "%s · %.1f S", MOMENTS[i][2], snapAt[i] < 0 ? 0 : snapAt[i]), x, y + th + 15, i == 0 ? TEAL : INK);
        }
        int py = top + 2 * (th + 26) + 8;
        // Ablauf der Zeilen
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        caption(g, "ABLAUF DER REGIE (ZEIT DES FILMS)", 14, py, MUTED);
        int ly = py + 16, col = 0;
        for (int k = 0; k < lineS.size(); k++) {
            String s = String.format(Locale.GERMANY, "%5.1f  %s", lineT.get(k)[0], lineS.get(k));
            if (s.length() > 44) s = s.substring(0, 44);
            g.setColor(lineS.get(k).startsWith("warte") ? MUTED : (lineS.get(k).startsWith("mara") ? AMBER : TEAL));
            g.drawString(s, 14 + col * 300, ly);
            ly += 14;
            if (ly > h - 12) { ly = py + 16; col++; if (col > 2) break; }
        }
        // Zahlen
        int tx = 14 + 3 * 300 + 10;
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.GERMANY, "Film %.1f s · %d Zeilen · %d Bilder · GPU %.2f ms im Mittel", t, r.lines.size(), frames, gpu / Math.max(1, frames - 10)));
        lines.add(String.format(Locale.GERMANY, "Ankunft am Steg_Ende: %.1f cm daneben", arriveErr * 100));
        lines.add(String.format(Locale.GERMANY, "Drehung zum Wasser: %.1f° daneben", turnErr));
        lines.add(String.format(Locale.GERMANY, "Kapuze %s · ab bei %.1f s · wieder auf bei %.1f s", hoodBefore, hoodOffAt, hoodOnAt));
        lines.add(String.format(Locale.GERMANY, "Blick am Ziel: Wasser nach %.2f s, Kamera nach %.2f s", lookWaterAt, lookCamAt));
        if (m != null) lines.add(String.format(Locale.GERMANY, "%d Schritte · Gleiten der tragenden Füße höchstens %.1f mm/s", m.steps.size(), m.slideMax * 1000));
        if (f != null && f.behavior != null) lines.add(String.format(Locale.GERMANY, "%d Lidschläge · Nässe Gesicht %.0f %% · Haar %.0f %%", f.behavior.blinks.size(),
                f.wetness.wet(com.dan.avatar.Wetness.FACE) * 100, f.wetness.wet(com.dan.avatar.Wetness.HAIR) * 100));
        lines.add("Fehler der Regie: " + (r.errors.isEmpty() ? "keine" : r.errors.size() + " · " + r.errors.get(0)));
        lines.add("Steg: 4 Laternen, Boje, Wasser mit Regenringen · Kino: Vorspann, Schnitte, Untertitel, Abspann");
        lines.add(video == null ? "" : "Video: " + video.getName());
        lines.add(glInfo.length() > 60 ? glInfo.substring(0, 60) : glInfo);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        for (int i = 0; i < lines.size(); i++) { g.setColor(i == 0 ? TEAL : INK); g.drawString(lines.get(i), tx, py + 16 + i * 18); }
        g.dispose();
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Statusbild Regie: " + out.getAbsolutePath());
        for (String s : lines) System.out.println("  " + s);
        for (String e : r.errors) System.out.println("  FEHLER " + e);
    }

    static void caption(Graphics2D g, String s, int x, int y, Color c) { AvatarStudio.caption(g, s, x, y, c); }
}
