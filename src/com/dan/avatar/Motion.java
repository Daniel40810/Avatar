package com.dan.avatar;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Bewegung (Phase 11, F15 A): Gehen aus Formeln, Stehen, Drehen auf der Stelle, Sitzen und Aufstehen,
 * Gesten (Winken, Zeigen, Bücken) und Hände mit Griffen, als Folge von Befehlen.
 *
 * <p>Der Gang ist je Person verschieden: Schrittlänge aus der Beinlänge (Doppelschritt ≈ 1,6 × Bein),
 * Tempo nach Alter und Stimmung (Erregung schneller, traurig langsamer und kleiner), Armschwung nach Stimmung.
 * Die Füße stehen in Weltkoordinaten: beim Aufsetzen wird der Ort festgehalten, solange der Fuß trägt; im
 * Schwung fliegt er vom Abheben zum vorhergesagten Aufsetzpunkt (dort, wo das Becken dann sein wird, mit der
 * Richtung von dann). Darum gleitet kein Fuß, auch nicht auf dem Kreis, beim Anhalten oder beim Drehen. Die
 * Phase läuft mit dem Weg (Doppelschritt je Schrittlänge), beim Drehen und zum Nachsetzen im Takt.
 *
 * <p>Becken: Senkung im Doppelstand (bis 2,5 cm), Seitenschwung zum Standbein, Drehung ±4°, Rumpf gegen;
 * Arme gegen die Beine. Werte mit [G] sind gesetzt, angelehnt an Messungen zum Gang (Perry, Winter).
 *
 * <pre>
 *   Motion m = new Motion(Motion.circle(0, 0, 1.6, true), 0);
 *   m.calibrate(skeleton, rest, person);
 *   m.then(Motion.walk(8)).then(Motion.stand(2)).then(Motion.turn(Math.PI / 2)).then(Motion.sit(4)).then(Motion.wave(3));
 *   m.step(dt);  ...  m.apply(skeleton, world, rest, pose);   // Weltmatrizen der Person, danach Ort x, z
 * </pre>
 */
public final class Motion {

    // ------------------------------------------------------------ Weg
    /** Ein Weg: Ort und Richtung nach der Strecke s (m): {x, z, dx, dz}. */
    public interface Path { double[] at(double s); }

    /** Kreis um (cx, cz) mit Radius r, gegen den Uhrzeigersinn von oben gesehen (oder mit). */
    public static Path circle(double cx, double cz, double r, boolean ccw) {
        return s -> {
            double a = s / r * (ccw ? 1 : -1);
            double x = cx + r * Math.cos(a), z = cz + r * Math.sin(a);
            double dx = -Math.sin(a) * (ccw ? 1 : -1), dz = Math.cos(a) * (ccw ? 1 : -1);
            return new double[]{x, z, dx, dz};
        };
    }

    /** Weg {@code p}, um {@code side} m zur linken Seite versetzt (Gruppe nebeneinander). */
    public static Path offset(Path p, double side) {
        return s -> {
            double[] a = p.at(s);
            return new double[]{a[0] + a[3] * side, a[1] - a[2] * side, a[2], a[3]};
        };
    }

    /** Gerade von (x, z) in Richtung yaw. */
    public static Path line(double x, double z, double yaw) {
        double dx = Math.sin(yaw), dz = Math.cos(yaw);
        return s -> new double[]{x + dx * s, z + dz * s, dx, dz};
    }

    // ------------------------------------------------------------ Befehle
    public enum Kind { GEHEN, STEHEN, DREHEN, SITZEN, AUFSTEHEN, WINKEN, ZEIGEN, BUECKEN, ZIEL, ZUWENDEN, KAPUZE }

    /**
     * Ein Befehl: GEHEN Strecke (m), STEHEN Dauer (s), DREHEN Winkel (rad, links positiv), SITZEN Dauer, Gesten Dauer.
     * Seit Phase 13 mit Ort: ZIEL geht zu {@code at = {x, z, tempo}} (erst drehen, dann gerade), ZUWENDEN dreht
     * sich zu {@code at = {x, z}}, KAPUZE (Wert +1 auf, −1 ab) greift mit beiden Händen an die Kapuze und meldet
     * in der Mitte {@link #hoodEvent}.
     */
    public record Cmd(Kind kind, double value, double[] at) {
        public Cmd(Kind kind, double value) { this(kind, value, null); }
    }

