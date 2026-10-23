package com.dan.portrait.gl;

import com.dan.portrait.BokehLights;
import com.dan.portrait.Grade;
import com.dan.portrait.Lens;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Das Bild auf der Grafikkarte (Phase 9): Lichter des Hintergrunds als Zerstreuungskreise in der Form
 * der Blende, Schärfentiefe aus der Tiefe der Szene (halbe Auflösung, Sammeln nach dem eigenen Kreis),
 * Farbtabelle der Farbgebung als 3D-Textur.
 *
 * <pre>
 *   PortraitGl p = new PortraitGl(); p.init(gl);
 *   p.lights(gl, BokehLights.city(...)); p.grade(gl, "Regennacht");
 *   // ins HDR-Ziel, vor der Szene:
 *   p.background(gl, viewProj, lens, w, h);
 *   // nach der Szene:
 *   int tex = p.depthOfField(gl, hdrTex, depthTex, lens, near, far, w, h);   // Ergebnis-Textur
 *   // in der Tonkurve: p.lutTexture()
 * </pre>
 */
public final class PortraitGl {

    private int progBokeh, progCoc, progGather, progComp, vao, ssbo, lightCount;
    private int halfA, halfB, outTex, fboA, fboB, fboOut, tw, th;
    private int lut;
    private String error;
    /** Größte Zerstreuung im Bild (Anteil der Bildhöhe) und Schärfentiefe an/aus. */
    public float maxCocFrac = 0.045f;
    public boolean dofOn = true;
    /** GPU-unabhängige Zahl: zuletzt gezeichnete Lichter. */
    public int lastLights;
    public static final int LUT_N = 32;

    public String error() { return error; }

    public void init(GL4 gl) {
        try {
            progBokeh = program(gl, "bokeh.vert", "bokeh.frag");
            progCoc = program(gl, "fs.vert", "dof_coc.frag");
            progGather = program(gl, "fs.vert", "dof_gather.frag");
            progComp = program(gl, "fs.vert", "dof_comp.frag");
        } catch (Exception e) {
            error = e.getMessage();
            return;
        }
        int[] a = new int[1];
        gl.glGenVertexArrays(1, a, 0); vao = a[0];
        gl.glGenBuffers(1, a, 0); ssbo = a[0];
        grade(gl, "Regennacht");
    }

