package com.dan.avatar;

import com.dan.gltf.Gltf;
import com.dan.gltf.Json;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Das Skelett des Grundkörpers (Rig {@code game_engine}, 53 Knochen) in glTF-Achsen.
 * <ul>
 *   <li><b>Ruhe nach Morphs:</b> Die Gelenke wandern mit den Morphzielen (aus Blender je Ziel
 *       gemessen, {@code avatar_base.rig.json}); die Ausrichtung bleibt die der Mitte.</li>
 *   <li><b>Posen:</b> je Gelenk eine örtliche starre Bewegung im Ruherahmen, so dass dieselbe Pose
 *       auf jede Statur passt.</li>
 *   <li><b>Fuß-IK:</b> Zwei-Knochen-IK für Oberschenkel und Wade; die Fußgelenke der Standbeine
 *       bleiben in Ruhehöhe, die Fußsohlen damit am Boden.</li>
 *   <li><b>Skinning:</b> je Gelenk S = Pose · Ruhe⁻¹, als Dual-Quaternion (Volumenerhalt) oder Matrix.</li>
 * </ul>
 */
public final class Skeleton {

    /** Gelenke in der Reihenfolge des glTF-Skins (so stehen sie in JOINTS_0). */
    public final String[] names;
    public final int[] parent;
    /** Ruhelage der Mitte: Weltmatrix je Gelenk (Inverse der inversen Bindematrix). */
    final double[][] rest;
    /** Ziel → (Gelenk, dx, dy, dz) der Gelenkköpfe. */
    private final Map<String, List<double[]>> targetDelta = new HashMap<>();
    /** Posen nach Namen. */
    public final Map<String, Pose> poses = new LinkedHashMap<>();
    private final Map<String, Integer> index = new HashMap<>();
    /** Gelenke so geordnet, dass Eltern vor Kindern kommen. */
    final int[] order;

    public Skeleton(Gltf g, Object rigJson) {
        Gltf.Skin skin = g.skins.get(0);
        int n = skin.joints.length;
        names = new String[n];
        parent = new int[n];
        rest = new double[n][];
        Map<Integer, Integer> nodeToJoint = new HashMap<>();
        for (int j = 0; j < n; j++) {
            names[j] = g.nodes.get(skin.joints[j]).name;
            nodeToJoint.put(skin.joints[j], j);
            index.put(names[j], j);
            rest[j] = M4.invert(M4.of(skin.inverseBind[j]));
        }
        for (int j = 0; j < n; j++) {
            int p = g.nodes.get(skin.joints[j]).parent;
            while (p >= 0 && !nodeToJoint.containsKey(p)) p = g.nodes.get(p).parent;
            parent[j] = p < 0 ? -1 : nodeToJoint.get(p);
        }
        order = new int[n];
        boolean[] done = new boolean[n];
        int k = 0;
        while (k < n)
            for (int j = 0; j < n; j++)
                if (!done[j] && (parent[j] < 0 || done[parent[j]])) { done[j] = true; order[k++] = j; }
        if (rigJson == null) return;
        List<Object> bones = Json.arr(rigJson, "knochen");
        Map<String, Object> ziele = Json.obj(rigJson, "ziele");
        if (ziele != null) for (Map.Entry<String, Object> e : ziele.entrySet()) {
            List<double[]> l = new ArrayList<>();
            for (Object row : (List<?>) e.getValue()) {
                List<?> r = (List<?>) row;
                String bn = String.valueOf(bones.get(((Number) r.get(0)).intValue()));
                Integer j = index.get(bn);
                if (j != null) l.add(new double[]{j, ((Number) r.get(1)).doubleValue(), ((Number) r.get(2)).doubleValue(), ((Number) r.get(3)).doubleValue()});
            }
            targetDelta.put(e.getKey(), l);
        }
        Map<String, Object> ps = Json.obj(rigJson, "posen");
        if (ps != null) for (Map.Entry<String, Object> e : ps.entrySet()) {
            double[][] d = new double[n][];
            @SuppressWarnings("unchecked")
            Map<String, Object> per = (Map<String, Object>) e.getValue();
            for (int j = 0; j < n; j++) {
                List<?> m = (List<?>) per.get(names[j]);
                d[j] = M4.identity();
                if (m != null) for (int i = 0; i < 16; i++) d[j][i] = ((Number) m.get(i)).doubleValue();
            }
            poses.put(e.getKey(), Pose.fromBlender(e.getKey(), this, d));
        }
    }

