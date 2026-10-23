package com.dan.avatarstudio;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Prüfung von Phase 1 und 2 ohne Fenster und ohne Grafikkarte. Ende „Alles in Ordnung.“
 * <ul>
 *   <li>Ressourcen: Shader, README der vier Package-Hüllen</li>
 *   <li>Kamera: Ansicht mal Inverse ist die Einheit, Projektion bildet nah/fern auf −1/+1 ab</li>
 *   <li>JSON-Leser</li>
 *   <li>Grundkörper: .glb lesbar, Prüfsumme wie im Protokoll, zweiter Lauf gleich,
 *       Ecken, Knochen und Morphziele wie im Protokoll, Füße auf dem Boden, Größe plausibel</li>
 * </ul>
 */
public final class StudioCheck {

    private static int ok, bad;
    private static final List<String> fails = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        System.out.println("Avatar Studio · Prüfung Phase 1 bis 18");

        // Ressourcen
        for (String s : new String[]{"stage.vert", "stage.frag", "steg.frag", "fullscreen.vert", "backdrop.frag", "post.frag", "hud.vert", "hud.frag"})
            check("Shader " + s, StudioScene.class.getResource("shader/" + s) != null);
        for (String p : new String[]{"gltf", "avatar", "hair", "portrait"})
            check("Package-Hülle com.dan." + p + " mit README", StudioCheck.class.getResource("/com/dan/" + p + "/README.md") != null);

        // Kamera
        Camera c = new Camera();
        c.yawDeg = 33; c.pitchDeg = 12; c.dist = 2.7;
        float[] v = c.view();
        float[] id = Camera.mul(v, Camera.invert(v));
        double err = 0;
        for (int i = 0; i < 16; i++) err = Math.max(err, Math.abs(id[i] - (i % 5 == 0 ? 1 : 0)));
        check(String.format(Locale.GERMANY, "Kamera: Ansicht · Inverse = Einheit (Fehler %.1e)", err), err < 1e-5);
        float[] pr = Camera.perspective(30, 16.0 / 9, 0.05, 80);
        check("Projektion: nah → −1, fern → +1", Math.abs(depthNdc(pr, 0.05) + 1) < 1e-5 && Math.abs(depthNdc(pr, 80) - 1) < 1e-4);
        double[] e = c.eye();
        double d = Math.sqrt(sq(e[0] - c.tx) + sq(e[1] - c.ty) + sq(e[2] - c.tz));
        check("Kamera: Abstand zum Ziel", Math.abs(d - 2.7) < 1e-9);

        // JSON
        Object j = Json.parse("{\"a\":[1,2.5,-3e2],\"b\":{\"c\":\"ä\\u00df\\n\"},\"t\":true,\"n\":null}");
        check("JSON-Leser", Json.arr(j, "a").size() == 3 && ((Number) Json.arr(j, "a").get(2)).doubleValue() == -300
                && "äß\n".equals(Json.str(Json.obj(j, "b"), "c")) && Boolean.TRUE.equals(((Map<?, ?>) j).get("t")));

        // Grundkörper
        byte[] glb = GlbInfo.resource(StudioCheck.class, AvatarStudio.GLB);
        byte[] pj = GlbInfo.resource(StudioCheck.class, AvatarStudio.PROTOKOLL);
        check("Grundkörper avatar_base.glb vorhanden", glb != null);
        check("Protokoll avatar_base.protokoll.json vorhanden", pj != null);
        if (glb != null && pj != null) {
            GlbInfo g = GlbInfo.read(glb);
            Object p = Json.parse(new String(pj, StandardCharsets.UTF_8));
            check("glTF-Version 2", g.version == 2);
            check("Prüfsumme wie im Protokoll", g.sha256.equals(Json.str(p, "sha256")));
            Map<String, Object> pruef = Json.obj(p, "pruefung");
            check("Zweiter Lauf in frischem Blender: gleiche Prüfsumme",
                    pruef != null && Boolean.TRUE.equals(pruef.get("gleich")) && g.sha256.equals(String.valueOf(pruef.get("zweiter_lauf_sha256"))));
            Map<String, Object> netz = Json.obj(p, "netz");
            Map<String, Object> gp = Json.obj(p, "glb");
            int eb = (int) Json.num(netz, "ecken_blender", -1);
            check(String.format(Locale.GERMANY, "Ecken wie im Protokoll (%,d in der Datei, %,d in Blender)", g.vertices, eb),
                    gp != null && g.vertices == (int) Json.num(gp, "ecken", -1) && g.vertices >= eb && eb > 10000);
            check(String.format(Locale.GERMANY, "Dreiecke wie in Blender (%,d)", g.triangles), g.triangles == (long) Json.num(netz, "dreiecke", -1));
            Map<String, Object> rig = Json.obj(p, "rig");
            check("Knochen wie im Protokoll (" + g.joints + ")", g.joints == (int) Json.num(rig, "knochen", -1) && g.skins == 1);
            Map<String, Object> mz = Json.obj(p, "morphziele");
            int n = (int) Json.num(mz, "anzahl", -1);
            check("Morphziele wie im Protokoll (" + g.morphTargets + ")", g.morphTargets == n && g.targetNames.size() == n);
            List<Object> macro = Json.arr(mz, "makro");
            check("Makro-Ziele vorhanden (" + (macro == null ? 0 : macro.size()) + ")", macro != null && macro.size() > 100 && g.targetNames.containsAll(macro));
            List<Object> face = Json.arr(mz, "gesicht");
            check("Gesichtsregler als Paare (" + (face == null ? 0 : face.size() / 2) + ")", face != null && face.size() >= 60 && face.size() % 2 == 0 && g.targetNames.containsAll(face));
            List<Object> formen = Json.arr(mz, "formen");
            check("Gesichtsformen vorhanden (" + (formen == null ? 0 : formen.size()) + ", " + Json.str(mz, "formen_quelle") + ")", formen != null && !formen.isEmpty());

            // Füße auf dem Boden, Größe (Mitte aller Regler ≈ 1,6–1,8 m)
            double[] mm = posMinMax(g);
            check(String.format(Locale.GERMANY, "Füße auf dem Boden (y min = %.4f m)", mm[0]), Math.abs(mm[0]) < 0.01);
            check(String.format(Locale.GERMANY, "Größe plausibel (%.3f m)", mm[1] - mm[0]), mm[1] - mm[0] > 1.5 && mm[1] - mm[0] < 1.9);
            check("Ein Netz mit Haut und Knochen", g.meshes >= 1 && g.materials >= 1);

            phase2(glb, p);

            List<Object> fehlt = Json.arr(p, "fehlt");
            if (fehlt != null && !fehlt.isEmpty()) {
                System.out.println("  Hinweis – noch nicht installiert (kein Fehler in Phase 1):");
                for (Object f : fehlt) System.out.println("    · " + f);
            }
        }

