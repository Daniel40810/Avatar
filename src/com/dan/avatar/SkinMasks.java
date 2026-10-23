package com.dan.avatar;

import com.dan.gltf.Gltf;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.IntStream;

/**
 * Zonen der Haut je Ecke des Körpers (Phase 4), reines Java, aus der Grundform gerechnet und
 * deshalb für jede Person gültig (die Ecken wandern mit den Morphs). Je Ecke acht Werte:
 * <ul>
 *   <li>A: Dicke (m, nach innen gemessen), Bart, Rötung, Sommersprossen</li>
 *   <li>B: Fett (T-Zone), Falten, Lippen, Ohr</li>
 * </ul>
 * Die Zonen hängen an Landmarken aus der Grundform: Augenmitten (Augennetz), Mund (Zähne),
 * Nasenspitze, Kinn, Ohren. Die Dicke ist der kürzeste von fünf Strahlen nach innen gegen den
 * Körper (für die Durchleuchtung von Ohren und Nasenflügeln, F8).
 */
public final class SkinMasks {

    /** Acht Werte je Körperecke (siehe oben). */
    public final float[] data;
    /**
     * Vier weitere Werte je Körperecke (Phase 5): Lidrand (nass, Tränenlinie), Mundinneres (Tiefe 0…1
     * hinter den Lippen), frei, frei.
     */
    public final float[] extra;
    public final int vertexCount;
    /** Landmarken in Grundform-Koordinaten (glTF-Achsen, Meter). */
    public final double[] eyeL, eyeR, mouth, noseTip, chin, earL, earR;
    /** Dauer der Rechnung in ms. */
    public final double ms;

    private static final Map<Gltf, SkinMasks> CACHE = new WeakHashMap<>();

