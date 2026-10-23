package com.dan.gltf;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Abbild eines gelesenen Modells in eigenem Binärformat: Beim ersten Laden wird die Datei
 * gelesen und geprüft, danach kommt das Modell aus dem Abbild (kein JSON, keine Zugriffe,
 * keine Tangentenrechnung). Das Abbild trägt die Prüfsumme der Quelle; stimmt sie nicht,
 * wird neu gelesen.
 *
 * <pre>
 *   Gltf g = GltfCache.load(bytesDerDatei, Path.of("daten/cache"), "avatar_base");
 * </pre>
 */
public final class GltfCache {

    private static final long MAGIC = 0x44414E474C544631L; // "DANGLTF1"
    private static final int VERSION = 1;

    /** Was beim letzten {@link #load} geschah: "Abbild" oder "Datei gelesen, Abbild angelegt". */
    public static volatile String lastSource = "";

    private GltfCache() {}

    public static Gltf load(byte[] source, Path dir, String name) throws IOException {
        String sha = GltfReader.sha(source);
        Path f = dir.resolve(name + "-" + sha.substring(0, 16) + ".gltfc");
        if (Files.isRegularFile(f)) {
            try (InputStream in = Files.newInputStream(f)) {
                Gltf g = read(in);
                if (sha.equals(g.sha256)) { lastSource = "Abbild"; return g; }
            } catch (IOException e) {
                // unbrauchbares Abbild: neu lesen
            }
        }
        Gltf g = Gltf.read(source);
        Files.createDirectories(dir);
        Path tmp = dir.resolve(f.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            write(g, out);
        }
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        // ältere Abbilder desselben Modells (andere Prüfsumme) wegräumen
        try (var old = Files.newDirectoryStream(dir, name + "-*.gltfc")) {
            for (Path o : old) if (!o.equals(f)) Files.deleteIfExists(o);
        } catch (IOException e) {
            // nicht schlimm: bleibt liegen
        }
        lastSource = "Datei gelesen, Abbild angelegt";
        return g;
    }

    // ------------------------------------------------------------ Schreiben

    public static void write(Gltf g, OutputStream os) throws IOException {
        DataOutputStream o = new DataOutputStream(new BufferedOutputStream(os, 1 << 20));
        o.writeLong(MAGIC);
        o.writeInt(VERSION);
        o.writeUTF(g.sha256); o.writeUTF(g.generator); o.writeUTF(g.copyright);
        ints(o, g.sceneRoots);
        o.writeInt(g.warnings.size());
        for (String w : g.warnings) o.writeUTF(w);
        o.writeInt(g.images.size());
        for (Gltf.Image im : g.images) { o.writeUTF(im.name); o.writeUTF(im.mimeType); bytes(o, im.data); }
        o.writeInt(g.textures.size());
        for (Gltf.Texture t : g.textures) { o.writeInt(t.source); o.writeInt(t.magFilter); o.writeInt(t.minFilter); o.writeInt(t.wrapS); o.writeInt(t.wrapT); }
        o.writeInt(g.materials.size());
        for (Gltf.Material m : g.materials) {
            o.writeUTF(m.name); floats(o, m.baseColor); o.writeFloat(m.metallic); o.writeFloat(m.roughness); floats(o, m.emissive);
            o.writeInt(m.baseColorTexture); o.writeInt(m.normalTexture); o.writeInt(m.metallicRoughnessTexture);
            o.writeInt(m.occlusionTexture); o.writeInt(m.emissiveTexture); o.writeFloat(m.normalScale);
            o.writeUTF(m.alphaMode); o.writeFloat(m.alphaCutoff); o.writeBoolean(m.doubleSided);
        }
        o.writeInt(g.meshes.size());
        for (Gltf.Mesh m : g.meshes) {
            o.writeUTF(m.name); o.writeInt(m.node); floats(o, m.weights);
            o.writeInt(m.targetNames.size());
            for (String n : m.targetNames) o.writeUTF(n);
            o.writeInt(m.primitives.size());
            for (Gltf.Primitive p : m.primitives) {
                o.writeInt(p.vertexCount); o.writeInt(p.material); o.writeInt(p.mode); o.writeBoolean(p.tangentsComputed);
                floats(o, p.position); floats(o, p.normal); floats(o, p.uv0); floats(o, p.tangent);
                ints(o, p.joints); floats(o, p.weights); floats(o, p.color); ints(o, p.indices);
                floats(o, p.min); floats(o, p.max);
                o.writeInt(p.extra.size());
                for (Map.Entry<String, float[]> e : p.extra.entrySet()) {
                    o.writeUTF(e.getKey()); o.writeInt(p.extraComponents.get(e.getKey())); floats(o, e.getValue());
                }
                o.writeInt(p.targets.size());
                for (Gltf.Target t : p.targets) { o.writeUTF(t.name); ints(o, t.index); floats(o, t.dPosition); floats(o, t.dNormal); }
            }
        }
        o.writeInt(g.skins.size());
        for (Gltf.Skin s : g.skins) {
            o.writeUTF(s.name); ints(o, s.joints); o.writeInt(s.skeleton);
            for (float[] m : s.inverseBind) floats(o, m);
        }
        o.writeInt(g.nodes.size());
        for (Gltf.Node n : g.nodes) {
            o.writeUTF(n.name); o.writeInt(n.mesh); o.writeInt(n.skin); o.writeInt(n.parent); ints(o, n.children);
            floats(o, n.local); floats(o, n.world); floats(o, n.translation); floats(o, n.rotation); floats(o, n.scale);
        }
        o.writeLong(MAGIC);
        o.flush();
    }