    public static Cmd walk(double meters) { return new Cmd(Kind.GEHEN, meters); }
    public static Cmd stand(double s) { return new Cmd(Kind.STEHEN, s); }
    public static Cmd turn(double rad) { return new Cmd(Kind.DREHEN, rad); }
    public static Cmd sit(double s) { return new Cmd(Kind.SITZEN, s); }
    public static Cmd standUp() { return new Cmd(Kind.AUFSTEHEN, 0); }
    public static Cmd wave(double s) { return new Cmd(Kind.WINKEN, s); }
    public static Cmd point(double s) { return new Cmd(Kind.ZEIGEN, s); }
    public static Cmd bend(double s) { return new Cmd(Kind.BUECKEN, s); }
    /** Zum Ort (x, z) gehen, mit Tempo als Anteil des eigenen (1 = gewohnt). Weicht die Richtung ab, erst drehen. */
    public static Cmd goTo(double x, double z, double tempo) { return new Cmd(Kind.ZIEL, 0, new double[]{x, z, tempo}); }
    /** Zum Ort (x, z) drehen. */
    public static Cmd turnTo(double x, double z) { return new Cmd(Kind.ZUWENDEN, 0, new double[]{x, z}); }
    /** Kapuze mit beiden Händen aufsetzen ({@code up}) oder abnehmen; 1,8 s. */
    public static Cmd hood(boolean up) { return new Cmd(Kind.KAPUZE, up ? 1 : -1); }

    /** Läuft der Befehl {@code c} (genau dieses Objekt) noch oder wartet er? */
    public synchronized boolean pending(Cmd c) {
        if (c == null) return false;
        if (cur == c || (cur != null && origin == c)) return true;
        for (Cmd q : queue) if (q == c) return true;
        return false;
    }

    private final Deque<Cmd> queue = new ArrayDeque<>();
    private Cmd cur;
    private double curT, curS0, curYaw0;

    public synchronized Motion then(Cmd c) { queue.add(c); return this; }

    /** Alles Geplante verwerfen: beim Gehen bremsen und stehen bleiben, sonst den laufenden Befehl beenden. */
    public synchronized void interrupt() {
        queue.clear();
        if (cur != null && cur.kind() == Kind.GEHEN) cur = new Cmd(Kind.GEHEN, (s - curS0) + speed * speed / 1.8 + 0.06);
        else cur = null;
    }

    /** Befehl, der gerade läuft (oder null). */
    public Cmd current() { return cur; }

    /** Sind alle Befehle erledigt und steht die Person still? */
    public synchronized boolean idle() { return cur == null && queue.isEmpty() && speed < 0.01 && !swing[0] && !swing[1]; }

    // ------------------------------------------------------------ Zustand
    /** Der Weg (beim Führen von außen eine Gerade ab dem Ort, siehe {@link #drive}). */
    public Path path;
    /** Ort der Füße (m, Figur-Koordinaten der Szene) und Richtung (rad, 0 = Blick +z, links positiv). */
    public double x, z, yaw;
    /** Strecke auf dem Weg (m), Geschwindigkeit (m/s), Schrittphase 0…1 je Doppelschritt, Gang 0…1. */
    public double s, speed, phase, gait;
    /** Sitzen, Winken, Zeigen, Bücken 0…1 (weich nachgeführt). */
    public double sit, wave, point, bend;
    /** Griff der Hände 0 offen … 1 Faust (locker hängend 0,35). */
    public double gripL = 0.35, gripR = 0.35;
    /** Zeit (s). */
    public double time;
    /** Tempo als Anteil des eigenen (aus {@link #goTo}); 1 = gewohnt. */
    public double pace = 1;
    /** Hände an der Kapuze 0…1 (weich nachgeführt). */
    public double hoodHands;
    /**
     * Ereignis der Kapuzen-Geste: +1 aufsetzen, −1 abnehmen, 0 keins. Die Szene tauscht das Netz und setzt es
     * wieder auf 0 (oder liest es mit {@link #takeHoodEvent}).
     */
    public volatile int hoodEvent;
    /** Ereignis abholen (und löschen). */
    public synchronized int takeHoodEvent() { int e = hoodEvent; hoodEvent = 0; return e; }
    private double turnRate;

    // Füße (Weltkoordinaten der Szene): Standort, Schwung von → nach
    final double[][] planted = new double[2][3], from = new double[2][3], to = new double[2][3];
    final boolean[] swing = new boolean[2];
    final double[] lastU = new double[2];
    private static final double[] OFFSET = {0, 0.5};
    private static final String[] SIDE = {"l", "r"};

    // Person
    /** Beinlänge (m), halbe Spur (m), Doppelschritt (m), Tempo (m/s), Takt (Doppelschritte/s), Armschwung (rad). */
    public double legLen = 0.9, halfTrack = 0.1, stride = 1.45, vWalk = 1.3, cadence = 0.9, armSwing = 0.3;
    private double ankleY = 0.08, footLen = 0.2;
    /** Höchstes Tempo (m/s), z. B. das einer Gruppe; sonst das eigene. */
    public double speedLimit = Double.MAX_VALUE;
    /** Anteil der Stütze am Doppelschritt [G]. */
    public double beta = 0.6;
    /** Stimmung: Valenz und Erregung (−1…1), ändern Tempo, Schritt, Armschwung und Haltung. */
    public double valence, arousal;

