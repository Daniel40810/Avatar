package com.dan.avatar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Der Kleiderschrank des Grundkörpers (Phase 10): welche Kleidungsnetze es gibt und welches Bit jedes im
 * Merkmal {@code _VERDECKT} des Körpers hat (aus {@code avatar_base.kleidung.json}, geschrieben von der
 * Blender-Kette ab Fassung 1.7). Ohne die Datei gibt es nur das Alltags-Outfit mit Bit 0.
 */
public final class Wardrobe {

    /** Ein Netz: Name im Grundkörper, Bit, Quelldatei, Lizenz. */
    public static final class Item {
        public final String mesh, file, license;
        public final int bit;
        /** Hat das Teil eine MPFB-Gruppe „Delete.…“ (sonst rechnet {@link Wardrobe#cover} die Deckung)? */
        public boolean groups = true;
        Item(String mesh, int bit, String file, String license) { this.mesh = mesh; this.bit = bit; this.file = file; this.license = license; }
        @Override public String toString() { return mesh + " · Bit " + bit + " · " + license; }
    }

    public final List<Item> items = new ArrayList<>();

    /** Aus dem Text der JSON-Datei (einfacher Leser: je Teil "netz", "datei", "bit", "lizenz"). */
    public static Wardrobe parse(String json) {
        Wardrobe w = new Wardrobe();
        Matcher m = Pattern.compile("\\{[^{}]*\"netz\"[^{}]*\\}").matcher(json);
        while (m.find()) {
            String o = m.group();
            Item it = new Item(str(o, "netz"), (int) Double.parseDouble(num(o, "bit")), str(o, "datei"), str(o, "lizenz"));
            Matcher g = Pattern.compile("\"gruppen\"\\s*:\\s*\\[\\s*\\]").matcher(o);
            it.groups = !g.find();
            w.items.add(it);
        }
        return w;
    }

    /** Aus den Ressourcen neben dem Grundkörper; ohne Datei der Schrank von Fassung 1.6 (nur „casualsuit“). */
    public static Wardrobe load() {
        try (InputStream in = Wardrobe.class.getResourceAsStream("res/avatar_base.kleidung.json")) {
            if (in != null) return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException ignored) {}
        Wardrobe w = new Wardrobe();
        w.items.add(new Item("casualsuit", 0, "male_casualsuit01.mhclo", "CC0"));
        return w;
    }

    /** Teil für einen Netznamen (exakt, sonst als Teil des Namens) oder null. */
    public Item item(String meshName) {
        if (meshName == null) return null;
        for (Item i : items) if (i.mesh.equals(meshName)) return i;
        Item best = null;
        for (Item i : items) if (meshName.contains(i.mesh) || i.mesh.contains(meshName)) if (best == null || i.mesh.length() > best.mesh.length()) best = i;
        return best;
    }

    /** Ist dieser Netzname Kleidung? */
    public boolean isCloth(String meshName) { return item(meshName) != null; }

    /** Bits der getragenen Teile eines Outfits. */
    public int mask(Outfit o) {
        int m = 0;
        for (Outfit.Piece p : o.pieces) { Item i = find(p.mesh); if (i != null) m |= 1 << i.bit; }
        return m;
    }

    /** Teil zu einem Schlüssel aus {@link Outfit} (Teil des Netznamens, der längste Treffer). */
    public Item find(String key) {
        Item best = null;
        for (Item i : items) if (i.mesh.equals(key)) return i;
        for (Item i : items) if (i.mesh.contains(key) && (best == null || i.mesh.length() < best.mesh.length())) best = i;
        return best;
    }

