package com.dan.avatar;

import com.dan.gltf.Gltf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Der Körper einer Person: alle Netze des Grundkörpers (Körper, Augen, Zähne, Zunge, Brauen,
 * Wimpern, Haar, Kleidung), gemischt nach den Gewichten der {@link Person} (F2, vorerst auf dem
 * Prozessor). Normalen werden nach dem Mischen neu gerechnet und über Nähte hinweg geglättet
 * (Ecken mit gleicher Blender-Nummer {@code _BLIDX} teilen eine Normale).
 *
 * <pre>
 *   Body b = new Body(gltf);
 *   b.mix(person);                  // oder b.mix(weightsMap)
 *   for (Body.Part p : b.parts) { p.position, p.normal }
 * </pre>
 */
public final class Body {

    public final Gltf model;
    public final List<Part> parts = new ArrayList<>();
    /** Unterste Höhe des Körpers nach dem letzten Mischen (zum Aufstellen auf den Boden). */
    public float footY;
    /** Zahl der Ziele mit Gewicht ≠ 0 beim letzten Mischen. */
    public int activeTargets;
    /** Dauer des letzten Mischens in ms. */
    public double mixMs;

    /** Ein Teil eines Netzes mit seinen gemischten Lagen und Normalen. */
    public static final class Part {
        public final Gltf.Mesh mesh;
        public final Gltf.Primitive prim;
        public final float[] position, normal;
        /** Ecke → Gruppe gleicher Blender-Ecke (für glatte Normalen über UV-Nähte). */
        final int[] weld;
        final int groups;
        /** Ist dies der Körper (das Netz mit den meisten Morphzielen)? */
        public boolean isBody;
        /** Zusätzlich verdeckt (Bits wie {@code _VERDECKT}), für Kleidung ohne MPFB-Gruppe; siehe {@link Wardrobe#cover}. */
        public int[] extraCover;

        Part(Gltf.Mesh m, Gltf.Primitive p) {
            mesh = m;
            prim = p;
            position = p.position.clone();
            normal = p.normal != null ? p.normal.clone() : new float[p.position.length];
            float[] bl = p.extra.get("_BLIDX");
            weld = new int[p.vertexCount];
            if (bl != null) {
                Map<Integer, Integer> map = new HashMap<>();
                for (int i = 0; i < p.vertexCount; i++) weld[i] = map.computeIfAbsent(Math.round(bl[i]), k -> map.size());
                groups = map.size();
            } else {
                for (int i = 0; i < p.vertexCount; i++) weld[i] = i;
                groups = p.vertexCount;
            }
        }

        /**
         * Dreiecke zum Zeichnen: ohne die, deren drei Ecken alle unter der Kleidung liegen
         * (Merkmal {@code _VERDECKT} aus den MPFB-Gruppen "Delete.…"; seit Fassung 1.7 ein Bit je Teil).
         * Wie bis Phase 9 mit dem Alltags-Outfit (Bit 0).
         */
        public int[] visibleIndices() { return visibleIndices(1); }

        /** Wie {@link #visibleIndices()} für die getragenen Teile {@code worn} (Bits, siehe {@link Wardrobe}). */
        public int[] visibleIndices(int worn) {
            float[] h0 = prim.extra.get("_VERDECKT");
            int[] ix = prim.indices;
            if ((h0 == null && extraCover == null) || worn == 0) return ix;
            float[] h = new float[prim.vertexCount];
            for (int i = 0; i < h.length; i++) h[i] = (float) ((h0 == null ? 0 : (int) h0[i]) | (extraCover == null ? 0 : extraCover[i]));
            int[] out = new int[ix.length];
            int n = 0;
            for (int k = 0; k + 2 < ix.length; k += 3)
                if (((int) h[ix[k]] & worn) == 0 || ((int) h[ix[k + 1]] & worn) == 0 || ((int) h[ix[k + 2]] & worn) == 0) {
                    out[n++] = ix[k]; out[n++] = ix[k + 1]; out[n++] = ix[k + 2];
                }
            return java.util.Arrays.copyOf(out, n);
        }

        /** Ecke → Gruppe gleicher Blender-Ecke. */
        public int[] weld() { return weld; }

        public int groups() { return groups; }

        /** Blender-Nummer einer Ecke oder −1. */
        public int blenderIndex(int i) {
            float[] bl = prim.extra.get("_BLIDX");
            return bl == null ? -1 : Math.round(bl[i]);
        }
    }

    /** Ecken nahe der Sohle (Grundform y < 0,12 m) für die schnelle Fußhöhe. */
    private int[] soleCand;

