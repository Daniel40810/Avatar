package com.dan.avatarstudio;

/** Qualitätsstufen des Studios; ab Phase 2 regelt jedes Package seine eigene Stufe (Paketvertrag Punkt 7). */
public enum Quality {
    LOW("Niedrig"), MEDIUM("Mittel"), HIGH("Hoch"), ULTRA("Ultra");

    public final String label;

    Quality(String label) { this.label = label; }

    @Override
    public String toString() { return label; }
}
