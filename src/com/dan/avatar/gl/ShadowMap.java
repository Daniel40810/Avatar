package com.dan.avatar.gl;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL4;

/**
 * Die Schattenkarte des Führungslichts (F12): eine Tiefenkarte aus Sicht des Spots, auf die
 * Personen zugeschnitten. Personen zeichnen sich mit {@link AvatarGl#drawDepth} hinein; Haut,
 * Kleidung und Bühne lesen sie mit weichem PCF, die Haut zusätzlich als Weg des Lichts durch
 * den Körper (Durchleuchtung).
 *
 * <pre>
 *   ShadowMap sm = new ShadowMap();
 *   sm.fit(lightPos, centre, radius);       // Kamera des Lichts auf die Personen
 *   sm.begin(gl, 1024); figures.drawDepth(...); sm.end(gl, w, h);
 * </pre>
 */
public final class ShadowMap {

    public int size, tex, fbo;
    /** Licht-Ansicht·Projektion (spaltenweise). */
    public final float[] viewProj = new float[16];
    public float near = 0.2f, far = 10f;
    /** Weichheit: Radius des PCF in Texeln. */
    public float soft = 2.5f;
    public boolean valid;

    /** Kamera des Lichts: von {@code pos} auf eine Kugel um {@code c} mit Radius {@code r}. */
    public void fit(float[] pos, double[] c, double r) {
        double dx = c[0] - pos[0], dy = c[1] - pos[1], dz = c[2] - pos[2];
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double half = Math.asin(Math.min(0.99, r / Math.max(d, 1e-3)));
        near = (float) Math.max(0.05, d - r * 1.2);
        far = (float) (d + r * 1.2);
        float[] view = lookAt(pos[0], pos[1], pos[2], c[0], c[1], c[2]);
        float[] proj = perspective(half * 2 * 1.04, near, far);
        mul(proj, view, viewProj);
    }

    /** Ziel anlegen (bei Größenwechsel neu) und zum Zeichnen binden. */
    public void begin(GL4 gl, int s) {
        if (s != size || fbo == 0) {
            dispose(gl);
            size = s;
            int[] a = new int[1];
            gl.glGenTextures(1, a, 0); tex = a[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
            gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_DEPTH_COMPONENT24, s, s, 0, GL2ES2.GL_DEPTH_COMPONENT, GL.GL_UNSIGNED_INT, null);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            gl.glGenFramebuffers(1, a, 0); fbo = a[0];
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
            gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, tex, 0);
            gl.glDrawBuffers(1, new int[]{GL.GL_NONE}, 0);
            gl.glReadBuffer(GL.GL_NONE);
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, size, size);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
        gl.glEnable(GL.GL_POLYGON_OFFSET_FILL);
        gl.glPolygonOffset(1.5f, 2f);
    }

    public void end(GL4 gl) {
        gl.glDisable(GL.GL_POLYGON_OFFSET_FILL);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        valid = true;
    }

    /** Schatten-Uniforms für ein Programm setzen; die Karte liegt auf Einheit {@code unit}. */
    public void bind(GL4 gl, int prog, int unit) {
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(GL.GL_TEXTURE_2D, valid ? tex : 0);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uShadow"), unit);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uShadowOn"), valid ? 1 : 0);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uLightVP"), 1, false, viewProj, 0);
        gl.glUniform2f(gl.glGetUniformLocation(prog, "uLightNF"), near, far);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uShadowSoft"), soft);
        gl.glUniform2f(gl.glGetUniformLocation(prog, "uShadowTexel"), 1f / Math.max(1, size), 1f / Math.max(1, size));
        gl.glActiveTexture(GL.GL_TEXTURE0);
    }

    public void dispose(GL4 gl) {
        if (fbo != 0) gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
        if (tex != 0) gl.glDeleteTextures(1, new int[]{tex}, 0);
        fbo = tex = 0;
        valid = false;
    }

    // ------------------------------------------------------------ Matrizen (spaltenweise)

    static float[] lookAt(double ex, double ey, double ez, double cx, double cy, double cz) {
        double fx = cx - ex, fy = cy - ey, fz = cz - ez;
        double fl = Math.sqrt(fx * fx + fy * fy + fz * fz); fx /= fl; fy /= fl; fz /= fl;
        double ux = 0, uy = 1, uz = 0;
        if (Math.abs(fy) > 0.98) { ux = 0; uy = 0; uz = 1; }
        double sx = fy * uz - fz * uy, sy = fz * ux - fx * uz, sz = fx * uy - fy * ux;
        double sl = Math.sqrt(sx * sx + sy * sy + sz * sz); sx /= sl; sy /= sl; sz /= sl;
        double vx = sy * fz - sz * fy, vy = sz * fx - sx * fz, vz = sx * fy - sy * fx;
        return new float[]{
            (float) sx, (float) vx, (float) -fx, 0,
            (float) sy, (float) vy, (float) -fy, 0,
            (float) sz, (float) vz, (float) -fz, 0,
            (float) -(sx * ex + sy * ey + sz * ez), (float) -(vx * ex + vy * ey + vz * ez), (float) (fx * ex + fy * ey + fz * ez), 1};
    }

    static float[] perspective(double fovy, double n, double f) {
        double t = 1 / Math.tan(fovy / 2);
        return new float[]{(float) t, 0, 0, 0, 0, (float) t, 0, 0, 0, 0, (float) ((f + n) / (n - f)), -1, 0, 0, (float) (2 * f * n / (n - f)), 0};
    }

    static void mul(float[] a, float[] b, float[] out) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++)
            for (int rr = 0; rr < 4; rr++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + rr] * b[c * 4 + k];
                r[c * 4 + rr] = s;
            }
        System.arraycopy(r, 0, out, 0, 16);
    }
}
