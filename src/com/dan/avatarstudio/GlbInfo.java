package com.dan.avatarstudio;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Steckbrief einer .glb-Datei: Kopf und JSON-Teil nach glTF 2.0, ohne die Puffer zu deuten.
 * Reicht für das HUD und die Prüfung in Phase 1; der Lader selbst ist Phase 2 ({@code com.dan.gltf}).
 */
public final class GlbInfo {

    public final long bytes;
    public final String sha256;
    public final int version;
    public final String generator;
    public final int meshes, nodes, materials, skins, joints, accessors;
    /** Ecken des Körpers (Netz mit den meisten Morphzielen). */
    public final int vertices;
    /** Dreiecke des Körpers (Indizes / 3). */
    public final long triangles;
    /** Morphziele des Körpers. */
    public final int morphTargets;
    public final List<Object> targetNames;
    public final Object json;

    private GlbInfo(byte[] d) throws IOException {
        bytes = d.length;
        sha256 = sha(d);
        ByteBuffer b = ByteBuffer.wrap(d).order(ByteOrder.LITTLE_ENDIAN);
        if (d.length < 20 || b.getInt(0) != 0x46546C67) throw new IOException("keine .glb (Kennung glTF fehlt)");
        version = b.getInt(4);
        int len = b.getInt(8);
        if (len != d.length) throw new IOException("Länge im Kopf " + len + " ≠ Datei " + d.length);
        int jlen = b.getInt(12);
        if (b.getInt(16) != 0x4E4F534A) throw new IOException("erster Abschnitt ist nicht JSON");
        String text = new String(d, 20, jlen, StandardCharsets.UTF_8).trim();
        json = Json.parse(text);
        Map<String, Object> asset = Json.obj(json, "asset");
        generator = asset == null ? "?" : Json.str(asset, "generator");
        meshes = size("meshes");
        nodes = size("nodes");
        materials = size("materials");
        skins = size("skins");
        accessors = size("accessors");
        List<Object> sk = Json.arr(json, "skins");
        joints = sk == null || sk.isEmpty() ? 0 : Json.arr(sk.get(0), "joints").size();

        List<Object> acc = Json.arr(json, "accessors");
        int bestV = 0, bestT = 0;
        long tris = 0;
        List<Object> bestNames = null;
        List<Object> ms = Json.arr(json, "meshes");
        if (ms != null) for (Object m : ms) {
            List<Object> prims = Json.arr(m, "primitives");
            int v = 0, t = 0;
            long tr = 0;
            for (Object p : prims) {
                Map<String, Object> at = Json.obj(p, "attributes");
                Object pos = at.get("POSITION");
                if (pos instanceof Number n) v += (int) Json.num(acc.get(n.intValue()), "count", 0);
                Object ind = ((Map<?, ?>) p).get("indices");
                if (ind instanceof Number n) tr += (long) Json.num(acc.get(n.intValue()), "count", 0) / 3;
                List<Object> tg = Json.arr(p, "targets");
                if (tg != null) t = Math.max(t, tg.size());
            }
            if (t > bestT || (t == bestT && v > bestV)) {
                bestV = v; bestT = t; tris = tr;
                Map<String, Object> ex = Json.obj(m, "extras");
                bestNames = ex == null ? null : Json.arr(ex, "targetNames");
            }
        }
        vertices = bestV;
        morphTargets = bestT;
        triangles = tris;
        targetNames = bestNames == null ? List.of() : bestNames;
    }

    private int size(String k) {
        List<Object> a = Json.arr(json, k);
        return a == null ? 0 : a.size();
    }

    public static GlbInfo read(byte[] d) throws IOException { return new GlbInfo(d); }

    /** Liest eine Ressource neben einer Klasse, oder null, wenn sie fehlt. */
    public static byte[] resource(Class<?> near, String path) throws IOException {
        try (InputStream in = near.getResourceAsStream(path)) {
            if (in == null) return null;
            ByteArrayOutputStream o = new ByteArrayOutputStream(1 << 20);
            in.transferTo(o);
            return o.toByteArray();
        }
    }

    public static String sha(byte[] d) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(d));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
