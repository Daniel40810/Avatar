package com.dan.avatar.gl;

import com.dan.avatar.Body;
import com.dan.avatar.EyeRig;
import com.dan.avatar.Person;
import com.dan.avatar.Skin;
import com.dan.avatar.SkinDetail;
import com.dan.avatar.SkinMasks;
import com.dan.gltf.Gltf;
import com.dan.gltf.gl.GltfGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL4;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Zeichnet einen {@link Body}: Morphs, Normalen und Skinning laufen in Compute-Shadern auf der
 * Grafikkarte (F7), gleiche Rechnung wie {@code Body.mix} und {@code Skinning} in Java. Seit Phase 4
 * mit Haut (F6, F8): Grundhäute nach Alter und Geschlecht, Ton aus Melanin und Rötung, Detailkarten,
 * zwei Glanzlagen, Schatten, Durchleuchtung; das diffuse Hautlicht geht auf Wunsch in ein zweites
 * Ziel für {@link SkinScatter}. Seit Phase 5 die Augen; das Haar zeichnet seit Phase 6 ein fremder Zeichner
 * (siehe {@link #external}), dieses Paket kennt com.dan.hair nicht.
 *
 * <pre>
 *   AvatarGl g = new AvatarGl(); g.init(gl, body);
 *   g.setWeights(gl, person.weights());          // Morphs + Normalen, nur bei Änderung
 *   g.setPerson(person);                          // Haut
 *   g.setSkin(gl, dq, mat, true);                 // Skinning, wenn sich die Pose ändert
 *   g.drawDepth(gl, shadowMap.viewProj, x, y, z); // in die Schattenkarte
 *   g.draw(gl, viewProj, eye, light, shading, x, y, z);
 * </pre>
 */
public final class AvatarGl {

    /** Licht der Szene, linear und vorbelichtet. */
    public static final class Light {
        public float[] keyPos = {-1.6f, 3.4f, 2.2f}, keyDir = {1.6f, -2.6f, -2.2f}, keyCol = {19f, 12.5f, 7f};
        public float keyConeInner = 14, keyConeOuter = 30;
        public float[] rimPos = {1.9f, 2.6f, -2.4f}, rimCol = {3.0f, 9.5f, 10.5f};
        public float[] fillCol = {0.020f, 0.030f, 0.040f};
        /** Helligkeit aller drei Lichter (1 = wie gesetzt); für Dämmerung und Pupille. */
        public float gain = 1;

        /** Farbe mal Helligkeit. */
        public float[] g(float[] c) { return new float[]{c[0] * gain, c[1] * gain, c[2] * gain}; }

        public Light copy() {
            Light l = new Light();
            l.keyPos = keyPos.clone(); l.keyDir = keyDir.clone(); l.keyCol = keyCol.clone();
            l.keyConeInner = keyConeInner; l.keyConeOuter = keyConeOuter;
            l.rimPos = rimPos.clone(); l.rimCol = rimCol.clone(); l.fillCol = fillCol.clone(); l.gain = gain;
            return l;
        }
    }

    /** Wie gezeichnet wird (je Bild, von der Szene): Schatten, Ziele, Stufe. */
    public static final class Shading {
        /** Schattenkarte des Führungslichts oder null. */
        public ShadowMap shadow;
        /** Diffuses Hautlicht in Ziel 1 (für {@link SkinScatter}). */
        public boolean split;
        /** „Niedrig“: vorab integrierte Streuung statt Bildraum. */
        public boolean low;
        /** Durchleuchtung: Maßstab der Dicke (kleiner = durchscheinender) und Stärke. */
        public float transScale = 0.2f, transStrength = 3.0f;
        /** Kacheln der Detailkarten je UV-Einheit. */
        public float tile = 100f;
    }

    private Body body;
    private int prog;
    private final List<GltfGl.PrimitiveGl> prims = new ArrayList<>();
    private final Map<Integer, Integer> textures = new HashMap<>();
    private String error;
    private int cMorph, cTri, cVnorm, cSkin;
    private final List<Gpu> gpu = new ArrayList<>();
    private final Map<String, Integer> targetIndex = new java.util.LinkedHashMap<>();
    private int wgtBuf, dqBuf, matBuf, maskBuf, progDepth;
    private final List<Integer> extraBufs = new ArrayList<>();
    private EyeRig eyes;
    /** Augen in Weltlage relativ zur Person (wie die Ecken): Mitte, Achsen, Radius je Auge. */
    private final float[] eyeC = new float[6], eyeX = new float[6], eyeY = new float[6], eyeZ = new float[6], eyeR = {0.015f, 0.015f};
    private final float[] iris = {0.5f, 0.35f, 0f, 0f};
    private float mouthOpen;
    private SkinGl skin;
    private int bodyIndex = -1;
    /** Haut-Uniforms der Person. */
    private final float[] tone = {0.62f, 0.43f, 0.33f}, texW = new float[6], skinP = new float[4], skinQ = new float[4], seedOfs = new float[2];
    /** Gelenke im Skin (für die Größe der Puffer). */
    private int joints;
    /** Netze, die nicht gezeichnet werden (nach Namen, z. B. die Frisur für eine Formtafel). */
    public final java.util.Set<String> hidden = new java.util.HashSet<>();

    // ------------------------------------------------------------ Umwelt (Phase 8)
    /** Nässe 0…1: Gesicht, Haar und Kopfhaut, Oberkörper (Stoff, Hals, Hände), Beine. */
    public final float[] wet = new float[4];
    /** Richtung, aus der der Regen kommt (Einheit, Figur-Koordinaten). */
    public float[] rainFrom = {0, 1, 0};
    /** Kälte 0…1: Röte an Nase, Wangen, Ohren; Lippen bläulich. */
    public float cold;
    /** Höhe von Hals und Hüfte in Figur-Koordinaten (Zonen der Nässe). */
    public float neckY = 1.45f, hipY = 0.95f;
    /** Tropfenkarte (oder null) und ihr UV-Ausschnitt {u0, v0, u1, v1}. */
    public DropMapGl drops;
    /** Lichter der Szene für Spiegelungen (Phase 9): Anzahl, Richtung vom Kopf (Welt), Leuchtdichte, cos des Winkelhalbmessers. */
    public int envN;
    public final float[] envDir = new float[48], envCol = new float[48], envCos = new float[16];
    public float[] dropRect;
    /** Vor {@link #init}: alle Dreiecke hochladen, auch die unter der Kleidung (für Ansichten ohne Kleidung). */
    public boolean allTriangles;

    // ------------------------------------------------------------ Kleidung (Phase 10)
    /** Getragene Teile als Bits ({@link com.dan.avatar.Wardrobe}); Körperdreiecke darunter werden nicht gezeichnet. */
    public int worn = 1;
    /** Stoff je Teil: {rau, Flaum, Muster, Maßstab, saugt, Perlen, Wasserglanz, Farbe gesetzt, r, g, b}. */
    private final Map<Integer, float[]> fabric = new HashMap<>();
    /** Lose je Ecke (Puffer) für die Nachbewegung, je Teil. */
    private final Map<Integer, Integer> flexBuf = new HashMap<>();
    private int flexDummy;
    /** Nachbewegung: Auslenkung und Flattern (m, Figur-Koordinaten), Zeit (s). */
    public final float[] sway = new float[3], flutter = new float[3];
    public float swayTime;
    /** Rasterblende der Detailstufen (Phase 12): sichtbar im Fenster [lo, hi), {0, 1} = ganz. */
    public final float[] dither = {0, 1};
    /** Zeit der letzten Compute-Durchgänge in ms (Prozessorseite, ohne Warten). */
    public double lastMorphMs, lastSkinMs;

    /** Puffer eines Teils für die Compute-Shader. */
    /**
     * Teile, die gerade nicht gebraucht werden (z. B. Frisuren außer der aktiven): kein Morph und kein
     * Skinning, bis sie wieder gebraucht werden (dann einmal nachgeholt). Spart in jedem Bild Rechenzeit,
     * seit das Verhalten (Phase 7) Gesichtsformen in jedem Bild ändert.
     */
    public final java.util.Set<Integer> idle = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private float[] lastW;
    private boolean[] morphStale, skinStale, staticStale;

    private static final class Gpu {
        /** Ziele des Teils: fest (Körper, Gesichtsregler) und beweglich (Gesichtsformen, Viseme). */
        int[] targetsS = new int[0], targetsD = new int[0];
        int n, tris, base, baseN, restS, sOff, sTid, sDlt, off, tid, dlt, rest, restN, idx, triN, weld, gOff, gTri, jn, wt;
    }

    public String error() { return error; }

    public void init(GL4 gl, Body b) {
        body = b;
        try {
            prog = program(gl, "avatar.vert", "avatar.frag");
            progDepth = program(gl, "avatar.vert", "depth.frag");
        } catch (IOException e) {
            error = e.getMessage();
            return;
        }
        try {
            cMorph = compute(gl, "morph.comp");
            cTri = compute(gl, "trinorm.comp");
            cVnorm = compute(gl, "vnorm.comp");
            cSkin = compute(gl, "skin.comp");
        } catch (IOException e) {
            error = e.getMessage();
            return;
        }
        Gltf m = b.model;
        for (Body.Part p : b.parts)
            for (Gltf.Target t : p.prim.targets) targetIndex.putIfAbsent(t.name, targetIndex.size());
        joints = m.skins.isEmpty() ? 1 : m.skins.get(0).joints.length;
        wgtBuf = ssbo(gl, new float[Math.max(1, targetIndex.size())]);
        dqBuf = ssbo(gl, identityDq(joints));
        matBuf = ssbo(gl, identityMat(joints));
        eyes = new EyeRig(b);
        for (int pi = 0; pi < b.parts.size(); pi++) {
            Body.Part p = b.parts.get(pi);
            int[] ix = allTriangles ? p.prim.indices : p.visibleIndices(worn);
            if (pi == eyes.part) ix = withoutCornea(p.prim.indices, eyes.cornea);
            prims.add(GltfGl.upload(gl, p.prim, ix));
            gpu.add(buffers(gl, p));
            int mi = p.prim.material;
            if (mi >= 0) {
                int ti = m.materials.get(mi).baseColorTexture;
                if (ti >= 0 && !textures.containsKey(ti)) {
                    Gltf.Texture t = m.textures.get(ti);
                    textures.put(ti, t.source >= 0 ? GltfGl.texture(gl, m.images.get(t.source), t, true) : 0);
                }
            }
        }
        // Hautzonen je Ecke des Körpers (Attribut 6 und 7)
        for (int i = 0; i < b.parts.size(); i++) if (b.parts.get(i).isBody) bodyIndex = i;
        if (bodyIndex >= 0) {
            SkinMasks sm = SkinMasks.of(b);
            int[] a = new int[1];
            gl.glGenBuffers(1, a, 0);
            maskBuf = a[0];
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, maskBuf);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) sm.data.length * 4, java.nio.FloatBuffer.wrap(sm.data), GL.GL_STATIC_DRAW);
            gl.glBindVertexArray(prims.get(bodyIndex).vao);
            gl.glEnableVertexAttribArray(6);
            gl.glVertexAttribPointer(6, 4, GL.GL_FLOAT, false, 32, 0);
            gl.glEnableVertexAttribArray(7);
            gl.glVertexAttribPointer(7, 4, GL.GL_FLOAT, false, 32, 16);
            gl.glBindVertexArray(0);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
            attrib8(gl, bodyIndex, sm.extra);
        }
        // Zähne und Zunge: Tiefe hinter der Lippenfront (Attribut 8)
        if (bodyIndex >= 0) {
            SkinMasks sm = SkinMasks.of(b);
            for (int i = 0; i < b.parts.size(); i++) {
                if (kind(b.parts.get(i)) != Kind.MOUTH) continue;
                float[] P = b.parts.get(i).prim.position;
                int n = b.parts.get(i).prim.vertexCount;
                float[] ex = new float[n * 4];
                for (int v = 0; v < n; v++)
                    ex[v * 4 + 1] = (float) Math.max(0, Math.min(1, (sm.mouth[2] - 0.003 - P[v * 3 + 2]) / 0.035));
                attrib8(gl, i, ex);
            }
        }
        skin = SkinGl.acquire(gl, b);
        setPerson(new Person());
        setWeights(gl, Map.of("macro.basis", -1.0, "macro.w.jung", 0.5, "macro.m.jung", 0.5));
        setSkin(gl, null, null, true);
    }

    /** Dreiecke des Augennetzes ohne die Hornhaut-Schale (das Auge rechnet der Shader). */
    private static int[] withoutCornea(int[] ix, boolean[] cornea) {
        int[] out = new int[ix.length];
        int n = 0;
        for (int k = 0; k + 2 < ix.length; k += 3)
            if (!cornea[ix[k]] && !cornea[ix[k + 1]] && !cornea[ix[k + 2]]) {
                out[n++] = ix[k]; out[n++] = ix[k + 1]; out[n++] = ix[k + 2];
            }
        return java.util.Arrays.copyOf(out, n);
    }

    /** Vier Werte je Ecke als Attribut 8 an das VAO eines Teils; gibt den Puffer zurück. */
    public int attrib8(GL4 gl, int part, float[] data) {
        int[] a = new int[1];
        gl.glGenBuffers(1, a, 0);
        extraBufs.add(a[0]);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, a[0]);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) data.length * 4, java.nio.FloatBuffer.wrap(data), GL.GL_STATIC_DRAW);
        gl.glBindVertexArray(prims.get(part).vao);
        gl.glEnableVertexAttribArray(8);
        gl.glVertexAttribPointer(8, 4, GL.GL_FLOAT, false, 16, 0);
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        return a[0];
    }

    // ------------------------------------------------------------ für fremde Zeichner (Haare, Phase 6)

    /** Teile, die ein anderer zeichnet (z. B. Haarkarten aus com.dan.hair); hier übersprungen. */
    public final java.util.Set<Integer> external = new java.util.HashSet<>();

    /** Anzahl der Teile (wie {@code body.parts}). */
    public int partCount() { return prims.size(); }

    /** Ist Teil {@code i} ein Haar (Frisur, Brauen, Wimpern)? */
    public boolean isHair(int i) { return kind(body.parts.get(i)) == Kind.HAIR; }

    /** Zeichenpuffer eines Teils: VAO, verformte Lagen und Normalen (je 3 floats), Indexzahl. */
    public GltfGl.PrimitiveGl primGl(int i) { return prims.get(i); }

    /** Grundfarbtextur eines Teils oder 0. */
    public int baseTexture(int i) {
        int mi = body.parts.get(i).prim.material;
        if (mi < 0) return 0;
        Integer t = textures.get(body.model.materials.get(mi).baseColorTexture);
        return t == null ? 0 : t;
    }

    /** Die Augen dieses Körpers (für {@link #setEyes}). */
    public EyeRig eyeRig() { return eyes; }

    /** Lage der Augen (aus {@link EyeRig#frames}), Koordinaten wie die Ecken (ohne Standort der Person). */
    public void setEyes(EyeRig.Frame[] f) {
        for (int e = 0; e < 2; e++) {
            for (int k = 0; k < 3; k++) {
                eyeC[e * 3 + k] = (float) f[e].c[k]; eyeX[e * 3 + k] = (float) f[e].x[k];
                eyeY[e * 3 + k] = (float) f[e].y[k]; eyeZ[e * 3 + k] = (float) f[e].z[k];
            }
            eyeR[e] = (float) f[e].r;
        }
    }

    /** Pupille als Anteil am Irisradius (0,2 eng … 0,6 weit). */
    public void setPupil(double frac) { iris[1] = (float) frac; }

    private Gpu buffers(GL4 gl, Body.Part p) {
        Gltf.Primitive pr = p.prim;
        Gpu g = new Gpu();
        g.n = pr.vertexCount;
        g.tris = pr.indices.length / 3;
        g.base = ssbo(gl, pr.position);
        g.baseN = ssbo(gl, pr.normal != null ? pr.normal : new float[g.n * 3]);
        // Morph-Einträge je Ecke, nach Ziel geordnet; getrennt in feste Ziele (ändern sich mit der Person)
        // und bewegliche (Gesichtsformen und Viseme, ändern sich in jedem Bild). Die beweglichen stehen
        // im Modell hinter den festen, die Summe läuft also in derselben Reihenfolge wie Body.mix.
        Object[] st = csr(pr, false), dy = csr(pr, true);
        g.sOff = ssbo(gl, (int[]) st[0]); g.sTid = ssbo(gl, (int[]) st[1]); g.sDlt = ssbo(gl, (float[]) st[2]);
        g.off = ssbo(gl, (int[]) dy[0]); g.tid = ssbo(gl, (int[]) dy[1]); g.dlt = ssbo(gl, (float[]) dy[2]);
        g.targetsS = (int[]) st[3]; g.targetsD = (int[]) dy[3];
        g.restS = ssbo(gl, new float[g.n * 3]);
        g.rest = ssbo(gl, new float[g.n * 3]);
        g.restN = ssbo(gl, new float[g.n * 3]);
        g.idx = ssbo(gl, pr.indices);
        g.triN = ssbo(gl, new float[Math.max(3, g.tris * 3)]);
        // Dreiecke je Blender-Ecke (in Dreiecksreihenfolge, wie Body.normals)
        int[] weld = p.weld();
        int groups = p.groups();
        int[] gc = new int[groups + 1];
        for (int k = 0; k < g.tris * 3; k++) gc[weld[pr.indices[k]] + 1]++;
        for (int i = 0; i < groups; i++) gc[i + 1] += gc[i];
        int[] gt = new int[Math.max(1, gc[groups])];
        int[] gf = java.util.Arrays.copyOf(gc, groups);
        for (int k = 0; k < g.tris; k++)
            for (int c = 0; c < 3; c++) gt[gf[weld[pr.indices[k * 3 + c]]]++] = k;
        g.weld = ssbo(gl, weld);
        g.gOff = ssbo(gl, gc);
        g.gTri = ssbo(gl, gt);
        g.jn = ssbo(gl, pr.joints != null ? pr.joints : new int[g.n * 4]);
        float[] w = pr.weights;
        if (w == null) { w = new float[g.n * 4]; }
        g.wt = ssbo(gl, w);
        return g;
    }

    /** Ist das ein bewegliches Ziel (Gesichtsform oder Visem, ändert sich in jedem Bild)? */
    static boolean dynamic(String target) { return target.startsWith("arkit.") || target.startsWith("visem."); }

    /** Einträge je Ecke (CSR) für feste oder bewegliche Ziele: {off, tid, dlt, Zielnummern}. */
    private Object[] csr(Gltf.Primitive pr, boolean dyn) {
        int n = pr.vertexCount;
        int[] cnt = new int[n + 1];
        java.util.TreeSet<Integer> ts = new java.util.TreeSet<>();
        for (Gltf.Target t : pr.targets) {
            if (dynamic(t.name) != dyn) continue;
            for (int ix : t.index) cnt[ix + 1]++;
            if (t.index.length > 0) ts.add(targetIndex.get(t.name));
        }
        for (int i = 0; i < n; i++) cnt[i + 1] += cnt[i];
        int m = cnt[n];
        int[] tid = new int[Math.max(1, m)];
        float[] dlt = new float[Math.max(3, m * 3)];
        int[] fill = java.util.Arrays.copyOf(cnt, n);
        for (Gltf.Target t : pr.targets) {
            if (dynamic(t.name) != dyn) continue;
            int gi = targetIndex.get(t.name);
            for (int k = 0; k < t.index.length; k++) {
                int e = fill[t.index[k]]++;
                tid[e] = gi;
                dlt[e * 3] = t.dPosition[k * 3]; dlt[e * 3 + 1] = t.dPosition[k * 3 + 1]; dlt[e * 3 + 2] = t.dPosition[k * 3 + 2];
            }
        }
        return new Object[]{cnt, tid, dlt, ts.stream().mapToInt(Integer::intValue).toArray()};
    }

    /**
     * Morph-Gewichte setzen: Ruhelage und Normalen neu (Compute), nur für Teile, deren Ziele sich
     * geändert haben und die nicht {@link #idle} sind.
     */
    public void setWeights(GL4 gl, Map<String, Double> weights) {
        if (cMorph == 0) return;
        long t0 = System.nanoTime();
        float[] w = new float[Math.max(1, targetIndex.size())];
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            Integer i = targetIndex.get(e.getKey());
            if (i != null) w[i] = (float) (double) e.getValue();
        }
        if (morphStale == null) { morphStale = new boolean[gpu.size()]; java.util.Arrays.fill(morphStale, true); }
        if (skinStale == null) skinStale = new boolean[gpu.size()];
        if (staticStale == null) { staticStale = new boolean[gpu.size()]; java.util.Arrays.fill(staticStale, true); }
        boolean any = false;
        boolean[] run = new boolean[gpu.size()], runS = new boolean[gpu.size()];
        for (int i = 0; i < gpu.size(); i++) {
            Gpu g = gpu.get(i);
            boolean cs = staticStale[i] || lastW == null, cd = morphStale[i] || lastW == null;
            if (!cs) for (int t : g.targetsS) if (w[t] != lastW[t]) { cs = true; break; }
            if (!cd) for (int t : g.targetsD) if (w[t] != lastW[t]) { cd = true; break; }
            if (!cs && !cd) continue;
            if (idle.contains(i)) { morphStale[i] = true; if (cs) staticStale[i] = true; continue; }
            run[i] = true; runS[i] = cs; any = true;
        }
        lastW = w;
        if (any) upload(gl, wgtBuf, w);
        lastParts = 0;
        for (int i = 0; i < gpu.size(); i++) {
            if (!run[i]) continue;
            Gpu g = gpu.get(i);
            morphStale[i] = false;
            skinStale[i] = true;
            lastParts++;
            gl.glUseProgram(cMorph);
            if (runS[i]) {
                staticStale[i] = false;
                bind(gl, g.base, g.sOff, g.sTid, g.sDlt, wgtBuf, g.restS);
                gl.glUniform1i(gl.glGetUniformLocation(cMorph, "uCount"), g.n);
                gl.glDispatchCompute((g.n + 63) / 64, 1, 1);
                gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT);
            }
            bind(gl, g.restS, g.off, g.tid, g.dlt, wgtBuf, g.rest);
            gl.glUniform1i(gl.glGetUniformLocation(cMorph, "uCount"), g.n);
            gl.glDispatchCompute((g.n + 63) / 64, 1, 1);
            gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT);
            gl.glUseProgram(cTri);
            bind(gl, g.rest, g.idx, g.triN);
            gl.glUniform1i(gl.glGetUniformLocation(cTri, "uTris"), g.tris);
            gl.glDispatchCompute((g.tris + 63) / 64, 1, 1);
            gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT);
            gl.glUseProgram(cVnorm);
            bind(gl, g.triN, g.weld, g.gOff, g.gTri, g.baseN, g.restN);
            gl.glUniform1i(gl.glGetUniformLocation(cVnorm, "uCount"), g.n);
            gl.glDispatchCompute((g.n + 63) / 64, 1, 1);
        }
        gl.glMemoryBarrier(GL4.GL_SHADER_STORAGE_BARRIER_BIT);
        lastMorphMs = (System.nanoTime() - t0) / 1e6;
    }

    /** Gibt es Teile, die nicht (mehr) {@link #idle} sind, aber Morph oder Skinning nachholen müssen? */
    public boolean needsRefresh() {
        if (morphStale == null) return true;
        for (int i = 0; i < gpu.size(); i++)
            if (!idle.contains(i) && (morphStale[i] || (skinStale != null && skinStale[i]))) return true;
        return false;
    }

    /** Getragene Teile setzen: die Dreiecke des Körpers darunter neu hochladen. */
    public void setWorn(GL4 gl, int mask) {
        if (mask == worn || bodyIndex < 0) { worn = mask; return; }
        worn = mask;
        if (!allTriangles) prims.get(bodyIndex).setIndices(gl, body.parts.get(bodyIndex).visibleIndices(worn));
    }

    /** Eigene Dreiecksliste für ein Teil (z. B. die Kapuze ohne den Strick der Kutte). */
    public void setPartIndices(GL4 gl, int part, int[] ix) {
        if (part == bodyIndex) return;
        prims.get(part).setIndices(gl, ix);
    }

    /** Stoff eines Teils (null = wie bis Phase 9: Textur mit einfachem Licht). */
    public void setFabric(int part, com.dan.avatar.Fabric f, double[] color) {
        if (f == null) { fabric.remove(part); return; }
        double[] c = color != null ? color : f.color;
        fabric.put(part, new float[]{(float) f.rough, (float) f.sheen, f.weave, (float) f.scale, (float) f.absorb, (float) f.beading,
            (float) f.wetGloss, 1, (float) c[0], (float) c[1], (float) c[2]});
    }

    /** Lose je Ecke eines Teils (0 am Körper … 1 frei) für die Nachbewegung; null entfernt sie. */
    public void setFlex(GL4 gl, int part, float[] flex) {
        Integer old = flexBuf.remove(part);
        if (old != null) gl.glDeleteBuffers(1, new int[]{old}, 0);
        if (flex != null) flexBuf.put(part, ssbo(gl, flex));
    }

    /** Hat irgendein Teil Lose (dann wird in jedem Bild verformt)? */
    public boolean hasFlex() { return !flexBuf.isEmpty(); }

    /** Zahl der Teile, die beim letzten {@link #setWeights} oder {@link #setSkin} gerechnet wurden (Prüfung, HUD). */
    public int lastParts;

    /**
     * Skinning (Compute) in die Zeichenpuffer. {@code dq}: 8 floats je Gelenk, {@code mat}: 12 floats je
     * Gelenk (3×4); null = Ruhehaltung. {@code dualQuaternion}: Volumenerhalt, sonst linear. Teile in
     * {@link #idle} werden übersprungen und nachgeholt, sobald sie wieder gebraucht werden.
     */
    public void setSkin(GL4 gl, float[] dq, float[] mat, boolean dualQuaternion) {
        if (cSkin == 0) return;
        long t0 = System.nanoTime();
        if (skinStale == null) skinStale = new boolean[gpu.size()];
        upload(gl, dqBuf, dq != null ? dq : identityDq(joints));
        upload(gl, matBuf, mat != null ? mat : identityMat(joints));
        gl.glUseProgram(cSkin);
        gl.glUniform1i(gl.glGetUniformLocation(cSkin, "uMode"), dualQuaternion ? 1 : 0);
        gl.glUniform3fv(gl.glGetUniformLocation(cSkin, "uSway"), 1, sway, 0);
        gl.glUniform3fv(gl.glGetUniformLocation(cSkin, "uFlutter"), 1, flutter, 0);
        gl.glUniform1f(gl.glGetUniformLocation(cSkin, "uTime"), swayTime);
        if (flexDummy == 0) flexDummy = ssbo(gl, new float[1]);
        int n = 0;
        for (int i = 0; i < gpu.size(); i++) {
            if (idle.contains(i)) { skinStale[i] = true; continue; }
            if (morphStale != null && morphStale[i]) continue;   // erst nach dem Morph
            Gpu g = gpu.get(i);
            GltfGl.PrimitiveGl pg = prims.get(i);
            bind(gl, g.rest, g.restN, g.jn, g.wt, dqBuf, matBuf, pg.vboPos, pg.vboNrm);
            Integer fb = flexBuf.get(i);
            gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 8, fb != null ? fb : flexDummy);
            gl.glUniform1i(gl.glGetUniformLocation(cSkin, "uFlexOn"), fb != null ? 1 : 0);
            gl.glUniform1i(gl.glGetUniformLocation(cSkin, "uCount"), g.n);
            gl.glDispatchCompute((g.n + 63) / 64, 1, 1);
            skinStale[i] = false;
            n++;
        }
        lastParts = n;
        gl.glMemoryBarrier(GL4.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT | GL4.GL_SHADER_STORAGE_BARRIER_BIT | GL4.GL_BUFFER_UPDATE_BARRIER_BIT);
        lastSkinMs = (System.nanoTime() - t0) / 1e6;
    }

    /** Liest Lagen (oder Normalen) eines Teils von der Grafikkarte zurück (Prüfung). */
    public float[] readback(GL4 gl, int part, boolean normals, boolean skinned) {
        Gpu g = gpu.get(part);
        int buf = skinned ? (normals ? prims.get(part).vboNrm : prims.get(part).vboPos) : (normals ? g.restN : g.rest);
        java.nio.FloatBuffer fb = com.jogamp.common.nio.Buffers.newDirectFloatBuffer(g.n * 3);
        gl.glMemoryBarrier(GL4.GL_BUFFER_UPDATE_BARRIER_BIT);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, buf);
        gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, (long) g.n * 12, fb);
        float[] out = new float[g.n * 3];
        fb.get(out);
        return out;
    }

    private static float[] identityDq(int n) {
        float[] d = new float[n * 8];
        for (int j = 0; j < n; j++) d[j * 8 + 3] = 1;
        return d;
    }

    private static float[] identityMat(int n) {
        float[] d = new float[n * 12];
        for (int j = 0; j < n; j++) { d[j * 12] = 1; d[j * 12 + 4] = 1; d[j * 12 + 8] = 1; }
        return d;
    }

    private static int ssbo(GL4 gl, float[] d) {
        int[] a = new int[1];
        gl.glGenBuffers(1, a, 0);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, a[0]);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, (long) d.length * 4, java.nio.FloatBuffer.wrap(d), GL4.GL_DYNAMIC_DRAW);
        return a[0];
    }

    private static int ssbo(GL4 gl, int[] d) {
        int[] a = new int[1];
        gl.glGenBuffers(1, a, 0);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, a[0]);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, (long) d.length * 4, java.nio.IntBuffer.wrap(d), GL4.GL_STATIC_DRAW);
        return a[0];
    }

    private static void upload(GL4 gl, int buf, float[] d) {
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, buf);
        gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, (long) d.length * 4, java.nio.FloatBuffer.wrap(d));
    }

    private static void bind(GL4 gl, int... bufs) {
        for (int i = 0; i < bufs.length; i++) gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, i, bufs[i]);
    }

    private static int compute(GL4 gl, String name) throws IOException {
        int s = gl.glCreateShader(GL4.GL_COMPUTE_SHADER);
        gl.glShaderSource(s, 1, new String[]{read(name)}, null);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL2ES2.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] b = new byte[4096];
            gl.glGetShaderInfoLog(s, b.length, null, 0, b, 0);
            throw new IOException(name + ": " + new String(b, StandardCharsets.UTF_8).trim());
        }
        int p = gl.glCreateProgram();
        gl.glAttachShader(p, s);
        gl.glLinkProgram(p);
        gl.glDeleteShader(s);
        gl.glGetProgramiv(p, GL2ES2.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] b = new byte[4096];
            gl.glGetProgramInfoLog(p, b.length, null, 0, b, 0);
            throw new IOException(name + ": " + new String(b, StandardCharsets.UTF_8).trim());
        }
        return p;
    }

    /** Zonen sichtbar machen (1…8, Systemeigenschaft avatar.zone), sonst 0. */
    static final int DEBUG = Integer.getInteger("avatar.zone", 0);

    /** Hinweis zu den Grundhäuten (Format, Ladezeit) oder Fehler. */
    public String skinNote() { return skin == null ? "" : skin.note; }

    /** Haut der Person übernehmen (Ton, Grundhäute, Zonenstärken, Versatz der Detailkarten). */
    public void setPerson(Person p) {
        float[] t = Skin.tone(p.melanin, p.redness);
        System.arraycopy(t, 0, tone, 0, 3);
        double years = p.ageYears();
        float[] w = Skin.textureWeights(years, p.gender);
        System.arraycopy(w, 0, texW, 0, 6);
        double ageF = Math.max(0, Math.min(1, (years - 22) / 50));
        skinP[0] = (float) (p.pores * (0.85 + 0.3 * ageF));
        skinP[1] = (float) ageF;
        skinP[2] = (float) p.freckles;
        skinP[3] = (float) p.beard;
        skinQ[0] = (float) (p.oil * (1 - 0.35 * ageF));
        skinQ[1] = (float) (Math.max(0, Math.min(1, (years - 45) / 30)) * (1 - 0.7 * p.melanin));
        skinQ[2] = (float) p.redness;
        skinQ[3] = (float) p.melanin;
        iris[0] = (float) p.iris;
        com.dan.avatar.Person.Rng ir = new com.dan.avatar.Person.Rng(p.seed * 31 + 7);
        iris[2] = (float) ir.next(); iris[3] = (float) ir.next();
        mouthOpen = (float) mouthOpening(p);
        com.dan.avatar.Person.Rng r = new com.dan.avatar.Person.Rng(p.seed ^ 0x5EED5EEDL);
        seedOfs[0] = (float) r.next() * 7;
        seedOfs[1] = (float) r.next() * 7;
    }

    /** Mundöffnung (0…1) direkt setzen, z. B. aus den Formen des Verhaltens in jedem Bild (Phase 7). */
    public void setMouthOpen(double v) { mouthOpen = (float) Math.max(0, Math.min(1, v)); }

    /** Wie weit der Mund offen ist (0…1) aus den Gesichtsformen und Visemen der Person. */
    public static double mouthOpening(Person p) { return mouthOpening(p.forms); }

    /** Wie weit der Mund offen ist (0…1) aus Gesichtsformen ("arkit.…", "visem.…" → Gewicht). */
    public static double mouthOpening(Map<String, Double> forms) {
        double o = 0;
        String[][] f = {{"arkit.jawOpen", "1"}, {"arkit.mouthFunnel", "0.4"}, {"arkit.mouthLowerDownLeft", "0.25"}, {"arkit.mouthLowerDownRight", "0.25"},
            {"arkit.mouthUpperUpLeft", "0.15"}, {"arkit.mouthUpperUpRight", "0.15"}, {"arkit.mouthSmileLeft", "0.2"}, {"arkit.mouthSmileRight", "0.2"},
            {"arkit.mouthStretchLeft", "0.15"}, {"arkit.mouthStretchRight", "0.15"}, {"visem.viseme_aa", "0.8"}, {"visem.viseme_O", "0.6"},
            {"visem.viseme_E", "0.5"}, {"visem.viseme_I", "0.4"}, {"visem.viseme_U", "0.4"}, {"visem.viseme_CH", "0.35"}, {"visem.viseme_SS", "0.25"},
            {"visem.viseme_TH", "0.3"}, {"visem.viseme_DD", "0.3"}, {"visem.viseme_kk", "0.35"}, {"visem.viseme_nn", "0.25"}, {"visem.viseme_RR", "0.3"}};
        for (String[] e : f) o += forms.getOrDefault(e[0], 0.0) * Double.parseDouble(e[1]);
        return Math.max(0, Math.min(1, o));
    }

    /** In die Schattenkarte zeichnen (nur Tiefe; Haar und Wimpern mit Alphatest). */
    public void drawDepth(GL4 gl, float[] lightViewProj, float x, float y, float z) {
        if (progDepth == 0) return;
        gl.glUseProgram(progDepth);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(progDepth, "uViewProj"), 1, false, lightViewProj, 0);
        gl.glUniform3f(gl.glGetUniformLocation(progDepth, "uOffset"), x, y, z);
        gl.glUniform1i(gl.glGetUniformLocation(progDepth, "uTex"), 0);
        gl.glDisable(GL.GL_CULL_FACE);
        for (int i = 0; i < prims.size(); i++) {
            Body.Part p = body.parts.get(i);
            if (isHidden(p.mesh.name) || external.contains(i)) continue;
            Kind k = kind(p);
            if (k == Kind.EYE) continue;                      // Augen werfen keinen Schatten (Hornhaut)
            Gltf.Material m = p.prim.material >= 0 ? body.model.materials.get(p.prim.material) : new Gltf.Material();
            boolean alpha = !"OPAQUE".equals(m.alphaMode) || k == Kind.HAIR;
            Integer tex = m.baseColorTexture >= 0 ? textures.get(m.baseColorTexture) : null;
            gl.glUniform1i(gl.glGetUniformLocation(progDepth, "uAlphaTest"), alpha && tex != null && tex != 0 ? 1 : 0);
            gl.glUniform1f(gl.glGetUniformLocation(progDepth, "uAlphaCutoff"), "MASK".equals(m.alphaMode) ? m.alphaCutoff : 0.5f);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            gl.glBindTexture(GL.GL_TEXTURE_2D, tex != null ? tex : 0);
            prims.get(i).draw(gl);
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    /** Zeichnet die Person ohne Schatten und Streuung (wie bis Phase 3). */
    public void draw(GL4 gl, float[] viewProj, double[] eye, Light l, float x, float y, float z) {
        draw(gl, viewProj, eye, l, new Shading(), x, y, z);
    }

    /** Zeichnet die Person, verschoben um (x, y, z); y = −Fußhöhe stellt sie auf den Boden. */
    public void draw(GL4 gl, float[] viewProj, double[] eye, Light l, Shading sh, float x, float y, float z) {
        if (prog == 0) return;
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(loc(gl, "uViewProj"), 1, false, viewProj, 0);
        gl.glUniform3f(loc(gl, "uEye"), (float) eye[0], (float) eye[1], (float) eye[2]);
        gl.glUniform3f(loc(gl, "uOffset"), x, y, z);
        gl.glUniform3fv(loc(gl, "uKeyPos"), 1, l.keyPos, 0);
        gl.glUniform3fv(loc(gl, "uKeyDir"), 1, l.keyDir, 0);
        gl.glUniform3fv(loc(gl, "uKeyCol"), 1, l.g(l.keyCol), 0);
        gl.glUniform2f(loc(gl, "uKeyCone"), (float) Math.cos(Math.toRadians(l.keyConeInner)), (float) Math.cos(Math.toRadians(l.keyConeOuter)));
        gl.glUniform3fv(loc(gl, "uRimPos"), 1, l.rimPos, 0);
        gl.glUniform3fv(loc(gl, "uRimCol"), 1, l.g(l.rimCol), 0);
        gl.glUniform3fv(loc(gl, "uFillCol"), 1, l.g(l.fillCol), 0);
        gl.glUniform1i(loc(gl, "uTex"), 0);
        // Schatten (Einheit 1)
        if (sh.shadow != null) sh.shadow.bind(gl, prog, 1);
        else gl.glUniform1i(loc(gl, "uShadowOn"), 0);
        gl.glUniform1i(loc(gl, "uShadow"), 1);
        // Haut (Einheiten 2–5)
        gl.glUniform1i(loc(gl, "uSplit"), sh.split && !sh.low ? 1 : 0);
        gl.glUniform1i(loc(gl, "uLow"), sh.low ? 1 : 0);
        gl.glUniform1i(loc(gl, "uDebug"), DEBUG);
        gl.glUniform2f(loc(gl, "uTrans"), sh.transScale, sh.transStrength);
        gl.glUniform1f(loc(gl, "uTile"), sh.tile);
        gl.glUniform1f(loc(gl, "uSlope"), SkinDetail.SLOPE_A);
        gl.glUniform2f(loc(gl, "uSeedOfs"), seedOfs[0], seedOfs[1]);
        gl.glUniform3fv(loc(gl, "uTone"), 1, tone, 0);
        gl.glUniform1fv(loc(gl, "uTexW"), 6, texW, 0);
        float[] im = new float[18];
        for (int i = 0; i < 6; i++) System.arraycopy(skin.invMean[i], 0, im, i * 3, 3);
        gl.glUniform3fv(loc(gl, "uTexInvMean"), 6, im, 0);
        gl.glUniform4fv(loc(gl, "uSkinP"), 1, skinP, 0);
        gl.glUniform4fv(loc(gl, "uSkinQ"), 1, skinQ, 0);
        gl.glActiveTexture(GL.GL_TEXTURE2);
        gl.glBindTexture(com.jogamp.opengl.GL2ES3.GL_TEXTURE_2D_ARRAY, skin.skinArray);
        gl.glActiveTexture(GL.GL_TEXTURE3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, skin.detA);
        gl.glActiveTexture(GL.GL_TEXTURE4);
        gl.glBindTexture(GL.GL_TEXTURE_2D, skin.detB);
        gl.glActiveTexture(GL.GL_TEXTURE5);
        gl.glBindTexture(GL.GL_TEXTURE_2D, skin.lut);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(loc(gl, "uSkinTex"), 2);
        gl.glUniform1i(loc(gl, "uDetA"), 3);
        gl.glUniform1i(loc(gl, "uDetB"), 4);
        gl.glUniform1i(loc(gl, "uLut"), 5);
        float[] ec = eyeC.clone();
        for (int e = 0; e < 2; e++) { ec[e * 3] += x; ec[e * 3 + 1] += y; ec[e * 3 + 2] += z; }
        gl.glUniform3fv(loc(gl, "uEyeC"), 2, ec, 0);
        gl.glUniform3fv(loc(gl, "uEyeX"), 2, eyeX, 0);
        gl.glUniform3fv(loc(gl, "uEyeY"), 2, eyeY, 0);
        gl.glUniform3fv(loc(gl, "uEyeZ"), 2, eyeZ, 0);
        gl.glUniform1fv(loc(gl, "uEyeR"), 2, eyeR, 0);
        gl.glUniform4fv(loc(gl, "uIris"), 1, iris, 0);
        gl.glUniform1f(loc(gl, "uMouthOpen"), mouthOpen);
        gl.glUniform4fv(loc(gl, "uWet"), 1, wet, 0);
        gl.glUniform1i(loc(gl, "uEnvN"), Math.min(16, envN));
        if (envN > 0) {
            gl.glUniform3fv(loc(gl, "uEnvDir"), 16, envDir, 0);
            gl.glUniform3fv(loc(gl, "uEnvCol"), 16, envCol, 0);
            gl.glUniform1fv(loc(gl, "uEnvCos"), 16, envCos, 0);
        }
        gl.glUniform3fv(loc(gl, "uRainFrom"), 1, rainFrom, 0);
        gl.glUniform1f(loc(gl, "uCold"), cold);
        gl.glUniform2f(loc(gl, "uZoneY"), neckY + y, hipY + y);
        boolean dr = drops != null && dropRect != null && drops.heightTexture() != 0;
        gl.glUniform1i(loc(gl, "uDropOn"), dr ? 1 : 0);
        if (dr) {
            gl.glUniform4fv(loc(gl, "uDropRect"), 1, dropRect, 0);
            gl.glUniform1f(loc(gl, "uDropTexel"), 1f / drops.size);
            gl.glActiveTexture(GL.GL_TEXTURE6);
            gl.glBindTexture(GL.GL_TEXTURE_2D, drops.heightTexture());
            gl.glActiveTexture(GL.GL_TEXTURE7);
            gl.glBindTexture(GL.GL_TEXTURE_2D, drops.trailTexture());
            gl.glActiveTexture(GL.GL_TEXTURE0);
        }
        gl.glUniform1i(loc(gl, "uDropH"), 6);
        gl.glUniform1i(loc(gl, "uDropT"), 7);
        gl.glUniform1f(loc(gl, "uGain"), l.gain);
        gl.glUniform2f(loc(gl, "uDither"), dither[0], dither[1]);
        gl.glDisable(GL.GL_CULL_FACE);
        for (int pass = 0; pass < 2; pass++)
            for (int i = 0; i < prims.size(); i++) {
                Body.Part p = body.parts.get(i);
                if (isHidden(p.mesh.name) || external.contains(i)) continue;
                Gltf.Material m = p.prim.material >= 0 ? body.model.materials.get(p.prim.material) : new Gltf.Material();
                boolean alpha = !"OPAQUE".equals(m.alphaMode) || kind(p) == Kind.HAIR;
                if ((pass == 0) == alpha) continue;
                Kind k = kind(p);
                float[] bc = k == Kind.SKIN ? new float[]{0.60f, 0.42f, 0.34f, 1} : m.baseColor;
                gl.glUniform4fv(loc(gl, "uBaseColor"), 1, bc, 0);
                Integer tex = m.baseColorTexture >= 0 ? textures.get(m.baseColorTexture) : null;
                gl.glUniform1i(loc(gl, "uHasTex"), tex != null && tex != 0 ? 1 : 0);
                gl.glActiveTexture(GL.GL_TEXTURE0);
                gl.glBindTexture(GL.GL_TEXTURE_2D, tex != null ? tex : 0);
                gl.glUniform1i(loc(gl, "uAlphaMode"), "MASK".equals(m.alphaMode) ? 1 : ("BLEND".equals(m.alphaMode) || k == Kind.HAIR ? 2 : 0));
                gl.glUniform1f(loc(gl, "uAlphaCutoff"), m.alphaCutoff);
                gl.glUniform1f(loc(gl, "uWrap"), k.wrap);
                gl.glUniform1f(loc(gl, "uSpec"), k.spec);
                gl.glUniform1f(loc(gl, "uShine"), k.shine);
                boolean eyeOk = k == Kind.EYE && eyes != null && eyes.valid() && i == eyes.part;
                gl.glUniform1i(loc(gl, "uMode"), k == Kind.SKIN ? 1 : (eyeOk ? 2 : (k == Kind.MOUTH ? 3 : 0)));
                gl.glUniform1i(loc(gl, "uCloth"), k == Kind.CLOTH ? 1 : 0);
                float[] fa = k == Kind.CLOTH ? fabric.get(i) : null;
                gl.glUniform4f(loc(gl, "uFab0"), fa == null ? 0 : fa[0], fa == null ? 0 : fa[1], fa == null ? 0 : fa[2], fa == null ? 0 : fa[3]);
                gl.glUniform4f(loc(gl, "uFab1"), fa == null ? 0 : fa[4], fa == null ? 0 : fa[5], fa == null ? 0 : fa[6], fa == null ? -1 : fa[7]);
                gl.glUniform3f(loc(gl, "uFabCol"), fa == null ? 0 : fa[8], fa == null ? 0 : fa[9], fa == null ? 0 : fa[10]);
                if (i != bodyIndex) { gl.glVertexAttrib4f(6, 0.25f, 0, 0, 0); gl.glVertexAttrib4f(7, 0, 0, 0, 0); }
                if (i != bodyIndex && k != Kind.MOUTH) gl.glVertexAttrib4f(8, 0, 0, 0, 0);
                prims.get(i).draw(gl);
            }
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    private boolean isHidden(String name) {
        if (name == null || hidden.isEmpty()) return false;
        for (String h : hidden) if (name.contains(h)) return true;
        return false;
    }

    public void dispose(GL4 gl) {
        for (GltfGl.PrimitiveGl p : prims) p.dispose(gl);
        prims.clear();
        for (int t : textures.values()) if (t != 0) gl.glDeleteTextures(1, new int[]{t}, 0);
        textures.clear();
        if (prog != 0) gl.glDeleteProgram(prog);
        if (progDepth != 0) gl.glDeleteProgram(progDepth);
        prog = progDepth = 0;
        if (maskBuf != 0) gl.glDeleteBuffers(1, new int[]{maskBuf}, 0);
        maskBuf = 0;
        for (int bb : extraBufs) gl.glDeleteBuffers(1, new int[]{bb}, 0);
        extraBufs.clear();
        if (skin != null) { SkinGl.release(gl); skin = null; }
        for (int c : new int[]{cMorph, cTri, cVnorm, cSkin}) if (c != 0) gl.glDeleteProgram(c);
        for (Gpu g : gpu)
            gl.glDeleteBuffers(18, new int[]{g.base, g.baseN, g.off, g.tid, g.dlt, g.rest, g.restN, g.idx, g.triN, g.weld, g.gOff, g.gTri, g.jn, g.wt, g.restS, g.sOff, g.sTid, g.sDlt}, 0);
        gpu.clear();
        gl.glDeleteBuffers(3, new int[]{wgtBuf, dqBuf, matBuf}, 0);
        for (int fb : flexBuf.values()) gl.glDeleteBuffers(1, new int[]{fb}, 0);
        flexBuf.clear();
        if (flexDummy != 0) gl.glDeleteBuffers(1, new int[]{flexDummy}, 0);
        flexDummy = 0;
    }

    /** Art eines Netzes nach seinem Namen (Grundkörper aus MPFB). */
    enum Kind {
        SKIN(0.45f, 0.12f, 22), EYE(0.0f, 0.9f, 300), MOUTH(0.3f, 0.3f, 60), HAIR(0.25f, 0.12f, 40), CLOTH(0.15f, 0.04f, 8);
        final float wrap, spec, shine;
        Kind(float w, float s, float sh) { wrap = w; spec = s; shine = sh; }
    }

    static Kind kind(Body.Part p) {
        if (p.isBody) return Kind.SKIN;
        String n = p.mesh.name.toLowerCase();
        if (n.contains("brow") || n.contains("lash") || n.contains("hair") || n.contains("bob") || n.contains("ponytail")
                || n.contains("long0") || n.contains("short0") || n.contains("braid") || n.contains("afro")) return Kind.HAIR;
        if (n.contains("eye") || n.contains("poly")) return Kind.EYE;
        if (n.contains("teeth") || n.contains("tongue")) return Kind.MOUTH;
        return Kind.CLOTH;
    }

    private final Map<String, Integer> locs = new HashMap<>();

    private int loc(GL4 gl, String n) {
        return locs.computeIfAbsent(n, k -> gl.glGetUniformLocation(prog, k));
    }

    // ------------------------------------------------------------ Shader

    static String read(String name) throws IOException {
        try (InputStream in = AvatarGl.class.getResourceAsStream(name)) {
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
            throw new IOException(vs + "+" + fs + ": " + new String(b, StandardCharsets.UTF_8).trim());
        }
        return p;
    }

    private static int shader(GL4 gl, int type, String name) throws IOException {
        int s = gl.glCreateShader(type);
        gl.glShaderSource(s, 1, new String[]{read(name)}, null);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL2ES2.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] b = new byte[4096];
            gl.glGetShaderInfoLog(s, b.length, null, 0, b, 0);
            throw new IOException(name + ": " + new String(b, StandardCharsets.UTF_8).trim());
        }
        return s;
    }
}
