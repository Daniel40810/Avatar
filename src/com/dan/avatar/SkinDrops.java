package com.dan.avatar;

import java.util.ArrayList;
import java.util.List;

/**
 * Tropfen auf der Haut des Kopfes (Phase 8, F13 A), im Texturraum auf einem {@link DropField}.
 * Regentropfen treffen nach Lage der Haut zur Fallrichtung (Größen nach Marshall und Palmer,
 * Fallgeschwindigkeit nach Atlas), bleiben als Perlen liegen, wachsen, wenn weitere sie treffen, und
 * fließen zusammen. Wird eine Perle zu schwer für ihre Neigung, läuft sie bergab, verliert dabei Wasser
 * an ihre Spur und nimmt Perlen auf ihrem Weg mit; am Rand (Kinn, Haaransatz) tropft sie ab. Perlen
 * verdunsten langsam. Reproduzierbar mit {@code seed}.
 *
 * <p>Ausgabe je Bild: Perlen (Lage im Ausschnitt 0…1, Halbmesser, Höhe) und die Spuren dieses
 * Schritts als Strecken. Gezeichnet wird auf der Grafikkarte ({@code gl/DropMapGl}).
 */
public final class SkinDrops {

    public final DropField field;
    private final Person.Rng rng;
    /** Höchstzahl der Perlen. */
    public int max = 380;
    /** Anteil der Einschläge, die als Perle liegen bleiben; der Rest zerstäubt zum Film [G]. */
    public double beadShare = 0.5;
    /** Nässe des Films auf der Haut 0…1 (von außen): auf nasser Haut fließen Perlen leichter zusammen,
     *  sehr kleine gehen im Film auf. */
    public double film;

    /** Eine Perle: Lage (Gitter), Wasser (mm³), läuft sie, Geschwindigkeit (m/s). */
    public static final class Bead {
        public double x, y, vol, speed;
        public boolean running;
        /** Halbmesser der Auflage (mm) bei Randwinkel um 80° [G]: V ≈ 1,6 r³. */
        public double radius() { return Math.cbrt(vol / 1.6); }
    }

    private final List<Bead> beads = new ArrayList<>();
    private final List<float[]> trails = new ArrayList<>();
    private double[] cdf;
    private double cdfSum;
    private final double[] cdfFrom = {9, 9, 9};
    private double time;
    /** Zähler: Einschläge, abgetropfte Perlen. */
    public long hits, drips;

    public SkinDrops(DropField f, long seed) {
        field = f;
        rng = new Person.Rng(seed * 0x2545F491L + 99);
    }

    public List<Bead> beads() { return beads; }

    public double time() { return time; }

    /** Alle Perlen fort (abgetrocknet). */
    public void clear() { beads.clear(); trails.clear(); }

    /**
     * Tropfen über 0,5 mm, die je m² und Sekunde auf eine waagerechte Fläche treffen:
     * ∫ N(D) v(D) dD mit N(D) = 8000 · e^(−Λ D) m⁻³ mm⁻¹, Λ = 4,1 · R^(−0,21) (Marshall und Palmer) und
     * v(D) = 9,65 − 10,3 · e^(−0,6 D) m/s (Atlas).
     */
    public static double impacts(double rate) {
        if (rate <= 0) return 0;
        double lam = 4.1 * Math.pow(rate, -0.21), s = 0;
        for (double d = 0.5; d < 6; d += 0.01) s += 8000 * Math.exp(-lam * d) * Math.max(0, 9.65 - 10.3 * Math.exp(-0.6 * d)) * 0.01;
        return s;
    }

