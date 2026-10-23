package com.dan.avatar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Tanz aus Bewegungsaufnahmen (Phase 18): mehrere kurze Aufnahmen ({@link Mocap}) werden auf den Körper jeder Person
 * übertragen, im Takt eines Lieds gedehnt und aneinandergereiht.
 *
 * <ul>
 * <li><b>Takt der Aufnahme:</b> die Schrittfolge aus der Autokorrelation der Fußgeschwindigkeit (0,25–0,65 s), die Lage
 * der Schritte dort, wo das Becken am tiefsten ist. Je Schlag des Lieds ein, zwei oder ein halber Schritt, so dass die
 * Aufnahme am wenigsten gedehnt wird.</li>
 * <li><b>Folge:</b> je Takt (vier Schläge) ein Stück aus einer Aufnahme, reihum; zwischen zwei Stücken wird über einen
 * Drittel Schlag gemischt (Posen sphärisch).</li>
 * <li><b>Übertragung:</b> das Becken nach dem Rahmen aus Hüften und Kreuz, dann je Knochen die Richtung wie in der
 * Aufnahme (Wirbelsäule, Hals, Schlüsselbeine, Arme, Hände, Beine, Füße), der Brustkorb mit der Drehung der Schultern;
 * Längen bleiben die der Person. Der tiefere Knöchel steht so hoch über dem Boden wie in der Aufnahme (gestreckt mit
 * dem Verhältnis der Beinlängen), Wege wie aufgenommen (gestreckt mit dem Verhältnis der Beinlängen). Der Kopf bleibt dem Verhalten (Blick).</li>
 * </ul>
 */
public final class Dance {

    /** Eine Aufnahme mit ihrem Takt. */
    public static final class Clip {
        public final Mocap m;
        /** Dauer eines Schritts (s) und Lage des ersten (s). */
        public final double period, phase;
        /** Tiefster Knöchel in der Aufnahme (m) und Beinlänge (m). */
        final double ankleFloor, leg;

        Clip(Mocap m) {
            this.m = m;
            int n = m.frames(), lf = m.bone("lfoot"), rf = m.bone("rfoot"), lt = m.bone("ltibia"), rt = m.bone("rtibia");
            double[] sp = new double[n];
            for (int f = 1; f < n; f++) sp[f] = dist(m.end(lf, f), m.end(lf, f - 1)) + dist(m.end(rf, f), m.end(rf, f - 1));
            double mean = 0;
            for (double v : sp) mean += v;
            mean /= n;
            int lo = (int) Math.round(0.25 * m.fps), hi = (int) Math.round(0.65 * m.fps);
            double best = -1e18;
            int bl = lo;
            for (int lag = lo; lag <= hi && lag < n - 1; lag++) {
                double c = 0;
                for (int f = lag; f < n; f++) c += (sp[f] - mean) * (sp[f - lag] - mean);
                c /= n - lag;
                if (c > best) { best = c; bl = lag; }
            }
            period = bl / m.fps;
            // Lage: die Schritte dorthin, wo das Becken am tiefsten ist
            double bestPh = 0, bs = 1e18;
            for (int o = 0; o < bl; o++) {
                double s = 0;
                int k = 0;
                for (int f = o; f < n; f += bl) { s += m.end(0, f)[1]; k++; }
                s /= Math.max(1, k);
                if (s < bs) { bs = s; bestPh = o / m.fps; }
            }
            phase = bestPh;
            double af = 1e9;
            for (int f = 0; f < n; f++) af = Math.min(af, Math.min(m.end(lt, f)[1], m.end(rt, f)[1]));
            ankleFloor = af;
            leg = m.length[m.bone("lfemur")] + m.length[m.bone("ltibia")];
        }

        /** Schritte je Schlag bei einem Liedschlag von {@code beatS} Sekunden: ½, 1 oder 2 (am wenigsten gedehnt). */
        public double stepsPerBeat(double beatS) {
            double best = 1, bd = 1e9;
            for (double k : new double[]{0.5, 1, 2}) {
                double d = Math.abs(Math.log(beatS / (k * period)));
                if (d < bd) { bd = d; best = k; }
            }
            return best;
        }

