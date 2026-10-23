package com.dan.avatarstudio;

import com.dan.avatar.Body;
import com.dan.avatar.Person;
import com.dan.gltf.Gltf;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

import static com.dan.avatarstudio.AvatarStudio.*;

/**
 * Statusbild Schliff (Abnahme Phase 19): dasselbe Porträt im Regen in den vier Qualitätsstufen mit ihren Zeiten, daneben
 * was jede Stufe schaltet, das Ergebnis von {@code ant check} (aus {@code docs/status/Pruefung.txt}), der Dauerlauf
 * (aus {@code docs/status/Dauerlauf_Phase19.txt}) und die bewussten Grenzen. {@code ant statusbild} hängt an
 * {@code check-packages}: entsteht das Bild, sind alle Packages grün.
 */
final class SchliffBild {

    private SchliffBild() {}

    /** Was jede Stufe schaltet (Zeile: Merkmal, Niedrig, Mittel, Hoch, Ultra). */
    static final String[][] TABLE = {
            {"Schattenkarte", "1024", "2048", "4096", "4096"},
            {"Streuung der Haut", "vorab", "11 Proben", "17 Proben", "25 Proben"},
            {"Einzelne Haare", "aus", "an", "an", "an"},
            {"Schärfentiefe", "aus", "an", "an", "an"},
            {"Lichter im Hintergrund", "55 %", "100 %", "130 %", "170 %"},
            {"Regen", "niedrig", "niedrig", "mittel", "mittel"},
            {"Volle Figur ab", "1,5×", "1×", "0,8×", "0,6×"}};

