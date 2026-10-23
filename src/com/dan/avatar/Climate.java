package com.dan.avatar;

/**
 * Die Umwelt am Ort der Person (Phase 8, F13 A): Regen (mm/h, schon um Unterstände gemindert), Wind
 * (m/s, Figur-Koordinaten), Temperatur, Luftfeuchte und Kapuze. Daraus: gefühlte Temperatur
 * (Windchill), Kälte 0…1 und wie gut der Atemhauch zu sehen ist. Werte, die ich gesetzt habe, sind
 * [G] markiert.
 */
public final class Climate {

    /** Regen am Ort (mm/h). */
    public double rain;
    /** Wind (m/s) in Figur-Koordinaten (+x links der Person, +z vor ihr). */
    public double[] wind = {0, 0, 0};
    /** Lufttemperatur (°C). */
    public double tempC = 18;
    /** Relative Feuchte 0…1. */
    public double humidity = 0.6;
    /** Kapuze auf (0…1). Bis Phase 10 nur als Schutz, ohne eigenes Netz. */
    public double hood;

    public Climate copy() {
        Climate c = new Climate();
        c.rain = rain; c.wind = wind.clone(); c.tempC = tempC; c.humidity = humidity; c.hood = hood;
        return c;
    }

    /** Windgeschwindigkeit (m/s, waagerecht). */
    public double windSpeed() { return Math.hypot(wind[0], wind[2]); }

    /**
     * Gefühlte Temperatur nach der Windchill-Formel (Umweltbehörden der USA und Kanadas, 2001), gültig
     * unter 10 °C und über 4,8 km/h; sonst die Lufttemperatur.
     */
    public double windChill() {
        double v = windSpeed() * 3.6;
        if (tempC > 10 || v < 4.8) return tempC;
        double p = Math.pow(v, 0.16);
        return 13.12 + 0.6215 * tempC - 11.37 * p + 0.3965 * tempC * p;
    }

    /** Kälte 0…1 aus gefühlter Temperatur und nasser Haut [G]: ab 12 °C beginnt sie, bei −8 °C ist sie voll. */
    public double coldness(double wetSkin) {
        double t = windChill() - 5 * wetSkin * Math.min(1, 0.3 + windSpeed() / 6);
        return clamp((12 - t) / 20, 0, 1);
    }

    /**
     * Wie gut man den Atem sieht (0…1) [G]: Ausgeatmete Luft (gut 34 °C, gesättigt) mischt sich mit der
     * Umgebung; Nebel entsteht, wenn die Mischung übersättigt ist. Bei feuchter Luft schon unter etwa
     * 10 °C, bei trockener erst um 2 °C.
     */
    public double breathFog() {
        double tc = 2 + 8 * humidity;
        return clamp((tc - tempC) / 9, 0, 1) * (0.55 + 0.45 * humidity);
    }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    // ------------------------------------------------------------ Voreinstellungen (Block UMWELT)

    /** Regennacht wie die Vorlage: 5 mm/h, leichter Wind, 9 °C, feucht. */
    public static Climate regennacht() { return of(5, 3, 9, 0.95, 0); }
    /** Sturm an der Küste: 18 mm/h, 17 m/s (Bft 8), 8 °C. */
    public static Climate sturm() { return of(18, 17, 8, 0.97, 0); }
    /** Wintermorgen: trocken, −6 °C, schwacher Wind, der Atem steht in der Luft. */
    public static Climate wintermorgen() { return of(0, 1.8, -6, 0.85, 0); }
    /** Sommerabend: trocken, 24 °C, laue Luft. */
    public static Climate sommerabend() { return of(0, 1.2, 24, 0.55, 0); }

    /** Regen, Wind schräg von vorn links (aus Sicht der Person), Temperatur, Feuchte, Kapuze. */
    public static Climate of(double rain, double wind, double tempC, double humidity, double hood) {
        Climate c = new Climate();
        c.rain = rain; c.wind = new double[]{-wind * 0.5, 0, -wind * 0.85}; c.tempC = tempC; c.humidity = humidity; c.hood = hood;
        return c;
    }

    @Override public String toString() {
        return String.format(java.util.Locale.GERMANY, "%.1f mm/h · %.1f m/s · %.0f °C (gefühlt %.0f) · %.0f %%%s",
                rain, windSpeed(), tempC, windChill(), humidity * 100, hood > 0.5 ? " · Kapuze" : "");
    }
}
