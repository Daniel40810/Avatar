package com.dan.avatarstudio;

import com.dan.avatar.Emotion;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.BiConsumer;
import javax.swing.JComponent;

/**
 * Der Gefühlsraum als Fläche zum Ziehen (Werkbuch Abschnitt 7): waagerecht Valenz (unangenehm …
 * angenehm), senkrecht Erregung (ruhig … aufgeregt). Die benannten Gefühle stehen als Punkte darin;
 * Klicken oder Ziehen setzt den Zielpunkt.
 */
final class FeelingPad extends JComponent {

    private double v, a;
    private BiConsumer<Double, Double> onChange;
    private String hover;

    FeelingPad() {
        setPreferredSize(new Dimension(300, 210));
        setMinimumSize(new Dimension(200, 160));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 210));
        setAlignmentX(LEFT_ALIGNMENT);
        setToolTipText("");
        MouseAdapter m = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { pick(e); }
            @Override public void mouseDragged(MouseEvent e) { pick(e); }
            @Override public void mouseMoved(MouseEvent e) {
                String h = nearest(e.getX(), e.getY());
                if (!java.util.Objects.equals(h, hover)) { hover = h; repaint(); }
            }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    void onChange(BiConsumer<Double, Double> c) { onChange = c; }

    /** Punkt setzen (ohne Meldung). */
    void set(double valence, double arousal) { v = valence; a = arousal; repaint(); }

    private int[] box() {
        int pad = 8;
        return new int[]{pad, pad, getWidth() - 2 * pad, getHeight() - 2 * pad - 14};
    }

    private void pick(MouseEvent e) {
        int[] b = box();
        double nv = (e.getX() - b[0]) / (double) b[2] * 2 - 1, na = 1 - (e.getY() - b[1]) / (double) b[3] * 2;
        nv = Math.max(-1, Math.min(1, nv)); na = Math.max(-1, Math.min(1, na));
        // in der Nähe eines benannten Gefühls einrasten
        String n = nearest(e.getX(), e.getY());
        if (n != null) { Emotion.Def d = Emotion.named(n); nv = d.valence; na = d.arousal; }
        v = nv; a = na;
        repaint();
        if (onChange != null) onChange.accept(v, a);
    }

    private String nearest(int x, int y) {
        int[] b = box();
        for (Emotion.Def d : Emotion.NAMED) {
            double px = b[0] + (d.valence + 1) / 2 * b[2], py = b[1] + (1 - (d.arousal + 1) / 2) * b[3];
            if (Math.hypot(px - x, py - y) < 8) return d.name;
        }
        return null;
    }

    @Override public String getToolTipText(MouseEvent e) {
        String n = nearest(e.getX(), e.getY());
        return n == null ? null : n;
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int[] b = box();
        g.setColor(new Color(12, 16, 20));
        g.fillRoundRect(b[0] - 4, b[1] - 4, b[2] + 8, b[3] + 8, 10, 10);
        g.setColor(AvatarStudio.LINE);
        g.drawRoundRect(b[0] - 4, b[1] - 4, b[2] + 8, b[3] + 8, 10, 10);
        // Achsen und Ringe
        int cx = b[0] + b[2] / 2, cy = b[1] + b[3] / 2;
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{3, 4}, 0));
        g.drawLine(b[0], cy, b[0] + b[2], cy);
        g.drawLine(cx, b[1], cx, b[1] + b[3]);
        g.setStroke(new BasicStroke(1f));
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 9));
        g.setColor(AvatarStudio.MUTED);
        g.drawString("aufgeregt", cx + 4, b[1] + 10);
        g.drawString("ruhig", cx + 4, b[1] + b[3] - 3);
        g.drawString("unangenehm", b[0] + 2, cy - 4);
        String ang = "angenehm";
        g.drawString(ang, b[0] + b[2] - g.getFontMetrics().stringWidth(ang) - 2, cy - 4);
        // benannte Gefühle
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        for (Emotion.Def d : Emotion.NAMED) {
            int px = (int) Math.round(b[0] + (d.valence + 1) / 2 * b[2]), py = (int) Math.round(b[1] + (1 - (d.arousal + 1) / 2) * b[3]);
            boolean h = d.name.equals(hover);
            g.setColor(h ? AvatarStudio.AMBER : new Color(120, 135, 145));
            g.fillOval(px - 3, py - 3, 6, 6);
            if (!d.name.equals("neutral") || h) {
                g.setColor(h ? AvatarStudio.AMBER : AvatarStudio.MUTED);
                int tw = g.getFontMetrics().stringWidth(d.name);
                int tx = Math.max(b[0], Math.min(b[0] + b[2] - tw, px - tw / 2));
                g.drawString(d.name, tx, py - 6);
            }
        }
        // Zielpunkt
        int tx = (int) Math.round(b[0] + (v + 1) / 2 * b[2]), ty = (int) Math.round(b[1] + (1 - (a + 1) / 2) * b[3]);
        g.setColor(new Color(79, 198, 201, 60));
        g.fillOval(tx - 11, ty - 11, 22, 22);
        g.setColor(AvatarStudio.TEAL);
        g.setStroke(new BasicStroke(2f));
        g.drawOval(tx - 7, ty - 7, 14, 14);
        g.fillOval(tx - 2, ty - 2, 4, 4);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        g.setColor(AvatarStudio.MUTED);
        g.drawString(String.format(java.util.Locale.GERMANY, "Valenz %+.2f · Erregung %+.2f", v, a), b[0], getHeight() - 4);
        g.dispose();
    }
}