    /** Lichter des Hintergrunds hochladen. */
    public void lights(GL4 gl, BokehLights b) {
        if (ssbo == 0) return;
        int n = b == null ? 0 : b.lights.size();
        float[] d = new float[Math.max(8, n * 8)];
        for (int i = 0; i < n; i++) {
            BokehLights.Light l = b.lights.get(i);
            d[i * 8] = (float) l.p[0]; d[i * 8 + 1] = (float) l.p[1]; d[i * 8 + 2] = (float) l.p[2]; d[i * 8 + 3] = (float) l.r;
            d[i * 8 + 4] = (float) l.col[0]; d[i * 8 + 5] = (float) l.col[1]; d[i * 8 + 6] = (float) l.col[2]; d[i * 8 + 7] = l.mirror ? 1 : 0;
        }
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, ssbo);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, (long) d.length * 4, java.nio.FloatBuffer.wrap(d), GL.GL_STATIC_DRAW);
        lightCount = n;
    }

    /** Farbtabelle der Farbgebung erneuern. */
    public void grade(GL4 gl, String name) {
        float[] t = Grade.lut(name, LUT_N);
        if (lut == 0) {
            int[] a = new int[1];
            gl.glGenTextures(1, a, 0);
            lut = a[0];
            gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, lut);
            gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES2.GL_TEXTURE_WRAP_R, GL.GL_CLAMP_TO_EDGE);
        }
        gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, lut);
        gl.glTexImage3D(GL2ES2.GL_TEXTURE_3D, 0, GL.GL_RGB16F, LUT_N, LUT_N, LUT_N, 0, GL.GL_RGB, GL.GL_FLOAT, java.nio.FloatBuffer.wrap(t));
        gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, 0);
    }

    public int lutTexture() { return lut; }

    /** Lichter als Zerstreuungskreise ins gebundene Ziel (additiv, ohne Tiefe). */
    public void background(GL4 gl, float[] viewProj, Lens lens, int w, int h) {
        if (progBokeh == 0 || lightCount == 0) { lastLights = 0; return; }
        gl.glUseProgram(progBokeh);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(progBokeh, "uViewProj"), 1, false, viewProj, 0);
        gl.glUniform2f(gl.glGetUniformLocation(progBokeh, "uSize"), w, h);
        gl.glUniform1f(gl.glGetUniformLocation(progBokeh, "uCocK"), (float) lens.cocK(h));
        gl.glUniform1f(gl.glGetUniformLocation(progBokeh, "uFocus"), (float) lens.focusM);
        gl.glUniform1f(gl.glGetUniformLocation(progBokeh, "uFocalPx"), (float) lens.focalPx(h));
        gl.glUniform1f(gl.glGetUniformLocation(progBokeh, "uMaxPx"), 0.4f * h);
        gl.glUniform1i(gl.glGetUniformLocation(progBokeh, "uBlades"), lens.blades);
        gl.glUniform1f(gl.glGetUniformLocation(progBokeh, "uRound"), (float) lens.roundness);
        gl.glUniform1f(gl.glGetUniformLocation(progBokeh, "uRot"), (float) Math.toRadians(lens.bladeRotDeg));
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, ssbo);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, lightCount * 6);
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_BLEND);
        lastLights = lightCount;
    }

    /**
     * Schärfentiefe: aus HDR-Farbe und Tiefe in die eigene Ergebnis-Textur (volle Auflösung, RGBA16F).
     * Ohne Schärfentiefe kommt {@code color} zurück.
     */
    public int depthOfField(GL4 gl, int color, int depth, Lens lens, double near, double far, int w, int h) {
        if (!dofOn || progComp == 0) return color;
        targets(gl, w, h);
        int hw = Math.max(1, w / 2), hh = Math.max(1, h / 2);
        float k = (float) lens.cocK(h), fz = (float) lens.focusM, mx = maxCocFrac * h;
        int[] fb = new int[1], vp = new int[4];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, fb, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glBindVertexArray(vao);
        // 1: halbe Auflösung mit Kreis
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboA);
        gl.glViewport(0, 0, hw, hh);
        gl.glUseProgram(progCoc);
        bindTex(gl, 0, color); bindTex(gl, 1, depth);
        gl.glUniform1i(gl.glGetUniformLocation(progCoc, "uColor"), 0);
        gl.glUniform1i(gl.glGetUniformLocation(progCoc, "uDepth"), 1);
        gl.glUniform2f(gl.glGetUniformLocation(progCoc, "uTexel"), 1f / w, 1f / h);
        common(gl, progCoc, near, far, k, fz, mx);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        // 2: sammeln
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboB);
        gl.glUseProgram(progGather);
        bindTex(gl, 0, halfA);
        gl.glUniform1i(gl.glGetUniformLocation(progGather, "uHalf"), 0);
        gl.glUniform2f(gl.glGetUniformLocation(progGather, "uTexel"), 1f / hw, 1f / hh);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        // 3: zusammensetzen
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboOut);
        gl.glViewport(0, 0, w, h);
        gl.glUseProgram(progComp);
        bindTex(gl, 0, color); bindTex(gl, 1, depth); bindTex(gl, 2, halfB);
        gl.glUniform1i(gl.glGetUniformLocation(progComp, "uColor"), 0);
        gl.glUniform1i(gl.glGetUniformLocation(progComp, "uDepth"), 1);
        gl.glUniform1i(gl.glGetUniformLocation(progComp, "uBlur"), 2);
        common(gl, progComp, near, far, k, fz, mx);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fb[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        return outTex;
    }

    private void common(GL4 gl, int p, double near, double far, float k, float fz, float mx) {
        gl.glUniform1f(gl.glGetUniformLocation(p, "uNear"), (float) near);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uFar"), (float) far);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uCocK"), k);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uFocus"), fz);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uMaxCoc"), mx);
    }

    private static void bindTex(GL4 gl, int unit, int tex) {
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
    }

    private void targets(GL4 gl, int w, int h) {
        if (w == tw && h == th && outTex != 0) return;
        free(gl);
        tw = w; th = h;
        int hw = Math.max(1, w / 2), hh = Math.max(1, h / 2);
        halfA = tex(gl, hw, hh); halfB = tex(gl, hw, hh); outTex = tex(gl, w, h);
        fboA = fbo(gl, halfA); fboB = fbo(gl, halfB); fboOut = fbo(gl, outTex);
    }

    private static int tex(GL4 gl, int w, int h) {
        int[] a = new int[1];
        gl.glGenTextures(1, a, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, a[0]);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA16F, w, h, 0, GL.GL_RGBA, GL.GL_FLOAT, null);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        return a[0];
    }

    private static int fbo(GL4 gl, int tex) {
        int[] a = new int[1];
        gl.glGenFramebuffers(1, a, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, a[0]);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, tex, 0);
        return a[0];
    }

    private void free(GL4 gl) {
        if (fboA != 0) gl.glDeleteFramebuffers(3, new int[]{fboA, fboB, fboOut}, 0);
        if (halfA != 0) gl.glDeleteTextures(3, new int[]{halfA, halfB, outTex}, 0);
        fboA = fboB = fboOut = halfA = halfB = outTex = 0;
    }

    public void dispose(GL4 gl) {
        free(gl);
        for (int p : new int[]{progBokeh, progCoc, progGather, progComp}) if (p != 0) gl.glDeleteProgram(p);
        progBokeh = progCoc = progGather = progComp = 0;
        if (lut != 0) gl.glDeleteTextures(1, new int[]{lut}, 0);
        gl.glDeleteBuffers(1, new int[]{ssbo}, 0);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
    }

    // ------------------------------------------------------------ Shader

    static String read(String name) throws IOException {
        try (InputStream in = PortraitGl.class.getResourceAsStream(name)) {
            if (in == null) throw new IOException("Shader fehlt: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static int program(GL4 gl, String vs, String fs) throws IOException {
        int v = shader(gl, GL2ES2.GL_VERTEX_SHADER, read(vs), vs), f = shader(gl, GL2ES2.GL_FRAGMENT_SHADER, read(fs), fs);
        int p = gl.glCreateProgram();
        gl.glAttachShader(p, v); gl.glAttachShader(p, f);
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL2ES2.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetProgramiv(p, GL2ES2.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetProgramInfoLog(p, b.length, null, 0, b, 0);
            throw new IOException("portrait " + vs + "/" + fs + ": " + new String(b, StandardCharsets.UTF_8).trim());
        }
        gl.glDeleteShader(v); gl.glDeleteShader(f);
        return p;
    }

    private static int shader(GL4 gl, int type, String src, String name) throws IOException {
        int s = gl.glCreateShader(type);
        gl.glShaderSource(s, 1, new String[]{src}, null, 0);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL2ES2.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetShaderiv(s, GL2ES2.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetShaderInfoLog(s, b.length, null, 0, b, 0);
            throw new IOException("portrait " + name + ": " + new String(b, StandardCharsets.UTF_8).trim());
        }
        return s;
    }
}
