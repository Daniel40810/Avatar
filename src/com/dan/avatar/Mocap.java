package com.dan.avatar;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Eine Bewegungsaufnahme im Format ASF/AMC der CMU-Datenbank (Phase 18): Skelett aus der ASF, je Bild die Drehungen
 * aus der AMC, daraus je Bild die Orte der Knochenenden in Metern (y oben). Vorwärtskinematik wie in der CMU-Anleitung:
 * Weltdrehung eines Knochens = Eltern · C · R(Bild) · C⁻¹ mit C aus der Achse der ASF; Ende = Anfang + Länge · Drehung ·
 * Richtung. Beim Laden wird die Aufnahme so gedreht, dass die Person im Mittel nach +z schaut, und waagerecht um den
 * Mittelpunkt des Beckens gelegt.
 */
public final class Mocap {

    /** Meter je Längeneinheit der CMU-Dateien: (1 / 0,45) · 2,54 / 100. */
    public static final double UNIT = (1.0 / 0.45) * 2.54 / 100;

    public final String name;
    public final double fps;
    public final String[] bones;
    final int[] parent;
    /** Länge je Knochen (m). */
    final double[] length;
    /** Je Bild: Ort des Beckens (Index 0, „root“) und der Knochenenden (m). */
    final double[][][] pos;

    private Mocap(String name, double fps, String[] bones, int[] parent, double[] length, double[][][] pos) {
        this.name = name; this.fps = fps; this.bones = bones; this.parent = parent; this.length = length; this.pos = pos;
    }

    public int frames() { return pos.length; }

    public double seconds() { return pos.length / fps; }

    public int bone(String n) { for (int i = 0; i < bones.length; i++) if (bones[i].equals(n)) return i; return -1; }

    /** Ende des Knochens b im Bild f (gebrochen: linear zwischen zwei Bildern). */
    public double[] end(int b, double f) {
        f = Math.max(0, Math.min(pos.length - 1, f));
        int i = (int) Math.floor(f);
        double u = f - i;
        double[] a = pos[i][b], c = pos[Math.min(pos.length - 1, i + 1)][b];
        return new double[]{a[0] + u * (c[0] - a[0]), a[1] + u * (c[1] - a[1]), a[2] + u * (c[2] - a[2])};
    }

    /** Anfang des Knochens b (Ende des Eltern, beim Becken sein Ort). */
    public double[] start(int b, double f) { return parent[b] < 0 ? end(b, f) : end(parent[b], f); }

    // ------------------------------------------------------------ Lesen

    static final class Bone {
        String name;
        double[] dir = new double[3];
        double len;
        double[] axis = new double[3];
        List<String> dof = new ArrayList<>();
    }

    public static Mocap read(File asf, File amc, double fps) throws IOException {
        String a = Files.readString(asf.toPath(), StandardCharsets.ISO_8859_1);
        Map<String, Bone> bones = new LinkedHashMap<>();
        Bone root = new Bone();
        root.name = "root";
        root.dof = List.of("tx", "ty", "tz", "rx", "ry", "rz");
        bones.put("root", root);
        String[] lines = a.split("\\R");
        int i = 0;
        Map<String, String> par = new HashMap<>();
        while (i < lines.length) {
            String l = lines[i].trim();
            if (l.equals("begin") && !inHierarchy(lines, i)) {
                Bone b = new Bone();
                for (i++; i < lines.length && !lines[i].trim().equals("end"); i++) {
                    String[] t = lines[i].trim().split("\\s+");
                    switch (t[0]) {
                        case "name" -> b.name = t[1];
                        case "direction" -> { for (int k = 0; k < 3; k++) b.dir[k] = Double.parseDouble(t[1 + k]); }
                        case "length" -> b.len = Double.parseDouble(t[1]) * UNIT;
                        case "axis" -> { for (int k = 0; k < 3; k++) b.axis[k] = Math.toRadians(Double.parseDouble(t[1 + k])); }
                        case "dof" -> { b.dof = new ArrayList<>(); for (int k = 1; k < t.length; k++) b.dof.add(t[k].toLowerCase()); }
                        default -> { }
                    }
                }
                bones.put(b.name, b);
            } else if (l.equals(":hierarchy")) {
                for (i++; i < lines.length && !lines[i].trim().equals("end"); i++) {
                    String[] t = lines[i].trim().split("\\s+");
                    if (t[0].equals("begin") || t.length < 2) continue;
                    for (int k = 1; k < t.length; k++) par.put(t[k], t[0]);
                }
            }
            i++;
        }
        // Reihenfolge: Eltern vor Kindern
        List<String> order = new ArrayList<>();
        order.add("root");
        boolean grew = true;
        while (grew) {
            grew = false;
            for (String n : bones.keySet()) if (!order.contains(n) && order.contains(par.getOrDefault(n, "root"))) { order.add(n); grew = true; }
        }
        int nb = order.size();
        String[] names = order.toArray(new String[0]);
        int[] parent = new int[nb];
        double[] len = new double[nb];
        double[][] C = new double[nb][], Ci = new double[nb][];
        for (int k = 0; k < nb; k++) {
            Bone b = bones.get(names[k]);
            parent[k] = k == 0 ? -1 : order.indexOf(par.getOrDefault(names[k], "root"));
            len[k] = b.len;
            C[k] = euler(b.axis[0], b.axis[1], b.axis[2]);
            Ci[k] = transpose(C[k]);
        }
        // Bilder
        List<double[][]> frames = new ArrayList<>();
        Map<String, double[]> vals = new HashMap<>();
        Runnable[] flush = new Runnable[1];
        flush[0] = () -> {
            if (vals.isEmpty()) return;
            double[][] p = new double[nb][];
            double[][] R = new double[nb][];
            for (int k = 0; k < nb; k++) {
                Bone b = bones.get(names[k]);
                double[] v = vals.getOrDefault(names[k], new double[0]);
                double rx = 0, ry = 0, rz = 0, tx = 0, ty = 0, tz = 0;
                for (int d = 0; d < b.dof.size() && d < v.length; d++) {
                    switch (b.dof.get(d)) {
                        case "rx" -> rx = Math.toRadians(v[d]);
                        case "ry" -> ry = Math.toRadians(v[d]);
                        case "rz" -> rz = Math.toRadians(v[d]);
                        case "tx" -> tx = v[d] * UNIT;
                        case "ty" -> ty = v[d] * UNIT;
                        case "tz" -> tz = v[d] * UNIT;
                        default -> { }
                    }
                }
                double[] rot = mul(C[k], mul(euler(rx, ry, rz), Ci[k]));
                if (k == 0) { R[k] = rot; p[k] = new double[]{tx, ty, tz}; }
                else {
                    R[k] = mul(R[parent[k]], rot);
                    double[] d = apply(R[k], b.dir);
                    double[] s = p[parent[k]];
                    p[k] = new double[]{s[0] + len[k] * d[0], s[1] + len[k] * d[1], s[2] + len[k] * d[2]};
                }
            }
            frames.add(p);
            vals.clear();
        };
        for (String raw : Files.readString(amc.toPath(), StandardCharsets.ISO_8859_1).split("\\R")) {
            String l = raw.trim();
            if (l.isEmpty() || l.startsWith("#") || l.startsWith(":")) continue;
            if (l.matches("\\d+")) { flush[0].run(); continue; }
            String[] t = l.split("\\s+");
            double[] v = new double[t.length - 1];
            for (int k = 1; k < t.length; k++) v[k - 1] = Double.parseDouble(t[k]);
            vals.put(t[0], v);
        }
        flush[0].run();
        double[][][] pos = frames.toArray(new double[0][][]);
        normalize(pos, names);
        return new Mocap(amc.getName().replaceFirst("\\.amc$", ""), fps, names, parent, len, pos);
    }