        /** Wie viele Takte zu vier Schlägen in die Aufnahme passen (mit Rand zum Mischen). */
        int bars(double k) {
            double barM = 4 * k * period;
            return Math.max(1, (int) Math.floor((m.seconds() - phase - 0.3 * period) / barM));
        }
    }

    public final List<Clip> clips = new ArrayList<>();
    /** Schlag des Lieds in Sekunden (für die Dehnung). */
    public volatile double beatS = 0.53;
    /**
     * Wege auf der Stelle (Anteil der Aufnahme). Phase 19: 1, wie aufgenommen; bei kleineren Werten gleiten die Füße,
     * weil das Becken weniger weit geht als die Beine schreiten.
     */
    public volatile double travel = 1.0;

    public Dance(List<Mocap> takes) { for (Mocap m : takes) clips.add(new Clip(m)); }

    /** Welche Aufnahme und welche Stelle zum Schlag b gehört (Takt = floor(b / 4)). */
    public record Place(int clip, double time, double stretch) { }

    /** Stelle in der Aufnahme für den Schlag {@code b} (stetig gezählt ab einer Eins). */
    public Place place(double b) {
        int bar = (int) Math.floor(b / 4);
        Clip c = clips.get(Math.floorMod(bar, clips.size()));
        double k = c.stepsPerBeat(beatS);
        int nb = c.bars(k);
        int round = Math.floorDiv(bar, clips.size());
        double start = c.phase + Math.floorMod(round, nb) * 4 * k * c.period;
        double u = b - 4 * bar;                                       // Schläge im Takt
        return new Place(clips.indexOf(c), start + u * k * c.period, k * c.period / beatS);
    }

    /** Pose zum Schlag {@code b} (mit Mischung in den letzten 0,35 Schlägen eines Takts). */
    public Pose pose(Skeleton sk, double[][] r, double b) {
        Place p = place(b);
        Pose cur = retarget(sk, r, clips.get(p.clip()), p.time());
        double u = b - 4 * Math.floor(b / 4);
        if (u > 3.65 && clips.size() > 0) {
            int nextBar = (int) Math.floor(b / 4) + 1;
            Place q = place(4.0 * nextBar);
            Clip nc = clips.get(q.clip());
            double back = (4 - u) * nc.stepsPerBeat(beatS) * nc.period;   // vor dem Anfang des nächsten Stücks
            Pose nxt = retarget(sk, r, nc, Math.max(0, q.time() - back));
            double t = (u - 3.65) / 0.35;
            cur = Pose.blend(cur, nxt, t * t * (3 - 2 * t));
        }
        return cur;
    }

    /** Weltmatrizen zum Schlag {@code b} mit Zugabe des Verhaltens (Atmen, Blick), ohne Fuß-IK. */
    public double[][] world(Skeleton sk, double[][] r, double b, Skeleton.Additive add) {
        return sk.pose(pose(sk, r, b), r, false, add);
    }

    // ------------------------------------------------------------ Übertragung

    /** Je Knochen der Person: Gelenk, Kind und der Knochen der Aufnahme (Anfang → Ende). */
    static final String[][] AIM = {
            {"spine_01", "spine_02", "lowerback"}, {"spine_02", "spine_03", "upperback"}, {"spine_03", "neck_01", "thorax"},
            {"neck_01", "head", "neck"},
            {"clavicle_l", "upperarm_l", "lclavicle"}, {"upperarm_l", "lowerarm_l", "lhumerus"}, {"lowerarm_l", "hand_l", "lradius"}, {"hand_l", "middle_01_l", "lhandall"},
            {"clavicle_r", "upperarm_r", "rclavicle"}, {"upperarm_r", "lowerarm_r", "rhumerus"}, {"lowerarm_r", "hand_r", "rradius"}, {"hand_r", "middle_01_r", "rhandall"},
            {"thigh_l", "calf_l", "lfemur"}, {"calf_l", "foot_l", "ltibia"}, {"foot_l", "ball_l", "lfoot"},
            {"thigh_r", "calf_r", "rfemur"}, {"calf_r", "foot_r", "rtibia"}, {"foot_r", "ball_r", "rfoot"}};

