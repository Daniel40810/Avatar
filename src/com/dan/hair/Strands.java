package com.dan.hair;

/**
 * Einzelne Haare als Linien (F10 A: „einige Tausend echte Strähnen im Gesicht und am Rand“), reines
 * Java. Jedes Haar wurzelt in einem Dreieck eines Netzes (baryzentrisch) und wächst in eine Richtung,
 * die im Rahmen des Dreiecks gespeichert ist (Kanten e1, e2 und Normale n). So folgt es dem verformten
 * Netz auf der Grafikkarte, ohne dass das Paket den Menschen kennt: das Netz kommt von außen.
 *
 * <p>Je Haar 12 floats (siehe {@link #STRIDE}): Dreieck, b1, b2, a, b, c (Richtung = a·e1 + b·e2 + c·n),
 * Länge (m), Locke (Radius m), Lockenzahl, Zufall, Biegsamkeit, Grau (0/1).
 */
public final class Strands {

    public static final int STRIDE = 12;

    /** Art des Wachstums. */
    public enum Mode {
        /** Entlang der Haarkarte (UV-Richtung „Länge“), leicht von der Karte abgehoben: Fliegenhaare. */
        ALONG_CARD,
        /** Auf der Haut nach unten an der Fläche entlang, wenig abgehoben: Bart, Brauen. */
        DOWN_SURFACE
    }

    /** Parameter eines Haarfelds. */
    public static final class Params {
        public Mode mode = Mode.ALONG_CARD;
        public int count = 2000;
        public double lengthMin = 0.05, lengthMax = 0.18;
        /** Abheben von der Fläche: Anteil der Normalen an der Richtung. */
        public double lift = 0.35;
        /** Streuung der Richtung (0 parallel … 1 wirr). */
        public double spread = 0.35;
        public double curl = 0.0, curlTurns = 2;
        public double flex = 1.0;
        /** Anteil grauer Haare. */
        public double grey = 0;
        /** Bei ALONG_CARD: UV-Achse in Haarrichtung (0 = u, 1 = v) und Vorzeichen (Wurzel → Spitze). */
        public int axis = 1;
        public double axisSign = 1;
        public long seed = 1;
    }

    public final float[] data;
    public final int count;

    private Strands(float[] d, int n) { data = d; count = n; }

    /**
     * Haare auf einem Netz wachsen lassen. {@code density} je Ecke (0…1) gewichtet die Fläche; null = gleich.
     * {@code uv} nur für {@link Mode#ALONG_CARD} nötig.
     */
    public static Strands grow(float[] pos, float[] nrm, float[] uv, int[] idx, float[] density, Params p) {
        int tris = idx.length / 3;
        double[] cum = new double[tris + 1];
        for (int t = 0; t < tris; t++) {
            int a = idx[t * 3], b = idx[t * 3 + 1], c = idx[t * 3 + 2];
            double area = 0.5 * len(cross(sub(pos, b, a), sub(pos, c, a)));
            double w = density == null ? 1 : (density[a] + density[b] + density[c]) / 3.0;
            cum[t + 1] = cum[t] + area * Math.max(0, w);
        }
        int n = cum[tris] <= 0 ? 0 : p.count;
        float[] out = new float[n * STRIDE];
        Rng r = new Rng(p.seed);
        double down[] = {0, -1, 0};
        for (int s = 0; s < n; s++) {
            double x = r.next() * cum[tris];
            int lo = 0, hi = tris - 1;
            while (lo < hi) { int mid = (lo + hi) >>> 1; if (cum[mid + 1] < x) lo = mid + 1; else hi = mid; }
            int t = lo;
            int a = idx[t * 3], b = idx[t * 3 + 1], c = idx[t * 3 + 2];
            double u1 = r.next(), u2 = r.next();
            if (u1 + u2 > 1) { u1 = 1 - u1; u2 = 1 - u2; }
            double[] e1 = sub(pos, b, a), e2 = sub(pos, c, a);
            double[] nn = norm(cross(e1, e2));
            double[] na = {nrm[a * 3] + nrm[b * 3] + nrm[c * 3], nrm[a * 3 + 1] + nrm[b * 3 + 1] + nrm[c * 3 + 1], nrm[a * 3 + 2] + nrm[b * 3 + 2] + nrm[c * 3 + 2]};
            if (dot(nn, na) < 0) nn = scale(nn, -1);
            // Richtung in Weltachsen der Grundform
            double[] dir;
            if (p.mode == Mode.ALONG_CARD && uv != null) {
                double du1 = uv[b * 2] - uv[a * 2], du2 = uv[c * 2] - uv[a * 2];
                double dv1 = uv[b * 2 + 1] - uv[a * 2 + 1], dv2 = uv[c * 2 + 1] - uv[a * 2 + 1];
                double det = du1 * dv2 - du2 * dv1;
                double[] dPdu = det == 0 ? e1 : scale(sub3(scale(e1, dv2), scale(e2, dv1)), 1 / det);
                double[] dPdv = det == 0 ? e2 : scale(sub3(scale(e2, du1), scale(e1, du2)), 1 / det);
                double[] along = norm(p.axis == 0 ? dPdu : dPdv);
                along = scale(along, p.axisSign);
                dir = add(along, scale(nn, p.lift * (0.4 + 1.2 * r.next())));
            } else {
                // an der Fläche nach unten (Schwerkraft auf die Ebene), leicht abgehoben
                double[] t1 = sub3(down, scale(nn, dot(down, nn)));
                if (len(t1) < 1e-6) t1 = norm(e1);
                dir = add(norm(t1), scale(nn, p.lift * (0.5 + r.next())));
            }
            dir = norm(dir);
            // Streuung: zufällige Neigung
            double[] rnd = norm(new double[]{r.gauss(), r.gauss(), r.gauss()});
            dir = norm(add(dir, scale(rnd, p.spread * r.next())));
            // in den Rahmen (e1, e2, n) zerlegen: dir = A·e1 + B·e2 + C·n
            double[] abc = solve3(e1, e2, nn, dir);
            int o = s * STRIDE;
            out[o] = t;
            out[o + 1] = (float) u1;
            out[o + 2] = (float) u2;
            out[o + 3] = (float) abc[0];
            out[o + 4] = (float) abc[1];
            out[o + 5] = (float) abc[2];
            out[o + 6] = (float) (p.lengthMin + (p.lengthMax - p.lengthMin) * Math.pow(r.next(), 1.5));
            out[o + 7] = (float) (p.curl * (0.6 + 0.8 * r.next()));
            out[o + 8] = (float) (p.curlTurns * (0.7 + 0.6 * r.next()));
            out[o + 9] = (float) r.next();
            out[o + 10] = (float) (p.flex * (0.7 + 0.6 * r.next()));
            out[o + 11] = r.next() < p.grey ? 1f : 0f;
        }
        return new Strands(out, n);
    }