    private static boolean inHierarchy(String[] lines, int i) {
        for (int k = i - 1; k >= 0; k--) {
            String l = lines[k].trim();
            if (l.startsWith(":hierarchy")) return true;
            if (l.startsWith(":bonedata")) return false;
        }
        return false;
    }

    /** Im Mittel nach +z drehen und das Becken waagerecht in die Mitte legen. */
    private static void normalize(double[][][] pos, String[] names) {
        int lh = indexOf(names, "lfemur"), rh = indexOf(names, "rfemur"), lb = indexOf(names, "lowerback");
        int lhj = indexOf(names, "lhipjoint"), rhj = indexOf(names, "rhipjoint");
        double sx = 0, sz = 0, cx = 0, cz = 0;
        for (double[][] p : pos) {
            if (lhj < 0 || rhj < 0 || lb < 0) break;
            double[] x = sub(p[lhj], p[rhj]), up = sub(p[lb], p[0]);
            double[] fw = cross(x, up);            // links × oben = vorn
            sx += fw[0]; sz += fw[2];
            cx += p[0][0]; cz += p[0][2];
        }
        double yaw = Math.atan2(sx, sz);           // Richtung, in die die Person schaut
        cx /= Math.max(1, pos.length); cz /= Math.max(1, pos.length);
        double c = Math.cos(-yaw), s = Math.sin(-yaw);
        for (double[][] p : pos)
            for (double[] v : p) {
                double x = v[0] - cx, z = v[2] - cz;
                v[0] = c * x + s * z;
                v[2] = -s * x + c * z;
            }
    }

    private static int indexOf(String[] a, String n) { for (int i = 0; i < a.length; i++) if (a[i].equals(n)) return i; return -1; }

    // ------------------------------------------------------------ 3×3 (zeilenweise)

    /** R = Rz · Ry · Rx (feste Achsen x, dann y, dann z). */
    static double[] euler(double x, double y, double z) {
        double cx = Math.cos(x), sx = Math.sin(x), cy = Math.cos(y), sy = Math.sin(y), cz = Math.cos(z), sz = Math.sin(z);
        double[] rx = {1, 0, 0, 0, cx, -sx, 0, sx, cx}, ry = {cy, 0, sy, 0, 1, 0, -sy, 0, cy}, rz = {cz, -sz, 0, sz, cz, 0, 0, 0, 1};
        return mul(rz, mul(ry, rx));
    }

    static double[] mul(double[] a, double[] b) {
        double[] r = new double[9];
        for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) r[3 * i + j] = a[3 * i] * b[j] + a[3 * i + 1] * b[3 + j] + a[3 * i + 2] * b[6 + j];
        return r;
    }

    static double[] transpose(double[] a) { return new double[]{a[0], a[3], a[6], a[1], a[4], a[7], a[2], a[5], a[8]}; }

    static double[] apply(double[] m, double[] v) {
        return new double[]{m[0] * v[0] + m[1] * v[1] + m[2] * v[2], m[3] * v[0] + m[4] * v[1] + m[5] * v[2], m[6] * v[0] + m[7] * v[1] + m[8] * v[2]};
    }

    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }

    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
}