    /** Richtung eines Knochens der Aufnahme (zusammengesetzte: Hals, ganze Hand). */
    static double[] dir(Mocap m, String b, double f) {
        double[] a, e;
        switch (b) {
            case "neck" -> { a = m.start(m.bone("lowerneck"), f); e = m.end(m.bone("upperneck"), f); }
            case "lhandall" -> { a = m.start(m.bone("lwrist"), f); e = m.end(m.bone("lhand"), f); }
            case "rhandall" -> { a = m.start(m.bone("rwrist"), f); e = m.end(m.bone("rhand"), f); }
            default -> { int i = m.bone(b); a = m.start(i, f); e = m.end(i, f); }
        }
        return Skeleton.norm(Skeleton.sub(e, a));
    }

    /** Pose der Person für die Stelle {@code time} (s) der Aufnahme. */
    Pose retarget(Skeleton sk, double[][] r, Clip c, double time) {
        Mocap m = c.m;
        double f = time * m.fps;
        double[][] w = sk.pose(null, r, false);
        int pv = sk.joint("pelvis"), tl = sk.joint("thigh_l"), tr = sk.joint("thigh_r"), s2 = sk.joint("spine_02");
        double[] pelvis = M4.pos(w[pv]);
        double ankle0 = Math.min(M4.pos(w[sk.joint("foot_l")])[1], M4.pos(w[sk.joint("foot_r")])[1]);
        double legO = Skeleton.dist(M4.pos(w[tl]), M4.pos(w[sk.joint("calf_l")])) + Skeleton.dist(M4.pos(w[sk.joint("calf_l")]), M4.pos(w[sk.joint("foot_l")]));
        double ratio = legO / c.leg;
        // Becken: Rahmen aus Hüften (links) und Kreuz (oben)
        double[][] fo = frame(Skeleton.sub(M4.pos(w[tl]), M4.pos(w[tr])), Skeleton.sub(M4.pos(w[s2]), pelvis));
        double[][] fc = frame(Skeleton.sub(m.start(m.bone("lfemur"), f), m.start(m.bone("rfemur"), f)), Skeleton.sub(m.end(m.bone("lowerback"), f), m.end(0, f)));
        double[] rot = M4.identity();
        // R = Fc · Foᵀ (Spalten der Rahmen sind die Achsen)
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                double s = 0;
                for (int k = 0; k < 3; k++) s += fc[k][i] * fo[k][j];
                rot[j * 4 + i] = s;
            }
        double[] about = M4.about(rot, pelvis);
        for (int j = 0; j < w.length; j++) w[j] = M4.mul(about, w[j]);
        // Richtungen
        for (String[] a : AIM) {
            int j = sk.joint(a[0]), ch = sk.joint(a[1]);
            if (j < 0 || ch < 0) continue;
            sk.aim(w, j, ch, dir(m, a[2], f), 1);
            if (a[0].equals("spine_03")) {
                // Brustkorb: die Schulterlinie wie in der Aufnahme (Drehung um die Achse des Knochens)
                int ul = sk.joint("upperarm_l"), ur = sk.joint("upperarm_r");
                double[] ax = Skeleton.norm(Skeleton.sub(M4.pos(w[ch]), M4.pos(w[j])));
                double[] have = Skeleton.sub(M4.pos(w[ul]), M4.pos(w[ur]));
                double[] want = Skeleton.sub(m.start(m.bone("lhumerus"), f), m.start(m.bone("rhumerus"), f));
                double ang = signedAngle(have, want, ax);
                sk.rotateAt(w, j, ax[0], ax[1], ax[2], ang);
            }
        }
        // Ort: waagerecht wie die Aufnahme (gedämpft), senkrecht nach dem tieferen Knöchel
        double[] root = m.end(0, f);
        double tx = root[0] * ratio * travel, tz = root[2] * ratio * travel;
        double[] pnow = M4.pos(w[pv]);
        double ankleNow = Math.min(M4.pos(w[sk.joint("foot_l")])[1], M4.pos(w[sk.joint("foot_r")])[1]);
        double ankleWant = ankle0 + (Math.min(m.end(m.bone("ltibia"), f)[1], m.end(m.bone("rtibia"), f)[1]) - c.ankleFloor) * ratio;
        double[] mv = M4.translation(tx - pnow[0] + pelvis[0], ankleWant - ankleNow, tz - pnow[2] + pelvis[2]);
        for (int j = 0; j < w.length; j++) w[j] = M4.mul(mv, w[j]);
        // Wurzel (über dem Becken, ohne Haut) aufrecht am Boden unter dem Becken lassen
        for (int j = 0; j < w.length; j++)
            if (sk.parent[j] < 0 && j != pv) { double[] pp = M4.pos(w[pv]); w[j] = M4.mul(M4.translation(pp[0], 0, pp[2]), r[j]); w[j][12] = pp[0]; w[j][13] = r[j][13]; w[j][14] = pp[2]; }
        return toPose(sk, r, w, "tanz " + m.name);
    }

    /** Weltmatrizen → Pose (örtlich je Gelenk). */
    static Pose toPose(Skeleton sk, double[][] r, double[][] w, String name) {
        int n = w.length;
        double[][] q = new double[n][];
        for (int j = 0; j < n; j++) {
            double[] l = Skeleton.local(r, sk.parent, j);
            double[] pp = sk.parent[j] < 0 ? M4.identity() : w[sk.parent[j]];
            q[j] = M4.mul(M4.invRigid(l), M4.mul(M4.invRigid(pp), w[j]));
        }
        Pose p = new Pose(name, q);
        p.supportLegs = List.of();
        return p;
    }

    /** Rechtshändiger Rahmen: x aus {@code left}, y aus {@code up} (senkrecht gemacht), z = x × y (vorn). */
    static double[][] frame(double[] left, double[] up) {
        double[] x = Skeleton.norm(left);
        double[] y = Skeleton.sub(up, Skeleton.scale(x, Skeleton.dot(up, x)));
        y = Skeleton.norm(y);
        double[] z = {x[1] * y[2] - x[2] * y[1], x[2] * y[0] - x[0] * y[2], x[0] * y[1] - x[1] * y[0]};
        return new double[][]{x, y, z};
    }

    /** Winkel von a nach b um die Achse ax (beide in die Ebene senkrecht zur Achse gelegt). */
    static double signedAngle(double[] a, double[] b, double[] ax) {
        double[] pa = Skeleton.sub(a, Skeleton.scale(ax, Skeleton.dot(a, ax))), pb = Skeleton.sub(b, Skeleton.scale(ax, Skeleton.dot(b, ax)));
        double[] cr = {pa[1] * pb[2] - pa[2] * pb[1], pa[2] * pb[0] - pa[0] * pb[2], pa[0] * pb[1] - pa[1] * pb[0]};
        return Math.atan2(Skeleton.dot(cr, ax), Skeleton.dot(pa, pb));
    }

    static double dist(double[] a, double[] b) { return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2])); }

    /** Kurzbeschreibung. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (Clip c : clips) {
            if (sb.length() > 0) sb.append(" · ");
            double k = c.stepsPerBeat(beatS);
            sb.append(String.format(Locale.GERMANY, "%s %.1f s, Schritt %.2f s, %s je Schlag, gedehnt %.2f", c.m.name, c.m.seconds(), c.period,
                    k == 0.5 ? "½" : String.valueOf((int) k), beatS / (k * c.period)));
        }
        return sb.toString();
    }
}