    /**
     * Welche Körperecken ein Kleidungsstück verdeckt, wenn es keine MPFB-Gruppe hat: ein Strahl von der
     * Ecke entlang ihrer Normalen trifft das Teil innerhalb von 2,5 cm (Grundform). An Säumen geht der Strahl
     * vorbei, dort bleibt die Haut. Gitter mit 3 cm Zellen, Schnitt nach Möller und Trumbore.
     */
    public static boolean[] cover(Body.Part body, Body.Part cloth) {
        float[] bp = body.prim.position, bn = body.prim.normal, cp = cloth.prim.position;
        int[] ix = cloth.prim.indices;
        double cs = 0.03;
        java.util.HashMap<Long, java.util.List<Integer>> grid = new java.util.HashMap<>();
        for (int t = 0; t + 2 < ix.length; t += 3) {
            double[] lo = {1e9, 1e9, 1e9}, hi = {-1e9, -1e9, -1e9};
            for (int k = 0; k < 3; k++) for (int c = 0; c < 3; c++) { double v = cp[ix[t + k] * 3 + c]; lo[c] = Math.min(lo[c], v); hi[c] = Math.max(hi[c], v); }
            for (int x = (int) Math.floor(lo[0] / cs); x <= (int) Math.floor(hi[0] / cs); x++)
                for (int y = (int) Math.floor(lo[1] / cs); y <= (int) Math.floor(hi[1] / cs); y++)
                    for (int z = (int) Math.floor(lo[2] / cs); z <= (int) Math.floor(hi[2] / cs); z++)
                        grid.computeIfAbsent(key(x, y, z), q -> new java.util.ArrayList<>()).add(t);
        }
        int n = bp.length / 3;
        boolean[] out = new boolean[n];
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        for (int v = 0; v < n; v++) {
            if (bn == null) break;
            double px = bp[v * 3], py = bp[v * 3 + 1], pz = bp[v * 3 + 2], nx = bn[v * 3], ny = bn[v * 3 + 1], nz = bn[v * 3 + 2];
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (nl < 1e-9) continue;
            nx /= nl; ny /= nl; nz /= nl;
            seen.clear();
            boolean hit = false;
            for (double s = -0.005; s <= 0.026 && !hit; s += 0.01) {
                java.util.List<Integer> l = grid.get(key((int) Math.floor((px + nx * s) / cs), (int) Math.floor((py + ny * s) / cs), (int) Math.floor((pz + nz * s) / cs)));
                if (l == null) continue;
                for (int t : l) {
                    if (!seen.add(t)) continue;
                    double d = ray(px - nx * 0.004, py - ny * 0.004, pz - nz * 0.004, nx, ny, nz, cp, ix[t], ix[t + 1], ix[t + 2]);
                    if (d >= 0 && d <= 0.029) { hit = true; break; }
                }
            }
            out[v] = hit;
        }
        return out;
    }

    private static double ray(double ox, double oy, double oz, double dx, double dy, double dz, float[] p, int a, int b, int c) {
        double e1x = p[b * 3] - p[a * 3], e1y = p[b * 3 + 1] - p[a * 3 + 1], e1z = p[b * 3 + 2] - p[a * 3 + 2];
        double e2x = p[c * 3] - p[a * 3], e2y = p[c * 3 + 1] - p[a * 3 + 1], e2z = p[c * 3 + 2] - p[a * 3 + 2];
        double hx = dy * e2z - dz * e2y, hy = dz * e2x - dx * e2z, hz = dx * e2y - dy * e2x;
        double det = e1x * hx + e1y * hy + e1z * hz;
        if (Math.abs(det) < 1e-12) return -1;
        double f = 1 / det;
        double sx = ox - p[a * 3], sy = oy - p[a * 3 + 1], sz = oz - p[a * 3 + 2];
        double u = f * (sx * hx + sy * hy + sz * hz);
        if (u < 0 || u > 1) return -1;
        double qx = sy * e1z - sz * e1y, qy = sz * e1x - sx * e1z, qz = sx * e1y - sy * e1x;
        double w = f * (dx * qx + dy * qy + dz * qz);
        if (w < 0 || u + w > 1) return -1;
        return f * (e2x * qx + e2y * qy + e2z * qz);
    }

    private static long key(int x, int y, int z) { return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF); }

    private static String str(String o, String k) {
        Matcher m = Pattern.compile("\"" + k + "\"\\s*:\\s*\"([^\"]*)\"").matcher(o);
        return m.find() ? m.group(1) : "";
    }

    private static String num(String o, String k) {
        Matcher m = Pattern.compile("\"" + k + "\"\\s*:\\s*([0-9.]+)").matcher(o);
        return m.find() ? m.group(1) : "0";
    }
}
