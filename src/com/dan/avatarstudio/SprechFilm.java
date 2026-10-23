package com.dan.avatarstudio;

import com.dan.avatar.Body;
import com.dan.avatar.Person;
import com.dan.avatar.Skeleton;
import com.dan.gltf.Gltf;
import com.dan.voice.Audio;
import com.dan.voice.G2P;
import com.dan.voice.Speech;
import com.dan.voice.Viseme;

import java.awt.BasicStroke;
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
 * Statusbild und Video Sprechen (Abnahme Phase 16): ein Satz aus Text (stumm, Dauern aus den Regeln) und ein Satz zu
 * einer WAV-Datei (Text an den Ton angelegt), ohne Fenster. Das Bild zeigt je Satz den Mund groß bei „B“, „O“, „F“ und
 * „I“ (Mitte des Lauts), darunter die Spur der Mundbilder über der Zeit, beim Ton mit Wellenform. Das Video (AVI mit
 * Ton) zeigt beide Sätze nacheinander mit Untertitel.
 */
final class SprechFilm {

    private SprechFilm() {}

    /** Satz aus Text: enthält b, o, f, i. */
    static final String TEXT = "Bei Sturm bleibt das Fischerboot im Hafen.";
    static final Viseme[] WANT = {Viseme.PP, Viseme.O, Viseme.FF, Viseme.I};
    static final String[] NAME = {"B", "O", "F", "I"};
    static final int FPS = 30;

    /** Eine Nahaufnahme: Bild, Zeit, Laut, Mundbild an der Stelle, Öffnung. */
    record Close(BufferedImage img, double t, String sym, Viseme dom, double open) { }

