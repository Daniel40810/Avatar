package com.dan.gltf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Liest glTF 2.0 nach der Spezifikation von Khronos: Kopf und Abschnitte der .glb,
 * Puffer (eingebettet), Pufferansichten mit Schrittweite, Zugriffe aller Komponententypen
 * (auch normalisiert und dünn besetzt), Netze, Morphziele, Skins, Knoten, Materialien, Bilder.
 */
final class GltfReader {

    private static final int GLB_MAGIC = 0x46546C67, CHUNK_JSON = 0x4E4F534A, CHUNK_BIN = 0x004E4942;

    private final byte[] data;
    private Object json;
    private final List<ByteBuffer> buffers = new ArrayList<>();
    private final Gltf g = new Gltf();

    GltfReader(byte[] data) { this.data = data; }

    Gltf read() throws IOException {
        g.sha256 = sha(data);
        ByteBuffer bin = null;
        ByteBuffer b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (data.length >= 12 && b.getInt(0) == GLB_MAGIC) {
            int version = b.getInt(4);
            if (version != 2) throw new IOException("glb Version " + version + ", erwartet 2");
            int len = b.getInt(8);
            if (len != data.length) throw new IOException("Länge im Kopf " + len + " ≠ Datei " + data.length);
            int off = 12;
            String text = null;
            while (off + 8 <= len) {
                int clen = b.getInt(off), ctype = b.getInt(off + 4);
                if (off + 8 + clen > len) throw new IOException("Abschnitt ragt über das Dateiende");
                if (clen % 4 != 0) g.warnings.add("Abschnitt nicht auf 4 Byte ausgerichtet");
                if (ctype == CHUNK_JSON) text = new String(data, off + 8, clen, StandardCharsets.UTF_8);
                else if (ctype == CHUNK_BIN && bin == null)
                    bin = ByteBuffer.wrap(data, off + 8, clen).slice().order(ByteOrder.LITTLE_ENDIAN);
                off += 8 + clen;
            }
            if (text == null) throw new IOException("kein JSON-Abschnitt");
            json = Json.parse(text.trim());
        } else {
            json = Json.parse(new String(data, StandardCharsets.UTF_8).trim());
        }

        Map<String, Object> asset = Json.obj(json, "asset");
        if (asset == null) throw new IOException("asset fehlt");
        String ver = Json.str(asset, "version");
        if (!"2.0".equals(ver)) throw new IOException("glTF-Version " + ver + ", erwartet 2.0");
        g.generator = nz(Json.str(asset, "generator"));
        g.copyright = nz(Json.str(asset, "copyright"));
        List<Object> req = Json.arr(json, "extensionsRequired");
        if (req != null && !req.isEmpty()) g.warnings.add("verlangte Erweiterungen nicht unterstützt: " + req);

        // Puffer
        List<Object> bs = Json.arr(json, "buffers");
        if (bs != null) for (Object o : bs) {
            String uri = Json.str(o, "uri");
            int bl = (int) Json.num(o, "byteLength", 0);
            if (uri == null) {
                if (bin == null) throw new IOException("Puffer ohne URI, aber kein BIN-Abschnitt");
                if (bin.capacity() < bl) throw new IOException("BIN-Abschnitt kürzer als Puffer");
                buffers.add(bin);
            } else if (uri.startsWith("data:")) {
                byte[] d = Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1));
                buffers.add(ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN));
            } else {
                throw new IOException("externe Puffer werden nicht gelesen: " + uri);
            }
        }

        readImagesTextures();
        readMaterials();
        readMeshes();
        readSkins();
        readNodes();
        validate();
        return g;
    }

    // ------------------------------------------------------------ Zugriffe

    /** Ein Zugriff als float-Feld (count × Komponenten), normalisiert wie verlangt, dünn besetzte Werte eingesetzt. */
    float[] accessorF(int idx) throws IOException {
        Object a = acc(idx);
        int count = (int) Json.num(a, "count", 0);
        int nc = comps(Json.str(a, "type"));
        int ct = (int) Json.num(a, "componentType", 0);
        boolean norm = Boolean.TRUE.equals(((Map<?, ?>) a).get("normalized"));
        float[] out = new float[count * nc];
        Object bvO = ((Map<?, ?>) a).get("bufferView");
        if (bvO instanceof Number bvn) {
            View v = view(bvn.intValue());
            int aoff = (int) Json.num(a, "byteOffset", 0);
            int csize = csize(ct);
            int stride = v.stride > 0 ? v.stride : csize * nc;
            long need = aoff + (long) stride * (count - 1) + (long) csize * nc;
            if (count > 0 && need > v.length) throw new IOException("Zugriff " + idx + " ragt über die Pufferansicht");
            for (int i = 0; i < count; i++)
                for (int c = 0; c < nc; c++)
                    out[i * nc + c] = comp(v.buf, v.offset + aoff + i * stride + c * csize, ct, norm);
        }
        Map<String, Object> sp = Json.obj(a, "sparse");
        if (sp != null) {
            int sc = (int) Json.num(sp, "count", 0);
            int[] si = sparseIndices(sp, sc);
            Map<String, Object> vals = Json.obj(sp, "values");
            View vv = view((int) Json.num(vals, "bufferView", -1));
            int voff = vv.offset + (int) Json.num(vals, "byteOffset", 0);
            int csize = csize(ct);
            for (int k = 0; k < sc; k++) {
                if (si[k] < 0 || si[k] >= count) throw new IOException("dünner Index außerhalb in Zugriff " + idx);
                for (int c = 0; c < nc; c++)
                    out[si[k] * nc + c] = comp(vv.buf, voff + (k * nc + c) * csize, ct, norm);
            }
        }
        return out;
    }

    int[] accessorI(int idx) throws IOException {
        Object a = acc(idx);
        int count = (int) Json.num(a, "count", 0);
        int nc = comps(Json.str(a, "type"));
        int ct = (int) Json.num(a, "componentType", 0);
        int[] out = new int[count * nc];
        Object bvO = ((Map<?, ?>) a).get("bufferView");
        if (bvO instanceof Number bvn) {
            View v = view(bvn.intValue());
            int aoff = (int) Json.num(a, "byteOffset", 0);
            int csize = csize(ct);
            int stride = v.stride > 0 ? v.stride : csize * nc;
            for (int i = 0; i < count; i++)
                for (int c = 0; c < nc; c++)
                    out[i * nc + c] = compI(v.buf, v.offset + aoff + i * stride + c * csize, ct);
        }
        Map<String, Object> sp = Json.obj(a, "sparse");
        if (sp != null) {
            int sc = (int) Json.num(sp, "count", 0);
            int[] si = sparseIndices(sp, sc);
            Map<String, Object> vals = Json.obj(sp, "values");
            View vv = view((int) Json.num(vals, "bufferView", -1));
            int voff = vv.offset + (int) Json.num(vals, "byteOffset", 0);
            for (int k = 0; k < sc; k++)
                for (int c = 0; c < nc; c++)
                    out[si[k] * nc + c] = compI(vv.buf, voff + (k * nc + c) * csize(ct), ct);
        }
        return out;
    }

    private int[] sparseIndices(Map<String, Object> sp, int sc) throws IOException {
        Map<String, Object> ind = Json.obj(sp, "indices");
        View iv = view((int) Json.num(ind, "bufferView", -1));
        int ict = (int) Json.num(ind, "componentType", 0);
        int ioff = iv.offset + (int) Json.num(ind, "byteOffset", 0);
        int[] si = new int[sc];
        for (int k = 0; k < sc; k++) si[k] = compI(iv.buf, ioff + k * csize(ict), ict);
        for (int k = 1; k < sc; k++)
            if (si[k] <= si[k - 1]) { g.warnings.add("dünne Indizes nicht streng steigend"); break; }
        return si;
    }

    private Object acc(int idx) throws IOException {
        List<Object> as = Json.arr(json, "accessors");
        if (as == null || idx < 0 || idx >= as.size()) throw new IOException("Zugriff " + idx + " fehlt");
        return as.get(idx);
    }

    private record View(ByteBuffer buf, int offset, int length, int stride) {}

    private View view(int idx) throws IOException {
        List<Object> vs = Json.arr(json, "bufferViews");
        if (vs == null || idx < 0 || idx >= vs.size()) throw new IOException("Pufferansicht " + idx + " fehlt");
        Object v = vs.get(idx);
        int bi = (int) Json.num(v, "buffer", -1);
        if (bi < 0 || bi >= buffers.size()) throw new IOException("Puffer " + bi + " fehlt");
        int off = (int) Json.num(v, "byteOffset", 0), len = (int) Json.num(v, "byteLength", 0);
        if (off + len > buffers.get(bi).capacity()) throw new IOException("Pufferansicht " + idx + " ragt über den Puffer");
        return new View(buffers.get(bi), off, len, (int) Json.num(v, "byteStride", 0));
    }

    static int comps(String t) throws IOException {
        if (t == null) throw new IOException("Zugriff ohne Typ");
        return switch (t) {
            case "SCALAR" -> 1; case "VEC2" -> 2; case "VEC3" -> 3; case "VEC4" -> 4;
            case "MAT2" -> 4; case "MAT3" -> 9; case "MAT4" -> 16;
            default -> throw new IOException("unbekannter Typ " + t);
        };
    }

    static int csize(int ct) throws IOException {
        return switch (ct) {
            case 5120, 5121 -> 1; case 5122, 5123 -> 2; case 5125, 5126 -> 4;
            default -> throw new IOException("unbekannter Komponententyp " + ct);
        };
    }

    private static float comp(ByteBuffer b, int o, int ct, boolean norm) {
        return switch (ct) {
            case 5126 -> b.getFloat(o);
            case 5120 -> norm ? Math.max(b.get(o) / 127f, -1f) : b.get(o);
            case 5121 -> norm ? (b.get(o) & 0xFF) / 255f : (b.get(o) & 0xFF);
            case 5122 -> norm ? Math.max(b.getShort(o) / 32767f, -1f) : b.getShort(o);
            case 5123 -> norm ? (b.getShort(o) & 0xFFFF) / 65535f : (b.getShort(o) & 0xFFFF);
            case 5125 -> (float) (b.getInt(o) & 0xFFFFFFFFL);
            default -> 0f;
        };
    }

    private static int compI(ByteBuffer b, int o, int ct) {
        return switch (ct) {
            case 5120 -> b.get(o);
            case 5121 -> b.get(o) & 0xFF;
            case 5122 -> b.getShort(o);
            case 5123 -> b.getShort(o) & 0xFFFF;
            case 5125 -> b.getInt(o);
            case 5126 -> (int) b.getFloat(o);
            default -> 0;
        };
    }

    // ------------------------------------------------------------ Teile

    private void readImagesTextures() throws IOException {
        List<Object> is = Json.arr(json, "images");
        if (is != null) for (Object o : is) {
            Gltf.Image im = new Gltf.Image();
            im.name = nz(Json.str(o, "name"));
            im.mimeType = nz(Json.str(o, "mimeType"));
            Object bv = ((Map<?, ?>) o).get("bufferView");
            String uri = Json.str(o, "uri");
            if (bv instanceof Number n) {
                View v = view(n.intValue());
                im.data = new byte[v.length];
                v.buf.get(v.offset, im.data);
            } else if (uri != null && uri.startsWith("data:")) {
                im.data = Base64.getDecoder().decode(uri.substring(uri.indexOf(',') + 1));
                if (im.mimeType.isEmpty()) im.mimeType = uri.substring(5, uri.indexOf(';') > 0 ? uri.indexOf(';') : uri.indexOf(','));
            } else if (uri != null) {
                g.warnings.add("externes Bild nicht gelesen: " + uri);
            }
            g.images.add(im);
        }
        List<Object> ts = Json.arr(json, "textures");
        List<Object> samplers = Json.arr(json, "samplers");
        if (ts != null) for (Object o : ts) {
            Gltf.Texture t = new Gltf.Texture();
            t.source = (int) Json.num(o, "source", -1);
            int si = (int) Json.num(o, "sampler", -1);
            if (samplers != null && si >= 0 && si < samplers.size()) {
                Object s = samplers.get(si);
                t.magFilter = (int) Json.num(s, "magFilter", t.magFilter);
                t.minFilter = (int) Json.num(s, "minFilter", t.minFilter);
                t.wrapS = (int) Json.num(s, "wrapS", t.wrapS);
                t.wrapT = (int) Json.num(s, "wrapT", t.wrapT);
            }
            g.textures.add(t);
        }
    }

    private void readMaterials() {
        List<Object> ms = Json.arr(json, "materials");
        if (ms == null) return;
        for (Object o : ms) {
            Gltf.Material m = new Gltf.Material();
            m.name = nz(Json.str(o, "name"));
            Map<String, Object> pbr = Json.obj(o, "pbrMetallicRoughness");
            if (pbr != null) {
                List<Object> bc = Json.arr(pbr, "baseColorFactor");
                if (bc != null) for (int i = 0; i < 4; i++) m.baseColor[i] = ((Number) bc.get(i)).floatValue();
                m.metallic = (float) Json.num(pbr, "metallicFactor", 1);
                m.roughness = (float) Json.num(pbr, "roughnessFactor", 1);
                m.baseColorTexture = texIndex(pbr, "baseColorTexture");
                m.metallicRoughnessTexture = texIndex(pbr, "metallicRoughnessTexture");
            }
            m.normalTexture = texIndex(o, "normalTexture");
            Map<String, Object> nt = Json.obj(o, "normalTexture");
            if (nt != null) m.normalScale = (float) Json.num(nt, "scale", 1);
            m.occlusionTexture = texIndex(o, "occlusionTexture");
            m.emissiveTexture = texIndex(o, "emissiveTexture");
            List<Object> em = Json.arr(o, "emissiveFactor");
            if (em != null) for (int i = 0; i < 3; i++) m.emissive[i] = ((Number) em.get(i)).floatValue();
            String am = Json.str(o, "alphaMode");
            if (am != null) m.alphaMode = am;
            m.alphaCutoff = (float) Json.num(o, "alphaCutoff", 0.5);
            m.doubleSided = Boolean.TRUE.equals(((Map<?, ?>) o).get("doubleSided"));
            g.materials.add(m);
        }
    }

    private static int texIndex(Object o, String key) {
        Map<String, Object> t = Json.obj(o, key);
        return t == null ? -1 : (int) Json.num(t, "index", -1);
    }

    private void readMeshes() throws IOException {
        List<Object> ms = Json.arr(json, "meshes");
        if (ms == null) return;
        for (Object mo : ms) {
            Gltf.Mesh mesh = new Gltf.Mesh();
            mesh.name = nz(Json.str(mo, "name"));
            Map<String, Object> ex = Json.obj(mo, "extras");
            List<Object> tn = ex == null ? null : Json.arr(ex, "targetNames");
            List<Object> w = Json.arr(mo, "weights");
            if (w != null) {
                mesh.weights = new float[w.size()];
                for (int i = 0; i < w.size(); i++) mesh.weights[i] = ((Number) w.get(i)).floatValue();
            }
            int nTargets = -1;
            for (Object po : Json.arr(mo, "primitives")) {
                Gltf.Primitive p = new Gltf.Primitive();
                Map<String, Object> at = Json.obj(po, "attributes");
                Object pos = at.get("POSITION");
                if (!(pos instanceof Number)) throw new IOException("Netz " + mesh.name + ": POSITION fehlt");
                int pi = ((Number) pos).intValue();
                p.position = accessorF(pi);
                p.vertexCount = p.position.length / 3;
                List<Object> mn = Json.arr(acc(pi), "min"), mx = Json.arr(acc(pi), "max");
                if (mn != null && mx != null) for (int i = 0; i < 3; i++) {
                    p.min[i] = ((Number) mn.get(i)).floatValue();
                    p.max[i] = ((Number) mx.get(i)).floatValue();
                }
                for (Map.Entry<String, Object> e : at.entrySet()) {
                    int ai = ((Number) e.getValue()).intValue();
                    switch (e.getKey()) {
                        case "POSITION" -> {}
                        case "NORMAL" -> p.normal = accessorF(ai);
                        case "TEXCOORD_0" -> p.uv0 = accessorF(ai);
                        case "TANGENT" -> p.tangent = accessorF(ai);
                        case "JOINTS_0" -> p.joints = accessorI(ai);
                        case "WEIGHTS_0" -> p.weights = accessorF(ai);
                        case "COLOR_0" -> {
                            float[] c = accessorF(ai);
                            if (c.length == p.vertexCount * 3) {
                                p.color = new float[p.vertexCount * 4];
                                for (int i = 0; i < p.vertexCount; i++) {
                                    System.arraycopy(c, i * 3, p.color, i * 4, 3);
                                    p.color[i * 4 + 3] = 1;
                                }
                            } else p.color = c;
                        }
                        default -> {
                            if (e.getKey().startsWith("_")) {
                                float[] v = accessorF(ai);
                                p.extra.put(e.getKey(), v);
                                p.extraComponents.put(e.getKey(), v.length / Math.max(1, p.vertexCount));
                            }
                        }
                    }
                }
                Object ind = ((Map<?, ?>) po).get("indices");
                if (ind instanceof Number n) p.indices = accessorI(n.intValue());
                else {
                    p.indices = new int[p.vertexCount];
                    for (int i = 0; i < p.vertexCount; i++) p.indices[i] = i;
                }
                p.material = (int) Json.num(po, "material", -1);
                p.mode = (int) Json.num(po, "mode", 4);
                if (p.mode != 4) g.warnings.add("Netz " + mesh.name + ": Modus " + p.mode + " (nur Dreiecke werden gezeichnet)");

                List<Object> tg = Json.arr(po, "targets");
                int nt = tg == null ? 0 : tg.size();
                if (nTargets >= 0 && nt != nTargets) g.warnings.add("Netz " + mesh.name + ": Teile mit verschieden vielen Morphzielen");
                nTargets = nt;
                for (int t = 0; t < nt; t++) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> to = (Map<String, Object>) tg.get(t);
                    float[] dp = to.get("POSITION") instanceof Number n ? accessorF(n.intValue()) : new float[p.vertexCount * 3];
                    float[] dn = to.get("NORMAL") instanceof Number n ? accessorF(n.intValue()) : null;
                    p.targets.add(sparse(dp, dn, p.vertexCount));
                }
                if (p.tangent == null && p.uv0 != null && p.normal != null) {
                    p.tangent = Tangents.compute(p.position, p.normal, p.uv0, p.indices);
                    p.tangentsComputed = true;
                }
                mesh.primitives.add(p);
            }
            for (int t = 0; t < Math.max(nTargets, 0); t++)
                mesh.targetNames.add(tn != null && t < tn.size() ? String.valueOf(tn.get(t)) : "ziel" + t);
            if (tn != null && tn.size() != nTargets) g.warnings.add("Netz " + mesh.name + ": targetNames passt nicht zur Zahl der Morphziele");
            for (Gltf.Primitive p : mesh.primitives)
                for (int t = 0; t < p.targets.size(); t++) p.targets.get(t).name = mesh.targetNames.get(t);
            if (mesh.weights.length == 0) mesh.weights = new float[Math.max(nTargets, 0)];
            g.meshes.add(mesh);
        }
    }

    /** Dichte Differenzen auf die Ecken mit Änderung verdichten. */
    static Gltf.Target sparse(float[] dp, float[] dn, int n) {
        int c = 0;
        for (int i = 0; i < n; i++)
            if (nz3(dp, i) || (dn != null && nz3(dn, i))) c++;
        Gltf.Target t = new Gltf.Target();
        t.index = new int[c];
        t.dPosition = new float[c * 3];
        t.dNormal = dn == null ? null : new float[c * 3];
        int k = 0;
        for (int i = 0; i < n; i++)
            if (nz3(dp, i) || (dn != null && nz3(dn, i))) {
                t.index[k] = i;
                System.arraycopy(dp, i * 3, t.dPosition, k * 3, 3);
                if (dn != null) System.arraycopy(dn, i * 3, t.dNormal, k * 3, 3);
                k++;
            }
        return t;
    }

    private static boolean nz3(float[] a, int i) {
        return a[i * 3] != 0 || a[i * 3 + 1] != 0 || a[i * 3 + 2] != 0;
    }

    private void readSkins() throws IOException {
        List<Object> ss = Json.arr(json, "skins");
        if (ss == null) return;
        for (Object o : ss) {
            Gltf.Skin s = new Gltf.Skin();
            s.name = nz(Json.str(o, "name"));
            List<Object> j = Json.arr(o, "joints");
            s.joints = new int[j.size()];
            for (int i = 0; i < j.size(); i++) s.joints[i] = ((Number) j.get(i)).intValue();
            s.skeleton = (int) Json.num(o, "skeleton", -1);
            s.inverseBind = new float[s.joints.length][];
            Object ib = ((Map<?, ?>) o).get("inverseBindMatrices");
            float[] all = ib instanceof Number n ? accessorF(n.intValue()) : null;
            for (int i = 0; i < s.joints.length; i++) {
                s.inverseBind[i] = new float[16];
                if (all != null) System.arraycopy(all, i * 16, s.inverseBind[i], 0, 16);
                else s.inverseBind[i] = Gltf.Mat.identity();
            }
            g.skins.add(s);
        }
    }

    private void readNodes() {
        List<Object> ns = Json.arr(json, "nodes");
        if (ns != null) for (Object o : ns) {
            Gltf.Node n = new Gltf.Node();
            n.name = nz(Json.str(o, "name"));
            n.mesh = (int) Json.num(o, "mesh", -1);
            n.skin = (int) Json.num(o, "skin", -1);
            List<Object> ch = Json.arr(o, "children");
            if (ch != null) {
                n.children = new int[ch.size()];
                for (int i = 0; i < ch.size(); i++) n.children[i] = ((Number) ch.get(i)).intValue();
            }
            List<Object> m = Json.arr(o, "matrix");
            if (m != null) {
                for (int i = 0; i < 16; i++) n.local[i] = ((Number) m.get(i)).floatValue();
            } else {
                vec(Json.arr(o, "translation"), n.translation);
                vec(Json.arr(o, "rotation"), n.rotation);
                vec(Json.arr(o, "scale"), n.scale);
                n.local = Gltf.Mat.trs(n.translation, n.rotation, n.scale);
            }
            g.nodes.add(n);
        }
        for (int i = 0; i < g.nodes.size(); i++)
            for (int c : g.nodes.get(i).children) g.nodes.get(c).parent = i;
        List<Object> scenes = Json.arr(json, "scenes");
        int sc = (int) Json.num(json, "scene", 0);
        if (scenes != null && sc < scenes.size()) {
            List<Object> roots = Json.arr(scenes.get(sc), "nodes");
            if (roots != null) {
                g.sceneRoots = new int[roots.size()];
                for (int i = 0; i < roots.size(); i++) g.sceneRoots[i] = ((Number) roots.get(i)).intValue();
            }
        }
        for (int i = 0; i < g.nodes.size(); i++) if (g.nodes.get(i).parent < 0) world(i, Gltf.Mat.identity());
        for (int i = 0; i < g.nodes.size(); i++) {
            int m = g.nodes.get(i).mesh;
            if (m >= 0 && m < g.meshes.size()) g.meshes.get(m).node = i;
        }
    }

    private void world(int i, float[] parent) {
        Gltf.Node n = g.nodes.get(i);
        n.world = Gltf.Mat.mul(parent, n.local);
        for (int c : n.children) world(c, n.world);
    }

    private static void vec(List<Object> a, float[] out) {
        if (a != null) for (int i = 0; i < out.length && i < a.size(); i++) out[i] = ((Number) a.get(i)).floatValue();
    }

    // ------------------------------------------------------------ Prüfung

    private void validate() {
        for (Gltf.Mesh m : g.meshes)
            for (Gltf.Primitive p : m.primitives) {
                int n = p.vertexCount;
                for (int ix : p.indices)
                    if (ix < 0 || ix >= n) { g.warnings.add("Netz " + m.name + ": Index außerhalb"); break; }
                if (p.indices.length % 3 != 0) g.warnings.add("Netz " + m.name + ": Indizes nicht durch 3 teilbar");
                if (p.normal != null) {
                    int bad = 0;
                    for (int i = 0; i < n; i++) {
                        double l = Math.sqrt(sq(p.normal[i * 3]) + sq(p.normal[i * 3 + 1]) + sq(p.normal[i * 3 + 2]));
                        if (Math.abs(l - 1) > 0.01) bad++;
                    }
                    if (bad > 0) g.warnings.add("Netz " + m.name + ": " + bad + " Normalen nicht Einheitslänge");
                }
                if (p.weights != null) {
                    int bad = 0;
                    for (int i = 0; i < n; i++) {
                        float s = p.weights[i * 4] + p.weights[i * 4 + 1] + p.weights[i * 4 + 2] + p.weights[i * 4 + 3];
                        if (Math.abs(s - 1) > 0.01) bad++;
                    }
                    if (bad > 0) g.warnings.add("Netz " + m.name + ": " + bad + " Gewichtssummen ≠ 1");
                }
                if (p.material >= g.materials.size()) g.warnings.add("Netz " + m.name + ": Material fehlt");
            }
        for (Gltf.Material m : g.materials)
            for (int t : new int[]{m.baseColorTexture, m.normalTexture})
                if (t >= g.textures.size()) g.warnings.add("Material " + m.name + ": Textur fehlt");
    }

    private static double sq(double x) { return x * x; }

    private static String nz(String s) { return s == null ? "" : s; }

    static String sha(byte[] d) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(d));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
