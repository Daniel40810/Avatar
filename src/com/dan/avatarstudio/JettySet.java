package com.dan.avatarstudio;

import com.dan.avatar.gl.AvatarGl;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Demo-Szene „Steg“ (Phase 13): ein hölzerner Steg im Regen, 14 m lang und 2 m breit, auf Pfählen über
 * schwarzem Wasser, vom Kai aus; vier Laternen an Pfosten, eine türkise Boje draußen, Dunst. Sie ist eine
 * {@link com.dan.avatar.World}: Boden auf 0 (Planken und Kai), Orte für die Regie (Ufer, Steg, Steg_Mitte,
 * Steg_Ende, Wasser, Boje, Laterne), Kamera. Das Licht der Person kommt aus den Laternen: das Führungslicht
 * ist die nach Nähe gewichtete Laterne, das Kantenlicht türkis von draußen (Boje, Stadt).
 *
 * <p>Koordinaten: der Steg läuft entlang +z vom Kai (z = −10) zum Ende (z = +4), x quer, Wasser auf
 * {@link #WATER_Y}. Werte mit [G] sind gesetzt.
 */
public final class JettySet implements com.dan.avatar.World {

    public static final float WATER_Y = -0.55f, Z0 = -10f, Z1 = 4f, HALF_W = 1.0f;
    /** Laternen: Lage des Glases {x, y, z} und Farbe (linear, W/sr-ähnlich wie das Studio) [G]. */
    public final float[][] lampPos = {
            {-1.02f, 2.30f, -8.6f}, {1.02f, 2.30f, -4.4f}, {-1.02f, 2.30f, -0.2f}, {1.02f, 2.30f, 3.6f},
            {6.5f, 0.55f, 17f}};
    public final float[][] lampCol = {
            {4.6f, 2.8f, 1.25f}, {4.6f, 2.8f, 1.25f}, {4.6f, 2.8f, 1.25f}, {4.6f, 2.8f, 1.25f},
            {0.30f, 1.9f, 2.1f}};
    /** Wie viele der Lichter Laternen am Steg sind (der Rest: Boje). */
    public static final int LANTERNS = 4;
    /** Himmel, Mond und Stadt über dem Wasser (Aufhellung) und Dunst. */
    public final float[] fill = {0.016f, 0.022f, 0.032f}, fog = {0.007f, 0.010f, 0.015f};
    /** Anteil des Laternenlichts für die Person (die Haut ist heller als das nasse Holz) [G]. */
    public double personGain = 0.62;

    private final Map<String, double[]> places = new LinkedHashMap<>();
    private int prog, vao, vbo, count;
    private String error;

    public JettySet() {
        places.put("Ufer", new double[]{0.3, 0, -13.5});
        places.put("Kai", new double[]{-2.5, 0, -12});
        places.put("Steg", new double[]{0, 0, -8.5});
        places.put("Steg_Mitte", new double[]{0, 0, -3});
        places.put("Steg_Ende", new double[]{0, 0, 2.6});
        places.put("Wasser", new double[]{1.2, WATER_Y, 9});
        places.put("Boje", new double[]{lampPos[4][0], lampPos[4][1], lampPos[4][2]});
        places.put("Laterne", new double[]{lampPos[3][0], lampPos[3][1], lampPos[3][2]});
    }

    // ------------------------------------------------------------ World

    @Override public double ground(double x, double z) { return 0; }

    @Override public double[] place(String name) { double[] p = places.get(name); return p == null ? null : p.clone(); }

    @Override public List<String> places() { return new ArrayList<>(places.keySet()); }

    /** Kamera der Totalen: vom Wasser vor dem Steg zum Ufer hin {tx, ty, tz, Gier°, Neigung°, Abstand, Bildwinkel°}. */
    public double[] totale() { return new double[]{0.0, 1.0, -5.5, 20, 4, 14.5, 38}; }

    // ------------------------------------------------------------ Licht der Person

    /**
     * Licht der Person mit dem Kopf bei {@code head} und der Kamera bei {@code eye}: Führungslicht aus den
     * Laternen (Lage nach 1/d⁴ gewichtet, Stärke so, dass es am Kopf so hell ist wie alle zusammen), weit
     * offen (Laternen strahlen rundum); Kantenlicht türkis von draußen, hinter der Person aus Sicht der
     * Kamera; Aufhellung wie der Himmel. Liefert die Laterne mit dem meisten Licht (für den Schatten).
     */
    public int light(AvatarGl.Light l, double[] head, double[] eye) {
        double sw = 0, px = 0, py = 0, pz = 0;
        double[] E = new double[3];
        int best = -1;
        double bestE = 0;
        for (int i = 0; i < LANTERNS; i++) {
            float[] p = lampPos[i];
            double dx = p[0] - head[0], dy = p[1] - head[1], dz = p[2] - head[2];
            double d2 = Math.max(0.25, dx * dx + dy * dy + dz * dz);
            double w = 1 / (d2 * d2);
            sw += w; px += w * p[0]; py += w * p[1]; pz += w * p[2];
            for (int k = 0; k < 3; k++) E[k] += lampCol[i][k] * personGain / d2;
            double e = lampCol[i][0] / d2;
            if (e > bestE) { bestE = e; best = i; }
        }
        px /= sw; py /= sw; pz /= sw;
        double kd2 = (px - head[0]) * (px - head[0]) + (py - head[1]) * (py - head[1]) + (pz - head[2]) * (pz - head[2]);
        l.keyPos = new float[]{(float) px, (float) py, (float) pz};
        l.keyDir = new float[]{(float) (head[0] - px), (float) (head[1] - 0.25 - py), (float) (head[2] - pz)};
        l.keyCol = new float[]{(float) (E[0] * kd2), (float) (E[1] * kd2), (float) (E[2] * kd2)};
        l.keyConeInner = 45; l.keyConeOuter = 85;
        // Kante: von draußen, gegenüber der Kamera, etwas seitlich und oben [G]
        double bx = head[0] - eye[0], bz = head[2] - eye[2], bl = Math.max(1e-6, Math.hypot(bx, bz));
        bx /= bl; bz /= bl;
        double rx = head[0] + bx * 3.2 - bz * 1.4, rz = head[2] + bz * 3.2 + bx * 1.4;
        l.rimPos = new float[]{(float) rx, (float) (head[1] + 1.1), (float) rz};
        l.rimCol = new float[]{0.35f, 1.55f, 1.75f};
        l.fillCol = fill.clone();
        return best;
    }

    // ------------------------------------------------------------ GL

    public String error() { return error; }

    /** Schon angelegt (oder mit Fehler)? */
    public boolean ready() { return prog != 0 || error != null; }

    public void init(GL4 gl) {
        try { prog = StudioScene.program(gl, "stage.vert", "steg.frag"); }
        catch (Exception e) { error = "Steg: " + e.getMessage(); return; }
        List<float[]> v = new ArrayList<>();
        build(v);
        count = v.size();
        FloatBuffer fb = Buffers.newDirectFloatBuffer(count * 10);
        for (float[] e : v) fb.put(e);
        fb.flip();
        int[] a = new int[1];
        gl.glGenVertexArrays(1, a, 0); vao = a[0];
        gl.glGenBuffers(1, a, 0); vbo = a[0];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) fb.limit() * 4, fb, GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0); gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 40, 0);
        gl.glEnableVertexAttribArray(1); gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 40, 12);
        gl.glEnableVertexAttribArray(2); gl.glVertexAttribPointer(2, 4, GL.GL_FLOAT, false, 40, 24);
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    /** Zeichnen ins HDR-Ziel (Tiefe an). {@code keyLamp}: Laterne mit dem Schatten der Person (oder −1). */
    public void draw(GL4 gl, float[] vp, double[] eye, double time, double rain, com.dan.avatar.gl.ShadowMap shadow, int keyLamp) {
        if (prog == 0) return;
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uViewProj"), 1, false, vp, 0);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uEye"), (float) eye[0], (float) eye[1], (float) eye[2]);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uTime"), (float) time);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uLampN"), lampPos.length);
        float[] lp = new float[lampPos.length * 3], lc = new float[lampPos.length * 3];
        for (int i = 0; i < lampPos.length; i++) for (int k = 0; k < 3; k++) { lp[i * 3 + k] = lampPos[i][k]; lc[i * 3 + k] = lampCol[i][k]; }
        gl.glUniform3fv(gl.glGetUniformLocation(prog, "uLampPos"), lampPos.length, lp, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(prog, "uLampCol"), lampPos.length, lc, 0);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uKeyLamp"), shadow != null && shadow.valid ? keyLamp : -1);
        gl.glUniform3fv(gl.glGetUniformLocation(prog, "uFillCol"), 1, fill, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(prog, "uFogCol"), 1, fog, 0);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uRimPos"), lampPos[4][0], lampPos[4][1] + 6, lampPos[4][2] + 20);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uRimCol"), 40f, 170f, 190f);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uRain"), (float) rain);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uWaterY"), WATER_Y);
        if (shadow != null) shadow.bind(gl, prog, 1);
        else gl.glUniform1i(gl.glGetUniformLocation(prog, "uShadowOn"), 0);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, count);
        gl.glBindVertexArray(0);
    }

    public void dispose(GL4 gl) {
        if (vao != 0) { gl.glDeleteVertexArrays(1, new int[]{vao}, 0); gl.glDeleteBuffers(1, new int[]{vbo}, 0); vao = vbo = 0; }
        if (prog != 0) { gl.glDeleteProgram(prog); prog = 0; }
    }

    // ------------------------------------------------------------ Geometrie

    /** Dreiecke {x, y, z, nx, ny, nz, r, g, b, a}. */
    void build(List<float[]> v) {
        java.util.Random rnd = new java.util.Random(4711);
        float[] water = {0.004f, 0.006f, 0.008f, 0.6f};
        // Wasser: große Fläche in Ringen (fein nah am Steg, damit die Spiegel nicht in langen Dreiecken liegen)
        float wy = WATER_Y;
        float[] xs = {-400, -60, -20, -8, -3, 3, 8, 20, 60, 400};
        float[] zs = {-11, -6, -1, 4, 9, 16, 30, 60, 150, 400};
        for (int i = 0; i + 1 < xs.length; i++)
            for (int j = 0; j + 1 < zs.length; j++)
                quadUp(v, xs[i], zs[j], xs[i + 1], zs[j + 1], wy, water);
        // Kai: Steinfläche und Mauer zum Wasser
        float[] stone = {0.080f, 0.078f, 0.074f, 0.3f};
        box(v, -40, -0.6f, -60, 40, 0.0f, Z0 - 0.6f, stone);
        // Steg: Planken quer, 14 cm breit, 5 cm dick, mit Fugen
        for (float z = Z0 - 0.6f; z < Z1; z += 0.145f) {
            float sh = 0.85f + 0.3f * rnd.nextFloat();
            float[] wood = {0.130f * sh, 0.092f * sh, 0.066f * sh, 0.0f};
            box(v, -HALF_W, -0.05f, z, HALF_W, 0.0f, z + 0.135f, wood);
        }
        // Längsträger und Pfähle (rund durch acht Seiten), bis unter das Wasser
        float[] beam = {0.060f, 0.045f, 0.034f, 0.0f};
        box(v, -HALF_W + 0.05f, -0.22f, Z0 - 0.6f, -HALF_W + 0.17f, -0.05f, Z1, beam);
        box(v, HALF_W - 0.17f, -0.22f, Z0 - 0.6f, HALF_W - 0.05f, -0.05f, Z1, beam);
        float[] pile = {0.055f, 0.044f, 0.035f, 0.0f};
        for (float z = Z0 + 0.2f; z <= Z1 + 0.01f; z += 2.3f)
            for (int s = -1; s <= 1; s += 2) cylinder(v, s * (HALF_W + 0.07f), z, 0.11f, -2.0f, 0.32f, pile);
        // Laternen: Pfosten, Arm, Gehäuse, Glas (leuchtet)
        float[] iron = {0.012f, 0.012f, 0.013f, 0.0f};
        for (int i = 0; i < LANTERNS; i++) {
            float[] p = lampPos[i];
            float sx = Math.signum(p[0]);
            float bx = p[0] + sx * 0.06f;
            box(v, bx - 0.035f, 0.0f, p[2] - 0.035f, bx + 0.035f, p[1] - 0.14f, p[2] + 0.035f, iron);
            box(v, p[0] - 0.12f, p[1] - 0.15f, p[2] - 0.12f, p[0] + 0.12f, p[1] - 0.11f, p[2] + 0.12f, iron);
            box(v, p[0] - 0.13f, p[1] + 0.13f, p[2] - 0.13f, p[0] + 0.13f, p[1] + 0.18f, p[2] + 0.13f, iron);
            float[] glass = {lampCol[i][0] * 4f, lampCol[i][1] * 4f, lampCol[i][2] * 4f, 1f};
            box(v, p[0] - 0.09f, p[1] - 0.11f, p[2] - 0.09f, p[0] + 0.09f, p[1] + 0.13f, p[2] + 0.09f, glass);
        }
        // Boje: Körper und Licht
        float[] b = lampPos[4];
        cylinder(v, b[0], b[2], 0.28f, WATER_Y - 0.2f, WATER_Y + 0.35f, new float[]{0.05f, 0.015f, 0.012f, 0.3f});
        float[] bl = {lampCol[4][0] * 3, lampCol[4][1] * 3, lampCol[4][2] * 3, 1f};
        box(v, b[0] - 0.07f, b[1] - 0.08f, b[2] - 0.07f, b[0] + 0.07f, b[1] + 0.08f, b[2] + 0.07f, bl);
        // Poller am Ende (zum Anlehnen, später zum Sitzen)
        cylinder(v, 0.62f, Z1 - 0.35f, 0.10f, 0.0f, 0.45f, iron);
    }

    private static void quadUp(List<float[]> v, float x0, float z0, float x1, float z1, float y, float[] c) {
        v.add(new float[]{x0, y, z0, 0, 1, 0, c[0], c[1], c[2], c[3]});
        v.add(new float[]{x1, y, z1, 0, 1, 0, c[0], c[1], c[2], c[3]});
        v.add(new float[]{x1, y, z0, 0, 1, 0, c[0], c[1], c[2], c[3]});
        v.add(new float[]{x0, y, z0, 0, 1, 0, c[0], c[1], c[2], c[3]});
        v.add(new float[]{x0, y, z1, 0, 1, 0, c[0], c[1], c[2], c[3]});
        v.add(new float[]{x1, y, z1, 0, 1, 0, c[0], c[1], c[2], c[3]});
    }

    private static void box(List<float[]> v, float x0, float y0, float z0, float x1, float y1, float z1, float[] col) {
        float[][] c = {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
        int[][] f = {{4, 5, 6, 7}, {1, 0, 3, 2}, {5, 1, 2, 6}, {0, 4, 7, 3}, {7, 6, 2, 3}, {0, 1, 5, 4}};
        float[][] n = {{0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
        for (int i = 0; i < 6; i++) {
            float[] a = c[f[i][0]], b = c[f[i][1]], cc = c[f[i][2]], d = c[f[i][3]];
            for (float[] p : new float[][]{a, b, cc, a, cc, d})
                v.add(new float[]{p[0], p[1], p[2], n[i][0], n[i][1], n[i][2], col[0], col[1], col[2], col[3]});
        }
    }

    /** Senkrechter Zylinder (12 Seiten, mit Deckel) um (x, z) mit Radius r von y0 bis y1. */
    private static void cylinder(List<float[]> v, float x, float z, float r, float y0, float y1, float[] col) {
        int n = 12;
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n, am = (a0 + a1) / 2;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float nx = (float) Math.cos(am), nz = (float) Math.sin(am);
            float[][] q = {{x + r * c0, y0, z + r * s0}, {x + r * c1, y0, z + r * s1}, {x + r * c1, y1, z + r * s1}, {x + r * c0, y1, z + r * s0}};
            for (int k : new int[]{0, 2, 1, 0, 3, 2})
                v.add(new float[]{q[k][0], q[k][1], q[k][2], nx, 0, nz, col[0], col[1], col[2], col[3]});
            for (float[] p : new float[][]{{x, y1, z}, {x + r * c1, y1, z + r * s1}, {x + r * c0, y1, z + r * s0}})
                v.add(new float[]{p[0], p[1], p[2], 0, 1, 0, col[0], col[1], col[2], col[3]});
        }
    }
}
