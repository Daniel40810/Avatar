package com.dan.portrait;

/**
 * Farbgebung (Phase 9) als 3D-Farbtabelle über dem Bild nach der Tonkurve (0…1, linear): „Regennacht“
 * legt die Schatten ins Türkis und die Lichter ins Bernstein (Komplementärkontrast der Vorlage), hebt
 * das Schwarz leicht an und nimmt den Mitten etwas Farbe; „Abendsonne“ wärmt, „Nebel“ macht flach
 * und kühl, „neutral“ lässt alles. Die Tabelle hat {@code n}³ Einträge (RGB).
 */
public final class Grade {

    public static final String[] NAMES = {"Regennacht", "Abendsonne", "Nebel", "neutral"};

    private Grade() {}

    /**
     * Ein Farbwert durch die Farbgebung: Ein- und Ausgabe linear (0…1), gerechnet wird im
     * wahrnehmungsgleichen Raum (Gamma 2,2), damit die Verschiebungen in Schatten und Lichtern gleich
     * stark wirken.
     */
    public static double[] apply(String name, double r, double g, double b) {
        if ("neutral".equals(name)) return new double[]{clamp(r), clamp(g), clamp(b)};
        double[] c = applyDisplay(name, Math.pow(clamp(r), 1 / 2.2), Math.pow(clamp(g), 1 / 2.2), Math.pow(clamp(b), 1 / 2.2));
        return new double[]{Math.pow(c[0], 2.2), Math.pow(c[1], 2.2), Math.pow(c[2], 2.2)};
    }

    /** Farbgebung im Anzeigeraum (Gamma 2,2, 0…1). */
    public static double[] applyDisplay(String name, double r, double g, double b) {
        double l = 0.2126 * r + 0.7152 * g + 0.0722 * b;
        double sh = (1 - l) * (1 - l), hi = l * l;
        switch (name) {
            case "Regennacht" -> {
                // Mitten etwas entsättigt, S-Kurve, Schatten Türkis, Lichter Bernstein, Schwarz angehoben
                r = mix(l, r, 0.92); g = mix(l, g, 0.92); b = mix(l, b, 0.92);
                r = curve(r, 1.08); g = curve(g, 1.08); b = curve(b, 1.08);
                r += -0.030 * sh + 0.060 * hi; g += 0.012 * sh + 0.018 * hi; b += 0.034 * sh - 0.050 * hi;
                r = 0.008 + r * 0.992; g = 0.012 + g * 0.988; b = 0.016 + b * 0.984;
            }
            case "Abendsonne" -> {
                r = curve(r, 1.04) * 1.06 + 0.02 * hi; g = curve(g, 1.04) * 0.99; b = curve(b, 1.04) * 0.86 + 0.03 * sh;
            }
            case "Nebel" -> {
                r = mix(l, r, 0.6); g = mix(l, g, 0.6); b = mix(l, b, 0.6);
                r = 0.05 + r * 0.88; g = 0.055 + g * 0.89; b = 0.065 + b * 0.90;
            }
            default -> {}
        }
        return new double[]{clamp(r), clamp(g), clamp(b)};
    }

    /** Farbtabelle n³ (RGB, r läuft am schnellsten), für eine 3D-Textur. */
    public static float[] lut(String name, int n) {
        float[] t = new float[n * n * n * 3];
        int k = 0;
        for (int bi = 0; bi < n; bi++)
            for (int gi = 0; gi < n; gi++)
                for (int ri = 0; ri < n; ri++) {
                    double[] c = apply(name, ri / (n - 1.0), gi / (n - 1.0), bi / (n - 1.0));
                    t[k++] = (float) c[0]; t[k++] = (float) c[1]; t[k++] = (float) c[2];
                }
        return t;
    }

    private static double curve(double x, double k) {
        // S-Kurve um 0,45 mit Steilheit k, Enden fest
        double m = 0.45;
        double y = x < m ? m * Math.pow(x / m, k) : 1 - (1 - m) * Math.pow((1 - x) / (1 - m), k);
        return y;
    }

    private static double mix(double a, double b, double t) { return a + (b - a) * t; }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
}
