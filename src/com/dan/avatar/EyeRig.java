package com.dan.avatar;

import com.dan.gltf.Gltf;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Die beiden Augäpfel als Kugeln (Phase 5, F9 A), reines Java. Das Augennetz aus MPFB besteht je
 * Auge aus zwei Schalen: Augapfel (Lederhaut, Iris als Mulde) und Hornhaut (darüber, durchsichtig).
 * In der Grundform wird je Auge eine Kugel an den Augapfel gelegt (ohne die Mulde der Iris), die
 * Blickachse geht durch die Mitte der Mulde. Zur Laufzeit folgen Mitte, Achsen und Radius dem
 * gemischten und verformten Netz (Blickformen eyeLook…, Gesichtsregler, Kopfknochen): aus drei
 * Gruppen von Ecken (alle, vorn, oben) wird die starre Bewegung samt Maßstab bestimmt.
 * Der Shader rechnet das Auge dann analytisch: Hornhaut, Brechung, Irisebene, Lederhaut.
 */
public final class EyeRig {

    /** Index des Augennetzes in {@code body.parts} oder −1. */
    public final int part;
    /** Je Ecke des Augennetzes: Hornhaut (wird nicht gezeichnet)? */
    public final boolean[] cornea;
    /** Je Auge (0 = links, +x; 1 = rechts): Ecken des Augapfels, vorn (Irismulde) und oben. */
    final int[][] all = new int[2][], front = new int[2][], up = new int[2][];
    /** Grundform: Kugelmitte, Radius, Achsen (x, y, z = Blick), Schwerpunkte der Gruppen. */
    public final double[][] c0 = new double[2][], ax0 = new double[2][], ay0 = new double[2][], az0 = new double[2][];
    public final double[] r0 = new double[2];
    private final double[][] gAll0 = new double[2][], gFront0 = new double[2][], gUp0 = new double[2][];

    /** Lage eines Auges: Mitte, Achsen (Einheitsvektoren), Radius; Koordinaten wie die Ecken. */
    public static final class Frame {
        public final double[] c = new double[3], x = new double[3], y = new double[3], z = new double[3];
        public double r;
    }

    public EyeRig(Body body) {
        int pi = -1;
        for (int i = 0; i < body.parts.size(); i++) {
            String n = body.parts.get(i).mesh.name.toLowerCase();
            if (n.contains("high-poly") || n.contains("low-poly") || n.equals("eyes")) pi = i;
        }
        part = pi;
        if (pi < 0) { cornea = new boolean[0]; return; }
        Gltf.Primitive pr = body.parts.get(pi).prim;
        float[] P = pr.position, U = pr.uv0;
        int n = pr.vertexCount;
        cornea = new boolean[n];
        List<List<Integer>> ball = List.of(new ArrayList<>(), new ArrayList<>());
        for (int i = 0; i < n; i++) {
            // Hornhaut: eigener Bereich oben rechts in der UV-Karte des MakeHuman-Auges
            cornea[i] = U != null && U[i * 2] > 0.87f && U[i * 2 + 1] > 0.87f;
            if (!cornea[i]) ball.get(P[i * 3] > 0 ? 0 : 1).add(i);
        }
        for (int e = 0; e < 2; e++) {
            List<Integer> b = ball.get(e);
            double[] s = fitSphere(P, b);
            // Mulde der Iris (deutlich innerhalb der Kugel) weglassen und neu anpassen
            List<Integer> shell = new ArrayList<>(), dent = new ArrayList<>();
            for (int i : b) (dist(P, i, s) - s[3] < -0.0006 ? dent : shell).add(i);
            s = fitSphere(P, shell);
            dent.clear();
            for (int i : b) if (dist(P, i, s) - s[3] < -0.0006) dent.add(i);
            c0[e] = new double[]{s[0], s[1], s[2]};
            r0[e] = s[3];
            double[] gd = centroid(P, dent);
            double[] z = norm(sub(gd, c0[e]));
            double[] x = norm(cross(new double[]{0, 1, 0}, z));
            double[] y = cross(z, x);
            ax0[e] = x; ay0[e] = y; az0[e] = z;
            // Gruppe „oben“: Augapfel-Ecken in Richtung +y (40°–90° von der Blickachse)
            List<Integer> upl = new ArrayList<>();
            for (int i : shell) {
                double[] d = norm(sub(new double[]{P[i * 3], P[i * 3 + 1], P[i * 3 + 2]}, c0[e]));
                if (dot(d, y) > 0.6) upl.add(i);
            }
            all[e] = b.stream().mapToInt(Integer::intValue).toArray();
            front[e] = dent.stream().mapToInt(Integer::intValue).toArray();
            up[e] = upl.stream().mapToInt(Integer::intValue).toArray();
            gAll0[e] = centroid(P, b);
            gFront0[e] = gd;
            gUp0[e] = centroid(P, upl);
        }
    }