    public int joint(String name) {
        Integer j = index.get(name);
        return j == null ? -1 : j;
    }

    public int size() { return names.length; }

    /** Ruhelage für die Gewichte einer Person: Gelenkköpfe verschoben, Ausrichtung wie in der Mitte. */
    public double[][] restFor(Map<String, Double> weights) {
        double[][] r = new double[rest.length][];
        double[][] d = new double[rest.length][3];
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            List<double[]> l = targetDelta.get(e.getKey());
            if (l == null) continue;
            double w = e.getValue();
            for (double[] x : l) {
                int j = (int) x[0];
                d[j][0] += w * x[1]; d[j][1] += w * x[2]; d[j][2] += w * x[3];
            }
        }
        for (int j = 0; j < rest.length; j++) {
            r[j] = rest[j].clone();
            r[j][12] += d[j][0]; r[j][13] += d[j][1]; r[j][14] += d[j][2];
        }
        return r;
    }

    /** Örtliche Ruhematrix L = Ruhe(Eltern)⁻¹ · Ruhe. */
    static double[] local(double[][] r, int[] parent, int j) {
        return parent[j] < 0 ? r[j] : M4.mul(M4.invRigid(r[parent[j]]), r[j]);
    }

    /**
     * Weltmatrizen der Gelenke in einer Pose für die gegebene Ruhelage, mit Fuß-IK
     * (Standbeine: Fußgelenk in Ruhehöhe, Fuß in Ruhe-Ausrichtung).
     */
    public double[][] pose(Pose p, double[][] r) {
        return pose(p, r, true);
    }

    /** Wie {@link #pose(Pose, double[][])}; {@code ik = false}: genau die Pose aus Blender, ohne Boden und Fuß-IK. */
    public double[][] pose(Pose p, double[][] r, boolean ik) {
        return pose(p, r, ik, null);
    }

    /**
     * Wie {@link #pose(Pose, double[][], boolean)}, dazu eine Zugabe des Verhaltens (Phase 7): Atmen,
     * Kopf, Gewichtsverlagerung. Die Zugabe wirkt nach der Pose und vor Boden und Fuß-IK; die Füße der
     * Standbeine bleiben dort, wo die Pose sie hingestellt hat.
     */
    public double[][] pose(Pose p, double[][] r, boolean ik, Additive add) {
        int n = rest.length;
        double[][] w = new double[n][];
        for (int j : order) {
            double[] q = p == null ? M4.identity() : p.local[j];
            double[] l = local(r, parent, j);
            w[j] = parent[j] < 0 ? M4.mul(l, q) : M4.mul(w[parent[j]], M4.mul(l, q));
        }
        if (ik && p != null && p.fitRoot) fitRoot(w, r);
        Map<String, double[]> feet = new HashMap<>();
        if (add != null && !add.isEmpty()) {
            if (ik && p != null) for (String side : p.supportLegs) {
                int ft = joint("foot_" + side);
                if (ft >= 0) feet.put(side, M4.pos(w[ft]));
            }
            applyAdditive(w, add);
        }
        if (ik && p != null) for (String side : p.supportLegs) legIk(w, r, side, feet.get(side));
        return w;
    }

    /**
     * Zugabe zur Pose in Figur-Koordinaten (y oben, Blick +z): Verschiebung des Beckens und je Gelenk
     * eine Drehung um das Gelenk (Achse · Winkel in Bogenmaß, Weltachsen). Eltern werden vor Kindern
     * gedreht, die Kinder folgen.
     */
    public static final class Additive {
        public final double[] pelvis = new double[3];
        public final Map<String, double[]> rot = new LinkedHashMap<>();

        /** Drehung um eine Weltachse (wird zu einer schon gesetzten Drehung dieses Gelenks addiert). */
        public Additive rotate(String joint, double ax, double ay, double az, double rad) {
            double[] v = rot.computeIfAbsent(joint, k -> new double[3]);
            double l = Math.sqrt(ax * ax + ay * ay + az * az);
            if (l < 1e-12 || rad == 0) return this;
            v[0] += ax / l * rad; v[1] += ay / l * rad; v[2] += az / l * rad;
            return this;
        }

        public boolean isEmpty() {
            if (pelvis[0] != 0 || pelvis[1] != 0 || pelvis[2] != 0) return false;
            for (double[] v : rot.values()) if (v[0] != 0 || v[1] != 0 || v[2] != 0) return false;
            return true;
        }

        public void clear() { java.util.Arrays.fill(pelvis, 0); rot.clear(); }
    }

    private void applyAdditive(double[][] w, Additive add) {
        int pv = joint("pelvis");
        if (pv >= 0 && (add.pelvis[0] != 0 || add.pelvis[1] != 0 || add.pelvis[2] != 0))
            applyToSubtree(w, pv, M4.translation(add.pelvis[0], add.pelvis[1], add.pelvis[2]));
        for (int j : order) {
            double[] v = add.rot.get(names[j]);
            if (v == null) continue;
            double a = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
            if (a < 1e-12) continue;
            applyToSubtree(w, j, M4.about(M4.rotation(v[0] / a, v[1] / a, v[2] / a, a), M4.pos(w[j])));
        }
    }

    /** Skin-Matrix eines Gelenks (Ruhe → Pose), 4×4 spaltenweise. */
    public static double[] skinMatrix(double[][] posed, double[][] r, int j) {
        return M4.mul(posed[j], M4.invRigid(r[j]));
    }

    /** Drehanteil der Skin-Matrix eines Gelenks (Ruhe → Pose), 3×3 spaltenweise. */
    public static double[] skinRotation(double[][] posed, double[][] r, int j) {
        double[] s = M4.mul(posed[j], M4.invRigid(r[j]));
        return new double[]{s[0], s[1], s[2], s[4], s[5], s[6], s[8], s[9], s[10]};
    }

    /** Ganze Pose senkrecht so verschieben, dass das tiefere Fußgelenk auf Ruhehöhe steht (Sitzen). */
    private void fitRoot(double[][] w, double[][] r) {
        int fl = joint("foot_l"), fr = joint("foot_r");
        if (fl < 0 || fr < 0) return;
        double restY = Math.min(r[fl][13], r[fr][13]);
        double nowY = Math.min(w[fl][13], w[fr][13]);
        double dy = restY - nowY;
        for (double[] m : w) m[13] += dy;
    }

    /** Zwei-Knochen-IK: Fußgelenk auf (x, Ruhehöhe, z), Knie in seiner Ebene, Fuß wie in Ruhe ausgerichtet (Sohle flach). */
    private void legIk(double[][] w, double[][] r, String side, double[] keep) {
        int th = joint("thigh_" + side), ca = joint("calf_" + side), ft = joint("foot_" + side);
        if (th < 0 || ca < 0 || ft < 0) return;
        double[] h = M4.pos(w[th]), k = M4.pos(w[ca]), a = M4.pos(w[ft]);
        double[] t = keep != null ? new double[]{keep[0], r[ft][13], keep[2]} : new double[]{a[0], r[ft][13], a[2]};
        double la = dist(h, k), lb = dist(k, a);
        double[] ht = sub(t, h);
        double d = Math.max(1e-6, Math.min(len(ht), la + lb - 1e-5));
        double[] u = norm(ht);
        double[] hk = sub(k, h);
        double[] perp = sub(hk, scale(u, dot(hk, u)));
        if (len(perp) < 1e-9) perp = new double[]{0, 0, -1};
        perp = norm(perp);
        double cosA = Math.max(-1, Math.min(1, (la * la + d * d - lb * lb) / (2 * la * d)));
        double sinA = Math.sqrt(1 - cosA * cosA);
        double[] k2 = add(h, add(scale(u, la * cosA), scale(perp, la * sinA)));
        double[] t2 = add(h, scale(u, d));
        double[] footRot = r[ft].clone();         // Fuß wie in Ruhe: Sohle flach
        // Oberschenkel: hk → k2−h
        double[] r1 = M4.about(M4.rotationBetween(norm(hk), norm(sub(k2, h))), h);
        applyToSubtree(w, th, r1);
        // Wade: (Fuß − Knie) → (t2 − k2)
        double[] a1 = M4.pos(w[ft]);
        double[] r2 = M4.about(M4.rotationBetween(norm(sub(a1, k2)), norm(sub(t2, k2))), k2);
        applyToSubtree(w, ca, r2);
        // Fuß: Ausrichtung wie in Ruhe, Lage am Ziel; Kinder (Ballen) folgen
        double[] newFoot = footRot.clone();
        newFoot[12] = t2[0]; newFoot[13] = t2[1]; newFoot[14] = t2[2];
        double[] fix = M4.mul(newFoot, M4.invRigid(w[ft]));
        applyToSubtree(w, ft, fix);
    }

    /**
     * Fußgelenk einer Seite an einen Ort (Figur-Koordinaten der Pose) bringen, Zwei-Knochen-IK mit dem Knie in
     * seiner Ebene; der Fuß wie in Ruhe ausgerichtet, um {@code pitch} (rad, positiv: Spitze hoch) um die
     * Querachse gekippt (Phase 11, Gehen). Gibt zurück, wie weit das Ziel außer Reichweite lag (m, 0 = erreicht).
     */
    public double footTo(double[][] w, double[][] r, String side, double[] target, double pitch, double yaw) {
        int th = joint("thigh_" + side), ca = joint("calf_" + side), ft = joint("foot_" + side);
        if (th < 0 || ca < 0 || ft < 0) return 0;
        double[] h = M4.pos(w[th]), k = M4.pos(w[ca]), a = M4.pos(w[ft]);
        double la = dist(h, k), lb = dist(k, a);
        double[] ht = sub(target, h);
        double want = len(ht);
        double d = Math.max(1e-6, Math.min(want, la + lb - 1e-5));
        double[] u = norm(ht);
        double[] hk = sub(k, h);
        double[] perp = sub(hk, scale(u, dot(hk, u)));
        if (len(perp) < 1e-9) perp = new double[]{0, 0, 1};
        perp = norm(perp);
        // Knie zeigt nach vorn (+z), nie nach hinten
        if (perp[2] < 0.2) { perp = sub(new double[]{0, 0, 1}, scale(u, u[2])); perp = norm(perp); }
        double cosA = Math.max(-1, Math.min(1, (la * la + d * d - lb * lb) / (2 * la * d)));
        double sinA = Math.sqrt(1 - cosA * cosA);
        double[] k2 = add(h, add(scale(u, la * cosA), scale(perp, la * sinA)));
        double[] t2 = add(h, scale(u, d));
        applyToSubtree(w, th, M4.about(M4.rotationBetween(norm(hk), norm(sub(k2, h))), h));
        double[] a1 = M4.pos(w[ft]);
        applyToSubtree(w, ca, M4.about(M4.rotationBetween(norm(sub(a1, k2)), norm(sub(t2, k2))), k2));
        double[] newFoot = M4.mul(M4.rotation(0, 1, 0, yaw), M4.mul(M4.rotation(1, 0, 0, pitch), r[ft]));
        newFoot[12] = t2[0]; newFoot[13] = t2[1]; newFoot[14] = t2[2];
        applyToSubtree(w, ft, M4.mul(newFoot, M4.invRigid(w[ft])));
        return Math.max(0, want - (la + lb - 1e-5));
    }

    /** Eine starre Bewegung {@code m} auf ein Gelenk und alle seine Kinder anwenden (Weltmatrizen). */
    public void moveSubtree(double[][] w, int j, double[] m) { applyToSubtree(w, j, m); }

    /** Drehung (Achse in Figur-Koordinaten, Einheitsvektor) um das Gelenk {@code j}, mit Kindern. */
    public void rotateAt(double[][] w, int j, double ax, double ay, double az, double rad) {
        if (j < 0 || Math.abs(rad) < 1e-12) return;
        applyToSubtree(w, j, M4.about(M4.rotation(ax, ay, az, rad), M4.pos(w[j])));
    }

    /** Gelenk {@code j} so drehen, dass die Richtung zu seinem Kind {@code c} nach {@code dir} zeigt (Anteil {@code t}). */
    public void aim(double[][] w, int j, int c, double[] dir, double t) {
        if (j < 0 || c < 0 || t <= 0) return;
        double[] a = norm(sub(M4.pos(w[c]), M4.pos(w[j]))), b = norm(dir);
        double[] m = M4.rotationBetween(a, b);
        if (t < 1) {
            double cx = a[1] * b[2] - a[2] * b[1], cy = a[2] * b[0] - a[0] * b[2], cz = a[0] * b[1] - a[1] * b[0];
            double s = Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (s < 1e-12) return;
            m = M4.rotation(cx / s, cy / s, cz / s, t * Math.atan2(s, dot(a, b)));
        }
        applyToSubtree(w, j, M4.about(m, M4.pos(w[j])));
    }

    /** Alle Gelenke um die Hochachse durch den Ursprung drehen (Richtung der Person, rad, 0 = Blick +z). */
    public static void turn(double[][] w, double yaw) {
        if (Math.abs(yaw) < 1e-12) return;
        double[] m = M4.rotation(0, 1, 0, yaw);
        for (int j = 0; j < w.length; j++) w[j] = M4.mul(m, w[j]);
    }

    private int[][] desc;

    private void applyToSubtree(double[][] w, int j, double[] m) {
        if (desc == null) {
            int[][] d = new int[w.length][];
            for (int k = 0; k < w.length; k++) {
                int cnt = 0;
                for (int c = 0; c < w.length; c++) if (isDescendant(c, k)) cnt++;
                d[k] = new int[cnt];
                cnt = 0;
                for (int c = 0; c < w.length; c++) if (isDescendant(c, k)) d[k][cnt++] = c;
            }
            desc = d;
        }
        w[j] = M4.mul(m, w[j]);
        for (int c : desc[j]) w[c] = M4.mul(m, w[c]);
    }

    private boolean isDescendant(int c, int j) {
        for (int p = parent[c]; p >= 0; p = parent[p]) if (p == j) return true;
        return false;
    }

    /** Skin-Matrizen S = Pose · Ruhe⁻¹. */
    public static double[][] skin(double[][] posed, double[][] r) {
        double[][] s = new double[posed.length][];
        for (int j = 0; j < posed.length; j++) s[j] = M4.mul(posed[j], M4.invRigid(r[j]));
        return s;
    }

    /** Skin als Dual-Quaternionen (8 floats je Gelenk). */
    public static float[] toDq(double[][] s) {
        float[] out = new float[s.length * 8];
        for (int j = 0; j < s.length; j++) Dq.fromMatrix(s[j], out, j * 8);
        return out;
    }

    /** Skin als 3×4-Matrizen (12 floats je Gelenk, spaltenweise) für lineares Skinning. */
    public static float[] toMat(double[][] s) {
        float[] out = new float[s.length * 12];
        for (int j = 0; j < s.length; j++)
            for (int c = 0; c < 4; c++)
                for (int r = 0; r < 3; r++) out[j * 12 + c * 3 + r] = (float) s[j][c * 4 + r];
        return out;
    }

    /** Lage eines Gelenks (Weltmatrix-Ursprung). */
    public static double[] position(double[][] w, int j) { return M4.pos(w[j]); }

    // ------------------------------------------------------------ Vektoren

    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static double[] add(double[] a, double[] b) { return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]}; }
    static double[] scale(double[] a, double s) { return new double[]{a[0] * s, a[1] * s, a[2] * s}; }
    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double len(double[] a) { return Math.sqrt(dot(a, a)); }
    static double dist(double[] a, double[] b) { return len(sub(a, b)); }
    static double[] norm(double[] a) { double l = len(a); return l < 1e-15 ? new double[]{0, 1, 0} : scale(a, 1 / l); }
}