    // Bank zum Sitzen (Weltkoordinaten der Szene): Mitte, Richtung, Höhe; null = keine
    public double[] bench;
    /** Wunsch für die Bank: an der Strecke s, Blick um dyaw gegen den Weg gedreht {s, dyaw}; die Szene stellt sie auf. */
    public double[] benchAt;

    /** Fußspuren: je Aufsetzen {x, z, Richtung, Seite 0 links / 1 rechts}. */
    public final java.util.List<double[]> steps = new java.util.ArrayList<>();

    // Prüfung: Gleiten der tragenden Füße
    public double slideMax, slideSum;
    /** Wie weit ein tragender Fuß höchstens außer Reichweite lag (m); 0 = jedes Ziel erreicht. */
    public double missMax;
    private final double[][] lastAnkle = new double[2][];
    private final boolean[] wasPlanted = new boolean[2];

    public Motion(Path path, double s0) {
        this.path = path;
        this.s = s0;
        double[] p = path.at(s0);
        x = p[0]; z = p[1]; yaw = Math.atan2(p[2], p[3]);
    }

    /** Maße der Person aus der Ruhelage ihres Skeletts; Tempo nach Alter. Danach stehen beide Füße. */
    public void calibrate(Skeleton sk, double[][] rest, Person person) {
        int th = sk.joint("thigh_l"), ft = sk.joint("foot_l"), ba = sk.joint("ball_l"), fr = sk.joint("foot_r");
        double[] h = M4.pos(rest[th]), a = M4.pos(rest[ft]);
        legLen = h[1] - a[1];
        ankleY = a[1];
        halfTrack = Math.abs(a[0] - M4.pos(rest[fr])[0]) / 2 * 0.9;
        if (ba >= 0) footLen = Skeleton.dist(a, M4.pos(rest[ba])) * 1.4;
        double age = person == null ? 35 : person.ageYears();
        double ageF = age < 60 ? 1 : Math.max(0.7, 1 - (age - 60) * 0.008);
        double moodV = 1 + 0.12 * arousal + 0.06 * valence;
        stride = 1.6 * legLen * (0.95 + 0.05 * moodV) * (age > 65 ? 0.92 : 1);
        vWalk = 1.33 * Math.sqrt(legLen / 0.88) * ageF * moodV;
        cadence = vWalk / stride;
        armSwing = 0.30 * (1 + 0.5 * arousal + 0.3 * valence) * (age > 70 ? 0.7 : 1);
        placeFeet();
    }

    /** Beide Füße unter das Becken stellen (Start, Sprung). */
    public void placeFeet() {
        for (int i = 0; i < 2; i++) {
            double[] w = toWorld(i == 0 ? halfTrack : -halfTrack, 0, x, z, yaw);
            planted[i][0] = w[0]; planted[i][1] = ankleY; planted[i][2] = w[1];
            swing[i] = false;
            lastU[i] = frac(phase + OFFSET[i]);
        }
    }

