package com.dan.portrait;

/**
 * Das Objektiv (Phase 9): Brennweite, Blende, Fokus, Blendenform, Kleinbild (36 × 24 mm). Rechnet
 * Bildwinkel und Zerstreuungskreise nach der Linsengleichung:
 * <pre>
 *   c = f² / N · |z − s| / (z · (s − f))      (Durchmesser auf dem Sensor, alles in Metern)
 * </pre>
 * mit Brennweite f, Blendenzahl N, Fokusabstand s und Abstand z des Punkts.
 */
public final class Lens {

    /** Brennweite (mm). */
    public double focalMm = 85;
    /** Blendenzahl. */
    public double fNumber = 1.8;
    /** Fokusabstand (m). */
    public double focusM = 0.8;
    /** Lamellen (0 = rund), Rundung der Ecken 0…1, Drehung (Grad). */
    public int blades = 0;
    public double roundness = 0.35, bladeRotDeg = 12;
    /** Sensorhöhe (m), Kleinbild. */
    public static final double SENSOR_H = 0.024;

    public Lens copy() {
        Lens l = new Lens();
        l.focalMm = focalMm; l.fNumber = fNumber; l.focusM = focusM; l.blades = blades; l.roundness = roundness; l.bladeRotDeg = bladeRotDeg;
        return l;
    }

    /** Senkrechter Bildwinkel (Grad). */
    public double fovyDeg() { return Math.toDegrees(2 * Math.atan(SENSOR_H / 2 / (focalMm / 1000))); }

    /** Brennweite in Bildpunkten für ein Bild der Höhe {@code h}. */
    public double focalPx(int h) { return h * (focalMm / 1000) / SENSOR_H; }

    /** Faktor K (Bildpunkte) mit cocPx(z) = K · |z − s| / z. */
    public double cocK(int h) {
        double f = focalMm / 1000, s = Math.max(focusM, f * 1.01);
        return f * f / fNumber / (s - f) / SENSOR_H * h;
    }

    /** Durchmesser des Zerstreuungskreises (Bildpunkte) für einen Punkt im Abstand {@code z} (m). */
    public double cocPx(double z, int h) { return cocK(h) * Math.abs(z - focusM) / Math.max(z, 1e-3); }

    /** Schärfentiefe (m): nahe und ferne Grenze für einen zulässigen Kreis von {@code cPx} Bildpunkten. */
    public double[] depthOfField(double cPx, int h) {
        double f = focalMm / 1000, s = focusM, c = cPx / h * SENSOR_H;
        double H = f * f / (fNumber * c) + f;
        double near = s * (H - f) / (H + s - 2 * f), far = s >= H ? Double.POSITIVE_INFINITY : s * (H - f) / (H - s);
        return new double[]{near, far};
    }

    @Override public String toString() {
        return String.format(java.util.Locale.GERMANY, "%.0f mm · f/%.1f · Fokus %.2f m%s", focalMm, fNumber, focusM, blades == 0 ? " · rund" : " · " + blades + " Lamellen");
    }
}
