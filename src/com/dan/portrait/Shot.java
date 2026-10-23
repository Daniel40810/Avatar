package com.dan.portrait;

/**
 * Einstellungen für Menschen (Werkbuch Abschnitt 6): wie viel vom Menschen das Bild senkrecht zeigt
 * und wohin die Bildmitte relativ zum Kopf rückt. Aus Bildwinkel und Bildhöhe folgt der Abstand.
 */
public enum Shot {
    GROSS("Groß (Augen)", 0.13, -0.005),
    NAH("Nah (Kopf)", 0.34, -0.03),
    HALBNAH("Halbnah (bis Brust)", 0.62, -0.17),
    AMERIKANISCH("Amerikanisch", 1.15, -0.42),
    HALBTOTALE("Halbtotale", 2.05, -0.78);

    public final String label;
    /** Gezeigte Höhe am Ort der Person (m) und Versatz der Bildmitte zum Kopf (m, senkrecht). */
    public final double frameH, offsetY;

    Shot(String label, double frameH, double offsetY) { this.label = label; this.frameH = frameH; this.offsetY = offsetY; }

    /** Abstand der Kamera (m) für einen senkrechten Bildwinkel. */
    public double distance(double fovyDeg) { return frameH / 2 / Math.tan(Math.toRadians(fovyDeg) / 2); }

    @Override public String toString() { return label; }
}
