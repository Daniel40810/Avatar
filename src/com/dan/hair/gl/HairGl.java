package com.dan.hair.gl;

import com.dan.hair.HairDynamics;
import com.dan.hair.Strands;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL4;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Zeichnet Haare (Phase 6, F10 A): Haarkarten aus einem fremden Netz (Lage und Normale liegen schon
 * verformt in dessen Puffern) und einzelne Haare als Linien, deren Wurzeln in einem fremden Netz
 * sitzen. Das Paket kennt keinen Menschen: Netz, Licht und Schatten kommen als Zahlen von außen.
 *
 * <pre>
 *   HairGl h = new HairGl(); h.init(gl);
 *   h.look.albedo = HairColor.albedo(0.6, 0.1);
 *   h.drawCards(gl, card, light, viewProj, 0);   // deckend
 *   h.drawCards(gl, card, light, viewProj, 1);   // Saum, gemischt
 *   h.drawStrands(gl, set, light, viewProj);
 * </pre>
 */
public final class HairGl {

    /** Ein Netz mit Haarkarten: VAO (Attribute 0 Lage, 1 Normale, 2 UV, 8 Biegsamkeit), Textur. */
    public static final class Card {
        public int vao, indexCount, texture;
        /** UV-Achse entlang der Haare und Richtung (Wurzel → Spitze). */
        public int axis = 1;
        public float axisSign = 1;
        /** Mittlere Helligkeit der Textur (über deckende Texel). */
        public float texMean = 0.5f;
    }

    /** Licht und Schatten von außen (linear, vorbelichtet). */
    public static final class Lighting {
        public float[] keyPos = new float[3], keyDir = {0, -1, 0}, keyCol = new float[3], rimPos = new float[3], rimCol = new float[3], fillCol = new float[3];
        public float coneInnerCos = 0.96f, coneOuterCos = 0.86f;
        public int shadowTex;
        public boolean shadowOn;
        public float[] lightViewProj = new float[16];
        public float near = 0.1f, far = 10f, texel = 1f / 1024;
        public float[] eye = new float[3];
        /** Pixel je Meter in 1 m Abstand (für die Deckung der Linien). */
        public float focal = 1000;
        public float time;
    }

    /** Aussehen. */
    public static final class Look {
        public float[] albedo = {0.12f, 0.065f, 0.032f};
        public float roughness = 0.32f, shift = 0.05f, grey = 0f;
        /** Standort der Person (Versatz zu den Ecken). */
        public float[] offset = new float[3];
        public float[] tip = new float[3], flutter = new float[3];
        /** Durchhang der Linien (Anteil der Länge) und Radius eines Haares (m). */
        public float gravity = 0.25f, radius = 4e-5f;
        /** Einzelne Haare: wie stark sie das Hautlicht unter sich abdecken (0 … 1). */
        public float diffCover = 0f;
        /** Drehung des Kopfes gegen die Ruhe (3×3 spaltenweise): dreht die Haarrichtung der Karten mit (Phase 7). */
        public float[] rot = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    }

    /** Ein Satz Haare auf einem Netz (Wurzeln und Form). */
    public static final class StrandSet {
        public int count, segments = 8, dataBuf, idxBuf;
        /** Puffer des Wurzelnetzes (verformte Lagen und Normalen, je 3 floats). */
        public int posBuf, nrmBuf;
        /** Biegsamkeit der Wurzelecken (Attribut 8 des Wurzelnetzes, 4 floats je Ecke) oder 0. */
        public int flexBuf;
        public Look look = new Look();

        public void dispose(GL4 gl) {
            gl.glDeleteBuffers(2, new int[]{dataBuf, idxBuf}, 0);
            dataBuf = idxBuf = 0;
        }
    }

    /** Prüfansicht der Karten (Systemeigenschaft hair.debug): 1 Richtung, 2 Textur, 3 Selbstschatten. */
    static final int DEBUG = Integer.getInteger("hair.debug", 0);

    public final Look look = new Look();
    /** Vergleich: Linien hart (deckend, ohne Glättung) statt Deckung nach Pixelbreite. */
    public boolean hardLines;
    private int progCard, progDepth, progStrand, vaoEmpty;
    private String error;

    public String error() { return error; }

    public void init(GL4 gl) {
        try {
            String light = read("hairlight.glsl");
            progCard = program(gl, read("hair.vert"), "#version 430 core\n" + light + read("hair.frag"));
            progDepth = program(gl, read("hair.vert"), read("hair_depth.frag"));
            progStrand = program(gl, read("strand.vert"), "#version 430 core\n" + light + read("strand.frag"));
        } catch (IOException e) {
            error = e.getMessage();
        }
        int[] a = new int[1];
        gl.glGenVertexArrays(1, a, 0);
        vaoEmpty = a[0];
    }

