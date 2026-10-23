package com.dan.gltf;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ein gelesenes glTF-2.0-Modell als reine Java-Daten: Knoten, Netze mit Teilen, Morphzielen
 * und Zusatzattributen, Skins, Materialwerte, Texturen und Bilder.
 *
 * <pre>
 *   Gltf g = Gltf.load(Path.of("modell.glb"));             // oder Gltf.read(bytes)
 *   Gltf g = GltfCache.load(datei, Path.of("daten/cache"));  // mit Abbild für schnellen Start
 *   for (Gltf.Mesh m : g.meshes) for (Gltf.Primitive p : m.primitives) { p.position, p.normal, ... }
 * </pre>
 *
 * Alle Felder sind öffentlich. Lagen in Metern, Achsen wie in glTF (Y oben, rechtshändig).
 */
public final class Gltf {

    /** SHA-256 der gelesenen Datei (hex). */
    public String sha256 = "";
    public String generator = "";
    public String copyright = "";
    public final List<Node> nodes = new ArrayList<>();
    public final List<Mesh> meshes = new ArrayList<>();
    public final List<Skin> skins = new ArrayList<>();
    public final List<Material> materials = new ArrayList<>();
    public final List<Texture> textures = new ArrayList<>();
    public final List<Image> images = new ArrayList<>();
    /** Wurzelknoten der Standardszene. */
    public int[] sceneRoots = new int[0];
    /** Hinweise beim Lesen (Spezifikation, Plausibilität); leer heißt sauber. */
    public final List<String> warnings = new ArrayList<>();

    // ------------------------------------------------------------ Teile

    public static final class Node {
        public String name = "";
        public int mesh = -1, skin = -1;
        public int[] children = new int[0];
        public int parent = -1;
        /** Örtliche Matrix (spaltenweise 4×4). */
        public float[] local = Mat.identity();
        /** Weltmatrix (spaltenweise 4×4), aus der Hierarchie gerechnet. */
        public float[] world = Mat.identity();
        public float[] translation = {0, 0, 0}, rotation = {0, 0, 0, 1}, scale = {1, 1, 1};
    }

    public static final class Mesh {
        public String name = "";
        public final List<Primitive> primitives = new ArrayList<>();
        /** Namen der Morphziele (aus {@code extras.targetNames}); sonst "ziel0", "ziel1", … */
        public final List<String> targetNames = new ArrayList<>();
        /** Vorgabegewichte der Morphziele. */
        public float[] weights = new float[0];
        /** Knoten, der dieses Netz trägt (oder -1). */
        public int node = -1;

        public int targetIndex(String name) { return targetNames.indexOf(name); }
    }

    public static final class Primitive {
        public int vertexCount;
        public float[] position;          // 3 je Ecke
        public float[] normal;            // 3 je Ecke oder null
        public float[] uv0;               // 2 je Ecke oder null
        /** 4 je Ecke (xyz, Händigkeit w); aus der Datei oder vom Lader gerechnet ({@link #tangentsComputed}). */
        public float[] tangent;
        public boolean tangentsComputed;
        public int[] joints;              // 4 je Ecke oder null
        public float[] weights;           // 4 je Ecke oder null
        public float[] color;             // 4 je Ecke oder null
        public int[] indices;             // Dreiecke
        public int material = -1;
        public int mode = 4;
        /** Morphziele in der Reihenfolge von {@link Mesh#targetNames}. */
        public final List<Target> targets = new ArrayList<>();
        /** Eigene Attribute, deren Name mit "_" beginnt (z. B. "_BLIDX"), je Ecke so viele Werte wie ihr Typ hat. */
        public final Map<String, float[]> extra = new java.util.LinkedHashMap<>();
        public final Map<String, Integer> extraComponents = new java.util.LinkedHashMap<>();
        /** Min und Max der Lagen (aus der Datei). */
        public float[] min = new float[3], max = new float[3];
    }

    /** Ein Morphziel, dünn besetzt: nur Ecken mit Änderung. */
    public static final class Target {
        public String name = "";
        public int[] index = new int[0];
        public float[] dPosition = new float[0];  // 3 je Eintrag
        public float[] dNormal;                   // 3 je Eintrag oder null
        public int count() { return index.length; }
    }

    public static final class Skin {
        public String name = "";
        public int[] joints = new int[0];
        public float[][] inverseBind = new float[0][];
        public int skeleton = -1;
    }

    public static final class Material {
        public String name = "";
        public float[] baseColor = {1, 1, 1, 1};
        public float metallic = 1, roughness = 1;
        public float[] emissive = {0, 0, 0};
        public int baseColorTexture = -1, normalTexture = -1, metallicRoughnessTexture = -1, occlusionTexture = -1, emissiveTexture = -1;
        public float normalScale = 1;
        /** OPAQUE, MASK oder BLEND. */
        public String alphaMode = "OPAQUE";
        public float alphaCutoff = 0.5f;
        public boolean doubleSided;
    }

    public static final class Texture {
        public int source = -1;
        public int magFilter = 9729, minFilter = 9987, wrapS = 10497, wrapT = 10497;
    }

    public static final class Image {
        public String name = "";
        public String mimeType = "";
        public byte[] data = new byte[0];
    }

    // ------------------------------------------------------------ Lesen

    public static Gltf load(Path file) throws IOException {
        return read(Files.readAllBytes(file));
    }

    /** Liest .glb (binär) oder .gltf (JSON mit eingebetteten Puffern als data:-URI). */
    public static Gltf read(byte[] data) throws IOException {
        return new GltfReader(data).read();
    }

    /** Ecken aller Netze. */
    public long vertexTotal() {
        long n = 0;
        for (Mesh m : meshes) for (Primitive p : m.primitives) n += p.vertexCount;
        return n;
    }

    /** Netz nach Namen oder null. */
    public Mesh mesh(String name) {
        for (Mesh m : meshes) if (m.name.equals(name)) return m;
        return null;
    }

    /** Kleine Matrixhilfen, spaltenweise. */
    public static final class Mat {
        private Mat() {}

        public static float[] identity() {
            return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        }

        public static float[] mul(float[] a, float[] b) {
            float[] r = new float[16];
            for (int c = 0; c < 4; c++)
                for (int rr = 0; rr < 4; rr++) {
                    float s = 0;
                    for (int k = 0; k < 4; k++) s += a[k * 4 + rr] * b[c * 4 + k];
                    r[c * 4 + rr] = s;
                }
            return r;
        }

        public static float[] trs(float[] t, float[] q, float[] s) {
            float x = q[0], y = q[1], z = q[2], w = q[3];
            float[] m = new float[16];
            m[0] = (1 - 2 * (y * y + z * z)) * s[0];
            m[1] = (2 * (x * y + z * w)) * s[0];
            m[2] = (2 * (x * z - y * w)) * s[0];
            m[4] = (2 * (x * y - z * w)) * s[1];
            m[5] = (1 - 2 * (x * x + z * z)) * s[1];
            m[6] = (2 * (y * z + x * w)) * s[1];
            m[8] = (2 * (x * z + y * w)) * s[2];
            m[9] = (2 * (y * z - x * w)) * s[2];
            m[10] = (1 - 2 * (x * x + y * y)) * s[2];
            m[12] = t[0]; m[13] = t[1]; m[14] = t[2]; m[15] = 1;
            return m;
        }

        public static float[] transformPoint(float[] m, float x, float y, float z) {
            return new float[]{m[0] * x + m[4] * y + m[8] * z + m[12],
                               m[1] * x + m[5] * y + m[9] * z + m[13],
                               m[2] * x + m[6] * y + m[10] * z + m[14]};
        }
    }
}
