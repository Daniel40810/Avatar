package com.dan.gltf.demo;

import com.dan.gltf.Gltf;
import com.dan.gltf.GltfCache;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Prüfung von com.dan.gltf ohne Fenster und ohne Grafikkarte; Ende „Alles in Ordnung.“
 * Baut eine kleine .glb im Speicher (Viereck mit verschachtelter Pufferansicht, dünn besetztem
 * Morphziel, normalisierten UV, Skin mit zwei Gelenken, eigenem Attribut und eingebettetem PNG),
 * liest sie und vergleicht Wert für Wert. Mit Dateiname: liest die Datei und gibt einen Steckbrief aus.
 *
 * <pre>
 *   GltfCheck                 Selbsttest
 *   GltfCheck modell.glb      Steckbrief einer Datei (mit Abbild-Rundreise)
 * </pre>
 */
public final class GltfCheck {

    private static int ok, bad;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) { report(Path.of(args[0])); return; }
        System.out.println("com.dan.gltf · Prüfung");
        byte[] glb = sample();
        Gltf g = Gltf.read(glb);
        check("Kopf und JSON gelesen, keine Hinweise " + g.warnings, g.warnings.isEmpty());
        Gltf.Mesh m = g.meshes.get(0);
        Gltf.Primitive p = m.primitives.get(0);
        check("4 Ecken, 2 Dreiecke", p.vertexCount == 4 && p.indices.length == 6);
        check("Lagen aus verschachtelter Ansicht", Arrays.equals(p.position, new float[]{0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0}));
        check("Normalen aus verschachtelter Ansicht", p.normal[2] == 1 && p.normal[11] == 1);
        check("UV normalisiert (unsigned short)", Math.abs(p.uv0[2] - 1f) < 1e-6 && Math.abs(p.uv0[5] - 1f) < 1e-6 && p.uv0[0] == 0);
        check("Tangenten gerechnet, senkrecht und Einheit", p.tangentsComputed && tangentsOk(p));
        check("Tangente zeigt in +x (u wächst mit x)", Math.abs(p.tangent[0] - 1) < 1e-6 && p.tangent[3] == 1);
        check("Morphziel dünn besetzt: 1 Ecke", p.targets.size() == 1 && p.targets.get(0).count() == 1 && p.targets.get(0).index[0] == 2);
        check("Morphziel-Wert", Math.abs(p.targets.get(0).dPosition[2] - 0.25f) < 1e-7);
        check("Morphziel-Name aus extras.targetNames", "lift".equals(m.targetNames.get(0)) && "lift".equals(p.targets.get(0).name));
        check("Gelenke und Gewichte", p.joints[4] == 1 && Math.abs(p.weights[4] - 0.5f) < 1e-6);
        check("eigenes Attribut _BLIDX", p.extra.containsKey("_BLIDX") && p.extra.get("_BLIDX")[3] == 7);
        check("Skin: 2 Gelenke, inverse Bindematrix", g.skins.size() == 1 && g.skins.get(0).joints.length == 2 && g.skins.get(0).inverseBind[1][13] == -1);
        check("Knoten: Weltmatrix aus Hierarchie", Math.abs(g.nodes.get(2).world[13] - 1) < 1e-6);
        check("Material: Farbe, Textur, MASK", g.materials.get(0).baseColorTexture == 0 && "MASK".equals(g.materials.get(0).alphaMode)
                && Math.abs(g.materials.get(0).baseColor[1] - 0.5f) < 1e-6);
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(g.images.get(0).data));
        check("eingebettetes PNG lesbar", img != null && img.getWidth() == 2 && "image/png".equals(g.images.get(0).mimeType));

        // Abbild hin und zurück
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        GltfCache.write(g, bo);
        Gltf c = GltfCache.read(new ByteArrayInputStream(bo.toByteArray()));
        Gltf.Primitive q = c.meshes.get(0).primitives.get(0);
        check("Abbild: gleiche Daten", Arrays.equals(q.position, p.position) && Arrays.equals(q.tangent, p.tangent)
                && Arrays.equals(q.targets.get(0).dPosition, p.targets.get(0).dPosition) && c.sha256.equals(g.sha256)
                && Arrays.equals(c.images.get(0).data, g.images.get(0).data) && Arrays.equals(c.nodes.get(2).world, g.nodes.get(2).world));
        Path dir = Files.createTempDirectory("gltfcheck");
        GltfCache.load(glb, dir, "probe");
        String first = GltfCache.lastSource;
        GltfCache.load(glb, dir, "probe");
        check("Abbild auf Platte: erst gelesen, dann aus dem Abbild", first.startsWith("Datei") && "Abbild".equals(GltfCache.lastSource));
        Gltf twice = Gltf.read(glb);
        check("Reproduzierbar: zweimal gelesen, gleiche Tangenten", Arrays.equals(twice.meshes.get(0).primitives.get(0).tangent, p.tangent));

        // Fehlerfälle
        byte[] broken = Arrays.copyOf(glb, glb.length - 8);
        boolean threw = false;
        try { Gltf.read(broken); } catch (Exception e) { threw = true; }
        check("abgeschnittene Datei wird abgewiesen", threw);

        System.out.println();
        if (bad == 0) System.out.println(ok + " Prüfungen bestanden. Alles in Ordnung.");
        else { System.out.println(bad + " von " + (ok + bad) + " Prüfungen fehlgeschlagen."); System.exit(1); }
    }

    private static boolean tangentsOk(Gltf.Primitive p) {
        for (int i = 0; i < p.vertexCount; i++) {
            float tx = p.tangent[i * 4], ty = p.tangent[i * 4 + 1], tz = p.tangent[i * 4 + 2];
            double l = Math.sqrt(tx * tx + ty * ty + tz * tz);
            double d = tx * p.normal[i * 3] + ty * p.normal[i * 3 + 1] + tz * p.normal[i * 3 + 2];
            if (Math.abs(l - 1) > 1e-5 || Math.abs(d) > 1e-5) return false;
        }
        return true;
    }

    private static void check(String what, boolean pass) {
        if (pass) { ok++; System.out.println("  ✓ " + what); }
        else { bad++; System.out.println("  ✗ " + what); }
    }

    // ------------------------------------------------------------ Steckbrief

    static void report(Path f) throws Exception {
        long t0 = System.nanoTime();
        byte[] d = Files.readAllBytes(f);
        Gltf g = Gltf.read(d);
        long t1 = System.nanoTime();
        System.out.printf(Locale.GERMANY, "%s · %.1f MB · gelesen in %.0f ms · %s%n", f.getFileName(), d.length / 1048576.0, (t1 - t0) / 1e6, g.generator);
        for (Gltf.Mesh m : g.meshes) {
            int v = 0, t = 0;
            long sparse = 0;
            for (Gltf.Primitive p : m.primitives) {
                v += p.vertexCount; t += p.indices.length / 3;
                for (Gltf.Target tg : p.targets) sparse += tg.count();
            }
            System.out.printf(Locale.GERMANY, "  %-20s %,7d Ecken %,7d Dreiecke %4d Morphziele (%,d Einträge)%n", m.name, v, t, m.targetNames.size(), sparse);
        }
        System.out.println("  Skins " + g.skins.size() + ", Knoten " + g.nodes.size() + ", Materialien " + g.materials.size() + ", Bilder " + g.images.size());
        for (String w : g.warnings) System.out.println("  Hinweis: " + w);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        GltfCache.write(g, bo);
        long t2 = System.nanoTime();
        GltfCache.read(new ByteArrayInputStream(bo.toByteArray()));
        long t3 = System.nanoTime();
        System.out.printf(Locale.GERMANY, "  Abbild %.1f MB, gelesen in %.0f ms%n", bo.size() / 1048576.0, (t3 - t2) / 1e6);
    }

    // ------------------------------------------------------------ Probe-Datei

    /** Baut eine kleine, gültige .glb mit allen Fällen, die der Lader kennen muss. */
    static byte[] sample() throws Exception {
        ByteBuffer bin = ByteBuffer.allocate(4096).order(ByteOrder.LITTLE_ENDIAN);
        // 0: verschachtelt Lage+Normale, 4 Ecken × 24 Byte
        float[][] pos = {{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}};
        for (float[] q : pos) { bin.putFloat(q[0]).putFloat(q[1]).putFloat(q[2]).putFloat(0).putFloat(0).putFloat(1); }
        int bvInter = 0, lenInter = bin.position();
        // UV als unsigned short normalisiert
        int bvUv = bin.position();
        int[][] uv = {{0, 0}, {65535, 0}, {65535, 65535}, {0, 65535}};
        for (int[] q : uv) { bin.putShort((short) q[0]).putShort((short) q[1]); }
        int lenUv = bin.position() - bvUv;
        // Indizes
        int bvIdx = bin.position();
        for (int i : new int[]{0, 1, 2, 0, 2, 3}) bin.putShort((short) i);
        bin.putShort((short) 0);
        int lenIdx = 12;
        // Gelenke (ubyte vec4) und Gewichte (float vec4)
        int bvJ = bin.position();
        int[][] jj = {{0, 0, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}, {0, 0, 0, 0}};
        for (int[] q : jj) for (int x : q) bin.put((byte) x);
        int bvW = bin.position();
        float[][] ww = {{1, 0, 0, 0}, {0.5f, 0.5f, 0, 0}, {1, 0, 0, 0}, {1, 0, 0, 0}};
        for (float[] q : ww) for (float x : q) bin.putFloat(x);
        // dünnes Morphziel: Index 2 (ubyte, auf 4 aufgefüllt), Wert (0, 0, 0.25)
        int bvSi = bin.position();
        bin.put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        int bvSv = bin.position();
        bin.putFloat(0).putFloat(0).putFloat(0.25f);
        // eigenes Attribut
        int bvX = bin.position();
        for (float x : new float[]{4, 5, 6, 7}) bin.putFloat(x);
        // inverse Bindematrizen
        int bvIb = bin.position();
        float[] id = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        for (float x : id) bin.putFloat(x);
        float[] ib1 = id.clone(); ib1[13] = -1;
        for (float x : ib1) bin.putFloat(x);
        // Bild
        BufferedImage im = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        im.setRGB(0, 0, 0xFFFF0000); im.setRGB(1, 0, 0xFF00FF00); im.setRGB(0, 1, 0xFF0000FF); im.setRGB(1, 1, 0x80FFFFFF);
        ByteArrayOutputStream pngO = new ByteArrayOutputStream();
        ImageIO.write(im, "png", pngO);
        byte[] png = pngO.toByteArray();
        int bvPng = bin.position();
        bin.put(png);
        while (bin.position() % 4 != 0) bin.put((byte) 0);
        int binLen = bin.position();

        String js = "{\"asset\":{\"version\":\"2.0\",\"generator\":\"GltfCheck\"},"
            + "\"buffers\":[{\"byteLength\":" + binLen + "}],"
            + "\"bufferViews\":["
            + "{\"buffer\":0,\"byteOffset\":" + bvInter + ",\"byteLength\":" + lenInter + ",\"byteStride\":24},"
            + "{\"buffer\":0,\"byteOffset\":" + bvUv + ",\"byteLength\":" + lenUv + "},"
            + "{\"buffer\":0,\"byteOffset\":" + bvIdx + ",\"byteLength\":" + lenIdx + "},"
            + "{\"buffer\":0,\"byteOffset\":" + bvJ + ",\"byteLength\":16},"
            + "{\"buffer\":0,\"byteOffset\":" + bvW + ",\"byteLength\":64},"
            + "{\"buffer\":0,\"byteOffset\":" + bvSi + ",\"byteLength\":1},"
            + "{\"buffer\":0,\"byteOffset\":" + bvSv + ",\"byteLength\":12},"
            + "{\"buffer\":0,\"byteOffset\":" + bvX + ",\"byteLength\":16},"
            + "{\"buffer\":0,\"byteOffset\":" + bvIb + ",\"byteLength\":128},"
            + "{\"buffer\":0,\"byteOffset\":" + bvPng + ",\"byteLength\":" + png.length + "}],"
            + "\"accessors\":["
            + "{\"bufferView\":0,\"byteOffset\":0,\"componentType\":5126,\"count\":4,\"type\":\"VEC3\",\"min\":[0,0,0],\"max\":[1,1,0]},"
            + "{\"bufferView\":0,\"byteOffset\":12,\"componentType\":5126,\"count\":4,\"type\":\"VEC3\"},"
            + "{\"bufferView\":1,\"componentType\":5123,\"normalized\":true,\"count\":4,\"type\":\"VEC2\"},"
            + "{\"bufferView\":2,\"componentType\":5123,\"count\":6,\"type\":\"SCALAR\"},"
            + "{\"bufferView\":3,\"componentType\":5121,\"count\":4,\"type\":\"VEC4\"},"
            + "{\"bufferView\":4,\"componentType\":5126,\"count\":4,\"type\":\"VEC4\"},"
            + "{\"componentType\":5126,\"count\":4,\"type\":\"VEC3\",\"sparse\":{\"count\":1,"
            + "\"indices\":{\"bufferView\":5,\"componentType\":5121},\"values\":{\"bufferView\":6}}},"
            + "{\"bufferView\":7,\"componentType\":5126,\"count\":4,\"type\":\"SCALAR\"},"
            + "{\"bufferView\":8,\"componentType\":5126,\"count\":2,\"type\":\"MAT4\"}],"
            + "\"images\":[{\"bufferView\":9,\"mimeType\":\"image/png\"}],"
            + "\"samplers\":[{\"magFilter\":9729,\"minFilter\":9987}],"
            + "\"textures\":[{\"source\":0,\"sampler\":0}],"
            + "\"materials\":[{\"name\":\"probe\",\"pbrMetallicRoughness\":{\"baseColorFactor\":[1,0.5,0.25,1],"
            + "\"baseColorTexture\":{\"index\":0},\"metallicFactor\":0,\"roughnessFactor\":0.6},\"alphaMode\":\"MASK\",\"alphaCutoff\":0.4}],"
            + "\"meshes\":[{\"name\":\"viereck\",\"primitives\":[{\"attributes\":{\"POSITION\":0,\"NORMAL\":1,\"TEXCOORD_0\":2,"
            + "\"JOINTS_0\":4,\"WEIGHTS_0\":5,\"_BLIDX\":7},\"indices\":3,\"material\":0,\"targets\":[{\"POSITION\":6}]}],"
            + "\"weights\":[0],\"extras\":{\"targetNames\":[\"lift\"]}}],"
            + "\"skins\":[{\"joints\":[1,2],\"inverseBindMatrices\":8}],"
            + "\"nodes\":[{\"name\":\"netz\",\"mesh\":0,\"skin\":0},{\"name\":\"wurzel\",\"children\":[2]},{\"name\":\"kind\",\"translation\":[0,1,0]}],"
            + "\"scenes\":[{\"nodes\":[0,1]}],\"scene\":0}";
        byte[] jb = js.getBytes(StandardCharsets.UTF_8);
        int jlen = (jb.length + 3) & ~3;
        int total = 12 + 8 + jlen + 8 + binLen;
        ByteBuffer out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x46546C67).putInt(2).putInt(total);
        out.putInt(jlen).putInt(0x4E4F534A).put(jb);
        for (int i = jb.length; i < jlen; i++) out.put((byte) ' ');
        out.putInt(binLen).putInt(0x004E4942).put(bin.array(), 0, binLen);
        return out.array();
    }
}
