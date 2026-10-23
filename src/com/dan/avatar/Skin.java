package com.dan.avatar;

/**
 * Die Haut als Zahlen (Phase 4, F6 und F8), reines Java: Grundton aus Melanin und Rötung,
 * Mischung der sechs Grundhäute nach Alter und Geschlecht, Streuprofil nach d'Eon und Luebke
 * (sechs Gaußglocken), daraus der trennbare Kern für die Streuung im Bildraum (Jimenez,
 * „Separable Subsurface Scattering“, 2015), die vorab integrierte Tabelle für „Niedrig“ (Penner
 * 2011) und die Durchleuchtung dünner Stellen (Jimenez 2010). Die Grafikkarte rechnet mit genau
 * diesen Zahlen.
 */
public final class Skin {

    private Skin() {}

    // ------------------------------------------------------------ Ton

    /** Linearer Grundton (Albedo) für Melanin 0…1 an fünf Stützstellen. */
    private static final double[][] TONE = {
        {0.78, 0.57, 0.47},   // sehr hell
        {0.62, 0.43, 0.33},   // hell
        {0.42, 0.26, 0.17},   // mittel
        {0.22, 0.12, 0.075},  // dunkel
        {0.09, 0.050, 0.032}, // sehr dunkel
    };

    /** Linearer Grundton der Haut aus Melanin (0…1) und Rötung (0…1, Mitte 0,5). */
    public static float[] tone(double melanin, double redness) {
        double m = Math.max(0, Math.min(1, melanin)) * (TONE.length - 1);
        int i = Math.min(TONE.length - 2, (int) Math.floor(m));
        double t = m - i;
        double[] c = new double[3];
        for (int k = 0; k < 3; k++) c[k] = TONE[i][k] * (1 - t) + TONE[i + 1][k] * t;
        // Durchblutung: mehr Rot, weniger Grün und Blau; dunkle Haut zeigt sie schwächer
        double r = (redness - 0.5) * (1 - 0.6 * melanin);
        c[0] *= 1 + 0.22 * r;
        c[1] *= 1 - 0.14 * r;
        c[2] *= 1 - 0.12 * r;
        return new float[]{(float) c[0], (float) c[1], (float) c[2]};
    }

    /** Die sechs Grundhäute (Reihenfolge der Textur-Ebenen). */
    public static final String[] TEXTURES = {"jung_w", "jung_m", "mittel_w", "mittel_m", "alt_w", "alt_m"};

    /** Gewichte der sechs Grundhäute für Alter (Jahre) und Geschlecht (0 Frau … 1 Mann); Summe 1. */
    public static float[] textureWeights(double years, double gender) {
        double a0, a1, a2;     // jung, mittel, alt
        if (years <= 25) { a0 = 1; a1 = 0; a2 = 0; }
        else if (years <= 50) { double t = (years - 25) / 25; a0 = 1 - t; a1 = t; a2 = 0; }
        else if (years <= 75) { double t = (years - 50) / 25; a0 = 0; a1 = 1 - t; a2 = t; }
        else { a0 = 0; a1 = 0; a2 = 1; }
        // Geschlecht nur in der Mitte gemischt: die Männerhäute tragen gemalten Bartschatten, der hat seit
        // Phase 4 einen eigenen Regler (Bartzone × beard); eine deutliche Frau bekommt ihn so nicht anteilig ab
        double g0 = Math.max(0, Math.min(1, (gender - 0.3) / 0.4));
        double g = g0 * g0 * (3 - 2 * g0);
        return new float[]{(float) (a0 * (1 - g)), (float) (a0 * g), (float) (a1 * (1 - g)), (float) (a1 * g),
                           (float) (a2 * (1 - g)), (float) (a2 * g)};
    }

    // ------------------------------------------------------------ Streuprofil

    /** d'Eon und Luebke (2007): Varianzen in mm² und RGB-Gewichte der sechs Glocken. */
    public static final double[] VAR = {0.0064, 0.0484, 0.187, 0.567, 1.99, 7.41};
    public static final double[][] W = {
        {0.233, 0.455, 0.649}, {0.100, 0.336, 0.344}, {0.118, 0.198, 0.0},
        {0.113, 0.007, 0.007}, {0.358, 0.004, 0.0}, {0.078, 0.0, 0.0}};

    /** Abfall je Kanal (Jimenez): Grün und Blau streuen kürzer als Rot. */
    public static final double[] FALLOFF = {1.0, 0.37, 0.3};

    /** Stärke der Streuung je Kanal für Melanin 0…1 (dunkle Haut zeigt weniger rotes Ausbluten). */
    public static double[] strength(double melanin) {
        double m = Math.max(0, Math.min(1, melanin));
        return new double[]{0.48 * (1 - m) + 0.40 * m, 0.41 * (1 - m) + 0.30 * m, 0.28 * (1 - m) + 0.22 * m};
    }

