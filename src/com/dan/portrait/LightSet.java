package com.dan.portrait;

/**
 * Lichtsetzung für ein Porträt (Phase 9): Führungslicht (Spot mit Kegel), Kantenlicht von hinten,
 * Aufhellung als Umgebung; Lagen relativ zu Kopf und Kamera, Farben vorbelichtet (linear). Kennt
 * keinen Avatar; die Szene übernimmt die Zahlen in ihr eigenes Licht.
 */
public final class LightSet {

    public final String name;
    public final float[] keyPos = new float[3], keyDir = new float[3], keyCol = new float[3];
    public float keyConeInner = 16, keyConeOuter = 34;
    public final float[] rimPos = new float[3], rimCol = new float[3], fillCol = new float[3];

    private LightSet(String name) { this.name = name; }

    /** Namen der Lichtsetzungen. */
    public static final String[] NAMES = {"Regennacht", "Porträt", "Rembrandt", "Abendsonne", "Nebel", "Gegenlicht"};

    /**
     * Lichtsetzung für einen Kopf bei {@code head} und eine Kamera bei {@code eye} (Welt, m). Die Lagen
     * sind im Rahmen der Kamera gesetzt (links und rechts aus Sicht der Kamera), damit das Licht beim
     * Umkreisen mitgeht wie im Fotostudio.
     */
    public static LightSet of(String name, double[] head, double[] eye) {
        double[] f = norm(new double[]{head[0] - eye[0], 0, head[2] - eye[2]});     // Blick der Kamera (waagerecht)
        double[] r = {-f[2], 0, f[0]};                                                // rechts aus Sicht der Kamera
        LightSet s = new LightSet(name);
        switch (name) {
            case "Regennacht" -> {
                // weiches, kühles Licht von vorn oben (wie eine Straßenlaterne vor der Person), warmes Kantenlicht
                // von hinten links (die Lichter der Stadt), Aufhellung in Türkis
                s.set(s.keyPos, head, f, r, -0.95, 0.30, 0.95);
                s.col(s.keyCol, 0.95, 0.90, 0.88);
                s.keyConeInner = 24; s.keyConeOuter = 50;
                s.set(s.rimPos, head, f, r, 1.4, 0.4, -0.9);
                s.col(s.rimCol, 3.4, 1.9, 0.7);
                s.col(s.fillCol, 0.020, 0.048, 0.058);
            }
            case "Rembrandt" -> {
                s.set(s.keyPos, head, f, r, -1.1, 0.6, -0.95);
                s.col(s.keyCol, 4.6, 3.4, 2.3);
                s.keyConeInner = 14; s.keyConeOuter = 30;
                s.set(s.rimPos, head, f, r, 1.0, 0.5, 1.1);
                s.col(s.rimCol, 0.35, 0.55, 0.7);
                s.col(s.fillCol, 0.010, 0.012, 0.016);
            }
            case "Abendsonne" -> {
                s.set(s.keyPos, head, f, r, -1.5, -0.05, -1.4);
                s.col(s.keyCol, 6.0, 3.3, 1.4);
                s.keyConeInner = 20; s.keyConeOuter = 40;
                s.set(s.rimPos, head, f, r, 1.2, 0.3, 1.2);
                s.col(s.rimCol, 0.6, 1.0, 1.6);
                s.col(s.fillCol, 0.030, 0.034, 0.055);
            }
            case "Nebel" -> {
                s.set(s.keyPos, head, f, r, -1.2, 0.5, 0.2);
                s.col(s.keyCol, 2.4, 2.5, 2.6);
                s.keyConeInner = 30; s.keyConeOuter = 60;
                s.set(s.rimPos, head, f, r, 1.1, 0.4, -1.0);
                s.col(s.rimCol, 0.9, 1.0, 1.05);
                s.col(s.fillCol, 0.055, 0.060, 0.066);
            }
            case "Gegenlicht" -> {
                s.set(s.keyPos, head, f, r, 0.25, 0.15, -1.3);
                s.col(s.keyCol, 4.4, 3.2, 2.1);
                s.keyConeInner = 16; s.keyConeOuter = 32;
                s.set(s.rimPos, head, f, r, -1.1, 0.15, 1.5);
                s.col(s.rimCol, 0.45, 1.25, 1.45);
                s.col(s.fillCol, 0.022, 0.030, 0.040);
            }
            default -> {    // „Porträt“: klassisch 45° vorn, Kante von hinten
                s.set(s.keyPos, head, f, r, -0.95, 0.10, 1.50);
                s.col(s.keyCol, 4.2, 3.0, 2.0);
                s.set(s.rimPos, head, f, r, 0.85, 0.35, -1.05);
                s.col(s.rimCol, 0.5, 1.6, 1.8);
                s.col(s.fillCol, 0.030, 0.038, 0.048);
            }
        }
        for (int k = 0; k < 3; k++) s.keyDir[k] = (float) (head[k] - s.keyPos[k]);
        return s;
    }

    /** Lage: {@code side} nach rechts (Kamera), {@code up} nach oben, {@code toward} zur Kamera hin (m). */
    private void set(float[] p, double[] head, double[] f, double[] r, double side, double up, double toward) {
        for (int k = 0; k < 3; k++) p[k] = (float) (head[k] + r[k] * side - f[k] * toward + (k == 1 ? up : 0));
    }

    private void col(float[] c, double r, double g, double b) { c[0] = (float) r; c[1] = (float) g; c[2] = (float) b; }

    private static double[] norm(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return l < 1e-9 ? new double[]{0, 0, -1} : new double[]{v[0] / l, v[1] / l, v[2] / l};
    }
}