    /**
     * Welche UV-Achse einer Haarkarte läuft entlang der Haare? Die, deren Richtung im Mittel stärker
     * nach unten zeigt (Haare hängen); Vorzeichen so, dass die Spitze unten liegt.
     * Ergebnis {achse (0 = u, 1 = v), vorzeichen}.
     */
    public static double[] cardAxis(float[] pos, float[] uv, int[] idx) {
        double su = 0, sv = 0, du = 0, dv = 0;
        for (int t = 0; t + 2 < idx.length; t += 3) {
            int a = idx[t], b = idx[t + 1], c = idx[t + 2];
            double[] e1 = sub(pos, b, a), e2 = sub(pos, c, a);
            double du1 = uv[b * 2] - uv[a * 2], du2 = uv[c * 2] - uv[a * 2];
            double dv1 = uv[b * 2 + 1] - uv[a * 2 + 1], dv2 = uv[c * 2 + 1] - uv[a * 2 + 1];
            double det = du1 * dv2 - du2 * dv1;
            if (Math.abs(det) < 1e-12) continue;
            double[] dPdu = scale(sub3(scale(e1, dv2), scale(e2, dv1)), 1 / det);
            double[] dPdv = scale(sub3(scale(e2, du1), scale(e1, du2)), 1 / det);
            double area = 0.5 * len(cross(e1, e2));
            if (len(dPdu) > 0) { double y = dPdu[1] / len(dPdu); su += Math.abs(y) * area; du += y * area; }
            if (len(dPdv) > 0) { double y = dPdv[1] / len(dPdv); sv += Math.abs(y) * area; dv += y * area; }
        }
        boolean v = sv >= su;
        double d = v ? dv : du;
        return new double[]{v ? 1 : 0, d <= 0 ? 1 : -1};
    }

