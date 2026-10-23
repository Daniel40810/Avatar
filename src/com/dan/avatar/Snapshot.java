package com.dan.avatar;

/**
 * Unveränderlicher Zustand einer Person in einem Augenblick (Phase 13, Ausgang der Schnittstelle): wo sie
 * steht, wohin sie schaut, was sie tut, wie sie sich fühlt, wie nass sie ist und wie viele Lidschläge und
 * Schritte bisher waren. Die Szene liest ihn für Ton (Schritte, Lidschlag), Spuren, Kamera und Regie; er
 * kann ohne Sperre in einen anderen Faden gegeben werden.
 *
 * @param name      Name in der Regie (oder der Person)
 * @param time      Zeit der Person (s)
 * @param x         Ort der Füße (m)
 * @param y         Höhe des Bodens am Ort (m)
 * @param z         Ort der Füße (m)
 * @param yaw       Richtung (rad, 0 = Blick +z, links positiv)
 * @param head      Kopf (Gelenk „head“) in Weltkoordinaten oder {@code null}, solange nicht gerechnet
 * @param doing     was die Bewegung gerade tut: „steht“, „GEHEN“, „DREHEN“, „SITZEN“, „KAPUZE“ …
 * @param speed     Geschwindigkeit (m/s)
 * @param sitting   sitzt (mehr als halb)
 * @param feeling   nächstes benanntes Gefühl
 * @param valence   Valenz des Ziels (−1…1)
 * @param arousal   Erregung des Ziels (−1…1)
 * @param look      Blickart: CAMERA, POINT, FREE, HORIZON
 * @param lookAt    Blickziel in Weltkoordinaten (bei POINT) oder {@code null}
 * @param hoodUp    Kapuze auf
 * @param outfit    Name des Outfits
 * @param wet       Nässe je Zone 0…1 (Reihenfolge {@link Wetness#NAMES})
 * @param blinks    Lidschläge bisher
 * @param steps     Schritte (Aufsetzen) bisher
 */
public record Snapshot(String name, double time, double x, double y, double z, double yaw, double[] head,
                       String doing, double speed, boolean sitting, String feeling, double valence, double arousal,
                       String look, double[] lookAt, boolean hoodUp, String outfit, double[] wet, int blinks, int steps) {

    public Snapshot {
        head = head == null ? null : head.clone();
        lookAt = lookAt == null ? null : lookAt.clone();
        wet = wet == null ? new double[Wetness.N] : wet.clone();
    }

    /**
     * Aus den Teilen einer Person. {@code head} und {@code lookAt} in Weltkoordinaten; {@code motion},
     * {@code behavior}, {@code wetness} und {@code outfit} dürfen {@code null} sein.
     */
    public static Snapshot of(String name, double groundY, Motion motion, Behavior behavior, Wetness wetness,
                              Outfit outfit, double x, double z, double[] head, double[] lookAt) {
        double[] wet = new double[Wetness.N];
        if (wetness != null) for (int i = 0; i < wet.length; i++) wet[i] = wetness.wet(i);
        String doing = "steht";
        if (motion != null) {
            Motion.Cmd c = motion.current();
            doing = c == null ? (motion.sit > 0.5 ? "sitzt" : "steht") : c.kind().name();
        }
        return new Snapshot(name,
                behavior != null ? behavior.time() : (motion != null ? motion.time : 0),
                motion != null ? motion.x : x, groundY, motion != null ? motion.z : z, motion != null ? motion.yaw : 0,
                head, doing, motion != null ? motion.speed : 0, motion != null && motion.sit > 0.5,
                behavior != null ? behavior.feelingName() : "ruhig",
                behavior != null ? behavior.valence() : 0, behavior != null ? behavior.arousal() : 0,
                behavior != null ? behavior.look().name() : "CAMERA", lookAt,
                outfit != null && outfit.hoodUp(), outfit != null ? outfit.name : "ohne", wet,
                behavior != null ? behavior.blinks.size() : 0, motion != null ? motion.steps.size() : 0);
    }

    @Override public String toString() {
        return String.format(java.util.Locale.GERMANY, "%s: %s bei (%.2f, %.2f) %.0f° · %.2f m/s · %s · Blick %s%s",
                name, doing, x, z, Math.toDegrees(yaw), speed, feeling, look, hoodUp ? " · Kapuze auf" : "");
    }
}