    // ------------------------------------------------------------ Schritt
    /** Zeit dt (s) weiter: Befehle, Tempo, Weg, Füße, Gesten. */
    public void step(double dt) {
        if (dt <= 0) return;
        dt = Math.min(dt, 0.05);
        time += dt;
        double targetV = 0, sitT = sit > 0.5 ? 1 : 0, waveT = 0, pointT = 0, bendT = 0, hoodT = 0;
        boolean turning = false;
        synchronized (this) {
            if (cur == null && !queue.isEmpty()) { cur = queue.poll(); origin = cur; curT = 0; curS0 = s; curYaw0 = yaw; begin(); }
        }
        if (cur != null) {
            curT += dt;
            boolean done = false;
            switch (cur.kind()) {
                case GEHEN -> {
                    double rest = cur.value() - (s - curS0);
                    double brake = speed * speed / (2 * 0.9) + 0.05;
                    targetV = rest > brake ? Math.min(vWalk * pace, speedLimit) : 0;
                    if (rest <= brake) sitT = 0;
                    done = targetV == 0 && speed < 0.02 && !swing[0] && !swing[1];
                }
                case STEHEN -> done = curT >= cur.value() && !swing[0] && !swing[1];
                case DREHEN -> {
                    double left = cur.value() - (yaw - curYaw0);
                    turnRate = Math.signum(left) * Math.min(1.4, Math.abs(left) * 6 + 0.2);
                    turning = Math.abs(left) > 0.004;
                    if (!turning) { yaw = curYaw0 + cur.value(); turnRate = 0; }
                    done = !turning && !swing[0] && !swing[1] && settled();
                }
                case SITZEN -> { sitT = 1; done = sit > 0.98 && curT >= cur.value(); }
                case AUFSTEHEN -> { sitT = 0; done = sit < 0.02; }
                case WINKEN -> { waveT = curT < cur.value() ? 1 : 0; done = curT >= cur.value() && wave < 0.02; }
                case ZEIGEN -> { pointT = curT < cur.value() ? 1 : 0; done = curT >= cur.value() && point < 0.02; }
                case BUECKEN -> { bendT = curT < cur.value() ? 1 : 0; done = curT >= cur.value() && bend < 0.02; }
                case KAPUZE -> {
                    hoodT = curT < HOOD_S ? 1 : 0;
                    if (!hoodFired && curT >= HOOD_S * 0.55) { hoodEvent = cur.value() > 0 ? 1 : -1; hoodFired = true; }
                    done = curT >= HOOD_S && hoodHands < 0.02;
                }
                default -> done = true;
            }
            if (cur.kind() != Kind.SITZEN && cur.kind() != Kind.AUFSTEHEN && sit > 0.5) sitT = 1;
            if (done) cur = null;
        }
        if (!turning) turnRate = 0;
        // Tempo weich (Anfahren 1,2 m/s², Bremsen 0,9 m/s²) [G]
        double acc = targetV > speed ? 1.2 : 0.9;
        speed += Math.max(-acc * dt, Math.min(acc * dt, targetV - speed));
        if (speed < 1e-4 && targetV == 0) speed = 0;
        // Weg und Richtung
        double ds = speed * dt;
        if (ds > 0) {
            s += ds;
            double[] p = path.at(s);
            x = p[0]; z = p[1]; yaw = Math.atan2(p[2], p[3]);
        }
        yaw += turnRate * dt;
        stepFeet(dt, ds, turning);
        // Sitzen und Gesten weich
        sit += (sitT - sit) * Math.min(1, dt / 0.45);
        wave += (waveT - wave) * Math.min(1, dt / 0.25);
        point += (pointT - point) * Math.min(1, dt / 0.3);
        bend += (bendT - bend) * Math.min(1, dt / 0.5);
        hoodHands += (hoodT - hoodHands) * Math.min(1, dt / 0.32);
        gripR = 0.35 + (0.0 - 0.35) * wave;
        gripL = 0.35;
        if (hoodHands > 0) { gripR += (0.75 - gripR) * hoodHands; gripL += (0.75 - gripL) * hoodHands; }
    }

    /** Dauer der Kapuzen-Geste (s) [G]. */
    public static final double HOOD_S = 1.8;
    private boolean hoodFired;
    /** Befehl, wie er gegeben wurde (ZIEL und ZUWENDEN laufen als DREHEN und GEHEN, gelten aber als dieser). */
    private Cmd origin;

    /**
     * Neuer Befehl beginnt (unter der Sperre): ZIEL und ZUWENDEN werden hier aus dem jetzigen Ort zu DREHEN
     * und GEHEN auf einer Geraden; was danach kommt, bleibt in der Schlange.
     */
    private void begin() {
        hoodFired = false;
        Kind k = cur.kind();
        if (k != Kind.ZIEL && k != Kind.ZUWENDEN) { if (k == Kind.GEHEN) pace = 1; return; }
        double[] a = cur.at();
        double dx = a[0] - x, dz = a[1] - z, d = Math.hypot(dx, dz);
        if (d < 0.12) { cur = null; return; }
        double head = Math.atan2(dx, dz);
        double dyaw = Math.atan2(Math.sin(head - yaw), Math.cos(head - yaw));
        if (k == Kind.ZUWENDEN) { cur = new Cmd(Kind.DREHEN, dyaw); curYaw0 = yaw; return; }
        if (Math.abs(dyaw) > 0.10) {                       // erst drehen, dann denselben Befehl noch einmal
            queue.addFirst(cur);
            cur = new Cmd(Kind.DREHEN, dyaw);
            curYaw0 = yaw;
            return;
        }
        pace = a.length > 2 && a[2] > 0 ? a[2] : 1;
        path = line(x, z, head);
        s = 0; curS0 = 0;
        yaw = head;
        // Länge so, dass die Füße am Ziel zum Stehen kommen (Bremsweg wird im GEHEN abgezogen)
        cur = new Cmd(Kind.GEHEN, d);
    }

