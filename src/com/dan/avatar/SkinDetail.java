package com.dan.avatar;

/**
 * Die Detailkarten der Haut (F6 A), aus Code und {@code seed}, kachelbar, reines Java.
 * Zwei Karten je {@link #SIZE}² RGBA (8 Bit):
 * <ul>
 *   <li><b>A</b> Steigungen: R, G = ∂h/∂u, ∂h/∂v der Poren; B, A = ∂h/∂u, ∂h/∂v der Fältchen
 *       (je Kachel, 128 = 0, Bereich ±{@link #SLOPE_A}).</li>
 *   <li><b>B</b> Felder: R = Porentiefe (Höhle), G = Fleckenfeld (Sommersprossen in grober Kachel),
 *       B = Haarbälge (Bartstoppeln), A = Marmorierung (Rötung, Altersflecken in sehr grober Kachel).</li>
 * </ul>
 * Höhen in Einheiten der Amplitude (0…1); der Shader skaliert mit Metern und Kachelzahl.
 * Dieselbe Karte für alle Personen; jede Person verschiebt sie um einen Versatz aus ihrem seed.
 */
public final class SkinDetail {

    public static final int SIZE = 512;
    /** Vorgabe-seed der Karten. */
    public static final long SEED = 4711;
    /** Größte Steigung je Kachel in den Karten A (Höhe 1 über SIZE/… Pixel). */
    public static final float SLOPE_A = 96f;

    public final byte[] a = new byte[SIZE * SIZE * 4], b = new byte[SIZE * SIZE * 4];
    public final long seed;

    private SkinDetail(long seed) { this.seed = seed; }

    private static volatile SkinDetail standard;

    /** Die Karten zum Vorgabe-seed (einmal gerechnet). */
    public static SkinDetail standard() {
        SkinDetail s = standard;
        if (s == null) synchronized (SkinDetail.class) {
            if (standard == null) standard = make(SEED);
            s = standard;
        }
        return s;
    }

    public static SkinDetail make(long seed) {
        SkinDetail d = new SkinDetail(seed);
        int n = SIZE;
        float[] pore = poreField(n, seed, 22, 0.35f, 0.75f);      // Poren: Mulden um Punkte (kleiner Abstand)
        float[] wr = wrinkleField(n, seed + 1);                    // Fältchen: zwei Scharen feiner Linien
        float[] spots = blobField(n, seed + 2, 40);                // Flecken (in grober Kachel genutzt)
        float[] hair = dotField(n, seed + 3, 40);                  // Haarbälge
        float[] mottle = valueNoise(n, seed + 4, new int[]{4, 8, 16, 32}, new float[]{1f, 0.55f, 0.3f, 0.15f});
        float[] dpu = slope(pore, n, true), dpv = slope(pore, n, false), dwu = slope(wr, n, true), dwv = slope(wr, n, false);
        for (int i = 0; i < n * n; i++) {
            d.a[i * 4] = s8(dpu[i] / SLOPE_A);
            d.a[i * 4 + 1] = s8(dpv[i] / SLOPE_A);
            d.a[i * 4 + 2] = s8(dwu[i] / SLOPE_A);
            d.a[i * 4 + 3] = s8(dwv[i] / SLOPE_A);
            d.b[i * 4] = u8(-pore[i]);
            d.b[i * 4 + 1] = u8(spots[i]);
            d.b[i * 4 + 2] = u8(hair[i]);
            d.b[i * 4 + 3] = u8(mottle[i]);
        }
        return d;
    }

    // ------------------------------------------------------------ Felder (alle periodisch)