    /** Haare auf die Grafikkarte: Daten aus {@link Strands}, Dreiecke des Wurzelnetzes, dessen Lage-/Normalenpuffer. */
    public static StrandSet upload(GL4 gl, Strands s, int[] indices, int posBuf, int nrmBuf, int segments) {
        StrandSet set = new StrandSet();
        set.count = s.count;
        set.segments = segments;
        set.posBuf = posBuf;
        set.nrmBuf = nrmBuf;
        int[] a = new int[2];
        gl.glGenBuffers(2, a, 0);
        set.dataBuf = a[0];
        set.idxBuf = a[1];
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, set.dataBuf);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, (long) Math.max(1, s.data.length) * 4, java.nio.FloatBuffer.wrap(s.data.length == 0 ? new float[1] : s.data), GL.GL_STATIC_DRAW);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, set.idxBuf);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, (long) indices.length * 4, java.nio.IntBuffer.wrap(indices), GL.GL_STATIC_DRAW);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        return set;
    }

    private void lightUniforms(GL4 gl, int p, Lighting l, Look k, float[] viewProj) {
        gl.glUseProgram(p);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(p, "uViewProj"), 1, false, viewProj, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(p, "uOffset"), 1, k.offset, 0);
        gl.glUniform4fv(gl.glGetUniformLocation(p, "uHood"), 1, hood, 0);
        gl.glUniform2fv(gl.glGetUniformLocation(p, "uDither"), 1, dither, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(p, "uTip"), 1, k.tip, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(p, "uFlutter"), 1, k.flutter, 0);
        gl.glUniformMatrix3fv(gl.glGetUniformLocation(p, "uRot"), 1, false, k.rot, 0);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uTime"), l.time);
        gl.glUniform3fv(gl.glGetUniformLocation(p, "uEye"), 1, l.eye, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(p, "uKeyPos"), 1, l.keyPos, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(p, "uKeyDir"), 1, l.keyDir, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(p, "uKeyCol"), 1, l.keyCol, 0);
        gl.glUniform2f(gl.glGetUniformLocation(p, "uKeyCone"), l.coneInnerCos, l.coneOuterCos);
        gl.glUniform3fv(gl.glGetUniformLocation(p, "uRimPos"), 1, l.rimPos, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(p, "uRimCol"), 1, l.rimCol, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(p, "uFillCol"), 1, l.fillCol, 0);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uShadowOn"), l.shadowOn ? 1 : 0);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(p, "uLightVP"), 1, false, l.lightViewProj, 0);
        gl.glUniform2f(gl.glGetUniformLocation(p, "uLightNF"), l.near, l.far);
        gl.glUniform2f(gl.glGetUniformLocation(p, "uShadowTexel"), l.texel, l.texel);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.shadowOn ? l.shadowTex : 0);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uShadow"), 1);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform3fv(gl.glGetUniformLocation(p, "uHairAlb"), 1, k.albedo, 0);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uRough"), k.roughness);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uShift"), k.shift);
    }

    /** Haarkarten zeichnen: {@code pass} 0 deckend (Tiefe schreiben), 1 Saum (gemischt, ohne Tiefe). */
    public void drawCards(GL4 gl, Card c, Lighting l, Look k, float[] viewProj, int pass) {
        if (progCard == 0) return;
        lightUniforms(gl, progCard, l, k, viewProj);
        gl.glUniform1i(gl.glGetUniformLocation(progCard, "uPass"), pass);
        gl.glUniform1i(gl.glGetUniformLocation(progCard, "uAxis"), c.axis);
        gl.glUniform1f(gl.glGetUniformLocation(progCard, "uAxisSign"), c.axisSign);
        gl.glUniform1f(gl.glGetUniformLocation(progCard, "uTexMean"), c.texMean);
        gl.glUniform1f(gl.glGetUniformLocation(progCard, "uGreyFrac"), k.grey);
        gl.glUniform1i(gl.glGetUniformLocation(progCard, "uDebug"), DEBUG);
        gl.glUniform1i(gl.glGetUniformLocation(progCard, "uTex"), 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, c.texture);
        gl.glDisable(GL.GL_CULL_FACE);
        if (pass == 1) {
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
            gl.glDepthMask(false);
        }
        gl.glBindVertexArray(c.vao);
        gl.glDrawElements(GL.GL_TRIANGLES, c.indexCount, GL.GL_UNSIGNED_INT, 0);
        gl.glBindVertexArray(0);
        if (pass == 1) {
            gl.glDepthMask(true);
            gl.glDisable(GL.GL_BLEND);
        }
    }

    /** Haarkarten in die Schattenkarte (mit Wind und Kopf gebogen wie im Bild). */
    public void drawCardsDepth(GL4 gl, Card c, Look k, float[] lightViewProj, float time) {
        if (progDepth == 0) return;
        gl.glUseProgram(progDepth);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(progDepth, "uViewProj"), 1, false, lightViewProj, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(progDepth, "uOffset"), 1, k.offset, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(progDepth, "uTip"), 1, k.tip, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(progDepth, "uFlutter"), 1, k.flutter, 0);
        gl.glUniform1f(gl.glGetUniformLocation(progDepth, "uTime"), time);
        gl.glUniform1i(gl.glGetUniformLocation(progDepth, "uTex"), 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, c.texture);
        gl.glBindVertexArray(c.vao);
        gl.glDrawElements(GL.GL_TRIANGLES, c.indexCount, GL.GL_UNSIGNED_INT, 0);
        gl.glBindVertexArray(0);
    }

    /** Kapuze (Phase 10): Mitte (Welt) und Innenradius (m); Radius 0 = keine. */
    public final float[] hood = new float[4];
    /** Rasterblende der Detailstufen (Phase 12): sichtbar im Fenster [lo, hi). */
    public final float[] dither = {0, 1};

    /** Einzelne Haare schreiben Tiefe (für die Schärfentiefe in portrait, Phase 9). */
    public boolean strandDepth;

    /** Einzelne Haare als Linien (gemischt, ohne Tiefe zu schreiben, außer {@link #strandDepth}). */
    public void drawStrands(GL4 gl, StrandSet s, Lighting l, float[] viewProj) {
        if (progStrand == 0 || s == null || s.count == 0) return;
        Look k = s.look;
        lightUniforms(gl, progStrand, l, k, viewProj);
        gl.glUniform1i(gl.glGetUniformLocation(progStrand, "uSeg"), s.segments);
        gl.glUniform1f(gl.glGetUniformLocation(progStrand, "uGravity"), k.gravity);
        gl.glUniform1f(gl.glGetUniformLocation(progStrand, "uFocal"), l.focal);
        gl.glUniform1f(gl.glGetUniformLocation(progStrand, "uRadius"), hardLines ? 1f : k.radius);
        gl.glUniform1f(gl.glGetUniformLocation(progStrand, "uGreyMix"), Math.min(1f, k.grey * 4));
        gl.glUniform1f(gl.glGetUniformLocation(progStrand, "uDiffCover"), k.diffCover);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, s.posBuf);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 1, s.nrmBuf);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 2, s.idxBuf);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 3, s.dataBuf);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 4, s.flexBuf != 0 ? s.flexBuf : s.dataBuf);
        gl.glUniform1i(gl.glGetUniformLocation(progStrand, "uRootFlex"), s.flexBuf != 0 ? 1 : 0);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDepthMask(strandDepth);
        if (!hardLines) gl.glEnable(GL.GL_LINE_SMOOTH);
        gl.glBindVertexArray(vaoEmpty);
        gl.glDrawArrays(GL.GL_LINES, 0, s.count * s.segments * 2);
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_LINE_SMOOTH);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_BLEND);
    }

    /** Bewegung übernehmen. */
    public static void motion(Look k, HairDynamics d) {
        float[] t = d.tipOffset(), f = d.flutter();
        System.arraycopy(t, 0, k.tip, 0, 3);
        System.arraycopy(f, 0, k.flutter, 0, 3);
    }

    public void dispose(GL4 gl) {
        for (int p : new int[]{progCard, progDepth, progStrand}) if (p != 0) gl.glDeleteProgram(p);
        progCard = progDepth = progStrand = 0;
        if (vaoEmpty != 0) gl.glDeleteVertexArrays(1, new int[]{vaoEmpty}, 0);
        vaoEmpty = 0;
    }

    // ------------------------------------------------------------ Shader

    static String read(String name) throws IOException {
        try (InputStream in = HairGl.class.getResourceAsStream(name)) {
            if (in == null) throw new IOException("Shader fehlt: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static int program(GL4 gl, String vs, String fs) throws IOException {
        int v = shader(gl, GL2ES2.GL_VERTEX_SHADER, vs), f = shader(gl, GL2ES2.GL_FRAGMENT_SHADER, fs);
        int p = gl.glCreateProgram();
        gl.glAttachShader(p, v);
        gl.glAttachShader(p, f);
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL2ES2.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(v);
        gl.glDeleteShader(f);
        if (ok[0] == 0) {
            byte[] b = new byte[4096];
            gl.glGetProgramInfoLog(p, b.length, null, 0, b, 0);
            throw new IOException("Haar: " + new String(b, StandardCharsets.UTF_8).trim());
        }
        return p;
    }

    private static int shader(GL4 gl, int type, String src) throws IOException {
        int s = gl.glCreateShader(type);
        gl.glShaderSource(s, 1, new String[]{src}, null);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL2ES2.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] b = new byte[4096];
            gl.glGetShaderInfoLog(s, b.length, null, 0, b, 0);
            throw new IOException("Haar-Shader: " + new String(b, StandardCharsets.UTF_8).trim());
        }
        return s;
    }
}