    /** Profil R(r) je Kanal, r in mm, mit Abfall; jede Glocke 1D-normiert wie bei Jimenez (trennbar). */
    static double profile(double r, int ch) {
        double f = FALLOFF[ch], s = 0;
        // die schmalste Glocke ist im Bildraum nicht aufzulösen; sie steckt im Mittelpunkt (wie Jimenez)
        for (int i = 1; i < VAR.length; i++) {
            double v = VAR[i] * f * f;
            s += W[i][ch] * Math.exp(-r * r / (2 * v)) / Math.sqrt(2 * Math.PI * v);   // 1D-normiert (trennbar)
        }
        return s;
    }

    /**
     * Trennbarer Kern mit {@code n} Proben (ungerade): je Probe (r, g, b, Versatz in mm).
     * Probe 0 ist die Mitte. Summe je Kanal = 1. Wie {@code calculateKernel} bei Jimenez.
     */
    public static float[] kernel(int n, double[] strength) {
        double range = n > 20 ? 3.0 : 2.0, exponent = 2.0;
        double[][] k = new double[n][4];
        double step = 2.0 * range / (n - 1);
        for (int i = 0; i < n; i++) {
            double o = -range + i * step;
            double sign = o < 0 ? -1 : 1;
            k[i][3] = range * sign * Math.abs(Math.pow(o, exponent)) / Math.pow(range, exponent);
        }
        for (int i = 0; i < n; i++) {
            double w0 = i > 0 ? Math.abs(k[i][3] - k[i - 1][3]) : 0;
            double w1 = i < n - 1 ? Math.abs(k[i][3] - k[i + 1][3]) : 0;
            double area = (w0 + w1) / 2;
            for (int c = 0; c < 3; c++) k[i][c] = area * profile(k[i][3], c);
        }
        // Mitte nach vorn
        double[] mid = k[n / 2];
        System.arraycopy(k, 0, k, 1, n / 2);
        k[0] = mid;
        // normieren
        for (int c = 0; c < 3; c++) {
            double sum = 0;
            for (double[] e : k) sum += e[c];
            for (double[] e : k) e[c] /= sum;
        }
        // Stärke: Mitte (1 − s) + s · k, Rest s · k
        for (int c = 0; c < 3; c++) {
            k[0][c] = (1 - strength[c]) + strength[c] * k[0][c];
            for (int i = 1; i < n; i++) k[i][c] *= strength[c];
        }
        float[] out = new float[n * 4];
        for (int i = 0; i < n; i++) for (int c = 0; c < 4; c++) out[i * 4 + c] = (float) k[i][c];
        return out;
    }

    // ------------------------------------------------------------ Durchleuchtung

    /** Durchlässigkeit T(s) je Kanal für eine Dicke {@code s} in mm (Jimenez 2010, gleiche Glocken). */
    public static double[] transmittance(double s) {
        double dd = -s * s;
        double[] t = new double[3];
        for (int i = 0; i < VAR.length; i++)
            for (int c = 0; c < 3; c++) t[c] += W[i][c] * Math.exp(dd / VAR[i]);
        return t;
    }

    // ------------------------------------------------------------ Vorab integriert (Niedrig)

    /** Breite (N·L) und Höhe (Krümmung) der Tabelle. */
    public static final int LUT_W = 64, LUT_H = 32;
    /** Krümmungsradius in der obersten Zeile (mm); Zeile y entspricht r = LUT_R0 / max(y, 1/LUT_H). */
    public static final double LUT_R0 = 4.0;

    private static volatile float[] lutCache;

    /** {@link #preintegrated()} einmal gerechnet. */
    public static float[] preintegratedCached() {
        float[] t = lutCache;
        if (t == null) lutCache = t = preintegrated();
        return t;
    }

    /**
     * Vorab integrierte Haut (Penner 2011): D(θ, r) = ∫ sat(cos(θ + x)) R(2r sin(x/2)) dx / ∫ R(…) dx.
     * Ergebnis RGB, zeilenweise; x = N·L · 0,5 + 0,5, y = 4 mm / Radius.
     */
    public static float[] preintegrated() {
        float[] out = new float[LUT_W * LUT_H * 3];
        int steps = 720;
        for (int y = 0; y < LUT_H; y++) {
            double yy = (y + 0.5) / LUT_H;
            double r = LUT_R0 / yy;
            for (int x = 0; x < LUT_W; x++) {
                double nl = ((x + 0.5) / LUT_W) * 2 - 1;
                double th = Math.acos(Math.max(-1, Math.min(1, nl)));
                for (int c = 0; c < 3; c++) {
                    double num = 0, den = 0;
                    for (int i = 0; i < steps; i++) {
                        double a = -Math.PI + (i + 0.5) * 2 * Math.PI / steps;
                        double d = Math.abs(2 * r * Math.sin(a / 2));
                        double p = profile(d, c) + W[0][c] * Math.exp(-d * d / (2 * VAR[0])) / Math.sqrt(2 * Math.PI * VAR[0]);
                        num += Math.max(0, Math.cos(th + a)) * p;
                        den += p;
                    }
                    out[(y * LUT_W + x) * 3 + c] = (float) (num / den);
                }
            }
        }
        return out;
    }
}