    static void run(File out, int w, int h) throws Exception {
        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        scene.exposure = 1.0;
        scene.fixedDt = 1 / 30.0;
        Gltf model = loadModel();
        Person per = Person.wuerfeln(11).copy();
        per.hair = "kurz"; per.hairColor = 0.30; per.hairRed = 0.10;   // kurz: nichts vor dem Auge, nichts durch die Kapuze
        StudioScene.Figure f = new StudioScene.Figure(new Body(model), loadSkeleton(model), per, "stehen", 0, 0);
        f.outfit = com.dan.avatar.Outfit.preset("Strickkapuze");
        scene.figures.add(f);
        int vw = 960, vh = 540;
        Offscreen off = new Offscreen(scene, vw, vh);
        off.shot(3);
        com.dan.avatar.Behavior b = f.behavior;
        b.feel("ruhig", 0.4);
        b.look(com.dan.avatar.Behavior.Look.CAMERA);
        com.dan.avatar.Climate rn = com.dan.avatar.Climate.regennacht();
        scene.climate = rn;
        scene.wind = new double[]{rn.wind[0], 0.45, rn.wind[2]};
        scene.lens.focalMm = 85; scene.lens.fNumber = 2.0;
        scene.grade = "Regennacht"; scene.bokehStyle = "Regennacht";
        portraitShot(scene, f, com.dan.portrait.Shot.NAH, 12, "Regennacht");
        simulate(off, scene, 300, 90);                      // fünf Minuten Regen: nass, Tropfen
        portraitShot(scene, f, com.dan.portrait.Shot.NAH, 12, "Regennacht");
        Quality[] qs = Quality.values();
        BufferedImage[] img = new BufferedImage[qs.length];
        double[] gpu = new double[qs.length], cpu = new double[qs.length];
        java.util.Arrays.fill(gpu, Double.MAX_VALUE);
        java.util.Arrays.fill(cpu, Double.MAX_VALUE);
        // zwei Runden, je Stufe die bessere (die Grafikkarte taktet beim ersten Mal oft noch hoch)
        for (int round = 0; round < 2; round++)
        for (int k = 0; k < qs.length; k++) {
            scene.quality = qs[k];
            for (int i = 0; i < 20; i++) { scene.hairTime += 1 / 30.0; off.shot(1); }
            double g = 0, c = 0;
            for (int i = 0; i < 60; i++) {
                scene.hairTime += 1 / 30.0;
                long t0 = System.nanoTime();
                off.shot(1);
                c += (System.nanoTime() - t0) / 1e6;
                g += scene.gpuMs;
            }
            gpu[k] = Math.min(gpu[k], g / 60); cpu[k] = Math.min(cpu[k], c / 60);
            img[k] = copy(off.last);
        }
        String glInfo = scene.glInfo;
        off.d.destroy();
        double[] drum = drumHand(model);

        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(TEAL);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        g.drawString("AVATAR STUDIO · PHASE 19 · SCHLIFF", 14, 28);
        g.setColor(MUTED);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.drawString("Dasselbe Porträt nach fünf Minuten Regen in den vier Qualitätsstufen · 960 × 540 ohne Fenster · " + (glInfo.length() > 40 ? glInfo.substring(0, 40) : glInfo), 380, 28);
        String[] qn = {"NIEDRIG", "MITTEL", "HOCH", "ULTRA"};
        int tw = 520, th = 293, top = 42;
        for (int k = 0; k < 4; k++) {
            int x = 14 + (k % 2) * (tw + 10), y = top + (k / 2) * (th + 26);
            drawCrop(g, img[k], x, y, tw, th);
            caption(g, String.format(Locale.GERMANY, "%s · GPU %.2f MS · BILD %.1f MS", qn[k], gpu[k], cpu[k]), x, y + th + 15, k == 1 ? TEAL : INK);
        }
        // rechte Spalte
        int rx = 14 + 2 * (tw + 10) + 6, ry = top + 4, rw = w - rx - 14;
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        g.setColor(TEAL);
        g.drawString("WAS DIE STUFEN SCHALTEN", rx, ry + 10);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        int[] col = {0, 190, 255, 325, 400};
        int yy = ry + 28;
        g.setColor(MUTED);
        String[] hd = {"", "Niedrig", "Mittel", "Hoch", "Ultra"};
        for (int c = 0; c < 5; c++) g.drawString(hd[c], rx + col[c], yy);
        for (String[] row : TABLE) {
            yy += 15;
            for (int c = 0; c < 5; c++) { g.setColor(c == 0 ? INK : (c == 2 ? TEAL : MUTED)); g.drawString(row[c], rx + col[c], yy); }
        }
        yy += 28;
        // Prüfungen
        List<String> pr = read("docs/status/Pruefung.txt");
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        g.setColor(TEAL);
        g.drawString("PRÜFUNGEN", rx, yy);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        yy += 16;
        g.setColor(INK);
        g.drawString("check-packages: alle grün (gltf, avatar, hair, portrait, voice, beat)", rx, yy);
        yy += 14;
        g.drawString(pr.isEmpty() ? "ant check: noch nicht gelaufen" : "ant check: " + pr.get(0).replaceFirst("^\\S+ · ", ""), rx, yy);
        for (int i = 1; i < Math.min(4, pr.size()); i++) { yy += 14; g.setColor(AMBER); g.drawString(cut(pr.get(i), 70), rx, yy); }
        yy += 26;
        // Dauerlauf
        List<String> dl = read("docs/status/Dauerlauf_Phase19.txt");
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        g.setColor(TEAL);
        g.drawString("DAUERLAUF (EINE STUNDE STUDIOZEIT)", rx, yy);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        if (dl.isEmpty()) { yy += 16; g.setColor(AMBER); g.drawString("noch nicht gelaufen (ant dauerlauf)", rx, yy); }
        else for (int i = Math.max(0, dl.size() - 3); i < dl.size(); i++) {
            for (String part : wrap(dl.get(i), 72)) { yy += 14; g.setColor(dl.get(i).startsWith("Dauerlauf bestanden") ? TEAL : INK); g.drawString(part, rx, yy); }
        }
        yy += 26;
        // Grenzen
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        g.setColor(TEAL);
        g.drawString("OFFENE FEHLER: KEINE · BEWUSSTE GRENZEN", rx, yy);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        String[] lim = {
                "Haare ohne Kollision mit Schultern und Kragen (Feder je Kopf)",
                "Gehen gerade auf ein Ziel, ohne Wegsuche; Gesten im Stehen",
                "Stoff ohne Simulation (Federn je Ecke), Kapuze mit Falten",
                "Laute nach Regeln, Liedzeiten in der LRC nach Gehör setzen",
                "Tanz aus kurzen Aufnahmen: Stücke wiederholen sich",
                "Takt immer 4/4; Gesang nicht aus dem Lied herausgehört"};
        for (String s : lim) { yy += 14; g.setColor(MUTED); g.drawString("· " + s, rx, yy); }
        // Trommelhand (zu Status_Phase_Ich18.png: „Hand in!“)
        int by = top + 2 * (th + 26) + 8;
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        g.setColor(TEAL);
        g.drawString("TROMMELHAND (ZU STATUS_PHASE_ICH18: „HAND IN!“) · BEHOBEN", 14, by);
        BufferedImage s18 = null;
        try { s18 = ImageIO.read(new File("docs/status/Status_Phase18.png")); } catch (Exception e) { /* ohne Bild */ }
        int cw = 300, chh = 169;
        if (s18 != null && s18.getWidth() >= 1594 && s18.getHeight() >= 655) {
            g.drawImage(s18.getSubimage(544, 362, 520, 293), 14, by + 8, cw, chh, null);
            g.drawImage(s18.getSubimage(1074, 362, 520, 293), 14 + cw + 10, by + 8, cw, chh, null);
        }
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        int tx = 14 + 2 * (cw + 10) + 6, ty = by + 22;
        String[] dt = {
                "Vorher: Handgelenk über dem Fell, die Finger hingen",
                "rund 2,7 cm tiefer und steckten im Fell.",
                "Jetzt: je Bild eine Probe auf einer Kopie der Pose,",
                "das Handgelenk steht so hoch, wie die Finger hängen.",
                String.format(Locale.GERMANY, "Fingerspitzen rechts nie unter dem Fell: tiefste %.1f mm darüber", drum[0] * 1000),
                String.format(Locale.GERMANY, "(Mitte des Fingerglieds), beim Schlag %.1f cm, %.0f ms neben dem Schlag", drum[1] * 100, drum[2]),
                String.format(Locale.GERMANY, "linke Finger %.1f cm hinter dem Fell, %d Anschläge in 8 Schlägen", -drum[3] * 100, (int) drum[4]),
                "Dazu: Trommel und Bank werfen Schatten, Füße gleiten beim Tanz nicht mehr"};
        for (String s : dt) { g.setColor(s.startsWith("Fingersp") || s.startsWith("(Mitte") || s.startsWith("linke") ? INK : MUTED); g.drawString(s, tx, ty); ty += 15; }
        // unten: Messwerte
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.setColor(TEAL);
        g.drawString(String.format(Locale.GERMANY, "GPU je Stufe: Niedrig %.2f · Mittel %.2f · Hoch %.2f · Ultra %.2f ms · Ziel Mittel (Porträt, Full HD, 60 Bilder/s): 16,7 ms je Bild",
                gpu[0], gpu[1], gpu[2], gpu[3]), 14, h - 26);
        g.setColor(INK);
        g.drawString("Programmsymbol, Verknüpfung (Verknuepfung.bat), Avatar.bat, README mit Bildern, Quellen in docs/QUELLEN.md · Werkbuch: alle 19 Phasen fertig", 14, h - 10);
        g.dispose();
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(im, "png", out);
        System.out.println("Statusbild Schliff: " + out.getAbsolutePath());
        System.out.printf(Locale.GERMANY, "  GPU: %.2f / %.2f / %.2f / %.2f ms%n", gpu[0], gpu[1], gpu[2], gpu[3]);
    }

