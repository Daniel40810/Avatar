package com.dan.avatar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Netz einer Person für die fernen Detailstufen (Phase 12, F19 A): aus der gemischten Ruhelage einer Person
 * (Körper, getragene Kleidung, Haar) durch Zusammenfassen der Ecken in einem Gitter (Rossignac und Borrel 1993):
 * je Teil und Zelle eine Ecke (Mittel der Lagen, Gewichte der Ecke, die dem Mittel am nächsten liegt), Dreiecke,
 * deren Ecken in dieselbe Zelle fallen, entfallen. Farbe je Teil (Haut, Stoff, Haar), Knochen und Gewichte für das
 * Skinning auf der Grafikkarte. L2 mit 2,5 cm Zellen (rund 3 000–6 000 Dreiecke), L3 mit 9 cm (einige Hundert).
 */
public final class LodMesh {

    /** Lage (3), Normale (3), Farbe (3) je Ecke; Gelenke (4) und Gewichte (4) je Ecke; Dreiecke. */
    public float[] pos, nrm, col, wt;
    public int[] jn, idx;
    public int vertices, triangles;
    /** Größe der Zelle (m). */
    public double cell;

    /**
     * Netz bauen. {@code body}: schon gemischt ({@link Body#mix}), {@code parts}: Teile mit ihren Dreiecken
     * (z. B. Körper ohne verdeckte), {@code colors}: Farbe je Teil (linear RGB), {@code cell}: Zellgröße (m).
     */
    public static LodMesh build(Body body, List<Body.Part> parts, List<int[]> tris, List<float[]> colors, double cell) {
        LodMesh m = new LodMesh();
        m.cell = cell;
        List<float[]> P = new ArrayList<>(), N = new ArrayList<>(), C = new ArrayList<>(), W = new ArrayList<>();
        List<int[]> J = new ArrayList<>();
        List<int[]> T = new ArrayList<>();
        for (int k = 0; k < parts.size(); k++) {
            Body.Part p = parts.get(k);
            float[] pos = p.position, nrm = p.normal;
            int n = p.prim.vertexCount;
            Map<Long, Integer> cellOf = new HashMap<>();
            int[] map = new int[n];
            List<double[]> acc = new ArrayList<>();          // Summe x y z, nx ny nz, Anzahl
            List<Integer> firstV = new ArrayList<>();
            for (int v = 0; v < n; v++) {
                long key = key((int) Math.floor(pos[v * 3] / cell), (int) Math.floor(pos[v * 3 + 1] / cell), (int) Math.floor(pos[v * 3 + 2] / cell));
                Integer c = cellOf.get(key);
                if (c == null) { c = acc.size(); cellOf.put(key, c); acc.add(new double[7]); firstV.add(v); }
                double[] a = acc.get(c);
                a[0] += pos[v * 3]; a[1] += pos[v * 3 + 1]; a[2] += pos[v * 3 + 2];
                a[3] += nrm[v * 3]; a[4] += nrm[v * 3 + 1]; a[5] += nrm[v * 3 + 2];
                a[6]++;
                map[v] = c;
            }
            // Ecke je Zelle: Mittel; Gewichte der Ecke am nächsten zum Mittel
            int base = P.size();
            int[] best = new int[acc.size()];
            double[] bd = new double[acc.size()];
            java.util.Arrays.fill(bd, Double.MAX_VALUE);
            for (int v = 0; v < n; v++) {
                double[] a = acc.get(map[v]);
                double dx = pos[v * 3] - a[0] / a[6], dy = pos[v * 3 + 1] - a[1] / a[6], dz = pos[v * 3 + 2] - a[2] / a[6];
                double d = dx * dx + dy * dy + dz * dz;
                if (d < bd[map[v]]) { bd[map[v]] = d; best[map[v]] = v; }
            }
            float[] colr = colors.get(k);
            for (int c = 0; c < acc.size(); c++) {
                double[] a = acc.get(c);
                P.add(new float[]{(float) (a[0] / a[6]), (float) (a[1] / a[6]), (float) (a[2] / a[6])});
                double l = Math.sqrt(a[3] * a[3] + a[4] * a[4] + a[5] * a[5]);
                N.add(l < 1e-12 ? new float[]{0, 1, 0} : new float[]{(float) (a[3] / l), (float) (a[4] / l), (float) (a[5] / l)});
                C.add(colr);
                int v = best[c];
                int[] jj = new int[4];
                float[] ww = new float[4];
                for (int q = 0; q < 4; q++) {
                    jj[q] = p.prim.joints != null ? p.prim.joints[v * 4 + q] : 0;
                    ww[q] = p.prim.weights != null ? p.prim.weights[v * 4 + q] : (q == 0 ? 1 : 0);
                }
                J.add(jj);
                W.add(ww);
            }
            int[] ix = tris.get(k);
            java.util.HashSet<Long> seen = new java.util.HashSet<>();
            for (int t = 0; t + 2 < ix.length; t += 3) {
                int a = map[ix[t]], b = map[ix[t + 1]], c = map[ix[t + 2]];
                if (a == b || b == c || a == c) continue;
                int lo = Math.min(a, Math.min(b, c)), hi = Math.max(a, Math.max(b, c)), mid = a + b + c - lo - hi;
                long key = ((long) lo << 42) | ((long) mid << 21) | hi;
                if (!seen.add(key)) continue;
                T.add(new int[]{base + a, base + b, base + c});
            }
        }
        int nv = P.size();
        m.vertices = nv;
        m.triangles = T.size();
        m.pos = new float[nv * 3]; m.nrm = new float[nv * 3]; m.col = new float[nv * 3]; m.wt = new float[nv * 4]; m.jn = new int[nv * 4];
        for (int i = 0; i < nv; i++) {
            System.arraycopy(P.get(i), 0, m.pos, i * 3, 3);
            System.arraycopy(N.get(i), 0, m.nrm, i * 3, 3);
            System.arraycopy(C.get(i), 0, m.col, i * 3, 3);
            System.arraycopy(W.get(i), 0, m.wt, i * 4, 4);
            System.arraycopy(J.get(i), 0, m.jn, i * 4, 4);
        }
        m.idx = new int[T.size() * 3];
        for (int t = 0; t < T.size(); t++) System.arraycopy(T.get(t), 0, m.idx, t * 3, 3);
        return m;
    }

    private static long key(int x, int y, int z) { return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF); }

    /** Haarfarbe aus Eumelanin und Rotanteil (vereinfacht wie {@code com.dan.hair.HairColor}). */
    public static float[] hairColor(double eu, double red) {
        double t = Math.pow(Math.max(0, Math.min(1, eu)), 0.6);
        double r = 0.62 + (0.025 - 0.62) * t, g = 0.48 + (0.018 - 0.48) * t, b = 0.30 + (0.012 - 0.30) * t;
        r += 0.2 * red * (1 - t); g += 0.03 * red * (1 - t);
        return new float[]{(float) r, (float) g, (float) b};
    }

    @Override public String toString() { return String.format(java.util.Locale.GERMANY, "%,d Ecken · %,d Dreiecke · Zelle %.1f cm", vertices, triangles, cell * 100); }
}
