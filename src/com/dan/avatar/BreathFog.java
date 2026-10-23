package com.dan.avatar;

import java.util.ArrayList;
import java.util.List;

/**
 * Atemhauch (Phase 8, F13 A): beim Ausatmen in kalter Luft kleine Wolken aus Nase oder Mund. Jede
 * Wolke startet mit dem Atemstrom (rund 1 m/s aus dem Mund, schräg nach unten aus der Nase), wird
 * von der Luft gebremst, weitet sich, steigt warm ein wenig, treibt mit dem Wind und löst sich in 1–3 s
 * auf. Wie dicht sie ist, sagt {@link Climate#breathFog()}. Lage in Figur-Koordinaten.
 */
public final class BreathFog {

    /** Eine Wolke: Lage, Geschwindigkeit, Alter, Lebensdauer, Halbmesser, Dichte. */
    public static final class Puff {
        public final double[] p = new double[3], v = new double[3];
        public double age, life, r, dens, seed;
    }

    private final List<Puff> puffs = new ArrayList<>();
    private final Person.Rng rng;
    private double carry;
    /** Höchstzahl. */
    public int max = 400;
    /** Zähler (Prüfung): ausgestoßene Wolken. */
    public long emitted;

    public BreathFog(long seed) { rng = new Person.Rng(seed * 31 + 0xB4EA7L); }

    public List<Puff> puffs() { return puffs; }

    /**
     * Ein Schritt. {@code flow}: Atemstrom 0…1 (nur beim Ausatmen > 0); {@code at}: Austritt (Nase oder
     * Mund) und {@code dir}: Richtung des Stroms (Einheit), beides Figur-Koordinaten; {@code fog}: Sichtbarkeit
     * aus dem Klima; {@code wind}: m/s.
     */
    public void step(double dt, double flow, double[] at, double[] dir, double fog, double[] wind) {
        if (dt <= 0) return;
        // ausstoßen: bis 60 Wolken je Sekunde bei vollem Strom
        if (flow > 0.02 && fog > 0.01) {
            carry += dt * 60 * flow;
            while (carry >= 1) {
                carry -= 1;
                Puff q = new Puff();
                double sp = 0.6 + 0.7 * flow;
                for (int k = 0; k < 3; k++) {
                    q.p[k] = at[k] + 0.004 * rng.gauss();
                    q.v[k] = dir[k] * sp + 0.12 * rng.gauss();
                }
                q.life = 0.7 + 0.9 * rng.next() * (0.5 + fog);
                q.r = 0.008 + 0.006 * rng.next();
                q.dens = fog * (0.5 + 0.5 * flow) * (0.7 + 0.6 * rng.next());
                q.seed = rng.next();
                puffs.add(q);
                emitted++;
            }
        } else carry = 0;
        for (int i = puffs.size() - 1; i >= 0; i--) {
            Puff q = puffs.get(i);
            q.age += dt;
            if (q.age > q.life) { puffs.remove(i); continue; }
            double drag = 1 - Math.exp(-dt * 3.0);              // die Luft bremst den Strahl
            for (int k = 0; k < 3; k++) q.v[k] += ((k == 1 ? 0.06 : 0) + wind[k] - q.v[k]) * drag;
            for (int k = 0; k < 3; k++) q.p[k] += q.v[k] * dt;
            q.r += dt * (0.03 + 0.02 * q.seed);                 // weitet sich
        }
        while (puffs.size() > max) puffs.remove(0);
    }

    /** Sichtbarkeit einer Wolke jetzt (0…1): schnell da, langsam fort. */
    public static double alpha(Puff q) {
        double x = q.age / q.life;
        return q.dens * Math.min(1, x / 0.08) * (1 - x) * (1 - x);
    }

    public void clear() { puffs.clear(); carry = 0; }

    /** Für die Grafikkarte: je Wolke {x, y, z, Halbmesser, Deckung, seed}. */
    public float[] data() {
        float[] out = new float[puffs.size() * 6];
        int k = 0;
        for (Puff q : puffs) {
            out[k++] = (float) q.p[0]; out[k++] = (float) q.p[1]; out[k++] = (float) q.p[2];
            out[k++] = (float) q.r; out[k++] = (float) alpha(q); out[k++] = (float) q.seed;
        }
        return out;
    }
}
