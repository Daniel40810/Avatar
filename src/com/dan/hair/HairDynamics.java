package com.dan.hair;

/**
 * Bewegung der Haare ohne Simulation einzelner Haare (F10 A, Federkette in einfachster Form): die
 * Spitzen folgen dem Kopf über eine gedämpfte Feder, dazu Wind mit Böen. Ergebnis ist ein Versatz
 * (m) für die Spitze; der Shader verteilt ihn quadratisch über die Länge (Wurzel fest).
 *
 * <pre>
 *   HairDynamics d = new HairDynamics();
 *   d.step(dt, kopfLage, windVektor, zeit);
 *   float[] tip = d.tipOffset();     // an HairGl
 * </pre>
 */
public final class HairDynamics {

    /** Federsteife (1/s²) und Dämpfung (1/s). */
    public double stiffness = 60, damping = 7;
    /** Wie stark der Wind die Spitzen biegt (m je m/s). */
    public double windBend = 0.02;
    private final double[] off = new double[3], vel = new double[3], lastHead = new double[3], lastVel = new double[3];
    private boolean first = true;
    private final float[] tip = new float[3], gust = new float[3];

    /**
     * Ein Schritt. {@code head}: Lage des Kopfes (Welt, m); {@code wind}: Windgeschwindigkeit (m/s, Welt);
     * {@code t}: Zeit (s) für die Böen.
     */
    public void step(double dt, double[] head, double[] wind, double t) {
        if (first || dt <= 0 || dt > 0.2) {
            System.arraycopy(head, 0, lastHead, 0, 3);
            first = false;
            dt = Math.max(dt, 1e-3);
        }
        double[] hv = new double[3], acc = new double[3];
        for (int k = 0; k < 3; k++) {
            hv[k] = (head[k] - lastHead[k]) / dt;
            acc[k] = (hv[k] - lastVel[k]) / dt;
            lastVel[k] = hv[k];
            lastHead[k] = head[k];
        }
        // Feder: Versatz o der Spitze relativ zum Kopf; Kopfbeschleunigung wirkt als Trägheit (−a)
        int sub = Math.max(1, (int) Math.ceil(dt / 0.004));
        double h = dt / sub;
        for (int s = 0; s < sub; s++)
            for (int k = 0; k < 3; k++) {
                double a = -stiffness * off[k] - damping * vel[k] - Math.max(-30, Math.min(30, acc[k])) * 0.02;
                vel[k] += a * h;
                off[k] += vel[k] * h;
            }
        // Wind mit Böen (zwei langsame Schwingungen und eine schnelle)
        double speed = Math.sqrt(wind[0] * wind[0] + wind[1] * wind[1] + wind[2] * wind[2]);
        double g = 0.7 + 0.2 * Math.sin(t * 0.9) + 0.1 * Math.sin(t * 2.3 + 1.7) + 0.08 * Math.sin(t * 7.1);
        for (int k = 0; k < 3; k++) {
            tip[k] = (float) (off[k] + wind[k] * windBend * g);
            gust[k] = (float) (wind[k] * windBend * 0.25 * Math.sin(t * 5.3 + k));
        }
        if (speed == 0) java.util.Arrays.fill(gust, 0);
    }

    /** Versatz der Spitzen (m). */
    public float[] tipOffset() { return tip.clone(); }

    /** Flattern (m), wird im Shader je Haar mit einer eigenen Phase angewendet. */
    public float[] flutter() { return gust.clone(); }

    /** Zurück in die Ruhe (Standbilder). */
    public void reset() {
        java.util.Arrays.fill(off, 0);
        java.util.Arrays.fill(vel, 0);
        first = true;
    }
}
