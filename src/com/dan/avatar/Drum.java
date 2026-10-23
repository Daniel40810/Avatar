package com.dan.avatar;

/**
 * Handtrommel (Phase 18): eine Rahmentrommel (Durchmesser 42 cm, Zarge 6,5 cm) vor der Brust. Die linke Hand hält sie
 * am Rand, die rechte schlägt im Takt auf das Fell: auf dem Schlag liegt sie auf, dazwischen hebt sie sich (auf der
 * Eins höher und schneller), Nachschläge auf der Zwei und Vier ein wenig leiser. Arme mit Zwei-Knochen-IK (Ellbogen
 * nach unten außen), die Hände zum Fell gedreht. Lage in Figur-Koordinaten für die Szene ({@link #center},
 * {@link #normal}, {@link #right}), dazu {@link #hit} (0 … 1, Ausschwingen des Fells nach einem Schlag).
 */
public final class Drum {

    public final double radius = 0.21, depth = 0.065;
    /** Mitte des Fells, Richtung des Fells (nach außen), Querachse (zur linken Hand) — Figur-Koordinaten. */
    public volatile double[] center = {0, 1.2, 0.3}, normal = {0, 0, 1}, right = {1, 0, 0};
    /** Ausschwingen des Fells (1 im Moment des Schlags, klingt in 150 ms ab) und Lautstärke des letzten Schlags. */
    public volatile double hit, lastStrength;
    /** Höhe der schlagenden Hand über dem Fell (m), zuletzt. */
    public volatile double handHeight;
    /** Wie hoch die Hand ausholt (m) auf der Eins; die anderen Schläge 70 %. */
    public volatile double lift = 0.16;
    /** Zähler der Schläge (für Ton und Prüfung). */
    public int strikes;
    private double lastBeat = Double.NaN;

    /**
     * Arme an die Trommel; {@code beat}: Schläge stetig gezählt, {@code down}: Nummer einer Eins, {@code beatS}: Dauer
     * eines Schlags (s).
     */
    public void apply(Skeleton sk, double[][] w, double beat, double down, double beatS) {
        int ul = sk.joint("upperarm_l"), ur = sk.joint("upperarm_r"), s3 = sk.joint("spine_03");
        if (ul < 0 || ur < 0 || s3 < 0) return;
        double[] x = Skeleton.norm(Skeleton.sub(M4.pos(w[ul]), M4.pos(w[ur])));        // links
        double[] y = {0, 1, 0};
        double[] z = Skeleton.norm(new double[]{x[1] * y[2] - x[2] * y[1], x[2] * y[0] - x[0] * y[2], x[0] * y[1] - x[1] * y[0]});
        y = new double[]{z[1] * x[2] - z[2] * x[1], z[2] * x[0] - z[0] * x[2], z[0] * x[1] - z[1] * x[0]};
        double[] chest = M4.pos(w[s3]);
        // Trommel vor der Brust, etwas links, das Fell schräg nach vorn oben zur rechten Hand
        double[] c = add(chest, add(scale(z, 0.30), add(scale(x, 0.05), scale(y, -0.10))));
        double[] n = Skeleton.norm(add(scale(z, 0.55), add(scale(y, 0.75), scale(x, -0.30))));
        double[] rt = Skeleton.norm(Skeleton.sub(x, scale(n, Skeleton.dot(x, n))));
        center = c; normal = n; right = rt;
        // Schlag: auf dem Schlag aufliegen, dazwischen ausholen
        double ph = beat - Math.floor(beat);
        int i = (int) Math.floor(beat);
        boolean one = Math.floorMod((long) (i - Math.round(down)), 4) == 0;
        double l = lift * (one ? 1 : 0.7);
        double h = l * Math.pow(Math.sin(Math.PI * Math.pow(ph, 0.8)), 0.9);
        handHeight = h;
        if (!Double.isNaN(lastBeat) && Math.floor(beat) > Math.floor(lastBeat) && beat - lastBeat < 1) {
            strikes++;
            lastStrength = one ? 1 : 0.7;
        }
        lastBeat = beat;
        hit = lastStrength * Math.exp(-ph * beatS / 0.15);
        // rechte Hand flach auf dem Fell (Phase 19: vorher stand das Handgelenk über dem Fell und die Finger zeigten
        // hinein): Finger zur Mitte parallel zum Fell, das Handgelenk eine Handlänge rechts davon; beim Ausholen hebt
        // sich die ganze Hand und klappt im Handgelenk nach oben
        int hr = sk.joint("hand_r"), tipR = sk.joint("middle_03_r");
        double handLen = hr >= 0 && tipR >= 0 ? Skeleton.dist(M4.pos(w[hr]), M4.pos(w[tipR])) + 0.02 : 0.18;
        double[] contact = add(c, scale(rt, -0.03));
        double[] fingers = Skeleton.norm(add(rt, scale(n, Math.min(0.8, 2.5 * h))));
        // erst zur Probe auf einer Kopie: wie tief hängen die Finger unter dem Handgelenk? Dann das Handgelenk so hoch,
        // dass die tiefste Fingerspitze genau mit h über dem Fell steht (sonst landen die Finger vor dem Schlag)
        double[][] probe = new double[w.length][];
        for (int k = 0; k < w.length; k++) probe[k] = w[k].clone();
        double[] wrist0 = add(contact, add(scale(rt, -0.62 * handLen), scale(n, PALM)));
        armTo(sk, probe, "r", wrist0, add(scale(x, -1), scale(y, -1)));
        aimHand(sk, probe, "r", fingers);
        double droop = PALM - lowestTip(sk, probe, c, n);                  // > 0: Finger tiefer als der Handballen
        double lift0 = Math.max(PALM, droop + 0.004);
        double[] wristR = add(contact, add(scale(rt, -0.62 * handLen), scale(n, lift0 + h)));
        armTo(sk, w, "r", wristR, add(scale(x, -1), scale(y, -1)));
        aimHand(sk, w, "r", fingers);
        // linke Hand: Daumen über dem Rand links, die Finger innen hinter dem Fell
        double[] grip = add(c, add(scale(rt, radius + 0.035), scale(n, -depth * 0.6)));
        armTo(sk, w, "l", grip, add(scale(x, 1), scale(y, -1)));
        aimHand(sk, w, "l", Skeleton.norm(add(scale(rt, -1), scale(n, -0.7))));
        // Sicherung: Finger, die doch unter das Fell geraten, wieder darüber (rechte Hand)
        double low = lowestTip(sk, w, c, n);
        if (low < 0.004 && hr >= 0) sk.moveSubtree(w, hr, M4.translation(n[0] * (0.004 - low), n[1] * (0.004 - low), n[2] * (0.004 - low)));
        fingerGap = lowestTip(sk, w, c, n);
    }

