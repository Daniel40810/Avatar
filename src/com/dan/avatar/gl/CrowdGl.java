package com.dan.avatar.gl;

import com.dan.avatar.LodMesh;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL4;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Viele Personen in den fernen Detailstufen (Phase 12): je Person und Stufe ein Netz aus {@link LodMesh} auf
 * der Grafikkarte, Gelenke aller Personen in einem Puffer (Dual-Quaternionen, 8 floats je Gelenk), Skinning im
 * Scheitelshader, ein Aufruf je Person. Übergänge mit der Rasterblende ({@link #draw} mit Fenster lo, hi).
 */
public final class CrowdGl {

    /** Ein Netz auf der Grafikkarte. */
    public static final class Mesh {
        int vao, vbo, ibo, count;
        public int triangles() { return count / 3; }
    }

    private int prog, dqBuf, joints, capacity;
    private String error;

    public String error() { return error; }

    public void init(GL4 gl, int joints, int capacity) {
        this.joints = joints;
        this.capacity = capacity;
        try { prog = AvatarGl.program(gl, "crowd.vert", "crowd.frag"); }
        catch (IOException e) { error = e.getMessage(); return; }
        int[] a = new int[1];
        gl.glGenBuffers(1, a, 0);
        dqBuf = a[0];
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, dqBuf);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, (long) capacity * joints * 8 * 4, null, GL2ES2.GL_STREAM_DRAW);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
    }

    /** Netz hochladen: Lage, Normale, Farbe, Gelenke (int), Gewichte verschachtelt. */
    public Mesh upload(GL4 gl, LodMesh m) {
        Mesh r = new Mesh();
        int stride = (3 + 3 + 3 + 4 + 4) * 4;
        java.nio.ByteBuffer bb = Buffers.newDirectByteBuffer(m.vertices * stride);
        for (int i = 0; i < m.vertices; i++) {
            for (int k = 0; k < 3; k++) bb.putFloat(m.pos[i * 3 + k]);
            for (int k = 0; k < 3; k++) bb.putFloat(m.nrm[i * 3 + k]);
            for (int k = 0; k < 3; k++) bb.putFloat(m.col[i * 3 + k]);
            for (int k = 0; k < 4; k++) bb.putInt(m.jn[i * 4 + k]);
            for (int k = 0; k < 4; k++) bb.putFloat(m.wt[i * 4 + k]);
        }
        bb.flip();
        int[] a = new int[1];
        gl.glGenVertexArrays(1, a, 0); r.vao = a[0];
        gl.glBindVertexArray(r.vao);
        gl.glGenBuffers(1, a, 0); r.vbo = a[0];
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, r.vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, bb.limit(), bb, GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0); gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, stride, 0);
        gl.glEnableVertexAttribArray(1); gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, stride, 12);
        gl.glEnableVertexAttribArray(2); gl.glVertexAttribPointer(2, 3, GL.GL_FLOAT, false, stride, 24);
        gl.glEnableVertexAttribArray(3); gl.glVertexAttribIPointer(3, 4, GL2ES2.GL_INT, stride, 36);
        gl.glEnableVertexAttribArray(4); gl.glVertexAttribPointer(4, 4, GL.GL_FLOAT, false, stride, 52);
        IntBuffer ib = Buffers.newDirectIntBuffer(m.idx.length == 0 ? new int[3] : m.idx);
        gl.glGenBuffers(1, a, 0); r.ibo = a[0];
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, r.ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) ib.limit() * 4, ib, GL.GL_STATIC_DRAW);
        r.count = m.idx.length;
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        return r;
    }

    /** Gelenke der Person {@code slot} (Dual-Quaternionen, 8 floats je Gelenk). */
    public void bones(GL4 gl, int slot, float[] dq) {
        if (slot >= capacity) return;
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, dqBuf);
        gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, (long) slot * joints * 8 * 4, (long) Math.min(dq.length, joints * 8) * 4, FloatBuffer.wrap(dq));
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
    }

    /** Licht und Kamera für alle folgenden {@link #draw}. */
    public void begin(GL4 gl, float[] viewProj, double[] eye, AvatarGl.Light l) {
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uViewProj"), 1, false, viewProj, 0);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uEye"), (float) eye[0], (float) eye[1], (float) eye[2]);
        gl.glUniform3fv(gl.glGetUniformLocation(prog, "uKeyPos"), 1, l.keyPos, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(prog, "uKeyDir"), 1, l.keyDir, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(prog, "uKeyCol"), 1, l.g(l.keyCol), 0);
        gl.glUniform2f(gl.glGetUniformLocation(prog, "uKeyCone"), (float) Math.cos(Math.toRadians(l.keyConeInner)), (float) Math.cos(Math.toRadians(l.keyConeOuter)));
        gl.glUniform3fv(gl.glGetUniformLocation(prog, "uRimPos"), 1, l.rimPos, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(prog, "uRimCol"), 1, l.g(l.rimCol), 0);
        gl.glUniform3fv(gl.glGetUniformLocation(prog, "uFillCol"), 1, l.g(l.fillCol), 0);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, dqBuf);
        gl.glDisable(GL.GL_CULL_FACE);
    }

    /** Eine Person zeichnen: Netz, Platz im Gelenkpuffer, Versatz, Fenster der Rasterblende, Farbe der Stufe (oder null). */
    public void draw(GL4 gl, Mesh m, int slot, float x, float y, float z, float lo, float hi, float[] tint) {
        if (m == null || hi <= lo) return;
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uBone"), slot * joints);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uOffset"), x, y, z);
        gl.glUniform2f(gl.glGetUniformLocation(prog, "uDither"), lo, hi);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uTint"), tint == null ? 0 : tint[0], tint == null ? 0 : tint[1], tint == null ? 0 : tint[2]);
        gl.glBindVertexArray(m.vao);
        gl.glDrawElements(GL.GL_TRIANGLES, m.count, GL.GL_UNSIGNED_INT, 0);
        gl.glBindVertexArray(0);
    }

    public void free(GL4 gl, Mesh m) {
        if (m == null) return;
        gl.glDeleteBuffers(2, new int[]{m.vbo, m.ibo}, 0);
        gl.glDeleteVertexArrays(1, new int[]{m.vao}, 0);
    }

    public void dispose(GL4 gl) {
        if (prog != 0) gl.glDeleteProgram(prog);
        if (dqBuf != 0) gl.glDeleteBuffers(1, new int[]{dqBuf}, 0);
        prog = dqBuf = 0;
    }
}
