package com.dan.voice;

/**
 * Ein Laut: Zeichen (angelehnt an SAMPA), Art, Mundbild (bei Doppellauten ein zweites für die zweite Hälfte), Dauer
 * in ms bei normalem Tempo, betont oder nicht, Nummer des Worts (−1 für Pausen).
 */
public record Phon(String sym, Kind kind, Viseme v, Viseme v2, double ms, boolean stressed, int word) {

    /** Art des Lauts (für Dauer, Stärke und die Zuordnung zum Ton). */
    public enum Kind { PAUSE, VOWEL, PLOSIVE, FRICATIVE, NASAL, LIQUID, H }

    public Phon(String sym, Kind kind, Viseme v, double ms, boolean stressed, int word) { this(sym, kind, v, null, ms, stressed, word); }

    public boolean vowel() { return kind == Kind.VOWEL; }

    public boolean pause() { return kind == Kind.PAUSE; }

    /** Derselbe Laut mit anderer Dauer. */
    public Phon withMs(double d) { return new Phon(sym, kind, v, v2, d, stressed, word); }

    @Override public String toString() { return sym + (stressed ? "'" : ""); }
}
