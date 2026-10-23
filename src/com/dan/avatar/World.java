package com.dan.avatar;

/**
 * Die Szene, wie der Avatar sie sieht (Phase 13, Schnittstelle zur Szene): Boden, Klima, benannte Orte und
 * die Kamera. Jede Szene (Studio, Steg, Lighthouse, Rungholt …) gibt eine {@code World}; der Avatar fragt nur
 * diese Werte ab und kennt sonst nichts von ihr. Alle Methoden haben eine Vorgabe, eine leere Szene ist
 * {@code new World() {}}: flacher Boden auf 0, kein eigenes Klima, keine Orte.
 *
 * <p>Koordinaten: Meter, y nach oben, Richtung 0 = Blick nach +z (wie {@link Motion}).
 */
public interface World {

    /** Höhe des Bodens am Ort (m). Die Füße stehen darauf. */
    default double ground(double x, double z) { return 0; }

    /**
     * Klima am Ort (Regen in mm/h, Wind, Temperatur, Feuchte, Schutz); {@code null} heißt: das gemeinsame
     * Klima der Szene. Eine Szene mit Dach (Laterne, Haus) gibt darunter weniger Regen.
     */
    default Climate climateAt(double x, double y, double z) { return null; }

    /** Benannter Ort {x, y, z} für die Regie („gehe Steg_Ende“, „blick Wasser“) oder {@code null}. */
    default double[] place(String name) { return null; }

    /** Namen der Orte (für Bedienfeld und Prüfung). */
    default java.util.List<String> places() { return java.util.List.of(); }

    /** Auge der Kamera {x, y, z} (Blick „Kamera“) oder {@code null}. */
    default double[] camera() { return null; }

    /** Flache Szene ohne Orte. */
    World FLAT = new World() { };
}
