package com.dan.avatarstudio;

import com.dan.avatar.gl.AvatarGl;

/**
 * Lichtsetzungen des Studios (vorläufig, bis portrait in Phase 9 die Lichtführung übernimmt):
 * „Bühne“ (ganze Person, Spot von vorn links), „Porträt“ (Führungslicht 45° vorn, Kante von hinten)
 * und „Gegenlicht“ (Führungslicht hinter dem Kopf, für durchleuchtete Ohren; Abnahme Phase 4).
 */
public final class LightRig {

    private LightRig() {}

    public static final String[] NAMES = {"Bühne", "Porträt", "Gegenlicht"};

    /** Setzt das Licht {@code l} für eine Person, deren Kopf bei {@code head} (Welt) liegt. */
    public static void apply(AvatarGl.Light l, String name, double[] head) {
        AvatarGl.Light d = new AvatarGl.Light();
        double hx = head[0], hy = head[1], hz = head[2];
        switch (name) {
            case "Porträt" -> {
                set(l.keyPos, hx - 0.95, hy + 0.10, hz + 1.50);
                set(l.keyCol, 4.2, 3.0, 2.0);
                l.keyConeInner = 16; l.keyConeOuter = 32;
                set(l.rimPos, hx + 0.85, hy + 0.35, hz - 1.05);
                set(l.rimCol, 0.5, 1.6, 1.8);
                set(l.fillCol, 0.030, 0.038, 0.048);
            }
            case "Gegenlicht" -> {
                set(l.keyPos, hx + 0.22, hy + 0.14, hz - 1.30);
                set(l.keyCol, 4.4, 3.2, 2.1);
                l.keyConeInner = 16; l.keyConeOuter = 32;
                set(l.rimPos, hx - 1.1, hy + 0.15, hz + 1.5);
                set(l.rimCol, 0.45, 1.25, 1.45);
                set(l.fillCol, 0.022, 0.030, 0.040);
            }
            default -> {
                System.arraycopy(d.keyPos, 0, l.keyPos, 0, 3);
                System.arraycopy(d.keyCol, 0, l.keyCol, 0, 3);
                l.keyConeInner = d.keyConeInner; l.keyConeOuter = d.keyConeOuter;
                System.arraycopy(d.rimPos, 0, l.rimPos, 0, 3);
                System.arraycopy(d.rimCol, 0, l.rimCol, 0, 3);
                System.arraycopy(d.fillCol, 0, l.fillCol, 0, 3);
                System.arraycopy(d.keyDir, 0, l.keyDir, 0, 3);
                return;
            }
        }
        set(l.keyDir, hx - l.keyPos[0], hy - 0.1 - l.keyPos[1], hz - l.keyPos[2]);
    }

    /**
     * Lichtsetzung aus portrait (Phase 9) für einen Kopf bei {@code head} und die Kamera bei {@code eye}:
     * „Regennacht“, „Porträt“, „Rembrandt“, „Abendsonne“, „Nebel“, „Gegenlicht“.
     */
    public static void applySet(AvatarGl.Light l, String name, double[] head, double[] eye) {
        com.dan.portrait.LightSet s = com.dan.portrait.LightSet.of(name, head, eye);
        System.arraycopy(s.keyPos, 0, l.keyPos, 0, 3);
        System.arraycopy(s.keyDir, 0, l.keyDir, 0, 3);
        System.arraycopy(s.keyCol, 0, l.keyCol, 0, 3);
        l.keyConeInner = s.keyConeInner; l.keyConeOuter = s.keyConeOuter;
        System.arraycopy(s.rimPos, 0, l.rimPos, 0, 3);
        System.arraycopy(s.rimCol, 0, l.rimCol, 0, 3);
        System.arraycopy(s.fillCol, 0, l.fillCol, 0, 3);
    }

    private static void set(float[] v, double x, double y, double z) {
        v[0] = (float) x; v[1] = (float) y; v[2] = (float) z;
    }
}
