package com.dan.avatarstudio;

import com.dan.avatar.Body;
import com.dan.avatar.Person;
import com.dan.avatar.SkinMasks;
import com.dan.avatar.gl.AvatarGl;
import com.dan.avatar.gl.ShadowMap;
import com.dan.gltf.Gltf;
import com.dan.gltf.gl.GltfGl;
import com.dan.hair.HairColor;
import com.dan.hair.HairDynamics;
import com.dan.hair.Strands;
import com.dan.hair.gl.HairGl;
import com.jogamp.opengl.GL4;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Verbindet eine Person mit ihren Haaren (Phase 6). Hier, im Studio, treffen sich die Pakete:
 * {@code com.dan.hair} kennt keinen Menschen, {@code com.dan.avatar} keine Haare. Die Frisur wählt
 * die Person ({@link Person#hair}), Netze, Kopfhaut und Gelenke kommen vom Körper.
 *
 * <ul>
 *   <li>Haarkarten: die sechs Frisuren der Bibliothek, Brauen und Wimpern (Attribut 8 = Biegsamkeit);</li>
 *   <li>Fliegenhaare: einzelne Haare auf der Frisur, vorn dichter;</li>
 *   <li>Bart: einzelne Haare auf der Haut, Dichte aus der Bartzone, Länge nach {@link Person#beardLength};</li>
 *   <li>Bewegung: Feder am Kopf und Wind ({@link HairDynamics}).</li>
 * </ul>
 */
final class HairRig {

    /** Frisur → Teil des Netznamens. */
    static final Map<String, String> STYLE_MESH = new LinkedHashMap<>();
    static {
        STYLE_MESH.put("bob", "bob02");
        STYLE_MESH.put("lang", "long01");
        STYLE_MESH.put("zopf", "braid01");
        STYLE_MESH.put("dutt", "braidbun");
        STYLE_MESH.put("kurz", "short02");
        STYLE_MESH.put("locken", "afro01");
        STYLE_MESH.put("glatze", null);
    }

    /** Anzeigenamen der Frisuren (wie {@link Person#HAIR_STYLES}). */
    static final String[] STYLE_NAMES = {"Bob", "Lang offen", "Zopf", "Dutt", "Kurz", "Locken", "Glatze"};

    static final class CardPart {
        int part;
        String name;
        boolean browLash;
        HairGl.Card card = new HairGl.Card();
        float[] flex;
        float maxFlex;
        /** Übereinstimmung UV-Richtung und Fluss vom Wirbel (unter 0,55: Kappe, Fluss gilt). */
        double coherence = 1;
        int flexBuf;
    }

    final HairGl hgl = new HairGl();
    final HairDynamics dyn = new HairDynamics();
    final List<CardPart> cards = new ArrayList<>();
    private HairGl.StrandSet fly, beard;
    private String grown = "";
    private CardPart active;
    private final HairGl.Look brows = new HairGl.Look();
    private boolean ok;
    /** Einzelne Haare zeichnen (Fliegenhaare und Bart). */
    volatile boolean strandsOn = true;
    /** Zuletzt: Anzahl Fliegenhaare und Barthaare. */
    int flyCount, beardCount;
    private int bodyPart = -1;
    /** Nässe des Haars 0…1 (Phase 8) und der Brauen (vom Gesicht). */
    volatile double wetness, browWet;

    String error() { return hgl.error(); }

    void init(GL4 gl, AvatarGl ag, Body body) {
        hgl.init(gl);
        ok = hgl.error() == null;
        if (!ok) return;
        float[] bodyPos = null;
        for (int i = 0; i < body.parts.size(); i++) if (body.parts.get(i).isBody) { bodyPart = i; bodyPos = body.parts.get(i).prim.position; }
        Grid grid = bodyPos == null ? null : new Grid(bodyPos, 0.02);
        double topY = -1e9;
        double[] crown = null;
        if (bodyPos != null) for (int v = 0; v < bodyPos.length / 3; v++)
            if (bodyPos[v * 3 + 1] > topY) { topY = bodyPos[v * 3 + 1]; crown = new double[]{bodyPos[v * 3], topY, bodyPos[v * 3 + 2] - 0.035}; }
        for (int i = 0; i < ag.partCount(); i++) {
            if (!ag.isHair(i)) continue;
            Body.Part p = body.parts.get(i);
            CardPart c = new CardPart();
            c.part = i;
            c.name = p.mesh.name;
            String n = c.name.toLowerCase();
            c.browLash = n.contains("brow") || n.contains("lash");
            GltfGl.PrimitiveGl pg = ag.primGl(i);
            c.card.vao = pg.vao;
            c.card.indexCount = pg.indexCount;
            c.card.texture = ag.baseTexture(i);
            Gltf.Primitive pr = p.prim;
            if (pr.uv0 != null) {
                double[] ax = Strands.cardAxis(pr.position, pr.uv0, pr.indices);
                c.card.axis = (int) ax[0];
                c.card.axisSign = (float) ax[1];
            }
            c.card.texMean = texMean(body.model, pr);
            c.flex = c.browLash || grid == null ? new float[pr.vertexCount * 4] : flex(pr.position, grid, topY - 0.10);
            if (pr.uv0 != null && pr.normal != null) {
                float[] tan = Strands.cardTangents(pr.position, pr.uv0, pr.indices, c.card.axis, c.card.axisSign);
                if (!c.browLash && crown != null) {
                    // Karten: Richtung aus der UV, Vorzeichen vom Wirbel weg; Kappen (UV ohne Haarrichtung): Fluss vom Wirbel
                    float[] flow = Strands.flowTangents(pr.position, pr.normal, crown);
                    c.coherence = Strands.coherence(tan, flow);
                    if (c.coherence < 0.55) tan = flow;
                    else for (int v = 0; v < pr.vertexCount; v++) {
                        float d = tan[v * 3] * flow[v * 3] + tan[v * 3 + 1] * flow[v * 3 + 1] + tan[v * 3 + 2] * flow[v * 3 + 2];
                        if (d < 0) for (int k = 0; k < 3; k++) tan[v * 3 + k] = -tan[v * 3 + k];
                    }
                }
                for (int v = 0; v < pr.vertexCount; v++)
                    for (int k = 0; k < 3; k++) c.flex[v * 4 + 1 + k] = tan[v * 3 + k];
            }
            for (int v = 0; v < pr.vertexCount; v++) c.maxFlex = Math.max(c.maxFlex, c.flex[v * 4]);
            c.flexBuf = ag.attrib8(gl, i, c.flex);
            cards.add(c);
            ag.external.add(i);
        }
    }

    /** Biegsamkeit je Ecke: Abstand zur Haut und wie weit unter der Stirn (lange Haare hängen). */
    private static float[] flex(float[] pos, Grid g, double yRef) {
        int n = pos.length / 3;
        float[] out = new float[n * 4];
        for (int v = 0; v < n; v++) {
            double x = pos[v * 3], y = pos[v * 3 + 1], z = pos[v * 3 + 2];
            double d = g.nearest(x, y, z, 0.14);
            double drop = Math.max(0, yRef - y);
            double f = 0.65 * smooth(0.004, 0.09, d) + 0.6 * smooth(0.0, 0.35, drop);
            f *= smooth(0.002, 0.012, d);
            out[v * 4] = (float) Math.min(1, f);
        }
        return out;
    }

    /** Mittlere Helligkeit der deckenden Texel einer Haarkarte (für die Strähnenvielfalt). */
    private static float texMean(Gltf m, Gltf.Primitive pr) {
        try {
            if (pr.material < 0) return 0.5f;
            int ti = m.materials.get(pr.material).baseColorTexture;
            if (ti < 0) return 0.5f;
            Gltf.Texture t = m.textures.get(ti);
            if (t.source < 0) return 0.5f;
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(m.images.get(t.source).data));
            if (img == null) return 0.5f;
            double s = 0, w = 0;
            int step = Math.max(1, img.getWidth() / 128);
            for (int y = 0; y < img.getHeight(); y += step)
                for (int x = 0; x < img.getWidth(); x += step) {
                    int c = img.getRGB(x, y);
                    double a = (c >>> 24) / 255.0;
                    if (a < 0.5) continue;
                    double l = 0.3 * lin((c >> 16) & 255) + 0.55 * lin((c >> 8) & 255) + 0.15 * lin(c & 255);
                    s += l; w++;
                }
            return w == 0 ? 0.5f : (float) Math.max(0.03, s / w);
        } catch (Exception e) {
            return 0.5f;
        }
    }

    private static double lin(int c) {
        double v = c / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    /** Frisur und Farben der Person übernehmen; einzelne Haare neu wachsen lassen, wenn nötig. */
    void update(GL4 gl, AvatarGl ag, Body body, Person p, Quality q, java.util.Set<String> hidden) {
        if (!ok) return;
        String mesh = STYLE_MESH.get(p.hair);
        active = null;
        for (CardPart c : cards) {
            if (c.browLash) continue;
            if (mesh != null && c.name.contains(mesh) && !isHidden(c.name, hidden)) active = c;
        }
        float[] alb = HairColor.albedo(p.hairColor, p.hairRed);
        HairGl.Look k = hgl.look;
        k.albedo = alb;
        k.grey = (float) p.grey;
        k.roughness = 0.30f + 0.08f * (float) p.grey;
        k.shift = 0.05f;
        brows.albedo = HairColor.albedo(Math.min(1, p.hairColor + 0.08), p.hairRed);
        brows.grey = (float) (p.grey * 0.6);
        brows.roughness = 0.38f;
        // nasses Haar: dunkler (Wasser füllt die Schuppen), glatter, die Strähnen kleben (Phase 8) [G]
        double ws = Math.sqrt(Math.max(0, Math.min(1, wetness))), wb = Math.sqrt(Math.max(0, Math.min(1, browWet)));
        for (int i = 0; i < 3; i++) { k.albedo[i] *= (float) (1 - 0.45 * ws); brows.albedo[i] *= (float) (1 - 0.3 * wb); }
        k.roughness = (float) (k.roughness + (0.22 - k.roughness) * ws);
        brows.roughness = (float) (brows.roughness + (0.2 - brows.roughness) * wb);
        dyn.windBend = 0.02 * (1 - 0.5 * ws);
        if (fly != null) { fly.look.gravity = (float) (0.18 + 0.95 * ws); fly.look.radius = (float) (3.5e-5 * (1 + 1.4 * ws)); }
        if (beard != null) beard.look.gravity = (float) (0.12 + 0.5 * wb);
        String key = p.hair + "|" + p.beardLength + "|" + p.grey + "|" + q + "|" + p.seed;
        if (key.equals(grown)) return;
        grown = key;
        if (fly != null) { fly.dispose(gl); fly = null; }
        if (beard != null) { beard.dispose(gl); beard = null; }
        double qs = q == Quality.LOW ? 0.4 : q == Quality.MEDIUM ? 1.0 : q == Quality.HIGH ? 1.6 : 2.2;
        // Fliegenhaare auf der Frisur, vorn und außen dichter
        if (active != null) {
            Body.Part hp = body.parts.get(active.part);
            Gltf.Primitive pr = hp.prim;
            int n = pr.vertexCount;
            double zmax = -1e9;
            for (int v = 0; v < n; v++) zmax = Math.max(zmax, pr.position[v * 3 + 2]);
            float[] dens = new float[n];
            for (int v = 0; v < n; v++) {
                double f = active.flex[v * 4];
                // vorn (Gesichtsrand) viel dichter: Strähnen vor dem Gesicht
                double front = smooth(zmax - 0.12, zmax - 0.02, pr.position[v * 3 + 2]);
                dens[v] = (float) (smooth(0.1, 0.7, f / Math.max(0.05, active.maxFlex)) * (0.12 + 0.88 * front * front));
            }
            Strands.Params sp = new Strands.Params();
            sp.mode = Strands.Mode.ALONG_CARD;
            sp.axis = active.card.axis;
            sp.axisSign = active.card.axisSign;
            boolean curly = "locken".equals(p.hair), shortHair = "kurz".equals(p.hair), longHair = "lang".equals(p.hair) || "bob".equals(p.hair);
            sp.count = (int) ((shortHair ? 700 : 1400) * qs);
            sp.lengthMin = shortHair ? 0.015 : 0.03; sp.lengthMax = curly ? 0.07 : (shortHair ? 0.045 : (longHair ? 0.22 : 0.13));
            sp.lift = curly ? 0.7 : 0.45; sp.spread = curly ? 0.6 : 0.4;
            sp.curl = curly ? 0.006 : 0.0; sp.curlTurns = 3;
            sp.flex = 1.0;
            sp.grey = p.grey;
            sp.seed = p.seed * 7 + 3;
            Strands s = Strands.grow(pr.position, pr.normal, pr.uv0, pr.indices, dens, sp);
            GltfGl.PrimitiveGl pg = ag.primGl(active.part);
            fly = HairGl.upload(gl, s, pr.indices, pg.vboPos, pg.vboNrm, 8);
            fly.flexBuf = active.flexBuf;
            fly.look.gravity = 0.18f;
            fly.look.radius = 3.5e-5f;
            flyCount = s.count;
        } else flyCount = 0;
        // Bart als einzelne Haare (die Stoppeln bleiben in der Haut)
        if (p.beardLength > 0.01 && p.beard > 0.05 && bodyPart >= 0) {
            Body.Part bp = body.parts.get(bodyPart);
            Gltf.Primitive pr = bp.prim;
            SkinMasks sm = SkinMasks.of(body);
            float[] dens = new float[pr.vertexCount];
            for (int v = 0; v < dens.length; v++) dens[v] = (float) (smooth(0.25, 0.65, sm.data[v * 8 + 1]) * Math.min(1, p.beard * 1.4));
            double bl = p.beardLength;
            Strands.Params sp = new Strands.Params();
            sp.mode = Strands.Mode.DOWN_SURFACE;
            sp.count = (int) ((3000 + 15000 * bl) * qs);
            sp.lengthMin = 0.002 + 0.006 * bl; sp.lengthMax = 0.004 + 0.028 * Math.pow(bl, 1.2);
            sp.lift = 0.45; sp.spread = 0.35;
            sp.curl = 0.0015 * bl; sp.curlTurns = 1.5;
            sp.flex = 0.15 + 0.5 * bl;
            sp.grey = Math.min(1, p.grey * 1.15);
            sp.seed = p.seed * 13 + 5;
            Strands s = Strands.grow(pr.position, pr.normal, null, pr.indices, dens, sp);
            GltfGl.PrimitiveGl pg = ag.primGl(bodyPart);
            beard = HairGl.upload(gl, s, pr.indices, pg.vboPos, pg.vboNrm, bl > 0.4 ? 6 : 4);
            beard.look.gravity = 0.12f;
            beard.look.radius = 5e-5f;
            beard.look.diffCover = 0.6f;
            beardCount = s.count;
        } else beardCount = 0;
    }

    private static boolean isHidden(String name, java.util.Set<String> hidden) {
        for (String h : hidden) if (name.contains(h)) return true;
        return false;
    }

    /** Bewegung: Kopf (Welt, m), Wind (m/s), Zeit (s). */
    void step(double dt, double[] head, double[] wind, double t) {
        dyn.step(dt, head, wind, t);
        HairGl.motion(hgl.look, dyn);
        if (fly != null) HairGl.motion(fly.look, dyn);
        if (beard != null) {
            float[] tp = dyn.tipOffset(), fl = dyn.flutter();
            for (int k = 0; k < 3; k++) { beard.look.tip[k] = tp[k] * 0.15f; beard.look.flutter[k] = fl[k] * 0.15f; }
        }
    }

    /** Drehung des Kopfes gegen die Ruhe (3×3 spaltenweise): die Haarrichtung der Karten dreht mit. */
    void headRotation(float[] r) {
        hgl.look.rot = r.clone();
        brows.rot = r.clone();
    }

    // ------------------------------------------------------------ Zeichnen

    private void offset(float x, float y, float z) {
        for (HairGl.Look k : new HairGl.Look[]{hgl.look, brows, fly == null ? null : fly.look, beard == null ? null : beard.look})
            if (k != null) { k.offset[0] = x; k.offset[1] = y; k.offset[2] = z; }
    }

    void depth(GL4 gl, float[] lightVP, float x, float y, float z, float time, java.util.Set<String> hidden) {
        if (!ok) return;
        offset(x, y, z);
        gl.glDisable(com.jogamp.opengl.GL.GL_CULL_FACE);
        for (CardPart c : cards) {
            if (isHidden(c.name, hidden)) continue;
            if (c.browLash) hgl.drawCardsDepth(gl, c.card, brows, lightVP, time);
            else if (c == active) hgl.drawCardsDepth(gl, c.card, hgl.look, lightVP, time);
        }
    }

    /** Licht für die Haare aus dem Licht der Szene. */
    static HairGl.Lighting lighting(AvatarGl.Light l, ShadowMap sm, double[] eye, float focal, float time) {
        HairGl.Lighting h = new HairGl.Lighting();
        h.keyPos = l.keyPos.clone();
        h.keyDir = l.keyDir.clone();
        h.keyCol = l.g(l.keyCol);
        h.coneInnerCos = (float) Math.cos(Math.toRadians(l.keyConeInner));
        h.coneOuterCos = (float) Math.cos(Math.toRadians(l.keyConeOuter));
        h.rimPos = l.rimPos.clone();
        h.rimCol = l.g(l.rimCol);
        h.fillCol = l.g(l.fillCol);
        if (sm != null && sm.valid) {
            h.shadowOn = true;
            h.shadowTex = sm.tex;
            System.arraycopy(sm.viewProj, 0, h.lightViewProj, 0, 16);
            h.near = sm.near; h.far = sm.far;
            h.texel = 1f / Math.max(1, sm.size);
        }
        h.eye = new float[]{(float) eye[0], (float) eye[1], (float) eye[2]};
        h.focal = focal;
        h.time = time;
        return h;
    }

    /** Deckende Teile der Karten (schreiben Tiefe). */
    void drawOpaque(GL4 gl, HairGl.Lighting li, float[] vp, float x, float y, float z, java.util.Set<String> hidden) {
        if (!ok) return;
        offset(x, y, z);
        for (CardPart c : cards) {
            if (isHidden(c.name, hidden)) continue;
            if (c.browLash) hgl.drawCards(gl, c.card, li, brows, vp, 0);
            else if (c == active) hgl.drawCards(gl, c.card, li, hgl.look, vp, 0);
        }
    }

    /** Saum der Karten und einzelne Haare (gemischt, ohne Sortieren). */
    void drawBlended(GL4 gl, HairGl.Lighting li, float[] vp, float x, float y, float z, java.util.Set<String> hidden) {
        if (!ok) return;
        offset(x, y, z);
        for (CardPart c : cards) {
            if (isHidden(c.name, hidden)) continue;
            if (c.browLash) hgl.drawCards(gl, c.card, li, brows, vp, 1);
            else if (c == active) hgl.drawCards(gl, c.card, li, hgl.look, vp, 1);
        }
        if (strandsOn) {
            if (fly != null && active != null) hgl.drawStrands(gl, fly, li, vp);
            if (beard != null) hgl.drawStrands(gl, beard, li, vp);
        }
    }

    /** Name des Netzes der aktiven Frisur oder null (Glatze). */
    String activeMesh() { return active == null ? null : active.name; }

    void dispose(GL4 gl) {
        if (fly != null) fly.dispose(gl);
        if (beard != null) beard.dispose(gl);
        fly = beard = null;
        hgl.dispose(gl);
        grown = "";
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Gitter für den nächsten Punkt (Abstand zur Haut). */
    static final class Grid {
        final double cell;
        final Map<Long, int[]> cells = new java.util.HashMap<>();
        final float[] p;

        Grid(float[] pos, double cell) {
            this.cell = cell;
            this.p = pos;
            Map<Long, List<Integer>> tmp = new java.util.HashMap<>();
            for (int v = 0; v < pos.length / 3; v++)
                tmp.computeIfAbsent(key(ix(pos[v * 3]), ix(pos[v * 3 + 1]), ix(pos[v * 3 + 2])), k -> new ArrayList<>()).add(v);
            for (Map.Entry<Long, List<Integer>> e : tmp.entrySet()) cells.put(e.getKey(), e.getValue().stream().mapToInt(Integer::intValue).toArray());
        }

        int ix(double x) { return (int) Math.floor(x / cell); }

        static long key(int x, int y, int z) { return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF); }

        /** Abstand zum nächsten Punkt, höchstens {@code max}. */
        double nearest(double x, double y, double z, double max) {
            int cx = ix(x), cy = ix(y), cz = ix(z);
            double best = max * max;
            int rings = (int) Math.ceil(max / cell);
            for (int r = 0; r <= rings; r++) {
                if ((r - 1) * cell > Math.sqrt(best)) break;
                for (int i = -r; i <= r; i++)
                    for (int j = -r; j <= r; j++)
                        for (int k = -r; k <= r; k++) {
                            if (Math.max(Math.abs(i), Math.max(Math.abs(j), Math.abs(k))) != r) continue;
                            int[] c = cells.get(key(cx + i, cy + j, cz + k));
                            if (c == null) continue;
                            for (int v : c) {
                                double dx = p[v * 3] - x, dy = p[v * 3 + 1] - y, dz = p[v * 3 + 2] - z;
                                double d = dx * dx + dy * dy + dz * dz;
                                if (d < best) best = d;
                            }
                        }
            }
            return Math.sqrt(best);
        }
    }
}
