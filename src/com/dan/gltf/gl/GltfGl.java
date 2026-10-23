package com.dan.gltf.gl;

import com.dan.gltf.Gltf;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL4;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import javax.imageio.ImageIO;

/**
 * Lädt Teile eines {@link Gltf}-Modells in Puffer der Grafikkarte (OpenGL 4.3).
 * Feste Lagen der Attribute: 0 Lage, 1 Normale, 2 UV, 3 Tangente, 4 Gelenke (ivec4), 5 Gewichte.
 * Lage und Normale liegen in eigenen Puffern, damit gemischte Morphs sie ersetzen können.
 */
public final class GltfGl {

    public static final int POSITION = 0, NORMAL = 1, UV = 2, TANGENT = 3, JOINTS = 4, WEIGHTS = 5;

    private GltfGl() {}

    /** Ein Teil auf der Grafikkarte. */
    public static final class PrimitiveGl {
        public int vao, vboPos, vboNrm, vboStatic, ibo, indexCount, vertexCount;

        /** Neue Lagen und Normalen (je 3 Werte je Ecke) hochladen. */
        public void update(GL4 gl, float[] pos, float[] nrm) {
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vboPos);
            gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, (long) pos.length * 4, FloatBuffer.wrap(pos));
            if (nrm != null) {
                gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vboNrm);
                gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, (long) nrm.length * 4, FloatBuffer.wrap(nrm));
            }
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        }

        /** Neue Dreiecksliste (z. B. wenn sich die Kleidung ändert). */
        public void setIndices(GL4 gl, int[] indices) {
            gl.glBindVertexArray(vao);
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
            gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) Math.max(1, indices.length) * 4, Buffers.newDirectIntBuffer(indices.length == 0 ? new int[1] : indices), GL.GL_STATIC_DRAW);
            gl.glBindVertexArray(0);
            indexCount = indices.length;
        }

        public void draw(GL4 gl) {
            gl.glBindVertexArray(vao);
            gl.glDrawElements(GL.GL_TRIANGLES, indexCount, GL.GL_UNSIGNED_INT, 0);
            gl.glBindVertexArray(0);
        }

        public void dispose(GL4 gl) {
            gl.glDeleteBuffers(4, new int[]{vboPos, vboNrm, vboStatic, ibo}, 0);
            gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        }
    }

    public static PrimitiveGl upload(GL4 gl, Gltf.Primitive p) {
        return upload(gl, p, p.indices);
    }

    /** Wie {@link #upload(GL4, Gltf.Primitive)}, aber mit eigener Dreiecksliste (z. B. ohne verdeckte Dreiecke). */
    public static PrimitiveGl upload(GL4 gl, Gltf.Primitive p, int[] indices) {
        PrimitiveGl r = new PrimitiveGl();
        int n = p.vertexCount;
        r.vertexCount = n;
        int[] a = new int[1];
        gl.glGenVertexArrays(1, a, 0); r.vao = a[0];
        gl.glBindVertexArray(r.vao);

        r.vboPos = buffer(gl, p.position, GL2ES2.GL_DYNAMIC_DRAW);
        gl.glEnableVertexAttribArray(POSITION);
        gl.glVertexAttribPointer(POSITION, 3, GL.GL_FLOAT, false, 0, 0);

        float[] nrm = p.normal != null ? p.normal : new float[n * 3];
        r.vboNrm = buffer(gl, nrm, GL2ES2.GL_DYNAMIC_DRAW);
        gl.glEnableVertexAttribArray(NORMAL);
        gl.glVertexAttribPointer(NORMAL, 3, GL.GL_FLOAT, false, 0, 0);

        // feste Daten verschachtelt: uv(2) tangente(4) gewichte(4) gelenke(4 int) = 56 Byte
        int stride = 56;
        ByteBuffer sb = Buffers.newDirectByteBuffer(n * stride);
        for (int i = 0; i < n; i++) {
            sb.putFloat(p.uv0 != null ? p.uv0[i * 2] : 0).putFloat(p.uv0 != null ? p.uv0[i * 2 + 1] : 0);
            for (int c = 0; c < 4; c++) sb.putFloat(p.tangent != null ? p.tangent[i * 4 + c] : (c == 0 || c == 3 ? 1 : 0));
            for (int c = 0; c < 4; c++) sb.putFloat(p.weights != null ? p.weights[i * 4 + c] : (c == 0 ? 1 : 0));
            for (int c = 0; c < 4; c++) sb.putInt(p.joints != null ? p.joints[i * 4 + c] : 0);
        }
        sb.flip();
        gl.glGenBuffers(1, a, 0); r.vboStatic = a[0];
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, r.vboStatic);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, sb.limit(), sb, GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(UV);
        gl.glVertexAttribPointer(UV, 2, GL.GL_FLOAT, false, stride, 0);
        gl.glEnableVertexAttribArray(TANGENT);
        gl.glVertexAttribPointer(TANGENT, 4, GL.GL_FLOAT, false, stride, 8);
        gl.glEnableVertexAttribArray(WEIGHTS);
        gl.glVertexAttribPointer(WEIGHTS, 4, GL.GL_FLOAT, false, stride, 24);
        gl.glEnableVertexAttribArray(JOINTS);
        gl.glVertexAttribIPointer(JOINTS, 4, GL2ES2.GL_INT, stride, 40);

        IntBuffer ib = Buffers.newDirectIntBuffer(indices);
        gl.glGenBuffers(1, a, 0); r.ibo = a[0];
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, r.ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) indices.length * 4, ib, GL.GL_STATIC_DRAW);
        r.indexCount = indices.length;
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        return r;
    }

    private static int buffer(GL4 gl, float[] d, int usage) {
        int[] a = new int[1];
        gl.glGenBuffers(1, a, 0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, a[0]);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) d.length * 4, FloatBuffer.wrap(d), usage);
        return a[0];
    }

    /**
     * Ein Bild als Textur mit Mipmaps. {@code srgb}: Farbbilder (Grundfarbe) in sRGB, Daten (Normalen) linear.
     * Gibt 0 zurück, wenn das Bild nicht lesbar ist.
     */
    public static int texture(GL4 gl, Gltf.Image im, Gltf.Texture t, boolean srgb) {
        BufferedImage img;
        try {
            img = ImageIO.read(new ByteArrayInputStream(im.data));
        } catch (IOException e) {
            return 0;
        }
        if (img == null) return 0;
        int w = img.getWidth(), h = img.getHeight();
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        ByteBuffer bb = Buffers.newDirectByteBuffer(w * h * 4);
        // glTF: UV (0,0) ist oben links, also Zeilen wie im Bild
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int c = px[y * w + x];
                bb.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) (c >>> 24));
            }
        bb.flip();
        int[] a = new int[1];
        gl.glGenTextures(1, a, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, a[0]);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, srgb ? GL.GL_SRGB8_ALPHA8 : GL.GL_RGBA8, w, h, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        int wrapS = t == null ? GL.GL_REPEAT : t.wrapS, wrapT = t == null ? GL.GL_REPEAT : t.wrapT;
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, wrapS);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, wrapT);
        gl.glTexParameterf(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAX_ANISOTROPY_EXT, 8f);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return a[0];
    }

    /** Hilfe für Rückfragen in Tests: Fehlerzustand von GL als Text oder null. */
    public static String error(GL4 gl) {
        int e = gl.glGetError();
        return e == GL.GL_NO_ERROR ? null : "GL-Fehler 0x" + Integer.toHexString(e);
    }
}
