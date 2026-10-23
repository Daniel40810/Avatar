package com.dan.avatar;

/**
 * Nachbewegung loser Teile (Phase 10, F11 A: keine Stoffsimulation): eine gedämpfte Feder je Person, die
 * dem Wind und der Bewegung des Oberkörpers folgt, und ein Flattern im Wind. Die Grafikkarte verschiebt
 * jede Ecke um {@code flex · (sway + flutter · sin(…))}, {@code flex} ist die Lose der Ecke (0 am Körper,
 * 1 frei hängend). Feder ω = 7 1/s, Dämpfung 0,35 [G]; Auslenkung durch Wind ∝ v² (Staudruck), höchstens
 * 6 cm.
 */
public final class ClothSway {

    /** Auslenkung (m, Figur-Koordinaten) und Geschwindigkeit der Feder. */
    public final double[] sway = new double[3], vel = new double[3];
    /** Amplitude des Flatterns (m, je Achse). */
    public final double[] flutter = new double[3];
    private final double[] lastAnchor = new double[3], lastVel = new double[3];
    private boolean first = true;
    public double omega = 7, zeta = 0.35;

    /**
     * Ein Schritt. {@code anchor}: Lage des Oberkörpers (Figur-Koordinaten, m), {@code wind}: Wind (m/s,
     * Figur-Koordinaten), {@code dt}: Zeitschritt (s).
     */
    public void step(double dt, double[] anchor, double[] wind) {
        if (dt <= 0) return;
        dt = Math.min(dt, 0.05);
        double[] acc = new double[3];
        if (first) { System.arraycopy(anchor, 0, lastAnchor, 0, 3); first = false; }
        for (int k = 0; k < 3; k++) {
            double v = (anchor[k] - lastAnchor[k]) / dt;
            acc[k] = (v - lastVel[k]) / dt;
            lastVel[k] = v;
            lastAnchor[k] = anchor[k];
        }
        double ws = Math.sqrt(wind[0] * wind[0] + wind[2] * wind[2]);
        // Ziel: der Wind drückt (Staudruck ∝ v²), die Beschleunigung des Körpers lässt das Lose zurück
        double push = Math.min(0.06, 0.00022 * ws * ws);
        double[] target = new double[3];
        for (int k = 0; k < 3; k++) {
            double wdir = ws > 1e-6 ? wind[k] / ws : 0;
            target[k] = push * wdir - Math.max(-0.03, Math.min(0.03, acc[k] / (omega * omega)));
        }
        target[1] = -0.3 * push;                                  // der Wind drückt das Lose etwas an den Körper
        int sub = (int) Math.ceil(dt / 0.005);
        double h = dt / sub;
        for (int s = 0; s < sub; s++)
            for (int k = 0; k < 3; k++) {
                double a = omega * omega * (target[k] - sway[k]) - 2 * zeta * omega * vel[k];
                vel[k] += a * h;
                sway[k] += vel[k] * h;
            }
        double fl = Math.min(0.012, 0.0006 * ws * ws / 10 + 0.0004 * ws);
        flutter[0] = fl * 0.6; flutter[1] = fl * 0.5; flutter[2] = fl;
    }

    /** Wie weit die Feder ausgelenkt ist (m). */
    public double amount() { return Math.sqrt(sway[0] * sway[0] + sway[1] * sway[1] + sway[2] * sway[2]); }

    /** Lose einer Ecke aus ihrem Abstand {@code d} zum Körper (m): 0 bis 1,2 cm, 1 ab 7 cm (glatt). */
    public static double flex(double d) {
        double t = Math.max(0, Math.min(1, (d - 0.012) / (0.07 - 0.012)));
        return t * t * (3 - 2 * t);
    }
}