    /**
     * Haarrichtung je Ecke einer Haarkarte (Wurzel → Spitze, Länge 1), flächengewichtet aus den
     * angrenzenden Dreiecken; {@code axis} und {@code sign} wie aus {@link #cardAxis}. 3 floats je Ecke.
     */
    public static float[] cardTangents(float[] pos, float[] uv, int[] idx, int axis, double sign) {
        int n = pos.length / 3;
        double[] acc = new double[n * 3];
        for (int t = 0; t + 2 < idx.length; t += 3) {
            int a = idx[t], b = idx[t + 1], c = idx[t + 2];
            double[] e1 = sub(pos, b, a), e2 = sub(pos, c, a);
            double du1 = uv[b * 2] - uv[a * 2], du2 = uv[c * 2] - uv[a * 2];
            double dv1 = uv[b * 2 + 1] - uv[a * 2 + 1], dv2 = uv[c * 2 + 1] - uv[a * 2 + 1];
            double det = du1 * dv2 - du2 * dv1;
            if (Math.abs(det) < 1e-12) continue;
            double[] d = axis == 0 ? scale(sub3(scale(e1, dv2), scale(e2, dv1)), 1 / det) : scale(sub3(scale(e2, du1), scale(e1, du2)), 1 / det);
            d = scale(norm(d), sign * 0.5 * len(cross(e1, e2)));
            for (int v : new int[]{a, b, c}) for (int k = 0; k < 3; k++) acc[v * 3 + k] += d[k];
        }
        float[] out = new float[n * 3];
        for (int v = 0; v < n; v++) {
            double[] d = norm(new double[]{acc[v * 3], acc[v * 3 + 1], acc[v * 3 + 2]});
            if (acc[v * 3] == 0 && acc[v * 3 + 1] == 0 && acc[v * 3 + 2] == 0) d = new double[]{0, -1, 0};
            for (int k = 0; k < 3; k++) out[v * 3 + k] = (float) d[k];
        }
        return out;
    }

    /**
     * Haarrichtung als Fluss vom Wirbel: von {@code crown} weg, auf die Fläche gelegt (3 floats je Ecke).
     * Für Kappen ohne Kartenaufteilung der UV; der Wirbel kommt von außen.
     */
    public static float[] flowTangents(float[] pos, float[] nrm, double[] crown) {
        int n = pos.length / 3;
        float[] out = new float[n * 3];
        for (int v = 0; v < n; v++) {
            double[] d = {pos[v * 3] - crown[0], pos[v * 3 + 1] - crown[1], pos[v * 3 + 2] - crown[2]};
            d[1] -= 0.35 * len(d);                      // eher nach unten als waagerecht
            double[] nn = norm(new double[]{nrm[v * 3], nrm[v * 3 + 1], nrm[v * 3 + 2]});
            double[] t = sub3(d, scale(nn, dot(d, nn)));
            if (len(t) < 1e-9) t = new double[]{0, -1, 0};
            t = norm(t);
            for (int k = 0; k < 3; k++) out[v * 3 + k] = (float) t[k];
        }
        return out;
    }

    /** Mittlere Übereinstimmung |a·b| zweier Richtungsfelder (3 floats je Ecke). */
    public static double coherence(float[] a, float[] b) {
        double s = 0;
        int n = a.length / 3;
        for (int v = 0; v < n; v++) s += Math.abs(a[v * 3] * b[v * 3] + a[v * 3 + 1] * b[v * 3 + 1] + a[v * 3 + 2] * b[v * 3 + 2]);
        return n == 0 ? 0 : s / n;
    }

    // ------------------------------------------------------------ Hilfen

    static double[] sub(float[] p, int i, int j) { return new double[]{p[i * 3] - p[j * 3], p[i * 3 + 1] - p[j * 3 + 1], p[i * 3 + 2] - p[j * 3 + 2]}; }
    static double[] sub3(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    static double[] scale(double[] a, double s) { return new double[]{a[0] * s, a[1] * s, a[2] * s}; }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double len(double[] a) { return Math.sqrt(dot(a, a)); }
    static double[] norm(double[] a) { double l = len(a); return l < 1e-20 ? new double[]{0, 1, 0} : scale(a, 1 / l); }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }

    /** x mit x0·a + x1·b + x2·c = d (Cramer). */
    static double[] solve3(double[] a, double[] b, double[] c, double[] d) {
        double det = dot(a, cross(b, c));
        if (Math.abs(det) < 1e-24) return new double[]{0, 0, 1};
        return new double[]{dot(d, cross(b, c)) / det, dot(a, cross(d, c)) / det, dot(a, cross(b, d)) / det};
    }

    /** SplitMix64 (eigene Kopie, das Paket ist unabhängig). */
    static final class Rng {
        private long s;
        Rng(long seed) { s = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L; }
        double next() {
            long z = (s += 0x9E3779B97F4A7C15L);
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return ((z ^ (z >>> 31)) >>> 11) * 0x1.0p-53;
        }
        double gauss() {
            double u = Math.max(next(), 1e-12), v = next();
            return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
        }
    }

    /** Prüfsumme der Daten (FNV-1a über die Bits). */
    public long hash() {
        long h = 0xcbf29ce484222325L;
        for (float f : data) { h ^= Float.floatToIntBits(f); h *= 0x100000001b3L; }
        return h;
    }
}
