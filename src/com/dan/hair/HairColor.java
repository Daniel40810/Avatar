package com.dan.hair;

/**
 * Haarfarbe aus drei Zahlen (wie im Haar selbst): Eumelanin (0 hellblond … 1 schwarz), Phäomelanin
 * (Rotanteil 0…1) und Grau (Anteil weißer Haare 0…1). Ergebnis ist die lineare Albedo der Faser;
 * graue Haare mischen sich je Strähne zu (Salz und Pfeffer), nicht als Farbton.
 */
public final class HairColor {

    private HairColor() {}

    /** Stützstellen nach Eumelanin: platin, hellblond, dunkelblond, hellbraun, braun, dunkelbraun, schwarz. */
    private static final double[][] EU = {
        {0.80, 0.74, 0.62}, {0.66, 0.52, 0.32}, {0.42, 0.29, 0.16}, {0.24, 0.14, 0.07},
        {0.12, 0.065, 0.032}, {0.055, 0.032, 0.018}, {0.022, 0.017, 0.014}};
    /** Rot (Phäomelanin) bei hellem Haar: kupfer. */
    private static final double[] RED = {0.52, 0.16, 0.05};
    /** Weißes (graues) Haar. */
    public static final float[] WHITE = {0.72f, 0.71f, 0.69f};

    /** Lineare Albedo für Eumelanin {@code eu} und Rot {@code red}. */
    public static float[] albedo(double eu, double red) {
        double m = Math.max(0, Math.min(1, eu)) * (EU.length - 1);
        int i = Math.min(EU.length - 2, (int) Math.floor(m));
        double t = m - i;
        double[] c = new double[3];
        for (int k = 0; k < 3; k++) c[k] = EU[i][k] * (1 - t) + EU[i + 1][k] * t;
        // Rot wirkt bei hellem Haar stark, bei dunklem kaum (Eumelanin überdeckt es)
        double r = Math.max(0, Math.min(1, red)) * (1 - 0.8 * Math.max(0, Math.min(1, eu)));
        for (int k = 0; k < 3; k++) c[k] = c[k] * (1 - r) + RED[k] * r * (0.6 + 0.6 * c[0]);
        return new float[]{(float) c[0], (float) c[1], (float) c[2]};
    }
}
