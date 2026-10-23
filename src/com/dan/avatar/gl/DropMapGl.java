package com.dan.avatar.gl;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;

/**
 * Tropfenkarte auf der Grafikkarte (Phase 8): Höhe der Perlen (mm, R16F, in jedem Bild neu) und
 * Spuren der gelaufenen Perlen (Nässe 0…1, R16F, bleibt und trocknet langsam). Beide über dem
 * UV-Ausschnitt von {@code DropField}; die Haut liest sie in {@code avatar.frag}.
 */
public final class DropMapGl {

    /** Kantenlänge der Perlenkarte und der Spurenkarte (Texel). */
    public final int size, trailSize;
    private int texH, texT, fboH, fboT, prog, ssbo, vao;
    private String error;

    public DropMapGl() { this(2048, 1024); }

    public DropMapGl(int size, int trailSize) { this.size = size; this.trailSize = trailSize; }

    public String error() { return error; }

    public int heightTexture() { return texH; }

    public int trailTexture() { return texT; }

    public void init(GL4 gl) {
        try {
            prog = AvatarGl.program(gl, "drop.vert", "drop.frag");
        } catch (Exception e) {
            error = e.getMessage();
            return;
        }
        int[] a = new int[2];
        gl.glGenTextures(2, a, 0);
        texH = a[0]; texT = a[1];
        for (int i = 0; i < 2; i++) {
            int s = i == 0 ? size : trailSize;
            gl.glBindTexture(GL.GL_TEXTURE_2D, a[i]);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL2ES3.GL_R16F, s, s);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        }
        gl.glGenFramebuffers(2, a, 0);
        fboH = a[0]; fboT = a[1];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboH);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, texH, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboT);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, texT, 0);
        gl.glClearBufferfv(GL2ES3.GL_COLOR, 0, new float[]{0, 0, 0, 0}, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboH);
        gl.glClearBufferfv(GL2ES3.GL_COLOR, 0, new float[]{0, 0, 0, 0}, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        gl.glGenBuffers(1, a, 0);
        ssbo = a[0];
        gl.glGenVertexArrays(1, a, 0);
        vao = a[0];
    }

    /**
     * Karten erneuern: {@code beads} und {@code trails} wie {@code SkinDrops.beadData/trailData};
     * {@code keep}: Anteil der alten Spuren, der bleibt (Trocknen in diesem Schritt).
     */
    public void update(GL4 gl, float[] beads, float[] trails, float keep) {
        if (prog == 0) return;
        int[] vp = new int[4], fb = new int[1];
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, fb, 0);
        boolean depth = gl.glIsEnabled(GL.GL_DEPTH_TEST), blend = gl.glIsEnabled(GL.GL_BLEND);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glUseProgram(prog);
        gl.glBindVertexArray(vao);
        // Spuren: trocknen, dann neue Strecken dazu (Maximum)
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboT);
        gl.glDrawBuffers(1, new int[]{GL.GL_COLOR_ATTACHMENT0}, 0);
        gl.glViewport(0, 0, trailSize, trailSize);
        gl.glEnable(GL.GL_BLEND);
        if (keep < 1) {
            gl.glBlendEquation(GL.GL_FUNC_ADD);
            gl.glBlendColor(keep, keep, keep, keep);
            gl.glBlendFunc(GL.GL_ZERO, GL2ES2.GL_CONSTANT_COLOR);
            // Ein Band über alles (Spur von (0,0,5) nach (1,1)): breit genug, um alles zu decken
            draw(gl, new float[]{0.5f, -0.5f, 0.5f, 1.5f, 1.0f}, 1);
        }
        if (trails.length > 0) {
            gl.glBlendEquation(GL2ES3.GL_MAX);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
            draw(gl, trails, 1);
        }
        // Perlen: neu, Maximum
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboH);
        gl.glViewport(0, 0, size, size);
        gl.glClearBufferfv(GL2ES3.GL_COLOR, 0, new float[]{0, 0, 0, 0}, 0);
        if (beads.length > 0) {
            gl.glBlendEquation(GL2ES3.GL_MAX);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
            draw(gl, beads, 0);
        }
        gl.glBlendEquation(GL.GL_FUNC_ADD);
        if (!blend) gl.glDisable(GL.GL_BLEND);
        if (depth) gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glBindVertexArray(0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fb[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
    }

    /** Alles trocken. */
    public void clear(GL4 gl) {
        if (prog == 0) return;
        int[] fb = new int[1];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, fb, 0);
        for (int f : new int[]{fboH, fboT}) {
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, f);
            gl.glClearBufferfv(GL2ES3.GL_COLOR, 0, new float[]{0, 0, 0, 0}, 0);
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fb[0]);
    }

    private void draw(GL4 gl, float[] data, int mode) {
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, ssbo);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, (long) data.length * 4, java.nio.FloatBuffer.wrap(data), GL2ES2.GL_STREAM_DRAW);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, ssbo);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uMode"), mode);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, data.length / 5 * 6);
    }

    public void dispose(GL4 gl) {
        if (prog != 0) gl.glDeleteProgram(prog);
        gl.glDeleteTextures(2, new int[]{texH, texT}, 0);
        gl.glDeleteFramebuffers(2, new int[]{fboH, fboT}, 0);
        gl.glDeleteBuffers(1, new int[]{ssbo}, 0);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        prog = 0;
    }
}