    // ------------------------------------------------------------ Lesen

    public static Gltf read(InputStream is) throws IOException {
        DataInputStream i = new DataInputStream(new BufferedInputStream(is, 1 << 20));
        if (i.readLong() != MAGIC) throw new IOException("kein Abbild");
        if (i.readInt() != VERSION) throw new IOException("Abbild in anderer Fassung");
        Gltf g = new Gltf();
        g.sha256 = i.readUTF(); g.generator = i.readUTF(); g.copyright = i.readUTF();
        g.sceneRoots = ints(i);
        for (int k = i.readInt(); k > 0; k--) g.warnings.add(i.readUTF());
        for (int k = i.readInt(); k > 0; k--) {
            Gltf.Image im = new Gltf.Image();
            im.name = i.readUTF(); im.mimeType = i.readUTF(); im.data = bytes(i);
            g.images.add(im);
        }
        for (int k = i.readInt(); k > 0; k--) {
            Gltf.Texture t = new Gltf.Texture();
            t.source = i.readInt(); t.magFilter = i.readInt(); t.minFilter = i.readInt(); t.wrapS = i.readInt(); t.wrapT = i.readInt();
            g.textures.add(t);
        }
        for (int k = i.readInt(); k > 0; k--) {
            Gltf.Material m = new Gltf.Material();
            m.name = i.readUTF(); m.baseColor = floats(i); m.metallic = i.readFloat(); m.roughness = i.readFloat(); m.emissive = floats(i);
            m.baseColorTexture = i.readInt(); m.normalTexture = i.readInt(); m.metallicRoughnessTexture = i.readInt();
            m.occlusionTexture = i.readInt(); m.emissiveTexture = i.readInt(); m.normalScale = i.readFloat();
            m.alphaMode = i.readUTF(); m.alphaCutoff = i.readFloat(); m.doubleSided = i.readBoolean();
            g.materials.add(m);
        }
        for (int k = i.readInt(); k > 0; k--) {
            Gltf.Mesh m = new Gltf.Mesh();
            m.name = i.readUTF(); m.node = i.readInt(); m.weights = floats(i);
            for (int t = i.readInt(); t > 0; t--) m.targetNames.add(i.readUTF());
            for (int pc = i.readInt(); pc > 0; pc--) {
                Gltf.Primitive p = new Gltf.Primitive();
                p.vertexCount = i.readInt(); p.material = i.readInt(); p.mode = i.readInt(); p.tangentsComputed = i.readBoolean();
                p.position = floats(i); p.normal = floats(i); p.uv0 = floats(i); p.tangent = floats(i);
                p.joints = ints(i); p.weights = floats(i); p.color = floats(i); p.indices = ints(i);
                p.min = floats(i); p.max = floats(i);
                for (int e = i.readInt(); e > 0; e--) {
                    String key = i.readUTF();
                    p.extraComponents.put(key, i.readInt());
                    p.extra.put(key, floats(i));
                }
                for (int t = i.readInt(); t > 0; t--) {
                    Gltf.Target tg = new Gltf.Target();
                    tg.name = i.readUTF(); tg.index = ints(i); tg.dPosition = floats(i); tg.dNormal = floats(i);
                    p.targets.add(tg);
                }
                m.primitives.add(p);
            }
            g.meshes.add(m);
        }
        for (int k = i.readInt(); k > 0; k--) {
            Gltf.Skin s = new Gltf.Skin();
            s.name = i.readUTF(); s.joints = ints(i); s.skeleton = i.readInt();
            s.inverseBind = new float[s.joints.length][];
            for (int j = 0; j < s.joints.length; j++) s.inverseBind[j] = floats(i);
            g.skins.add(s);
        }
        for (int k = i.readInt(); k > 0; k--) {
            Gltf.Node n = new Gltf.Node();
            n.name = i.readUTF(); n.mesh = i.readInt(); n.skin = i.readInt(); n.parent = i.readInt(); n.children = ints(i);
            n.local = floats(i); n.world = floats(i); n.translation = floats(i); n.rotation = floats(i); n.scale = floats(i);
            g.nodes.add(n);
        }
        if (i.readLong() != MAGIC) throw new IOException("Abbild unvollständig");
        return g;
    }

    // ------------------------------------------------------------ Felder

    private static void floats(DataOutputStream o, float[] a) throws IOException {
        if (a == null) { o.writeInt(-1); return; }
        o.writeInt(a.length);
        ByteBuffer b = ByteBuffer.allocate(a.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        b.asFloatBuffer().put(a);
        o.write(b.array());
    }

    private static float[] floats(DataInputStream i) throws IOException {
        int n = i.readInt();
        if (n < 0) return null;
        byte[] d = new byte[n * 4];
        i.readFully(d);
        float[] a = new float[n];
        ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(a);
        return a;
    }

    private static void ints(DataOutputStream o, int[] a) throws IOException {
        if (a == null) { o.writeInt(-1); return; }
        o.writeInt(a.length);
        ByteBuffer b = ByteBuffer.allocate(a.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        b.asIntBuffer().put(a);
        o.write(b.array());
    }

    private static int[] ints(DataInputStream i) throws IOException {
        int n = i.readInt();
        if (n < 0) return null;
        byte[] d = new byte[n * 4];
        i.readFully(d);
        int[] a = new int[n];
        ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(a);
        return a;
    }

    private static void bytes(DataOutputStream o, byte[] a) throws IOException {
        o.writeInt(a.length);
        o.write(a);
    }

    private static byte[] bytes(DataInputStream i) throws IOException {
        byte[] d = new byte[i.readInt()];
        i.readFully(d);
        return d;
    }
}