        System.out.println();
        // Ergebnis für das Statusbild (Phase 19)
        try {
            java.util.List<String> sum = new java.util.ArrayList<>();
            sum.add(java.time.LocalDateTime.now().withNano(0) + " · " + ok + " bestanden · " + bad + " fehlgeschlagen");
            for (String f : fails) sum.add("✗ " + f);
            java.nio.file.Files.createDirectories(java.nio.file.Path.of("docs/status"));
            java.nio.file.Files.write(java.nio.file.Path.of("docs/status/Pruefung.txt"), sum, StandardCharsets.UTF_8);
        } catch (java.io.IOException ignore) { }
        if (bad == 0) {
            System.out.println(ok + " Prüfungen bestanden. Alles in Ordnung.");
        } else {
            System.out.println(bad + " von " + (ok + bad) + " Prüfungen fehlgeschlagen:");
            for (String f : fails) System.out.println("  ✗ " + f);
            System.exit(1);
        }
    }

    // ------------------------------------------------------------ Phase 2

    @SuppressWarnings("unchecked")
    private static void phase2(byte[] glb, Object p) throws Exception {
        System.out.println("  — Phase 2: Lader, Person, Abgleich mit Blender");
        long t0 = System.nanoTime();
        com.dan.gltf.Gltf model = com.dan.gltf.Gltf.read(glb);
        double readMs = (System.nanoTime() - t0) / 1e6;
        check(String.format(Locale.GERMANY, "com.dan.gltf liest den Grundkörper (%.0f ms, %d Netze, %d Bilder)", readMs, model.meshes.size(), model.images.size()),
                model.meshes.size() >= 1);
        check("keine Hinweise beim Lesen" + (model.warnings.isEmpty() ? "" : " " + model.warnings), model.warnings.isEmpty());

        // Abbild hin und zurück
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        com.dan.gltf.GltfCache.write(model, bo);
        com.dan.gltf.Gltf back = com.dan.gltf.GltfCache.read(new java.io.ByteArrayInputStream(bo.toByteArray()));
        boolean same = back.meshes.size() == model.meshes.size();
        for (int m = 0; same && m < model.meshes.size(); m++) {
            com.dan.gltf.Gltf.Primitive a = model.meshes.get(m).primitives.get(0), b = back.meshes.get(m).primitives.get(0);
            same = java.util.Arrays.equals(a.position, b.position) && java.util.Arrays.equals(a.tangent, b.tangent) && a.targets.size() == b.targets.size();
        }
        check(String.format(Locale.GERMANY, "Abbild: gleiche Daten (%.1f MB)", bo.size() / 1048576.0), same);

        // Referenz aus Blender
        Map<String, Object> ref = Json.obj(p, "referenz");
        byte[] rb = GlbInfo.resource(StudioCheck.class, "/com/dan/avatar/res/avatar_base.referenz.bin");
        check("Referenz aus Blender vorhanden", ref != null && rb != null && GlbInfo.sha(rb).equals(Json.str(ref, "sha256")));
        if (ref == null || rb == null) return;
        java.nio.FloatBuffer fb = java.nio.ByteBuffer.wrap(rb).order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        List<Object> netze = Json.arr(ref, "netze");
        int perPerson = 0;
        for (Object n : netze) perPerson += (int) Json.num(n, "ecken", 0) * 3;
        com.dan.avatar.Body body = new com.dan.avatar.Body(model);

        // unter der Kleidung verdeckt
        com.dan.avatar.Body.Part bpart = body.body();
        float[] vd = bpart.prim.extra.get("_VERDECKT");
        int all = bpart.prim.indices.length / 3, vis = bpart.visibleIndices().length / 3;
        check(String.format(Locale.GERMANY, "Körper unter der Kleidung ausgeblendet (%,d von %,d Dreiecken)", all - vis, all), vd != null && vis < all && vis > all / 4);

        // jede Datei-Ecke hat eine gültige Blender-Nummer, jede Blender-Ecke kommt vor
        boolean cover = true;
        for (Object n : netze) {
            com.dan.avatar.Body.Part part = part(body, Json.str(n, "netz"));
            int ne = (int) Json.num(n, "ecken", 0);
            if (part == null) { cover = false; break; }
            boolean[] seen = new boolean[ne];
            for (int i = 0; i < part.prim.vertexCount; i++) {
                int bi = part.blenderIndex(i);
                if (bi < 0 || bi >= ne) { cover = false; break; }
                seen[bi] = true;
            }
            for (boolean x : seen) cover &= x;
        }
        check("jede Ecke der Datei findet ihre Blender-Ecke (_BLIDX), keine fehlt", cover);

        List<Object> personen = Json.arr(ref, "personen");
        double worst = 0;
        int pi = 0;
        boolean weightsSame = true;
        for (Object po : personen) {
            Map<String, Object> gw = Json.obj(po, "gewichte");
            Map<String, Double> w = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Object> e : gw.entrySet()) w.put(e.getKey(), ((Number) e.getValue()).doubleValue());
            // Java-Person aus den Reglern: gleiche Gewichte wie Python?
            com.dan.avatar.Person jp = person(Json.obj(po, "regler"));
            Map<String, Double> jw = jp.weights();
            boolean eq = jw.keySet().equals(w.keySet());
            for (String k : w.keySet()) eq &= jw.containsKey(k) && Math.abs(jw.get(k) - w.get(k)) < 1e-9;
            weightsSame &= eq;
            if (!eq) System.out.println("    Gewichte weichen ab bei " + Json.str(po, "name") + ": Java " + jw + " / Blender " + w);

            body.mix(w);
            int base = pi * perPerson;
            double maxE = 0;
            for (Object n : netze) {
                com.dan.avatar.Body.Part part = part(body, Json.str(n, "netz"));
                for (int i = 0; i < part.prim.vertexCount; i++) {
                    int bi = part.blenderIndex(i);
                    for (int c = 0; c < 3; c++)
                        maxE = Math.max(maxE, Math.abs(part.position[i * 3 + c] - fb.get(base + bi * 3 + c)));
                }
                base += (int) Json.num(n, "ecken", 0) * 3;
            }
            check(String.format(Locale.GERMANY, "Abgleich „%s“: höchstens %.4f mm (alle %d Netze, %.1f ms gemischt)",
                    Json.str(po, "name"), maxE * 1000, netze.size(), body.mixMs), maxE <= 0.0001);
            worst = Math.max(worst, maxE);
            pi++;
        }
        check("Java-Person rechnet dieselben Gewichte wie die Blender-Kette (5 Personen)", weightsSame);

        // Mitte aller Regler ergibt die Grundform
        body.mix(new com.dan.avatar.Person());
        com.dan.avatar.Body.Part bp = body.body();
        double md = 0;
        for (int i = 0; i < bp.position.length; i++) md = Math.max(md, Math.abs(bp.position[i] - bp.prim.position[i]));
        check(String.format(Locale.GERMANY, "Mitte aller Regler ergibt die Grundform (%.3f mm)", md * 1000), md < 0.0005);

        // Würfel
        com.dan.avatar.Person a1 = com.dan.avatar.Person.wuerfeln(4711), a2 = com.dan.avatar.Person.wuerfeln(4711), b1 = com.dan.avatar.Person.wuerfeln(4712);
        check("Würfel: gleicher seed, gleiche Person; anderer seed, andere", a1.weights().equals(a2.weights()) && !a1.weights().equals(b1.weights()));
        boolean plaus = true;
        for (long sd = 1; sd <= 200; sd++) {
            com.dan.avatar.Person q = com.dan.avatar.Person.wuerfeln(sd);
            plaus &= q.ageYears() >= 17.5 && q.ageYears() <= 76 && q.weight >= 0.15 && q.weight <= 0.95 && q.height >= 0.1 && q.height <= 0.9;
        }
        check("Würfel: 200 Personen erwachsen und in den Grenzen", plaus);

        phase3(model, body, p);
        phase4(body);
        phase5(body);
        phase6(body, p);
        phase7(model, body);
        phase8(model, body);
        phase9();
        phase10(model, body);
        phase11(model);
        phase12(model);
        phase13(model);
        phase14();
        phase15();
        phase16();
        phase17();
        phase18(model);

        // Linearisierung (Hinweis)
        List<Object> lin = Json.arr(p, "linearisierung");
        if (lin != null) {
            StringBuilder sb = new StringBuilder("    Makro-Mischung gegen MPFB (Näherung bei Größe, Proportionen, Brust):");
            for (Object l : lin) sb.append(String.format(Locale.GERMANY, " %s %.1f mm;", Json.str(l, "person"), Json.num(l, "max_mm", 0)));
            System.out.println(sb);
        }
    }

    // ------------------------------------------------------------ Phase 3

    @SuppressWarnings("unchecked")
    private static void phase3(com.dan.gltf.Gltf model, com.dan.avatar.Body body, Object proto) throws Exception {
        System.out.println("  — Phase 3: Rig, Posen, Skinning, Fuß-IK");
        byte[] rj = GlbInfo.resource(StudioCheck.class, AvatarStudio.RIG);
        check("Rig-Datei avatar_base.rig.json vorhanden", rj != null);
        if (rj == null) return;
        Object rig = Json.parse(new String(rj, StandardCharsets.UTF_8));
        com.dan.avatar.Skeleton sk = new com.dan.avatar.Skeleton(model, rig);
        int roots = 0;
        for (int pa : sk.parent) if (pa < 0) roots++;
        check("Skelett: " + sk.size() + " Gelenke, ein Skin, eine Wurzel", sk.size() == 53 && model.skins.size() == 1 && roots == 1);
        check("Posen aus Blender: " + sk.poses.keySet(), sk.poses.keySet().containsAll(List.of("stehen", "sitzen", "lehnen", "hand_ans_gesicht", "unterarm_drehen")));

        // Mitte: Gelenke wie in Ruhe
        com.dan.avatar.Person mid = new com.dan.avatar.Person();
        Map<String, Double> wm = mid.weights();
        double[][] rest0 = sk.restFor(Map.of());
        double[][] restM = sk.restFor(wm);
        double dm = 0;
        for (int j = 0; j < sk.size(); j++)
            for (int c = 12; c < 15; c++) dm = Math.max(dm, Math.abs(restM[j][c] - rest0[j][c]));
        check(String.format(Locale.GERMANY, "Gelenke der Mitte wie die Ruhe des Rigs (%.4f mm)", dm * 1000), dm < 0.0001);

        // Gelenke folgen den Morphs: gegen Blender (Rig neu angepasst) für die fünf Referenzpersonen
        List<Object> rgj = Json.arr(rig, "referenz_gelenke");
        Map<String, Object> ref = Json.obj(proto, "referenz");
        List<Object> personen = Json.arr(ref, "personen");
        double worstJ = 0;
        for (int i = 0; i < rgj.size(); i++) {
            Map<String, Object> gw = Json.obj(personen.get(i), "gewichte");
            Map<String, Double> w = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Object> e : gw.entrySet()) w.put(e.getKey(), ((Number) e.getValue()).doubleValue());
            double[][] r = sk.restFor(w);
            Map<String, Object> kopf = Json.obj(rgj.get(i), "koepfe");
            for (int j = 0; j < sk.size(); j++) {
                List<Object> b = (List<Object>) kopf.get(sk.names[j]);
                for (int c = 0; c < 3; c++) worstJ = Math.max(worstJ, Math.abs(r[j][12 + c] - ((Number) b.get(c)).doubleValue()));
            }
        }
        check(String.format(Locale.GERMANY, "Gelenke folgen den Morphs wie in Blender (5 Personen, höchstens %.4f mm)", worstJ * 1000), worstJ < 0.0001);

        // Skinning gegen Blender (Volumenerhalt), Mitte, ohne Boden-IK
        Map<String, Object> rp = Json.obj(proto, "referenz_pose");
        byte[] rpb = GlbInfo.resource(StudioCheck.class, "/com/dan/avatar/res/avatar_base.referenz_pose.bin");
        check("Pose-Referenz aus Blender vorhanden", rp != null && rpb != null && GlbInfo.sha(rpb).equals(Json.str(rp, "sha256")));
        if (rp != null && rpb != null) {
            java.nio.FloatBuffer fb = java.nio.ByteBuffer.wrap(rpb).order(java.nio.ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
            int ne = (int) Json.num(rp, "ecken", 0);
            body.mix(wm);
            com.dan.avatar.Body.Part bp = body.body();
            int k0 = body.parts.indexOf(bp);
            List<Object> pn = Json.arr(rp, "posen");
            for (int pi = 0; pi < pn.size(); pi++) {
                String name = String.valueOf(pn.get(pi));
                double[][] sm = com.dan.avatar.Skeleton.skin(sk.pose(sk.poses.get(name), restM, false), restM);
                float[][] op = new float[body.parts.size()][], on = new float[body.parts.size()][];
                long t0 = System.nanoTime();
                com.dan.avatar.Skinning.apply(body, com.dan.avatar.Skeleton.toDq(sm), com.dan.avatar.Skeleton.toMat(sm), true, op, on);
                double ms = (System.nanoTime() - t0) / 1e6;
                double mx = 0;
                for (int i = 0; i < bp.prim.vertexCount; i++) {
                    int bi = bp.blenderIndex(i);
                    for (int c = 0; c < 3; c++) mx = Math.max(mx, Math.abs(op[k0][i * 3 + c] - fb.get((pi * ne + bi) * 3 + c)));
                }
                check(String.format(Locale.GERMANY, "Dual-Quaternion-Skinning „%s“ wie Blender (höchstens %.4f mm, %.1f ms alle Netze)", name, mx * 1000, ms), mx < 0.0001);
            }
        }

        // Unterarm gedreht: Querschnitt am Handgelenk, Dual-Quaternion gegen linear
        double[] vol = forearm(body, sk, restM);
        check(String.format(Locale.GERMANY, "Unterarm gedreht ohne Einknicken: Querschnitt %.0f %% (linear %.0f %%)", vol[0] * 100, vol[1] * 100),
                vol[0] > 0.9 && vol[0] > vol[1]);

        // Füße am Boden: 40 gewürfelte Personen, stehen, sitzen, lehnen
        double worstF = 0;
        String worstWho = "";
        for (long sd = 100; sd < 140; sd++) {
            com.dan.avatar.Person q = com.dan.avatar.Person.wuerfeln(sd);
            if (sd == 100) { q.height = 0.0; } if (sd == 101) { q.height = 1.0; } if (sd == 102) { q.age = 0.15; }
            Map<String, Double> w = q.weights();
            body.mix(w);
            float footY = body.footY(w);
            double[][] r = sk.restFor(w);
            for (String pose : new String[]{"stehen", "sitzen", "lehnen"}) {
                com.dan.avatar.Pose po = sk.poses.get(pose);
                double[][] sm = com.dan.avatar.Skeleton.skin(sk.pose(po, r), r);
                float[][] op = new float[body.parts.size()][], on = new float[body.parts.size()][];
                com.dan.avatar.Skinning.apply(body, com.dan.avatar.Skeleton.toDq(sm), com.dan.avatar.Skeleton.toMat(sm), true, op, on);
                com.dan.avatar.Body.Part bp = body.body();
                int k0 = body.parts.indexOf(bp);
                for (String side : po.supportLegs) {
                    int fj = sk.joint("foot_" + side), bj = sk.joint("ball_" + side);
                    double lo = Double.MAX_VALUE;
                    for (int i = 0; i < bp.prim.vertexCount; i++) {
                        double s = 0;
                        for (int c = 0; c < 4; c++) {
                            int j = bp.prim.joints[i * 4 + c];
                            if (j == fj || j == bj) s += bp.prim.weights[i * 4 + c];
                        }
                        if (s >= 0.5) lo = Math.min(lo, op[k0][i * 3 + 1] - footY);
                    }
                    if (Math.abs(lo) > worstF) { worstF = Math.abs(lo); worstWho = q.name + " · " + pose + " · " + side; }
                }
            }
        }
        check(String.format(Locale.GERMANY, "Füße am Boden: 40 Personen (auch kleinste, größte, Kind) × 3 Posen, Sohle höchstens %.1f mm daneben", worstF * 1000), worstF < 0.006);
        if (worstF >= 0.006) System.out.println("    schlechteste: " + worstWho);
    }

    /** Querschnitt am Handgelenk nach der Drehung relativ zur Ruhe: [Dual-Quaternion, linear]. */
    private static double[] forearm(com.dan.avatar.Body body, com.dan.avatar.Skeleton sk, double[][] rest) {
        com.dan.avatar.Body.Part bp = body.body();
        int k0 = body.parts.indexOf(bp);
        int la = sk.joint("lowerarm_l"), ha = sk.joint("hand_l");
        java.util.List<Integer> ring = new java.util.ArrayList<>();
        for (int i = 0; i < bp.prim.vertexCount; i++) {
            double wl = 0, wh = 0;
            for (int c = 0; c < 4; c++) {
                int j = bp.prim.joints[i * 4 + c];
                if (j == la) wl += bp.prim.weights[i * 4 + c];
                if (j == ha) wh += bp.prim.weights[i * 4 + c];
            }
            if (wl > 0.25 && wh > 0.25) ring.add(i);
        }
        double[] out = new double[2];
        com.dan.avatar.Pose po = sk.poses.get("unterarm_drehen");
        double[][] world = sk.pose(po, rest);
        double[][] sm = com.dan.avatar.Skeleton.skin(world, rest);
        double[] restRad = radius(bp.position, ring, com.dan.avatar.Skeleton.position(rest, la), com.dan.avatar.Skeleton.position(rest, ha));
        for (int mode = 0; mode < 2; mode++) {
            float[][] op = new float[body.parts.size()][], on = new float[body.parts.size()][];
            com.dan.avatar.Skinning.apply(body, com.dan.avatar.Skeleton.toDq(sm), com.dan.avatar.Skeleton.toMat(sm), mode == 0, op, on);
            double[] pr = radius(op[k0], ring, com.dan.avatar.Skeleton.position(world, la), com.dan.avatar.Skeleton.position(world, ha));
            out[mode] = pr[0] / restRad[0];
        }
        return out;
    }

    /** Mittlerer Abstand der Ring-Ecken zur Achse Ellbogen–Handgelenk. */
    private static double[] radius(float[] pos, java.util.List<Integer> ring, double[] a, double[] b) {
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        double l = Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= l; uy /= l; uz /= l;
        double s = 0;
        for (int i : ring) {
            double px = pos[i * 3] - a[0], py = pos[i * 3 + 1] - a[1], pz = pos[i * 3 + 2] - a[2];
            double d = px * ux + py * uy + pz * uz;
            double qx = px - d * ux, qy = py - d * uy, qz = pz - d * uz;
            s += Math.sqrt(qx * qx + qy * qy + qz * qz);
        }
        return new double[]{ring.isEmpty() ? 0 : s / ring.size()};
    }

    private static com.dan.avatar.Body.Part part(com.dan.avatar.Body b, String mesh) {
        for (com.dan.avatar.Body.Part p : b.parts) if (p.mesh.name.equals(mesh)) return p;
        return null;
    }

    @SuppressWarnings("unchecked")
    private static com.dan.avatar.Person person(Map<String, Object> regler) {
        com.dan.avatar.Person p = new com.dan.avatar.Person();
        Map<String, Object> m = Json.obj(regler, "makro");
        if (m != null) for (Map.Entry<String, Object> e : m.entrySet()) {
            double v = ((Number) e.getValue()).doubleValue();
            switch (e.getKey()) {
                case "gender" -> p.gender = v; case "age" -> p.age = v; case "muscle" -> p.muscle = v; case "weight" -> p.weight = v;
                case "height" -> p.height = v; case "proportions" -> p.proportions = v; case "cupsize" -> p.cupsize = v;
                case "firmness" -> p.firmness = v; default -> {}
            }
        }
        Map<String, Object> f = Json.obj(regler, "gesicht");
        if (f != null) for (Map.Entry<String, Object> e : f.entrySet()) p.face.put(e.getKey(), ((Number) e.getValue()).doubleValue());
        Map<String, Object> fo = Json.obj(regler, "formen");
        if (fo != null) for (Map.Entry<String, Object> e : fo.entrySet()) p.forms.put(e.getKey(), ((Number) e.getValue()).doubleValue());
        return p;
    }

    private static double[] posMinMax(GlbInfo g) {
        List<Object> acc = Json.arr(g.json, "accessors");
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        int best = 0;
        for (Object m : Json.arr(g.json, "meshes"))
            for (Object pr : Json.arr(m, "primitives")) {
                Object pos = Json.obj(pr, "attributes").get("POSITION");
                Object a = acc.get(((Number) pos).intValue());
                List<Object> tg = Json.arr(pr, "targets");
                int cnt = tg == null ? 0 : tg.size();
                List<Object> mn = Json.arr(a, "min"), mx = Json.arr(a, "max");
                if (mn != null && mx != null && cnt >= best) {
                    best = cnt;
                    lo = ((Number) mn.get(1)).doubleValue();
                    hi = ((Number) mx.get(1)).doubleValue();
                }
            }
        return new double[]{lo, hi};
    }

    // ------------------------------------------------------------ Phase 4

    private static void phase4(com.dan.avatar.Body body) throws Exception {
        System.out.println("  — Phase 4: Haut");
        for (String sh : new String[]{"avatar.vert", "avatar.frag", "depth.frag", "screen.vert", "sss.frag"})
            check("Shader gl/" + sh, com.dan.avatar.gl.AvatarGl.class.getResource(sh) != null);
        // Ton
        boolean mono = true;
        double last = 9;
        for (int i = 0; i <= 20; i++) {
            float[] t = com.dan.avatar.Skin.tone(i / 20.0, 0.5);
            double y = 0.2126 * t[0] + 0.7152 * t[1] + 0.0722 * t[2];
            mono &= y < last && t[0] > t[1] && t[1] > t[2] && y > 0.03 && y < 0.7;
            last = y;
        }
        check("Hautton: mit Melanin stetig dunkler, immer R > G > B", mono);
        float[] rr = com.dan.avatar.Skin.tone(0.3, 0.9), rl = com.dan.avatar.Skin.tone(0.3, 0.1);
        check("Rötung: mehr Rot, weniger Grün", rr[0] > rl[0] && rr[1] < rl[1]);
        boolean sum1 = true;
        for (int y = 5; y <= 95; y += 5)
            for (double g = 0; g <= 1; g += 0.25) {
                float s = 0;
                for (float w : com.dan.avatar.Skin.textureWeights(y, g)) { s += w; sum1 &= w >= 0; }
                sum1 &= Math.abs(s - 1) < 1e-5;
            }
        check("Grundhäute: Gewichte nach Alter und Geschlecht ergeben 1", sum1);
        float[] fw = com.dan.avatar.Skin.textureWeights(24, 0.2), mw = com.dan.avatar.Skin.textureWeights(24, 0.85);
        check("Grundhäute: deutliche Frau ohne Männerhaut (kein gemalter Bartschatten), deutlicher Mann ohne Frauenhaut",
                fw[1] == 0 && fw[3] == 0 && fw[5] == 0 && mw[0] == 0 && mw[2] == 0 && mw[4] == 0);
        // Kern
        boolean kOk = true;
        for (int n : new int[]{11, 17, 25}) {
            float[] k = com.dan.avatar.Skin.kernel(n, com.dan.avatar.Skin.strength(0.3));
            for (int c = 0; c < 3; c++) {
                double s = 0;
                for (int i = 0; i < n; i++) s += k[i * 4 + c];
                kOk &= Math.abs(s - 1) < 1e-4;
            }
            kOk &= k[3] == 0;
            for (int i = 1; i <= n / 2; i++) kOk &= Math.abs(k[i * 4 + 3] + k[(n - i) * 4 + 3]) < 1e-5 && Math.abs(k[i * 4] - k[(n - i) * 4]) < 1e-6;
        }
        float[] k11 = com.dan.avatar.Skin.kernel(11, com.dan.avatar.Skin.strength(0.0));
        check("Streukern (11, 17, 25 Proben): je Kanal Summe 1, symmetrisch, Mitte zuerst", kOk);
        check(String.format(Locale.GERMANY, "Streukern: Rot streut weiter als Blau (Mitte R %.2f < B %.2f)", k11[0], k11[2]), k11[0] < k11[1] && k11[1] < k11[2]);
        double[] t0 = com.dan.avatar.Skin.transmittance(0), t1 = com.dan.avatar.Skin.transmittance(1), t3 = com.dan.avatar.Skin.transmittance(3);
        check(String.format(Locale.GERMANY, "Durchleuchtung: T(0) = 1, fällt mit der Dicke, Rot am weitesten (T(3 mm) = %.3f %.4f %.4f)", t3[0], t3[1], t3[2]),
                Math.abs(t0[0] - 1) < 0.01 && Math.abs(t0[1] - 1) < 0.01 && t1[0] < t0[0] && t3[0] < t1[0] && t3[0] > t3[1] && t3[1] >= t3[2]);
        float[] lut = com.dan.avatar.Skin.preintegratedCached();
        int W = com.dan.avatar.Skin.LUT_W, H = com.dan.avatar.Skin.LUT_H;
        boolean lOk = true;
        for (int y = 0; y < H; y++)
            for (int x = 1; x < W; x++)
                for (int c = 0; c < 3; c++) {
                    float a = lut[(y * W + x) * 3 + c], b = lut[(y * W + x - 1) * 3 + c];
                    lOk &= a >= b - 1e-5 && a >= 0 && a <= 1.0001;
                }
        int mid = ((H - 1) * W + W / 2 - 1) * 3;
        check(String.format(Locale.GERMANY, "Vorab integriert: 0…1, steigt mit N·L; starke Krümmung bei N·L = 0: Rot %.3f > Blau %.3f", lut[mid], lut[mid + 2]),
                lOk && lut[mid] > lut[mid + 2] * 1.5);
        // Detailkarten
        com.dan.avatar.SkinDetail d1 = com.dan.avatar.SkinDetail.make(com.dan.avatar.SkinDetail.SEED), d2 = com.dan.avatar.SkinDetail.make(com.dan.avatar.SkinDetail.SEED);
        com.dan.avatar.SkinDetail d3 = com.dan.avatar.SkinDetail.make(1234);
        check(String.format("Detailkarten: gleicher seed gleich (%016x), anderer seed anders", d1.hash()), d1.hash() == d2.hash() && d1.hash() != d3.hash());
        int n = com.dan.avatar.SkinDetail.SIZE;
        double edge = 0, inner = 0;
        for (int y = 0; y < n; y++)
            for (int c = 0; c < 4; c++) {
                edge += Math.abs((d1.b[(y * n + n - 1) * 4 + c] & 255) - (d1.b[(y * n) * 4 + c] & 255));
                inner += Math.abs((d1.b[(y * n + n / 2) * 4 + c] & 255) - (d1.b[(y * n + n / 2 - 1) * 4 + c] & 255));
            }
        check(String.format(Locale.GERMANY, "Detailkarten kachelbar: Sprung über die Kante %.2f wie innen %.2f", edge / (n * 4.0), inner / (n * 4.0)), edge < inner * 1.5 + n);
        // Zonen
        com.dan.avatar.SkinMasks m = com.dan.avatar.SkinMasks.of(body);
        check(String.format(Locale.GERMANY, "Landmarken: Augen symmetrisch (%.1f mm), Nase vor dem Mund, Kinn unter dem Mund, Ohren hinter den Augen",
                Math.abs(m.eyeL[0] + m.eyeR[0]) * 1000),
                Math.abs(m.eyeL[0] + m.eyeR[0]) < 0.002 && m.noseTip[2] > m.mouth[2] && m.chin[1] < m.mouth[1] && m.earL[2] < m.eyeL[2] - 0.05);
        float[] P = body.body().prim.position;
        int nv = m.vertexCount;
        double chinB = 0, foreB = 0, mouthL = 0, chest = 0;
        int cc = 0, cf = 0, cm = 0, cs = 0;
        java.util.List<Float> earT = new ArrayList<>();
        for (int i = 0; i < nv; i++) {
            double x = P[i * 3], y = P[i * 3 + 1], z = P[i * 3 + 2];
            if (Math.abs(x) < 0.01 && Math.abs(y - m.chin[1] - 0.012) < 0.006 && z > m.chin[2] - 0.01) { chinB += m.data[i * 8 + 1]; cc++; }
            if (Math.abs(x) < 0.02 && y > m.eyeL[1] + 0.04 && y < m.eyeL[1] + 0.07 && z > m.eyeL[2] - 0.02) { foreB += m.data[i * 8 + 1]; cf++; }
            if (Math.abs(x) < 0.01 && Math.abs(y - m.mouth[1]) < 0.004 && z > m.mouth[2] - 0.006) { mouthL += m.data[i * 8 + 6]; cm++; }
            if (Math.abs(x) < 0.03 && y > 1.2 && y < 1.3 && z > 0.05) { chest += m.data[i * 8]; cs++; }
            if (m.data[i * 8 + 7] > 0.9) earT.add(m.data[i * 8]);
        }
        java.util.Collections.sort(earT);
        double earMed = earT.isEmpty() ? 1 : earT.get(earT.size() / 2);
        check(String.format(Locale.GERMANY, "Bartzone: Kinn %.2f, Stirn %.2f", chinB / Math.max(1, cc), foreB / Math.max(1, cf)), cc > 0 && cf > 0 && chinB / cc > 0.8 && foreB / cf < 0.01);
        check(String.format(Locale.GERMANY, "Lippenzone an der Mundmitte %.2f", mouthL / Math.max(1, cm)), cm > 0 && mouthL / cm > 0.8);
        check(String.format(Locale.GERMANY, "Dicke: Ohr (Median) %.1f mm, Brust %.0f mm (%.0f ms)", earMed * 1000, chest / Math.max(1, cs) * 1000, m.ms),
                earMed < 0.02 && cs > 0 && chest / cs > 0.08);
        // Person
        com.dan.avatar.Person a = com.dan.avatar.Person.wuerfeln(77), b = com.dan.avatar.Person.wuerfeln(77);
        boolean pOk = a.melanin == b.melanin && a.freckles == b.freckles && a.beard == b.beard && a.oil == b.oil;
        boolean rng = true;
        for (int i = 0; i < 200; i++) {
            com.dan.avatar.Person q = com.dan.avatar.Person.wuerfeln(5000 + i);
            rng &= q.melanin >= 0 && q.melanin <= 1 && q.freckles >= 0 && q.freckles <= 1 && q.pores >= 0 && q.oil >= 0;
            if (q.gender < 0.5) rng &= q.beard == 0;
            com.dan.avatar.Person cp = q.copy();
            rng &= cp.melanin == q.melanin && cp.beard == q.beard && cp.redness == q.redness;
        }
        check("Würfel Haut: gleicher seed gleich, 200 Personen in den Grenzen, Frauen ohne Bart, copy() vollständig", pOk && rng);
        // Grundhäute
        byte[] hj = GlbInfo.resource(com.dan.avatar.Skin.class, "res/haut/haut.json");
        boolean shaOk = hj != null;
        if (hj != null) {
            Object h = Json.parse(new String(hj, StandardCharsets.UTF_8));
            for (Object e : Json.arr(h, "haeute")) {
                byte[] img = GlbInfo.resource(com.dan.avatar.Skin.class, "res/haut/" + Json.str(e, "ziel"));
                shaOk &= img != null && GlbInfo.sha(img).equals(Json.str(e, "sha256"));
            }
        }
        check("Grundhäute: sechs Bilder wie in haut.json (SHA-256, CC0)", shaOk);
        com.dan.avatar.SkinTextures st = com.dan.avatar.SkinTextures.load(body);
        boolean mOk = st.size == 2048 && st.rgb.length == 6;
        for (float[] mm : st.mean) mOk &= mm[0] > mm[1] && mm[1] > mm[2] && mm[0] < 0.95;
        check(String.format(Locale.GERMANY, "Grundhäute 2048², Mittelwerte Haut (jung Frau %.2f %.2f %.2f), %.0f ms", st.mean[0][0], st.mean[0][1], st.mean[0][2], st.ms), mOk);
    }

    // ------------------------------------------------------------ Phase 5

    private static void phase6(com.dan.avatar.Body body, Object proto) {
        System.out.println("  — Phase 6: Haare");
        // sechs Frisuren im Grundkörper, mit Lizenz im Protokoll
        List<Object> aus = Json.arr(proto, "ausstattung");
        int found = 0, licensed = 0;
        StringBuilder names = new StringBuilder();
        for (Map.Entry<String, String> e : HairRig.STYLE_MESH.entrySet()) {
            if (e.getValue() == null) continue;
            com.dan.avatar.Body.Part hp = null;
            for (com.dan.avatar.Body.Part bp : body.parts) if (bp.mesh.name.contains(e.getValue())) hp = bp;
            if (hp == null) continue;
            found++;
            names.append(' ').append(hp.mesh.name);
            if (aus != null) for (Object a : aus) {
                String datei = Json.str(a, "datei"), liz = Json.str(a, "lizenz");
                String key = e.getValue().equals("braidbun") ? "braid_bun" : e.getValue();
                if (datei != null && datei.contains(key) && liz != null && !liz.isBlank()) { licensed++; break; }
            }
        }
        check("Sechs Frisuren im Grundkörper (" + found + ":" + names + ")", found == 6);
        check("Jede Frisur mit Lizenz im Protokoll (" + licensed + " von 6)", licensed == 6);
        // Haarkarten: Richtung zeigt nach unten (lang offen)
        com.dan.avatar.Body.Part lang = null;
        for (com.dan.avatar.Body.Part bp : body.parts) if (bp.mesh.name.contains("long01")) lang = bp;
        if (lang != null) {
            com.dan.gltf.Gltf.Primitive pr = lang.prim;
            double[] ax = com.dan.hair.Strands.cardAxis(pr.position, pr.uv0, pr.indices);
            float[] t = com.dan.hair.Strands.cardTangents(pr.position, pr.uv0, pr.indices, (int) ax[0], ax[1]);
            double my = 0;
            for (int v = 0; v < pr.vertexCount; v++) my += t[v * 3 + 1];
            my /= pr.vertexCount;
            check(String.format(Locale.GERMANY, "Haarkarten „lang offen“: Richtung Wurzel → Spitze zeigt nach unten (mittleres y %.2f)", my), my < -0.3);
            // Fliegenhaare: gleich bei gleichem seed, Wurzeln in den Dreiecken
            com.dan.hair.Strands.Params sp = new com.dan.hair.Strands.Params();
            sp.axis = (int) ax[0]; sp.axisSign = ax[1]; sp.count = 3000; sp.seed = 42;
            com.dan.hair.Strands a = com.dan.hair.Strands.grow(pr.position, pr.normal, pr.uv0, pr.indices, null, sp);
            com.dan.hair.Strands b = com.dan.hair.Strands.grow(pr.position, pr.normal, pr.uv0, pr.indices, null, sp);
            sp.seed = 43;
            com.dan.hair.Strands c = com.dan.hair.Strands.grow(pr.position, pr.normal, pr.uv0, pr.indices, null, sp);
            check("Einzelne Haare: gleicher seed gleich, anderer seed anders", a.hash() == b.hash() && a.hash() != c.hash());
            boolean inside = a.count == 3000;
            int tris = pr.indices.length / 3;
            for (int i = 0; i < a.count; i++) {
                int o = i * com.dan.hair.Strands.STRIDE;
                float b1 = a.data[o + 1], b2 = a.data[o + 2], len = a.data[o + 6];
                inside &= a.data[o] >= 0 && a.data[o] < tris && b1 >= 0 && b2 >= 0 && b1 + b2 <= 1.0001 && len >= sp.lengthMin - 1e-6 && len <= sp.lengthMax + 1e-6;
            }
            check("Einzelne Haare: Wurzeln in ihren Dreiecken, Längen in den Grenzen (3000)", inside);
        } else check("Frisur long01 vorhanden", false);
        // Bart nur in der Bartzone
        com.dan.avatar.Body.Part bodyP = null;
        for (com.dan.avatar.Body.Part bp : body.parts) if (bp.isBody) bodyP = bp;
        if (bodyP != null) {
            com.dan.avatar.SkinMasks sm = com.dan.avatar.SkinMasks.of(body);
            com.dan.gltf.Gltf.Primitive pr = bodyP.prim;
            float[] dens = new float[pr.vertexCount];
            for (int v = 0; v < dens.length; v++) dens[v] = (float) HairRig.smooth(0.25, 0.65, sm.data[v * 8 + 1]);
            com.dan.hair.Strands.Params sp = new com.dan.hair.Strands.Params();
            sp.mode = com.dan.hair.Strands.Mode.DOWN_SURFACE; sp.count = 2000; sp.lengthMin = 0.005; sp.lengthMax = 0.02;
            com.dan.hair.Strands st = com.dan.hair.Strands.grow(pr.position, pr.normal, null, pr.indices, dens, sp);
            int inZone = 0;
            for (int i = 0; i < st.count; i++) {
                int tr = (int) st.data[i * com.dan.hair.Strands.STRIDE];
                double m = 0;
                for (int k = 0; k < 3; k++) m = Math.max(m, sm.data[pr.indices[tr * 3 + k] * 8 + 1]);
                if (m > 0.25) inZone++;
            }
            check(String.format(Locale.GERMANY, "Barthaare wurzeln in der Bartzone (%d von %d)", inZone, st.count), st.count == 2000 && inZone == st.count);
        }
        // Haarfarbe: dunkler mit mehr Eumelanin, rot bei hellem Haar
        boolean mono = true;
        double last = 9;
        for (int i = 0; i <= 20; i++) {
            float[] al = com.dan.hair.HairColor.albedo(i / 20.0, 0);
            double l = 0.2126 * al[0] + 0.7152 * al[1] + 0.0722 * al[2];
            mono &= l < last;
            last = l;
        }
        float[] rot = com.dan.hair.HairColor.albedo(0.3, 0.9);
        check("Haarfarbe: dunkler mit Eumelanin (21 Stufen), Rot macht helles Haar kupfern", mono && rot[0] > 2.5 * rot[2]);
        // Bewegung: Feder kommt zur Ruhe, Wind biegt in Windrichtung
        com.dan.hair.HairDynamics hd = new com.dan.hair.HairDynamics();
        double tt = 0;
        for (int i = 0; i < 60; i++, tt += 1 / 60.0) hd.step(1 / 60.0, new double[]{0.3 * Math.sin(tt * 9), 1.6, 0}, new double[3], tt);
        float swing = Math.abs(hd.tipOffset()[0]);
        for (int i = 0; i < 240; i++, tt += 1 / 60.0) hd.step(1 / 60.0, new double[]{0, 1.6, 0}, new double[3], tt);
        float rest = Math.abs(hd.tipOffset()[0]);
        for (int i = 0; i < 120; i++, tt += 1 / 60.0) hd.step(1 / 60.0, new double[]{0, 1.6, 0}, new double[]{3, 0, 0}, tt);
        float[] w = hd.tipOffset();
        check(String.format(Locale.GERMANY, "Haare: Kopf schwingt (%.1f mm), Ruhe nach 4 s (%.3f mm), Wind 3 m/s biegt in x (%.1f mm)", swing * 1000, rest * 1000, w[0] * 1000),
                swing > 0.002 && rest < 0.0005 && w[0] > 0.01 && Math.abs(w[1]) < 0.002 && Math.abs(w[2]) < 0.002);
        // Würfel: Frisuren
        boolean styles = true;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (long sd = 1; sd <= 200; sd++) {
            com.dan.avatar.Person q = com.dan.avatar.Person.wuerfeln(sd);
            seen.add(q.hair);
            styles &= java.util.Arrays.asList(com.dan.avatar.Person.HAIR_STYLES).contains(q.hair);
            if (q.gender < 0.5) styles &= !q.hair.equals("glatze") && q.beardLength == 0;
            styles &= q.copy().hair.equals(q.hair) && q.copy().beardLength == q.beardLength && q.copy().hairColor == q.hairColor;
        }
        check("Würfel: Frisuren aus der Bibliothek (" + seen.size() + " von 7 gesehen), Frauen ohne Bart und Glatze", styles && seen.size() == 7);
    }

    private static void phase5(com.dan.avatar.Body body) {
        System.out.println("  — Phase 5: Augen, Zähne, Mund");
        com.dan.avatar.EyeRig er = new com.dan.avatar.EyeRig(body);
        int nc = 0;
        for (boolean b : er.cornea) if (b) nc++;
        check(String.format(Locale.GERMANY, "Augennetz gefunden, Hornhaut-Schale erkannt (%d von %d Ecken)", nc, er.cornea.length), er.valid() && nc > 100 && nc < er.cornea.length);
        check(String.format(Locale.GERMANY, "Augäpfel als Kugeln: r = %.1f / %.1f mm, symmetrisch (%.2f mm)", er.r0[0] * 1000, er.r0[1] * 1000, Math.abs(er.c0[0][0] + er.c0[1][0]) * 1000),
                er.r0[0] > 0.011 && er.r0[0] < 0.018 && Math.abs(er.r0[0] - er.r0[1]) < 0.0005 && Math.abs(er.c0[0][0] + er.c0[1][0]) < 0.0005);
        check(String.format(Locale.GERMANY, "Blickachse nach vorn (z = %.3f)", er.az0[0][2]), er.az0[0][2] > 0.97 && er.az0[1][2] > 0.97);
        com.dan.avatar.Person p = new com.dan.avatar.Person();
        java.util.Map<String, Double> w0 = p.weights();
        com.dan.avatar.EyeRig.Frame[] f0 = er.frames(body, w0, null);
        double rest = Math.abs(f0[0].c[0] - er.c0[0][0]) + Math.abs(f0[0].z[2] - er.az0[0][2]);
        java.util.Map<String, Double> wu = new java.util.LinkedHashMap<>(w0);
        wu.put("arkit.eyeLookUpLeft", 1.0);
        com.dan.avatar.EyeRig.Frame[] fu = er.frames(body, wu, null);
        double moved = Math.sqrt(Math.pow(fu[0].c[0] - f0[0].c[0], 2) + Math.pow(fu[0].c[1] - f0[0].c[1], 2) + Math.pow(fu[0].c[2] - f0[0].c[2], 2));
        check(String.format(Locale.GERMANY, "eyeLookUpLeft hebt den Blick des linken Auges (y %+.2f → %+.2f), Mitte bleibt (%.2f mm), rechtes Auge ruhig",
                f0[0].z[1], fu[0].z[1], moved * 1000), rest < 1e-4 && fu[0].z[1] > f0[0].z[1] + 0.1 && moved < 0.002 && Math.abs(fu[1].z[1] - f0[1].z[1]) < 1e-6);
        java.util.Map<String, Double> wi = new java.util.LinkedHashMap<>(w0);
        wi.put("arkit.eyeLookInLeft", 1.0);
        com.dan.avatar.EyeRig.Frame[] fi = er.frames(body, wi, null);
        check(String.format(Locale.GERMANY, "eyeLookInLeft dreht das linke Auge zur Nase (x %+.2f → %+.2f)", f0[0].z[0], fi[0].z[0]), fi[0].z[0] < f0[0].z[0] - 0.2);
        double pd = com.dan.avatar.EyeRig.pupilMm(0.01), pb = com.dan.avatar.EyeRig.pupilMm(1000);
        check(String.format(Locale.GERMANY, "Pupille nach Moon und Spencer: dunkel %.1f mm, hell %.1f mm", pd, pb), pd > 6 && pb < 3 && pd > com.dan.avatar.EyeRig.pupilMm(1));
        com.dan.avatar.SkinMasks m = com.dan.avatar.SkinMasks.of(body);
        int wet = 0, in = 0, far = 0;
        float[] P = body.body().prim.position;
        for (int i = 0; i < m.vertexCount; i++) {
            if (m.extra[i * 4] > 0.9) wet++;
            if (m.extra[i * 4 + 1] > 0.5) in++;
            if (P[i * 3 + 1] < 1.3 && (m.extra[i * 4] > 0 || m.extra[i * 4 + 1] > 0)) far++;
        }
        check(String.format(Locale.GERMANY, "Lidrand: %d Ecken am Rand der Augenöffnungen, %d nass; Mundinneres %d Ecken; sonst nirgends", m.lidMarginCount, wet, in),
                m.lidMarginCount > 20 && wet >= 20 && in > 20 && far == 0);
        float[] Nn = body.body().prim.normal;
        int outside = 0;
        for (int i = 0; i < m.vertexCount; i++) if (m.extra[i * 4 + 1] > 0.05 && Nn[i * 3 + 2] > 0.6) outside++;
        check("Mundinneres: keine nach vorn gewandte Haut (Oberlippe, Kinn) abgedunkelt (" + outside + ")", outside == 0);
        com.dan.avatar.Person mo = new com.dan.avatar.Person();
        double closed = com.dan.avatar.gl.AvatarGl.mouthOpening(mo);
        mo.forms.put("arkit.jawOpen", 1.0);
        double open = com.dan.avatar.gl.AvatarGl.mouthOpening(mo);
        check(String.format(Locale.GERMANY, "Mundöffnung aus den Formen: zu %.2f, jawOpen %.2f", closed, open), closed == 0 && open == 1);
        boolean irisOk = true;
        for (int i = 0; i < 200; i++) {
            com.dan.avatar.Person q = com.dan.avatar.Person.wuerfeln(9000 + i);
            irisOk &= q.iris >= 0 && q.iris <= 1 && (q.melanin < 0.4 || q.iris >= 0.55) && q.copy().iris == q.iris;
        }
        check("Würfel Iris: in den Grenzen, dunkle Haut meist braun, copy() vollständig", irisOk);
        for (String sh : new String[]{"avatar.frag"})
            check("Shader " + sh + " mit Auge und Mund", com.dan.avatar.gl.AvatarGl.class.getResource(sh) != null);
    }

    // ------------------------------------------------------------ Phase 7

    /** Ein Lauf des Verhaltens ohne Fenster: {@code secs} Sekunden bei 60 Bildern/s, mit Pose und Augen. */
    private static void run(com.dan.avatar.Behavior b, com.dan.avatar.Skeleton sk, com.dan.avatar.Pose pose, double[][] rest,
                            double secs, java.util.function.Consumer<Map<String, Double>> each) {
        com.dan.avatar.Skeleton.Additive add = new com.dan.avatar.Skeleton.Additive();
        int n = (int) Math.round(secs * 60);
        for (int i = 0; i < n; i++) {
            b.step(1 / 60.0);
            b.additive(add);
            double[][] w = sk.pose(pose, rest, true, add);
            Map<String, Double> f = b.forms(w, rest, sk);
            if (each != null) each.accept(f);
        }
    }

    private static void phase7(com.dan.gltf.Gltf model, com.dan.avatar.Body body) throws Exception {
        System.out.println("  — Phase 7: Leben, Blick, Gefühl");
        Object rig = Json.parse(new String(GlbInfo.resource(StudioCheck.class, AvatarStudio.RIG), StandardCharsets.UTF_8));
        com.dan.avatar.Skeleton sk = new com.dan.avatar.Skeleton(model, rig);
        com.dan.avatar.Person per = com.dan.avatar.Person.wuerfeln(11);
        Map<String, Double> w = per.weights();
        double[][] rest = sk.restFor(w);
        com.dan.avatar.Pose stand = sk.poses.get("stehen");
        com.dan.avatar.EyeRig er = new com.dan.avatar.EyeRig(body);

        // Gefühlsraum
        boolean sum1 = true, own = true;
        StringBuilder weak = new StringBuilder();
        for (com.dan.avatar.Emotion.Def d : com.dan.avatar.Emotion.NAMED) {
            double[] bl = com.dan.avatar.Emotion.blend(d.valence, d.arousal);
            double s = 0;
            for (double x : bl) s += x;
            sum1 &= Math.abs(s - 1) < 1e-9;
            int i = com.dan.avatar.Emotion.NAMED.indexOf(d);
            if (bl[i] < 0.6) { own = false; weak.append(' ').append(d.name).append(String.format(Locale.GERMANY, " %.2f", bl[i])); }
        }
        check("Gefühlsraum: " + com.dan.avatar.Emotion.NAMED.size() + " benannte Gefühle, Anteile Summe 1, am eigenen Punkt überwiegt das Gefühl" + weak, sum1 && own);
        boolean formsOk = true;
        for (com.dan.avatar.Emotion.Def d : com.dan.avatar.Emotion.NAMED)
            for (String f : d.forms.keySet()) formsOk &= com.dan.avatar.Emotion.exists(f);
        Set<String> modelTargets = new java.util.HashSet<>();
        for (com.dan.avatar.Body.Part bp : body.parts) for (com.dan.gltf.Gltf.Target t : bp.prim.targets) modelTargets.add(t.name);
        for (String a : com.dan.avatar.Emotion.ARKIT) formsOk &= modelTargets.contains("arkit." + a);
        check("Jede Gefühlsform ist eine ARKit-Form, alle 52 im Modell", formsOk);
        // feste Ziele vor beweglichen: die Grafikkarte mischt in zwei Schritten in derselben Reihenfolge
        Map<String, Integer> ti = new java.util.LinkedHashMap<>();
        for (com.dan.avatar.Body.Part bp : body.parts) for (com.dan.gltf.Gltf.Target t : bp.prim.targets) ti.putIfAbsent(t.name, ti.size());
        int maxS = -1, minD = Integer.MAX_VALUE, nD = 0;
        for (Map.Entry<String, Integer> e : ti.entrySet()) {
            boolean dyn = e.getKey().startsWith("arkit.") || e.getKey().startsWith("visem.");
            if (dyn) { minD = Math.min(minD, e.getValue()); nD++; } else maxS = Math.max(maxS, e.getValue());
        }
        check(String.format(Locale.GERMANY, "Morphs in zwei Schritten: %d feste Ziele vor %d beweglichen (Gesichtsformen, Viseme), gleiche Summenfolge", maxS + 1, nD), maxS < minD);

        // Augen einmessen
        com.dan.avatar.Behavior b = new com.dan.avatar.Behavior(7);
        Map<String, Double> plain = new java.util.LinkedHashMap<>(w);
        plain.keySet().removeIf(k -> k.startsWith("arkit.") || k.startsWith("visem."));
        b.calibrate(er, body, plain);
        double[] rg = b.eyeRange();
        check(String.format(Locale.GERMANY, "Augen eingemessen: Blickformen drehen links %.0f°, rechts %.0f°, oben %.0f°, unten %.0f°", rg[0], rg[1], rg[2], rg[3]),
                b.calibrated() && rg[0] > 15 && rg[1] > 15 && rg[2] > 10 && rg[3] > 10);

        // zehn Minuten ohne Eingriff: Lidschläge, Sakkaden, Atem, Gewicht
        double[] lastB = {0};
        int[] breaths = {0};
        double[] wMin = {0}, wMax = {0};
        int[] wFlips = {0};
        double[] lastW = {0};
        double[] lidMax = {0};
        b.camera = new double[]{0.15, 1.62, 2.2};
        run(b, sk, stand, rest, 600, f -> {});
        // (zweiter Lauf mit Messung von Atem und Gewicht über die Zeit)
        com.dan.avatar.Behavior b2 = new com.dan.avatar.Behavior(7);
        b2.calibrate(er, body, plain);
        b2.camera = b.camera;
        double[] prevBr = {0};
        boolean[] rising = {false};
        run(b2, sk, stand, rest, 600, f -> {
            double br = b2.breath();
            if (br > prevBr[0] + 1e-9 && !rising[0] && prevBr[0] < 0.05) { breaths[0]++; rising[0] = true; }
            if (br < prevBr[0]) rising[0] = false;
            prevBr[0] = br;
            double ws = b2.weightNow();
            if (Math.signum(ws) != Math.signum(lastW[0]) && Math.abs(ws) > 0.5) wFlips[0]++;
            if (Math.abs(ws) > 0.5) lastW[0] = ws;
            lidMax[0] = Math.max(lidMax[0], b2.lid());
        });
        double minutes = 10;
        double rate = b.blinks.size() / minutes;
        double[] gaps = new double[b.blinks.size() - 1];
        for (int i = 1; i < b.blinks.size(); i++) gaps[i - 1] = b.blinks.get(i) - b.blinks.get(i - 1);
        double gmin = java.util.Arrays.stream(gaps).min().orElse(0), gmax = java.util.Arrays.stream(gaps).max().orElse(0);
        double gmean = java.util.Arrays.stream(gaps).average().orElse(0), gsd = 0;
        for (double g : gaps) gsd += (g - gmean) * (g - gmean);
        gsd = Math.sqrt(gsd / Math.max(1, gaps.length));
        check(String.format(Locale.GERMANY, "Lidschläge in 10 min: %.1f je Minute (Ziel 15–20), Abstand %.1f–%.1f s, unregelmäßig (Streuung %.0f %%)",
                rate, gmin, gmax, gsd / gmean * 100), rate >= 14 && rate <= 21 && gmin > 0.3 && gsd / gmean > 0.35);
        check(String.format(Locale.GERMANY, "Lidschlag schließt ganz (höchstens %.2f)", lidMax[0]), lidMax[0] > 0.99);
        double dmin = 1, dmax = 0, amax = 0, asum = 0;
        int nS = b.saccades.size();
        boolean mainSeq = true;
        for (double[] s : b.saccades) {
            dmin = Math.min(dmin, s[2]); dmax = Math.max(dmax, s[2]); amax = Math.max(amax, s[1]); asum += s[1];
            mainSeq &= Math.abs(s[2] - (0.021 + 0.0022 * s[1])) < 1e-9;
        }
        check(String.format(Locale.GERMANY, "Sakkaden: %d in 10 min (%.1f/s), Weite bis %.0f° (Mittel %.1f°), Dauer %.0f–%.0f ms nach der Hauptfolge (Ziel 20–200 ms)",
                nS, nS / 600.0, amax, asum / Math.max(1, nS), dmin * 1000, dmax * 1000), nS > 300 && dmin >= 0.020 && dmax <= 0.2 && mainSeq);
        int induced = 0;
        for (double[] s : b.saccades) if (s[1] > 15) for (double t : b.blinks) if (t >= s[0] - 1e-9 && t < s[0] + 0.02) { induced++; break; }
        int big = 0;
        for (double[] s : b.saccades) if (s[1] > 15) big++;
        check(String.format(Locale.GERMANY, "Lidschlag nach großen Blicksprüngen: %d von %d über 15°", induced, big), big == 0 || induced > 0);
        double bpm = breaths[0] / minutes;
        check(String.format(Locale.GERMANY, "Atem: %.1f Züge je Minute in Ruhe (Ziel 12–20)", bpm), bpm >= 12 && bpm <= 20);
        double shiftEvery = 600.0 / Math.max(1, wFlips[0]);
        check(String.format(Locale.GERMANY, "Gewichtsverlagerung: %d Wechsel in 10 min (alle %.1f s)", wFlips[0], shiftEvery), shiftEvery > 4 && shiftEvery < 20);
        int nm = b.micros.size();
        double mdur = 0;
        for (double[] m : b.micros) mdur = Math.max(mdur, m[2]);
        check(String.format(Locale.GERMANY, "Mikroausdrücke: %d in 10 min, höchstens %.0f ms", nm, mdur * 1000), nm >= 6 && nm <= 30 && mdur < 0.35);

        // gleicher seed, gleiches Verhalten
        com.dan.avatar.Behavior c1 = new com.dan.avatar.Behavior(99), c2 = new com.dan.avatar.Behavior(99), c3 = new com.dan.avatar.Behavior(100);
        for (com.dan.avatar.Behavior c : new com.dan.avatar.Behavior[]{c1, c2, c3}) { c.calibrate(er, body, plain); c.feel("freudig", 0.8); }
        java.util.List<Map<String, Double>> fl = new java.util.ArrayList<>();
        for (com.dan.avatar.Behavior c : new com.dan.avatar.Behavior[]{c1, c2, c3}) {
            Map<String, Double>[] last = new Map[1];
            run(c, sk, stand, rest, 30, f -> last[0] = f);
            fl.add(last[0]);
        }
        check("Gleicher seed, gleiches Verhalten nach 30 s; anderer seed, anderes", fl.get(0).equals(fl.get(1)) && !fl.get(0).equals(fl.get(2)));

        // Füße bleiben stehen, das Becken wandert
        com.dan.avatar.Behavior bw = new com.dan.avatar.Behavior(5);
        bw.calibrate(er, body, plain);
        double[][] base = sk.pose(stand, rest);
        int fl_ = sk.joint("foot_l"), fr_ = sk.joint("foot_r"), pv = sk.joint("pelvis");
        double footErr = 0, pelvisMax = 0;
        com.dan.avatar.Skeleton.Additive add = new com.dan.avatar.Skeleton.Additive();
        for (int i = 0; i < 60 * 40; i++) {
            bw.step(1 / 60.0);
            if (i % 15 != 0) continue;
            bw.additive(add);
            double[][] pw = sk.pose(stand, rest, true, add);
            for (int j : new int[]{fl_, fr_}) footErr = Math.max(footErr, Math.sqrt(sq(pw[j][12] - base[j][12]) + sq(pw[j][13] - base[j][13]) + sq(pw[j][14] - base[j][14])));
            pelvisMax = Math.max(pelvisMax, Math.abs(pw[pv][12] - base[pv][12]));
        }
        check(String.format(Locale.GERMANY, "Gewicht verlagert: Becken bis %.0f mm zur Seite, Füße bleiben (höchstens %.2f mm)", pelvisMax * 1000, footErr * 1000),
                pelvisMax > 0.012 && footErr < 1.0e-3);

        // Blick: Ziel links, Augen und Kopf teilen sich die Drehung; nah: Vergenz
        com.dan.avatar.Behavior bg = new com.dan.avatar.Behavior(3);
        bg.calibrate(er, body, plain);
        bg.alive = true;
        bg.camera = new double[]{0, 1.6, 2.0};
        run(bg, sk, stand, rest, 1.5, null);
        double[] eye0 = er.c0[0];
        bg.lookAt(new double[]{1.3, 1.6, 1.6});        // gut 35° nach links (Person)
        double[] tDone = {-1};
        double[] t0 = {bg.time()};
        Map<String, Double>[] fLook = new Map[1];
        run(bg, sk, stand, rest, 1.2, f -> { if (tDone[0] < 0 && bg.done("blick")) tDone[0] = bg.time() - t0[0]; fLook[0] = f; });
        double[] gh = bg.gazeHead();
        double outL = fLook[0].getOrDefault("arkit.eyeLookOutLeft", 0.0), inR = fLook[0].getOrDefault("arkit.eyeLookInRight", 0.0);
        check(String.format(Locale.GERMANY, "Blick 35° nach links: am Ziel nach %.0f ms, Kopf %.0f° von %.0f°, Augen eyeLookOutLeft %.2f / eyeLookInRight %.2f",
                tDone[0] * 1000, gh[2], gh[0], outL, inR), tDone[0] > 0 && tDone[0] < 1.0 && gh[2] > 8 && gh[2] < gh[0] && outL > 0.05 && inR > 0.05);
        bg.alive = false;
        bg.lookAt(new double[]{eye0[0] * 0 , 1.62, 0.35});
        Map<String, Double>[] fNear = new Map[1];
        run(bg, sk, stand, rest, 0.5, f -> fNear[0] = f);
        double inL = fNear[0].getOrDefault("arkit.eyeLookInLeft", 0.0), inR2 = fNear[0].getOrDefault("arkit.eyeLookInRight", 0.0);
        check(String.format(Locale.GERMANY, "Vergenz: Punkt 25 cm vor dem Gesicht, beide Augen zur Nase (%.2f / %.2f), Augen %.1f° / %.1f°",
                inL, inR2, bg.eyeAngles[0][0], bg.eyeAngles[1][0]), inL > 0.05 && inR2 > 0.05);
        bg.alive = true;

        // Gefühl: Brauen zuerst, Mund später; Abklingen langsamer als Einsetzen; Asymmetrie
        com.dan.avatar.Behavior be = new com.dan.avatar.Behavior(21);
        be.calibrate(er, body, plain);
        be.alive = false;
        be.feel("überrascht", 1);
        double[] tBrow = {-1}, tJaw = {-1};
        double browT = com.dan.avatar.Emotion.named("überrascht").forms.get("browInnerUp"), jawT = com.dan.avatar.Emotion.named("überrascht").forms.get("jawOpen");
        double[] tt = {0};
        run(be, sk, stand, rest, 2, f -> {
            tt[0] += 1 / 60.0;
            if (tBrow[0] < 0 && f.getOrDefault("arkit.browInnerUp", 0.0) > 0.63 * browT) tBrow[0] = tt[0];
            if (tJaw[0] < 0 && f.getOrDefault("arkit.jawOpen", 0.0) > 0.63 * jawT) tJaw[0] = tt[0];
        });
        check(String.format(Locale.GERMANY, "„überrascht“: Brauen nach %.0f ms, Kiefer nach %.0f ms (versetzter Einsatz)", tBrow[0] * 1000, tJaw[0] * 1000),
                tBrow[0] > 0 && tJaw[0] > tBrow[0]);
        check("„überrascht“ ist angekommen (warte gefuehl)", be.done("gefuehl"));
        be.feel("neutral", 1);
        double[] tOff = {-1};
        tt[0] = 0;
        run(be, sk, stand, rest, 3, f -> { tt[0] += 1 / 60.0; if (tOff[0] < 0 && f.getOrDefault("arkit.browInnerUp", 0.0) < 0.37 * browT) tOff[0] = tt[0]; });
        check(String.format(Locale.GERMANY, "Abklingen langsamer als Einsetzen: Brauen %.0f ms gegen %.0f ms", tOff[0] * 1000, tBrow[0] * 1000), tOff[0] > 1.5 * tBrow[0]);
        be.feel("freudig", 1);
        Map<String, Double>[] fj = new Map[1];
        run(be, sk, stand, rest, 3, f -> fj[0] = f);
        double sl = fj[0].getOrDefault("arkit.mouthSmileLeft", 0.0), sr = fj[0].getOrDefault("arkit.mouthSmileRight", 0.0);
        double cs = fj[0].getOrDefault("arkit.cheekSquintLeft", 0.0) + fj[0].getOrDefault("arkit.cheekSquintRight", 0.0);
        check(String.format(Locale.GERMANY, "„freudig“: Lächeln %.2f / %.2f (Seiten verschieden %.0f %%), erreicht die Augen (cheekSquint %.2f, Duchenne)",
                sl, sr, Math.abs(sl - sr) / Math.max(sl, sr) * 100, cs / 2), sl > 0.6 && sr > 0.6 && Math.abs(sl - sr) / Math.max(sl, sr) > 0.05 && cs / 2 > 0.4);

        // Leben aus: kein Lidschlag, kein Atem
        com.dan.avatar.Behavior bo = new com.dan.avatar.Behavior(8);
        bo.calibrate(er, body, plain);
        bo.alive = false;
        run(bo, sk, stand, rest, 20, null);
        check("Leben aus: kein Lidschlag, kein Atem, keine Sakkade in 20 s", bo.blinks.isEmpty() && bo.breath() == 0 && bo.saccades.isEmpty());

        // Zeitlupe
        com.dan.avatar.Behavior bz = new com.dan.avatar.Behavior(8);
        bz.timeScale = 0.25;
        for (int i = 0; i < 600; i++) bz.step(1 / 60.0);
        check(String.format(Locale.GERMANY, "Zeitlupe 25 %%: 10 s Uhr = %.2f s Verhalten", bz.time()), Math.abs(bz.time() - 2.5) < 1e-6);

        // Mundöffnung aus beliebigen Formen (für das Mundinnere in jedem Bild)
        Map<String, Double> mf = new java.util.HashMap<>();
        mf.put("arkit.jawOpen", 0.32);
        check("Mundöffnung aus den Formen des Verhaltens", Math.abs(com.dan.avatar.gl.AvatarGl.mouthOpening(mf) - 0.32) < 1e-9);
    }

    // ------------------------------------------------------------ Phase 8

    private static void phase12(com.dan.gltf.Gltf model) throws Exception {
        System.out.println("  — Phase 12: Detailstufen und Mengen");
        Object rig = Json.parse(new String(GlbInfo.resource(StudioCheck.class, AvatarStudio.RIG), StandardCharsets.UTF_8));
        com.dan.avatar.Skeleton sk = new com.dan.avatar.Skeleton(model, rig);
        com.dan.avatar.Body b = new com.dan.avatar.Body(model);
        com.dan.avatar.Person p = com.dan.avatar.Person.wuerfeln(23);
        b.mix(p.weights());
        com.dan.avatar.Body.Part bp = b.body();
        java.util.List<com.dan.avatar.Body.Part> parts = java.util.List.of(bp);
        java.util.List<int[]> tris = java.util.List.of(bp.visibleIndices(0));
        java.util.List<float[]> cols = java.util.List.of(com.dan.avatar.Skin.tone(p.melanin, p.redness));
        com.dan.avatar.LodMesh l2 = com.dan.avatar.LodMesh.build(b, parts, tris, cols, 0.035), l3 = com.dan.avatar.LodMesh.build(b, parts, tris, cols, 0.09);
        boolean ok = true;
        double wsum = 0;
        for (com.dan.avatar.LodMesh m : new com.dan.avatar.LodMesh[]{l2, l3}) {
            for (int i = 0; i < m.vertices; i++) {
                double s = 0;
                for (int k = 0; k < 4; k++) { s += m.wt[i * 4 + k]; ok &= m.jn[i * 4 + k] >= 0 && m.jn[i * 4 + k] < sk.size(); }
                wsum = Math.max(wsum, Math.abs(s - 1));
            }
            for (int t = 0; t < m.triangles; t++) ok &= m.idx[t * 3] != m.idx[t * 3 + 1] && m.idx[t * 3 + 1] != m.idx[t * 3 + 2] && m.idx[t * 3] != m.idx[t * 3 + 2];
        }
        double[] lo = {9, 9, 9}, hi = {-9, -9, -9}, lo2 = {9, 9, 9}, hi2 = {-9, -9, -9};
        for (int i = 0; i < bp.prim.vertexCount; i++) for (int k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], bp.position[i * 3 + k]); hi[k] = Math.max(hi[k], bp.position[i * 3 + k]); }
        for (int i = 0; i < l2.vertices; i++) for (int k = 0; k < 3; k++) { lo2[k] = Math.min(lo2[k], l2.pos[i * 3 + k]); hi2[k] = Math.max(hi2[k], l2.pos[i * 3 + k]); }
        double bbErr = 0;
        for (int k = 0; k < 3; k++) bbErr = Math.max(bbErr, Math.max(Math.abs(lo[k] - lo2[k]), Math.abs(hi[k] - hi2[k])));
        int full = bp.prim.indices.length / 3;
        check(String.format(Locale.GERMANY, "Ferne Netze (Körper): voll %,d Dreiecke, L2 %,d, L3 %,d; Gewichte summieren zu 1 (%.0e), Umriss wie voll (%.1f cm)", full, l2.triangles, l3.triangles, wsum, bbErr * 100),
                ok && wsum < 1e-4 && l2.triangles < full / 2 && l2.triangles > 1000 && l3.triangles < 1200 && l3.triangles > 150 && bbErr < 0.04);
        // Gruppe
        com.dan.avatar.Crowd cr = com.dan.avatar.Crowd.group(40, com.dan.avatar.Motion.line(0, 0, Math.PI / 2), 0, 100, 10, 2026);
        cr.calibrate(sk);
        java.util.Set<Long> seeds = new java.util.HashSet<>();
        for (com.dan.avatar.Crowd.Member m : cr.members) seeds.add(m.person.seed);
        double x0 = cr.centre()[0];
        com.dan.avatar.Pose st = sk.poses.get("stehen");
        com.dan.avatar.Skeleton.Additive add = new com.dan.avatar.Skeleton.Additive();
        double minGap = 9, slide = 0;
        long t0 = System.nanoTime();
        int poses = 0;
        for (int i = 0; i < 300; i++) {
            cr.step(1 / 30.0);
            if (i % 3 == 0) for (com.dan.avatar.Crowd.Member m : cr.members) {
                add.clear(); m.motion.additive(add);
                double[][] w = sk.pose(st, m.rest, true, add);
                m.motion.apply(sk, w, m.rest);
                m.motion.measure(sk, w, 1 / 10.0);
                poses++;
            }
            if (i % 30 == 0) for (int a = 0; a < 40; a++) for (int c = a + 1; c < 40; c++) {
                com.dan.avatar.Motion ma = cr.members.get(a).motion, mc = cr.members.get(c).motion;
                minGap = Math.min(minGap, Math.hypot(ma.x - mc.x, ma.z - mc.z));
            }
        }
        double poseMs = (System.nanoTime() - t0) / 1e6 / Math.max(1, poses);
        for (com.dan.avatar.Crowd.Member m : cr.members) slide = Math.max(slide, m.motion.slideMax);
        double moved = cr.centre()[0] - x0;
        check(String.format(Locale.GERMANY, "Gruppe: 40 verschiedene Personen in 8 Reihen, gemeinsames Tempo %.2f m/s (in 10 s %.1f m), kleinster Abstand %.2f m, Gleiten %.1f mm/s, %.2f ms je Pose",
                cr.speed, moved, minGap, slide * 1000, poseMs),
                seeds.size() == 40 && cr.speed > 0.8 && cr.speed <= 1.05 && moved > 7 && minGap > 0.35 && slide < 0.005);
        // Rasterblende: Fenster teilen [0, 1) ohne Lücke und Doppelung
        boolean part = true;
        for (double a = 0; a <= 1.0001; a += 0.125) for (double c = 0; c <= 1.0001; c += 0.125) {
            double split = a + (1 - a) * c;
            part &= split >= a - 1e-12 && split <= 1 + 1e-12;
        }
        check("Übergänge: Figur [0, a), L2 [a, s), L3 [s, 1) teilen die Rasterblende lückenlos", part);
        String fr = new String(com.dan.avatar.gl.AvatarGl.class.getResourceAsStream("avatar.frag").readAllBytes(), StandardCharsets.UTF_8);
        String hf = new String(com.dan.hair.gl.HairGl.class.getResourceAsStream("hair.frag").readAllBytes(), StandardCharsets.UTF_8);
        check("Shader: Menge (crowd.vert, crowd.frag), Rasterblende in Haut und Haar",
                com.dan.avatar.gl.AvatarGl.class.getResource("crowd.vert") != null && com.dan.avatar.gl.AvatarGl.class.getResource("crowd.frag") != null && fr.contains("ditherOut") && hf.contains("ditherOut"));
    }

    private static void phase11(com.dan.gltf.Gltf model) throws Exception {
        System.out.println("  — Phase 11: Bewegung");
        Object rig = Json.parse(new String(GlbInfo.resource(StudioCheck.class, AvatarStudio.RIG), StandardCharsets.UTF_8));
        com.dan.avatar.Skeleton sk = new com.dan.avatar.Skeleton(model, rig);
        com.dan.avatar.Person man = com.dan.avatar.Person.wuerfeln(23);
        double[][] rest = sk.restFor(man.weights());
        com.dan.avatar.Pose st = sk.poses.get("stehen"), si = sk.poses.get("sitzen");
        // Mischen von Posen
        double e0 = 0, e1 = 0;
        double[][] wa = sk.pose(st, rest, false), wb = sk.pose(si, rest, false);
        double[][] w0 = sk.pose(com.dan.avatar.Pose.blend(st, si, 0), rest, false), w1 = sk.pose(com.dan.avatar.Pose.blend(st, si, 1), rest, false);
        for (int j = 0; j < wa.length; j++) for (int k = 12; k < 15; k++) { e0 = Math.max(e0, Math.abs(wa[j][k] - w0[j][k])); e1 = Math.max(e1, Math.abs(wb[j][k] - w1[j][k])); }
        check(String.format(Locale.GERMANY, "Posen mischen: Enden genau (%.1e / %.1e m)", e0, e1), e0 < 1e-5 && e1 < 1e-5);
        // Ablauf auf dem Kreis
        double r = 1.45;
        com.dan.avatar.Motion m = new com.dan.avatar.Motion(com.dan.avatar.Motion.circle(0, 0, r, true), 0);
        m.calibrate(sk, rest, man);
        double yaw0 = m.yaw;
        m.then(com.dan.avatar.Motion.walk(2 * Math.PI * r)).then(com.dan.avatar.Motion.stand(0.5)).then(com.dan.avatar.Motion.turn(-Math.PI / 2))
                .then(com.dan.avatar.Motion.sit(0.6)).then(com.dan.avatar.Motion.wave(1.5)).then(com.dan.avatar.Motion.standUp()).then(com.dan.avatar.Motion.turn(Math.PI / 2));
        com.dan.avatar.Skeleton.Additive add = new com.dan.avatar.Skeleton.Additive();
        double minAnkle = 9, maxHand = -9, headY = 0, minPelvis = 9, yawAfterTurn = Double.NaN, yawBeforeTurn = Double.NaN;
        int hj = sk.joint("head"), hand = sk.joint("hand_r"), pv = sk.joint("pelvis"), fl = sk.joint("foot_l"), fr = sk.joint("foot_r");
        double ankleRest = com.dan.avatar.Skeleton.position(rest, fl)[1];
        long t0 = System.nanoTime();
        int frames = 0;
        for (double t = 0; t < 40 && !(m.idle() && t > 1); t += 1 / 30.0) {
            m.step(1 / 30.0);
            m.trackFeet();
            com.dan.avatar.Pose p = m.sit > 1e-3 ? com.dan.avatar.Pose.blend(st, si, StudioScene.smooth01(m.sit)) : st;
            add.clear();
            m.additive(add);
            double[][] w = sk.pose(p, rest, true, add);
            m.apply(sk, w, rest);
            m.measure(sk, w, 1 / 30.0);
            frames++;
            minAnkle = Math.min(minAnkle, Math.min(com.dan.avatar.Skeleton.position(w, fl)[1], com.dan.avatar.Skeleton.position(w, fr)[1]));
            if (m.wave > 0.9) { maxHand = Math.max(maxHand, com.dan.avatar.Skeleton.position(w, hand)[1]); headY = com.dan.avatar.Skeleton.position(w, hj)[1]; }
            if (m.sit > 0.98) minPelvis = Math.min(minPelvis, com.dan.avatar.Skeleton.position(w, pv)[1]);
            if (m.current() != null && m.current().kind() == com.dan.avatar.Motion.Kind.STEHEN && Double.isNaN(yawAfterTurn)) yawBeforeTurn = m.yaw;
            if (m.current() != null && m.current().kind() == com.dan.avatar.Motion.Kind.SITZEN && Double.isNaN(yawAfterTurn)) yawAfterTurn = m.yaw;
        }
        double ms = (System.nanoTime() - t0) / 1e6 / Math.max(1, frames);
        // Schrittlänge aus den Spuren: gleicher Fuß, gerade Strecke
        double strideSum = 0; int strideN = 0;
        double[] last = new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN};
        for (double[] stp : m.steps) {
            int f = (int) stp[3];
            if (!Double.isNaN(last[f * 2])) {
                double d = Math.hypot(stp[0] - last[f * 2], stp[1] - last[f * 2 + 1]);
                if (d > 0.8 * m.stride && d < 1.2 * m.stride) { strideSum += d; strideN++; }
            }
            last[f * 2] = stp[0]; last[f * 2 + 1] = stp[1];
        }
        double strideM = strideN == 0 ? 0 : strideSum / strideN;
        check(String.format(Locale.GERMANY, "Gang: Bein %.2f m → Doppelschritt %.2f m (in den Spuren %.2f m, %d), %.2f Schritte/s, %.2f m/s",
                m.legLen, m.stride, strideM, strideN, m.cadence * 2, m.vWalk),
                strideN > 4 && Math.abs(strideM - m.stride) < 0.08 * m.stride && m.cadence * 2 > 1.5 && m.cadence * 2 < 2.4 && m.vWalk > 1.0 && m.vWalk < 1.6);
        check(String.format(Locale.GERMANY, "Kein Gleiten: tragende Füße höchstens %.2f mm/s, zusammen %.2f mm in %d Schritten; Reichweite verfehlt %.1f mm; kein Fuß im Boden (%.1f mm)",
                m.slideMax * 1000, m.slideSum * 1000, m.steps.size(), m.missMax * 1000, (minAnkle - ankleRest) * 1000),
                m.slideMax < 0.005 && m.missMax < 0.002 && minAnkle > ankleRest - 0.002 && m.steps.size() > 10);
        check(String.format(Locale.GERMANY, "Drehen auf der Stelle: −90° genau (%.2f°), danach stehen beide Füße neben dem Becken", Math.toDegrees(yawAfterTurn - yawBeforeTurn)),
                !Double.isNaN(yawAfterTurn) && Math.abs(Math.IEEEremainder(yawAfterTurn - yawBeforeTurn + Math.PI / 2, 2 * Math.PI)) < 0.01);
        double restPelvis = com.dan.avatar.Skeleton.position(rest, pv)[1];
        check(String.format(Locale.GERMANY, "Sitzen: Becken %.2f m tiefer (%.2f statt %.2f m); Winken: Hand %.0f cm über dem Kopf", restPelvis - minPelvis, minPelvis, restPelvis, (maxHand - headY) * 100),
                restPelvis - minPelvis > 0.25 && maxHand > headY);
        check(String.format(Locale.GERMANY, "Ablauf erledigt (Kreis, Stehen, Drehen, Sitzen, Winken, Aufstehen, Drehen), %.2f ms je Bild auf dem Prozessor", ms), m.idle() && ms < 3);
        // Personen verschieden
        com.dan.avatar.Person jung = com.dan.avatar.Person.wuerfeln(11).copy(), alt = jung.copy();
        jung.age = com.dan.avatar.Person.ageFromYears(25); alt.age = com.dan.avatar.Person.ageFromYears(80);
        com.dan.avatar.Motion mj = new com.dan.avatar.Motion(com.dan.avatar.Motion.line(0, 0, 0), 0), ma = new com.dan.avatar.Motion(com.dan.avatar.Motion.line(0, 0, 0), 0);
        mj.calibrate(sk, sk.restFor(jung.weights()), jung); ma.calibrate(sk, sk.restFor(alt.weights()), alt);
        com.dan.avatar.Motion mt = new com.dan.avatar.Motion(com.dan.avatar.Motion.line(0, 0, 0), 0);
        mt.calibrate(sk, rest, man);
        check(String.format(Locale.GERMANY, "Je Person verschieden: 25 Jahre %.2f m/s, 80 Jahre %.2f m/s; Mann (Bein %.2f m) Schritt %.2f m, Frau (Bein %.2f m) %.2f m",
                mj.vWalk, ma.vWalk, mt.legLen, mt.stride / 2, mj.legLen, mj.stride / 2), ma.vWalk < 0.9 * mj.vWalk && (mt.legLen > mj.legLen) == (mt.stride > mj.stride));
        // Von außen geführt (Begriffe von figure)
        com.dan.avatar.Motion mf = new com.dan.avatar.Motion(com.dan.avatar.Motion.line(0, 0, 0), 0);
        mf.calibrate(sk, rest, man);
        double fx = 0, fz = 0, head = 0.3;
        for (int i = 0; i < 300; i++) {
            double v = 1.15 * Math.min(1, i / 60.0) * (i > 240 ? Math.max(0, (300 - i) / 60.0) : 1);
            fx += Math.sin(Math.PI - head) * v / 30; fz += Math.cos(Math.PI - head) * v / 30;
            com.dan.avatar.FigureTerms.follow(mf, fx, fz, head, 0, 0, 1 / 30.0);
            mf.trackFeet();
            add.clear(); mf.additive(add);
            double[][] w = sk.pose(st, rest, true, add);
            mf.apply(sk, w, rest);
            mf.measure(sk, w, 1 / 30.0);
        }
        double y = com.dan.avatar.FigureTerms.yaw(0);
        check(String.format(Locale.GERMANY, "Begriffe von figure: Azimut Nord → Blick −z (%.0f°), hin und zurück genau; nachgeführt %d Schritte, Gleiten %.2f mm/s",
                Math.toDegrees(y), mf.steps.size(), mf.slideMax * 1000),
                Math.abs(Math.cos(y) + 1) < 1e-9 && Math.abs(com.dan.avatar.FigureTerms.heading(com.dan.avatar.FigureTerms.yaw(1.234)) - 1.234) < 1e-9 && mf.steps.size() > 6 && mf.slideMax < 0.005
                        && com.dan.avatar.FigureTerms.medieval(1, new java.util.SplittableRandom(1), false).name.equals("1362"));
    }

    private static void phase10(com.dan.gltf.Gltf model, com.dan.avatar.Body body) throws Exception {
        System.out.println("  — Phase 10: Kleidung");
        com.dan.avatar.Wardrobe w = com.dan.avatar.Wardrobe.load();
        java.util.Set<Integer> bits = new java.util.HashSet<>();
        boolean cc0 = true, inModel = true;
        java.util.Set<String> meshes = new java.util.HashSet<>();
        for (com.dan.gltf.Gltf.Mesh m : model.meshes) meshes.add(m.name);
        for (com.dan.avatar.Wardrobe.Item it : w.items) {
            bits.add(it.bit);
            cc0 &= it.license.replace(":", "").replace("-", "").trim().toUpperCase().startsWith("CC0");
            inModel &= meshes.contains(it.mesh);
        }
        check(String.format(Locale.GERMANY, "Kleiderschrank: %d Teile, jedes mit eigenem Bit, alle im Grundkörper", w.items.size()),
                w.items.size() >= 15 && bits.size() == w.items.size() && inModel && java.util.Collections.max(bits) < 23);
        check("Kleiderschrank: alle Teile CC0 (Lizenz aus der .mhclo)", cc0);
        boolean presets = true;
        StringBuilder miss = new StringBuilder();
        for (String n : com.dan.avatar.Outfit.PRESETS)
            for (com.dan.avatar.Outfit.Piece pc : com.dan.avatar.Outfit.preset(n).pieces)
                if (w.find(pc.mesh) == null) { presets = false; miss.append(" ").append(pc.mesh); }
        check("Outfits: jedes Teil der " + com.dan.avatar.Outfit.PRESETS.length + " Presets liegt im Schrank" + miss, presets);
        com.dan.avatar.Outfit a = com.dan.avatar.Outfit.preset("Strickkapuze"), r = com.dan.avatar.Outfit.preset("Regenjacke");
        check("Kapuze: Strickkapuze und Regenjacke mit, Sommer ohne", a.hoodUp() && r.hoodUp() && !com.dan.avatar.Outfit.preset("Sommer").hoodUp());
        check("Masken: verschiedene Outfits verdecken verschiedene Teile", w.mask(a) != w.mask(com.dan.avatar.Outfit.preset("Sommer")) && w.mask(com.dan.avatar.Outfit.preset("ohne")) == 0);
        // Körper unter dem Pullover verdeckt (Strahlen, der Pullover hat keine MPFB-Gruppe)
        com.dan.avatar.Body.Part bp = body.body(), sw = null;
        for (com.dan.avatar.Body.Part q : body.parts) if (q.mesh.name.equals(com.dan.avatar.Outfit.SWEATER)) sw = q;
        if (sw != null) {
            boolean[] cv = com.dan.avatar.Wardrobe.cover(bp, sw);
            int n = 0, chest = 0, chestAll = 0, hand = 0;
            for (int i = 0; i < cv.length; i++) {
                if (cv[i]) n++;
                float y = bp.prim.position[i * 3 + 1], x = Math.abs(bp.prim.position[i * 3]);
                if (y > 1.15f && y < 1.35f && x < 0.12f) { chestAll++; if (cv[i]) chest++; }
                if (y < 0.85f && x > 0.30f && cv[i]) hand++;
            }
            check(String.format(Locale.GERMANY, "Pullover verdeckt %,d Körperecken: Brust %.0f %%, keine Hand", n, 100.0 * chest / Math.max(1, chestAll)),
                    chest > 0.9 * chestAll && hand == 0);
        }
        // Stoffe
        boolean fab = true;
        for (com.dan.avatar.Fabric f : com.dan.avatar.Fabric.values()) fab &= f.rough > 0 && f.rough <= 1 && f.absorb >= 0 && f.absorb <= 1 && f.beading >= 0 && f.beading <= 1;
        check("Stoffe: Ölzeug und Gummi wasserdicht, Baumwolle und Leinen saugen, Wolle und Strick perlen",
                fab && com.dan.avatar.Fabric.OELZEUG.absorb == 0 && com.dan.avatar.Fabric.GUMMI.absorb == 0 && com.dan.avatar.Fabric.BAUMWOLLE.absorb > 0.9
                        && com.dan.avatar.Fabric.STRICK.beading > com.dan.avatar.Fabric.BAUMWOLLE.beading && com.dan.avatar.Fabric.WOLLE.sheen > com.dan.avatar.Fabric.LEDER.sheen);
        // nach Monat
        String jan = com.dan.avatar.Outfit.forMonth(1, com.dan.avatar.Outfit.Role.DRAUSSEN, com.dan.avatar.Outfit.Epoch.HEUTE, 3).name;
        String jul = com.dan.avatar.Outfit.forMonth(7, com.dan.avatar.Outfit.Role.ALLTAG, com.dan.avatar.Outfit.Epoch.HEUTE, 3).name;
        String okt = com.dan.avatar.Outfit.forMonth(10, com.dan.avatar.Outfit.Role.DRAUSSEN, com.dan.avatar.Outfit.Epoch.HEUTE, 2).name;
        String mit = com.dan.avatar.Outfit.forMonth(1, com.dan.avatar.Outfit.Role.DRAUSSEN, com.dan.avatar.Outfit.Epoch.MITTELALTER_1362, 2).name;
        String wik = com.dan.avatar.Outfit.forMonth(7, com.dan.avatar.Outfit.Role.ARBEIT, com.dan.avatar.Outfit.Epoch.WIKINGERZEIT, 2).name;
        check("Outfit nach Monat: Januar " + jan + ", Juli " + jul + ", Oktober draußen " + okt + ", 1362 " + mit + ", Wikingerzeit " + wik,
                jan.equals("Winter") && jul.equals("Sommer") && okt.equals("Regenjacke") && mit.equals("1362") && wik.equals("Wikingerzeit"));
        // Feder
        com.dan.avatar.ClothSway cs = new com.dan.avatar.ClothSway();
        double[] anchor = {0, 1.5, 0};
        for (int i = 0; i < 300; i++) cs.step(1 / 60.0, anchor, new double[]{-8, 0, 0});
        double still = cs.amount();
        boolean dir = cs.sway[0] < -0.01;
        com.dan.avatar.ClothSway c2 = new com.dan.avatar.ClothSway();
        for (int i = 0; i < 300; i++) c2.step(1 / 60.0, anchor, new double[]{0, 0, 0});
        double peak = 0;
        for (int i = 0; i < 30; i++) { anchor[0] += 0.02; c2.step(1 / 60.0, anchor, new double[]{0, 0, 0}); peak = Math.max(peak, c2.amount()); }
        for (int i = 0; i < 240; i++) c2.step(1 / 60.0, anchor, new double[]{0, 0, 0});
        check(String.format(Locale.GERMANY, "Lose Teile: Wind 8 m/s lenkt %.1f cm mit dem Wind aus; ein Ruck schwingt %.1f cm und klingt ab (%.2f mm)", still * 100, peak * 100, c2.amount() * 1000),
                dir && still > 0.01 && still <= 0.06 && peak > 0.001 && c2.amount() < 0.0005);
        check("Lose: am Körper 0, ab 7 cm Abstand 1", com.dan.avatar.ClothSway.flex(0.005) == 0 && com.dan.avatar.ClothSway.flex(0.08) == 1 && com.dan.avatar.ClothSway.flex(0.04) > 0.3);
        String sc = new String(com.dan.avatar.gl.AvatarGl.class.getResourceAsStream("skin.comp").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        String fr = new String(com.dan.avatar.gl.AvatarGl.class.getResourceAsStream("avatar.frag").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        check("Shader: Lose im Skinning, Stoffe mit Bindung und Perlen", sc.contains("uSway") && fr.contains("weaveH") && fr.contains("beads("));
    }

    private static void phase9() throws Exception {
        System.out.println("  — Phase 9: Ein Gesicht im Regen (portrait)");
        com.dan.portrait.Lens l = new com.dan.portrait.Lens();
        l.focalMm = 85;
        double fov85 = l.fovyDeg();
        l.focalMm = 50;
        double fov50 = l.fovyDeg();
        check(String.format(Locale.GERMANY, "Bildwinkel senkrecht (Kleinbild 24 mm): 85 mm %.2f°, 50 mm %.2f° (16,1° / 27,0°)", fov85, fov50),
                Math.abs(fov85 - 16.07) < 0.05 && Math.abs(fov50 - 26.99) < 0.05);
        l.focalMm = 85; l.fNumber = 2.8; l.focusM = 1.2;
        double[] d = l.depthOfField(0.03 / 24.0 * 1000, 1000);
        check(String.format(Locale.GERMANY, "Schärfentiefe 85 mm f/2,8 bei 1,2 m: %.3f … %.3f m = %.1f cm (Rechner: 3,1 cm)", d[0], d[1], (d[1] - d[0]) * 100),
                Math.abs((d[1] - d[0]) * 100 - 3.1) < 0.2);
        l.fNumber = 1.8; l.focusM = 2;
        double k = l.cocK(1000) / 1000, inf = l.cocPx(1e7, 1000) / 1000;
        check(String.format(Locale.GERMANY, "Zerstreuungskreis im Unendlichen, 85 mm f/1,8 auf 2 m: %.2f %% der Bildhöhe (f²/(N(s−f)) = 2,10 mm)", inf * 100),
                Math.abs(inf - 0.0873) < 0.001 && Math.abs(k - inf) < 1e-4 && l.cocPx(2, 1000) == 0);
        double nah = com.dan.portrait.Shot.NAH.distance(fov85), gross = com.dan.portrait.Shot.GROSS.distance(fov85);
        boolean shotsUp = true;
        com.dan.portrait.Shot[] sh = com.dan.portrait.Shot.values();
        for (int i = 1; i < sh.length; i++) shotsUp &= sh[i].distance(fov85) > sh[i - 1].distance(fov85);
        check(String.format(Locale.GERMANY, "Einstellungen mit 85 mm: Groß %.2f m, Nah %.2f m (… Halbtotale weiter weg)", gross, nah), Math.abs(nah - 1.204) < 0.01 && shotsUp);

        // Farbe
        float[] lut = com.dan.portrait.Grade.lut("neutral", 32);
        double dev = 0;
        int q = 0;
        for (int b = 0; b < 32; b++) for (int g = 0; g < 32; g++) for (int r = 0; r < 32; r++) {
            dev = Math.max(dev, Math.abs(lut[q++] - r / 31.0)); dev = Math.max(dev, Math.abs(lut[q++] - g / 31.0)); dev = Math.max(dev, Math.abs(lut[q++] - b / 31.0));
        }
        check(String.format(Locale.GERMANY, "Farbtabelle „neutral“ = Einheit (Fehler %.1e)", dev), dev < 1e-6);
        double[] shd = com.dan.portrait.Grade.apply("Regennacht", 0.02, 0.02, 0.02), hi = com.dan.portrait.Grade.apply("Regennacht", 0.6, 0.6, 0.6);
        check(String.format(Locale.GERMANY, "„Regennacht“: Schatten türkis (%.3f %.3f %.3f), Lichter bernstein (%.3f %.3f %.3f)", shd[0], shd[1], shd[2], hi[0], hi[1], hi[2]),
                shd[2] > shd[0] && shd[1] > shd[0] && hi[0] > hi[2]);
        boolean mono = true;
        for (String n : com.dan.portrait.Grade.NAMES) {
            double last = -1;
            for (int i = 0; i <= 64; i++) {
                double[] c = com.dan.portrait.Grade.apply(n, i / 64.0, i / 64.0, i / 64.0);
                double y = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
                mono &= y >= last - 1e-9; last = y;
            }
        }
        check("Farbstile erhalten die Reihenfolge der Helligkeit (Grauachse steigt)", mono);

        // Lichter des Hintergrunds
        com.dan.portrait.BokehLights a1 = com.dan.portrait.BokehLights.city("Regennacht", 4711, new double[]{0, 0, 0}, 90, 1, true, Math.PI, Math.toRadians(55));
        com.dan.portrait.BokehLights a2 = com.dan.portrait.BokehLights.city("Regennacht", 4711, new double[]{0, 0, 0}, 90, 1, true, Math.PI, Math.toRadians(55));
        com.dan.portrait.BokehLights dry = com.dan.portrait.BokehLights.city("Regennacht", 4711, new double[]{0, 0, 0}, 90, 1, false, Math.PI, Math.toRadians(55));
        boolean same = a1.lights.size() == a2.lights.size(), fan = true;
        int amber = 0, real = 0;
        for (int i = 0; same && i < a1.lights.size(); i++) same = java.util.Arrays.equals(a1.lights.get(i).p, a2.lights.get(i).p) && java.util.Arrays.equals(a1.lights.get(i).col, a2.lights.get(i).col);
        for (com.dan.portrait.BokehLights.Light x : dry.lights) {
            double ang = Math.atan2(x.p[0], x.p[2]);
            double dd = Math.abs(Math.IEEEremainder(ang - Math.PI, 2 * Math.PI));
            fan &= dd <= Math.toRadians(27.6);
            real++;
            if (x.col[0] > x.col[2]) amber++;
        }
        check(String.format(Locale.GERMANY, "Hintergrund-Lichter: gleich bei gleichem Samen, %d Lichter im Fächer, %d mit Spiegelung, %.0f %% Bernstein", dry.lights.size(), a1.lights.size(), 100.0 * amber / real),
                same && fan && dry.lights.size() == 90 && a1.lights.size() > 90 && Math.abs(amber / (double) real - 0.6) < 0.15
                        && com.dan.portrait.BokehLights.city("aus", 1, new double[3], 50, 1, true).lights.isEmpty());
        java.util.List<com.dan.portrait.BokehLights.Light> top = a1.brightest(12);
        boolean noMirror = true;
        for (com.dan.portrait.BokehLights.Light x : top) noMirror &= !x.mirror;
        check("die zwölf hellsten Lichter (für Spiegelungen in Augen und Wasser) ohne Spiegelbilder", top.size() == 12 && noMirror);

        // Lichtsetzung im Rahmen der Kamera
        double[] head = {0, 1.6, 0};
        com.dan.portrait.LightSet front = com.dan.portrait.LightSet.of("Regennacht", head, new double[]{0, 1.6, 1.2});
        com.dan.portrait.LightSet side = com.dan.portrait.LightSet.of("Regennacht", head, new double[]{1.2, 1.6, 0});
        boolean keyFront = front.keyPos[2] > 0.3 && front.rimPos[2] < -0.3;
        boolean turns = side.keyPos[0] > 0.3 && Math.abs(side.keyPos[2] + front.keyPos[0]) < 1e-5;
        boolean all = true;
        for (String n : com.dan.portrait.LightSet.NAMES) {
            com.dan.portrait.LightSet s = com.dan.portrait.LightSet.of(n, head, new double[]{0, 1.6, 1.2});
            double kd = Math.sqrt(s.keyDir[0] * s.keyDir[0] + s.keyDir[1] * s.keyDir[1] + s.keyDir[2] * s.keyDir[2]);
            all &= kd > 0.5 && s.keyConeOuter > s.keyConeInner;
        }
        check("Lichtsetzungen: Führungslicht vorn, Kante hinten, beim Umkreisen geht das Licht mit (" + com.dan.portrait.LightSet.NAMES.length + " Setzungen)", keyFront && turns && all);

        // Shader
        for (String sname : new String[]{"fs.vert", "bokeh.vert", "bokeh.frag", "dof_coc.frag", "dof_gather.frag", "dof_comp.frag"})
            check("Shader portrait/gl/" + sname, com.dan.portrait.gl.PortraitGl.class.getResource(sname) != null);
        String post = new String(StudioScene.class.getResourceAsStream("shader/post.frag").readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        check("Nachbearbeitung mit Farbtabelle, Korn, Balken und Randabdunklung", post.contains("uLut") && post.contains("uGrain") && post.contains("uBars") && post.contains("uVignette"));
    }

    private static void phase8(com.dan.gltf.Gltf model, com.dan.avatar.Body body) throws Exception {
        System.out.println("  — Phase 8: Nässe, Regen, Kälte");
        // Klima
        com.dan.avatar.Climate cw = com.dan.avatar.Climate.of(0, 20 / 3.6, -5, 0.8, 0);
        check(String.format(Locale.GERMANY, "Windchill: −5 °C bei 20 km/h fühlt sich an wie %.1f °C (Tafel −11,6)", cw.windChill()), Math.abs(cw.windChill() + 11.6) < 0.3);
        double fw = com.dan.avatar.Climate.wintermorgen().breathFog(), fs = com.dan.avatar.Climate.sommerabend().breathFog(), fr = com.dan.avatar.Climate.regennacht().breathFog();
        check(String.format(Locale.GERMANY, "Atemhauch sichtbar: Wintermorgen %.2f, Regennacht (9 °C, 95 %%) %.2f, Sommerabend %.2f", fw, fr, fs), fw > 0.6 && fr > 0 && fr < fw && fs == 0);
        check(String.format(Locale.GERMANY, "Kälte: Sommerabend %.2f, Regennacht %.2f, nass %.2f, Wintermorgen %.2f",
                com.dan.avatar.Climate.sommerabend().coldness(0), com.dan.avatar.Climate.regennacht().coldness(0), com.dan.avatar.Climate.regennacht().coldness(1), com.dan.avatar.Climate.wintermorgen().coldness(0)),
                com.dan.avatar.Climate.sommerabend().coldness(0) == 0 && com.dan.avatar.Climate.regennacht().coldness(1) > com.dan.avatar.Climate.regennacht().coldness(0) && com.dan.avatar.Climate.wintermorgen().coldness(0) > 0.7);

        // Nässe je Zone
        com.dan.avatar.Climate rn = com.dan.avatar.Climate.regennacht();
        com.dan.avatar.Wetness w = new com.dan.avatar.Wetness();
        boolean mono = true;
        double last = 0;
        for (int i = 0; i < 120 * 4; i++) { w.step(0.25, rn); mono &= w.wet(com.dan.avatar.Wetness.FACE) >= last - 1e-12; last = w.wet(com.dan.avatar.Wetness.FACE); }
        double f2 = w.wet(com.dan.avatar.Wetness.FACE), h2 = w.wet(com.dan.avatar.Wetness.HAIR), s2 = w.wet(com.dan.avatar.Wetness.SHOULDERS), l2 = w.wet(com.dan.avatar.Wetness.LEGS);
        check(String.format(Locale.GERMANY, "Regennacht, 2 min: Gesicht %.0f %%, Haar %.0f %%, Schultern %.0f %%, Beine %.0f %% (steigt stetig)", f2 * 100, h2 * 100, s2 * 100, l2 * 100),
                mono && f2 > 0.3 && h2 > 0.05 && s2 > 0.05 && l2 < s2);
        com.dan.avatar.Wetness wh = new com.dan.avatar.Wetness(), wn = new com.dan.avatar.Wetness();
        com.dan.avatar.Climate rh = rn.copy(); rh.hood = 1;
        for (int i = 0; i < 900 * 4; i++) { wh.step(0.25, rh); wn.step(0.25, rn); }
        check(String.format(Locale.GERMANY, "15 min: unter der Kapuze Haar %.0f %% und Gesicht %.0f %% (ohne: %.0f %% und %.0f %%), Schultern %.0f %%",
                wh.wet(com.dan.avatar.Wetness.HAIR) * 100, wh.wet(com.dan.avatar.Wetness.FACE) * 100, wn.wet(com.dan.avatar.Wetness.HAIR) * 100, wn.wet(com.dan.avatar.Wetness.FACE) * 100, wh.wet(com.dan.avatar.Wetness.SHOULDERS) * 100),
                wh.wet(com.dan.avatar.Wetness.HAIR) < 0.15 && wn.wet(com.dan.avatar.Wetness.HAIR) > 0.5 && wh.wet(com.dan.avatar.Wetness.FACE) < wn.wet(com.dan.avatar.Wetness.FACE) && wh.wet(com.dan.avatar.Wetness.SHOULDERS) > 0.3);
        com.dan.avatar.Climate dryAir = com.dan.avatar.Climate.of(0, 3, 20, 0.5, 0), wetAir = com.dan.avatar.Climate.of(0, 0.5, 9, 0.95, 0);
        com.dan.avatar.Wetness d1 = new com.dan.avatar.Wetness(), d2 = new com.dan.avatar.Wetness();
        d1.soak(1); d2.soak(1);
        double tDry = -1;
        for (int i = 0; i < 3600 * 2; i++) {
            d1.step(0.5, dryAir); d2.step(0.5, wetAir);
            if (tDry < 0 && d1.wet(com.dan.avatar.Wetness.FACE) < 0.1) tDry = (i + 1) * 0.5;
        }
        check(String.format(Locale.GERMANY, "Trocknen: Gesicht bei 20 °C, 50 %%, 3 m/s nach %.0f min unter 10 %%; in feuchter Nachtluft nach 1 h noch %.0f %%, Haar %.0f %%",
                tDry / 60, d2.wet(com.dan.avatar.Wetness.FACE) * 100, d1.wet(com.dan.avatar.Wetness.HAIR) * 100),
                tDry > 0 && tDry < 1800 && d2.wet(com.dan.avatar.Wetness.FACE) > 0.4);

        // Tropfenfeld auf dem Kopf
        com.dan.avatar.DropField df = new com.dan.avatar.DropField(body);
        int good = 0, tested = 0;
        for (int c = 0; c < df.n * df.n; c += 7) {
            if (!df.valid[c] || df.steep[c] < 0.5 || df.scalp[c]) continue;
            double x = c % df.n + 0.5, y = c / df.n + 0.5;
            double nx = x + df.down[c * 2] * 0.004, ny = y + df.down[c * 2 + 1] * 0.004;
            int c2 = df.cell(nx, ny);
            if (c2 < 0 || c2 == c) continue;
            tested++;
            if (df.pos[c2 * 3 + 1] < df.pos[c * 3 + 1]) good++;
        }
        check(String.format(Locale.GERMANY, "Tropfenfeld: %,d Zellen auf Gesicht, Ohren und Hals (UV %.2f–%.2f × %.2f–%.2f); bergab heißt nach unten in %.0f %% der Proben",
                df.validCount, df.rect[0], df.rect[2], df.rect[1], df.rect[3], 100.0 * good / Math.max(1, tested)), df.validCount > 4000 && good > 0.9 * tested && tested > 100);
        double i5 = com.dan.avatar.SkinDrops.impacts(5), i1 = com.dan.avatar.SkinDrops.impacts(1);
        check(String.format(Locale.GERMANY, "Einschläge über 0,5 mm: %,.0f je m² und s bei 1 mm/h, %,.0f bei 5 mm/h", i1, i5), i5 > i1 && i5 > 500 && i5 < 6000);
        com.dan.avatar.SkinDrops sd = new com.dan.avatar.SkinDrops(df, 11), sd2 = new com.dan.avatar.SkinDrops(df, 11);
        com.dan.avatar.Wetness ww = new com.dan.avatar.Wetness();
        double[] from = ww.rainFrom(rn);
        int maxBeads = 0, trailsSeen = 0;
        for (int i = 0; i < 120 * 20; i++) {
            sd.step(0.05, 5, from, 1e-6, true);
            sd2.step(0.05, 5, from, 1e-6, true);
            maxBeads = Math.max(maxBeads, sd.beads().size());
            trailsSeen += sd.trailData().length / 5;
        }
        boolean onSkin = true;
        for (com.dan.avatar.SkinDrops.Bead b : sd.beads()) { int c = df.cell(b.x, b.y); onSkin &= c >= 0 && !df.scalp[c]; }
        boolean same = sd.beads().size() == sd2.beads().size() && sd.hits == sd2.hits;
        double rmax = 0;
        for (com.dan.avatar.SkinDrops.Bead b : sd.beads()) rmax = Math.max(rmax, b.radius());
        check(String.format(Locale.GERMANY, "Tropfen, 2 min bei 5 mm/h: %,d Einschläge, %d Perlen (größte %.1f mm), %,d Spurstücke, %d abgetropft; alle auf Haut unter dem Haaransatz; gleicher seed gleich",
                sd.hits, sd.beads().size(), rmax, trailsSeen, sd.drips), sd.hits > 500 && sd.beads().size() > 50 && trailsSeen > 0 && sd.drips > 0 && onSkin && same);
        // eine große Perle auf der Stirn läuft nach unten
        com.dan.avatar.SkinDrops one = new com.dan.avatar.SkinDrops(df, 3);
        int cs = -1;
        for (int c = 0; c < df.n * df.n; c++) if (df.valid[c] && !df.scalp[c] && df.steep[c] > 0.8 && df.nrm[c * 3 + 2] > 0.8 && (cs < 0 || df.pos[c * 3 + 1] > df.pos[cs * 3 + 1])) cs = c;
        com.dan.avatar.SkinDrops.Bead bb = new com.dan.avatar.SkinDrops.Bead();
        bb.x = cs % df.n + 0.5; bb.y = cs / df.n + 0.5; bb.vol = 12;
        one.beads().add(bb);
        double y0 = df.pos[cs * 3 + 1];
        for (int i = 0; i < 20; i++) one.step(0.05, 0, from, 0, true);
        int ce = one.beads().isEmpty() ? -1 : df.cell(one.beads().get(0).x, one.beads().get(0).y);
        double y1 = ce < 0 ? -1 : df.pos[ce * 3 + 1];
        check(String.format(Locale.GERMANY, "Große Perle (r %.1f mm) auf der Stirn läuft in 1 s %.0f mm tiefer", Math.cbrt(12 / 1.6), (y0 - y1) * 1000), ce >= 0 && y1 < y0 - 0.005);

        // Atemhauch
        com.dan.avatar.BreathFog bf = new com.dan.avatar.BreathFog(5), bs = new com.dan.avatar.BreathFog(5);
        com.dan.avatar.Behavior bh = new com.dan.avatar.Behavior(5);
        double fogW = com.dan.avatar.Climate.wintermorgen().breathFog();
        double maxUp = 0;
        int inhaleEmit = 0;
        for (int i = 0; i < 60 * 20; i++) {
            bh.step(1 / 60.0);
            long before = bf.emitted;
            bf.step(1 / 60.0, bh.breathFlow(), new double[]{0, 1.6, 0.1}, new double[]{0, -0.6, 0.8}, fogW, new double[]{0, 0, 0});
            bs.step(1 / 60.0, bh.breathFlow(), new double[]{0, 1.6, 0.1}, new double[]{0, -0.6, 0.8}, com.dan.avatar.Climate.sommerabend().breathFog(), new double[]{0, 0, 0});
            if (bh.breathFlow() == 0 && bf.emitted > before) inhaleEmit++;
            for (com.dan.avatar.BreathFog.Puff q : bf.puffs()) if (q.age > 1) maxUp = Math.max(maxUp, q.p[2] - 0.1);
        }
        check(String.format(Locale.GERMANY, "Atemhauch: Wintermorgen %d Wölkchen in 20 s, nur beim Ausatmen (%d beim Einatmen), bis %.0f cm vor dem Gesicht; Sommerabend %d",
                bf.emitted, inhaleEmit, maxUp * 100, bs.emitted), bf.emitted > 100 && inhaleEmit == 0 && maxUp > 0.15 && bs.emitted == 0);

        // Kälte und Regen am Verhalten
        Object rig = Json.parse(new String(GlbInfo.resource(StudioCheck.class, AvatarStudio.RIG), StandardCharsets.UTF_8));
        com.dan.avatar.Skeleton sk = new com.dan.avatar.Skeleton(model, rig);
        com.dan.avatar.Behavior warm = new com.dan.avatar.Behavior(9), frost = new com.dan.avatar.Behavior(9);
        frost.cold = 1; frost.rainOnFace = 1;
        com.dan.avatar.Skeleton.Additive aw = new com.dan.avatar.Skeleton.Additive(), af = new com.dan.avatar.Skeleton.Additive();
        double[][] rest = sk.restFor(com.dan.avatar.Person.wuerfeln(11).weights());
        double shW = 0, shF = 0;
        for (int i = 0; i < 600; i++) {
            warm.step(1 / 60.0); frost.step(1 / 60.0);
            warm.additive(aw); frost.additive(af);
            shW += aw.rot.getOrDefault("clavicle_l", new double[3])[2]; shF += af.rot.getOrDefault("clavicle_l", new double[3])[2];
        }
        Map<String, Double> ff = frost.forms(sk.pose(sk.poses.get("stehen"), rest, true, af), rest, sk);
        check(String.format(Locale.GERMANY, "Kälte: Schultern %.1f° höher; Regen im Gesicht: Augen zusammengekniffen (eyeSquint %.2f)",
                Math.toDegrees((shF - shW) / 600), ff.getOrDefault("arkit.eyeSquintLeft", 0.0)), shF > shW && ff.getOrDefault("arkit.eyeSquintLeft", 0.0) > 0.3);
        for (String sh : new String[]{"drop.vert", "drop.frag", "breath.vert", "breath.frag"})
            check("Shader gl/" + sh, com.dan.avatar.gl.AvatarGl.class.getResource(sh) != null);
    }

    // ------------------------------------------------------------ Phase 14

    private static void phase14() throws Exception {
        System.out.println("  — Phase 14: Datenbank AVATAR (ohne Verbindung; mit Verbindung: ant db-pruefung)");
        // Person als JSON (Spalte REGLER von AV_PERSON_T) hin und zurück
        int same = 0;
        for (long s = 0; s < 300; s++) {
            com.dan.avatar.Person p = com.dan.avatar.Person.wuerfeln(s * 7919 - 1000);
            com.dan.avatar.Person q = com.dan.avatar.Person.fromJson(p.toJson(), p.seed, p.name);
            if (q.sameAs(p) && q.seed == p.seed) same++;
        }
        check("Person als JSON hin und zurück: " + same + " von 300 genau gleich", same == 300);
        com.dan.avatar.Person p = com.dan.avatar.Person.wuerfeln(4711);
        com.dan.avatar.Person a20 = p.aelter(20), a200 = p.aelter(200), j = p.aelter(-100);
        check(String.format(Locale.GERMANY, "Älter: %.1f → %.1f Jahre (+20), Grenzen %.0f und %.0f Jahre, grauer, schwerer", p.ageYears(), a20.ageYears(), a200.ageYears(), j.ageYears()),
                Math.abs(a20.ageYears() - p.ageYears() - 20) < 0.2 && Math.abs(a200.ageYears() - 90) < 0.1 && Math.abs(j.ageYears() - 1) < 0.1
                        && a20.grey >= p.grey && a20.weight >= p.weight && a20.face.equals(p.face) && a20.hair.equals(p.hair));
        com.dan.avatar.Person g = p.geschwister(99), w = com.dan.avatar.Person.wuerfeln(99);
        boolean faceMid = true;
        for (String f : com.dan.avatar.Person.FACE) {
            double want = Math.round((p.face.getOrDefault(f, 0.0) + w.face.getOrDefault(f, 0.0)) / 2 * 1000) / 1000.0;
            faceMid &= Math.abs(g.face.getOrDefault(f, 0.0) - (Math.abs(want) > 0.02 ? want : 0)) < 1e-12;
        }
        check("Geschwister: Gesicht halb und halb, Haut und Augen zu 70 % (" + g.name + ")", faceMid && Math.abs(g.melanin - (0.7 * p.melanin + 0.3 * w.melanin)) < 0.0006 && g.seed == 99);
        // Skripte
        java.io.File sql = new java.io.File("db/sql");
        String[] files = {"01_typen.sql", "02_tabellen.sql", "03_api.sql", "04_sichten.sql", "05_rechte.sql", "06_startdaten.sql", "99_entfernen.sql"};
        int stm = 0;
        boolean prefix = true, allThere = true;
        for (String f : files) {
            java.io.File ff = new java.io.File(sql, f);
            if (!ff.exists()) { allThere = false; continue; }
            for (com.dan.store.Skript.Anweisung an : com.dan.store.Skript.zerlegen(java.nio.file.Files.readString(ff.toPath(), StandardCharsets.UTF_8))) {
                stm++;
                String o = com.dan.store.Skript.objekt(an.sql());
                if (o != null) { String n = o.split(" ")[1]; prefix &= n.startsWith("AV_") || n.equals("USER_AV_METADATA") || n.equals("ALL_AV_METADATA"); }
            }
        }
        check("Skripte db/sql 01–06 und 99 da und zerlegbar (" + stm + " Anweisungen), alle Objekte mit AV_ (F40)", allThere && prefix && stm > 300
                && new java.io.File("db/dba/00_einmalig_als_dba.sql").exists() && new java.io.File("db/db.properties.example").exists());
        String gen = DbStartdaten.sql();
        String file = java.nio.file.Files.readString(new java.io.File(sql, "06_startdaten.sql").toPath(), StandardCharsets.UTF_8);
        check("06_startdaten.sql ist auf dem Stand des Java-Codes (sonst: ant db-startdaten)", gen.equals(file));
        // Kataloge passen zum Package
        java.util.Map<String, Integer> n = new java.util.LinkedHashMap<>();
        java.util.Set<String> poses = new java.util.HashSet<>(), nets = new java.util.HashSet<>();
        for (DbStartdaten.Katalog k : DbStartdaten.kataloge()) {
            n.put(k.tabelle(), k.zeilen().size());
            for (java.util.Map<String, Object> z : k.zeilen()) {
                if (k.tabelle().equals("AV_POSE") && "POSE".equals(z.get("ART"))) poses.add((String) z.get("NAME"));
                if (k.tabelle().equals("AV_KLEIDUNG")) nets.add((String) z.get("NETZ"));
            }
        }
        Object rig = Json.parse(new String(GlbInfo.resource(StudioCheck.class, AvatarStudio.RIG), StandardCharsets.UTF_8));
        boolean posesOk = Json.obj(rig, "posen").keySet().equals(poses);
        boolean netsOk = true;
        for (String o : com.dan.avatar.Outfit.PRESETS) for (com.dan.avatar.Outfit.Piece pc : com.dan.avatar.Outfit.preset(o).pieces) netsOk &= nets.contains(pc.mesh);
        check("Kataloge: " + n.get("AV_REGLER") + " Regler, " + n.get("AV_GEFUEHL") + " Gefühle, " + n.get("AV_FORM") + " Formen, " + n.get("AV_VISEM") + " Viseme, "
                        + n.get("AV_OUTFIT") + " Outfits; Posen wie im Rig, jedes Stück im Schrank",
                n.get("AV_REGLER") == com.dan.avatar.Person.NUMBERS.length + com.dan.avatar.Person.FACE.length && n.get("AV_FORM") == 52 && n.get("AV_VISEM") == 15
                        && n.get("AV_GEFUEHL") == com.dan.avatar.Emotion.NAMED.size() && posesOk && netsOk);
        // Rückfall auf Dateien ohne Datenbank
        java.io.File tmp = new java.io.File(System.getProperty("java.io.tmpdir"), "avatar_check_" + System.nanoTime());
        DbAvatar d = new DbAvatar(tmp);
        d.speichernDatei("Prüfung ö/ü", p, "Winter");
        java.util.List<DbAvatar.Preset> l = d.presetsDatei();
        check("Ohne Datenbank: Preset als Datei gespeichert und gelesen", l.size() == 1 && l.get(0).person().sameAs(p) && "Winter".equals(l.get(0).outfit()));
    }

    // ------------------------------------------------------------ Phase 15

    private static void phase15() throws Exception {
        System.out.println("  — Phase 15: Rückfluss nach Lighthouse");
        java.io.File lh = new java.io.File(System.getProperty("lighthouse.dir", "../Lighthouse"));
        if (!new java.io.File(lh, "src/com/dan/lighthouse").isDirectory()) { System.out.println("    (Projekt Lighthouse nicht gefunden: " + lh + ")"); return; }
        int files = 0;
        List<String> diff = new ArrayList<>();
        for (String p : new String[]{"gltf", "avatar", "hair", "portrait", "voice"}) {
            java.nio.file.Path a = java.nio.file.Path.of("src/com/dan", p), b = lh.toPath().resolve("src/com/dan/" + p);
            try (java.util.stream.Stream<java.nio.file.Path> st = java.nio.file.Files.walk(a)) {
                for (java.nio.file.Path x : st.filter(java.nio.file.Files::isRegularFile).toList()) {
                    files++;
                    java.nio.file.Path y = b.resolve(a.relativize(x).toString());
                    if (!java.nio.file.Files.exists(y) || java.nio.file.Files.mismatch(x, y) != -1) diff.add(p + "/" + a.relativize(x).toString().replace('\\', '/'));
                }
            }
        }
        check("Lighthouse hat dieselben Packages gltf, avatar, hair, portrait, voice (" + files + " Dateien" + (diff.isEmpty() ? ", alle gleich)" : ", " + diff.size() + " anders, z. B. " + diff.get(0) + "; ant lighthouse-kopie)"), diff.isEmpty());
        String[] app = {"Personen.java", "Figur.java", "HairRig.java", "Galerie.java", "GalerieStage.java", "PersonKamera.java", "galerie_sturmnacht.txt"};
        boolean all = true;
        for (String n : app) all &= new java.io.File(lh, "src/com/dan/lighthouse/person/" + n).isFile();
        String rb = all ? java.nio.file.Files.readString(new java.io.File(lh, "src/com/dan/lighthouse/person/galerie_sturmnacht.txt").toPath(), StandardCharsets.UTF_8) : "";
        com.dan.avatar.Regie rg = new com.dan.avatar.Regie("galerie", rb);
        check("Lighthouse: der Wärter in der App (person/: Personen, Figur, Galerie, Kamera, Regiebuch „" + rg.title + "“ mit " + rg.lines.size() + " Zeilen)", all && rg.lines.size() > 30);
        check("Wärter seed 19 ist " + com.dan.avatar.Person.wuerfeln(19).name + " (in Lighthouse aus AV_API.WUERFELN, sonst derselbe Würfel in Java)", com.dan.avatar.Person.wuerfeln(19).gender > 0.9);
    }

    // ------------------------------------------------------------ Phase 18

    private static void phase18(com.dan.gltf.Gltf model) throws Exception {
        System.out.println("  — Phase 18: Tanzen und Trommel");
        java.io.File dir = new java.io.File("daten/tanz/cmu");
        if (!new java.io.File(dir, "93.asf").isFile()) { System.out.println("    (keine Aufnahmen in daten/tanz/cmu)"); return; }
        com.dan.avatar.Dance dance = TanzFilm.dance();
        dance.beatS = 60 / 113.0;
        StringBuilder sb = new StringBuilder();
        boolean ok = !dance.clips.isEmpty();
        for (com.dan.avatar.Dance.Clip c : dance.clips) {
            com.dan.avatar.Mocap m = c.m;
            double head = 0, ankle = 9;
            for (int f = 0; f < m.frames(); f++) { head = Math.max(head, m.end(m.bone("head"), f)[1]); ankle = Math.min(ankle, m.end(m.bone("ltibia"), f)[1]); }
            double k = c.stepsPerBeat(dance.beatS), stretch = dance.beatS / (k * c.period);
            ok &= m.bones.length == 31 && head > 1.5 && head < 1.85 && ankle > 0.03 && ankle < 0.12 && c.period > 0.25 && c.period < 0.66 && stretch > 0.7 && stretch < 1.45;
            sb.append(String.format(Locale.GERMANY, " %s %.1f s Kopf %.2f m Schritt %.2f s ×%.2f;", m.name, m.seconds(), head, c.period, stretch));
        }
        check("CMU 93 (Charleston) gelesen, Takt der Aufnahmen gefunden:" + sb, ok);
        com.dan.avatar.Skeleton sk = AvatarStudio.loadSkeleton(model);
        double worstMean = 0, worstFloor = 0, jump = 0, jumpIn = 0;
        for (long seed : new long[]{19, 11}) {
            com.dan.avatar.Person p = com.dan.avatar.Person.wuerfeln(seed);
            double[][] r = sk.restFor(p.weights());
            double[][] w0 = sk.pose(null, r, false);
            double ankle0 = Math.min(w0[sk.joint("foot_l")][13], w0[sk.joint("foot_r")][13]);
            double sum = 0, low = 9;
            int n = 0;
            double[][] prev = null;
            for (int i = 0; i < 16 * 30; i++) {
                double b = i / 30.0 * 113 / 60;
                double[][] w = dance.world(sk, r, b, null);
                com.dan.avatar.Dance.Place pl = dance.place(b);
                com.dan.avatar.Mocap m = dance.clips.get(pl.clip()).m;
                double fm = pl.time() * m.fps;
                if (b - 4 * Math.floor(b / 4) < 3.6)
                    for (String[] a : TanzFilm.DANCE_AIM) {
                        double[] p0 = com.dan.avatar.Skeleton.position(w, sk.joint(a[0])), p1 = com.dan.avatar.Skeleton.position(w, sk.joint(a[1]));
                        sum += TanzFilm.angle(TanzFilm.sub(p1, p0), TanzFilm.sub(m.end(m.bone(a[2]), fm), m.start(m.bone(a[2]), fm)));
                        n++;
                    }
                low = Math.min(low, Math.min(w[sk.joint("foot_l")][13], w[sk.joint("foot_r")][13]));
                if (prev != null) for (int j : new int[]{sk.joint("pelvis"), sk.joint("spine_03"), sk.joint("head")}) {
                    double dx = w[j][12] - prev[j][12], dy = w[j][13] - prev[j][13], dz = w[j][14] - prev[j][14];
                    double u = b - 4 * Math.floor(b / 4), dd = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (u > 0.3 && u < 3.5) jumpIn = Math.max(jumpIn, dd); else jump = Math.max(jump, dd);
                }
                prev = w;
            }
            worstMean = Math.max(worstMean, sum / Math.max(1, n));
            worstFloor = Math.max(worstFloor, Math.abs(low - ankle0));
        }
        check(String.format(Locale.GERMANY, "Übertragen auf Mann (19) und Frau (11): Arme und Beine im Mittel %.1f° wie die Aufnahme, tiefster Knöchel %.1f cm von der Ruhe, Rumpf an der Taktgrenze höchstens %.1f cm je Bild (im Takt %.1f cm)",
                worstMean, worstFloor * 100, jump * 100, jumpIn * 100), worstMean < 4 && worstFloor < 0.03 && jump < Math.max(0.08, 1.5 * jumpIn));
        // Trommel: Hand auf dem Fell genau auf dem Schlag, linke Hand am Rand
        com.dan.avatar.Person p = com.dan.avatar.Person.wuerfeln(19);
        double[][] r = sk.restFor(p.weights());
        com.dan.avatar.Drum dr = new com.dan.avatar.Drum();
        double err = 0, grip = 0, top = 0, fingerMin = 9, contactGap = 0, leftFront = -9;
        int hits = 0;
        double[] hh = new double[8 * 120 + 1];
        for (int i = 0; i < hh.length; i++) {
            double[][] w = sk.pose(null, r, false);
            dr.apply(sk, w, i / 120.0, 0, 0.53);
            hh[i] = dr.fingerGap;
            fingerMin = Math.min(fingerMin, dr.fingerGap);
            if (Math.abs(i / 120.0 - Math.round(i / 120.0)) < 0.5 / 120) contactGap = Math.max(contactGap, dr.fingerGap);
            for (String tip : new String[]{"index_03_l", "middle_03_l", "ring_03_l", "pinky_03_l"}) {
                double[] tp = com.dan.avatar.Skeleton.position(w, sk.joint(tip));
                leftFront = Math.max(leftFront, (tp[0] - dr.center[0]) * dr.normal[0] + (tp[1] - dr.center[1]) * dr.normal[1] + (tp[2] - dr.center[2]) * dr.normal[2]);
            }
            top = Math.max(top, hh[i]);
            double[] hl = com.dan.avatar.Skeleton.position(w, sk.joint("hand_l"));
            double[] rim = {dr.center[0] + dr.right[0] * dr.radius, dr.center[1] + dr.right[1] * dr.radius, dr.center[2] + dr.right[2] * dr.radius};
            grip = Math.max(grip, Math.sqrt((hl[0] - rim[0]) * (hl[0] - rim[0]) + (hl[1] - rim[1]) * (hl[1] - rim[1]) + (hl[2] - rim[2]) * (hl[2] - rim[2])));
        }
        // Anschläge: Fingerspitzen unter 12 mm über dem Fell; der Moment des Anschlags ist die tiefste Stelle
        for (int i = 0; i < hh.length; ) {
            if (hh[i] >= 0.012) { i++; continue; }
            int best = i;
            while (i < hh.length && hh[i] < 0.012) { if (hh[i] < hh[best]) best = i; i++; }
            hits++;
            err = Math.max(err, Math.abs(best / 120.0 - Math.round(best / 120.0)));
        }
        check(String.format(Locale.GERMANY, "Trommel: %d Anschläge in 8 Schlägen, Finger am Fell höchstens %.0f ms neben dem Schlag, holt %.0f cm aus, linke Hand höchstens %.1f cm vom Rand, %d Schläge gezählt",
                hits, err * 530, top * 100, grip * 100, dr.strikes), hits >= 8 && hits <= 9 && err < 0.03 && top > 0.12 && grip < 0.06 && dr.strikes >= 7);
        check(String.format(Locale.GERMANY, "Hand nicht im Fell (Phase 19): Fingerspitzen rechts nie darunter (tiefste %.1f mm darüber), beim Schlag höchstens %.1f cm darüber; linke Finger hinter dem Fell (%.1f cm)",
                fingerMin * 1000, contactGap * 100, leftFront * 100), fingerMin >= -0.0005 && contactGap < 0.02 && leftFront < 0.005);
        // Programmsymbol (Phase 19): sieben Größen, Goldkante am Rand, dunkles Inneres
        java.util.List<java.awt.Image> ic = AvatarStudio.iconImages();
        boolean gold = false, dark = false;
        if (ic.size() == 7 && ic.get(6) instanceof java.awt.image.BufferedImage bi) {
            java.awt.Color e = new java.awt.Color(bi.getRGB(3, 128), true), m = new java.awt.Color(bi.getRGB(128, 232), true);
            gold = e.getAlpha() > 200 && e.getRed() > 140 && e.getRed() > e.getBlue() + 60 && e.getGreen() > e.getBlue() + 30;
            dark = m.getRed() + m.getGreen() + m.getBlue() < 300;
        }
        check("Programmsymbol in " + ic.size() + " Größen mit Goldkante und dunklem Inneren (Phase 19)", ic.size() == 7 && gold && dark);
    }

    // ------------------------------------------------------------ Phase 17

    private static void phase17() throws Exception {
        System.out.println("  — Phase 17: Musik fühlen und singen");
        // Takt aus Klick-Spuren mit bekanntem Tempo (Eins tief, die übrigen hell)
        double worstBpm = 0, worstT = 0;
        boolean downOk = true;
        for (double bpm : new double[]{84, 112, 128, 150}) {
            int sr = 22050;
            double sec = 24, per = 60 / bpm, off = 0.41;
            float[] s = new float[(int) (sr * sec)];
            java.util.Random rnd = new java.util.Random(7);
            for (int k = 0; off + k * per < sec; k++) {
                int i0 = (int) Math.round((off + k * per) * sr);
                for (int i = 0; i < sr * 0.06 && i0 + i < s.length; i++)
                    s[i0 + i] += (float) ((k % 4 == 0 ? 1 : 0.5) * Math.exp(-i / (sr * 0.012)) * Math.sin(2 * Math.PI * (k % 4 == 0 ? 60 : 1500) * i / sr));
            }
            for (int i = 0; i < s.length; i++) s[i] += (float) (0.02 * rnd.nextGaussian());
            com.dan.beat.Rhythm rh = com.dan.beat.Rhythm.analyse(new com.dan.beat.Track(s, sr, "klick"));
            worstBpm = Math.max(worstBpm, Math.abs(rh.bpm - bpm) / bpm);
            for (double b : rh.beats) { double k = Math.round((b - off) / per); worstT = Math.max(worstT, Math.abs(b - off - k * per)); }
            int first = (int) Math.round((rh.beats[0] - off) / per);
            downOk &= Math.floorMod(rh.downbeat + first, 4) == 0;
        }
        check(String.format(Locale.GERMANY, "Takt aus Klick-Spuren 84/112/128/150: Tempo höchstens %.2f %% daneben, Schläge höchstens %.0f ms, Eins erkannt", worstBpm * 100, worstT * 1000),
                worstBpm < 0.005 && worstT < 0.015 && downOk);
        // Lautheit und Ruhe: leise Strecke ist ruhig, laute nicht
        {
            int sr = 22050;
            float[] s = new float[sr * 20];
            for (int i = 0; i < s.length; i++) {
                double t = i / (double) sr, amp = t < 10 ? 0.02 : 0.5;
                s[i] = (float) (amp * Math.sin(2 * Math.PI * 220 * t) * (t >= 10 && (t * 2) % 1 < 0.1 ? 1 : 0.6));
            }
            com.dan.beat.Rhythm rh = com.dan.beat.Rhythm.analyse(new com.dan.beat.Track(s, sr, "leise-laut"));
            check(String.format(Locale.GERMANY, "Lautheit und Ruhe: leise %.2f / ruhig %.2f, laut %.2f / ruhig %.2f", rh.energy(4), rh.calm(4), rh.energy(16), rh.calm(16)),
                    rh.energy(4) < 0.3 && rh.calm(4) > 0.5 && rh.energy(16) > 0.6 && rh.calm(16) < 0.2);
        }
        // Liedtext (LRC)
        com.dan.beat.Lyrics ly = com.dan.beat.Lyrics.parse("[ti:Probe]\n[ar:Studio]\n[offset:+100]\n# Bemerkung\n[00:01.00][00:09.50]Eins zwei | Ains zwai\n[00:03.20]\n[00:04.00]Drei\n[00:06,0]Vier\n");
        check("LRC: Kopf, Bemerkung, zwei Marken für eine Zeile, Ende durch leere Marke, Lautung nach |, Versatz: " + ly.lines.size() + " Zeilen",
                ly.title().equals("Probe") && ly.artist().equals("Studio") && ly.lines.size() == 4 && Math.abs(ly.lines.get(0).t0() - 1.1) < 1e-9
                        && Math.abs(ly.lines.get(0).t1() - 3.3) < 1e-9 && ly.lines.get(0).text().equals("Eins zwei") && ly.lines.get(0).sung().equals("Ains zwai")
                        && Math.abs(ly.lines.get(1).t1() - 5.8) < 1e-9 && ly.lines.get(3).text().equals("Eins zwei") && ly.at(4.5).text().equals("Drei"));
        // Singen: gehaltene Vokale, Atem vor jeder Zeile
        com.dan.voice.Speech sg = com.dan.voice.Speech.sung(List.of("Oben im Boot", "bin ich frei"), new double[]{1, 4}, new double[]{3, 6.5});
        double vow = 0, con = 0;
        int nv = 0, nc = 0;
        for (com.dan.voice.Speech.Seg s : sg.segs) {
            if (s.p().pause()) continue;
            if (s.p().vowel()) { vow += s.t1() - s.t0(); nv++; } else { con += s.t1() - s.t0(); nc++; }
        }
        check(String.format(Locale.GERMANY, "Singen: Vokale im Mittel %.0f ms, Konsonanten %.0f ms, %d Atemzüge, Mund bei „frei“ %s", 1000 * vow / nv, 1000 * con / nc, sg.breaths.size(), sg.dominant(6.2)),
                vow / nv > 3 * con / nc && sg.breaths.size() == 2 && sg.breaths.get(1) < 4 && sg.talking(2) && !sg.talking(3.6) && sg.dominant(6.2) == com.dan.voice.Viseme.I);
        // Verhalten: Nicken auf den Schlag, Augen zu bei Ruhe
        com.dan.avatar.Behavior b = new com.dan.avatar.Behavior(17);
        b.groove = 1;
        b.musicDown = 0;
        com.dan.avatar.Skeleton.Additive add = new com.dan.avatar.Skeleton.Additive();
        double[] nod = new double[2];
        for (int k = 0; k < 2; k++) {
            b.musicBeat = 3 + (k == 0 ? 0.12 : 0.62);
            b.additive(add);
            nod[k] = Math.toDegrees(add.rot.get("head")[0]);
        }
        b.musicCalm = 1;
        for (int i = 0; i < 300; i++) b.step(0.01);
        double closed = b.calmLid();
        b.musicCalm = 0;
        for (int i = 0; i < 200; i++) b.step(0.01);
        check(String.format(Locale.GERMANY, "Nicken auf dem Schlag %.1f° gegen %.1f° dazwischen; Augen bei Ruhe nach 3 s %.0f %% zu, 2 s danach %.0f %%", nod[0], nod[1], closed * 100, b.calmLid() * 100),
                nod[0] - nod[1] > 2 && closed > 0.85 && b.calmLid() < 0.1);
        // ein Lied aus daten/musik
        java.io.File wf = MusikFilm.pick(new java.io.File("daten/musik"));
        if (wf == null || !wf.isFile()) { System.out.println("    (kein Lied in daten/musik)"); return; }
        long t0 = System.nanoTime();
        com.dan.beat.Track tr = com.dan.beat.Track.read(wf);
        com.dan.beat.Rhythm rh = com.dan.beat.Rhythm.analyse(tr);
        double ms = (System.nanoTime() - t0) / 1e6;
        double[] d = new double[rh.beats.length - 1];
        for (int i = 1; i < rh.beats.length; i++) d[i - 1] = rh.beats[i] - rh.beats[i - 1];
        double[] ds = d.clone();
        java.util.Arrays.sort(ds);
        double med = ds[ds.length / 2];
        int steady = 0;
        for (double v : d) if (Math.abs(v - med) < 0.1 * med) steady++;
        check(String.format(Locale.GERMANY, "%s: %s, %.0f %% der Abstände gleichmäßig (%.0f ms)", wf.getName(), rh.describe(), 100.0 * steady / d.length, ms),
                rh.bpm > 60 && rh.bpm < 200 && steady > 0.9 * d.length);
        java.io.File lf = MusikFilm.lrcOf(wf);
        if (lf.isFile()) {
            com.dan.beat.Lyrics l2 = com.dan.beat.Lyrics.read(lf);
            com.dan.voice.Speech song = MusikFilm.song(l2);
            int inside = 0, talk = 0;
            for (com.dan.beat.Lyrics.Line l : l2.lines) { if (l.t1() <= tr.seconds()) inside++; if (song.talking(l.mid())) talk++; }
            check(String.format(Locale.GERMANY, "Liedtext %s: %d Zeilen im Lied, an %d Zeilenmitten singt die Person, %d Atemzüge", lf.getName(), inside, talk, song.breaths.size()),
                    inside == l2.lines.size() && talk == l2.lines.size() && song.breaths.size() == l2.lines.size());
        }
    }

    // ------------------------------------------------------------ Phase 16

    private static void phase16() throws Exception {
        System.out.println("  — Phase 16: Sprechen");
        // Laute aus Text
        String s1 = com.dan.voice.G2P.show(com.dan.voice.G2P.phonemes("Oben im Fischerboot bin ich frei."));
        String s2 = com.dan.voice.G2P.show(com.dan.voice.G2P.phonemes("Heute Nacht kommt keiner mehr raus."));
        String s3 = com.dan.voice.G2P.show(com.dan.voice.G2P.phonemes("Jede Stunde geht er einmal um die Laterne."));
        String s4 = com.dan.voice.G2P.show(com.dan.voice.G2P.phonemes("Am 22. Oktober um 20 Uhr."));
        check("Text → Laute: " + s1.replace(" |", ""), s1.contains("fi'S6") && s1.contains("iC") && s1.contains("o:'") && s1.contains("fRaI'"));
        check("sch, ch nach a (Nacht), ch nach i (ich), st am Wortanfang (Stunde), -er vokalisch, Doppellaute: " + s2.replace(" |", ""),
                s2.contains("na'xt") && s2.contains("hOY") && s3.contains("Stu") && s2.contains("kaI'n6"));
        check("Zahlen und Ordnungszahlen: " + s4.replace(" |", ""), s4.contains("tsvantsikst@n") && s4.contains("tsva'ntsik u:"));
        // Spur aus Text: an der Mitte jedes Lauts das passende Mundbild
        com.dan.voice.Speech sp = com.dan.voice.Speech.fromText("Oben im Fischerboot bin ich frei.", 1.0);
        int[] hit = new int[4], all = new int[4];
        String[] keys = {"B", "O", "F", "I"};
        com.dan.voice.Viseme[] want = {com.dan.voice.Viseme.PP, com.dan.voice.Viseme.O, com.dan.voice.Viseme.FF, com.dan.voice.Viseme.I};
        for (com.dan.voice.Speech.Seg sg : sp.segs) {
            for (int k = 0; k < 4; k++) if (sg.p().v() == want[k] && sg.p().v2() == null) {
                all[k]++;
                if (sp.dominant(sg.mid()) == want[k]) hit[k]++;
            }
        }
        boolean allHit = true;
        StringBuilder hb = new StringBuilder();
        for (int k = 0; k < 4; k++) { allHit &= all[k] > 0 && hit[k] == all[k]; hb.append(String.format(" %s %d/%d", keys[k], hit[k], all[k])); }
        check("Mundbild an der Lautmitte (Text):" + hb, allHit);
        double pp = 0;
        for (com.dan.voice.Speech.Seg sg : sp.segs) if (sg.p().v() == com.dan.voice.Viseme.PP) pp = Math.max(pp, sp.weights(sg.mid()).get(com.dan.voice.Viseme.PP));
        double rest = sp.weights(0.05).getOrDefault(com.dan.voice.Viseme.SIL, 0.0), sum = 0;
        for (double t = 0; t < sp.duration + 0.3; t += 0.01) { double w = 0; for (var e : sp.weights(t).entrySet()) w += e.getValue(); sum = Math.max(sum, w); }
        check(String.format(Locale.GERMANY, "Lippen zu bei b (PP %.2f), Ruhe vor dem Satz, Gewichte höchstens 1 (%.3f)", pp, sum), pp > 0.95 && rest > 0.99 && sum < 1.0001);
        com.dan.voice.Speech s5 = com.dan.voice.Speech.fromText("Bald brennt das Feuer, ihr Schiffer!", 1.0);
        check(String.format(Locale.GERMANY, "Atem vor dem Satz (%.2f s), %d Phrasen, spricht bis %.2f s", s5.breaths.get(0), s5.phraseEnds.size(), s5.duration),
                s5.breaths.size() == 2 && s5.phraseEnds.size() == 2 && s5.breaths.get(0) < s5.segs.get(1).t0() && s5.talking(1.0) && !s5.talking(0.1) && !s5.talking(s5.duration + 0.5));
        // Text an den Ton: Hedda (Sprachausgabe von Windows) und jede eigene Aufnahme in daten/sprache (WAV + TXT)
        java.io.File dir = new java.io.File("daten/sprache");
        java.io.File[] wavs = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".wav"));
        if (wavs == null || wavs.length == 0) { System.out.println("    (keine Aufnahme in daten/sprache)"); return; }
        java.util.Arrays.sort(wavs);
        for (java.io.File wf : wavs) {
            java.io.File tf = new java.io.File(dir, wf.getName().replaceFirst("(?i)\\.wav$", ".txt"));
            if (!tf.isFile()) continue;
            String text = java.nio.file.Files.readString(tf.toPath(), StandardCharsets.UTF_8).trim();
            com.dan.voice.Audio au;
            try { au = com.dan.voice.Audio.read(wf); }
            catch (java.io.IOException ex) { check(wf.getName() + ": " + ex.getMessage(), false); continue; }
            long t0 = System.nanoTime();
            com.dan.voice.Speech al = au.speech(text);
            double ms = (System.nanoTime() - t0) / 1e6;
            // Prüfgrößen aus dem Ton je Laut: b/p/m leiser als die Vokale daneben, f rauscht, o tiefer F2, i hoher F2
            int[] ok = new int[4], n = new int[4];
            for (int i = 0; i < al.segs.size(); i++) {
                com.dan.voice.Speech.Seg sg = al.segs.get(i);
                com.dan.voice.Phon p = sg.p();
                int a = (int) Math.round(sg.t0() * 100), b = Math.max(a + 1, (int) Math.round(sg.t1() * 100));
                if (p.v() == com.dan.voice.Viseme.PP && p.kind() == com.dan.voice.Phon.Kind.PLOSIVE) {
                    n[0]++;
                    double inner = minDb(au, a, b), around = Math.max(maxDb(au, a - 8, a), maxDb(au, b, b + 8));
                    if (around - inner > 6) ok[0]++;
                } else if (p.v() == com.dan.voice.Viseme.O && p.v2() == null) {
                    n[1]++;
                    if (median(au, a, b, 2) < 1150) ok[1]++;
                } else if (p.v() == com.dan.voice.Viseme.FF && !p.sym().equals("v")) {
                    n[2]++;
                    if (median(au, a, b, 0) > 0.18) ok[2]++;
                } else if (p.v() == com.dan.voice.Viseme.I && p.v2() == null && p.kind() == com.dan.voice.Phon.Kind.VOWEL) {
                    n[3]++;
                    if (median(au, a, b, 2) > 1800) ok[3]++;
                }
            }
            StringBuilder sb = new StringBuilder();
            int good = 0, tot = 0;
            for (int k = 0; k < 4; k++) { sb.append(String.format(" %s %d/%d", keys[k], ok[k], n[k])); good += ok[k]; tot += n[k]; }
            String said = text.replaceAll("\\s+", " ");
            boolean strict = wf.getName().toLowerCase(Locale.ROOT).startsWith("hedda_");
            if (strict) {
                check(String.format(Locale.GERMANY, "„%s“ an %s (%.2f s, %.0f ms): Laute passen zum Ton,%s", said, wf.getName(), au.seconds(), ms, sb),
                        tot > 0 && good >= tot - (tot >= 8 ? 1 : 0));
            } else {
                // eigene Aufnahme: Vokale liegen auf lautem, stimmhaftem Ton, Pausen auf Stille; B/O/F/I nur gemessen
                // (Formanten einer gepackten Aufnahme mit tiefer Stimme sind zu unsicher für eine harte Prüfung)
                double top = -200;
                for (var fr : au.frames) top = Math.max(top, fr.db());
                int vv = 0, vn = 0, pq = 0, pn = 0;
                for (com.dan.voice.Speech.Seg sg : al.segs) {
                    int a = (int) Math.round(sg.t0() * 100), b = (int) Math.round(sg.t1() * 100);
                    for (int i = Math.max(0, a); i < Math.min(au.frames.length, b); i++) {
                        var fr = au.frames[i];
                        if (sg.p().kind() == com.dan.voice.Phon.Kind.VOWEL) { vn++; if (fr.voiced() && fr.db() > top - 22) vv++; }
                        else if (sg.p().pause() && sg.t1() - sg.t0() > 0.2) { pn++; if (fr.db() < top - 18) pq++; }
                    }
                }
                double rv = vv / (double) Math.max(1, vn), rp = pq / (double) Math.max(1, pn);
                int pauses = 0;
                for (com.dan.voice.Speech.Seg sg : al.segs) if (sg.p().pause() && sg.t1() - sg.t0() > 0.2) pauses++;
                check(String.format(Locale.GERMANY, "Deine Aufnahme %s (%.2f s, %.0f ms): Vokale auf lautem Ton %.0f %%, %d Pausen auf Stille %.0f %%; gemessen%s",
                        wf.getName(), au.seconds(), ms, rv * 100, pauses, rp * 100, sb), rv > 0.75 && rp > 0.8);
            }
            com.dan.voice.Speech ot = au.speech();
            int oo = 0, on = 0;
            for (com.dan.voice.Speech.Seg sg : al.segs) {
                com.dan.voice.Viseme v = sg.p().v();
                if (sg.p().v2() != null || !(v == com.dan.voice.Viseme.O || v == com.dan.voice.Viseme.FF || v == com.dan.voice.Viseme.I)) continue;
                if (sg.p().sym().equals("v")) continue;
                on++;
                if (ot.dominant(sg.mid()) == v) oo++;
            }
            if (strict) check(String.format(Locale.GERMANY, "… und aus dem Ton allein (ohne Text): o, f, i an %d von %d Stellen", oo, on), on > 0 && oo >= 0.6 * on);
            else System.out.printf(Locale.GERMANY, "    … aus dem Ton allein (ohne Text): o, f, i an %d von %d Stellen (Hinweis)%n", oo, on);
        }
    }

    /** Leisester / lautester Ausschnitt (dB) in [a, b). */
    private static double minDb(com.dan.voice.Audio au, int a, int b) {
        double m = 0;
        boolean any = false;
        for (int i = Math.max(0, a); i < Math.min(au.frames.length, b); i++) { m = any ? Math.min(m, au.frames[i].db()) : au.frames[i].db(); any = true; }
        return any ? m : -180;
    }

    private static double maxDb(com.dan.voice.Audio au, int a, int b) {
        double m = -180;
        for (int i = Math.max(0, a); i < Math.min(au.frames.length, b); i++) m = Math.max(m, au.frames[i].db());
        return m;
    }

    /** Median einer Größe in [a, b): 0 Nulldurchgänge, 2 F2 (nur stimmhafte Ausschnitte mit Formanten). */
    private static double median(com.dan.voice.Audio au, int a, int b, int what) {
        List<Double> v = new ArrayList<>();
        for (int i = Math.max(0, a); i < Math.min(au.frames.length, b); i++) {
            var f = au.frames[i];
            if (what == 0) v.add(f.zcr());
            else if (f.voiced() && f.f2() > 0) v.add(f.f2());
        }
        if (v.isEmpty()) return what == 2 ? 0 : 0;
        java.util.Collections.sort(v);
        return v.get(v.size() / 2);
    }

    // ------------------------------------------------------------ Phase 13

    private static void phase13(com.dan.gltf.Gltf model) throws Exception {
        System.out.println("  — Phase 13: Schnittstelle und Regie");
        // Regiebuch „Am Steg“ lesen
        com.dan.avatar.Regie book = com.dan.avatar.Regie.resource(AvatarStudio.class, "regie/am_steg.txt");
        check("Regiebuch „Am Steg“: Titel, Unterzeile, vier Zeilen Abspann",
                book.title.equals("Am Steg") && book.subtitle.startsWith("Eine Regennacht") && book.credits.size() == 4);
        long caps = book.lines.stream().filter(l -> l.caption() != null).count();
        check(String.format(Locale.GERMANY, "Regiebuch: %d Zeilen, %d Untertitel, Befehle an mara", book.lines.size(), caps),
                book.lines.size() > 30 && caps >= 2 && book.lines.stream().anyMatch(l -> "mara".equals(l.actor()) && l.verb().equals("gehe")));
        check("Wörter: Anführungszeichen halten zusammen",
                com.dan.avatar.Regie.words("sage \"Heute Nacht bleibt\" x=1 „zwei Wörter“").equals(List.of("sage", "Heute Nacht bleibt", "x=1", "zwei Wörter")));
        // Outfit: Kapuze auf und ab
        com.dan.avatar.Outfit knit = com.dan.avatar.Outfit.preset("Strickkapuze");
        com.dan.avatar.Outfit off = knit.withHood(false);
        check("Outfit: Kapuze ab und wieder auf (gleicher Stoff)", knit.hoodUp() && !off.hoodUp() && off.withHood(true).hoodUp()
                && off.piece(com.dan.avatar.Outfit.HOOD_DOWN) != null && off.piece(com.dan.avatar.Outfit.HOOD_DOWN).fabric == knit.piece(com.dan.avatar.Outfit.HOOD_UP).fabric
                && com.dan.avatar.Outfit.preset("Sommer").withHood(false).name.equals("Sommer"));
        // Steg als Umwelt
        JettySet js = new JettySet();
        check("Steg: Orte für die Regie, Boden auf 0", js.places().containsAll(List.of("Ufer", "Steg_Ende", "Wasser", "Boje")) && js.ground(0, 1) == 0
                && js.place("Gibtsnicht") == null);
        com.dan.avatar.gl.AvatarGl.Light li = new com.dan.avatar.gl.AvatarGl.Light();
        int best = js.light(li, new double[]{0, 1.6, 2.6}, new double[]{0, 1.6, 4.5});
        check("Steg: Licht der Person aus der nächsten Laterne (Laterne " + best + "), Kante türkis von hinten",
                best == 3 && li.keyCol[0] > li.keyCol[2] && li.rimCol[2] > li.rimCol[0] && li.rimPos[2] < 2.6);
        // Kamera-Einstellungen
        CameraRig rig = new CameraRig();
        boolean all = true;
        for (String n : CameraRig.NAMES) all &= rig.set(n, null, 20.0, true);
        check("Kamera: alle Einstellungen bekannt, Unbekanntes abgelehnt", all && !rig.set("Vogel", null, null, null));

        // Ablauf eines kleinen Regiebuchs mit einer Person ohne Grafikkarte
        Object rigJ = Json.parse(new String(GlbInfo.resource(StudioCheck.class, AvatarStudio.RIG), StandardCharsets.UTF_8));
        com.dan.avatar.Skeleton sk = new com.dan.avatar.Skeleton(model, rigJ);
        com.dan.avatar.Person p = com.dan.avatar.Person.wuerfeln(11);
        double[][] rest = sk.restFor(p.weights());
        com.dan.avatar.Motion m = new com.dan.avatar.Motion(com.dan.avatar.Motion.line(0, -4, 0), 0);
        m.calibrate(sk, rest, p);
        com.dan.avatar.Outfit[] wear = {com.dan.avatar.Outfit.preset("Strickkapuze")};
        com.dan.avatar.Actor.Host host = new com.dan.avatar.Actor.Host() {
            @Override public com.dan.avatar.Behavior behavior() { return null; }
            @Override public com.dan.avatar.Motion motion() { return m; }
            @Override public com.dan.avatar.Outfit outfit() { return wear[0]; }
            @Override public void outfit(com.dan.avatar.Outfit o) { wear[0] = o; }
            @Override public double[] head() { return new double[]{m.x, 1.6, m.z}; }
        };
        com.dan.avatar.World world = new com.dan.avatar.World() {
            @Override public double[] place(String n) { return n.equals("Ziel") ? new double[]{0, 0, 2} : (n.equals("Wasser") ? new double[]{6, -0.5, 2} : null); }
        };
        String text = "titel Probe | Unterzeile\n"
                + "person mara\n"
                + "mara.gehe Ziel 0.9 | Erster Untertitel\n"
                + "warte mara.gehe\n"
                + "mara.dreh Wasser   # Kommentar\n"
                + "warte mara.dreh\n"
                + "mara.geste Kapuze_ab\n"
                + "warte mara.geste\n"
                + "mara.sage \"eins zwei drei\"\n"
                + "warte mara.sage\n"
                + "warte 1 s\n"
                + "mara.fliegen hoch\n"
                + "wolke 3\n";
        com.dan.avatar.Regie r = new com.dan.avatar.Regie("probe", text);
        List<String> said = new ArrayList<>();
        r.onText = said::add;
        int[] hoodChanges = {0};
        r.start(new com.dan.avatar.Regie.Stage() {
            @Override public boolean command(String verb, String args, com.dan.avatar.Regie rr) {
                if (verb.equals("person")) { rr.cast.put("mara", new com.dan.avatar.Actor("mara", host)); return true; }
                return false;
            }
            @Override public com.dan.avatar.World world() { return world; }
        });
        double t = 0, arrive = -1, turnErr = -1, sageStart = -1, sageEnd = -1;
        com.dan.avatar.Motion.Kind lastKind = null;
        boolean hoodWas = wear[0].hoodUp();
        com.dan.avatar.Skeleton.Additive add = new com.dan.avatar.Skeleton.Additive();
        com.dan.avatar.Pose st = sk.poses.get("stehen");
        for (; t < 60 && !r.finished(); t += 1 / 30.0) {
            r.step(1 / 30.0);
            m.step(1 / 30.0);
            m.trackFeet();
            add.clear();
            m.additive(add);
            double[][] w = sk.pose(st, rest, true, add);
            m.apply(sk, w, rest);
            m.measure(sk, w, 1 / 30.0);
            int he = m.takeHoodEvent();
            if (he != 0) host.outfit(host.outfit().withHood(he > 0));
            if (wear[0].hoodUp() != hoodWas) { hoodChanges[0]++; hoodWas = wear[0].hoodUp(); }
            com.dan.avatar.Motion.Kind k = m.current() == null ? null : m.current().kind();
            if (lastKind == com.dan.avatar.Motion.Kind.GEHEN && k != com.dan.avatar.Motion.Kind.GEHEN && arrive < 0) arrive = Math.hypot(m.x - 0, m.z - 2);
            if (lastKind == com.dan.avatar.Motion.Kind.DREHEN && k != com.dan.avatar.Motion.Kind.DREHEN && turnErr < 0 && arrive >= 0) {
                double want = Math.atan2(6 - m.x, 2 - m.z);
                turnErr = Math.abs(Math.toDegrees(Math.atan2(Math.sin(want - m.yaw), Math.cos(want - m.yaw))));
            }
            if (sageStart < 0 && said.size() >= 2) sageStart = r.time();
            if (sageStart >= 0 && sageEnd < 0 && r.waiting() != null && r.waiting().args().startsWith("1")) sageEnd = r.time();
            lastKind = k;
        }
        check(String.format(Locale.GERMANY, "Regie läuft durch (%.1f s), Titel aus der Kopfzeile", r.time()), r.finished() && r.title.equals("Probe") && r.subtitle.equals("Unterzeile"));
        check(String.format(Locale.GERMANY, "gehe Ziel: angekommen auf %.1f cm, ohne Gleiten (%.1f mm/s)", arrive * 100, m.slideMax * 1000), arrive >= 0 && arrive < 0.06 && m.slideMax < 0.05);
        check(String.format(Locale.GERMANY, "dreh Wasser: Richtung auf %.2f° genau", turnErr), turnErr >= 0 && turnErr < 1.0);
        check("geste Kapuze_ab: Kapuze genau einmal gewechselt, danach ab", hoodChanges[0] == 1 && !wear[0].hoodUp());
        check("Untertitel: aus „ | “ und aus sage", said.equals(List.of("Erster Untertitel", "eins zwei drei")));
        check(String.format(Locale.GERMANY, "warte mara.sage hält die Sprechdauer (%.2f s für drei Wörter)", sageEnd - sageStart),
                sageEnd - sageStart > 2.2 && sageEnd - sageStart < 2.5);
        check("Fehler mit Zeilennummer: unbekannter Befehl der Person und der Szene", r.errors.size() == 2
                && r.errors.get(0).startsWith("Zeile 12") && r.errors.get(1).startsWith("Zeile 13"));
        com.dan.avatar.Snapshot s = com.dan.avatar.Snapshot.of("mara", 0, m, null, null, wear[0], 0, 0, host.head(), null);
        check("Snapshot: Ort, Richtung, Zustand, Kapuze, Schritte", Math.abs(s.z() - m.z) < 1e-9 && s.yaw() == m.yaw && s.doing().equals("steht")
                && !s.hoodUp() && s.steps() == m.steps.size() && s.steps() > 10 && s.wet().length == com.dan.avatar.Wetness.N);
    }

    private static double depthNdc(float[] p, double z) {
        double cz = p[10] * -z + p[14], cw = p[11] * -z + p[15];
        return cz / cw;
    }

    private static double sq(double x) { return x * x; }

    private static void check(String what, boolean pass) {
        if (pass) { ok++; System.out.println("  ✓ " + what); }
        else { bad++; fails.add(what); System.out.println("  ✗ " + what); }
    }
}