    /**
     * Von außen führen (Adapter zu den Begriffen von {@code com.dan.figure}, F18): eine Szene bewegt die Person
     * selbst (Ort, Richtung) und gibt Bücken und Zeigen vor; die Füße setzen auf, wo das Becken mit der
     * jetzigen Geschwindigkeit hinkommt, und gleiten nicht.
     */
    public void drive(double nx, double nz, double nyaw, double dt) {
        if (dt <= 0) return;
        dt = Math.min(dt, 0.1);
        time += dt;
        double ds = Math.hypot(nx - x, nz - z);
        double dyaw = Math.atan2(Math.sin(nyaw - yaw), Math.cos(nyaw - yaw));
        speed += (ds / dt - speed) * Math.min(1, dt / 0.15);
        turnRate = dyaw / dt;
        x = nx; z = nz; yaw = nyaw;
        path = line(x, z, yaw);
        s = 0;
        stepFeet(dt, ds, Math.abs(turnRate) > 0.2 && speed < 0.2);
        turnRate = 0;
    }

    private void stepFeet(double dt, double ds, boolean turning) {
        // Gang
        double gaitT = Math.max(speed / vWalk, turning ? 0.35 : 0);
        gait += (gaitT - gait) * Math.min(1, dt / 0.25);
        // Phase: mit dem Weg, beim Drehen und Nachsetzen im Takt
        boolean anySwing = swing[0] || swing[1];
        double dphi = ds / stride;
        boolean settle = !settled();
        if (turning || anySwing || settle) dphi = Math.max(dphi, dt * cadence * 0.9);
        if (speed < 0.01 && !turning && !anySwing && !settle) dphi = 0;
        phase = frac(phase + dphi);
        for (int i = 0; i < 2; i++) {
            double u = frac(phase + OFFSET[i]);
            double lu = lastU[i];
            boolean lift = lu < beta && u >= beta && dphi > 0;
            boolean land = dphi > 0 && u < lu;                     // über 1 → 0
            if (land && swing[i]) {
                System.arraycopy(to[i], 0, planted[i], 0, 3);
                swing[i] = false;
                if (steps.size() < 4000) steps.add(new double[]{planted[i][0], planted[i][2], yaw, i});
            }
            if (lift && (speed > 0.02 || turning || needsStep(i))) {
                swing[i] = true;
                System.arraycopy(planted[i], 0, from[i], 0, 3);
                landing(i, 1 - beta, turning);
            }
            lastU[i] = u;
        }
    }

    /** Stehen beide Füße an ihrem Platz neben dem Becken? */
    private boolean settled() { return !needsStep(0) && !needsStep(1); }

    private boolean needsStep(int i) {
        double[] want = toWorld(i == 0 ? halfTrack : -halfTrack, 0, x, z, yaw);
        double dx = planted[i][0] - want[0], dz = planted[i][2] - want[1];
        return dx * dx + dz * dz > 0.04 * 0.04;
    }

    /** Aufsetzpunkt des Fußes i: Becken in dphi Doppelschritten, seitlich die halbe Spur, vorn die halbe Stütze. */
    private void landing(int i, double dphi, boolean turning) {
        double k = Math.min(1, speed / Math.max(0.1, vWalk));
        double fs = speed > 0.02 ? dphi * stride : 0;
        double rate = cadence * 0.9;
        double fx, fz, fyaw;
        if (fs > 0) { double[] p = path.at(s + fs); fx = p[0]; fz = p[1]; fyaw = Math.atan2(p[2], p[3]); }
        else { fx = x; fz = z; fyaw = yaw + turnRate * dphi / rate; }
        double fwd = beta * stride / 2 * k;
        double[] w = toWorld(i == 0 ? halfTrack : -halfTrack, fwd, fx, fz, fyaw);
        to[i][0] = w[0]; to[i][1] = ankleY; to[i][2] = w[1];
    }

