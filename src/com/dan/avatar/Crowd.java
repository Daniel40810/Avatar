package com.dan.avatar;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * Eine Gruppe (Phase 12): viele Personen, die zusammen einen Weg gehen, locker in Reihen wie eine Wandergruppe
 * ({@code com.dan.figure.Hike}): je Reihe bis zu fünf nebeneinander, 1,3 m Abstand der Reihen, kleine
 * Versätze, gemeinsames Tempo (das der Langsamsten). Jede Person hat Person, Kleidung (nach Monat) und eine
 * {@link Motion}; gezeichnet wird je nach Größe im Bild in einer der Detailstufen (Studio).
 */
public final class Crowd {

    /** Eine Person der Gruppe. */
    public static final class Member {
        public final int index;
        public final Person person;
        public Outfit outfit;
        public final Motion motion;
        /** Ruhelage des Skeletts für diese Person (nach {@link #calibrate}). */
        public double[][] rest;
        /** Größe (m, aus der Ruhelage). */
        public double height = 1.7;
        Member(int index, Person person, Outfit outfit, Motion motion) { this.index = index; this.person = person; this.outfit = outfit; this.motion = motion; }
    }

    public final List<Member> members = new ArrayList<>();
    public final Motion.Path route;
    /** Tempo der Gruppe (m/s). */
    public double speed = 1.05;

    public Crowd(Motion.Path route) { this.route = route; }

    /**
     * Gruppe aus {@code n} gewürfelten Personen auf dem Weg {@code route} (Spitze bei Strecke {@code s0}),
     * Kleidung nach dem Monat, Strecke {@code distance} (m).
     */
    public static Crowd group(int n, Motion.Path route, double s0, double distance, int month, long seed) {
        Crowd c = new Crowd(route);
        SplittableRandom r = new SplittableRandom(seed);
        int perRow = 5;
        for (int i = 0; i < n; i++) {
            Person p = Person.wuerfeln(seed * 1000 + i * 7 + 1);
            int row = i / perRow, col = i % perRow;
            double side = (col - (perRow - 1) / 2.0) * 0.78 + (r.nextDouble() - 0.5) * 0.25;
            double lag = row * 1.3 + (r.nextDouble() - 0.5) * 0.35;
            Motion m = new Motion(Motion.offset(route, side), s0 - lag);
            m.then(Motion.walk(distance));
            Outfit o = Outfit.forMonth(month, r.nextDouble() < 0.7 ? Outfit.Role.DRAUSSEN : Outfit.Role.ALLTAG, Outfit.Epoch.HEUTE, r.nextLong());
            c.members.add(new Member(i, p, o, m));
        }
        return c;
    }

    /** Maße und Ruhelagen (einmal, auf dem Prozessor); Tempo der Gruppe = das der Langsamsten. */
    public void calibrate(Skeleton sk) {
        double slow = Double.MAX_VALUE;
        int hj = sk.joint("head");
        for (Member m : members) {
            m.rest = sk.restFor(m.person.weights());
            m.motion.calibrate(sk, m.rest, m.person);
            m.height = M4.pos(m.rest[hj])[1] + 0.12;
            slow = Math.min(slow, m.motion.vWalk);
        }
        speed = Math.min(speed, slow);
        for (Member m : members) m.motion.speedLimit = speed;
    }

    /** Zeit dt weiter (Wege und Füße aller). */
    public void step(double dt) { for (Member m : members) m.motion.step(dt); }

    /** Mitte der Gruppe (x, z). */
    public double[] centre() {
        double x = 0, z = 0;
        for (Member m : members) { x += m.motion.x; z += m.motion.z; }
        return new double[]{x / Math.max(1, members.size()), z / Math.max(1, members.size())};
    }
}
