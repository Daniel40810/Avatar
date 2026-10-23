package com.dan.voice;

/**
 * Die 15 Mundbilder (Viseme) nach dem Oculus-Satz, wie sie der Grundkörper des Avatars als Formen trägt
 * ({@code visem.viseme_PP} …). Jedes Visem hat seine deutschen Laute und eine Öffnung (0 zu … 1 weit offen), die
 * für Kiefer und Atemhauch zählt.
 */
public enum Viseme {
    SIL("sil", "Stille", 0.0),
    PP("PP", "p, b, m", 0.0),
    FF("FF", "f, v, w", 0.12),
    TH("TH", "th", 0.18),
    DD("DD", "t, d", 0.22),
    KK("kk", "k, g, ch (ach)", 0.28),
    CH("CH", "sch, tsch, ch (ich)", 0.25),
    SS("SS", "s, z", 0.15),
    NN("nn", "n, l", 0.22),
    RR("RR", "r", 0.30),
    AA("aa", "a", 0.90),
    E("E", "e, ä", 0.55),
    I("I", "i", 0.40),
    O("O", "o, ö", 0.60),
    U("U", "u, ü", 0.35);

    /** Name im Satz (Teil des Formnamens). */
    public final String key;
    /** Deutsche Laute, die so aussehen. */
    public final String laute;
    /** Öffnung des Munds 0 … 1. */
    public final double open;

    Viseme(String key, String laute, double open) { this.key = key; this.laute = laute; this.open = open; }

    /** Name der Form im Grundkörper, z. B. {@code visem.viseme_PP}. */
    public String morph() { return "visem.viseme_" + key; }

    /** Visem nach seinem Namen im Satz ({@code "PP"}, {@code "aa"} …) oder null. */
    public static Viseme of(String key) {
        for (Viseme v : values()) if (v.key.equalsIgnoreCase(key)) return v;
        return null;
    }

    /** Gerundete Lippen (Vorausrundung der Konsonanten davor). */
    public boolean rounded() { return this == O || this == U; }
}