    // ------------------------------------------------------------ Pose
    /**
     * Die Weltmatrizen {@code w} der Person (aus Pose und Zugabe, ohne Fuß-IK, Blick +z, Füße am Ursprung)
     * nach dem Gang verändern: Becken, Beine an die Füße, Arme, Gesten, Finger, zuletzt die Richtung.
     * {@code add} wird vorher mit {@link #additive} gefüllt und an die Pose gegeben.
     */
    public void apply(Skeleton sk, double[][] w, double[][] rest) {
        int pv = sk.joint("pelvis");
        // Sitzen: der Körper rückt so, dass die Füße stehen bleiben (die Pose bringt sie nach vorn)
        double[][] loc = new double[2][];
        for (int i = 0; i < 2; i++) loc[i] = toLocal(planted[i][0], planted[i][2]);
        if (sit > 0.001) {
            double[] fl = M4.pos(w[sk.joint("foot_l")]), fr = M4.pos(w[sk.joint("foot_r")]);
            double mx = (fl[0] + fr[0]) / 2, mz = (fl[2] + fr[2]) / 2;
            double tx = (loc[0][0] + loc[1][0]) / 2, tz = (loc[0][1] + loc[1][1]) / 2;
            double[] m = M4.translation(tx - mx, 0, tz - mz);
            for (int j = 0; j < w.length; j++) w[j] = M4.mul(m, w[j]);
        }
        // Ziele der Füße (örtlich), im Schwung mit Hub und Kippen
        double[][] tgt = new double[2][];
        double[] pitch = new double[2];
        for (int i = 0; i < 2; i++) {
            double u = frac(phase + OFFSET[i]);
            if (swing[i]) {
                double v = Math.max(0, Math.min(1, (u - beta) / (1 - beta)));
                double e = v * v * (3 - 2 * v);
                double wx = from[i][0] + (to[i][0] - from[i][0]) * e, wz = from[i][2] + (to[i][2] - from[i][2]) * e;
                double[] l = toLocal(wx, wz);
                double lift = (0.035 + 0.05 * gait) * Math.sin(Math.PI * v) + 0.03 * gait * Math.sin(Math.PI * v) * Math.sin(Math.PI * v);
                tgt[i] = new double[]{l[0], ankleY + lift, l[1]};
                pitch[i] = gait * (-0.45 * (1 - smooth(0, 0.35, v)) + 0.22 * smooth(0.55, 0.9, v) * (1 - smooth(0.92, 1, v)));
            } else {
                tgt[i] = new double[]{loc[i][0], ankleY, loc[i][1]};
                // Ferse hebt sich am Ende der Stütze (Abrollen über den Ballen)
                double r = speed > 0.05 ? smooth(beta * 0.6, beta, u) : 0;
                pitch[i] = -0.5 * r * gait;
                tgt[i][1] += footLen * 0.6 * Math.sin(0.5 * r * gait);
            }
        }
        // Becken tiefer, wenn ein Fuß sonst nicht erreichbar wäre (Knie nie ganz durchgedrückt)
        int thl = sk.joint("thigh_l"), thr = sk.joint("thigh_r"), cal = sk.joint("calf_l"), fol = sk.joint("foot_l");
        double leg = Skeleton.dist(M4.pos(w[thl]), M4.pos(w[cal])) + Skeleton.dist(M4.pos(w[cal]), M4.pos(w[fol]));
        double drop = 0;
        for (int i = 0; i < 2; i++) {
            double[] hp = M4.pos(w[i == 0 ? thl : thr]);
            double d = Skeleton.dist(hp, tgt[i]), lim = leg * 0.993;
            if (d > lim) {
                double dx = tgt[i][0] - hp[0], dz = tgt[i][2] - hp[2];
                double need = hp[1] - tgt[i][1] - Math.sqrt(Math.max(0, lim * lim - dx * dx - dz * dz));
                drop = Math.max(drop, need);
            }
        }
        if (drop > 0 && pv >= 0) sk.moveSubtree(w, pv, M4.translation(0, -drop, 0));
        for (int i = 0; i < 2; i++) {
            double fy = 0;
            if (!swing[i]) {
                // Fuß in die Richtung, in der er aufgesetzt wurde (beim Drehen), relativ zum Körper
                fy = footYaw[i] - yaw;
            } else {
                double v = Math.max(0, Math.min(1, (frac(phase + OFFSET[i]) - beta) / (1 - beta)));
                fy = (footYaw[i] + (yaw - footYaw[i]) * v) - yaw;
            }
            double miss = sk.footTo(w, rest, SIDE[i], tgt[i], pitch[i], fy);
            if (!swing[i]) missMax = Math.max(missMax, miss);
        }
        // Gesten
        gestures(sk, w);
        hands(sk, w);
        Skeleton.turn(w, yaw);
    }

    /** Richtung jedes Fußes beim Aufsetzen (für das Drehen auf der Stelle). */
    private final double[] footYaw = new double[2];

    /** Nach jedem {@link #step}: Richtung der tragenden Füße festhalten (sie drehen sich nicht mit dem Körper). */
    public void trackFeet() {
        for (int i = 0; i < 2; i++) if (swing[i]) footYawSwing[i] = true; else if (footYawSwing[i]) { footYaw[i] = yaw; footYawSwing[i] = false; }
    }
    private final boolean[] footYawSwing = new boolean[2];
    { footYaw[0] = footYaw[1] = Double.NaN; }

