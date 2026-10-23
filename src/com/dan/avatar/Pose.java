package com.dan.avatar;

import java.util.List;

/**
 * Eine Pose: je Gelenk eine örtliche starre Bewegung im eigenen Ruherahmen, so dass sie auf jede
 * Statur passt (Pose(Welt) = Pose(Eltern) · L · Q mit L = Ruhe(Eltern)⁻¹ · Ruhe).
 * Aus Blender kommt je Knochen D = Pose · Ruhe⁻¹ der Mitte; daraus wird Q gerechnet.
 */
public final class Pose {

    public final String name;
    /** Q je Gelenk (spaltenweise 4×4). */
    final double[][] local;
    /** Senkrecht so verschieben, dass die Fußgelenke auf Ruhehöhe stehen (Sitzen). */
    public boolean fitRoot;
    /** Standbeine für die Fuß-IK ("l", "r"). */
    public List<String> supportLegs = List.of("l", "r");

    Pose(String name, double[][] local) {
        this.name = name;
        this.local = local;
    }

    /**
     * Mischung zweier Posen (Phase 11, Übergänge): je Gelenk die Drehung sphärisch (Quaternion), die
     * Verschiebung linear; {@code t} = 0 gibt {@code a}, 1 gibt {@code b}. Boden und Standbeine von der
     * näheren Pose.
     */
    public static Pose blend(Pose a, Pose b, double t) {
        t = Math.max(0, Math.min(1, t));
        int n = a.local.length;
        double[][] q = new double[n][];
        for (int j = 0; j < n; j++) q[j] = mix(a.local[j], b.local[j], t);
        Pose p = new Pose(t < 0.5 ? a.name : b.name, q);
        p.fitRoot = a.fitRoot || b.fitRoot;
        p.supportLegs = t < 0.5 ? a.supportLegs : b.supportLegs;
        return p;
    }

    static double[] mix(double[] m0, double[] m1, double t) {
        double[] q0 = quat(m0), q1 = quat(m1);
        double d = q0[0] * q1[0] + q0[1] * q1[1] + q0[2] * q1[2] + q0[3] * q1[3];
        if (d < 0) { for (int k = 0; k < 4; k++) q1[k] = -q1[k]; d = -d; }
        double[] q = new double[4];
        if (d > 0.9995) for (int k = 0; k < 4; k++) q[k] = q0[k] + t * (q1[k] - q0[k]);
        else {
            double th = Math.acos(d), s0 = Math.sin((1 - t) * th) / Math.sin(th), s1 = Math.sin(t * th) / Math.sin(th);
            for (int k = 0; k < 4; k++) q[k] = s0 * q0[k] + s1 * q1[k];
        }
        double l = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        for (int k = 0; k < 4; k++) q[k] /= l;
        double[] m = mat(q);
        for (int k = 12; k < 15; k++) m[k] = m0[k] + t * (m1[k] - m0[k]);
        return m;
    }

    /** Quaternion (x, y, z, w) aus dem Drehanteil einer 4×4 (spaltenweise). */
    static double[] quat(double[] m) {
        double m00 = m[0], m11 = m[5], m22 = m[10], tr = m00 + m11 + m22;
        double x, y, z, w;
        if (tr > 0) { double s = Math.sqrt(tr + 1) * 2; w = 0.25 * s; x = (m[6] - m[9]) / s; y = (m[8] - m[2]) / s; z = (m[1] - m[4]) / s; }
        else if (m00 > m11 && m00 > m22) { double s = Math.sqrt(1 + m00 - m11 - m22) * 2; w = (m[6] - m[9]) / s; x = 0.25 * s; y = (m[4] + m[1]) / s; z = (m[8] + m[2]) / s; }
        else if (m11 > m22) { double s = Math.sqrt(1 + m11 - m00 - m22) * 2; w = (m[8] - m[2]) / s; x = (m[4] + m[1]) / s; y = 0.25 * s; z = (m[9] + m[6]) / s; }
        else { double s = Math.sqrt(1 + m22 - m00 - m11) * 2; w = (m[1] - m[4]) / s; x = (m[8] + m[2]) / s; y = (m[9] + m[6]) / s; z = 0.25 * s; }
        return new double[]{x, y, z, w};
    }

    static double[] mat(double[] q) {
        double x = q[0], y = q[1], z = q[2], w = q[3];
        return new double[]{
            1 - 2 * (y * y + z * z), 2 * (x * y + z * w), 2 * (x * z - y * w), 0,
            2 * (x * y - z * w), 1 - 2 * (x * x + z * z), 2 * (y * z + x * w), 0,
            2 * (x * z + y * w), 2 * (y * z - x * w), 1 - 2 * (x * x + y * y), 0,
            0, 0, 0, 1};
    }

    /** Ruhehaltung (alle Q = Einheit). */
    public static Pose rest(Skeleton s) {
        double[][] q = new double[s.size()][];
        for (int j = 0; j < q.length; j++) q[j] = M4.identity();
        return new Pose("ruhe", q);
    }

    static Pose fromBlender(String name, Skeleton s, double[][] d) {
        int n = s.size();
        double[][] posed = new double[n][];
        for (int j = 0; j < n; j++) posed[j] = M4.mul(d[j], s.rest[j]);
        double[][] q = new double[n][];
        for (int j = 0; j < n; j++) {
            double[] l = Skeleton.local(s.rest, s.parent, j);
            double[] pp = s.parent[j] < 0 ? M4.identity() : posed[s.parent[j]];
            q[j] = M4.mul(M4.invRigid(l), M4.mul(M4.invRigid(pp), posed[j]));
        }
        Pose p = new Pose(name, q);
        switch (name) {
            case "sitzen" -> p.fitRoot = true;
            case "lehnen" -> p.supportLegs = List.of("l");
            default -> {}
        }
        return p;
    }
}
