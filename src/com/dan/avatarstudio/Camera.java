package com.dan.avatarstudio;

/**
 * Kreisende Kamera um einen Zielpunkt (Augenhöhe), Matrizen spaltenweise wie in OpenGL.
 * Reversed-Z wird nicht benutzt: die Bühne ist klein (0,1 m bis 60 m).
 */
public final class Camera {

    /** Drehung um die Hochachse in Grad, 0 = von vorn (Blick nach -Z). */
    public double yawDeg = 0;
    /** Neigung in Grad, positiv = von oben. */
    public double pitchDeg = 4;
    /** Abstand zum Ziel in Metern. */
    public double dist = 4.4;
    /** Zielpunkt in Metern (Mitte der Figur). */
    public double tx = 0, ty = 0.92, tz = 0;

    /** Ganze Person von vorn, leicht seitlich. */
    public void reset() {
        yawDeg = 12; pitchDeg = 4; dist = 4.4; tx = 0; ty = 0.92; tz = 0; fovyDeg = 30;
    }
    /** Senkrechter Bildwinkel in Grad. */
    public double fovyDeg = 30;
    public double near = 0.05, far = 80;

    public double[] eye() {
        double y = Math.toRadians(yawDeg), p = Math.toRadians(pitchDeg);
        return new double[]{
            tx + dist * Math.cos(p) * Math.sin(y),
            ty + dist * Math.sin(p),
            tz + dist * Math.cos(p) * Math.cos(y)};
    }

    public float[] view() {
        return lookAt(eye(), new double[]{tx, ty, tz});
    }

    public float[] proj(double aspect) {
        return perspective(fovyDeg, aspect, near, far);
    }

    public void orbit(double dxPx, double dyPx) {
        yawDeg -= dxPx * 0.35;
        pitchDeg = Math.max(-60, Math.min(80, pitchDeg + dyPx * 0.25));
    }

    /**
     * Fliegen mit WASD: Ziel und Kamera verschieben sich gemeinsam. {@code fwd}/{@code right}/{@code up}
     * je −1…+1 (W/S, D/A, E/Q), vorwärts in Blickrichtung auf der Ebene; Tempo wächst mit dem Abstand.
     */
    public void fly(double fwd, double right, double up, double dt, boolean fast) {
        double y = Math.toRadians(yawDeg);
        double fx = -Math.sin(y), fz = -Math.cos(y);          // Blick auf der Ebene
        double rx = Math.cos(y), rz = -Math.sin(y);           // rechts
        double v = Math.max(0.25, dist * 0.6) * (fast ? 3 : 1) * dt;
        tx += (fwd * fx + right * rx) * v;
        tz += (fwd * fz + right * rz) * v;
        ty = Math.max(-1, Math.min(4, ty + up * v));
    }

    public void zoom(double notches) {
        dist = Math.max(0.35, Math.min(40, dist * Math.pow(1.12, notches)));
    }

    // ------------------------------------------------------------ Mathe

    public static float[] perspective(double fovyDeg, double aspect, double n, double f) {
        double t = 1.0 / Math.tan(Math.toRadians(fovyDeg) / 2);
        float[] m = new float[16];
        m[0] = (float) (t / aspect);
        m[5] = (float) t;
        m[10] = (float) ((f + n) / (n - f));
        m[11] = -1;
        m[14] = (float) (2 * f * n / (n - f));
        return m;
    }

    public static float[] lookAt(double[] e, double[] c) {
        double fx = c[0] - e[0], fy = c[1] - e[1], fz = c[2] - e[2];
        double fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        double sx = fy * 0 - fz * 1, sy = fz * 0 - fx * 0, sz = fx * 1 - fy * 0; // f x up(0,1,0)
        double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-9) { sx = 1; sy = 0; sz = 0; sl = 1; }
        sx /= sl; sy /= sl; sz /= sl;
        double ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;
        float[] m = new float[16];
        m[0] = (float) sx; m[4] = (float) sy; m[8] = (float) sz;
        m[1] = (float) ux; m[5] = (float) uy; m[9] = (float) uz;
        m[2] = (float) -fx; m[6] = (float) -fy; m[10] = (float) -fz;
        m[12] = (float) -(sx * e[0] + sy * e[1] + sz * e[2]);
        m[13] = (float) -(ux * e[0] + uy * e[1] + uz * e[2]);
        m[14] = (float) (fx * e[0] + fy * e[1] + fz * e[2]);
        m[15] = 1;
        return m;
    }

    public static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++)
            for (int rr = 0; rr < 4; rr++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + rr] * b[c * 4 + k];
                r[c * 4 + rr] = s;
            }
        return r;
    }

    /** Allgemeine 4×4-Inverse (Cramer); null, wenn singulär. */
    public static float[] invert(float[] m) {
        double[] a = new double[16];
        for (int i = 0; i < 16; i++) a[i] = m[i];
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
        if (Math.abs(det) < 1e-30) return null;
        float[] r = new float[16];
        for (int i = 0; i < 16; i++) r[i] = (float) (inv[i] / det);
        return r;
    }
}
