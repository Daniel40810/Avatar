package com.dan.avatar;

import com.dan.gltf.Gltf;

/**
 * Das Feld für Tropfen auf der Haut des Kopfes (Phase 8): ein Gitter über dem UV-Ausschnitt von
 * Gesicht, Ohren und Hals, aus der Grundform gerechnet. Je Zelle: gültig, Lage und Normale in Ruhe,
 * Fläche (m²), Steilheit (0 waagerecht … 1 senkrecht) und die Richtung bergab im Gitter (Zellen je
 * Meter Weg auf der Haut). Damit laufen Tropfen im Texturraum so, wie sie auf dem Gesicht nach unten
 * laufen würden.
 */
public final class DropField {

    /** Zellen je Seite. */
    public final int n;
    /** UV-Ausschnitt {u0, v0, u1, v1}. */
    public final double[] rect = new double[4];
    public final boolean[] valid;
    /** Unter dem Haar (über dem Haaransatz) – dort hält das Haar den Regen. */
    public final boolean[] scalp;
    /** Lage und Normale in Ruhe (je 3). */
    public final float[] pos, nrm;
    /** Fläche der Zelle (m²) und Steilheit. */
    public final float[] area, steep;
    /** Richtung bergab: Zellen je Meter Weg (je 2). */
    public final float[] down;
    /** Gültige Zellen. */
    public final int validCount;

    public DropField(Body body) { this(body, 256); }

