package com.dan.avatar.gl;

import com.dan.avatar.Body;
import com.dan.avatar.Skin;
import com.dan.avatar.SkinDetail;
import com.dan.avatar.SkinTextures;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLContext;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Was alle Personen einer GL-Fläche für die Haut teilen: die sechs Grundhäute als Textur-Feld
 * (komprimiert, Werte sRGB-codiert), die beiden Detailkarten (kachelbar, mit Mipmaps) und die vorab integrierte
 * Tabelle für „Niedrig“. Einmal je GL-Kontext, mit Zähler; die letzte Person gibt alles frei.
 */
final class SkinGl {

    int skinArray, detA, detB, lut;
    float[][] invMean = new float[6][3];
    String note = "";
    private int refs;

    private static final Map<GLContext, SkinGl> SHARED = new HashMap<>();
    /**
     * S3TC (DXT1), Werte bleiben sRGB-codiert (der Shader rechnet linear um, weil nicht jeder Treiber
     * beim selbst komprimierten sRGB-Format richtig dekodiert); fällt auf RGB8 zurück.
     */
    static final int COMPRESSED_RGB_DXT1 = 0x83F0;

    static synchronized SkinGl acquire(GL4 gl, Body body) {
        GLContext ctx = GLContext.getCurrent();
        SkinGl s = SHARED.get(ctx);
        if (s == null) {
            s = new SkinGl();
            s.create(gl, body);
            SHARED.put(ctx, s);
        }
        s.refs++;
        return s;
    }

    static synchronized void release(GL4 gl) {
        GLContext ctx = GLContext.getCurrent();
        SkinGl s = SHARED.get(ctx);
        if (s == null) return;
        if (--s.refs > 0) return;
        gl.glDeleteTextures(4, new int[]{s.skinArray, s.detA, s.detB, s.lut}, 0);
        SHARED.remove(ctx);
    }

    private void create(GL4 gl, Body body) {
        int[] a = new int[4];
        gl.glGenTextures(4, a, 0);
        skinArray = a[0]; detA = a[1]; detB = a[2]; lut = a[3];
        // ---- Grundhäute
        try {
            SkinTextures st = SkinTextures.load(body);
            int n = st.rgb.length, s = st.size;
            boolean ext = gl.isExtensionAvailable("GL_EXT_texture_compression_s3tc");
            byte[] all = new byte[n * s * s * 3];
            for (int i = 0; i < n; i++) System.arraycopy(st.rgb[i], 0, all, i * s * s * 3, s * s * 3);
            while (gl.glGetError() != GL.GL_NO_ERROR) { }
            boolean ok = false;
            if (ext) ok = uploadArray(gl, COMPRESSED_RGB_DXT1, s, n, all);
            if (!ok) { ext = false; uploadArray(gl, GL.GL_RGB8, s, n, all); }
            gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
            gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameterf(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MAX_ANISOTROPY_EXT, 8f);
            for (int i = 0; i < n; i++)
                for (int c = 0; c < 3; c++) invMean[i][c] = 1f / Math.max(1e-4f, st.mean[i][c]);
            note = String.format(java.util.Locale.GERMANY, "%d Grundhäute %d² %s, %.0f ms", n, s, ext ? "DXT1" : "RGB8", st.ms);
        } catch (Exception e) {
            note = "Grundhäute fehlen: " + e.getMessage();
            gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, skinArray);
            byte[] grey = new byte[6 * 3];
            java.util.Arrays.fill(grey, (byte) 200);
            gl.glTexImage3D(GL2ES3.GL_TEXTURE_2D_ARRAY, 0, GL.GL_RGB8, 1, 1, 6, 0, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, ByteBuffer.wrap(grey));
            gl.glTexParameteri(GL2ES3.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            for (float[] m : invMean) java.util.Arrays.fill(m, 1f / 0.578f);
        }
        // ---- Detailkarten
        SkinDetail d = SkinDetail.standard();
        detail(gl, detA, d.a);
        detail(gl, detB, d.b);
        // ---- vorab integriert
        float[] t = Skin.preintegratedCached();
        gl.glBindTexture(GL.GL_TEXTURE_2D, lut);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGB16F, Skin.LUT_W, Skin.LUT_H, 0, GL.GL_RGB, GL.GL_FLOAT, FloatBuffer.wrap(t));
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    /** Feld mit allen Ebenen auf einmal hochladen (der Treiber komprimiert), dann Mipmaps; false bei Fehler. */
    private boolean uploadArray(GL4 gl, int fmt, int s, int n, byte[] all) {
        gl.glBindTexture(GL2ES3.GL_TEXTURE_2D_ARRAY, skinArray);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexImage3D(GL2ES3.GL_TEXTURE_2D_ARRAY, 0, fmt, s, s, n, 0, GL.GL_RGB, GL.GL_UNSIGNED_BYTE, ByteBuffer.wrap(all));
        gl.glGenerateMipmap(GL2ES3.GL_TEXTURE_2D_ARRAY);
        if (gl.glGetError() == GL.GL_NO_ERROR) return true;
        while (gl.glGetError() != GL.GL_NO_ERROR) { }
        gl.glDeleteTextures(1, new int[]{skinArray}, 0);
        int[] a = new int[1];
        gl.glGenTextures(1, a, 0);
        skinArray = a[0];
        return false;
    }

    private static void detail(GL4 gl, int tex, byte[] data) {
        int s = SkinDetail.SIZE;
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, s, s, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, ByteBuffer.wrap(data));
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
        gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAX_ANISOTROPY_EXT, 8f);
    }
}
