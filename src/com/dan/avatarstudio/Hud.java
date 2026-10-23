package com.dan.avatarstudio;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Anzeige oben links: Bildrate, Zeiten, Grafikkarte, Stufe und der Steckbrief des
 * Grundkörpers aus der Blender-Kette. Gemalt mit Java2D, vormultipliziert für die Grafikkarte.
 */
public final class Hud {

    public double fps, frameMs, gpuMs;
    public String gl = "", size = "", error;
    public Quality quality = Quality.MEDIUM;
    /** Person und Mischen (leer, solange keine Person steht). */
    public String person = "", mix = "", skin = "", life = "", env = "", image = "", cloth = "", motion = "";
    /** Zeilen zum Grundkörper (aus {@link AvatarStudio#summary()}). */
    public List<String> body = new ArrayList<>();

    static final Color BG = new Color(8, 12, 16, 190);
    static final Color LINE = new Color(37, 49, 58);
    static final Color TEAL = new Color(79, 198, 201);
    static final Color AMBER = new Color(232, 176, 96);
    static final Color INK = new Color(236, 238, 240);
    static final Color MUTED = new Color(147, 160, 170);
    static final Color WARN = new Color(224, 163, 63);

    private BufferedImage img;
    private final Font head = new Font(Font.MONOSPACED, Font.BOLD, 12);
    private final Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    public BufferedImage render() {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Bild", String.format(Locale.GERMANY, "%.0f Bilder/s · %.1f ms", fps, frameMs)});
        rows.add(new String[]{"GPU", String.format(Locale.GERMANY, "%.2f ms", gpuMs)});
        rows.add(new String[]{"Fläche", size});
        rows.add(new String[]{"Stufe", quality.label});
        rows.add(new String[]{"Karte", gl});
        if (!person.isEmpty()) rows.add(new String[]{"Person", person});
        if (!mix.isEmpty()) rows.add(new String[]{"Mischen", mix});
        if (!skin.isEmpty()) rows.add(new String[]{"Haut", skin});
        if (!life.isEmpty()) rows.add(new String[]{"Leben", life});
        if (!env.isEmpty()) rows.add(new String[]{"Umwelt", env});
        if (!cloth.isEmpty()) rows.add(new String[]{"Kleidung", cloth});
        if (!motion.isEmpty()) rows.add(new String[]{"Bewegung", motion});
        if (!image.isEmpty()) rows.add(new String[]{"Bild", image});
        int lineH = 17, pad = 12, wMin = 330;
        int n = rows.size() + body.size() + (error != null ? 1 : 0);
        int hh = pad * 2 + 22 + lineH * n + (body.isEmpty() ? 0 : 10);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D pg = probe.createGraphics();
        pg.setFont(mono);
        FontMetrics fm = pg.getFontMetrics();
        int ww = wMin;
        for (String[] r : rows) ww = Math.max(ww, pad * 2 + 70 + fm.stringWidth(r[1]));
        for (String s : body) ww = Math.max(ww, pad * 2 + fm.stringWidth(s));
        if (error != null) ww = Math.max(ww, pad * 2 + fm.stringWidth(error));
        pg.dispose();
        ww = Math.min(ww, 760);
        if (img == null || img.getWidth() != ww || img.getHeight() != hh)
            img = new BufferedImage(ww, hh, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        g.setComposite(java.awt.AlphaComposite.Clear);
        g.fillRect(0, 0, ww, hh);
        g.setComposite(java.awt.AlphaComposite.SrcOver);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRoundRect(0, 0, ww - 1, hh - 1, 10, 10);
        g.setColor(LINE);
        g.drawRoundRect(0, 0, ww - 1, hh - 1, 10, 10);
        g.setColor(TEAL);
        g.fillRect(pad, pad + 2, 3, 12);
        g.setFont(head);
        g.drawString(("Avatar Studio · " + AvatarStudio.STAND).toUpperCase(java.util.Locale.GERMANY), pad + 10, pad + 13);
        int y = pad + 22 + 12;
        g.setFont(mono);
        for (String[] r : rows) {
            g.setColor(MUTED);
            g.drawString(r[0], pad, y);
            g.setColor(INK);
            g.drawString(r[1], pad + 70, y);
            y += lineH;
        }
        if (!body.isEmpty()) {
            y += 4;
            g.setColor(LINE);
            g.drawLine(pad, y - 12, ww - pad, y - 12);
            y += 6;
            for (String s : body) {
                g.setColor(s.startsWith("fehlt") || s.startsWith("!") ? WARN : (s.startsWith("Grundkörper") ? AMBER : INK));
                g.drawString(s, pad, y);
                y += lineH;
            }
        }
        if (error != null) {
            g.setColor(WARN);
            g.drawString(error, pad, y);
        }
        g.dispose();
        return img;
    }
}