    /**
     * Pupillendurchmesser in mm für eine Leuchtdichte in cd/m² (Moon und Spencer 1944):
     * D = 4,9 − 3 · tanh(0,4 · log10 L); etwa 2 mm im hellen Tag, 7–8 mm im Dunkeln.
     */
    public static double pupilMm(double luminance) {
        return 4.9 - 3 * Math.tanh(0.4 * Math.log10(Math.max(1e-5, luminance)));
    }

    public boolean valid() { return part >= 0 && all[0] != null && front[0].length > 0 && up[0].length > 0; }

    /**
     * Lage beider Augen für diese Gewichte und dieses Skinning (Dual-Quaternionen je Gelenk wie
     * {@link Skeleton#toDq}; null = Ruhehaltung). Rechnet nur das Augennetz, gleich wie die Grafikkarte.
     */
    public Frame[] frames(Body body, Map<String, Double> weights, float[] dq) {
        Frame[] out = {new Frame(), new Frame()};
        if (!valid()) return out;
        Gltf.Primitive pr = body.parts.get(part).prim;
        int n = pr.vertexCount;
        float[] pos = pr.position.clone();
        for (Gltf.Target t : pr.targets) {
            Double w = weights.get(t.name);
            if (w == null || w == 0) continue;
            float fw = (float) (double) w;
            for (int j = 0; j < t.index.length; j++) {
                int o = t.index[j] * 3;
                pos[o] += fw * t.dPosition[j * 3];
                pos[o + 1] += fw * t.dPosition[j * 3 + 1];
                pos[o + 2] += fw * t.dPosition[j * 3 + 2];
            }
        }
        float[] sk = pos;
        if (dq != null && pr.joints != null) {
            sk = new float[n * 3];
            float[] nn = new float[n * 3], on = new float[n * 3];
            for (int v = 0; v < n; v++)
                Dq.skin(dq, pr.joints, pr.weights, v, pos[v * 3], pos[v * 3 + 1], pos[v * 3 + 2], 0, 0, 1, sk, on, v * 3);
        }
        for (int e = 0; e < 2; e++) {
            double[] ga = centroid(sk, all[e]), gf = centroid(sk, front[e]), gu = centroid(sk, up[e]);
            // Rahmen aus (vorn − alle) und (oben − alle), jetzt und in der Grundform
            double[] f0 = sub(gFront0[e], gAll0[e]), u0 = sub(gUp0[e], gAll0[e]);
            double[] f1 = sub(gf, ga), u1 = sub(gu, ga);
            double s = len(f1) / len(f0);
            double[][] B0 = basis(f0, u0), B1 = basis(f1, u1);
            Frame fr = out[e];
            fr.r = r0[e] * s;
            double[] off = sub(c0[e], gAll0[e]);
            double[] offN = rot(B0, B1, off);
            for (int k = 0; k < 3; k++) fr.c[k] = ga[k] + s * offN[k];
            System.arraycopy(norm(rot(B0, B1, ax0[e])), 0, fr.x, 0, 3);
            System.arraycopy(norm(rot(B0, B1, ay0[e])), 0, fr.y, 0, 3);
            System.arraycopy(norm(rot(B0, B1, az0[e])), 0, fr.z, 0, 3);
        }
        return out;
    }

