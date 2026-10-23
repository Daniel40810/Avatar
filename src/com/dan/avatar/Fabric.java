package com.dan.avatar;

/**
 * Stoffe (Phase 10, F11 A): wie ein Stoff aussieht und wie er mit Wasser umgeht. Werte sind gesetzt [G],
 * angelehnt an das Aussehen der Stoffe: Wolle und Strick mit Flaum (Glanz am Rand, „Sheen“), Leinen
 * matt mit grober Bindung, Leder mit weichem Glanz, Ölzeug und Gummi glatt und wasserdicht.
 *
 * <ul>
 *   <li>{@code rough}: Rauheit der Oberfläche (0 glatt … 1 stumpf)</li>
 *   <li>{@code sheen}: Flaum, der im Streiflicht leuchtet (0 … 1)</li>
 *   <li>{@code weave}: Muster 0 keins, 1 Strick (Maschen), 2 Köper (Denim), 3 Leinwand, 4 Narben (Leder)</li>
 *   <li>{@code scale}: Wiederholungen des Musters je UV-Einheit</li>
 *   <li>{@code absorb}: wie stark Wasser einzieht und dunkelt (0 wasserdicht … 1 saugt)</li>
 *   <li>{@code beading}: wie viel Wasser als Perlen auf der Oberfläche bleibt (Lanolin, Beschichtung)</li>
 *   <li>{@code wetGloss}: Glanz des Wasserfilms, wenn nass</li>
 * </ul>
 */
public enum Fabric {
    BAUMWOLLE("Baumwolle", 0.85, 0.25, 3, 90, 1.00, 0.05, 0.30, 0.52, 0.52, 0.52),
    STRICK("Strick", 0.90, 0.70, 1, 70, 0.55, 0.60, 0.45, 0.10, 0.11, 0.12),
    WOLLE("Wolle (Loden)", 0.92, 0.55, 3, 140, 0.45, 0.55, 0.40, 0.18, 0.15, 0.11),
    LEINEN("Leinen", 0.88, 0.15, 3, 60, 0.95, 0.05, 0.30, 0.55, 0.48, 0.36),
    DENIM("Denim", 0.80, 0.20, 2, 110, 0.85, 0.05, 0.30, 0.10, 0.16, 0.30),
    LEDER("Leder", 0.45, 0.10, 4, 40, 0.25, 0.30, 0.60, 0.16, 0.09, 0.05),
    OELZEUG("Ölzeug", 0.22, 0.00, 0, 1, 0.00, 0.85, 0.95, 0.75, 0.55, 0.04),
    GUMMI("Gummi", 0.30, 0.00, 0, 1, 0.00, 0.70, 0.90, 0.08, 0.20, 0.10);

    public final String label;
    public final double rough, sheen, scale, absorb, beading, wetGloss;
    public final int weave;
    /** Farbe, wenn das Outfit keine vorgibt (linear RGB). */
    public final double[] color;

    Fabric(String label, double rough, double sheen, int weave, double scale, double absorb, double beading, double wetGloss,
           double r, double g, double b) {
        this.label = label; this.rough = rough; this.sheen = sheen; this.weave = weave; this.scale = scale;
        this.absorb = absorb; this.beading = beading; this.wetGloss = wetGloss; this.color = new double[]{r, g, b};
    }

    @Override public String toString() { return label; }
}