    /**
     * Ein Schritt. {@code rate}: Regen am Kopf (mm/h, Kapuze schon abgezogen); {@code from}: Richtung,
     * aus der er kommt, im Ruherahmen des Kopfes; {@code dry}: Verdunstung (mm/s Wasserfilm);
     * {@code hair}: Haare halten den Regen über dem Haaransatz.
     */
    public void step(double dt, double rate, double[] from, double dry, boolean hair) {
        if (dt <= 0) return;
        time += dt;
        trails.clear();
        DropField f = field;
        // Einschläge: Poisson, Zellen nach Fläche · Lage zur Fallrichtung
        if (rate > 0) {
            if (cdf == null || Math.abs(from[0] - cdfFrom[0]) + Math.abs(from[1] - cdfFrom[1]) + Math.abs(from[2] - cdfFrom[2]) > 0.02) buildCdf(from, hair);
            double lambda = impacts(rate) * cdfSum * dt;
            int k = poisson(lambda);
            double lam = 4.1 * Math.pow(rate, -0.21);
            for (int i = 0; i < k; i++) {
                int c = pick(rng.next() * cdfSum);
                if (c < 0) continue;
                double d = 0.5 - Math.log(1 - rng.next() * (1 - Math.exp(-lam * 3.5))) / lam;   // 0,5 … 4 mm
                double vol = Math.PI / 6 * d * d * d * (0.5 + 0.4 * rng.next());                 // Rest nach dem Zerspritzen [G]
                double x = c % f.n + rng.next(), y = c / f.n + rng.next();
                hits++;
                if (rng.next() < beadShare) add(x, y, vol);
            }
        }
        // Laufen, Verdunsten
        for (int i = beads.size() - 1; i >= 0; i--) {
            Bead b = beads.get(i);
            int c = f.cell(b.x, b.y);
            if (c < 0) { beads.remove(i); drips++; continue; }
            double r = b.radius();
            b.vol -= dry * Math.PI * r * r * dt;
            if (film > 0.5 && r < 0.45) b.vol -= dt * 0.15 * (film - 0.5) * b.vol * 4;   // geht im Film auf
            if (b.vol < 0.02) { beads.remove(i); continue; }
            double st = Math.max(0.12, f.steep[c]);
            double rc = 1.6 / st;                                 // ab hier läuft die Perle [G]
            if (r > rc) {
                b.running = true;
                b.speed = Math.min(0.09, b.speed + dt * 0.25 * st);   // beschleunigt bis 9 cm/s
            } else if (b.running && r < 0.8 * rc) { b.running = false; b.speed = 0; }
            if (!b.running) continue;
            double step = b.speed * dt;                           // Meter
            double nx = b.x + f.down[c * 2] * step, ny = b.y + f.down[c * 2 + 1] * step;
            double lost = 0.03 * step * 1000;                     // mm³ je mm Weg an die Spur
            b.vol -= lost;
            trails.add(new float[]{(float) b.x, (float) b.y, (float) nx, (float) ny, (float) r});
            b.x = nx; b.y = ny;
        }
        // Zusammenfließen (Gitter von Zellen)
        merge();
        while (beads.size() > max) {
            int s = 0;
            for (int i = 1; i < beads.size(); i++) if (beads.get(i).vol < beads.get(s).vol) s = i;
            beads.remove(s);
        }
    }

    private void add(double x, double y, double vol) {
        Bead nb = new Bead();
        nb.x = x; nb.y = y; nb.vol = vol;
        beads.add(nb);
    }