    public Body(Gltf model) {
        this.model = model;
        Part body = null;
        for (Gltf.Mesh m : model.meshes)
            for (Gltf.Primitive p : m.primitives) {
                Part part = new Part(m, p);
                parts.add(part);
                if (body == null || m.targetNames.size() > body.mesh.targetNames.size()) body = part;
            }
        if (body != null) body.isBody = true;
        if (body != null) {
            int c = 0;
            int[] tmp = new int[body.prim.vertexCount];
            for (int i = 0; i < body.prim.vertexCount; i++) if (body.prim.position[i * 3 + 1] < 0.12f) tmp[c++] = i;
            soleCand = java.util.Arrays.copyOf(tmp, c);
        }
    }

    /**
     * Unterste Höhe des Körpers für diese Gewichte, ohne alles zu mischen (nur Ecken nahe der Sohle).
     * Gleiche Rechnung wie {@link #mix(Map)} für diese Ecken.
     */
    public float footY(Map<String, Double> weights) {
        Part b = body();
        if (b == null || soleCand == null) return 0;
        java.util.HashMap<Integer, Integer> pos = new java.util.HashMap<>();
        float[] y = new float[soleCand.length];
        for (int k = 0; k < soleCand.length; k++) { pos.put(soleCand[k], k); y[k] = b.prim.position[soleCand[k] * 3 + 1]; }
        for (Gltf.Target tg : b.prim.targets) {
            Double w = weights.get(tg.name);
            if (w == null || w == 0) continue;
            float fw = (float) (double) w;
            for (int j = 0; j < tg.index.length; j++) {
                Integer k = pos.get(tg.index[j]);
                if (k != null) y[k] += fw * tg.dPosition[j * 3 + 1];
            }
        }
        float lo = Float.MAX_VALUE;
        for (float v : y) lo = Math.min(lo, v);
        return lo;
    }

    public Part body() {
        for (Part p : parts) if (p.isBody) return p;
        return parts.isEmpty() ? null : parts.get(0);
    }

    public void mix(Person person) {
        mix(person.weights());
    }

    /** Mischt alle Teile: Lage = Grundform + Σ Gewicht · Ziel, dann Normalen neu. */
    public void mix(Map<String, Double> weights) {
        long t0 = System.nanoTime();
        int[] active = new int[1];
        IntStream.range(0, parts.size()).parallel().forEach(k -> {
            Part part = parts.get(k);
            Gltf.Primitive p = part.prim;
            float[] pos = part.position;
            System.arraycopy(p.position, 0, pos, 0, pos.length);
            int act = 0;
            for (int t = 0; t < p.targets.size(); t++) {
                Gltf.Target tg = p.targets.get(t);
                Double w = weights.get(tg.name);
                if (w == null || w == 0) continue;
                act++;
                float fw = (float) (double) w;
                int[] ix = tg.index;
                float[] d = tg.dPosition;
                for (int j = 0; j < ix.length; j++) {
                    int o = ix[j] * 3;
                    pos[o] += fw * d[j * 3];
                    pos[o + 1] += fw * d[j * 3 + 1];
                    pos[o + 2] += fw * d[j * 3 + 2];
                }
            }
            if (part.isBody) active[0] = act;
            normals(part);
        });
        activeTargets = active[0];
        Part b = body();
        float lo = Float.MAX_VALUE;
        if (b != null) for (int i = 1; i < b.position.length; i += 3) lo = Math.min(lo, b.position[i]);
        footY = lo == Float.MAX_VALUE ? 0 : lo;
        mixMs = (System.nanoTime() - t0) / 1e6;
    }

    /** Flächengewichtete Normalen, über gleiche Blender-Ecken zusammengefasst. */
    static void normals(Part part) {
        float[] pos = part.position;
        int[] idx = part.prim.indices;
        double[] acc = new double[part.groups * 3];
        for (int k = 0; k + 2 < idx.length; k += 3) {
            int a = idx[k], b = idx[k + 1], c = idx[k + 2];
            double ux = pos[b * 3] - pos[a * 3], uy = pos[b * 3 + 1] - pos[a * 3 + 1], uz = pos[b * 3 + 2] - pos[a * 3 + 2];
            double vx = pos[c * 3] - pos[a * 3], vy = pos[c * 3 + 1] - pos[a * 3 + 1], vz = pos[c * 3 + 2] - pos[a * 3 + 2];
            double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            for (int v : new int[]{a, b, c}) {
                int g = part.weld[v] * 3;
                acc[g] += nx; acc[g + 1] += ny; acc[g + 2] += nz;
            }
        }
        float[] n = part.normal;
        float[] orig = part.prim.normal;
        for (int i = 0; i < part.prim.vertexCount; i++) {
            int g = part.weld[i] * 3;
            double l = Math.sqrt(acc[g] * acc[g] + acc[g + 1] * acc[g + 1] + acc[g + 2] * acc[g + 2]);
            if (l > 1e-20) {
                n[i * 3] = (float) (acc[g] / l); n[i * 3 + 1] = (float) (acc[g + 1] / l); n[i * 3 + 2] = (float) (acc[g + 2] / l);
            } else if (orig != null) {
                System.arraycopy(orig, i * 3, n, i * 3, 3);
            }
        }
    }
}
