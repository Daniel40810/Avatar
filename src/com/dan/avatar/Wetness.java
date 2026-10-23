package com.dan.avatar;

/**
 * Nässe je Zone (Phase 8, F13 A). Jede Zone speichert Wasser (mm Film) bis zu ihrer Aufnahme; was
 * darüber hinaus kommt, läuft ab und nässt die Zone darunter (Haar → Gesicht, Nacken, Schultern;
 * Gesicht → Brust …). Regen trifft eine Fläche nach ihrer Lage zur Fallrichtung (Tropfen von 2 mm
 * fallen mit 6,5 m/s, der Wind treibt sie schräg). Die Kapuze schützt Kopf und Haar. Trocknen nach
 * Wind, Temperatur und Feuchte. Werte [G], wo nicht anders gesagt.
 *
 * <pre>
 *   Wetness w = new Wetness();
 *   w.step(dt, climate);
 *   double gesicht = w.wet(Wetness.FACE);   // 0…1
 * </pre>
 */
public final class Wetness {

    public static final int SCALP = 0, FACE = 1, HAIR = 2, SHOULDERS = 3, CHEST = 4, BACK = 5, ARMS = 6, LEGS = 7;
    public static final String[] NAMES = {"Kopfhaut", "Gesicht", "Haar", "Schultern", "Brust", "Rücken", "Arme", "Beine"};
    public static final int N = 8;

    /** Aufnahme je Zone (mm Wasserfilm, bezogen auf die Fläche der Zone) [G]. */
    public final double[] capacity = {0.15, 0.07, 1.2, 0.6, 0.55, 0.55, 0.5, 0.7};
    /** Faktor fürs Trocknen (Haut ist warm, Stoff und Haar trocknen langsam) [G]. */
    private static final double[] DRY = {0.6, 2.0, 0.35, 0.5, 0.55, 0.55, 0.6, 0.45};
    /** Fläche (Normale in Figur-Koordinaten, Anteil) je Zone. */
    private static final double[][][] FACES = {
        {{0, 1, 0, 1}},                                                     // Kopfhaut (unter dem Haar)
        {{0, 0, 1, 0.65}, {0, 0.55, 0.83, 0.25}, {0, -0.6, 0.8, 0.10}},   // Gesicht: vorn, Stirn und Nase, Kinn
        {{0, 1, 0, 0.45}, {0, 0.2, -0.98, 0.25}, {1, 0.2, 0, 0.15}, {-1, 0.2, 0, 0.15}},
        {{0, 1, 0, 0.6}, {1, 0.3, 0, 0.2}, {-1, 0.3, 0, 0.2}},
        {{0, 0, 1, 1}},
        {{0, 0, -1, 1}},
        {{1, 0, 0, 0.45}, {-1, 0, 0, 0.45}, {0, 0, 1, 0.1}},
        {{0, 0, 1, 0.35}, {0, 0, -1, 0.35}, {1, 0, 0, 0.15}, {-1, 0, 0, 0.15}},
    };
    /** Schatten durch Körperteile darüber (Beine unter dem Oberkörper), Kapuzenschutz je Zone. */
    private static final double[] SHADE = {1, 1, 1, 1, 0.9, 0.9, 0.85, 0.55};
    private static final double[] HOOD = {0.97, 0.78, 0.95, 0.35, 0, 0.1, 0, 0};
    /** Ablauf: wohin überschüssiges Wasser fließt {von, nach, Anteil}. */
    private static final double[][] RUNOFF = {
        {HAIR, FACE, 0.20}, {HAIR, SHOULDERS, 0.35}, {HAIR, BACK, 0.35}, {HAIR, SCALP, 0.10},
        {SCALP, FACE, 0.5}, {SCALP, HAIR, 0.5},
        {FACE, CHEST, 0.8},
        {SHOULDERS, CHEST, 0.3}, {SHOULDERS, BACK, 0.3}, {SHOULDERS, ARMS, 0.4},
        {CHEST, LEGS, 0.6}, {BACK, LEGS, 0.6}, {ARMS, LEGS, 0.3}};