    /**
     * Zugabe für den Gang vor der Pose (Becken, Rumpf, Arme), zusätzlich zu der des Verhaltens. Figur-Koordinaten
     * ohne Richtung (Blick +z).
     */
    public void additive(Skeleton.Additive add) {
        if (Double.isNaN(footYaw[0])) { footYaw[0] = footYaw[1] = yaw; }
        double g = gait * (1 - sit);
        double ph = 2 * Math.PI * phase;
        if (g > 1e-3) {
            // Senkung im Doppelstand: ein wenig, die Knie federn kaum (Daniel, Phase 11: weniger federn) [G]
            double bob = (0.006 + 0.007 * Math.min(1, speed / vWalk)) * g;
            add.pelvis[1] += -0.006 * g - bob * 0.5 * (1 + Math.cos(2 * ph));
            add.pelvis[0] += 0.022 * g * Math.sin(ph);
            add.rotate("pelvis", 0, 1, 0, -0.07 * g * Math.cos(ph));
            add.rotate("pelvis", 0, 0, 1, 0.04 * g * Math.sin(ph));
            add.rotate("spine_03", 0, 1, 0, 0.10 * g * Math.cos(ph));
            add.rotate("spine_01", 1, 0, 0, (0.05 - 0.06 * valence) * g);     // traurig gebeugter
            double a = armSwing * g;
            add.rotate("upperarm_l", 1, 0, 0, a * Math.cos(ph));
            add.rotate("upperarm_r", 1, 0, 0, -a * Math.cos(ph));
            add.rotate("lowerarm_l", 1, 0, 0, -(0.18 + 0.12 * Math.max(0, -Math.cos(ph))) * g);
            add.rotate("lowerarm_r", 1, 0, 0, -(0.18 + 0.12 * Math.max(0, Math.cos(ph))) * g);
        }
        if (bend > 1e-3) {
            add.rotate("spine_01", 1, 0, 0, 0.45 * bend);
            add.rotate("spine_02", 1, 0, 0, 0.35 * bend);
            add.rotate("spine_03", 1, 0, 0, 0.25 * bend);
            add.pelvis[2] -= 0.08 * bend;
        }
    }

    private void gestures(Skeleton sk, double[][] w) {
        int ua = sk.joint("upperarm_r"), la = sk.joint("lowerarm_r"), ha = sk.joint("hand_r"), cl = sk.joint("clavicle_r");
        if (wave > 1e-3) {
            sk.rotateAt(w, cl, 0, 0, 1, -0.25 * wave);
            sk.aim(w, ua, la, new double[]{-0.55, 0.62, 0.25}, wave);
            double osc = Math.sin(2 * Math.PI * 1.9 * time);
            sk.aim(w, la, ha, new double[]{-0.15 - 0.35 * osc, 0.95, 0.12}, wave);
        }
        if (point > 1e-3) {
            sk.aim(w, ua, la, new double[]{-0.25, 0.08, 0.96}, point);
            sk.aim(w, la, ha, new double[]{-0.2, 0.1, 0.97}, point);
        }
        if (hoodHands > 1e-3) {
            // beide Hände an den Rand der Kapuze über der Stirn: Oberarm nach vorn oben, Unterarm zum Kopf;
            // nach dem Wechsel ziehen sie nach hinten [G]
            double back = hoodFired ? 1 : 0;
            for (int sd = 0; sd < 2; sd++) {
                double m = sd == 0 ? 1 : -1;                     // links +x, rechts −x
                String n = SIDE[sd];
                int u = sk.joint("upperarm_" + n), l = sk.joint("lowerarm_" + n), hj = sk.joint("hand_" + n), c = sk.joint("clavicle_" + n);
                sk.rotateAt(w, c, 0, 0, 1, m * 0.18 * hoodHands);
                sk.aim(w, u, l, new double[]{m * 0.40, 0.72, 0.42 - 0.30 * back}, hoodHands);
                sk.aim(w, l, hj, new double[]{-m * 0.52, 0.80, -0.08 - 0.45 * back}, hoodHands);
            }
        }
    }

    private static final String[] FINGERS = {"index", "middle", "ring", "pinky"};
    private static final double[] CURL = {0.55, 0.85, 0.65};

