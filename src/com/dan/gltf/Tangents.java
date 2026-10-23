package com.dan.gltf;

/**
 * Tangenten je Ecke aus Lage, Normale und UV (nach Lengyel): je Dreieck die Richtungen
 * von u und v, in fester Reihenfolge auf die Ecken summiert, nach Gram-Schmidt zur Normale
 * senkrecht gestellt, Händigkeit in w. Gleiche Eingabe gibt bitgleich dieselben Tangenten.
 */
public final class Tangents {

    private Tangents() {}

    public static float[] compute(float[] pos, float[] nrm, float[] uv, int[] idx) {
        int n = pos.length / 3;
        double[] t = new double[n * 3], b = new double[n * 3];
        for (int k = 0; k + 2 < idx.length; k += 3) {
            int i0 = idx[k], i1 = idx[k + 1], i2 = idx[k + 2];
            double e1x = pos[i1 * 3] - pos[i0 * 3], e1y = pos[i1 * 3 + 1] - pos[i0 * 3 + 1], e1z = pos[i1 * 3 + 2] - pos[i0 * 3 + 2];
            double e2x = pos[i2 * 3] - pos[i0 * 3], e2y = pos[i2 * 3 + 1] - pos[i0 * 3 + 1], e2z = pos[i2 * 3 + 2] - pos[i0 * 3 + 2];
            double du1 = uv[i1 * 2] - uv[i0 * 2], dv1 = uv[i1 * 2 + 1] - uv[i0 * 2 + 1];
            double du2 = uv[i2 * 2] - uv[i0 * 2], dv2 = uv[i2 * 2 + 1] - uv[i0 * 2 + 1];
            double det = du1 * dv2 - du2 * dv1;
            if (Math.abs(det) < 1e-20) continue;
            double r = 1.0 / det;
            double tx = (e1x * dv2 - e2x * dv1) * r, ty = (e1y * dv2 - e2y * dv1) * r, tz = (e1z * dv2 - e2z * dv1) * r;
            double bx = (e2x * du1 - e1x * du2) * r, by = (e2y * du1 - e1y * du2) * r, bz = (e2z * du1 - e1z * du2) * r;
            for (int i : new int[]{i0, i1, i2}) {
                t[i * 3] += tx; t[i * 3 + 1] += ty; t[i * 3 + 2] += tz;
                b[i * 3] += bx; b[i * 3 + 1] += by; b[i * 3 + 2] += bz;
            }
        }
        float[] out = new float[n * 4];
        for (int i = 0; i < n; i++) {
            double nx = nrm[i * 3], ny = nrm[i * 3 + 1], nz = nrm[i * 3 + 2];
            double tx = t[i * 3], ty = t[i * 3 + 1], tz = t[i * 3 + 2];
            double d = nx * tx + ny * ty + nz * tz;
            tx -= nx * d; ty -= ny * d; tz -= nz * d;
            double l = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (l < 1e-12) {
                // beliebige Senkrechte zur Normale
                if (Math.abs(nx) < 0.9) { tx = 0; ty = -nz; tz = ny; } else { tx = nz; ty = 0; tz = -nx; }
                l = Math.sqrt(tx * tx + ty * ty + tz * tz);
            }
            tx /= l; ty /= l; tz /= l;
            double cx = ny * tz - nz * ty, cy = nz * tx - nx * tz, cz = nx * ty - ny * tx;
            double w = (cx * b[i * 3] + cy * b[i * 3 + 1] + cz * b[i * 3 + 2]) < 0 ? -1 : 1;
            out[i * 4] = (float) tx; out[i * 4 + 1] = (float) ty; out[i * 4 + 2] = (float) tz; out[i * 4 + 3] = (float) w;
        }
        return out;
    }
}