    private final double[] water = new double[N];
    private final double[] inflow = new double[N];
    /** Mit Haaren auf dem Kopf (sonst nimmt die Kopfhaut den Regen direkt). */
    public boolean hair = true;
    /** Fallgeschwindigkeit der Tropfen (m/s), Tropfen von etwa 2 mm (Atlas). */
    public double fallSpeed = 6.5;
    private double time;

    /** Nässe 0…1 einer Zone. */
    public double wet(int zone) { return Math.min(1, water[zone] / capacity[zone]); }

    /** Wasser (mm) einer Zone. */
    public double water(int zone) { return water[zone]; }

    /** Regen, der gerade auf die Zone trifft (mm/h, auf ihre Fläche bezogen). */
    public double inflow(int zone) { return inflow[zone] * 3600; }

    /** Alles trocken. */
    public void dry() { java.util.Arrays.fill(water, 0); }

    /** Alles nass (Ausgang für Standbilder). */
    public void soak(double level) { for (int i = 0; i < N; i++) water[i] = capacity[i] * level; }

    public double time() { return time; }

    /** Richtung, aus der der Regen kommt (Einheitsvektor, Figur-Koordinaten). */
    public double[] rainFrom(Climate c) {
        double x = -c.wind[0], y = fallSpeed, z = -c.wind[2];
        double l = Math.sqrt(x * x + y * y + z * z);
        return new double[]{x / l, y / l, z / l};
    }

    /** Anteil des Regens auf einer Fläche mit Normale {@code n} (bezogen auf die waagerechte Menge). */
    public double exposure(double[] n, Climate c) {
        double[] f = rainFrom(c);
        double d = n[0] * f[0] + n[1] * f[1] + n[2] * f[2];
        return Math.max(0, d) / Math.max(0.2, f[1]) + 0.02;   // etwas Spritzwasser immer
    }

    /** Ein Zeitschritt (s). */
    public void step(double dt, Climate c) {
        if (dt <= 0) return;
        time += dt;
        double[] add = new double[N];
        for (int z = 0; z < N; z++) {
            double e = 0;
            for (double[] f : FACES[z]) {
                double l = Math.sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]);
                e += f[3] * exposure(new double[]{f[0] / l, f[1] / l, f[2] / l}, c);
            }
            e *= SHADE[z] * (1 - HOOD[z] * Climate.clamp(c.hood, 0, 1));
            if (z == SCALP && hair) e *= 0.05;         // das Haar hält den Regen zuerst
            if (z == HAIR && !hair) e = 0;
            inflow[z] = c.rain / 3600.0 * e;           // mm/s
            add[z] += inflow[z] * dt;
        }
        // Trocknen [G]: 0,35 mm/h bei 20 °C, trockener Luft und Windstille, schneller mit Wind und Wärme
        double evap = 0.35 / 3600 * (1 + 0.5 * c.windSpeed()) * Math.max(0.02, 1 - c.humidity) * Math.pow(2, (c.tempC - 20) / 12);
        for (int z = 0; z < N; z++) {
            double cap = capacity[z], s = water[z];
            double room = 1 - Math.pow(Math.min(1, s / cap), 3);
            double keep = add[z] * room, over = add[z] - keep;
            water[z] = Math.max(0, s + keep - evap * DRY[z] * dt * Math.sqrt(Math.min(1, s / cap + 0.05)));
            if (water[z] > cap) { over += water[z] - cap; water[z] = cap; }
            if (over > 0) for (double[] r : RUNOFF) if ((int) r[0] == z) water[(int) r[1]] = Math.min(capacity[(int) r[1]], water[(int) r[1]] + over * r[2]);
        }
    }

    @Override public String toString() {
        StringBuilder b = new StringBuilder();
        for (int z = 0; z < N; z++) b.append(z == 0 ? "" : " · ").append(NAMES[z]).append(String.format(java.util.Locale.GERMANY, " %.0f %%", wet(z) * 100));
        return b.toString();
    }
}
