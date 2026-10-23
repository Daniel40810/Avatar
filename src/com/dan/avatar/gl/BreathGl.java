package com.dan.avatar.gl;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL4;

/**
 * Atemhauch zeichnen (Phase 8): weiche Punkte, die nach vorn streuen; nach der Person und den Haaren,
 * vormultipliziert, mit Tiefentest und ohne Tiefe zu schreiben.
 */
public final class BreathGl {

    private int prog, ssbo, vao;
    private String error;

    public String error() { return error; }

    public void init(GL4 gl) {
        try {
            prog = AvatarGl.program(gl, "breath.vert", "breath.frag");
        } catch (Exception e) {
            error = e.getMessage();
            return;
        }
        int[] a = new int[1];
        gl.glGenBuffers(1, a, 0);
        ssbo = a[0];
        gl.glGenVertexArrays(1, a, 0);
        vao = a[0];
    }

    /** {@code data} wie {@code BreathFog.data()}; Lage plus Standort {@code x, y, z}. */
    public void draw(GL4 gl, float[] data, float[] viewProj, double[] eye, AvatarGl.Light l, float focal, float x, float y, float z) {
        if (prog == 0 || data.length < 6) return;
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uViewProj"), 1, false, viewProj, 0);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uOffset"), x, y, z);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uEye"), (float) eye[0], (float) eye[1], (float) eye[2]);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uFocal"), focal);
        gl.glUniform3fv(gl.glGetUniformLocation(prog, "uKeyPos"), 1, l.keyPos, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(prog, "uKeyDir"), 1, l.keyDir, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(prog, "uKeyCol"), 1, l.g(l.keyCol), 0);
        gl.glUniform2f(gl.glGetUniformLocation(prog, "uKeyCone"), (float) Math.cos(Math.toRadians(l.keyConeInner)), (float) Math.cos(Math.toRadians(l.keyConeOuter)));
        gl.glUniform3fv(gl.glGetUniformLocation(prog, "uRimPos"), 1, l.rimPos, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(prog, "uRimCol"), 1, l.g(l.rimCol), 0);
        gl.glUniform3fv(gl.glGetUniformLocation(prog, "uFillCol"), 1, l.g(l.fillCol), 0);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, ssbo);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, (long) data.length * 4, java.nio.FloatBuffer.wrap(data), GL2ES2.GL_STREAM_DRAW);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, ssbo);
        gl.glEnable(GL4.GL_PROGRAM_POINT_SIZE);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDepthMask(false);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_POINTS, 0, data.length / 6);
        gl.glBindVertexArray(0);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL4.GL_PROGRAM_POINT_SIZE);
    }

    public void dispose(GL4 gl) {
        if (prog != 0) gl.glDeleteProgram(prog);
        gl.glDeleteBuffers(1, new int[]{ssbo}, 0);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        prog = 0;
    }
}
