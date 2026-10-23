package com.dan.avatar;

/**
 * Dual-Quaternion einer starren Transformation (Kavan u. a., „Skinning with Dual Quaternions“, 2007).
 * Gespeichert als 8 floats: Realteil (x, y, z, w), Dualteil (x, y, z, w) – so liegen sie auch auf der Grafikkarte.
 * Blenden wie Blender (Volumenerhalt): Vorzeichen nach dem Skalarprodukt mit der laufenden Summe,
 * dann durch die Länge des Realteils teilen.
 */
public final class Dq {

    private Dq() {}

    /** Aus einer starren 4×4-Matrix (spaltenweise). */
    static void fromMatrix(double[] m, float[] out, int o) {
        double m00 = m[0], m01 = m[4], m02 = m[8], m10 = m[1], m11 = m[5], m12 = m[9], m20 = m[2], m21 = m[6], m22 = m[10];
        double tr = m00 + m11 + m22, qx, qy, qz, qw;
        if (tr > 0) {
            double s = Math.sqrt(tr + 1.0) * 2;
            qw = 0.25 * s; qx = (m21 - m12) / s; qy = (m02 - m20) / s; qz = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            double s = Math.sqrt(1.0 + m00 - m11 - m22) * 2;
            qw = (m21 - m12) / s; qx = 0.25 * s; qy = (m01 + m10) / s; qz = (m02 + m20) / s;
        } else if (m11 > m22) {
            double s = Math.sqrt(1.0 + m11 - m00 - m22) * 2;
            qw = (m02 - m20) / s; qx = (m01 + m10) / s; qy = 0.25 * s; qz = (m12 + m21) / s;
        } else {
            double s = Math.sqrt(1.0 + m22 - m00 - m11) * 2;
            qw = (m10 - m01) / s; qx = (m02 + m20) / s; qy = (m12 + m21) / s; qz = 0.25 * s;
        }
        double l = Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
        qx /= l; qy /= l; qz /= l; qw /= l;
        double tx = m[12], ty = m[13], tz = m[14];
        // Dualteil = 0,5 · (t, 0) · q
        double dw = -0.5 * (tx * qx + ty * qy + tz * qz);
        double dx = 0.5 * (tx * qw + ty * qz - tz * qy);
        double dy = 0.5 * (-tx * qz + ty * qw + tz * qx);
        double dz = 0.5 * (tx * qy - ty * qx + tz * qw);
        out[o] = (float) qx; out[o + 1] = (float) qy; out[o + 2] = (float) qz; out[o + 3] = (float) qw;
        out[o + 4] = (float) dx; out[o + 5] = (float) dy; out[o + 6] = (float) dz; out[o + 7] = (float) dw;
    }

    /**
     * Eine Ecke mit bis zu 4 Gelenken verformen (wie skin.comp). {@code dq}: 8 floats je Gelenk.
     * Schreibt Lage und Normale nach {@code outP}/{@code outN} ab Index {@code o}.
     */
    static void skin(float[] dq, int[] joints, float[] weights, int v, float px, float py, float pz,
                     float nx, float ny, float nz, float[] outP, float[] outN, int o) {
        float rx = 0, ry = 0, rz = 0, rw = 0, ex = 0, ey = 0, ez = 0, ew = 0;
        for (int k = 0; k < 4; k++) {
            float w = weights[v * 4 + k];
            if (w == 0) continue;
            int j = joints[v * 4 + k] * 8;
            float qx = dq[j], qy = dq[j + 1], qz = dq[j + 2], qw = dq[j + 3];
            if (rx * qx + ry * qy + rz * qz + rw * qw < 0) w = -w;
            rx += w * qx; ry += w * qy; rz += w * qz; rw += w * qw;
            ex += w * dq[j + 4]; ey += w * dq[j + 5]; ez += w * dq[j + 6]; ew += w * dq[j + 7];
        }
        float l = (float) Math.sqrt(rx * rx + ry * ry + rz * rz + rw * rw);
        if (l < 1e-12f) { rw = 1; l = 1; }
        rx /= l; ry /= l; rz /= l; rw /= l; ex /= l; ey /= l; ez /= l; ew /= l;
        // Lage: q p q* + 2 (w_r d_v − w_d r_v + r_v × d_v)
        float[] p = rot(rx, ry, rz, rw, px, py, pz);
        float tx = 2 * (rw * ex - ew * rx + (ry * ez - rz * ey));
        float ty = 2 * (rw * ey - ew * ry + (rz * ex - rx * ez));
        float tz = 2 * (rw * ez - ew * rz + (rx * ey - ry * ex));
        outP[o] = p[0] + tx; outP[o + 1] = p[1] + ty; outP[o + 2] = p[2] + tz;
        float[] n = rot(rx, ry, rz, rw, nx, ny, nz);
        outN[o] = n[0]; outN[o + 1] = n[1]; outN[o + 2] = n[2];
    }

    /** v + 2 r × (r × v + w v) */
    static float[] rot(float x, float y, float z, float w, float vx, float vy, float vz) {
        float cx = y * vz - z * vy + w * vx, cy = z * vx - x * vz + w * vy, cz = x * vy - y * vx + w * vz;
        return new float[]{vx + 2 * (y * cz - z * cy), vy + 2 * (z * cx - x * cz), vz + 2 * (x * cy - y * cx)};
    }

    /** Lineares Skinning (zum Vergleich): Summe der Gewichte mal Matrizen (12 floats je Gelenk, spaltenweise 3×4). */
    static void linear(float[] m, int[] joints, float[] weights, int v, float px, float py, float pz,
                       float nx, float ny, float nz, float[] outP, float[] outN, int o) {
        float x = 0, y = 0, z = 0, a = 0, b = 0, c = 0;
        for (int k = 0; k < 4; k++) {
            float w = weights[v * 4 + k];
            if (w == 0) continue;
            int j = joints[v * 4 + k] * 12;
            x += w * (m[j] * px + m[j + 3] * py + m[j + 6] * pz + m[j + 9]);
            y += w * (m[j + 1] * px + m[j + 4] * py + m[j + 7] * pz + m[j + 10]);
            z += w * (m[j + 2] * px + m[j + 5] * py + m[j + 8] * pz + m[j + 11]);
            a += w * (m[j] * nx + m[j + 3] * ny + m[j + 6] * nz);
            b += w * (m[j + 1] * nx + m[j + 4] * ny + m[j + 7] * nz);
            c += w * (m[j + 2] * nx + m[j + 5] * ny + m[j + 8] * nz);
        }
        float l = (float) Math.sqrt(a * a + b * b + c * c);
        if (l > 0) { a /= l; b /= l; c /= l; }
        outP[o] = x; outP[o + 1] = y; outP[o + 2] = z;
        outN[o] = a; outN[o + 1] = b; outN[o + 2] = c;
    }
}