    /** Finger krümmen: 0 offen … 1 Faust; beim Zeigen bleibt der Zeigefinger gestreckt. */
    private void hands(Skeleton sk, double[][] w) {
        for (int h = 0; h < 2; h++) {
            String sd = SIDE[h];
            double c = h == 0 ? gripL : Math.max(gripR, 0.9 * point);
            int i1 = sk.joint("index_01_" + sd), p1 = sk.joint("pinky_01_" + sd), m1 = sk.joint("middle_01_" + sd), m2 = sk.joint("middle_02_" + sd), t2 = sk.joint("thumb_02_" + sd);
            if (i1 < 0 || p1 < 0 || m1 < 0 || m2 < 0 || t2 < 0) continue;
            double[] across = Skeleton.norm(Skeleton.sub(M4.pos(w[i1]), M4.pos(w[p1])));
            double[] dir = Skeleton.norm(Skeleton.sub(M4.pos(w[m2]), M4.pos(w[m1])));
            double[] ax = Skeleton.norm(cross(dir, across));
            // Richtung so wählen, dass sich die Fingerspitze zum Daumen (zur Handfläche) bewegt
            double[] tip = M4.pos(w[m2]), th = M4.pos(w[t2]), base = M4.pos(w[m1]);
            double[] moved = rot(ax, 0.3, Skeleton.sub(tip, base));
            double before = Skeleton.dist(tip, th), after = Skeleton.dist(Skeleton.add(base, moved), th);
            if (after > before) ax = Skeleton.scale(ax, -1);
            for (String f : FINGERS) {
                double cf = (h == 1 && point > 0.5 && f.equals("index")) ? 0 : c;
                for (int k = 0; k < 3; k++) {
                    int j = sk.joint(f + "_0" + (k + 1) + "_" + sd);
                    sk.rotateAt(w, j, ax[0], ax[1], ax[2], CURL[k] * cf * 1.6);
                }
            }
            sk.rotateAt(w, t2, ax[0], ax[1], ax[2], 0.4 * c);
        }
    }

    // ------------------------------------------------------------ Prüfung
    /**
     * Gleiten messen: Lage der Fußgelenke (Weltmatrizen nach {@link #apply}, plus Ort) mit dem letzten Bild
     * vergleichen, solange der Fuß trägt.
     */
    public void measure(Skeleton sk, double[][] w, double dt) {
        for (int i = 0; i < 2; i++) {
            double[] a = M4.pos(w[sk.joint("foot_" + SIDE[i])]);
            double[] p = {a[0] + x, a[1], a[2] + z};
            boolean pl = !swing[i] && (speed > 0.02 || gait > 0.05) && frac(phase + OFFSET[i]) < beta * 0.6;
            if (pl && wasPlanted[i] && lastAnkle[i] != null && dt > 0) {
                double d = Math.hypot(p[0] - lastAnkle[i][0], p[2] - lastAnkle[i][2]);
                slideSum += d;
                slideMax = Math.max(slideMax, d / dt);
            }
            wasPlanted[i] = pl;
            lastAnkle[i] = p;
        }
    }

    // ------------------------------------------------------------ Bank
    /**
     * Bank für das Sitzen: hinter der Person in der Richtung {@code yawSit}, so dass die Pose „sitzen“ auf ihr
     * sitzt (Becken über der Sitzfläche). {@code seatBack} und {@code seatH} aus der Pose (örtlich).
     */
    public void placeBench(double atX, double atZ, double yawSit, double seatBack, double seatH) {
        double[] c = toWorld(0, -seatBack, atX, atZ, yawSit);
        bench = new double[]{c[0], c[1], yawSit, seatH};
    }

    // ------------------------------------------------------------ Hilfen
    /** Örtlich (Blick +z) → Welt: Versatz (lx, lz) um den Ort (px, pz) mit Richtung y. */
    static double[] toWorld(double lx, double lz, double px, double pz, double y) {
        double c = Math.cos(y), s = Math.sin(y);
        return new double[]{px + c * lx + s * lz, pz - s * lx + c * lz};
    }

    /** Welt → örtlich (um den aktuellen Ort und die Richtung). */
    double[] toLocal(double wx, double wz) {
        double dx = wx - x, dz = wz - z, c = Math.cos(yaw), s = Math.sin(yaw);
        return new double[]{c * dx - s * dz, s * dx + c * dz};
    }

    static double frac(double v) { return v - Math.floor(v); }
    static double smooth(double a, double b, double v) { double t = Math.max(0, Math.min(1, (v - a) / (b - a))); return t * t * (3 - 2 * t); }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] rot(double[] ax, double ang, double[] v) {
        double[] m = M4.rotation(ax[0], ax[1], ax[2], ang);
        return new double[]{m[0] * v[0] + m[4] * v[1] + m[8] * v[2], m[1] * v[0] + m[5] * v[1] + m[9] * v[2], m[2] * v[0] + m[6] * v[1] + m[10] * v[2]};
    }

    @Override public String toString() {
        return String.format(java.util.Locale.GERMANY, "%s · %.2f m/s · Schritt %.2f m · Takt %.2f/s · Gang %.2f%s",
                cur == null ? "steht" : cur.kind(), speed, stride / 2, cadence * 2, gait, sit > 0.5 ? " · sitzt" : "");
    }
}