    static double lowestTip(Skeleton sk, double[][] w, double[] c, double[] n) {
        double low = Double.MAX_VALUE;
        for (String fj : TIPS_R) { int k = sk.joint(fj); if (k >= 0) low = Math.min(low, Skeleton.dot(Skeleton.sub(M4.pos(w[k]), c), n)); }
        return low;
    }

    /** Dicke von Handballen und Fingern über dem Fell (m). */
    static final double PALM = 0.028;
    /** Fingerspitzen und -glieder der rechten Hand. */
    static final String[] TIPS_R = {"index_02_r", "index_03_r", "middle_02_r", "middle_03_r", "ring_02_r", "ring_03_r", "pinky_02_r", "pinky_03_r", "thumb_03_r"};
    /** Abstand der tiefsten Fingerspitze rechts über dem Fell (m), zuletzt; nie unter 0. */
    public volatile double fingerGap;

    /** Zwei-Knochen-IK: Handgelenk an {@code target}, Ellbogen zur Seite {@code pole}. */
    static void armTo(Skeleton sk, double[][] w, String side, double[] target, double[] pole) {
        int ua = sk.joint("upperarm_" + side), la = sk.joint("lowerarm_" + side), ha = sk.joint("hand_" + side);
        if (ua < 0 || la < 0 || ha < 0) return;
        double[] s = M4.pos(w[ua]), e = M4.pos(w[la]), h = M4.pos(w[ha]);
        double a = Skeleton.dist(s, e), b = Skeleton.dist(e, h);
        double[] st = Skeleton.sub(target, s);
        double d = Math.max(1e-4, Math.min(Skeleton.len(st), a + b - 1e-4));
        double[] u = Skeleton.norm(st);
        double[] pp = Skeleton.sub(pole, scale(u, Skeleton.dot(pole, u)));
        pp = Skeleton.norm(pp);
        double cosA = Math.max(-1, Math.min(1, (a * a + d * d - b * b) / (2 * a * d)));
        double sinA = Math.sqrt(1 - cosA * cosA);
        double[] e2 = add(s, add(scale(u, a * cosA), scale(pp, a * sinA)));
        sk.moveSubtree(w, ua, M4.about(M4.rotationBetween(Skeleton.norm(Skeleton.sub(e, s)), Skeleton.norm(Skeleton.sub(e2, s))), s));
        double[] h1 = M4.pos(w[ha]);
        double[] t2 = add(s, scale(u, d));
        sk.moveSubtree(w, la, M4.about(M4.rotationBetween(Skeleton.norm(Skeleton.sub(h1, e2)), Skeleton.norm(Skeleton.sub(t2, e2))), e2));
    }

    static void aimHand(Skeleton sk, double[][] w, String side, double[] dir) {
        int ha = sk.joint("hand_" + side), mid = sk.joint("middle_01_" + side);
        if (ha >= 0 && mid >= 0) sk.aim(w, ha, mid, dir, 0.8);
    }

    /** Höhe der schlagenden Hand über dem Fell (m; 0 = sie liegt auf). */
    public double handToSkin(Skeleton sk, double[][] w) {
        int ha = sk.joint("hand_r");
        if (ha < 0) return Double.NaN;
        double[] p = M4.pos(w[ha]);
        return Skeleton.dot(Skeleton.sub(p, center), normal) - PALM;
    }

    static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }

    static double[] scale(double[] a, double s) { return new double[]{a[0] * s, a[1] * s, a[2] * s}; }
}