    private void merge() {
        DropField f = field;
        int B = 8;                                     // Eimer von 8 × 8 Zellen
        int nb = (f.n + B - 1) / B;
        java.util.Map<Integer, List<Bead>> grid = new java.util.HashMap<>();
        for (Bead b : beads) grid.computeIfAbsent((int) (b.y / B) * nb + (int) (b.x / B), k -> new ArrayList<>()).add(b);
        java.util.Set<Bead> gone = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Bead a : beads) {
            if (gone.contains(a)) continue;
            int c = f.cell(a.x, a.y);
            if (c < 0) continue;
            double mpc = f.meterPerCell(c) * 1000;     // mm je Zelle
            int bx = (int) (a.x / B), by = (int) (a.y / B);
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                List<Bead> l = grid.get((by + dy) * nb + bx + dx);
                if (l == null) continue;
                for (Bead o : l) {
                    if (o == a || gone.contains(o)) continue;
                    double d = Math.hypot(o.x - a.x, o.y - a.y) * mpc;
                    if (d < (a.radius() + o.radius()) * (1.2 + 1.2 * film)) {
                        double v = a.vol + o.vol;
                        a.x = (a.x * a.vol + o.x * o.vol) / v; a.y = (a.y * a.vol + o.y * o.vol) / v;
                        a.running |= o.running; a.speed = Math.max(a.speed, o.speed);
                        a.vol = v;
                        gone.add(o);
                    }
                }
            }
        }
        if (!gone.isEmpty()) beads.removeIf(gone::contains);
    }

    private void buildCdf(double[] from, boolean hair) {
        DropField f = field;
        int cells = f.n * f.n;
        cdf = new double[cells];
        double s = 0;
        double fy = Math.max(0.2, from[1]);
        for (int c = 0; c < cells; c++) {
            if (f.valid[c] && !(hair && f.scalp[c])) {
                double e = f.nrm[c * 3] * from[0] + f.nrm[c * 3 + 1] * from[1] + f.nrm[c * 3 + 2] * from[2];
                if (e > 0) s += f.area[c] * e / fy;
            }
            cdf[c] = s;
        }
        cdfSum = s;
        System.arraycopy(from, 0, cdfFrom, 0, 3);
    }

    private int pick(double r) {
        int lo = 0, hi = cdf.length - 1;
        while (lo < hi) { int m = (lo + hi) >>> 1; if (cdf[m] < r) lo = m + 1; else hi = m; }
        return field.valid[lo] ? lo : -1;
    }

    private int poisson(double l) {
        if (l <= 0) return 0;
        if (l > 30) return Math.max(0, (int) Math.round(l + Math.sqrt(l) * rng.gauss()));
        double L = Math.exp(-l), p = 1;
        int k = 0;
        do { k++; p *= rng.next(); } while (p > L);
        return k - 1;
    }

    /**
     * Perlen für die Grafikkarte: je Perle {x, y (Ausschnitt 0…1), Halbmesser x, y (Ausschnitt), Höhe (mm)}.
     */
    public float[] beadData() {
        DropField f = field;
        float[] out = new float[beads.size() * 5];
        int k = 0;
        for (Bead b : beads) {
            int c = f.cell(b.x, b.y);
            if (c < 0) continue;
            double r = b.radius(), mpc = f.meterPerCell(c) * 1000;
            double rl = r / mpc / f.n;
            out[k++] = (float) (b.x / f.n); out[k++] = (float) (b.y / f.n);
            out[k++] = (float) rl; out[k++] = (float) rl;
            out[k++] = (float) (0.75 * r);                // Höhe der Kappe (mm)
        }
        return java.util.Arrays.copyOf(out, k);
    }

    /** Spuren dieses Schritts: je Strecke {x0, y0, x1, y1 (Ausschnitt), Breite (Ausschnitt)}. */
    public float[] trailData() {
        DropField f = field;
        float[] out = new float[trails.size() * 5];
        int k = 0;
        for (float[] t : trails) {
            int c = f.cell(t[0], t[1]);
            if (c < 0) continue;
            double mpc = f.meterPerCell(c) * 1000;
            out[k++] = t[0] / f.n; out[k++] = t[1] / f.n; out[k++] = t[2] / f.n; out[k++] = t[3] / f.n;
            out[k++] = (float) (0.8 * t[4] / mpc / f.n);
        }
        return java.util.Arrays.copyOf(out, k);
    }

    /** Wasser in Perlen (mm³). */
    public double volume() { double s = 0; for (Bead b : beads) s += b.vol; return s; }
}