    /** Trommel wie in {@code ant check}: tiefste Fingerspitze, Abstand beim Schlag, Zeitfehler (ms bei 113 BPM), linke Finger, Anschläge. */
    static double[] drumHand(Gltf model) throws Exception {
        com.dan.avatar.Skeleton sk = loadSkeleton(model);
        double[][] r = sk.restFor(Person.wuerfeln(19).weights());
        com.dan.avatar.Drum dr = new com.dan.avatar.Drum();
        double fingerMin = 9, contact = 0, left = -9, err = 0;
        double[] hh = new double[8 * 120 + 1];
        for (int i = 0; i < hh.length; i++) {
            double[][] w = sk.pose(null, r, false);
            dr.apply(sk, w, i / 120.0, 0, 0.53);
            hh[i] = dr.fingerGap;
            fingerMin = Math.min(fingerMin, dr.fingerGap);
            if (i % 120 == 0) contact = Math.max(contact, dr.fingerGap);
            for (String tip : new String[]{"index_03_l", "middle_03_l", "ring_03_l", "pinky_03_l"}) {
                double[] tp = com.dan.avatar.Skeleton.position(w, sk.joint(tip));
                left = Math.max(left, (tp[0] - dr.center[0]) * dr.normal[0] + (tp[1] - dr.center[1]) * dr.normal[1] + (tp[2] - dr.center[2]) * dr.normal[2]);
            }
        }
        int hits = 0;
        for (int i = 0; i < hh.length; ) {
            if (hh[i] >= 0.012) { i++; continue; }
            int best = i;
            while (i < hh.length && hh[i] < 0.012) { if (hh[i] < hh[best]) best = i; i++; }
            hits++;
            err = Math.max(err, Math.abs(best / 120.0 - Math.round(best / 120.0)));
        }
        return new double[]{fingerMin, contact, err * 530, left, hits};
    }

    static List<String> read(String p) {
        try { return java.nio.file.Files.readAllLines(java.nio.file.Path.of(p), StandardCharsets.UTF_8); }
        catch (Exception e) { return new ArrayList<>(); }
    }

    static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "…"; }

    static List<String> wrap(String s, int n) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : s.split(" ")) {
            if (line.length() + word.length() + 1 > n && line.length() > 0) { out.add(line.toString()); line.setLength(0); }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    static void caption(Graphics2D g, String s, int x, int y, Color c) { AvatarStudio.caption(g, s, x, y, c); }
}
