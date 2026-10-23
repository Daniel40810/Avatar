package com.dan.avatar;

/**
 * 4×4-Matrizen spaltenweise (wie OpenGL und glTF), in double für das Rig.
 * Starre Transformationen (Drehung + Verschiebung) sind der Normalfall.
 */
final class M4 {

    private M4() {}

    static double[] identity() {
        return new double[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
    }

    static double[] of(float[] m) {
        double[] r = new double[16];
        for (int i = 0; i < 16; i++) r[i] = m[i];
        return r;
    }

    static double[] mul(double[] a, double[] b) {
        double[] r = new double[16];
        for (int c = 0; c < 4; c++)
            for (int rr = 0; rr < 4; rr++) {
                double s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + rr] * b[c * 4 + k];
                r[c * 4 + rr] = s;
            }
        return r;
    }

    /** Inverse einer starren Matrix (Drehung orthonormal): R^T, −R^T t. */
    static double[] invRigid(double[] m) {
        double[] r = new double[16];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) r[j * 4 + i] = m[i * 4 + j];
        for (int i = 0; i < 3; i++)
            r[12 + i] = -(r[i] * m[12] + r[4 + i] * m[13] + r[8 + i] * m[14]);
        r[15] = 1;
        return r;
    }

    /** Allgemeine Inverse (für inverse Bindematrizen mit kleinen Rundungsfehlern). */
    static double[] invert(double[] a) {
        double[] inv = new double[16];
        inv[0] = a[5]*a[10]*a[15]-a[5]*a[11]*a[14]-a[9]*a[6]*a[15]+a[9]*a[7]*a[14]+a[13]*a[6]*a[11]-a[13]*a[7]*a[10];
        inv[4] = -a[4]*a[10]*a[15]+a[4]*a[11]*a[14]+a[8]*a[6]*a[15]-a[8]*a[7]*a[14]-a[12]*a[6]*a[11]+a[12]*a[7]*a[10];
        inv[8] = a[4]*a[9]*a[15]-a[4]*a[11]*a[13]-a[8]*a[5]*a[15]+a[8]*a[7]*a[13]+a[12]*a[5]*a[11]-a[12]*a[7]*a[9];
        inv[12] = -a[4]*a[9]*a[14]+a[4]*a[10]*a[13]+a[8]*a[5]*a[14]-a[8]*a[6]*a[13]-a[12]*a[5]*a[10]+a[12]*a[6]*a[9];
        inv[1] = -a[1]*a[10]*a[15]+a[1]*a[11]*a[14]+a[9]*a[2]*a[15]-a[9]*a[3]*a[14]-a[13]*a[2]*a[11]+a[13]*a[3]*a[10];
        inv[5] = a[0]*a[10]*a[15]-a[0]*a[11]*a[14]-a[8]*a[2]*a[15]+a[8]*a[3]*a[14]+a[12]*a[2]*a[11]-a[12]*a[3]*a[10];
        inv[9] = -a[0]*a[9]*a[15]+a[0]*a[11]*a[13]+a[8]*a[1]*a[15]-a[8]*a[3]*a[13]-a[12]*a[1]*a[11]+a[12]*a[3]*a[9];
        inv[13] = a[0]*a[9]*a[14]-a[0]*a[10]*a[13]-a[8]*a[1]*a[14]+a[8]*a[2]*a[13]+a[12]*a[1]*a[10]-a[12]*a[2]*a[9];
        inv[2] = a[1]*a[6]*a[15]-a[1]*a[7]*a[14]-a[5]*a[2]*a[15]+a[5]*a[3]*a[14]+a[13]*a[2]*a[7]-a[13]*a[3]*a[6];
        inv[6] = -a[0]*a[6]*a[15]+a[0]*a[7]*a[14]+a[4]*a[2]*a[15]-a[4]*a[3]*a[14]-a[12]*a[2]*a[7]+a[12]*a[3]*a[6];
        inv[10] = a[0]*a[5]*a[15]-a[0]*a[7]*a[13]-a[4]*a[1]*a[15]+a[4]*a[3]*a[13]+a[12]*a[1]*a[7]-a[12]*a[3]*a[5];
        inv[14] = -a[0]*a[5]*a[14]+a[0]*a[6]*a[13]+a[4]*a[1]*a[14]-a[4]*a[2]*a[13]-a[12]*a[1]*a[6]+a[12]*a[2]*a[5];
        inv[3] = -a[1]*a[6]*a[11]+a[1]*a[7]*a[10]+a[5]*a[2]*a[11]-a[5]*a[3]*a[10]-a[9]*a[2]*a[7]+a[9]*a[3]*a[6];
        inv[7] = a[0]*a[6]*a[11]-a[0]*a[7]*a[10]-a[4]*a[2]*a[11]+a[4]*a[3]*a[10]+a[8]*a[2]*a[7]-a[8]*a[3]*a[6];
        inv[11] = -a[0]*a[5]*a[11]+a[0]*a[7]*a[9]+a[4]*a[1]*a[11]-a[4]*a[3]*a[9]-a[8]*a[1]*a[7]+a[8]*a[3]*a[5];
        inv[15] = a[0]*a[5]*a[10]-a[0]*a[6]*a[9]-a[4]*a[1]*a[10]+a[4]*a[2]*a[9]+a[8]*a[1]*a[6]-a[8]*a[2]*a[5];
        double det = a[0] * inv[0] + a[1] * inv[4] + a[2] * inv[8] + a[3] * inv[12];
        for (int i = 0; i < 16; i++) inv[i] /= det;
        return inv;
    }

    static double[] translation(double x, double y, double z) {
        double[] m = identity();
        m[12] = x; m[13] = y; m[14] = z;
        return m;
    }

    /** Drehung um eine Einheitsachse (Rodrigues) als 4×4. */
    static double[] rotation(double ax, double ay, double az, double ang) {
        double c = Math.cos(ang), s = Math.sin(ang), t = 1 - c;
        return new double[]{
            t * ax * ax + c, t * ax * ay + s * az, t * ax * az - s * ay, 0,
            t * ax * ay - s * az, t * ay * ay + c, t * ay * az + s * ax, 0,
            t * ax * az + s * ay, t * ay * az - s * ax, t * az * az + c, 0,
            0, 0, 0, 1};
    }

    /** Drehung, die Einheitsvektor a auf Einheitsvektor b bringt (kleinste Drehung). */
    static double[] rotationBetween(double[] a, double[] b) {
        double cx = a[1] * b[2] - a[2] * b[1], cy = a[2] * b[0] - a[0] * b[2], cz = a[0] * b[1] - a[1] * b[0];
        double s = Math.sqrt(cx * cx + cy * cy + cz * cz), c = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        if (s < 1e-12) return identity();
        return rotation(cx / s, cy / s, cz / s, Math.atan2(s, c));
    }

    /** Drehung um einen Punkt p: T(p) · R · T(−p). */
    static double[] about(double[] rot, double[] p) {
        return mul(translation(p[0], p[1], p[2]), mul(rot, translation(-p[0], -p[1], -p[2])));
    }

    static double[] pos(double[] m) {
        return new double[]{m[12], m[13], m[14]};
    }

    static double[] point(double[] m, double x, double y, double z) {
        return new double[]{m[0] * x + m[4] * y + m[8] * z + m[12], m[1] * x + m[5] * y + m[9] * z + m[13], m[2] * x + m[6] * y + m[10] * z + m[14]};
    }
}
