package com.dan.avatar;

import java.util.SplittableRandom;

/**
 * Adapter zu den Begriffen von {@code com.dan.figure} (F18 A): Szenen, die bisher Figuren führen, können den
 * Avatar mit denselben Größen bewegen und anziehen, ohne {@code figure} zu kennen (und umgekehrt).
 *
 * <ul>
 *   <li>Richtung: {@code figure} zählt den Azimut von Nord im Uhrzeigersinn (Nord = −z, Ost = +x), der Avatar
 *       den Winkel um die Hochachse ab +z: {@code yaw = π − heading}.</li>
 *   <li>Haltung: Ort und Richtung über {@link Motion#drive}, Gang folgt aus dem Weg; Bücken und Zeigen 0…1,
 *       Blick zur Seite (rad) als Blickziel.</li>
 *   <li>Kleidung: {@code forMonth}, {@code medieval}, {@code archaeologist} wie in {@code figure.Outfit}.</li>
 * </ul>
 */
public final class FigureTerms {

    private FigureTerms() {}

    /** Avatar-Richtung aus dem Azimut von {@code figure}. */
    public static double yaw(double heading) { return Math.PI - heading; }

    /** Azimut von {@code figure} aus der Avatar-Richtung. */
    public static double heading(double yaw) {
        double h = Math.PI - yaw;
        return h - 2 * Math.PI * Math.floor(h / (2 * Math.PI));
    }

    /**
     * Eine Figur nachführen: Ort (x, z), Azimut, Bücken, Zeigen wie in {@code figure.Person}; dt die Zeit seit dem
     * letzten Aufruf (s).
     */
    public static void follow(Motion m, double x, double z, double heading, double bend, double point, double dt) {
        m.drive(x, z, yaw(heading), dt);
        m.bend = clamp(bend);
        m.point = clamp(point);
    }

    /** Wie {@code Outfit.forMonth(month, r, guide, child)}: Führer draußen, Wanderer draußen, Kinder im Alltag. */
    public static Outfit forMonth(int month, SplittableRandom r, boolean guide, boolean child) {
        Outfit.Role role = child ? Outfit.Role.ALLTAG : Outfit.Role.DRAUSSEN;
        return Outfit.forMonth(month, role, Outfit.Epoch.HEUTE, r.nextLong());
    }

    /** Wie {@code Outfit.medieval(month, r, child)}: Kutte aus Wolle, im Winter mit Kapuze. */
    public static Outfit medieval(int month, SplittableRandom r, boolean child) {
        return Outfit.forMonth(month, Outfit.Role.DRAUSSEN, Outfit.Epoch.MITTELALTER_1362, r.nextLong());
    }

    /** Wie {@code Outfit.archaeologist(month, r)}: Ölzeug und Gummistiefel, im Winter Strick. */
    public static Outfit archaeologist(int month, SplittableRandom r) {
        boolean winter = month == 12 || month <= 2;
        return Outfit.preset(winter ? "Winter" : "Regenjacke");
    }

    private static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
}
