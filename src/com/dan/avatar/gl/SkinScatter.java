package com.dan.avatar.gl;

import com.dan.avatar.Skin;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import java.io.IOException;

/**
 * Streuung der Haut im Bildraum (F8 A, Jimenez „Separable SSS“): zwei Durchgänge über das diffuse
 * Hautlicht, waagerecht in ein Zwischenziel, senkrecht additiv ins Bild. Kerne für helle und dunkle
 * Haut aus {@link Skin#kernel}; je Pixel gemischt nach dem Melanin, das die Haut mitschreibt.
 *
 * <pre>
 *   SkinScatter sss = new SkinScatter();
 *   sss.apply(gl, diffuseTex, depthTex, targetFbo, w, h, focalPx, near, far);
 * </pre>
 */
public final class SkinScatter {

    /** Proben je Durchgang (ungerade, höchstens 25). */
    public int samples = 11;
    /** Breite des Profils (1 = d'Eon/Jimenez). */
    public float width = 1.0f;
    private int prog, vao, tmpTex, tmpFbo, tw, th, kernelN = -1;
    private float[] kLight, kDark;
    private String error;

    public String error() { return error; }

    private void init(GL4 gl) {
        try {
            prog = AvatarGl.program(gl, "screen.vert", "sss.frag");
        } catch (IOException e) {
            error = e.getMessage();
        }
        int[] a = new int[1];
        gl.glGenVertexArrays(1, a, 0);
        vao = a[0];
    }

    /**
     * Streut das diffuse Hautlicht {@code diffuse} (a = Hautmaske) und addiert es in das Ziel
     * {@code target} (FBO, Farbanhang 0). {@code focal}: Pixel je Meter in 1 m Abstand.
     */
    public void apply(GL4 gl, int diffuse, int depth, int target, int w, int h, float focal, float near, float far) {
        if (vao == 0) init(gl);
        if (prog == 0) return;
        if (tw != w || th != h) targets(gl, w, h);
        int n = Math.max(3, Math.min(25, samples | 1));
        if (n != kernelN) {
            kLight = Skin.kernel(n, Skin.strength(0.0));
            kDark = Skin.kernel(n, Skin.strength(1.0));
            kernelN = n;
        }
        gl.glUseProgram(prog);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uColor"), 0);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uDepth"), 1);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uFocal"), focal);
        gl.glUniform2f(gl.glGetUniformLocation(prog, "uNearFar"), near, far);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uWidth"), width);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uN"), n);
        gl.glUniform4fv(gl.glGetUniformLocation(prog, "uKernel"), n, kLight, 0);
        gl.glUniform4fv(gl.glGetUniformLocation(prog, "uKernelDark"), n, kDark, 0);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glBindVertexArray(vao);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depth);
        // waagerecht
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, tmpFbo);
        gl.glViewport(0, 0, w, h);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, diffuse);
        gl.glUniform2f(gl.glGetUniformLocation(prog, "uDir"), 1, 0);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uFinal"), 0);
        gl.glDisable(GL.GL_BLEND);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        // senkrecht, additiv ins Bild
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, target);
        gl.glDrawBuffers(1, new int[]{GL.GL_COLOR_ATTACHMENT0}, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tmpTex);
        gl.glUniform2f(gl.glGetUniformLocation(prog, "uDir"), 0, 1);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uFinal"), 1);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
        gl.glColorMask(true, true, true, false);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glColorMask(true, true, true, true);
        gl.glDisable(GL.GL_BLEND);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    private void targets(GL4 gl, int w, int h) {
        free(gl);
        tw = w; th = h;
        int[] a = new int[1];
        gl.glGenTextures(1, a, 0); tmpTex = a[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, tmpTex);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA16F, w, h, 0, GL.GL_RGBA, GL.GL_FLOAT, null);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glGenFramebuffers(1, a, 0); tmpFbo = a[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, tmpFbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, tmpTex, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
    }

    private void free(GL4 gl) {
        if (tmpFbo != 0) gl.glDeleteFramebuffers(1, new int[]{tmpFbo}, 0);
        if (tmpTex != 0) gl.glDeleteTextures(1, new int[]{tmpTex}, 0);
        tmpFbo = tmpTex = 0;
        tw = th = 0;
    }

    public void dispose(GL4 gl) {
        free(gl);
        if (prog != 0) gl.glDeleteProgram(prog);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        prog = vao = 0;
    }
}