    /** Aufnahme wählen: eine eigene (WAV + TXT, nicht hedda_…) vor der Sprachausgabe. */
    static File pickWav(File dir) {
        File[] all = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".wav"));
        if (all == null) return null;
        java.util.Arrays.sort(all);
        File hedda = null;
        for (File f : all) {
            if (!txtOf(f).isFile()) continue;
            if (!f.getName().toLowerCase(Locale.ROOT).startsWith("hedda_")) return f;
            if (f.getName().equals("hedda_fischerboot.wav") || hedda == null) hedda = f;
        }
        return hedda;
    }

    static File txtOf(File wav) { return new File(wav.getParentFile(), wav.getName().replaceFirst("(?i)\\.wav$", ".txt")); }

    static void run(File out, File video, int w, int h) throws Exception {
        File wf = pickWav(new File("daten/sprache"));
        if (wf == null) throw new IllegalStateException("keine WAV mit TXT in daten/sprache");
        String wtext = java.nio.file.Files.readString(txtOf(wf).toPath(), StandardCharsets.UTF_8).trim();
        Audio au = Audio.read(wf);
        long ta = System.nanoTime();
        Speech sw = au.speech(wtext);
        double alignMs = (System.nanoTime() - ta) / 1e6;
        Speech st = Speech.fromText(TEXT, 1.0);

        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        scene.exposure = 0.85;
        scene.fixedDt = 1.0 / FPS;
        Gltf model = loadModel();
        Skeleton sk = loadSkeleton(model);
        Person per = Person.wuerfeln(11).copy();
        per.hair = "lang"; per.hairColor = 0.30; per.hairRed = 0.10;
        StudioScene.Figure f = new StudioScene.Figure(new Body(model), sk, per, "stehen", 0, 0);
        f.pupilInstant = false;
        scene.figures.add(f);
        int vw = 960, vh = 540;
        Offscreen off = new Offscreen(scene, vw, vh);
        scene.hairTime = 0;
        off.shot(3);
        com.dan.avatar.Behavior b = f.behavior;
        double[] head = headOf(f);
        LightRig.apply(scene.light, "Porträt", head);
        b.feel("ruhig", 0.6);
        b.look(com.dan.avatar.Behavior.Look.CAMERA);

        // ---- Video: beide Sätze nacheinander, der zweite mit Ton
        halfClose(scene, head);
        for (int i = 0; i < 45; i++) { scene.hairTime = b.time(); off.shot(1); }
        short[] pcm = au.pcm16();
        MjpegAvi avi = video == null ? null : new MjpegAvi(video, vw, vh, FPS, 0.85f, au.rate);
        int frames = 0, blinksTalk = 0, blinksQuiet = 0;
        double talkS = 0, quietS = 0, gpu = 0;
        int inhales = 0;
        try {
            Object[][] parts = {{st, null, "SATZ AUS TEXT (STUMM)"}, {sw, pcm, "SATZ ZUR WAV-DATEI " + wf.getName().toUpperCase(Locale.ROOT)}};
            for (Object[] part : parts) {
                Speech s = (Speech) part[0];
                short[] tone = (short[]) part[1];
                f.speak(s, null);
                f.speechT = -1.0 / FPS;                    // Bild j zeigt die Spur bei j / FPS (wie der Ton)
                double len = Math.max(s.duration, tone == null ? 0 : au.seconds()) + 0.6;
                int n = (int) Math.ceil(len * FPS);
                double lastBreath = b.breath();
                for (int j = 0; j < n; j++) {
                    scene.hairTime = b.time();
                    int nb0 = b.blinks.size();
                    BufferedImage fr = off.shot(1);
                    frames++;
                    gpu += scene.gpuMs;
                    double t = j / (double) FPS;
                    boolean talking = s.talking(t);
                    if (talking) { talkS += 1.0 / FPS; blinksTalk += b.blinks.size() - nb0; }
                    if (lastBreath < 0.02 && b.breath() >= 0.02 && b.breath() > lastBreath && t < 0.6) inhales++;
                    lastBreath = b.breath();
                    if (avi != null) {
                        avi.add(overlay(fr, (String) part[2], s, t, talking));
                        if (tone != null) {
                            int a0 = (int) Math.round(j * (double) au.rate / FPS), a1 = (int) Math.round((j + 1) * (double) au.rate / FPS);
                            a0 = Math.min(a0, pcm.length); a1 = Math.min(a1, pcm.length);
                            if (a1 > a0) avi.addAudio(pcm, a0, a1 - a0);
                            else avi.addAudio(new short[au.rate / FPS], 0, au.rate / FPS);
                        } else avi.addAudio(new short[au.rate / FPS], 0, au.rate / FPS);
                    }
                }
                f.hush();
                // Pause zwischen den Sätzen
                for (int j = 0; j < FPS / 2; j++) {
                    scene.hairTime = b.time();
                    int nb0 = b.blinks.size();
                    BufferedImage fr = off.shot(1);
                    frames++;
                    quietS += 1.0 / FPS; blinksQuiet += b.blinks.size() - nb0;
                    if (avi != null) { avi.add(overlay(fr, (String) part[2], s, s.duration + 1, false)); avi.addAudio(new short[au.rate / FPS], 0, au.rate / FPS); }
                }
            }
        } finally { if (avi != null) avi.close(); }
        // Lidschläge: 45 s still gegen 45 s Sprechen (Sätze hintereinander, kurze Pausen), gleiche Person
        blinksQuiet = 0; quietS = 0; blinksTalk = 0; talkS = 0;
        for (int j = 0; j < 45 * FPS; j++) {
            int nb0 = b.blinks.size();
            scene.hairTime = b.time();
            off.shot(1);
            quietS += 1.0 / FPS; blinksQuiet += b.blinks.size() - nb0;
        }
        String[] more = {TEXT, wtext, "Heute Nacht kommt keiner mehr raus.", "Jede Stunde geht er einmal um die Laterne.", "Bald brennt das Feuer, ihr Schiffer!"};
        for (int k = 0; talkS < 45; k++) {
            Speech s = Speech.fromText(more[k % more.length], 1.0);
            f.speak(s, null);
            int n = (int) Math.ceil((s.duration + 0.3) * FPS);
            for (int j = 0; j < n; j++) {
                int nb0 = b.blinks.size();
                scene.hairTime = b.time();
                off.shot(1);
                talkS += 1.0 / FPS; blinksTalk += b.blinks.size() - nb0;
            }
        }
        f.hush();

        // ---- Nahaufnahmen des Munds bei B, O, F, I (Spur angehalten in der Mitte des Lauts)
        b.alive = false;
        Close[] ct = closeUps(scene, off, f, st, head);
        Close[] cw = closeUps(scene, off, f, sw, head);
        f.hush();
        String glInfo = scene.glInfo;
        off.d.destroy();

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
        g.drawString("AVATAR STUDIO · PHASE 16 · SPRECHEN", 14, 28);
        g.setColor(MUTED);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.drawString("Viseme aus deutschem Text und aus einer Tonspur · Mund groß in der Mitte des Lauts · com.dan.voice", 400, 28);
        int tw = (w - 14 * 2 - 3 * 10) / 4, th = (int) Math.round(tw * 0.5), top = 42;
        int y1 = top, y2 = top + th + 74 + 150;
        drawRow(g, ct, "TEXT", y1, tw, th);
        int tl1 = y1 + th + 30;
        timeline(g, st, null, 14, tl1, w - 28, 140, "„" + TEXT + "“ · " + G2P.show(G2P.phonemes(TEXT)).replace(" |", ""));
        drawRow(g, cw, "WAV", y2, tw, th);
        int tl2 = y2 + th + 30;
        timeline(g, sw, au, 14, tl2, w - 28, 140, "„" + wtext + "“ an " + wf.getName() + String.format(Locale.GERMANY, " · %.2f s, %d Hz · angelegt in %.0f ms", au.seconds(), au.rate, alignMs));
        // Zahlen
        List<String> lines = new ArrayList<>();
        int okT = 0, okW = 0;
        for (int i = 0; i < 4; i++) { if (ct[i] != null && ct[i].dom() == WANT[i]) okT++; if (cw[i] != null && cw[i].dom() == WANT[i]) okW++; }
        lines.add(String.format(Locale.GERMANY, "Mundbild passt: Text %d/4, Ton %d/4 · Video %d Bilder, %.1f s, GPU %.2f ms", okT, okW, frames, frames / (double) FPS, gpu / Math.max(1, frames)));
        lines.add(String.format(Locale.GERMANY, "Lidschläge je Minute: beim Sprechen %.1f (%d in %.0f s), still %.1f (%d in %.0f s) · Atemzug vor jedem Satz im Video: %d von 2",
                blinksTalk / Math.max(1e-6, talkS) * 60, blinksTalk, talkS, blinksQuiet / Math.max(1e-6, quietS) * 60, blinksQuiet, quietS, inhales));
        lines.add(video == null ? glInfo : "Video mit Ton: " + video.getName() + " · " + (glInfo.length() > 50 ? glInfo.substring(0, 50) : glInfo));
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        for (int i = 0; i < lines.size(); i++) { g.setColor(i == 0 ? TEAL : INK); g.drawString(lines.get(i), 14, h - 46 + i * 16); }
        g.dispose();
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Statusbild Sprechen: " + out.getAbsolutePath());
        System.out.print(st.describe().replace("\n", " · "));
        System.out.println();
        System.out.print(sw.describe().replace("\n", " · "));
        System.out.println();
        for (Close[] cs : new Close[][]{ct, cw})
            for (int i = 0; i < 4; i++) if (cs[i] != null)
                System.out.printf(Locale.GERMANY, "  %s %s bei %.2f s: %s, Öffnung %.2f%n", NAME[i], cs[i].sym(), cs[i].t(), cs[i].dom(), cs[i].open());
        for (String s : lines) System.out.println("  " + s);
    }

    /** Kamera halbnah auf das Gesicht (Video). */
    static void halfClose(StudioScene scene, double[] head) {
        scene.cam.tx = head[0]; scene.cam.ty = head[1] - 0.06; scene.cam.tz = head[2];
        scene.cam.yawDeg = 12; scene.cam.pitchDeg = 2; scene.cam.dist = 0.72; scene.cam.fovyDeg = 28;
    }

    /** Je B, O, F, I der deutlichste Laut (betont, am längsten) und der Mund in seiner Mitte. */
    static Close[] closeUps(StudioScene scene, Offscreen off, StudioScene.Figure f, Speech s, double[] head) {
        Close[] out = new Close[4];
        scene.cam.tx = head[0]; scene.cam.ty = head[1] - 0.10; scene.cam.tz = head[2];
        scene.cam.yawDeg = 8; scene.cam.pitchDeg = 0; scene.cam.dist = 0.40; scene.cam.fovyDeg = 16;
        for (int k = 0; k < 4; k++) {
            Speech.Seg best = null;
            double bs = -1;
            for (Speech.Seg sg : s.segs) {
                if (sg.p().v() != WANT[k] || sg.p().v2() != null) continue;
                if (k == 2 && sg.p().sym().equals("v")) continue;
                double sc = (sg.t1() - sg.t0()) + (sg.p().stressed() ? 0.2 : 0) + (sg.p().kind() == com.dan.voice.Phon.Kind.PLOSIVE ? 1 : 0);
                if (sc > bs) { bs = sc; best = sg; }
            }
            if (best == null) continue;
            f.speak(s, null);
            f.speechHold = true;
            f.speechT = best.mid();
            for (int i = 0; i < 3; i++) { scene.hairTime = 80; off.shot(1); }
            out[k] = new Close(copy(off.last), best.mid(), best.p().toString(), s.dominant(best.mid()), com.dan.avatar.gl.AvatarGl.mouthOpening(f.lastForms));
        }
        return out;
    }

    static void drawRow(Graphics2D g, Close[] cs, String what, int y, int tw, int th) {
        for (int i = 0; i < 4; i++) {
            int x = 14 + i * (tw + 10);
            Close c = cs[i];
            if (c == null) { g.setColor(LINE); g.drawRect(x, y, tw, th); continue; }
            drawCrop(g, c.img(), x, y, tw, th);
            boolean ok = c.dom() == WANT[i];
            caption(g, String.format(Locale.GERMANY, "%s · „%s“ · %s %.2f S · MUNDBILD %s · ÖFFNUNG %.2f", what, NAME[i], c.sym(), c.t(), c.dom().key, c.open()), x, y + th + 15, ok ? TEAL : AMBER);
        }
    }

    static final Color[] VCOL = new Color[Viseme.values().length];
    static {
        for (Viseme v : Viseme.values()) {
            float hue = (v.ordinal() * 0.618034f) % 1f;
            VCOL[v.ordinal()] = Color.getHSBColor(hue, 0.45f, 0.85f);
        }
        VCOL[Viseme.PP.ordinal()] = new Color(232, 96, 96);
        VCOL[Viseme.O.ordinal()] = new Color(232, 176, 96);
        VCOL[Viseme.FF.ordinal()] = new Color(79, 198, 201);
        VCOL[Viseme.I.ordinal()] = new Color(150, 200, 110);
        VCOL[Viseme.SIL.ordinal()] = new Color(40, 48, 56);
    }

    /** Spur über der Zeit: Gewichte gestapelt, darüber die Laute; mit Ton die Wellenform. */
    static void timeline(Graphics2D g, Speech s, Audio au, int x, int y, int w, int h, String title) {
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        caption(g, title, x, y, MUTED);
        y += 8;
        double T = Math.max(s.duration + 0.3, au == null ? 0 : au.seconds());
        int lh = 18, wh = au == null ? 0 : 34, sh = h - lh - wh - 16;
        g.setColor(PANEL);
        g.fillRect(x, y, w, h - 8);
        // Laute
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        for (Speech.Seg sg : s.segs) {
            int a = x + (int) (sg.t0() / T * w), bb = x + (int) (sg.t1() / T * w);
            if (sg.p().pause()) continue;
            Viseme v = sg.p().v();
            g.setColor(VCOL[v.ordinal()].darker().darker());
            g.fillRect(a, y + 2, Math.max(1, bb - a - 1), lh - 2);
            g.setColor(INK);
            String sym = sg.p().toString();
            if (g.getFontMetrics().stringWidth(sym) < bb - a) g.drawString(sym, a + 2, y + lh - 4);
        }
        // Wellenform
        int wy = y + lh + 2;
        if (au != null) {
            g.setColor(new Color(147, 160, 170, 160));
            float[] smp = au.samples;
            double peak = 1e-6;
            for (float v : smp) peak = Math.max(peak, Math.abs(v));
            for (int px = 0; px < w; px++) {
                int i0 = (int) (px / (double) w * T * au.rate), i1 = (int) ((px + 1) / (double) w * T * au.rate);
                double mx = 0;
                for (int i = Math.max(0, i0); i < Math.min(smp.length, i1); i++) mx = Math.max(mx, Math.abs(smp[i]));
                int hh = (int) (mx / peak * (wh / 2 - 1));
                g.drawLine(x + px, wy + wh / 2 - hh, x + px, wy + wh / 2 + hh);
            }
        }
        // Gewichte gestapelt
        int gy = wy + wh + 2;
        for (int px = 0; px < w; px++) {
            double t = (px + 0.5) / w * T;
            double acc = 0;
            for (var e : s.weights(t).entrySet()) {
                if (e.getKey() == Viseme.SIL) continue;
                double v = e.getValue();
                int y0 = gy + sh - (int) Math.round((acc + v) * sh), y1 = gy + sh - (int) Math.round(acc * sh);
                g.setColor(VCOL[e.getKey().ordinal()]);
                if (y1 > y0) g.drawLine(x + px, y0, x + px, y1 - 1);
                acc += v;
            }
        }
        // Atem und Phrasenenden
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[]{3, 3}, 0));
        g.setColor(TEAL);
        for (double t : s.breaths) { int px = x + (int) (t / T * w); g.drawLine(px, y, px, y + h - 10); }
        g.setColor(AMBER);
        for (double t : s.phraseEnds) { int px = x + (int) (t / T * w); g.drawLine(px, y, px, y + h - 10); }
        g.setStroke(new BasicStroke(1f));
        // Zeitachse und Legende
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        g.setColor(MUTED);
        for (int k = 0; k <= (int) T * 2; k++) { int px = x + (int) (k * 0.5 / T * w); g.drawLine(px, y + h - 10, px, y + h - 6); if (k % 2 == 0) g.drawString(k / 2 + " s", px + 2, y + h + 4); }
        int lx = x + w - 470, ly = y - 12;
        for (int i = 0; i < 4; i++) {
            g.setColor(VCOL[WANT[i].ordinal()]);
            g.fillRect(lx + i * 70, ly - 8, 10, 8);
            g.setColor(INK);
            g.drawString(NAME[i] + " " + WANT[i].key, lx + i * 70 + 14, ly);
        }
        g.setColor(TEAL); g.drawString("┊ Atem", lx + 290, ly);
        g.setColor(AMBER); g.drawString("┊ Phrasenende", lx + 350, ly);
    }

    /** Videobild mit Kopfzeile, Untertitel und dem Mundbild des Augenblicks. */
    static BufferedImage overlay(BufferedImage fr, String what, Speech s, double t, boolean talking) {
        BufferedImage small = new BufferedImage(960, 540, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = small.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.drawImage(fr, 0, 0, 960, 540, null);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        g.setColor(TEAL);
        g.drawString("AVATAR STUDIO · PHASE 16 · " + what, 16, 26);
        Speech.Seg sg = s.segAt(t);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.setColor(MUTED);
        g.drawString(String.format(Locale.GERMANY, "%.2f s · Laut %s · Mundbild %s", Math.max(0, t), sg == null || sg.p().pause() ? "–" : sg.p().toString(), s.dominant(t).key), 16, 44);
        if (talking || t < s.duration + 0.4 && t > 0.2) {
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
            int sw = g.getFontMetrics().stringWidth(s.text);
            int x = (960 - sw) / 2, y = 506;
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRoundRect(x - 12, y - 26, sw + 24, 36, 10, 10);
            g.setColor(INK);
            g.drawString(s.text, x, y);
        }
        g.dispose();
        return small;
    }

    static void caption(Graphics2D g, String s, int x, int y, Color c) { AvatarStudio.caption(g, s, x, y, c); }
}