    /** Poren: je Zelle ein Punkt, Mulde exp(−(d/r)²), Tiefe und Radius gestreut. Ergebnis −1…0. */
    static float[] poreField(int n, long seed, int cells, float rMin, float rMax) {
        Person.Rng rng = new Person.Rng(seed);
        int c = cells;
        double cs = (double) n / c;
        double[][] pt = new double[c * c][4];
        for (int i = 0; i < c * c; i++) {
            pt[i][0] = rng.next(); pt[i][1] = rng.next();
            pt[i][2] = (rMin + (rMax - rMin) * rng.next()) * cs * 0.32;  // Radius in Pixeln
            pt[i][3] = 0.5 + 0.5 * rng.next();                           // Tiefe
        }
        float[] f = new float[n * n];
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                int cx = (int) (x / cs), cy = (int) (y / cs);
                double h = 0;
                for (int oy = -1; oy <= 1; oy++)
                    for (int ox = -1; ox <= 1; ox++) {
                        int gx = Math.floorMod(cx + ox, c), gy = Math.floorMod(cy + oy, c);
                        double[] p = pt[gy * c + gx];
                        double px = (cx + ox + p[0]) * cs, py = (cy + oy + p[1]) * cs;
                        double dx = x + 0.5 - px, dy = y + 0.5 - py;
                        double e = Math.exp(-(dx * dx + dy * dy) / (p[2] * p[2]));
                        h = Math.max(h, e * p[3]);
                    }
                f[y * n + x] = (float) -h;
            }
        return f;
    }

    /** Fältchen: zwei Scharen dünner Rillen (±35° zur u-Richtung), durch Rauschen gebrochen. 0…1 (1 = Rille). */
    static float[] wrinkleField(int n, long seed) {
        float[] warp = valueNoise(n, seed, new int[]{8, 16}, new float[]{1f, 0.5f});
        float[] gate = valueNoise(n, seed + 7, new int[]{6, 12}, new float[]{1f, 0.5f});
        float[] f = new float[n * n];
        // Richtungen mit ganzzahligen Perioden, damit die Kachel nahtlos bleibt
        int[][] dir = {{9, 6}, {9, -6}};
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                double u = (double) x / n, v = (double) y / n;
                double w = warp[y * n + x] - 0.5;
                double s = 0;
                for (int k = 0; k < 2; k++) {
                    double ph = 2 * Math.PI * (dir[k][0] * u + dir[k][1] * v) + 2.2 * w * (k == 0 ? 1 : -1);
                    double line = Math.pow(Math.abs(Math.sin(ph)), 0.35);   // schmale Rillen bei sin ≈ 0
                    double g = Math.max(0, Math.min(1, (gate[y * n + x] - 0.35) * 2.2));
                    s = Math.max(s, (1 - line) * g);
                }
                f[y * n + x] = (float) s;
            }
        return f;
    }

    /** Weiche Flecken verschiedener Größe (Zellen mit Punkten): 0…1, Spitzen nahe 1. */
    static float[] blobField(int n, long seed, int cells) {
        Person.Rng rng = new Person.Rng(seed);
        double cs = (double) n / cells;
        double[][] pt = new double[cells * cells][4];
        for (int i = 0; i < pt.length; i++) {
            pt[i][0] = rng.next(); pt[i][1] = rng.next();
            pt[i][2] = cs * (0.18 + 0.32 * rng.next());
            pt[i][3] = rng.next();
        }
        float[] f = new float[n * n];
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                int cx = (int) (x / cs), cy = (int) (y / cs);
                double h = 0;
                for (int oy = -1; oy <= 1; oy++)
                    for (int ox = -1; ox <= 1; ox++) {
                        int gx = Math.floorMod(cx + ox, cells), gy = Math.floorMod(cy + oy, cells);
                        double[] p = pt[gy * cells + gx];
                        double dx = x + 0.5 - (cx + ox + p[0]) * cs, dy = y + 0.5 - (cy + oy + p[1]) * cs;
                        double e = Math.exp(-(dx * dx + dy * dy) / (p[2] * p[2]));
                        h = Math.max(h, e * (0.35 + 0.65 * p[3]));
                    }
                f[y * n + x] = (float) h;
            }
        return f;
    }

    /** Kleine harte Punkte (Haarbälge): 0…1. */
    static float[] dotField(int n, long seed, int cells) {
        Person.Rng rng = new Person.Rng(seed);
        double cs = (double) n / cells;
        double[][] pt = new double[cells * cells][3];
        for (int i = 0; i < pt.length; i++) { pt[i][0] = rng.next(); pt[i][1] = rng.next(); pt[i][2] = 0.6 + 0.4 * rng.next(); }
        float[] f = new float[n * n];
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                int cx = (int) (x / cs), cy = (int) (y / cs);
                double h = 0;
                for (int oy = -1; oy <= 1; oy++)
                    for (int ox = -1; ox <= 1; ox++) {
                        int gx = Math.floorMod(cx + ox, cells), gy = Math.floorMod(cy + oy, cells);
                        double[] p = pt[gy * cells + gx];
                        double dx = x + 0.5 - (cx + ox + p[0]) * cs, dy = y + 0.5 - (cy + oy + p[1]) * cs;
                        double r = cs * 0.16;
                        h = Math.max(h, Math.exp(-(dx * dx + dy * dy) / (r * r)) * p[2]);
                    }
                f[y * n + x] = (float) h;
            }
        return f;
    }

    /** Periodisches Wertrauschen (glatt, kubische Kurve), Summe der Oktaven auf 0…1 gebracht. */
    static float[] valueNoise(int n, long seed, int[] periods, float[] amps) {
        float[] f = new float[n * n];
        double total = 0;
        for (float a : amps) total += a;
        for (int o = 0; o < periods.length; o++) {
            int p = periods[o];
            Person.Rng rng = new Person.Rng(seed * 31 + o);
            double[] g = new double[p * p];
            for (int i = 0; i < g.length; i++) g[i] = rng.next();
            double cs = (double) n / p;
            for (int y = 0; y < n; y++)
                for (int x = 0; x < n; x++) {
                    double fx = (x + 0.5) / cs - 0.5, fy = (y + 0.5) / cs - 0.5;
                    int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
                    double tx = smooth(fx - x0), ty = smooth(fy - y0);
                    double v00 = g[Math.floorMod(y0, p) * p + Math.floorMod(x0, p)];
                    double v10 = g[Math.floorMod(y0, p) * p + Math.floorMod(x0 + 1, p)];
                    double v01 = g[Math.floorMod(y0 + 1, p) * p + Math.floorMod(x0, p)];
                    double v11 = g[Math.floorMod(y0 + 1, p) * p + Math.floorMod(x0 + 1, p)];
                    double v = (v00 * (1 - tx) + v10 * tx) * (1 - ty) + (v01 * (1 - tx) + v11 * tx) * ty;
                    f[y * n + x] += (float) (v * amps[o] / total);
                }
        }
        return f;
    }

    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    /** Zentrale Differenz je Kachel (Höhe pro Kachelbreite), periodisch. */
    static float[] slope(float[] h, int n, boolean du) {
        float[] s = new float[n * n];
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                float a, b;
                if (du) { a = h[y * n + Math.floorMod(x + 1, n)]; b = h[y * n + Math.floorMod(x - 1, n)]; }
                else { a = h[Math.floorMod(y + 1, n) * n + x]; b = h[Math.floorMod(y - 1, n) * n + x]; }
                s[y * n + x] = (a - b) * 0.5f * n;
            }
        return s;
    }

    private static byte s8(float v) {
        int q = Math.round(128 + 127 * Math.max(-1, Math.min(1, v)));
        return (byte) q;
    }

    private static byte u8(float v) {
        return (byte) Math.round(255 * Math.max(0, Math.min(1, v)));
    }

    /** Prüfsumme über beide Karten (FNV-1a, 64 Bit). */
    public long hash() {
        long h = 0xcbf29ce484222325L;
        for (byte x : a) { h ^= x & 0xff; h *= 0x100000001b3L; }
        for (byte x : b) { h ^= x & 0xff; h *= 0x100000001b3L; }
        return h;
    }
}