    /** Zonen für den Körper dieses Modells (einmal je Modell gerechnet). */
    public static SkinMasks of(Body body) {
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(body.model, m -> new SkinMasks(body));
        }
    }

    private SkinMasks(Body body) {
        long t0 = System.nanoTime();
        Body.Part b = body.body();
        Gltf.Primitive pr = b.prim;
        int n = pr.vertexCount;
        vertexCount = n;
        float[] P = pr.position, N = pr.normal;
        // ---------------- Landmarken
        double[][] eyes = eyeCenters(body);
        eyeL = eyes[0]; eyeR = eyes[1];
        double eyeY = (eyeL[1] + eyeR[1]) / 2, eyeZ = (eyeL[2] + eyeR[2]) / 2, eyeDX = Math.abs(eyeL[0] - eyeR[0]) / 2;
        double[] teeth = centroid(body, "teeth");
        // Nasenspitze: vorderste Ecke nahe der Mitte zwischen Augen und Mund
        double[] nt = null;
        for (int i = 0; i < n; i++) {
            double x = P[i * 3], y = P[i * 3 + 1], z = P[i * 3 + 2];
            if (Math.abs(x) < 0.006 && y < eyeY - 0.01 && y > teeth[1] + 0.01 && (nt == null || z > nt[2])) nt = new double[]{x, y, z};
        }
        noseTip = nt;
        // Mund: vorderste Ecke in Mundhöhe (Lippen)
        double[] mo = null;
        for (int i = 0; i < n; i++) {
            double x = P[i * 3], y = P[i * 3 + 1], z = P[i * 3 + 2];
            if (Math.abs(x) < 0.006 && Math.abs(y - teeth[1]) < 0.015 && (mo == null || z > mo[2])) mo = new double[]{x, y, z};
        }
        mouth = mo;
        // Kinn: vorderste Ecke unter dem Mund (Kinnspitze), dann die tiefste davor
        double[] ch = null;
        for (int i = 0; i < n; i++) {
            double x = P[i * 3], y = P[i * 3 + 1], z = P[i * 3 + 2];
            if (Math.abs(x) < 0.006 && y < mo[1] - 0.02 && y > mo[1] - 0.09 && z > mo[2] - 0.05 && (ch == null || y < ch[1])) ch = new double[]{x, y, z};
        }
        chin = ch;
        // Ohren: äußerste Ecken in Augenhöhe hinter den Augen
        double[] el = null, er = null;
        for (int i = 0; i < n; i++) {
            double x = P[i * 3], y = P[i * 3 + 1], z = P[i * 3 + 2];
            if (Math.abs(y - (eyeY - 0.012)) > 0.03 || z > eyeZ - 0.05 || z < eyeZ - 0.16) continue;
            if (x > 0 && (el == null || x > el[0])) el = new double[]{x, y, z};
            if (x < 0 && (er == null || x < er[0])) er = new double[]{x, y, z};
        }
        earL = el; earR = er;
        double earX = (el[0] - er[0]) / 2, earZ = (el[2] + er[2]) / 2;

        // ---------------- Zonen
        data = new float[n * 8];
        double noseY = noseTip[1], mouthY = mouth[1], chinY = chin[1];
        double noseBase = noseY - 0.012;
        for (int i = 0; i < n; i++) {
            double x = P[i * 3], y = P[i * 3 + 1], z = P[i * 3 + 2], ax = Math.abs(x);
            boolean head = y > chinY - 0.07 && z > earZ - 0.13 && y < eyeY + 0.16;
            // Ohr: nahe an einer Ohrlandmarke
            // Ohr: die Muschel steht seitlich ab (außen am Kopf, um die Ohrlandmarke)
            double earY = (el[1] + er[1]) / 2;
            double ear = head ? smooth(earX - 0.02, earX - 0.012, ax) * (1 - smooth(0.028, 0.04, Math.abs(y - earY + 0.008)))
                    * (1 - smooth(0.02, 0.032, Math.abs(z - earZ + 0.004))) : 0;
            // Lippen: Ellipse um die Mundmitte, vorn
            double lx = x / 0.026, ly = (y - mouthY) / 0.0095;
            double lip = z > mouth[2] - 0.02 ? 1 - smooth(0.7, 1.05, Math.sqrt(lx * lx + ly * ly)) : 0;
            // Bart: unterhalb der Linie Nasenbasis → Koteletten, vor den Ohren, bis unters Kinn
            double top = noseBase + (eyeY - 0.02 - noseBase) * smooth(0.02, earX - 0.012, ax);
            double beard = 0;
            if (head && y > chinY - 0.065) {
                double up = 1 - smooth(top - 0.006, top + 0.006, y);
                double front = smooth(earZ - 0.005, earZ + 0.012, z);          // vor dem Ohr
                // Unterkante: unter dem Kinn 4 cm tief, zum Ohrläppchen hin ansteigend (Kieferlinie)
                double tj = smooth(earZ, chin[2] - 0.02, z);
                double yLow = (earY - 0.045) * (1 - tj) + (chinY - 0.04) * tj;
                double neck = smooth(yLow - 0.008, yLow + 0.008, y);
                double side = 1 - smooth(earX - 0.015, earX + 0.002, ax);
                beard = up * front * neck * side * (1 - lip);
                // Nase und Nasenlöcher frei
                beard *= 1 - (1 - smooth(0.012, 0.02, ax)) * smooth(mouthY + 0.008, mouthY + 0.016, y);
            }
            // Rötung: Wangen, Nase, Ohren, Lippen
            double cheek = Math.exp(-(sq(ax - eyeDX * 1.15) + sq(y - (noseY + 0.004))) / (2 * sq(0.02))) * smooth(eyeZ - 0.05, eyeZ - 0.02, z);
            double nose = Math.exp(-dist2(x, y, z, noseTip) / (2 * sq(0.014)));
            double red = Math.max(Math.max(cheek, nose * 0.9), Math.max(ear * 0.8, lip * 0.3));
            // Sommersprossen: Nase und Wangen stark, Gesicht mittel, Schultern und Arme
            double faceFront = head && z > eyeZ - 0.06 ? 1 : 0;
            double fr = faceFront * Math.max(0.35, Math.max(nose, Math.exp(-(sq(ax - eyeDX) + sq(y - noseY - 0.008)) / (2 * sq(0.028)))));
            double shoulderY = chinY - 0.16;
            double sh = !head && y > shoulderY - 0.2 && y < shoulderY + 0.06 ? 0.6 : 0;
            double arm = !head && ax > 0.2 && y > 0.7 ? 0.45 : 0;
            double freck = Math.max(Math.max(fr, sh), Math.max(arm, 0.08)) * (1 - lip);
            // T-Zone: Stirnband in der Mitte und Nase, Kinn etwas
            double tz = faceFront * Math.max(Math.max(nose, (1 - smooth(0.02, 0.04, ax)) * band(y, eyeY + 0.015, eyeY + 0.075, 0.01)),
                    0.6 * Math.exp(-dist2(x, y, z, chin) / (2 * sq(0.018))));
            // Falten: Stirn, Krähenfüße, Nasolabial, Hals
            double forehead = faceFront * band(y, eyeY + 0.025, eyeY + 0.08, 0.012) * (1 - smooth(0.045, 0.06, ax));
            double crow = faceFront * Math.exp(-(sq(ax - (eyeDX + 0.028)) + sq(y - eyeY)) / (2 * sq(0.009)));
            double naso = faceFront * Math.exp(-(sq(ax - 0.022) + sq(y - (mouthY + 0.012))) / (2 * sq(0.008)));
            double neckW = !head && y > chinY - 0.13 && y < chinY - 0.04 && z > earZ - 0.03 ? 0.6 : 0;
            double wr = Math.min(1, Math.max(Math.max(forehead, crow), Math.max(naso, neckW)));
            int o = i * 8;
            data[o + 1] = (float) beard;
            data[o + 2] = (float) red;
            data[o + 3] = (float) freck;
            data[o + 4] = (float) tz;
            data[o + 5] = (float) wr;
            data[o + 6] = (float) lip;
            data[o + 7] = (float) ear;
        }
        float[] th = thickness(pr, P, N);
        for (int i = 0; i < n; i++) data[i * 8] = th[i];
        // ---------------- Lidrand und Mundinneres (Phase 5)
        extra = new float[n * 4];
        // Lidrand: Haut, die den Augapfel berührt (Abstand zur Kugel des Auges, vordere Hälfte)
        EyeRig rig = new EyeRig(body);
        int rimCount = 0;
        for (int i = 0; i < n; i++) {
            double x = P[i * 3], y = P[i * 3 + 1], z = P[i * 3 + 2];
            if (rig.valid())
                for (int e = 0; e < 2; e++) {
                    double[] c = rig.c0[e];
                    double ds = dist(x, y, z, c) - rig.r0[e];
                    double fw = (x - c[0]) * rig.az0[e][0] + (y - c[1]) * rig.az0[e][1] + (z - c[2]) * rig.az0[e][2];
                    if (fw > 0 && ds < 0.004) {
                        float wv = (float) (1 - smooth(0.0008, 0.0035, ds));
                        if (wv > extra[i * 4]) extra[i * 4] = wv;
                        if (ds < 0.0012) rimCount++;
                    }
                }
            // Mundinneres: hinter der Lippenfront, in der Öffnung zwischen den Lippen (Ellipse) und nicht nach
            // vorn gewandt; sonst zählen Oberlippe und Kinn seitlich mit (liegen auch hinter der Lippenfront)
            double mx = x / 0.027, my = (y - mouthY) / 0.011;
            double depth = mouth[2] - 0.004 - z;
            boolean opening = mx * mx + my * my < 1
                    || (depth > 0.012 && Math.abs(x) < 0.034 && Math.abs(y - mouthY) < 0.026);   // tiefer: Mundhöhle
            double nz = N != null ? N[i * 3 + 2] : 0;
            if (opening && nz < 0.55 && z < mouth[2] - 0.004 && z > mouth[2] - 0.09)
                extra[i * 4 + 1] = (float) (smooth(0.0, 0.03, mouth[2] - 0.004 - z) * (1 - smooth(0.25, 0.55, nz)));
        }
        lidMarginCount = rimCount;
        ms = (System.nanoTime() - t0) / 1e6;
    }

    /** Zahl der Ecken am Lidrand (Haut höchstens 1,2 mm vor dem Augapfel). */
    public int lidMarginCount;

    // ------------------------------------------------------------ Dicke

    /**
     * Dicke je Ecke: kürzester von fünf Strahlen nach innen (−N und vier Neigungen um 25°), höchstens
     * 0,25 m. Am Ohr findet einer den Weg durch die Muschel; an der Wange gehen alle in den Kopf.
     */
    static float[] thickness(Gltf.Primitive pr, float[] P, float[] N) {
        int n = pr.vertexCount;
        int[] idx = pr.indices;
        Grid g = new Grid(P, idx, 0.02);
        float[] out = new float[n];
        IntStream.range(0, n).parallel().forEach(i -> {
            double nx = -N[i * 3], ny = -N[i * 3 + 1], nz = -N[i * 3 + 2];
            double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (l < 1e-9) { out[i] = 0.25f; return; }
            nx /= l; ny /= l; nz /= l;
            // zwei Achsen senkrecht dazu
            double ax = Math.abs(nx) < 0.9 ? 1 : 0, ay = ax == 1 ? 0 : 1;
            double ux = ay * nz - 0 * ny, uy = 0 * nx - ax * nz, uz = ax * ny - ay * nx;
            double ul = Math.sqrt(ux * ux + uy * uy + uz * uz); ux /= ul; uy /= ul; uz /= ul;
            double vx = ny * uz - nz * uy, vy = nz * ux - nx * uz, vz = nx * uy - ny * ux;
            double ox = P[i * 3] + nx * 2e-4, oy = P[i * 3 + 1] + ny * 2e-4, oz = P[i * 3 + 2] + nz * 2e-4;
            double best = 0.25;
            double tilt = Math.tan(Math.toRadians(25));
            double[][] dirs = {{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (double[] d : dirs) {
                double dx = nx + tilt * (d[0] * ux + d[1] * vx), dy = ny + tilt * (d[0] * uy + d[1] * vy), dz = nz + tilt * (d[0] * uz + d[1] * vz);
                double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                best = Math.min(best, g.hit(ox, oy, oz, dx / dl, dy / dl, dz / dl, best, i));
            }
            out[i] = (float) best;
        });
        return out;
    }

    /** Gleichmäßiges Gitter über die Dreiecke für die Strahlen. */
    static final class Grid {
        final float[] P;
        final int[] idx;
        final double cell, x0, y0, z0;
        final int nx, ny, nz;
        final int[] start, tris;

        Grid(float[] P, int[] idx, double cell) {
            this.P = P; this.idx = idx; this.cell = cell;
            double[] lo = {1e9, 1e9, 1e9}, hi = {-1e9, -1e9, -1e9};
            for (int i = 0; i < P.length; i++) { lo[i % 3] = Math.min(lo[i % 3], P[i]); hi[i % 3] = Math.max(hi[i % 3], P[i]); }
            x0 = lo[0] - cell; y0 = lo[1] - cell; z0 = lo[2] - cell;
            nx = (int) Math.ceil((hi[0] - x0) / cell) + 2; ny = (int) Math.ceil((hi[1] - y0) / cell) + 2; nz = (int) Math.ceil((hi[2] - z0) / cell) + 2;
            int cells = nx * ny * nz;
            int[] cnt = new int[cells + 1];
            int tcount = idx.length / 3;
            int[][] range = new int[tcount][];
            for (int t = 0; t < tcount; t++) {
                double[] a = {1e9, 1e9, 1e9}, b = {-1e9, -1e9, -1e9};
                for (int c = 0; c < 3; c++) for (int k = 0; k < 3; k++) {
                    double v = P[idx[t * 3 + c] * 3 + k];
                    a[k] = Math.min(a[k], v); b[k] = Math.max(b[k], v);
                }
                int[] r = {ci(a[0], x0), ci(b[0], x0), ci(a[1], y0), ci(b[1], y0), ci(a[2], z0), ci(b[2], z0)};
                range[t] = r;
                for (int z = r[4]; z <= r[5]; z++) for (int y = r[2]; y <= r[3]; y++) for (int x = r[0]; x <= r[1]; x++) cnt[(z * ny + y) * nx + x + 1]++;
            }
            for (int i = 0; i < cells; i++) cnt[i + 1] += cnt[i];
            start = cnt;
            tris = new int[cnt[cells]];
            int[] fill = java.util.Arrays.copyOf(cnt, cells);
            for (int t = 0; t < tcount; t++) {
                int[] r = range[t];
                for (int z = r[4]; z <= r[5]; z++) for (int y = r[2]; y <= r[3]; y++) for (int x = r[0]; x <= r[1]; x++) tris[fill[(z * ny + y) * nx + x]++] = t;
            }
        }

        private int ci(double v, double o) { return (int) Math.floor((v - o) / cell); }

        /** Abstand bis zum ersten Treffer (ohne Dreiecke an Ecke {@code skip}), sonst {@code max}. */
        double hit(double ox, double oy, double oz, double dx, double dy, double dz, double max, int skip) {
            double best = max;
            int cx = ci(ox, x0), cy = ci(oy, y0), cz = ci(oz, z0);
            int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
            double tdx = Math.abs(cell / dx), tdy = Math.abs(cell / dy), tdz = Math.abs(cell / dz);
            double nxb = x0 + (cx + (sx > 0 ? 1 : 0)) * cell, nyb = y0 + (cy + (sy > 0 ? 1 : 0)) * cell, nzb = z0 + (cz + (sz > 0 ? 1 : 0)) * cell;
            double tmx = dx != 0 ? (nxb - ox) / dx : 1e30, tmy = dy != 0 ? (nyb - oy) / dy : 1e30, tmz = dz != 0 ? (nzb - oz) / dz : 1e30;
            double t = 0;
            while (t < best) {
                if (cx < 0 || cy < 0 || cz < 0 || cx >= nx || cy >= ny || cz >= nz) break;
                int c = (cz * ny + cy) * nx + cx;
                for (int k = start[c]; k < start[c + 1]; k++) {
                    int tr = tris[k];
                    int a = idx[tr * 3], b = idx[tr * 3 + 1], e = idx[tr * 3 + 2];
                    if (a == skip || b == skip || e == skip) continue;
                    double h = tri(ox, oy, oz, dx, dy, dz, a, b, e);
                    if (h > 1e-5 && h < best) best = h;
                }
                if (tmx < tmy && tmx < tmz) { t = tmx; tmx += tdx; cx += sx; }
                else if (tmy < tmz) { t = tmy; tmy += tdy; cy += sy; }
                else { t = tmz; tmz += tdz; cz += sz; }
            }
            return best;
        }

        /** Möller–Trumbore, beidseitig. */
        private double tri(double ox, double oy, double oz, double dx, double dy, double dz, int a, int b, int c) {
            double ax = P[a * 3], ay = P[a * 3 + 1], az = P[a * 3 + 2];
            double e1x = P[b * 3] - ax, e1y = P[b * 3 + 1] - ay, e1z = P[b * 3 + 2] - az;
            double e2x = P[c * 3] - ax, e2y = P[c * 3 + 1] - ay, e2z = P[c * 3 + 2] - az;
            double px = dy * e2z - dz * e2y, py = dz * e2x - dx * e2z, pz = dx * e2y - dy * e2x;
            double det = e1x * px + e1y * py + e1z * pz;
            if (Math.abs(det) < 1e-14) return -1;
            double inv = 1 / det;
            double tx = ox - ax, ty = oy - ay, tz = oz - az;
            double u = (tx * px + ty * py + tz * pz) * inv;
            if (u < 0 || u > 1) return -1;
            double qx = ty * e1z - tz * e1y, qy = tz * e1x - tx * e1z, qz = tx * e1y - ty * e1x;
            double v = (dx * qx + dy * qy + dz * qz) * inv;
            if (v < 0 || u + v > 1) return -1;
            return (e2x * qx + e2y * qy + e2z * qz) * inv;
        }
    }

    // ------------------------------------------------------------ Hilfen

    private static double[][] eyeCenters(Body body) {
        double[] l = new double[4], r = new double[4];
        for (Body.Part p : body.parts) {
            String nm = p.mesh.name.toLowerCase();
            if (!(nm.contains("high-poly") || nm.contains("low-poly") || nm.equals("eyes"))) continue;
            float[] P = p.prim.position;
            for (int i = 0; i < p.prim.vertexCount; i++) {
                double[] s = P[i * 3] > 0 ? l : r;
                s[0] += P[i * 3]; s[1] += P[i * 3 + 1]; s[2] += P[i * 3 + 2]; s[3]++;
            }
        }
        if (l[3] == 0 || r[3] == 0) return new double[][]{{0.032, 1.6, 0.07}, {-0.032, 1.6, 0.07}};
        return new double[][]{{l[0] / l[3], l[1] / l[3], l[2] / l[3]}, {r[0] / r[3], r[1] / r[3], r[2] / r[3]}};
    }

    private static double[] centroid(Body body, String name) {
        double[] s = new double[4];
        for (Body.Part p : body.parts) {
            if (!p.mesh.name.toLowerCase().contains(name)) continue;
            float[] P = p.prim.position;
            for (int i = 0; i < p.prim.vertexCount; i++) { s[0] += P[i * 3]; s[1] += P[i * 3 + 1]; s[2] += P[i * 3 + 2]; s[3]++; }
        }
        return s[3] == 0 ? new double[]{0, 1.5, 0.08} : new double[]{s[0] / s[3], s[1] / s[3], s[2] / s[3]};
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** 1 innerhalb [lo, hi], weiche Ränder der Breite {@code soft}. */
    static double band(double x, double lo, double hi, double soft) {
        return smooth(lo - soft, lo + soft, x) * (1 - smooth(hi - soft, hi + soft, x));
    }

    static double sq(double v) { return v * v; }

    static double dist2(double x, double y, double z, double[] p) { return sq(x - p[0]) + sq(y - p[1]) + sq(z - p[2]); }

    static double dist(double x, double y, double z, double[] p) { return Math.sqrt(dist2(x, y, z, p)); }

    /** Mittel, kleinster und größter Wert eines Kanals (0…7) über alle Ecken. */
    public double[] stats(int channel) {
        double s = 0, lo = 1e9, hi = -1e9;
        for (int i = 0; i < vertexCount; i++) { double v = data[i * 8 + channel]; s += v; lo = Math.min(lo, v); hi = Math.max(hi, v); }
        return new double[]{s / vertexCount, lo, hi};
    }
}