    // ------------------------------------------------------------ Hilfen

    /** Orthonormale Basis (a, b, c) aus Richtung f und ungefähr u. */
    private static double[][] basis(double[] f, double[] u) {
        double[] a = norm(f);
        double[] c = norm(cross(a, u));
        double[] b = cross(c, a);
        return new double[][]{a, b, c};
    }

    /** v aus Basis B0 in Basis B1 drehen: R = B1 · B0ᵀ. */
    private static double[] rot(double[][] B0, double[][] B1, double[] v) {
        double[] l = {dot(v, B0[0]), dot(v, B0[1]), dot(v, B0[2])};
        return new double[]{l[0] * B1[0][0] + l[1] * B1[1][0] + l[2] * B1[2][0],
                            l[0] * B1[0][1] + l[1] * B1[1][1] + l[2] * B1[2][1],
                            l[0] * B1[0][2] + l[1] * B1[1][2] + l[2] * B1[2][2]};
    }

    /** Kugel nach kleinsten Quadraten (algebraisch): Mitte und Radius. */
    static double[] fitSphere(float[] P, List<Integer> idx) {
        double[][] A = new double[4][4];
        double[] B = new double[4];
        for (int i : idx) {
            double x = P[i * 3], y = P[i * 3 + 1], z = P[i * 3 + 2];
            double[] r = {x, y, z, 1};
            double rhs = -(x * x + y * y + z * z);
            for (int a = 0; a < 4; a++) {
                B[a] += r[a] * rhs;
                for (int c = 0; c < 4; c++) A[a][c] += r[a] * r[c];
            }
        }
        double[] q = solve(A, B);
        double cx = -q[0] / 2, cy = -q[1] / 2, cz = -q[2] / 2;
        return new double[]{cx, cy, cz, Math.sqrt(Math.max(0, cx * cx + cy * cy + cz * cz - q[3]))};
    }

    private static double[] solve(double[][] A, double[] b) {
        int n = b.length;
        double[][] M = new double[n][n + 1];
        for (int i = 0; i < n; i++) { System.arraycopy(A[i], 0, M[i], 0, n); M[i][n] = b[i]; }
        for (int c = 0; c < n; c++) {
            int p = c;
            for (int r = c + 1; r < n; r++) if (Math.abs(M[r][c]) > Math.abs(M[p][c])) p = r;
            double[] t = M[c]; M[c] = M[p]; M[p] = t;
            for (int r = 0; r < n; r++)
                if (r != c) {
                    double f = M[r][c] / M[c][c];
                    for (int k = c; k <= n; k++) M[r][k] -= f * M[c][k];
                }
        }
        double[] x = new double[n];
        for (int i = 0; i < n; i++) x[i] = M[i][n] / M[i][i];
        return x;
    }

    private static double[] centroid(float[] P, List<Integer> idx) {
        double[] s = new double[3];
        for (int i : idx) { s[0] += P[i * 3]; s[1] += P[i * 3 + 1]; s[2] += P[i * 3 + 2]; }
        int n = Math.max(1, idx.size());
        return new double[]{s[0] / n, s[1] / n, s[2] / n};
    }

    private static double[] centroid(float[] P, int[] idx) {
        double[] s = new double[3];
        for (int i : idx) { s[0] += P[i * 3]; s[1] += P[i * 3 + 1]; s[2] += P[i * 3 + 2]; }
        int n = Math.max(1, idx.length);
        return new double[]{s[0] / n, s[1] / n, s[2] / n};
    }

    private static double dist(float[] P, int i, double[] c) {
        double dx = P[i * 3] - c[0], dy = P[i * 3 + 1] - c[1], dz = P[i * 3 + 2] - c[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double len(double[] a) { return Math.sqrt(dot(a, a)); }
    static double[] norm(double[] a) { double l = len(a); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
    static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }
}
