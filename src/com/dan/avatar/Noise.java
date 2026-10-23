package com.dan.avatar;

/**
 * Glattes Rauschen in der Zeit (Catmull-Rom durch zufällige Stützwerte), gleich auf jeder Maschine.
 * Für Mikrobewegungen: Kopf, Schwanken. Werte etwa in −1…+1.
 */
final class Noise {

    private final long seed;
    private final double rate;

    /** {@code rate}: Stützwerte je Sekunde (etwa die obere Frequenz). */
    Noise(long seed, double rate) { this.seed = seed; this.rate = rate; }

    double at(double t) {
        double x = t * rate;
        long i = (long) Math.floor(x);
        double f = x - i;
        double p0 = knot(i - 1), p1 = knot(i), p2 = knot(i + 1), p3 = knot(i + 2);
        return 0.5 * ((2 * p1) + (-p0 + p2) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f * f + (-p0 + 3 * p1 - 3 * p2 + p3) * f * f * f);
    }

    private double knot(long i) {
        long z = seed * 0x9E3779B97F4A7C15L + i * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return ((z >>> 11) * 0x1.0p-53) * 2 - 1;
    }
}