    public DropField(Body body, int n) {
        this.n = n;
        Body.Part bp = body.body();
        Gltf.Primitive pr = bp.prim;
        float[] P = pr.position, N = pr.normal, U = pr.uv0;
        int[] ix = bp.visibleIndices();
        SkinMasks sm = SkinMasks.of(body);
        double neckY = sm.chin[1] - 0.05;
        double hairline = (sm.eyeL[1] + sm.eyeR[1]) / 2 + 0.075;
        // Kopf-Insel im UV: dichtestes zusammenhängendes Gebiet der Kopfecken (32 × 32)
        int H = 32;
        int[] hist = new int[H * H];
        for (int k = 0; k + 2 < ix.length; k += 3) {
            if (centroidY(P, ix, k) < neckY) continue;
            double u = (U[ix[k] * 2] + U[ix[k + 1] * 2] + U[ix[k + 2] * 2]) / 3, v = (U[ix[k] * 2 + 1] + U[ix[k + 1] * 2 + 1] + U[ix[k + 2] * 2 + 1]) / 3;
            int cx = clampI((int) (u * H), 0, H - 1), cy = clampI((int) (v * H), 0, H - 1);
            hist[cy * H + cx]++;
        }
        int[] comp = new int[H * H];
        int best = -1, bestCount = 0, id = 0;
        for (int c = 0; c < H * H; c++) {
            if (hist[c] == 0 || comp[c] != 0) continue;
            id++;
            int count = 0;
            java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
            q.add(c); comp[c] = id;
            while (!q.isEmpty()) {
                int e = q.poll(); count += hist[e];
                int ex = e % H, ey = e / H;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int x = ex + dx, y = ey + dy;
                    if (x < 0 || y < 0 || x >= H || y >= H) continue;
                    int f = y * H + x;
                    if (hist[f] > 0 && comp[f] == 0) { comp[f] = id; q.add(f); }
                }
            }
            if (count > bestCount) { bestCount = count; best = id; }
        }
        double u0 = 1, v0 = 1, u1 = 0, v1 = 0;
        for (int c = 0; c < H * H; c++) if (comp[c] == best) {
            u0 = Math.min(u0, (c % H) / (double) H); u1 = Math.max(u1, (c % H + 1) / (double) H);
            v0 = Math.min(v0, (c / H) / (double) H); v1 = Math.max(v1, (c / H + 1) / (double) H);
        }
        rect[0] = u0; rect[1] = v0; rect[2] = u1; rect[3] = v1;
        int cells = n * n;
        valid = new boolean[cells]; scalp = new boolean[cells];
        pos = new float[cells * 3]; nrm = new float[cells * 3];
        area = new float[cells]; steep = new float[cells]; down = new float[cells * 2];
        double su = n / (u1 - u0), sv = n / (v1 - v0);
        for (int k = 0; k + 2 < ix.length; k += 3) {
            if (centroidY(P, ix, k) < neckY) continue;
            int a = ix[k], b = ix[k + 1], c = ix[k + 2];
            double[] ga = {(U[a * 2] - u0) * su, (U[a * 2 + 1] - v0) * sv}, gb = {(U[b * 2] - u0) * su, (U[b * 2 + 1] - v0) * sv}, gc = {(U[c * 2] - u0) * su, (U[c * 2 + 1] - v0) * sv};
            if (Math.min(ga[0], Math.min(gb[0], gc[0])) < -1 || Math.max(ga[0], Math.max(gb[0], gc[0])) > n + 1
                    || Math.min(ga[1], Math.min(gb[1], gc[1])) < -1 || Math.max(ga[1], Math.max(gb[1], gc[1])) > n + 1) continue;
            // Lage als lineare Funktion der Gitterkoordinaten: P = Pa + Pu·(x − xa) + Pv·(y − ya)
            double e1x = gb[0] - ga[0], e1y = gb[1] - ga[1], e2x = gc[0] - ga[0], e2y = gc[1] - ga[1];
            double det = e1x * e2y - e2x * e1y;
            if (Math.abs(det) < 1e-12) continue;
            double[] Pu = new double[3], Pv = new double[3];
            for (int d = 0; d < 3; d++) {
                double q1 = P[b * 3 + d] - P[a * 3 + d], q2 = P[c * 3 + d] - P[a * 3 + d];
                Pu[d] = (q1 * e2y - q2 * e1y) / det;
                Pv[d] = (q2 * e1x - q1 * e2x) / det;
            }
            // Metrik und Gefälle: bergab im Gitter = −G⁻¹ ∇y
            double guu = dot(Pu, Pu), guv = dot(Pu, Pv), gvv = dot(Pv, Pv);
            double gdet = guu * gvv - guv * guv;
            if (gdet < 1e-20) continue;
            double yu = Pu[1], yv = Pv[1];
            double dx = -(gvv * yu - guv * yv) / gdet, dy = -(-guv * yu + guu * yv) / gdet;
            double slope2 = yu * (-dx) + yv * (-dy);                // |∇y|² auf der Fläche
            double st = Math.sqrt(Math.max(0, slope2));
            double wlen = Math.sqrt(dx * dx * guu + 2 * dx * dy * guv + dy * dy * gvv);   // Meter je Schritt (dx, dy)
            double cellArea = Math.sqrt(gdet);                         // m² je Zelle
            int x0 = clampI((int) Math.floor(Math.min(ga[0], Math.min(gb[0], gc[0]))), 0, n - 1), x1 = clampI((int) Math.ceil(Math.max(ga[0], Math.max(gb[0], gc[0]))), 0, n - 1);
            int y0 = clampI((int) Math.floor(Math.min(ga[1], Math.min(gb[1], gc[1]))), 0, n - 1), y1 = clampI((int) Math.ceil(Math.max(ga[1], Math.max(gb[1], gc[1]))), 0, n - 1);
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++) {
                    double px = x + 0.5 - ga[0], py = y + 0.5 - ga[1];
                    double l1 = (px * e2y - e2x * py) / det, l2 = (e1x * py - px * e1y) / det;
                    if (l1 < -0.02 || l2 < -0.02 || l1 + l2 > 1.02) continue;
                    int cI = y * n + x;
                    valid[cI] = true;
                    double l0 = 1 - l1 - l2;
                    double nx = 0, ny = 0, nz = 0;
                    for (int d = 0; d < 3; d++) {
                        double p = l0 * P[a * 3 + d] + l1 * P[b * 3 + d] + l2 * P[c * 3 + d];
                        pos[cI * 3 + d] = (float) p;
                    }
                    nx = l0 * N[a * 3] + l1 * N[b * 3] + l2 * N[c * 3];
                    ny = l0 * N[a * 3 + 1] + l1 * N[b * 3 + 1] + l2 * N[c * 3 + 1];
                    nz = l0 * N[a * 3 + 2] + l1 * N[b * 3 + 2] + l2 * N[c * 3 + 2];
                    double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    nrm[cI * 3] = (float) (nx / nl); nrm[cI * 3 + 1] = (float) (ny / nl); nrm[cI * 3 + 2] = (float) (nz / nl);
                    area[cI] = (float) cellArea;
                    steep[cI] = (float) Math.min(1, st);
                    if (wlen > 1e-12) { down[cI * 2] = (float) (dx / wlen); down[cI * 2 + 1] = (float) (dy / wlen); }
                    scalp[cI] = pos[cI * 3 + 1] > hairline && nz < 0.9;
                }
        }
        int vc = 0;
        for (boolean b : valid) if (b) vc++;
        validCount = vc;
    }

    /** Zelle zu Gitterkoordinaten oder −1. */
    public int cell(double x, double y) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y);
        if (cx < 0 || cy < 0 || cx >= n || cy >= n) return -1;
        int c = cy * n + cx;
        return valid[c] ? c : -1;
    }

    /** Meter je Zelle (Mittel) an einer Zelle. */
    public double meterPerCell(int c) { return Math.sqrt(area[c]); }

    private static double centroidY(float[] P, int[] ix, int k) {
        return (P[ix[k] * 3 + 1] + P[ix[k + 1] * 3 + 1] + P[ix[k + 2] * 3 + 1]) / 3;
    }

    private static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    private static int clampI(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}
