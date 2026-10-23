package com.dan.portrait;

import java.util.ArrayList;
import java.util.List;

/**
 * Hintergrund aus Lichtern, wenn keine Szene da ist (Phase 9): Laternen, Fenster und Ampeln einer
 * nächtlichen Stadt in 4–30 m Abstand rund um die Person, in Bernstein und Türkis wie die Vorlage.
 * Jedes Licht hat Lage, Halbmesser (m) und Leuchtdichte (linear, vorbelichtet); auf nassem Boden
 * spiegelt es sich (gestreckt und schwächer). Gleicher {@code seed}, gleiche Stadt.
 */
public final class BokehLights {

    /** Ein Licht: Lage (m), Halbmesser (m), Leuchtdichte RGB, Spiegelung (1) oder nicht (0). */
    public static final class Light {
        public final double[] p = new double[3];
        public double r;
        public final double[] col = new double[3];
        public boolean mirror;
    }

    public final List<Light> lights = new ArrayList<>();
    public final String style;

    private BokehLights(String style) { this.style = style; }

    /** Stile: „Regennacht“ (Bernstein und Türkis), „Abendsonne“ (warm), „Nebel“ (wenige, weich), „aus“. */
    public static final String[] STYLES = {"Regennacht", "Abendsonne", "Nebel", "aus"};

    /**
     * Lichter um {@code center} (Welt, m), {@code count} Stück, {@code bright} als Faktor, mit
     * Spiegelung auf nassem Boden ({@code wet}).
     */
    public static BokehLights city(String style, long seed, double[] center, int count, double bright, boolean wet) {
        return city(style, seed, center, count, bright, wet, 0, 2 * Math.PI);
    }

    /**
     * Wie oben, aber nur in einem Fächer um die Richtung {@code yawRad} (Winkel um die Hochachse, 0 = +z)
     * mit der Breite {@code spreadRad}: so liegen alle Lichter hinter der Person, wenn der Fächer der
     * Kamera gegenüber steht.
     */
    public static BokehLights city(String style, long seed, double[] center, int count, double bright, boolean wet, double yawRad, double spreadRad) {
        BokehLights b = new BokehLights(style);
        if ("aus".equals(style)) return b;
        long s = seed * 0x9E3779B97F4A7C15L + 0x3C6EF372FE94F82BL;
        for (int i = 0; i < count; i++) {
            double u1 = rnd(s, i, 1), u2 = rnd(s, i, 2), u3 = rnd(s, i, 3), u4 = rnd(s, i, 4), u5 = rnd(s, i, 5);
            Light l = new Light();
            double ang = yawRad + (u1 - 0.5) * spreadRad, dist = 6 + 24 * Math.pow(u2, 1.3);
            l.p[0] = center[0] + Math.sin(ang) * dist;
            l.p[2] = center[2] + Math.cos(ang) * dist;
            l.p[1] = 0.6 + 4.5 * Math.pow(u3, 1.6);                // Fenster, Laternen, Ampeln
            l.r = 0.015 + 0.05 * u4;                                // Lampen, Fenster: klein gegen die Unschärfe
            double[] c;
            switch (style) {
                case "Abendsonne" -> c = u5 < 0.8 ? new double[]{1.0, 0.62, 0.28} : new double[]{1.0, 0.85, 0.6};
                case "Nebel" -> c = u5 < 0.7 ? new double[]{1.0, 0.85, 0.65} : new double[]{0.7, 0.85, 0.95};
                default -> c = u5 < 0.6 ? new double[]{1.0, 0.46, 0.10} : new double[]{0.04, 0.72, 0.80};   // Natriumdampf, Türkis
            }
            double e = bright * (20 + 110 * rnd(s, i, 6) * rnd(s, i, 7)) * ("Nebel".equals(style) ? 0.4 : 1);
            for (int k = 0; k < 3; k++) l.col[k] = c[k] * e;
            b.lights.add(l);
            if (wet && l.p[1] > 0.3) {
                Light m = new Light();
                m.p[0] = l.p[0]; m.p[1] = -l.p[1]; m.p[2] = l.p[2];
                m.r = l.r * 1.15;
                for (int k = 0; k < 3; k++) m.col[k] = l.col[k] * 0.30;
                m.mirror = true;
                b.lights.add(m);
            }
        }
        return b;
    }

    /** Die {@code n} hellsten Lichter (ohne Spiegelungen) für Spiegelungen in Augen und Wasser. */
    public List<Light> brightest(int n) {
        List<Light> l = new ArrayList<>();
        for (Light x : lights) if (!x.mirror) l.add(x);
        l.sort((a, c) -> Double.compare(lum(c.col) * c.r * c.r, lum(a.col) * a.r * a.r));
        return l.subList(0, Math.min(n, l.size()));
    }

    static double lum(double[] c) { return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; }

    private static double rnd(long s, int i, int k) {
        long z = s + i * 0xBF58476D1CE4E5B9L + k * 0x94D049BB133111EBL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }
}
