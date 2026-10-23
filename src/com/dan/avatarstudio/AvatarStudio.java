package com.dan.avatarstudio;

import com.dan.avatar.Body;
import com.dan.avatar.Person;
import com.dan.avatar.Skeleton;
import com.dan.avatar.Skinning;
import com.dan.beat.Lyrics;
import com.dan.beat.Rhythm;
import com.dan.beat.Track;
import com.dan.fscrollpane.FScrollPane;
import com.dan.fbutton.FButton;
import com.dan.fcombobox.FComboBox;
import com.dan.gltf.Gltf;
import com.dan.gltf.GltfCache;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.Animator;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Avatar Studio – die Demo, die zugleich das Werkzeug ist (Werkbuch Abschnitt 7).
 * FFrame mit GL-Fläche, Kamera (Ziehen dreht, Rad zoomt, Doppelklick setzt zurück), HUD und
 * Bedienfeld in Blöcken. Seit Phase 2 steht eine Person auf der Bühne; der Block PERSON würfelt
 * und stellt die Makro-Regler, die sofort wirken. Die späteren Blöcke zeigen, wann sie kommen.
 *
 * <pre>
 *   AvatarStudio                      Fenster
 *   AvatarStudio --bild datei.png [breite höhe]   Statusbild ohne Fenster
 * </pre>
 */
public final class AvatarStudio {

    static final Color BG = new Color(12, 16, 20);
    static final Color PANEL = new Color(19, 26, 32);
    static final Color LINE = new Color(37, 49, 58);
    static final Color TEAL = new Color(79, 198, 201);
    static final Color INK = new Color(236, 238, 240);
    static final Color MUTED = new Color(147, 160, 170);
    static final Color AMBER = new Color(232, 176, 96);

    /** Stand des Studios (Unterzeile und HUD). */
    static final String STAND = "Phase 19 · Schliff";

    static final String GLB = "/com/dan/avatar/res/avatar_base.glb";
    static final String PROTOKOLL = "/com/dan/avatar/res/avatar_base.protokoll.json";
    static final String RIG = "/com/dan/avatar/res/avatar_base.rig.json";

    private AvatarStudio() {}

    /** Grundkörper laden, beim ersten Mal mit Abbild in daten/cache. */
    public static Gltf loadModel() throws java.io.IOException {
        byte[] glb = GlbInfo.resource(AvatarStudio.class, GLB);
        if (glb == null) throw new java.io.IOException("Grundkörper fehlt: " + GLB);
        return GltfCache.load(glb, Path.of("daten", "cache"), "avatar_base");
    }

    /** Skelett mit Gelenk-Morphs und Posen aus avatar_base.rig.json. */
    public static Skeleton loadSkeleton(Gltf model) throws java.io.IOException {
        byte[] rj = GlbInfo.resource(AvatarStudio.class, RIG);
        Object rig = rj == null ? null : com.dan.gltf.Json.parse(new String(rj, StandardCharsets.UTF_8));
        return new Skeleton(model, rig);
    }

    public static void main(String[] args) throws Exception {
        if (args.length >= 2 && args[0].equals("--tafel")) {
            tafel(new File(args[1]));
            return;
        }
        if (args.length >= 2 && args[0].equals("--gehdev")) {
            gehDev(new File(args[1]), args.length >= 3 ? args[2] : "walk");
            return;
        }
        if (args.length >= 2 && args[0].equals("--daten")) {
            daten(new File(args[1]), 1600, 900);
            return;
        }
        if (args.length >= 2 && args[0].equals("--dauerlauf")) {
            Dauerlauf.run(new File(args[1]), args.length >= 3 ? Double.parseDouble(args[2]) : 60);
            return;
        }
        if (args.length >= 2 && args[0].equals("--schliff")) {
            SchliffBild.run(new File(args[1]), 1600, 900);
            return;
        }
        if (args.length >= 2 && args[0].equals("--tanz")) {
            TanzFilm.run(new File(args[1]), args.length >= 3 ? new File(args[2]) : null, 1600, 900);
            return;
        }
        if (args.length >= 2 && args[0].equals("--musik")) {
            MusikFilm.run(new File(args[1]), args.length >= 3 ? new File(args[2]) : null, 1600, 900);
            return;
        }
        if (args.length >= 2 && args[0].equals("--sprechen")) {
            SprechFilm.run(new File(args[1]), args.length >= 3 ? new File(args[2]) : null, 1600, 900);
            return;
        }
        if (args.length >= 2 && args[0].equals("--steg")) {
            StegFilm.run(new File(args[1]), args.length >= 3 ? new File(args[2]) : null, 1600, 900);
            return;
        }
        if (args.length >= 2 && args[0].equals("--mengen")) {
            mengen(new File(args[1]), args.length >= 3 ? new File(args[2]) : null, 1600, 900);
            return;
        }
        if (args.length >= 2 && args[0].equals("--bewegung")) {
            bewegung(new File(args[1]), args.length >= 3 ? new File(args[2]) : null, 1600, 900);
            return;
        }
        if (args.length >= 2 && args[0].equals("--kleidung")) {
            kleidung(new File(args[1]), 1600, 900);
            return;
        }
        if (args.length >= 2 && args[0].equals("--portrait")) {
            portrait(new File(args[1]), args.length >= 3 ? new File(args[2]) : null, 1600, 900);
            return;
        }
        if (args.length >= 2 && args[0].equals("--umwelt")) {
            umwelt(new File(args[1]), 1600, 900);
            return;
        }
        if (args.length >= 2 && args[0].equals("--leben")) {
            leben(new File(args[1]), args.length >= 3 ? new File(args[2]) : null, 1600, 900);
            return;
        }
        if (args.length >= 2 && args[0].equals("--haare")) {
            haare(new File(args[1]), 1600, 900);
            return;
        }
        if (args.length >= 2 && args[0].equals("--probe")) {
            probe(new File(args[1]), java.util.Arrays.copyOfRange(args, 2, args.length));
            return;
        }
        if (args.length >= 2 && args[0].equals("--haaredev")) {
            haareDev(new File(args[1]));
            return;
        }
        if (args.length >= 2 && args[0].equals("--augen")) {
            augen(new File(args[1]), 1600, 900);
            return;
        }
        if (args.length >= 2 && args[0].equals("--hautdev")) {
            hautDev(new File(args[1]));
            return;
        }
        if (args.length >= 2 && args[0].equals("--haut")) {
            haut(new File(args[1]), 1600, 900);
            return;
        }
        if (args.length >= 2 && args[0].equals("--bild")) {
            int w = args.length >= 4 ? Integer.parseInt(args[2]) : 1600;
            int h = args.length >= 4 ? Integer.parseInt(args[3]) : 900;
            bild(new File(args[1]), w, h);
            return;
        }
        SwingUtilities.invokeLater(AvatarStudio::open);
    }

    /** Zeilen zum Grundkörper für HUD und Statusbild, aus .glb und Protokoll der Blender-Kette. */
    public static List<String> summary() {
        List<String> out = new ArrayList<>();
        try {
            byte[] glb = GlbInfo.resource(AvatarStudio.class, GLB);
            if (glb == null) {
                out.add("! Grundkörper fehlt: tools/blender/avatar_build.py ausführen");
                return out;
            }
            GlbInfo g = GlbInfo.read(glb);
            out.add(String.format(Locale.GERMANY, "Grundkörper avatar_base.glb · %.1f MB · glTF %d", g.bytes / 1048576.0, g.version));
            out.add(String.format(Locale.GERMANY, "%,d Ecken · %,d Dreiecke · %d Knochen · %d Morphziele",
                    g.vertices, g.triangles, g.joints, g.morphTargets));
            out.add("SHA-256 " + g.sha256.substring(0, 16) + "…");
            byte[] pj = GlbInfo.resource(AvatarStudio.class, PROTOKOLL);
            if (pj != null) {
                Object p = Json.parse(new String(pj, StandardCharsets.UTF_8));
                out.add("Blender " + Json.str(p, "blender") + " · MPFB " + Json.str(p, "mpfb"));
                Object pr = Json.obj(p, "pruefung");
                if (pr != null) out.add("Zweiter Lauf: " + (Boolean.TRUE.equals(((java.util.Map<?, ?>) pr).get("gleich")) ? "gleiche Prüfsumme" : "! ABWEICHUNG"));
                List<Object> fehlt = Json.arr(p, "fehlt");
                if (fehlt != null && !fehlt.isEmpty()) out.add("fehlt: " + fehlt.size() + " Pakete/Teile (siehe Protokoll)");
            }
        } catch (Exception e) {
            out.add("! " + e.getMessage());
        }
        return out;
    }

    // ------------------------------------------------------------ Fenster

    private static void open() {
        StudioScene scene = new StudioScene();
        scene.hud.body = summary();
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(scene);
        MouseAdapter m = new MouseAdapter() {
            int lx, ly;
            @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); canvas.requestFocusInWindow(); }
            @Override public void mouseDragged(MouseEvent e) {
                scene.cam.orbit(e.getX() - lx, e.getY() - ly);
                lx = e.getX(); ly = e.getY();
            }
            @Override public void mouseWheelMoved(MouseWheelEvent e) { scene.cam.zoom(e.getPreciseWheelRotation()); }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) scene.cam.reset();
            }
        };
        canvas.addMouseListener(m);
        canvas.addMouseMotionListener(m);
        canvas.addMouseWheelListener(m);
        // WASD fliegen, Q/E tiefer/höher, Umschalt schneller
        canvas.setFocusable(true);
        canvas.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyPressed(java.awt.event.KeyEvent e) {
                if (e.getKeyCode() == java.awt.event.KeyEvent.VK_ESCAPE && scene.kino.laeuft()) { scene.stopFilm(); return; }
                scene.keys.add(e.getKeyCode());
            }
            @Override public void keyReleased(java.awt.event.KeyEvent e) { scene.keys.remove(e.getKeyCode()); }
        });
        canvas.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusLost(java.awt.event.FocusEvent e) { scene.keys.clear(); }
        });

        JPanel side = new TrackWidthPanel();
        side.setBackground(PANEL);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JLabel title = new JLabel("Avatar Studio");
        title.setForeground(INK);
        title.setFont(new Font(Font.SERIF, Font.PLAIN, 22));
        side.add(left(title));
        scene.cam.reset();
        JLabel sub = label(STAND, MUTED);
        side.add(left(sub));
        side.add(Box.createVerticalStrut(14));

        // PERSON (ab Phase 2)
        side.add(block("PERSON"));
        JLabel who = label("Grundkörper wird geladen …", INK);
        side.add(left(who));
        Person[] cur = {Person.wuerfeln(4711)};
        StudioScene.Figure[] fig = {null};
        boolean[] quiet = {false};
        String[][] defs = {{"Geschlecht", "gender"}, {"Alter", "age"}, {"Muskeln", "muscle"}, {"Gewicht", "weight"},
                           {"Größe", "height"}, {"Proportionen", "proportions"}, {"Brust", "cupsize"}, {"Festigkeit", "firmness"}};
        FSlider[] sl = new FSlider[defs.length];
        JLabel[] val = new JLabel[defs.length];
        Runnable[] skinShow = {null};
        Runnable[] eyeShow = {null};
        Runnable[] hairShow = {null};
        Runnable showValues = () -> {
            Person p = cur[0];
            double[] v = {p.gender, p.age, p.muscle, p.weight, p.height, p.proportions, p.cupsize, p.firmness};
            quiet[0] = true;
            for (int i = 0; i < defs.length; i++) {
                sl[i].setValue((int) Math.round(v[i] * 1000));
                val[i].setText(i == 1 ? String.format(Locale.GERMANY, "%.0f J.", p.ageYears()) : String.format(Locale.GERMANY, "%.2f", v[i]));
            }
            quiet[0] = false;
            who.setText(p.name.isEmpty() ? "eigene Einstellung" : p.name);
            if (skinShow[0] != null) skinShow[0].run();
            if (eyeShow[0] != null) eyeShow[0].run();
            if (hairShow[0] != null) hairShow[0].run();
        };
        for (int i = 0; i < defs.length; i++) {
            final int k = i;
            sl[i] = new FSlider(0, 1000, 500);
            val[i] = label("", MUTED);
            val[i].setPreferredSize(new Dimension(52, 20));
            sl[i].addChangeListener(e -> {
                if (quiet[0]) return;
                Person p = cur[0].copy();
                double v = sl[k].getValue() / 1000.0;
                switch (defs[k][1]) {
                    case "gender" -> p.gender = v; case "age" -> p.age = v; case "muscle" -> p.muscle = v;
                    case "weight" -> p.weight = v; case "height" -> p.height = v; case "proportions" -> p.proportions = v;
                    case "cupsize" -> p.cupsize = v; default -> p.firmness = v;
                }
                p.name = "";
                cur[0] = p;
                val[k].setText(k == 1 ? String.format(Locale.GERMANY, "%.0f J.", p.ageYears()) : String.format(Locale.GERMANY, "%.2f", v));
                who.setText("eigene Einstellung · seed " + p.seed);
                if (fig[0] != null) fig[0].set(p);
            });
            JPanel r = new JPanel(new BorderLayout(8, 0));
            r.setOpaque(false);
            JLabel n = label(defs[i][0], MUTED);
            n.setPreferredSize(new Dimension(86, 20));
            r.add(n, BorderLayout.WEST);
            r.add(sl[i], BorderLayout.CENTER);
            r.add(val[i], BorderLayout.EAST);
            r.setAlignmentX(Component.LEFT_ALIGNMENT);
            r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
            side.add(r);
        }
        FButton roll = new FButton("Würfeln");
        FButton mid = new FButton("Mitte");
        roll.addActionListener(e -> {
            cur[0] = Person.wuerfeln(cur[0].seed + 1);
            showValues.run();
            if (fig[0] != null) fig[0].set(cur[0]);
        });
        mid.addActionListener(e -> {
            Person p = new Person();
            p.seed = cur[0].seed;
            p.name = "Mitte aller Regler";
            cur[0] = p;
            showValues.run();
            if (fig[0] != null) fig[0].set(p);
        });
        JPanel bt = new JPanel(new java.awt.GridLayout(1, 2, 8, 0));
        bt.setOpaque(false);
        bt.add(roll);
        bt.add(mid);
        bt.setAlignmentX(Component.LEFT_ALIGNMENT);
        bt.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        bt.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
        side.add(bt);
        showValues.run();
        side.add(Box.createVerticalStrut(12));

        // POSE (ab Phase 3)
        side.add(block("POSE"));
        FComboBox poseSel = new FComboBox(new Object[]{"Ruhe", "stehen", "sitzen", "lehnen", "hand_ans_gesicht", "unterarm_drehen"});
        poseSel.setSelectedIndex(1);
        poseSel.addActionListener(e -> {
            if (fig[0] != null) fig[0].setPose(poseSel.getSelectedIndex() == 0 ? null : String.valueOf(poseSel.getSelectedItem()));
        });
        side.add(row("Pose", poseSel));
        FComboBox skinSel = new FComboBox(new Object[]{"Dual-Quaternion", "linear"});
        skinSel.addActionListener(e -> { if (fig[0] != null) fig[0].setDualQuaternion(skinSel.getSelectedIndex() == 0); });
        side.add(row("Skinning", skinSel));
        side.add(Box.createVerticalStrut(12));

        // GESICHT (ab Phase 3)
        side.add(block("GESICHT"));
        FComboBox faceSel = new FComboBox(Person.FACE);
        FSlider faceVal = new FSlider(-100, 100, 0);
        JLabel faceL = label("0,00", MUTED);
        boolean[] faceQuiet = {false};
        faceSel.addActionListener(e -> {
            faceQuiet[0] = true;
            faceVal.setValue((int) Math.round(cur[0].face.getOrDefault(String.valueOf(faceSel.getSelectedItem()), 0.0) * 100));
            faceL.setText(String.format(Locale.GERMANY, "%.2f", faceVal.getValue() / 100.0));
            faceQuiet[0] = false;
        });
        faceVal.addChangeListener(e -> {
            if (faceQuiet[0]) return;
            Person p = cur[0].copy();
            double v = faceVal.getValue() / 100.0;
            if (v == 0) p.face.remove(String.valueOf(faceSel.getSelectedItem())); else p.face.put(String.valueOf(faceSel.getSelectedItem()), v);
            cur[0] = p;
            faceL.setText(String.format(Locale.GERMANY, "%.2f", v));
            if (fig[0] != null) fig[0].set(p);
        });
        side.add(row("Regler", faceSel));
        side.add(sliderRow("Wert", faceVal, faceL));
        java.util.List<Object> forms = new java.util.ArrayList<>();
        for (String f : ARKIT) forms.add("arkit." + f);
        for (String v : VISEME) forms.add("visem." + v);
        FComboBox formSel = new FComboBox(forms.toArray());
        FSlider formVal = new FSlider(0, 100, 0);
        JLabel formL = label("0,00", MUTED);
        boolean[] formQuiet = {false};
        formSel.addActionListener(e -> {
            formQuiet[0] = true;
            formVal.setValue((int) Math.round(cur[0].forms.getOrDefault(String.valueOf(formSel.getSelectedItem()), 0.0) * 100));
            formL.setText(String.format(Locale.GERMANY, "%.2f", formVal.getValue() / 100.0));
            formQuiet[0] = false;
        });
        formVal.addChangeListener(e -> {
            if (formQuiet[0]) return;
            Person p = cur[0].copy();
            double v = formVal.getValue() / 100.0;
            if (v == 0) p.forms.remove(String.valueOf(formSel.getSelectedItem())); else p.forms.put(String.valueOf(formSel.getSelectedItem()), v);
            cur[0] = p;
            formL.setText(String.format(Locale.GERMANY, "%.2f", v));
            if (fig[0] != null) fig[0].set(p);
        });
        side.add(row("Form", formSel));
        side.add(sliderRow("Stärke", formVal, formL));
        FButton faceReset = new FButton("Gesicht zurücksetzen");
        faceReset.addActionListener(e -> {
            Person p = cur[0].copy();
            p.face.clear(); p.forms.clear();
            cur[0] = p;
            faceQuiet[0] = formQuiet[0] = true;
            faceVal.setValue(0); formVal.setValue(0);
            faceL.setText("0,00"); formL.setText("0,00");
            faceQuiet[0] = formQuiet[0] = false;
            if (fig[0] != null) fig[0].set(p);
        });
        JPanel fr = new JPanel(new BorderLayout());
        fr.setOpaque(false);
        fr.add(faceReset, BorderLayout.CENTER);
        fr.setAlignmentX(Component.LEFT_ALIGNMENT);
        fr.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        fr.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
        side.add(fr);
        side.add(Box.createVerticalStrut(12));

        // HAUT (ab Phase 4)
        side.add(block("HAUT · LICHT"));
        String[][] sdefs = {{"Hautton", "melanin"}, {"Rötung", "redness"}, {"Sommerspr.", "freckles"},
                            {"Bartschatten", "beard"}, {"Poren", "pores"}, {"Glanz", "oil"}};
        FSlider[] ss = new FSlider[sdefs.length];
        JLabel[] sv = new JLabel[sdefs.length];
        boolean[] sq = {false};
        skinShow[0] = () -> {
            Person p = cur[0];
            double[] v = {p.melanin, p.redness, p.freckles, p.beard, p.pores, p.oil};
            sq[0] = true;
            for (int i = 0; i < sdefs.length; i++) {
                ss[i].setValue((int) Math.round(v[i] * 1000));
                sv[i].setText(String.format(Locale.GERMANY, "%.2f", v[i]));
            }
            sq[0] = false;
        };
        for (int i = 0; i < sdefs.length; i++) {
            final int k = i;
            ss[i] = new FSlider(0, 1000, 500);
            sv[i] = label("", MUTED);
            ss[i].addChangeListener(e -> {
                if (sq[0]) return;
                Person p = cur[0].copy();
                double v = ss[k].getValue() / 1000.0;
                switch (sdefs[k][1]) {
                    case "melanin" -> p.melanin = v; case "redness" -> p.redness = v; case "freckles" -> p.freckles = v;
                    case "beard" -> p.beard = v; case "pores" -> p.pores = v; default -> p.oil = v;
                }
                cur[0] = p;
                sv[k].setText(String.format(Locale.GERMANY, "%.2f", v));
                if (fig[0] != null) fig[0].set(p);
            });
            side.add(sliderRow(sdefs[i][0], ss[i], sv[i]));
        }
        skinShow[0].run();
        FComboBox lightSel = new FComboBox(LightRig.NAMES);
        lightSel.addActionListener(e -> {
            String n = String.valueOf(lightSel.getSelectedItem());
            double[] head = {0, 1.58, 0.02};
            StudioScene.Figure f0 = fig[0];
            if (f0 != null && f0.jointWorld != null) {
                double[] hp = com.dan.avatar.Skeleton.position(f0.jointWorld, f0.skeleton.joint("head"));
                head = new double[]{f0.x + hp[0], hp[1] + f0.baseY() + 0.08, f0.z + hp[2]};
            }
            LightRig.apply(scene.light, n, head);
            if (!n.equals("Bühne")) {
                scene.cam.tx = head[0]; scene.cam.ty = head[1] - 0.02; scene.cam.tz = head[2];
                scene.cam.dist = 0.9; scene.cam.fovyDeg = 28; scene.cam.pitchDeg = 2;
                scene.cam.yawDeg = n.equals("Gegenlicht") ? 38 : 18;
            } else scene.cam.reset();
        });
        side.add(row("Licht", lightSel));
        FComboBox shadowSel = new FComboBox(new Object[]{"Schatten an", "Schatten aus"});
        shadowSel.addActionListener(e -> scene.shadows = shadowSel.getSelectedIndex() == 0);
        side.add(row("Schatten", shadowSel));
        FSlider trans = new FSlider(0, 600, 300);
        JLabel transL = label("3,00", MUTED);
        trans.addChangeListener(e -> {
            scene.transStrength = trans.getValue() / 100f;
            transL.setText(String.format(Locale.GERMANY, "%.2f", trans.getValue() / 100.0));
        });
        side.add(sliderRow("Durchleucht.", trans, transL));
        side.add(Box.createVerticalStrut(12));

        // AUGEN (ab Phase 5)
        side.add(block("AUGEN · MUND"));
        FSlider irisS = new FSlider(0, 1000, 600);
        JLabel irisL = label("0,60", MUTED);
        boolean[] iq = {false};
        eyeShow[0] = () -> {
            iq[0] = true;
            irisS.setValue((int) Math.round(cur[0].iris * 1000));
            irisL.setText(String.format(Locale.GERMANY, "%.2f", cur[0].iris));
            iq[0] = false;
        };
        irisS.addChangeListener(e -> {
            if (iq[0]) return;
            Person p = cur[0].copy();
            p.iris = irisS.getValue() / 1000.0;
            cur[0] = p;
            irisL.setText(String.format(Locale.GERMANY, "%.2f", p.iris));
            if (fig[0] != null) fig[0].set(p);
        });
        side.add(sliderRow("Iris", irisS, irisL));
        eyeShow[0].run();
        FSlider gainS = new FSlider(-200, 30, 0);
        JLabel gainL = label("100 %", MUTED);
        gainS.addChangeListener(e -> {
            double gn = Math.pow(10, gainS.getValue() / 100.0);
            scene.light.gain = (float) gn;
            gainL.setText(gn >= 0.1 ? String.format(Locale.GERMANY, "%.0f %%", gn * 100) : String.format(Locale.GERMANY, "%.1f %%", gn * 100));
        });
        side.add(sliderRow("Helligkeit", gainS, gainL));
        JLabel pupilL = label("Pupille –", MUTED);
        side.add(left(pupilL));
        new javax.swing.Timer(250, e -> {
            StudioScene.Figure f0 = fig[0];
            if (f0 != null) pupilL.setText(String.format(Locale.GERMANY, "Pupille %.1f mm (Ziel %.1f mm) · Mund offen %.0f %%",
                    f0.pupilMm, f0.pupilTargetMm, com.dan.avatar.gl.AvatarGl.mouthOpening(f0.person) * 100));
        }).start();
        side.add(Box.createVerticalStrut(12));

        // HAARE (ab Phase 6)
        side.add(block("HAARE"));
        FComboBox styleSel = new FComboBox(HairRig.STYLE_NAMES);
        String[][] hdefs = {{"Farbe", "hairColor"}, {"Rot", "hairRed"}, {"Grau", "grey"}, {"Bartlänge", "beardLength"}};
        FSlider[] hs = new FSlider[hdefs.length];
        JLabel[] hv = new JLabel[hdefs.length];
        boolean[] hq = {false};
        hairShow[0] = () -> {
            Person p = cur[0];
            double[] v = {p.hairColor, p.hairRed, p.grey, p.beardLength};
            hq[0] = true;
            styleSel.setSelectedIndex(Math.max(0, java.util.Arrays.asList(Person.HAIR_STYLES).indexOf(p.hair)));
            for (int i = 0; i < hdefs.length; i++) {
                hs[i].setValue((int) Math.round(v[i] * 1000));
                hv[i].setText(String.format(Locale.GERMANY, "%.2f", v[i]));
            }
            hq[0] = false;
        };
        styleSel.addActionListener(e -> {
            if (hq[0]) return;
            Person p = cur[0].copy();
            p.hair = Person.HAIR_STYLES[styleSel.getSelectedIndex()];
            cur[0] = p;
            if (fig[0] != null) fig[0].set(p);
        });
        side.add(row("Frisur", styleSel));
        for (int i = 0; i < hdefs.length; i++) {
            final int k = i;
            hs[i] = new FSlider(0, 1000, 500);
            hv[i] = label("", MUTED);
            hs[i].addChangeListener(e -> {
                if (hq[0]) return;
                Person p = cur[0].copy();
                double v = hs[k].getValue() / 1000.0;
                switch (hdefs[k][1]) {
                    case "hairColor" -> p.hairColor = v; case "hairRed" -> p.hairRed = v;
                    case "grey" -> p.grey = v; default -> p.beardLength = v;
                }
                cur[0] = p;
                hv[k].setText(String.format(Locale.GERMANY, "%.2f", v));
                if (fig[0] != null) fig[0].set(p);
            });
            side.add(sliderRow(hdefs[i][0], hs[i], hv[i]));
        }
        hairShow[0].run();
        FComboBox strandSel = new FComboBox(new Object[]{"einzelne Haare an", "einzelne Haare aus"});
        strandSel.addActionListener(e -> scene.strands = strandSel.getSelectedIndex() == 0);
        side.add(row("Strähnen", strandSel));
        JLabel hairL = label("Haare –", MUTED);
        side.add(left(hairL));
        new javax.swing.Timer(500, e -> {
            StudioScene.Figure f0 = fig[0];
            if (f0 != null) hairL.setText(f0.hairNote() + String.format(Locale.GERMANY, " · %.2f ms", scene.msHair));
        }).start();
        side.add(Box.createVerticalStrut(12));

        // GEFÜHL · BLICK · LEBEN (ab Phase 7)
        side.add(block("GEFÜHL · BLICK · LEBEN"));
        FeelingPad pad = new FeelingPad();
        FComboBox feelSel = new FComboBox(com.dan.avatar.Emotion.names());
        FSlider feelS = new FSlider(0, 100, 100);
        JLabel feelL = label("1,00", MUTED);
        boolean[] fq = {false};
        java.util.function.Supplier<com.dan.avatar.Behavior> beh = () -> fig[0] == null ? null : fig[0].behavior;
        Runnable feelApply = () -> {
            com.dan.avatar.Behavior b = beh.get();
            if (b == null) return;
            com.dan.avatar.Emotion.Def d = com.dan.avatar.Emotion.named(String.valueOf(feelSel.getSelectedItem()));
            if (d != null) { b.feel(d.name, feelS.getValue() / 100.0); pad.set(d.valence, d.arousal); }
        };
        feelSel.addActionListener(e -> { if (!fq[0]) feelApply.run(); });
        feelS.addChangeListener(e -> {
            feelL.setText(String.format(Locale.GERMANY, "%.2f", feelS.getValue() / 100.0));
            com.dan.avatar.Behavior b = beh.get();
            if (b != null) b.feelAt(b.valence(), b.arousal(), feelS.getValue() / 100.0);
        });
        pad.onChange((v, a) -> {
            com.dan.avatar.Behavior b = beh.get();
            if (b == null) return;
            b.feelAt(v, a, feelS.getValue() / 100.0);
            fq[0] = true;
            feelSel.setSelectedItem(b.feelingName());
            fq[0] = false;
        });
        side.add(left(pad));
        side.add(Box.createVerticalStrut(4));
        side.add(row("Gefühl", feelSel));
        side.add(sliderRow("Stärke", feelS, feelL));
        FComboBox lookSel = new FComboBox(new Object[]{"Kamera", "frei", "Horizont", "Punkt (Klick ins Bild)", "Maus"});
        lookSel.addActionListener(e -> {
            com.dan.avatar.Behavior b = beh.get();
            if (b == null) return;
            switch (lookSel.getSelectedIndex()) {
                case 0 -> b.look(com.dan.avatar.Behavior.Look.CAMERA);
                case 1 -> b.look(com.dan.avatar.Behavior.Look.FREE);
                case 2 -> b.look(com.dan.avatar.Behavior.Look.HORIZON);
                default -> b.lookAt(b.point());
            }
        });
        side.add(row("Blick", lookSel));
        FComboBox lifeSel = new FComboBox(new Object[]{"Leben an", "Leben aus (Standbild)", "Verhalten aus (Phase 6)"});
        lifeSel.addActionListener(e -> {
            StudioScene.Figure f0 = fig[0];
            if (f0 == null) return;
            f0.behaviorOn = lifeSel.getSelectedIndex() < 2;
            com.dan.avatar.Behavior b = f0.behavior;
            if (b != null) b.alive = lifeSel.getSelectedIndex() == 0;
        });
        side.add(row("Leben", lifeSel));
        FSlider slowS = new FSlider(5, 100, 100);
        JLabel slowL = label("100 %", MUTED);
        slowS.addChangeListener(e -> {
            slowL.setText(slowS.getValue() + " %");
            com.dan.avatar.Behavior b = beh.get();
            if (b != null) b.timeScale = slowS.getValue() / 100.0;
        });
        side.add(sliderRow("Zeitlupe", slowS, slowL));
        JLabel lifeL = label("Leben –", MUTED);
        side.add(left(lifeL));
        new javax.swing.Timer(500, e -> {
            com.dan.avatar.Behavior b = beh.get();
            if (b == null) return;
            double tb = b.time();
            int nb = 0, ns = 0;
            try {
                for (int i = b.blinks.size() - 1; i >= 0 && b.blinks.get(i) > tb - 60; i--) nb++;
                for (int i = b.saccades.size() - 1; i >= 0 && b.saccades.get(i)[0] > tb - 60; i--) ns++;
            } catch (RuntimeException ignored) { return; }   // Listen wachsen im GL-Faden
            double win = Math.min(60, Math.max(1, tb));
            lifeL.setText(String.format(Locale.GERMANY, "%.0f Lidschläge/min · %.1f Sakkaden/s · Atem %.0f/min", nb * 60 / win, ns / win, b.breathRate()));
        }).start();
        // Blickziel im Bild: Klick (Punkt) oder Maus
        MouseAdapter gazeMouse = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 1 || lookSel.getSelectedIndex() != 3) return;
                aim(e);
            }
            @Override public void mouseMoved(MouseEvent e) { if (lookSel.getSelectedIndex() == 4) aim(e); }
            private void aim(MouseEvent e) {
                StudioScene.Figure f0 = fig[0];
                if (f0 == null || f0.behavior == null || f0.jointWorld == null) return;
                double[] p = screenPoint(scene, f0, e.getX(), e.getY(), canvas.getWidth(), canvas.getHeight());
                f0.behavior.lookAt(p);
            }
        };
        canvas.addMouseListener(gazeMouse);
        canvas.addMouseMotionListener(gazeMouse);
        side.add(Box.createVerticalStrut(12));

        // UMWELT (ab Phase 8)
        side.add(block("UMWELT"));
        FComboBox envPre = new FComboBox(new Object[]{"Regennacht", "Sturm", "Wintermorgen", "Sommerabend", "eigene"});
        FSlider rainS = new FSlider(0, 1000, 0);
        JLabel rainL = label("0,0 mm/h", MUTED);
        FSlider windS = new FSlider(0, 250, 0);
        JLabel windL = label("0,0 m/s", MUTED);
        FComboBox windDir = new FComboBox(new Object[]{"von vorn", "von vorn links", "von links", "von rechts", "von hinten"});
        FSlider tempS = new FSlider(-200, 350, 180);
        JLabel tempL = label("18 °C", MUTED);
        FSlider humS = new FSlider(20, 100, 60);
        JLabel humL = label("60 %", MUTED);
        FComboBox hoodSel = new FComboBox(new Object[]{"ohne Schutz", "Schutz wie Kapuze"});
        FSlider envSp = new FSlider(0, 100, 0);
        JLabel envSpL = label("1×", MUTED);
        boolean[] eq = {false};
        // Regen logarithmisch: 0 … 60 mm/h
        java.util.function.IntToDoubleFunction rainOf = v -> v == 0 ? 0 : 0.1 * Math.pow(600, v / 1000.0);
        java.util.function.DoubleToIntFunction rainTo = r -> r <= 0.1 ? (r <= 0 ? 0 : 1) : (int) Math.round(1000 * Math.log(r / 0.1) / Math.log(600));
        double[][] dirs = {{0, 0, -1}, {-0.5, 0, -0.85}, {-1, 0, 0}, {1, 0, 0}, {0, 0, 1}};
        Runnable envSet = () -> {
            if (eq[0]) return;
            com.dan.avatar.Climate c = new com.dan.avatar.Climate();
            c.rain = rainOf.applyAsDouble(rainS.getValue());
            double v = windS.getValue() / 10.0;
            double[] d = dirs[windDir.getSelectedIndex()];
            c.wind = new double[]{d[0] * v, 0, d[2] * v};
            c.tempC = tempS.getValue() / 10.0;
            c.humidity = humS.getValue() / 100.0;
            c.hood = hoodSel.getSelectedIndex();
            scene.climate = c;
            scene.wind = new double[]{c.wind[0], 0.15 * v, c.wind[2]};
            rainL.setText(String.format(Locale.GERMANY, "%.1f mm/h", c.rain));
            windL.setText(String.format(Locale.GERMANY, "%.1f m/s", v));
            tempL.setText(String.format(Locale.GERMANY, "%.0f °C", c.tempC));
            humL.setText(String.format(Locale.GERMANY, "%.0f %%", c.humidity * 100));
        };
        java.util.function.Consumer<com.dan.avatar.Climate> envShow = c -> {
            eq[0] = true;
            rainS.setValue(rainTo.applyAsInt(c.rain));
            double v = c.windSpeed();
            windS.setValue((int) Math.round(v * 10));
            int best = 0; double bd = 9;
            for (int i = 0; i < dirs.length; i++) {
                double dd = v < 1e-6 ? 0 : Math.hypot(dirs[i][0] - c.wind[0] / v, dirs[i][2] - c.wind[2] / v);
                if (dd < bd) { bd = dd; best = i; }
            }
            windDir.setSelectedIndex(best);
            tempS.setValue((int) Math.round(c.tempC * 10));
            humS.setValue((int) Math.round(c.humidity * 100));
            hoodSel.setSelectedIndex(c.hood > 0.5 ? 1 : 0);
            eq[0] = false;
            envSet.run();
        };
        envPre.addActionListener(e -> {
            switch (envPre.getSelectedIndex()) {
                case 0 -> envShow.accept(com.dan.avatar.Climate.regennacht());
                case 1 -> envShow.accept(com.dan.avatar.Climate.sturm());
                case 2 -> envShow.accept(com.dan.avatar.Climate.wintermorgen());
                case 3 -> envShow.accept(com.dan.avatar.Climate.sommerabend());
                default -> {}
            }
        });
        for (FSlider fs : new FSlider[]{rainS, windS, tempS, humS}) fs.addChangeListener(e -> { envSet.run(); if (!eq[0]) envPre.setSelectedIndex(4); });
        windDir.addActionListener(e -> envSet.run());
        hoodSel.addActionListener(e -> envSet.run());
        envSp.addChangeListener(e -> {
            double k = Math.pow(60, envSp.getValue() / 100.0);
            scene.envSpeed = k;
            envSpL.setText(String.format(Locale.GERMANY, "%.0f×", k));
        });
        side.add(row("Preset", envPre));
        side.add(sliderRow("Regen", rainS, rainL));
        side.add(sliderRow("Wind", windS, windL));
        side.add(row("Richtung", windDir));
        side.add(sliderRow("Temperatur", tempS, tempL));
        side.add(sliderRow("Feuchte", humS, humL));
        side.add(row("Schutz", hoodSel));
        side.add(sliderRow("Zeitraffer", envSp, envSpL));
        FButton dryB = new FButton("Abtrocknen");
        FButton soakB = new FButton("Durchnässt");
        dryB.addActionListener(e -> { if (fig[0] != null) fig[0].dry(); });
        soakB.addActionListener(e -> { if (fig[0] != null) fig[0].wetness.soak(1); });
        JPanel eb = new JPanel(new java.awt.GridLayout(1, 2, 8, 0));
        eb.setOpaque(false);
        eb.add(dryB); eb.add(soakB);
        eb.setAlignmentX(Component.LEFT_ALIGNMENT);
        eb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        eb.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
        side.add(eb);
        JLabel envL = label("Nässe –", MUTED);
        side.add(left(envL));
        new javax.swing.Timer(500, e -> {
            StudioScene.Figure f0 = fig[0];
            if (f0 == null) return;
            com.dan.avatar.Wetness wt = f0.wetness;
            envL.setText(String.format(Locale.GERMANY, "Gesicht %.0f %% · Haar %.0f %% · Schultern %.0f %% · %d Perlen",
                    wt.wet(com.dan.avatar.Wetness.FACE) * 100, wt.wet(com.dan.avatar.Wetness.HAIR) * 100, wt.wet(com.dan.avatar.Wetness.SHOULDERS) * 100,
                    f0.drops == null ? 0 : f0.drops.beads().size()));
        }).start();
        envPre.setSelectedIndex(4);
        envSet.run();
        side.add(Box.createVerticalStrut(12));

        // KLEIDUNG (ab Phase 10)
        side.add(block("KLEIDUNG"));
        FComboBox outSel = new FComboBox(com.dan.avatar.Outfit.PRESETS);
        outSel.setSelectedItem("Alltag");
        FComboBox monthSel = new FComboBox(new Object[]{"Januar", "Februar", "März", "April", "Mai", "Juni", "Juli", "August", "September", "Oktober", "November", "Dezember"});
        monthSel.setSelectedIndex(java.time.LocalDate.now().getMonthValue() - 1);
        FComboBox roleSel = new FComboBox(new Object[]{"Alltag", "draußen", "Arbeit"});
        roleSel.setSelectedIndex(1);
        FComboBox epochSel = new FComboBox(new Object[]{"heute", "1362", "Wikingerzeit"});
        FComboBox pieceSel = new FComboBox(new Object[]{"–"});
        FComboBox fabSel = new FComboBox(com.dan.avatar.Fabric.values());
        String[] colNames = {"wie Stoff", "Schwarz", "Anthrazit", "Marine", "Ölzeug-Gelb", "Rot", "Waldgrün", "Naturweiß", "Braun", "Waid-Blau"};
        double[][] colVals = {null, {0.025, 0.025, 0.028}, {0.06, 0.065, 0.07}, {0.02, 0.04, 0.09}, {0.75, 0.55, 0.04}, {0.45, 0.04, 0.03},
            {0.04, 0.12, 0.05}, {0.62, 0.58, 0.48}, {0.18, 0.10, 0.05}, {0.16, 0.22, 0.30}};
        FComboBox colSel = new FComboBox(colNames);
        FComboBox hoodUpSel = new FComboBox(new Object[]{"Kapuze auf", "Kapuze ab"});
        JLabel outL = label("–", MUTED);
        boolean[] oq = {false};
        Runnable outShow = () -> {
            StudioScene.Figure f0 = fig[0];
            if (f0 == null) return;
            com.dan.avatar.Outfit o = f0.outfit;
            oq[0] = true;
            pieceSel.removeAllItems();
            for (com.dan.avatar.Outfit.Piece pc : o.pieces) pieceSel.addItem(pc.mesh);
            if (o.pieces.isEmpty()) pieceSel.addItem("–");
            boolean hasHood = false;
            for (com.dan.avatar.Outfit.Piece pc : o.pieces) hasHood |= pc.mesh.equals(com.dan.avatar.Outfit.HOOD_UP) || pc.mesh.equals(com.dan.avatar.Outfit.HOOD_DOWN);
            hoodUpSel.setEnabled(hasHood);
            hoodUpSel.setSelectedIndex(o.hoodUp() || !hasHood ? 0 : 1);
            oq[0] = false;
            outL.setText(o.name + " · " + o.pieces.size() + " Teile");
        };
        java.util.function.Consumer<com.dan.avatar.Outfit> wear = o -> { if (fig[0] != null) { fig[0].outfit = o; outShow.run(); } };
        boolean[] outQuiet = {false};
        outSel.addActionListener(e -> { if (!outQuiet[0]) wear.accept(com.dan.avatar.Outfit.preset((String) outSel.getSelectedItem())); });
        FButton monthB = new FButton("Nach Monat, Rolle, Epoche");
        monthB.addActionListener(e -> {
            com.dan.avatar.Outfit o = com.dan.avatar.Outfit.forMonth(monthSel.getSelectedIndex() + 1,
                    com.dan.avatar.Outfit.Role.values()[roleSel.getSelectedIndex()], com.dan.avatar.Outfit.Epoch.values()[epochSel.getSelectedIndex()],
                    fig[0] == null ? 1 : fig[0].person.seed);
            outQuiet[0] = true;
            outSel.setSelectedItem(o.name);          // Auswahl zeigt, was die Person jetzt trägt
            outQuiet[0] = false;
            wear.accept(o);
        });
        pieceSel.addActionListener(e -> {
            if (oq[0] || fig[0] == null) return;
            com.dan.avatar.Outfit.Piece pc = fig[0].outfit.piece((String) pieceSel.getSelectedItem());
            if (pc == null || pc.fabric == null) return;
            oq[0] = true;
            fabSel.setSelectedItem(pc.fabric);
            colSel.setSelectedIndex(0);
            oq[0] = false;
        });
        Runnable restyle = () -> {
            if (oq[0] || fig[0] == null) return;
            com.dan.avatar.Outfit o = fig[0].outfit;
            String key = (String) pieceSel.getSelectedItem();
            com.dan.avatar.Outfit n = new com.dan.avatar.Outfit(o.name + "*");
            for (com.dan.avatar.Outfit.Piece pc : o.pieces)
                if (pc.mesh.equals(key)) n.add(pc.mesh, (com.dan.avatar.Fabric) fabSel.getSelectedItem(), colSel.getSelectedIndex() == 0 ? pc.color : colVals[colSel.getSelectedIndex()]);
                else n.add(pc.mesh, pc.fabric, pc.color);
            fig[0].outfit = n;
            outL.setText(n.name + " · " + n.pieces.size() + " Teile");
        };
        fabSel.addActionListener(e -> restyle.run());
        colSel.addActionListener(e -> restyle.run());
        hoodUpSel.addActionListener(e -> {
            if (oq[0] || fig[0] == null) return;
            com.dan.avatar.Outfit o = fig[0].outfit;
            boolean up = hoodUpSel.getSelectedIndex() == 0;
            com.dan.avatar.Outfit n = new com.dan.avatar.Outfit(o.name);
            for (com.dan.avatar.Outfit.Piece pc : o.pieces) {
                boolean hood = pc.mesh.equals(com.dan.avatar.Outfit.HOOD_UP) || pc.mesh.equals(com.dan.avatar.Outfit.HOOD_DOWN);
                n.add(hood ? (up ? com.dan.avatar.Outfit.HOOD_UP : com.dan.avatar.Outfit.HOOD_DOWN) : pc.mesh, pc.fabric, pc.color);
            }
            wear.accept(n);
        });
        side.add(row("Outfit", outSel));
        side.add(row("Monat", monthSel));
        side.add(row("Rolle", roleSel));
        side.add(row("Epoche", epochSel));
        JPanel mb = new JPanel(new java.awt.GridLayout(1, 1));
        mb.setOpaque(false);
        mb.add(monthB);
        mb.setAlignmentX(Component.LEFT_ALIGNMENT);
        mb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        mb.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
        side.add(mb);
        side.add(row("Teil", pieceSel));
        side.add(row("Stoff", fabSel));
        side.add(row("Farbe", colSel));
        side.add(row("Kapuze", hoodUpSel));
        side.add(left(outL));
        new javax.swing.Timer(700, e -> { if (fig[0] != null && pieceSel.getItemCount() <= 1 && "–".equals(pieceSel.getItemAt(0))) outShow.run(); }).start();
        side.add(Box.createVerticalStrut(12));

        // BEWEGUNG (ab Phase 11)
        side.add(block("BEWEGUNG"));
        JLabel moL = label("steht", MUTED);
        java.util.function.Supplier<com.dan.avatar.Motion> mot = () -> {
            StudioScene.Figure f0 = fig[0];
            if (f0 == null) return null;
            if (f0.motion == null) f0.motion = new com.dan.avatar.Motion(com.dan.avatar.Motion.line(f0.x, f0.z, 0), 0);
            return f0.motion;
        };
        String[][] moves = {{"Im Kreis gehen", "Anhalten"}, {"Links drehen", "Rechts drehen"}, {"Setzen", "Aufstehen"}, {"Winken", "Zeigen"}, {"Bücken", "Zurück zur Mitte"}};
        for (String[] row2 : moves) {
            JPanel bp = new JPanel(new java.awt.GridLayout(1, 2, 8, 0));
            bp.setOpaque(false);
            for (String name : row2) {
                FButton b = new FButton(name);
                b.addActionListener(e -> {
                    StudioScene.Figure f0 = fig[0];
                    if (f0 == null) return;
                    switch (name) {
                        case "Im Kreis gehen" -> {
                            com.dan.avatar.Motion mv = new com.dan.avatar.Motion(com.dan.avatar.Motion.circle(0, 0, 1.45, true), 0);
                            mv.then(com.dan.avatar.Motion.walk(10000));
                            f0.motion = mv;
                        }
                        case "Anhalten" -> { if (f0.motion != null) f0.motion.interrupt(); }
                        case "Links drehen" -> mot.get().then(com.dan.avatar.Motion.turn(Math.PI / 2));
                        case "Rechts drehen" -> mot.get().then(com.dan.avatar.Motion.turn(-Math.PI / 2));
                        case "Setzen" -> {
                            com.dan.avatar.Motion mv = mot.get();
                            mv.interrupt();
                            mv.placeBench(mv.x, mv.z, mv.yaw, f0.seatBack, f0.seatH);
                            mv.then(com.dan.avatar.Motion.sit(1e6));
                        }
                        case "Aufstehen" -> { com.dan.avatar.Motion mv = mot.get(); mv.interrupt(); mv.then(com.dan.avatar.Motion.standUp()); }
                        case "Winken" -> mot.get().then(com.dan.avatar.Motion.wave(3));
                        case "Zeigen" -> mot.get().then(com.dan.avatar.Motion.point(3));
                        case "Bücken" -> mot.get().then(com.dan.avatar.Motion.bend(2.5));
                        default -> { f0.motion = null; f0.x = 0; f0.z = 0; }
                    }
                });
                bp.add(b);
            }
            bp.setAlignmentX(Component.LEFT_ALIGNMENT);
            bp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            bp.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
            side.add(bp);
        }
        side.add(left(moL));
        // Kamera für Menschen (Phase 13): mitgehen, Einstellungen, die der Person folgen
        FComboBox camSel = new FComboBox((Object[]) CameraRig.NAMES);
        camSel.addActionListener(e -> {
            String n = (String) camSel.getSelectedItem();
            scene.rig.set(n, fig[0], null, true);
            if (n.equals("frei")) scene.cam.reset();
        });
        side.add(row("Kamera", camSel));
        new javax.swing.Timer(300, e -> {
            StudioScene.Figure f0 = fig[0];
            com.dan.avatar.Motion mv = f0 == null ? null : f0.motion;
            moL.setText(mv == null ? "steht in der Pose" : mv.toString());
        }).start();
        side.add(Box.createVerticalStrut(12));

        // MENGE (ab Phase 12)
        side.add(block("MENGE"));
        FComboBox crowdSel = new FComboBox(new Object[]{"keine Gruppe", "Gruppe von 40", "Gruppe von 100"});
        FComboBox tintSel = new FComboBox(new Object[]{"Stufen normal", "Stufen farbig"});
        JLabel crowdL = label("–", MUTED);
        crowdSel.addActionListener(e -> {
            int n = crowdSel.getSelectedIndex() == 0 ? 0 : (crowdSel.getSelectedIndex() == 1 ? 40 : 100);
            if (n == 0) { scene.crowd = null; if (fig[0] != null) fig[0].visible = true; return; }
            Thread th = new Thread(() -> {
                try {
                    Gltf model = loadModel();
                    synchronized (scene.pool) {
                        while (scene.pool.size() < 2) {
                            StudioScene.Figure pf = new StudioScene.Figure(new Body(model), loadSkeleton(model), Person.wuerfeln(5 + scene.pool.size()), "stehen", 0, 0);
                            pf.visible = false;
                            scene.figures.add(pf);
                            scene.pool.add(pf);
                        }
                    }
                    if (fig[0] != null) fig[0].visible = false;
                    scene.crowd = com.dan.avatar.Crowd.group(n, com.dan.avatar.Motion.line(-6, 0, Math.PI / 2), 0, 400, java.time.LocalDate.now().getMonthValue(), 2026);
                } catch (Exception ex) { SwingUtilities.invokeLater(() -> crowdL.setText("Fehler: " + ex.getMessage())); }
            }, "Gruppe anlegen");
            th.setDaemon(true);
            th.start();
        });
        tintSel.addActionListener(e -> scene.lodTint = tintSel.getSelectedIndex() == 1);
        side.add(row("Gruppe", crowdSel));
        side.add(row("Stufen", tintSel));
        side.add(left(crowdL));
        new javax.swing.Timer(400, e -> {
            if (scene.crowd == null) { crowdL.setText("Detailstufen L0 bis L3 · zwei Personen voll (F20)"); return; }
            crowdL.setText(String.format(Locale.GERMANY, "L0 %d · L1 %d · L2 %d · L3 %d · gebacken %d · %.2f ms", scene.lodCount[0], scene.lodCount[1], scene.lodCount[2], scene.lodCount[3], scene.crowdBaked(), scene.msCrowd));
        }).start();
        side.add(Box.createVerticalStrut(12));

        // REGIE (ab Phase 13): Szene, Regiebuch im Kino
        side.add(block("REGIE"));
        FComboBox setSel = new FComboBox(new Object[]{"Bühne", "Steg im Regen"});
        FComboBox bookSel = new FComboBox(new Object[]{"Am Steg"});
        JLabel regL = label("–", MUTED);
        setSel.addActionListener(e -> {
            boolean steg = setSel.getSelectedIndex() == 1;
            scene.set = steg ? new JettySet() : null;
            scene.rig.set(steg ? "Totale" : "frei", fig[0], null, null);
            if (!steg) scene.cam.reset();
        });
        FButton play = new FButton("Im Kino abspielen");
        StudioStage[] stg = new StudioStage[1];
        boolean[] figWas = {true};
        javax.swing.JScrollPane[] spRef = new javax.swing.JScrollPane[1];
        play.addActionListener(e -> {
            if (scene.regie != null) return;
            Thread th = new Thread(() -> {
                try {
                    Gltf model = loadModel();
                    com.dan.avatar.Regie r = com.dan.avatar.Regie.resource(AvatarStudio.class, "regie/am_steg.txt");
                    StudioStage st = new StudioStage(scene, model);
                    stg[0] = st;
                    if (fig[0] != null) { figWas[0] = fig[0].visible; fig[0].visible = false; }
                    scene.crowd = null;
                    scene.startFilm(r, st);
                    SwingUtilities.invokeLater(() -> { if (spRef[0] != null) spRef[0].setVisible(false); canvas.requestFocusInWindow(); });
                } catch (Exception ex) { SwingUtilities.invokeLater(() -> regL.setText("Fehler: " + ex.getMessage())); }
            }, "Regiebuch");
            th.setDaemon(true);
            th.start();
        });
        side.add(row("Szene", setSel));
        side.add(row("Regiebuch", bookSel));
        JPanel pp = new JPanel(new java.awt.GridLayout(1, 1));
        pp.setOpaque(false);
        pp.add(play);
        pp.setAlignmentX(Component.LEFT_ALIGNMENT);
        pp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        pp.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        side.add(pp);
        side.add(left(regL));
        side.add(left(label("Im Kino: Esc bricht ab", MUTED)));
        new javax.swing.Timer(250, e -> {
            com.dan.avatar.Regie r = scene.regie;
            boolean film = r != null || scene.kino.laeuft();
            if (!film && stg[0] != null) {
                // nach dem Film: Personen der Regie abräumen, Studio wie vorher
                stg[0].clear();
                stg[0] = null;
                if (fig[0] != null) fig[0].visible = figWas[0];
                scene.rig.set(scene.set != null ? "Totale" : "frei", fig[0], null, null);
                if (spRef[0] != null) spRef[0].setVisible(true);
                regL.setText("fertig · " + scene.rig);
            } else if (r != null) {
                regL.setText(String.format(Locale.GERMANY, "%.1f s · %s%s", scene.filmClock, scene.filmLine, r.errors.isEmpty() ? "" : " · " + r.errors.size() + " Fehler"));
            } else if (!film) regL.setText("Kamera " + scene.rig + " · Orte: " + String.join(", ", scene.world.places()));
        }).start();
        side.add(Box.createVerticalStrut(12));

        // BILD (ab Phase 9, com.dan.portrait)
        side.add(block("BILD"));
        scene.portraitOn = true;
        FComboBox pOnSel = new FComboBox(new Object[]{"Bild-Werkzeuge an", "aus (wie Phase 8)"});
        pOnSel.addActionListener(e -> scene.portraitOn = pOnSel.getSelectedIndex() == 0);
        side.add(row("Bild", pOnSel));
        Object[] shots = new Object[com.dan.portrait.Shot.values().length + 1];
        shots[0] = "frei (Bühne)";
        for (int i = 0; i < com.dan.portrait.Shot.values().length; i++) shots[i + 1] = com.dan.portrait.Shot.values()[i];
        FComboBox shotSel = new FComboBox(shots);
        FComboBox bLightSel = new FComboBox(com.dan.portrait.LightSet.NAMES);
        FSlider focal = new FSlider(18, 200, 85);
        JLabel focalL = label("85 mm", MUTED);
        double[] stops = {1.2, 1.4, 1.8, 2, 2.8, 4, 5.6, 8, 11, 16};
        Object[] stopN = new Object[stops.length];
        for (int i = 0; i < stops.length; i++) stopN[i] = String.format(Locale.GERMANY, "f/%s", stops[i] == Math.rint(stops[i]) ? String.valueOf((int) stops[i]) : String.format(Locale.GERMANY, "%.1f", stops[i]));
        FComboBox stopSel = new FComboBox(stopN);
        stopSel.setSelectedIndex(2);
        FComboBox focusSel = new FComboBox(new Object[]{"näheres Auge", "linkes Auge", "rechtes Auge", "von Hand"});
        FSlider focusS = new FSlider(0, 1000, 300);
        JLabel focusL = label("Fokus –", MUTED);
        FComboBox bladeSel = new FComboBox(new Object[]{"rund", "5 Lamellen", "6 Lamellen", "7 Lamellen", "9 Lamellen"});
        FComboBox gradeSel = new FComboBox(com.dan.portrait.Grade.NAMES);
        FSlider gradeS = new FSlider(0, 100, 100);
        JLabel gradeL = label("100 %", MUTED);
        FComboBox bokSel = new FComboBox(com.dan.portrait.BokehLights.STYLES);
        FSlider bokN = new FSlider(0, 200, 70);
        JLabel bokNL = label("70", MUTED);
        FSlider bokB = new FSlider(10, 300, 100);
        JLabel bokBL = label("1,0×", MUTED);
        FComboBox mirSel = new FComboBox(new Object[]{"Spiegelung auf nassem Boden", "ohne Spiegelung"});
        FSlider grainS = new FSlider(0, 100, 40);
        JLabel grainL = label("0,40", MUTED);
        FSlider vigS = new FSlider(0, 100, 32);
        JLabel vigL = label("0,32", MUTED);
        FComboBox barSel = new FComboBox(new Object[]{"ohne Balken", "Kino 2,39 : 1"});
        Runnable bLightNow = () -> {
            StudioScene.Figure f0 = fig[0];
            if (f0 == null || f0.jointWorld == null) return;
            LightRig.applySet(scene.light, (String) bLightSel.getSelectedItem(), headOf(f0), scene.cam.eye());
        };
        shotSel.addActionListener(e -> {
            StudioScene.Figure f0 = fig[0];
            if (f0 == null || f0.jointWorld == null) return;
            if (shotSel.getSelectedIndex() == 0) { scene.lensFov = false; scene.cam.reset(); return; }
            portraitShot(scene, f0, (com.dan.portrait.Shot) shotSel.getSelectedItem(), scene.cam.yawDeg, (String) bLightSel.getSelectedItem());
        });
        bLightSel.addActionListener(e -> bLightNow.run());
        focal.addChangeListener(e -> {
            scene.lens.focalMm = focal.getValue();
            focalL.setText(focal.getValue() + " mm");
            if (shotSel.getSelectedIndex() > 0 && fig[0] != null && fig[0].jointWorld != null) {
                // Einstellung halten: der Abstand folgt der Brennweite
                com.dan.portrait.Shot sh = (com.dan.portrait.Shot) shotSel.getSelectedItem();
                scene.lensFov = true;
                scene.cam.dist = sh.distance(scene.lens.fovyDeg());
            } else scene.lensFov = true;
        });
        stopSel.addActionListener(e -> scene.lens.fNumber = stops[stopSel.getSelectedIndex()]);
        focusSel.addActionListener(e -> scene.focusMode = focusSel.getSelectedIndex());
        focusS.addChangeListener(e -> {
            if (scene.focusMode != 3) { focusSel.setSelectedIndex(3); }
            scene.lens.focusM = 0.3 * Math.pow(100 / 3.0, focusS.getValue() / 1000.0);
        });
        bladeSel.addActionListener(e -> scene.lens.blades = new int[]{0, 5, 6, 7, 9}[bladeSel.getSelectedIndex()]);
        gradeSel.addActionListener(e -> scene.grade = (String) gradeSel.getSelectedItem());
        gradeS.addChangeListener(e -> { scene.gradeMix = gradeS.getValue() / 100f; gradeL.setText(gradeS.getValue() + " %"); });
        bokSel.addActionListener(e -> scene.bokehStyle = (String) bokSel.getSelectedItem());
        bokN.addChangeListener(e -> { scene.bokehCount = bokN.getValue(); bokNL.setText(String.valueOf(bokN.getValue())); });
        bokB.addChangeListener(e -> { scene.bokehBright = bokB.getValue() / 100.0; bokBL.setText(String.format(Locale.GERMANY, "%.1f×", scene.bokehBright)); });
        mirSel.addActionListener(e -> scene.bokehMirror = mirSel.getSelectedIndex() == 0);
        grainS.addChangeListener(e -> { scene.grain = grainS.getValue() / 100f; grainL.setText(String.format(Locale.GERMANY, "%.2f", scene.grain)); });
        vigS.addChangeListener(e -> { scene.vignette = vigS.getValue() / 100f; vigL.setText(String.format(Locale.GERMANY, "%.2f", scene.vignette)); });
        barSel.addActionListener(e -> scene.bars = barSel.getSelectedIndex() == 1);
        side.add(row("Einstellung", shotSel));
        side.add(row("Licht", bLightSel));
        side.add(sliderRow("Brennweite", focal, focalL));
        side.add(row("Blende", stopSel));
        side.add(row("Fokus", focusSel));
        side.add(sliderRow("Abstand", focusS, focusL));
        side.add(row("Blendenform", bladeSel));
        side.add(row("Farbstil", gradeSel));
        side.add(sliderRow("Stärke", gradeS, gradeL));
        side.add(row("Lichter", bokSel));
        side.add(sliderRow("Anzahl", bokN, bokNL));
        side.add(sliderRow("Helligkeit", bokB, bokBL));
        side.add(row("Boden", mirSel));
        side.add(sliderRow("Korn", grainS, grainL));
        side.add(sliderRow("Rand", vigS, vigL));
        side.add(row("Balken", barSel));
        FButton photoB = new FButton("Foto 4K");
        JLabel photoL = label("3840 × 2160 · docs/fotos", MUTED);
        photoB.addActionListener(e -> {
            String ts = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
            scene.photoResult = null;
            scene.photoRequest = new File("docs/fotos/Foto_" + ts + ".jpg");
            photoL.setText("Foto wird gerechnet …");
        });
        JPanel pb = new JPanel(new java.awt.GridLayout(1, 1));
        pb.setOpaque(false);
        pb.add(photoB);
        pb.setAlignmentX(Component.LEFT_ALIGNMENT);
        pb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        pb.setBorder(BorderFactory.createEmptyBorder(6, 0, 4, 0));
        side.add(pb);
        side.add(left(photoL));
        new javax.swing.Timer(250, e -> {
            focusL.setText(String.format(Locale.GERMANY, "%.2f m", scene.lens.focusM));
            String r = scene.photoResult;
            if (r != null) { photoL.setText(r.startsWith("!") ? "Foto: Fehler " + r.substring(1) : "Foto: " + new File(r).getName()); scene.photoResult = null; }
        }).start();
        side.add(Box.createVerticalStrut(12));

        // TECHNIK (ab Phase 1)
        side.add(block("TECHNIK"));
        FComboBox q = new FComboBox(new Object[]{Quality.LOW, Quality.MEDIUM, Quality.HIGH, Quality.ULTRA});
        q.setSelectedIndex(1);
        q.addActionListener(e -> scene.quality = (Quality) q.getSelectedItem());
        side.add(row("Qualität", q));
        FSlider ex = new FSlider(-30, 30, 0);
        JLabel exL = label("Belichtung ±0,0 EV", MUTED);
        ex.addChangeListener(e -> {
            double ev = ex.getValue() / 10.0;
            scene.exposure = Math.pow(2, ev);
            exL.setText(String.format(Locale.GERMANY, "Belichtung %+.1f EV", ev));
        });
        side.add(left(exL));
        side.add(left(ex));
        FComboBox hudSel = new FComboBox(new Object[]{"HUD an", "HUD aus"});
        scene.hudOn = false;                 // Phase 19: beim Start ohne HUD, hier zuschaltbar
        hudSel.setSelectedIndex(1);
        hudSel.addActionListener(e -> scene.hudOn = hudSel.getSelectedIndex() == 0);
        side.add(row("Anzeige", hudSel));
        side.add(Box.createVerticalStrut(12));

        // DATEN (ab Phase 14): Schema AVATAR, Presets, ohne Datenbank als Dateien in daten/personen
        side.add(block("DATEN"));
        JLabel dbL = label("Datenbank wird gesucht …", MUTED);
        FComboBox presetSel = new FComboBox(new Object[]{"– Preset laden –"});
        com.dan.ftextfield.FTextfield presetName = new com.dan.ftextfield.FTextfield();
        presetName.setText("Mein Preset");
        DbAvatar dba = new DbAvatar(new File("."));
        boolean[] dbOk = {false};
        boolean[] quietP = {false};
        Runnable[] ladeListe = {null};
        ladeListe[0] = () -> {
            Thread th = new Thread(() -> {
                List<DbAvatar.Preset> l = new ArrayList<>();
                String st;
                try (java.sql.Connection c = dba.oeffnen()) {
                    int stufe = dba.stufe(c);
                    if (stufe < 1) { dbOk[0] = false; st = "AVATAR verbunden, noch nicht eingerichtet: „Einrichten“"; }
                    else {
                        int n = dba.uebernehmen(c);
                        l.addAll(DbAvatar.presets(c));
                        dbOk[0] = true;
                        st = "AVATAR auf PDBORCL · Stufe " + stufe + " · " + l.size() + " Presets" + (n > 0 ? " · " + n + " aus Dateien übernommen" : "");
                    }
                } catch (Exception dx) {
                    dbOk[0] = false;
                    String dm = String.valueOf(dx.getMessage()).split("\\R")[0];
                    st = "ohne Datenbank (" + (dm.length() > 50 ? dm.substring(0, 50) + " …" : dm) + ") · Presets aus daten/personen";
                    try { l.addAll(dba.presetsDatei()); } catch (Exception ignore) { /* keine */ }
                }
                String s2 = st;
                SwingUtilities.invokeLater(() -> {
                    quietP[0] = true;
                    presetSel.removeAllItems();
                    presetSel.addItem("– Preset laden –");
                    for (DbAvatar.Preset p : l) presetSel.addItem(p);
                    quietP[0] = false;
                    dbL.setText(s2);
                });
            }, "Daten");
            th.setDaemon(true);
            th.start();
        };
        presetSel.addActionListener(e -> {
            if (quietP[0] || !(presetSel.getSelectedItem() instanceof DbAvatar.Preset p)) return;
            cur[0] = p.person().copy();
            showValues.run();
            if (fig[0] != null) {
                fig[0].set(cur[0]);
                if (p.outfit() != null) fig[0].outfit = com.dan.avatar.Outfit.preset(p.outfit());
            }
            presetName.setText(p.name());
        });
        FButton dbSet = new FButton("Einrichten");
        FButton dbSave = new FButton("Als Preset speichern");
        dbSet.addActionListener(e -> {
            dbL.setText("Einrichten …");
            Thread th = new Thread(() -> {
                String st;
                try (java.sql.Connection c = dba.oeffnen()) {
                    com.dan.store.Einrichter.Ergebnis r = dba.einrichten(c, (t, a) -> SwingUtilities.invokeLater(() -> dbL.setText(String.format(Locale.GERMANY, "%.0f %% · %s", a * 100, t))));
                    for (String z : r.zeilen()) System.out.println("  " + z);
                    st = "Einrichten: " + r.kurz();
                } catch (Exception dx) { st = "Einrichten: " + String.valueOf(dx.getMessage()).split("\\R")[0]; }
                String s2 = st;
                SwingUtilities.invokeLater(() -> { dbL.setText(s2); ladeListe[0].run(); });
            }, "Einrichten");
            th.setDaemon(true);
            th.start();
        });
        dbSave.addActionListener(e -> {
            String n = presetName.getText().trim();
            if (n.isEmpty()) return;
            Person p = cur[0].copy();
            String o = fig[0] == null ? null : fig[0].outfit.name;
            Thread th = new Thread(() -> {
                String st;
                try {
                    if (dbOk[0]) { try (java.sql.Connection c = dba.oeffnen()) { DbAvatar.speichern(c, n, p, o); } st = "„" + n + "“ in AV_PRESET gespeichert"; }
                    else { dba.speichernDatei(n, p, o); st = "„" + n + "“ in daten/personen gespeichert (kommt beim Verbinden in die Datenbank)"; }
                } catch (Exception dx) { st = "Nicht gespeichert: " + String.valueOf(dx.getMessage()).split("\\R")[0]; }
                String s2 = st;
                SwingUtilities.invokeLater(() -> { ladeListe[0].run(); dbL.setText(s2); });
            }, "Preset speichern");
            th.setDaemon(true);
            th.start();
        });
        side.add(row("Preset", presetSel));
        side.add(row("Name", presetName));
        JPanel dbp = new JPanel(new java.awt.GridLayout(1, 2, 8, 0));
        dbp.setOpaque(false);
        dbp.add(dbSet);
        dbp.add(dbSave);
        dbp.setAlignmentX(Component.LEFT_ALIGNMENT);
        dbp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        dbp.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        side.add(dbp);
        side.add(left(dbL));
        ladeListe[0].run();
        side.add(Box.createVerticalStrut(12));

        // SPRECHEN (Phase 16): Text stumm sprechen oder eine WAV-Datei mit Text darauf legen (com.dan.voice)
        side.add(block("SPRECHEN"));
        com.dan.ftextfield.FTextfield sayText = new com.dan.ftextfield.FTextfield();
        sayText.setText("Oben im Fischerboot bin ich frei.");
        JLabel sayL = label("Viseme aus Text oder Ton · Atem vor dem Satz", MUTED);
        JLabel sayPh = label(" ", MUTED);
        FButton sayGo = new FButton("Sprechen");
        FButton sayWav = new FButton("WAV …");
        sayGo.addActionListener(e -> {
            StudioScene.Figure fg = fig[0];
            String t = sayText.getText().trim();
            if (fg == null || t.isEmpty()) return;
            com.dan.voice.Speech s = com.dan.voice.Speech.fromText(t, 1.0);
            fg.speak(s, null);
            sayL.setText(kurz(s));
            sayPh.setText(wrap(com.dan.voice.G2P.show(com.dan.voice.G2P.phonemes(t))));
        });
        sayWav.addActionListener(e -> {
            StudioScene.Figure fg = fig[0];
            if (fg == null) return;
            javax.swing.JFileChooser ch = new javax.swing.JFileChooser(new File("daten/sprache"));
            ch.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("WAV (PCM)", "wav"));
            if (ch.showOpenDialog(sayWav) != javax.swing.JFileChooser.APPROVE_OPTION) return;
            File wf = ch.getSelectedFile();
            sayL.setText("Lege „" + wf.getName() + "“ an …");
            Thread th = new Thread(() -> {
                String st, phs = " ";
                try {
                    com.dan.voice.Audio au = com.dan.voice.Audio.read(wf);
                    // Text: gleichnamige .txt neben der WAV, sonst das Textfeld; leer = nur aus dem Ton
                    File tf = new File(wf.getParentFile(), wf.getName().replaceFirst("(?i)\\.wav$", ".txt"));
                    String t = tf.isFile() ? java.nio.file.Files.readString(tf.toPath(), StandardCharsets.UTF_8).trim() : sayText.getText().trim();
                    com.dan.voice.Speech s = t.isEmpty() ? au.speech() : au.speech(t);
                    javax.sound.sampled.Clip c = null;
                    try {
                        c = javax.sound.sampled.AudioSystem.getClip();
                        c.open(javax.sound.sampled.AudioSystem.getAudioInputStream(wf));
                    } catch (Exception ax) { c = null; }
                    fg.speak(s, c);
                    st = kurz(s) + (c == null ? " · ohne Ton" : "");
                    if (!t.isEmpty()) { phs = wrap(com.dan.voice.G2P.show(com.dan.voice.G2P.phonemes(t))); String tt = t; SwingUtilities.invokeLater(() -> sayText.setText(tt)); }
                } catch (Exception wx) { st = "WAV: " + String.valueOf(wx.getMessage()).split("\\R")[0]; }
                String s2 = st, p2 = phs;
                SwingUtilities.invokeLater(() -> { sayL.setText(s2); sayPh.setText(p2); });
            }, "WAV anlegen");
            th.setDaemon(true);
            th.start();
        });
        side.add(row("Text", sayText));
        JPanel sayP = new JPanel(new java.awt.GridLayout(1, 2, 8, 0));
        sayP.setOpaque(false);
        sayP.add(sayGo);
        sayP.add(sayWav);
        sayP.setAlignmentX(Component.LEFT_ALIGNMENT);
        sayP.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        sayP.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        side.add(sayP);
        side.add(left(sayL));
        side.add(left(sayPh));
        side.add(Box.createVerticalStrut(12));

        // MUSIK (Phase 17): Lied aus daten/musik (WAV, LRC daneben), Takt und Stimmung, Singen, Musik-Visual im Kino
        side.add(block("MUSIK"));
        File musikDir = new File("daten/musik");
        List<Object> songs = new ArrayList<>();
        File[] wavs = musikDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".wav"));
        if (wavs != null) { java.util.Arrays.sort(wavs); for (File x : wavs) songs.add(x.getName()); }
        if (songs.isEmpty()) songs.add("– kein Lied in daten/musik –");
        FComboBox songSel = new FComboBox(songs.toArray());
        JLabel musL = label("Takt, Lautheit, Ruhe aus der WAV · Liedtext als LRC", MUTED);
        JLabel musL2 = label(" ", MUTED);
        FButton musPlay = new FButton("Spielen");
        FButton musVisual = new FButton("Visual");
        FButton musStop = new FButton("Stopp");
        // Bewegung zur Musik (Phase 18): Mitgehen, Tanzen (Charleston aus der CMU-Datenbank), Handtrommel
        FComboBox moveSel = new FComboBox(new Object[]{"Mitgehen", "Tanzen", "Trommel", "Tanzen und Trommel"});
        Runnable applyMove = () -> {
            StudioScene.Figure fg = fig[0];
            if (fg == null) return;
            String mv = String.valueOf(moveSel.getSelectedItem());
            try { fg.dance = mv.startsWith("Tanzen") ? TanzFilm.dance() : null; }
            catch (Exception dx) { fg.dance = null; musL.setText("Tanz: " + String.valueOf(dx.getMessage()).split("\\R")[0]); }
            fg.drum = mv.contains("Trommel") ? (fg.drum != null ? fg.drum : new com.dan.avatar.Drum()) : null;
        };
        moveSel.addActionListener(e -> applyMove.run());
        FSlider grooveVal = new FSlider(0, 100, 100);
        JLabel grooveL = label("1,00", MUTED);
        grooveVal.addChangeListener(e -> {
            double v = grooveVal.getValue() / 100.0;
            grooveL.setText(String.format(Locale.GERMANY, "%.2f", v));
            if (fig[0] != null) fig[0].grooveAmount = v;
        });
        javax.swing.Timer[] musTimer = {null};
        Runnable musStopAll = () -> {
            if (musTimer[0] != null) { musTimer[0].stop(); musTimer[0] = null; }
            if (fig[0] != null) { fig[0].hush(); fig[0].dance = null; fig[0].drum = null; }
            if (scene.kino.laeuft() && scene.regie == null) scene.kino.abbrechen();
        };
        java.util.function.Consumer<Boolean> musStart = visual -> {
            StudioScene.Figure fg = fig[0];
            Object sel = songSel.getSelectedItem();
            if (fg == null || sel == null || String.valueOf(sel).startsWith("–")) return;
            File wf = new File(musikDir, String.valueOf(sel));
            musStopAll.run();
            musL.setText("Analyse „" + wf.getName() + "“ …");
            Thread th = new Thread(() -> {
                try {
                    Track tr = Track.read(wf);
                    Rhythm rh = Rhythm.analyse(tr);
                    File lf = MusikFilm.lrcOf(wf);
                    Lyrics ly = lf.isFile() ? Lyrics.read(lf) : null;
                    com.dan.voice.Speech sung = MusikFilm.song(ly);
                    javax.sound.sampled.Clip c = null;
                    try {
                        c = javax.sound.sampled.AudioSystem.getClip();
                        c.open(javax.sound.sampled.AudioSystem.getAudioInputStream(wf));
                    } catch (Exception ax) { c = null; }
                    javax.sound.sampled.Clip clip = c;
                    SwingUtilities.invokeLater(() -> {
                        fg.grooveAmount = grooveVal.getValue() / 100.0;
                        if (visual) {
                            MusikFilm.setUp(scene);
                            MusikFilm.shot(scene, fg, 0);
                            String songTitle = ly != null && !ly.title().isEmpty() ? ly.title() : wf.getName();
                            scene.kino.beginnen("Avatar · Musik", songTitle, ly != null && !ly.artist().isEmpty() ? ly.artist() : "Musik-Visual");
                        }
                        fg.play(rh, sung, clip);
                        applyMove.run();
                        musL.setText(wrap(String.format(Locale.GERMANY, "%.1f Schläge/min · %d Schläge · Eins auf %d", rh.bpm, rh.beats.length, rh.downbeat + 1) + (clip == null ? " · ohne Ton" : "")));
                        musL2.setText(ly == null ? "ohne Liedtext (LRC)" : ly.lines.size() + " Zeilen Liedtext" + (sung == null ? "" : " · gesungen"));
                        // Untertitel und Schnitte (Musik-Visual) im Takt der Wiedergabe
                        Lyrics.Line[] shown = {null};
                        int[] lastBar = {Integer.MIN_VALUE}, cut = {0};
                        musTimer[0] = new javax.swing.Timer(40, ev -> {
                            if (fg.rhythm != rh) { ((javax.swing.Timer) ev.getSource()).stop(); return; }
                            double t = fg.speechTime();
                            Lyrics.Line l = ly == null ? null : ly.at(t);
                            if (visual && l != shown[0]) { scene.kino.untertitel(l == null ? "" : l.text()); shown[0] = l; }
                            if (!visual && l != shown[0]) { musL2.setText(l == null ? " " : "♪ " + l.text()); shown[0] = l; }
                            int bar = (int) Math.floor((rh.beatAt(t) - rh.downbeat) / rh.meter);
                            if (visual && lastBar[0] != Integer.MIN_VALUE && bar != lastBar[0] && Math.floorMod(bar, 4) == 0) {
                                int k = ++cut[0];
                                scene.cut(() -> MusikFilm.shot(scene, fg, k));
                            }
                            lastBar[0] = bar;
                        });
                        musTimer[0].start();
                    });
                } catch (Exception mx) {
                    String msg = String.valueOf(mx.getMessage()).split("\\R")[0];
                    SwingUtilities.invokeLater(() -> musL.setText("Musik: " + msg));
                }
            }, "Musik laden");
            th.setDaemon(true);
            th.start();
        };
        musPlay.addActionListener(e -> musStart.accept(false));
        musVisual.addActionListener(e -> musStart.accept(true));
        musStop.addActionListener(e -> { musStopAll.run(); musL.setText("Gestoppt"); musL2.setText(" "); });
        side.add(row("Lied", songSel));
        side.add(sliderRow("Mitgehen", grooveVal, grooveL));
        side.add(row("Bewegung", moveSel));
        JPanel musP = new JPanel(new java.awt.GridLayout(1, 3, 8, 0));
        musP.setOpaque(false);
        musP.add(musPlay);
        musP.add(musVisual);
        musP.add(musStop);
        musP.setAlignmentX(Component.LEFT_ALIGNMENT);
        musP.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        musP.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        side.add(musP);
        side.add(left(musL));
        side.add(left(musL2));
        side.add(Box.createVerticalStrut(12));

        // Die weiteren Blöcke (Werkbuch Abschnitt 7) – angelegt, noch ohne Inhalt
        String[][] later = {};
        for (String[] b : later) {
            side.add(block(b[0]));
            side.add(left(label(b[1] + " · ab Phase " + b[2], MUTED)));
            side.add(Box.createVerticalStrut(8));
        }
        side.add(Box.createVerticalGlue());
        side.add(left(label("Ziehen dreht · Rad zoomt · Doppelklick: Ansicht zurück", MUTED)));
        side.add(left(label("WASD fliegt · Q/E tiefer/höher · Umschalt schneller", MUTED)));

        foldBlocks(side, "PERSON");      // Blöcke auf- und zuklappbar (Phase 19)
        FScrollPane sp = new FScrollPane(side);
        sp.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sp.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        sp.getViewport().setBackground(PANEL);
        sp.setPreferredSize(new Dimension(360, 100));
        sp.getVerticalScrollBar().setUnitIncrement(16);
        spRef[0] = sp;

        FFrame f = new FFrame("Avatar Studio · Ein Gesicht im Regen");
        // Programmsymbol (Phase 19)
        f.setIconImages(iconImages());
        JPanel pane = f.getComponentPane();
        pane.setLayout(new BorderLayout());
        pane.add(canvas, BorderLayout.CENTER);
        pane.add(sp, BorderLayout.EAST);
        f.setPreferredFrameSize(new Dimension(1500, 900));
        f.setSize(1500, 900);
        f.setMinimumSize(new Dimension(900, 560));
        f.setBackground(BG);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        f.setExtendedState(JFrame.MAXIMIZED_BOTH);
        Animator anim = new Animator(canvas);
        anim.start();
        Thread loader = new Thread(() -> {
            try {
                Gltf model = loadModel();
                StudioScene.Figure fg = new StudioScene.Figure(new Body(model), loadSkeleton(model), cur[0], "stehen", 0, 0);
                fig[0] = fg;
                scene.figures.add(fg);
                SwingUtilities.invokeLater(() -> who.setText(cur[0].name + " · " + GltfCache.lastSource));
            } catch (Exception err) {
                SwingUtilities.invokeLater(() -> who.setText("Fehler: " + err.getMessage()));
            }
        }, "Grundkörper laden");
        loader.setDaemon(true);
        loader.start();
    }

    /**
     * Blickziel aus einem Bildpunkt: der Strahl der Kamera durch den Punkt, halb so weit wie der Kopf
     * entfernt ist (also zwischen Kamera und Person), in Figur-Koordinaten.
     */
    static double[] screenPoint(StudioScene scene, StudioScene.Figure f, int x, int y, int w, int h) {
        Camera c = scene.cam;
        double[] e = c.eye();
        float[] inv = Camera.invert(Camera.mul(c.proj((double) Math.max(1, w) / Math.max(1, h)), c.view()));
        double nx = 2.0 * x / Math.max(1, w) - 1, ny = 1 - 2.0 * y / Math.max(1, h);
        double[] far = new double[4];
        for (int r = 0; r < 4; r++) far[r] = inv[r] * nx + inv[4 + r] * ny + inv[8 + r] + inv[12 + r];
        double[] d = {far[0] / far[3] - e[0], far[1] / far[3] - e[1], far[2] / far[3] - e[2]};
        double l = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        double[] head = headOf(f);
        double hd = Math.sqrt(Math.pow(head[0] - e[0], 2) + Math.pow(head[1] - e[1], 2) + Math.pow(head[2] - e[2], 2));
        double t = 0.5 * hd / l;
        return new double[]{e[0] + d[0] * t - f.x, e[1] + d[1] * t - f.baseY(), e[2] + d[2] * t - f.z};
    }

    private static JLabel label(String s, Color c) {
        JLabel l = new JLabel(s);
        l.setForeground(c);
        l.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        return l;
    }

    private static JPanel block(String name) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.putClientProperty("avatar.block", name);
        JLabel l = new JLabel(name);
        l.setForeground(TEAL);
        l.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
                BorderFactory.createEmptyBorder(0, 0, 4, 0)));
        p.add(l, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        return p;
    }

    /**
     * Phase 19: macht aus jedem Block ({@link #block}) einen aufklappbaren Abschnitt. Alles bis zum nächsten Block (oder
     * bis zum Leerraum unten) wandert in einen Körper, den ein Klick auf die Überschrift zeigt oder verbirgt; die
     * Sichtbarkeit einzelner Zeilen, die das Studio selbst schaltet, bleibt dabei erhalten. Umschalt-Klick klappt alle
     * Blöcke gleich. Offen sind anfangs die Blöcke in {@code open}; der Zustand bleibt in den Einstellungen.
     */
    /** Programmsymbol in allen Größen (das Gesicht aus der Vorlage templates/E_Music.jpg mit Goldkante wie Lighthouse und Heidelberg). */
    static java.util.List<java.awt.Image> iconImages() {
        java.util.List<java.awt.Image> l = new java.util.ArrayList<>();
        for (int sz : new int[]{16, 24, 32, 48, 64, 128, 256}) {
            try (java.io.InputStream in = AvatarStudio.class.getResourceAsStream("icon/avatar_" + sz + ".png")) {
                if (in != null) l.add(ImageIO.read(in));
            } catch (java.io.IOException ignore) { /* nächste Größe */ }
        }
        return l;
    }

    static void foldBlocks(JPanel side, String... open) {
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(AvatarStudio.class).node("bloecke");
        java.util.Set<String> openSet = new java.util.HashSet<>(java.util.Arrays.asList(open));
        Component[] all = side.getComponents();
        java.util.List<JPanel> heads = new java.util.ArrayList<>();
        java.util.List<JPanel> bodies = new java.util.ArrayList<>();
        JPanel body = null;
        for (Component c : all) {
            boolean glue = c instanceof Box.Filler && c.getMaximumSize().height >= Short.MAX_VALUE;
            if (c instanceof JPanel jp && jp.getClientProperty("avatar.block") != null) {
                body = new JPanel();
                body.setOpaque(false);
                body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
                body.setAlignmentX(Component.LEFT_ALIGNMENT);
                body.setBorder(BorderFactory.createEmptyBorder(6, 0, 10, 0));
                heads.add(jp);
                bodies.add(body);
                side.add(body, side.getComponentZOrder(jp) + 1);
                continue;
            }
            if (glue) body = null;
            if (body != null) { side.remove(c); body.add(c); }
        }
        for (int i = 0; i < heads.size(); i++) {
            JPanel head = heads.get(i), b = bodies.get(i);
            String name = (String) head.getClientProperty("avatar.block");
            JLabel l = (JLabel) head.getComponent(0);
            boolean[] on = {prefs.getBoolean(name, openSet.contains(name))};
            Runnable apply = () -> {
                b.setVisible(on[0]);
                l.setText((on[0] ? "▾  " : "▸  ") + name);
                prefs.putBoolean(name, on[0]);
                side.revalidate();
                side.repaint();
            };
            apply.run();
            head.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            head.setToolTipText("Klick: auf- oder zuklappen · Umschalt-Klick: alle");
            final int k = i;
            head.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    boolean to = !bodies.get(k).isVisible();
                    if (e.isShiftDown()) {
                        for (int j = 0; j < heads.size(); j++) {
                            String nj = (String) heads.get(j).getClientProperty("avatar.block");
                            prefs.putBoolean(nj, to);
                        }
                        for (int j = 0; j < heads.size(); j++) {
                            String nj = (String) heads.get(j).getClientProperty("avatar.block");
                            bodies.get(j).setVisible(to);
                            ((JLabel) heads.get(j).getComponent(0)).setText((to ? "▾  " : "▸  ") + nj);
                        }
                        side.revalidate();
                        side.repaint();
                    } else {
                        on[0] = to;
                        apply.run();
                    }
                }
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { l.setForeground(INK); }
                @Override public void mouseExited(java.awt.event.MouseEvent e) { l.setForeground(TEAL); }
            });
        }
    }

    private static JPanel row(String name, Component c) {
        JPanel p = new JPanel(new BorderLayout(10, 0));
        p.setOpaque(false);
        JLabel l = label(name, MUTED);
        l.setPreferredSize(new Dimension(80, 24));
        p.add(l, BorderLayout.WEST);
        p.add(c, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        p.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        return p;
    }

    /** Kurz für das Bedienfeld (Phase 16): Laute, Dauer, Quelle. */
    static String kurz(com.dan.voice.Speech s) {
        int n = 0;
        for (com.dan.voice.Speech.Seg g : s.segs) if (!g.p().pause()) n++;
        return String.format(Locale.GERMANY, "%d Laute · %.2f s · %s", n, s.duration, s.source);
    }

    /** Langer Text im Bedienfeld umbrochen. */
    static String wrap(String s) {
        return "<html><body style='width:250px'>" + s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</body></html>";
    }

    private static JPanel sliderRow(String name, FSlider s, JLabel val) {
        JPanel r = new JPanel(new BorderLayout(8, 0));
        r.setOpaque(false);
        JLabel n = label(name, MUTED);
        n.setPreferredSize(new Dimension(86, 20));
        val.setPreferredSize(new Dimension(52, 20));
        r.add(n, BorderLayout.WEST);
        r.add(s, BorderLayout.CENTER);
        r.add(val, BorderLayout.EAST);
        r.setAlignmentX(Component.LEFT_ALIGNMENT);
        r.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        return r;
    }

    /** Seitenleiste, die der Breite des Sichtfensters folgt (keine waagerechte Laufleiste). */
    static final class TrackWidthPanel extends JPanel implements javax.swing.Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return r.height - 32; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** Die 52 Gesichtsformen nach ARKit (wie im Modell, ohne Präfix). */
    static final String[] ARKIT = {"browDownLeft", "browDownRight", "browInnerUp", "browOuterUpLeft", "browOuterUpRight", "cheekPuff",
        "cheekSquintLeft", "cheekSquintRight", "eyeBlinkLeft", "eyeBlinkRight", "eyeLookDownLeft", "eyeLookDownRight", "eyeLookInLeft",
        "eyeLookInRight", "eyeLookOutLeft", "eyeLookOutRight", "eyeLookUpLeft", "eyeLookUpRight", "eyeSquintLeft", "eyeSquintRight",
        "eyeWideLeft", "eyeWideRight", "jawForward", "jawLeft", "jawOpen", "jawRight", "mouthClose", "mouthDimpleLeft", "mouthDimpleRight",
        "mouthFrownLeft", "mouthFrownRight", "mouthFunnel", "mouthLeft", "mouthLowerDownLeft", "mouthLowerDownRight", "mouthPressLeft",
        "mouthPressRight", "mouthPucker", "mouthRight", "mouthRollLower", "mouthRollUpper", "mouthShrugLower", "mouthShrugUpper",
        "mouthSmileLeft", "mouthSmileRight", "mouthStretchLeft", "mouthStretchRight", "mouthUpperUpLeft", "mouthUpperUpRight",
        "noseSneerLeft", "noseSneerRight", "tongueOut"};
    /** Die 15 Viseme (Meta/ARKit-Benennung). */
    static final String[] VISEME = {"viseme_aa", "viseme_CH", "viseme_DD", "viseme_E", "viseme_FF", "viseme_I", "viseme_kk", "viseme_nn",
        "viseme_O", "viseme_PP", "viseme_RR", "viseme_sil", "viseme_SS", "viseme_TH", "viseme_U"};

    private static Component left(javax.swing.JComponent c) {
        c.setAlignmentX(Component.LEFT_ALIGNMENT);
        return c;
    }

    // ------------------------------------------------------------ Statusbild

    /** Offscreen-Fläche mit einer Szene; {@code shot} rechnet ein Bild und liest es aus. */
    static final class Offscreen {
        final GLOffscreenAutoDrawable d;
        final StudioScene scene;
        final int w, h;
        java.awt.image.BufferedImage last;
        Runnable inGl;

        Offscreen(StudioScene scene, int w, int h) {
            this.scene = scene; this.w = w; this.h = h;
            GLProfile prof = GLProfile.get(GLProfile.GL4);
            GLCapabilities caps = new GLCapabilities(prof);
            caps.setOnscreen(false);
            caps.setFBO(true);
            caps.setDepthBits(24);
            d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
            d.addGLEventListener(scene);
            d.addGLEventListener(new GLEventListener() {
                @Override public void init(GLAutoDrawable a) {}
                @Override public void dispose(GLAutoDrawable a) {}
                @Override public void reshape(GLAutoDrawable a, int x, int y, int ww, int hh) {}
                @Override public void display(GLAutoDrawable a) {
                    last = StudioScene.grab(a.getGL().getGL4(), Offscreen.this.w, Offscreen.this.h);
                    if (inGl != null) { inGl.run(); inGl = null; }
                }
            });
        }

        java.awt.image.BufferedImage shot(int frames) {
            for (int i = 0; i < frames; i++) d.display();
            return last;
        }
    }

    /** Kamera auf den Kopf einer Person: Blickrichtung (Gier) und Abstand. */
    static double[] headOf(StudioScene.Figure f) {
        double[] hp = Skeleton.position(f.jointWorld, f.skeleton.joint("head"));
        return new double[]{f.x + hp[0], hp[1] + f.baseY() + 0.08, f.z + hp[2]};
    }

    /**
     * Statusbild der Haut (Abnahme Phase 4): groß die Nahaufnahme im Gegenlicht (Ohr durchleuchtet),
     * daneben Porträts (Frau, Mann mit Bartschatten, dunkle Haut, Alter), die Vorlage und die Zeiten
     * je Durchgang auf „Mittel“ bei 1600 × 900.
     */
    static void haut(File out, int w, int h) throws Exception {
        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        Gltf model = loadModel();
        Skeleton sk = loadSkeleton(model);
        Person frau = Person.wuerfeln(11), mann = Person.wuerfeln(23);
        StudioScene.Figure f = new StudioScene.Figure(new Body(model), sk, frau, "stehen", 0, 0);
        scene.figures.add(f);
        scene.exposure = 0.7;
        Offscreen off = new Offscreen(scene, w, h);
        off.shot(2);
        double[] head = headOf(f);
        // ---- Zeiten: Porträt der Frau, ganzes Bild, „Mittel“
        LightRig.apply(scene.light, "Porträt", head);
        portraitCam(scene, head, 18, 0.95);
        off.shot(90);
        double[] ms = {scene.msShadow, scene.msScene, scene.msScatter, scene.msPost, scene.gpuMs, scene.msBack};
        String glInfo = scene.glInfo, note = f.skinNote();
        java.util.List<java.awt.image.BufferedImage> tiles = new java.util.ArrayList<>();
        java.util.List<String> caps = new java.util.ArrayList<>();
        // 1 Gegenlicht, Mann ohne Frisur, Ohr von der Seite
        f.set(mann);
        f.hidden.add("bob02");
        off.shot(2);
        head = headOf(f);
        LightRig.apply(scene.light, "Gegenlicht", head);
        portraitCam(scene, head, 48, 0.62);
        tiles.add(off.shot(24));
        caps.add("GEGENLICHT · OHR DURCHLEUCHTET · " + mann.name + " (ohne Frisur bis Phase 6)");
        // 2 Porträt Frau
        f.set(frau);
        f.hidden.clear();
        off.shot(2);
        head = headOf(f);
        LightRig.apply(scene.light, "Porträt", head);
        portraitCam(scene, head, 16, 0.75);
        tiles.add(off.shot(24));
        caps.add("PORTRÄT · " + frau.name + String.format(Locale.GERMANY, " · Melanin %.2f", frau.melanin));
        // 3 Mann mit Bartschatten
        f.set(mann);
        f.hidden.add("bob02");
        off.shot(2);
        head = headOf(f);
        LightRig.apply(scene.light, "Porträt", head);
        portraitCam(scene, head, 22, 0.75);
        tiles.add(off.shot(24));
        caps.add(String.format(Locale.GERMANY, "PORTRÄT · BARTSCHATTEN %.2f · Melanin %.2f", mann.beard, mann.melanin));
        // 4 Hell mit Sommersprossen
        Person hell = frau.copy();
        hell.melanin = 0.08; hell.freckles = 0.85; hell.redness = 0.6;
        f.set(hell);
        f.hidden.clear();
        off.shot(2);
        LightRig.apply(scene.light, "Porträt", head = headOf(f));
        portraitCam(scene, head, -14, 0.75);
        tiles.add(off.shot(24));
        caps.add("SEHR HELL · SOMMERSPROSSEN 0,85");
        // 5 Alt
        Person alt = mann.copy();
        alt.age = Person.ageFromYears(74);
        f.set(alt);
        f.hidden.add("bob02");
        off.shot(2);
        LightRig.apply(scene.light, "Porträt", head = headOf(f));
        portraitCam(scene, head, 20, 0.75);
        tiles.add(off.shot(24));
        caps.add("74 JAHRE · FÄLTCHEN, FLECKEN, GRUNDHAUT „ALT“");
        // 6 Niedrig (vorab integriert) zum Vergleich mit 2
        f.set(frau);
        f.hidden.clear();
        scene.quality = Quality.LOW;
        off.shot(2);
        LightRig.apply(scene.light, "Porträt", head = headOf(f));
        portraitCam(scene, head, 16, 0.75);
        tiles.add(off.shot(24));
        caps.add("„NIEDRIG“: VORAB INTEGRIERT, OHNE EIGENEN DURCHGANG");
        scene.quality = Quality.MEDIUM;
        off.d.destroy();

        // ---- zusammensetzen
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        int bigW = 820, bigH = 820, top = 46;
        drawCrop(g, tiles.get(0), 14, top, bigW, bigH);
        caption(g, caps.get(0), 14, top + bigH + 16, TEAL);
        int tw = 368, th = 248, x0 = 14 + bigW + 14;
        for (int i = 1; i < tiles.size(); i++) {
            int col = (i - 1) % 2, row = (i - 1) / 2;
            int x = x0 + col * (tw + 12), y = top + row * (th + 28);
            drawCrop(g, tiles.get(i), x, y, tw, th);
            caption(g, caps.get(i), x, y + th + 15, i == 5 ? AMBER : INK);
        }
        // Vorlage (rechts neben „Niedrig“) und Zeiten
        int vy = top + 2 * (th + 28);
        int vxx = x0 + tw + 12;
        try {
            java.awt.image.BufferedImage v = javax.imageio.ImageIO.read(new File("templates/E_Music.jpg"));
            if (v != null) {
                int vw = tw, vh = vw * v.getHeight() / v.getWidth();
                g.drawImage(v, vxx, vy, vw, vh, null);
                g.setColor(LINE);
                g.drawRect(vxx, vy, vw, vh);
                caption(g, "VORLAGE E_Music.jpg (nass: Phase 8)", vxx, vy + vh + 15, MUTED);
            }
        } catch (Exception ignore) { }
        // Zeiten (oben links im großen Bild)
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        int zx = 30, zy = top + 22;
        String[] lines = {
            "ZEITEN „MITTEL“ 1600×900, PORTRÄT",
            String.format(Locale.GERMANY, "Schattenkarte  %5.2f ms", ms[0]),
            String.format(Locale.GERMANY, "Hintergrund    %5.2f ms", ms[5]),
            String.format(Locale.GERMANY, "Person (Haut…) %5.2f ms", ms[1]),
            String.format(Locale.GERMANY, "Streuung 2×11  %5.2f ms", ms[2]),
            String.format(Locale.GERMANY, "Tonkurve       %5.2f ms", ms[3]),
            String.format(Locale.GERMANY, "GPU gesamt     %5.2f ms", ms[4]),
            String.format(Locale.GERMANY, "Person+Schatten+Streuung %4.2f ms", ms[0] + ms[1] + ms[2]),
            note.length() > 44 ? note.substring(0, 44) : note};
        g.setColor(new Color(8, 12, 16, 200));
        g.fillRoundRect(zx - 12, zy - 18, 300, lines.length * 17 + 14, 10, 10);
        g.setColor(LINE);
        g.drawRoundRect(zx - 12, zy - 18, 300, lines.length * 17 + 14, 10, 10);
        for (int i = 0; i < lines.length; i++) {
            g.setColor(i == 0 ? TEAL : (i == 7 ? AMBER : INK));
            g.drawString(lines[i], zx, zy + i * 17);
        }
        g.setColor(TEAL);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        g.drawString("AVATAR STUDIO · PHASE 4 · HAUT", 14, 28);
        g.setColor(MUTED);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.drawString("Streuung im Bildraum (Jimenez), Durchleuchtung, zwei Glanzlagen, Detailkarten aus seed, Schattenkarte", 300, 28);
        g.dispose();
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Statusbild Haut: " + out.getAbsolutePath());
        System.out.println(String.format(Locale.ROOT, "Zeiten Mittel: Schatten %.2f, Hintergrund %.2f, Person %.2f, Streuung %.2f, Tonkurve %.2f, GPU %.2f ms", ms[0], ms[5], ms[1], ms[2], ms[3], ms[4]));
        System.out.println("Grundhäute: " + note);
    }

    /** Mitte zwischen den Augen (Welt) und Blickachse des linken Auges. */
    static double[][] eyesOf(StudioScene.Figure f) {
        com.dan.avatar.EyeRig.Frame[] e = f.eyes;
        if (e == null) { double[] h = headOf(f); return new double[][]{{h[0], h[1] - 0.07, h[2] + 0.08}, {0, 0, 1}, {h[0] + 0.03, h[1] - 0.07, h[2] + 0.08}}; }
        double[] c = {f.x + (e[0].c[0] + e[1].c[0]) / 2, (e[0].c[1] + e[1].c[1]) / 2 + f.baseY(), f.z + (e[0].c[2] + e[1].c[2]) / 2};
        double[] l = {f.x + e[0].c[0] + e[0].z[0] * e[0].r, e[0].c[1] + e[0].z[1] * e[0].r + f.baseY(), f.z + e[0].c[2] + e[0].z[2] * e[0].r};
        return new double[][]{c, e[0].z, l};
    }

    /**
     * Statusbild der Augen und des Mundes (Abnahme Phase 5): Augen groß aus zwei Richtungen (Tiefe der
     * Iris unter der Hornhaut), hell und dunkel (Pupille), Lächeln mit Zähnen, sechs Iriden aus Parametern.
     */
    static void augen(File out, int w, int h) throws Exception {
        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        scene.exposure = 0.75;
        Gltf model = loadModel();
        Skeleton sk = loadSkeleton(model);
        Person mann = Person.wuerfeln(23).copy();
        mann.iris = 0.18;
        StudioScene.Figure f = new StudioScene.Figure(new Body(model), sk, mann, "stehen", 0, 0);
        f.hidden.add("bob02");
        f.pupilInstant = true;
        scene.figures.add(f);
        Offscreen off = new Offscreen(scene, w, h);
        off.shot(3);
        double[] head = headOf(f);
        LightRig.apply(scene.light, "Porträt", head);
        java.util.List<java.awt.image.BufferedImage> tiles = new java.util.ArrayList<>();
        java.util.List<String> caps = new java.util.ArrayList<>();
        double[][] ey = eyesOf(f);
        // 1, 2: aus zwei Richtungen
        for (double yaw : new double[]{28, -28}) {
            eyeCam(scene, ey[0], yaw, 0.20);
            tiles.add(off.shot(6));
            caps.add(String.format(Locale.GERMANY, "BLICK %+.0f° · IRIS LIEGT 3 MM UNTER DER HORNHAUT, VERSCHIEBT SICH GEGEN DEN LICHTREFLEX", yaw));
        }
        // 3, 4: hell und dunkel (Pupille)
        eyeCam(scene, ey[0], 8, 0.16);
        tiles.add(off.shot(6));
        double bright = f.pupilMm;
        caps.add(String.format(Locale.GERMANY, "HELL · PUPILLE %.1f MM", bright));
        scene.light.gain = 0.015f;
        scene.exposure = 0.75 * 48;
        tiles.add(off.shot(6));
        double dark = f.pupilMm;
        caps.add(String.format(Locale.GERMANY, "DUNKEL (LICHT 1,5 %%, BELICHTUNG +5,6 EV) · PUPILLE %.1f MM", dark));
        scene.light.gain = 1;
        scene.exposure = 0.75;
        // 5: Lächeln mit Zähnen
        Person frau = Person.wuerfeln(11).copy();
        frau.forms.put("arkit.mouthSmileLeft", 0.85);
        frau.forms.put("arkit.mouthSmileRight", 0.85);
        frau.forms.put("arkit.jawOpen", 0.12);
        frau.forms.put("arkit.mouthUpperUpLeft", 0.25);
        frau.forms.put("arkit.mouthUpperUpRight", 0.25);
        f.set(frau);
        f.hidden.clear();
        off.shot(3);
        head = headOf(f);
        LightRig.apply(scene.light, "Porträt", head);
        scene.cam.tx = head[0]; scene.cam.ty = head[1] - 0.10; scene.cam.tz = head[2] + 0.05;
        scene.cam.yawDeg = 14; scene.cam.pitchDeg = 2; scene.cam.dist = 0.42; scene.cam.fovyDeg = 28;
        tiles.add(off.shot(8));
        caps.add("LÄCHELN MIT ZÄHNEN · ABDUNKLUNG NACH TIEFE UND ÖFFNUNG (" + frau.name + ")");
        // 6–11: sechs Iriden
        java.util.List<java.awt.image.BufferedImage> irisTiles = new java.util.ArrayList<>();
        Person base = Person.wuerfeln(23).copy();
        for (int i = 0; i < 6; i++) {
            Person q = base.copy();
            q.iris = i / 5.0;
            q.seed = 100 + i * 17;
            f.set(q);
            f.hidden.add("bob02");
            off.shot(3);
            double[][] e2 = eyesOf(f);
            LightRig.apply(scene.light, "Porträt", headOf(f));
            eyeCam(scene, e2[2], 6, 0.055);
            irisTiles.add(off.shot(5));
        }
        String glInfo = scene.glInfo;
        double gpu = scene.gpuMs;
        off.d.destroy();

        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        int top = 46, cw = 780, ch = 240, gap = 26;
        for (int i = 0; i < 4; i++) {
            int x = 14 + (i % 2) * (cw + 12), y = top + (i / 2) * (ch + gap);
            drawCrop(g, tiles.get(i), x, y, cw, ch);
            caption(g, caps.get(i), x, y + ch + 15, i < 2 ? TEAL : (i == 2 ? INK : AMBER));
        }
        int y3 = top + 2 * (ch + gap);
        int h3 = h - y3 - 34;
        drawCrop(g, tiles.get(4), 14, y3, cw, h3);
        caption(g, caps.get(4), 14, y3 + h3 + 15, INK);
        int tw = (cw - 5 * 8) / 6;
        String[] names = {"blau", "blaugrün", "grün", "hasel", "braun", "dunkel"};
        for (int i = 0; i < 6; i++) {
            int x = 14 + cw + 12 + i * (tw + 8);
            drawCrop(g, irisTiles.get(i), x, y3, tw, tw);
            caption(g, String.format(Locale.GERMANY, "%s %.1f", names[i], i / 5.0), x, y3 + tw + 15, MUTED);
        }
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.setColor(MUTED);
        int ty = y3 + tw + 44;
        String[] lines = {
            "Hornhaut: Kugelkappe r = 0,64 R, Brechzahl 1,376; Irisebene 0,80 R, Radius 0,36 R",
            "Iris aus Parametern und seed: Fasern, Krause, Krypten, Limbusring – keine Textur",
            String.format(Locale.GERMANY, "Pupille nach Moon und Spencer: hell %.1f mm, dunkel %.1f mm", bright, dark),
            "Lidrand nass (Tränenfilm), Lider dunkeln das Auge oben und in den Winkeln ab",
            String.format(Locale.GERMANY, "%s · GPU %.2f ms je Bild (Nahaufnahme)", glInfo.length() > 36 ? glInfo.substring(0, 36) : glInfo, gpu)};
        for (int i = 0; i < lines.length; i++) g.drawString(lines[i], 14 + cw + 12, ty + i * 17);
        g.setColor(TEAL);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        g.drawString("AVATAR STUDIO · PHASE 5 · AUGEN, ZÄHNE, MUND", 14, 28);
        g.dispose();
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Statusbild Augen: " + out.getAbsolutePath());
        System.out.println(String.format(Locale.ROOT, "Pupille hell %.2f mm, dunkel %.2f mm", bright, dark));
    }

    /** Gegenlicht für die Haare: Führungslicht hinter dem Kopf, vorn ein weiches, neutrales Füllen. */
    static void haarLicht(StudioScene scene, double[] head) {
        LightRig.apply(scene.light, "Gegenlicht", head);
        scene.light.rimCol = new float[]{0.95f, 0.92f, 0.90f};
        scene.light.fillCol = new float[]{0.030f, 0.032f, 0.036f};
    }

    /** Die sechs Frisuren der Abnahme: Person, Frisur, Farbe, Rot, Grau, Bart. */
    static Person[] sechs() {
        Person frau = Person.wuerfeln(11), mann = Person.wuerfeln(23);
        Object[][] d = {
            {frau, "lang", 0.30, 0.10, 0.0, 0.0},
            {frau, "zopf", 0.12, 0.05, 0.0, 0.0},
            {frau, "dutt", 0.72, 0.10, 0.0, 0.0},
            {mann, "kurz", 0.88, 0.05, 0.25, 0.0},
            {frau, "locken", 0.96, 0.05, 0.0, 0.0},
            {mann, "glatze", 0.80, 0.15, 0.35, 0.75}};
        Person[] out = new Person[d.length];
        for (int i = 0; i < d.length; i++) {
            Person p = ((Person) d[i][0]).copy();
            p.hair = (String) d[i][1];
            p.hairColor = (Double) d[i][2]; p.hairRed = (Double) d[i][3]; p.grey = (Double) d[i][4]; p.beardLength = (Double) d[i][5];
            if (p.hair.equals("locken")) p.melanin = 0.82;
            if (p.beardLength > 0) p.beard = 0.85;
            out[i] = p;
        }
        return out;
    }

    /**
     * Flimmern: mittlere zweite zeitliche Ableitung |I(t+1) − 2 I(t) + I(t−1)| (0…255) auf den Bildpunkten
     * der Maske. Gleichmäßige Bewegung zählt nicht, nur Springen und Aufblitzen.
     */
    static double flimmern(java.util.List<java.awt.image.BufferedImage> frames, boolean[] mask, int w) {
        double s = 0;
        long n = 0;
        for (int f = 1; f + 1 < frames.size(); f++) {
            java.awt.image.BufferedImage a = frames.get(f - 1), b = frames.get(f), c = frames.get(f + 1);
            for (int i = 0; i < mask.length; i++) {
                if (!mask[i]) continue;
                int x = i % w, y = i / w;
                s += Math.abs(lum(a.getRGB(x, y)) - 2 * lum(b.getRGB(x, y)) + lum(c.getRGB(x, y)));
                n++;
            }
        }
        return n == 0 ? 0 : s / n;
    }

    static double lum(int c) { return 0.3 * ((c >> 16) & 255) + 0.59 * ((c >> 8) & 255) + 0.11 * (c & 255); }

    /**
     * Statusbild der Haare (Abnahme Phase 6): groß die Fliegenhaare vor dem Gesicht im Gegenlicht und
     * im Wind, daneben die sechs Frisuren der Bibliothek im Gegenlicht und im Wind, Zeiten auf „Mittel“
     * und das Flimmermaß (Änderung zwischen Folgebildern mit und ohne einzelne Haare).
     */
    static void haare(File out, int w, int h) throws Exception {
        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        scene.exposure = 0.7;
        Gltf model = loadModel();
        Skeleton sk = loadSkeleton(model);
        Person[] six = sechs();
        StudioScene.Figure f = new StudioScene.Figure(new Body(model), sk, six[0], "stehen", 0, 0);
        f.pupilInstant = true;
        scene.figures.add(f);
        Offscreen off = new Offscreen(scene, w, h);
        off.shot(3);
        double[] head = headOf(f);
        String[] dirs = {"von links", "von vorn"};
        java.util.List<java.awt.image.BufferedImage> tiles = new java.util.ArrayList<>();
        java.util.List<String> caps = new java.util.ArrayList<>();
        double[] msHair = new double[6];
        double msPersonPortrait = 0, msHairPortrait = 0, gpuPortrait = 0;
        // ---- Zeiten: Porträt, „Mittel“, 1600×900, lang offen mit Wind
        scene.wind = new double[]{2.5, 0.3, -0.8};
        scene.hairTime = 1.3;
        LightRig.apply(scene.light, "Porträt", head);
        portraitCam(scene, head, 18, 0.95);
        off.shot(90);
        msPersonPortrait = scene.msScene; msHairPortrait = scene.msHair; gpuPortrait = scene.gpuMs;
        // ---- groß: Fliegenhaare vor dem Gesicht, Gegenlicht, Wind von links; Flimmermaß
        haarLicht(scene, head);
        scene.cam.tx = head[0]; scene.cam.ty = head[1] - 0.06; scene.cam.tz = head[2] + 0.02;
        scene.cam.yawDeg = 24; scene.cam.pitchDeg = 2; scene.cam.dist = 0.66; scene.cam.fovyDeg = 28;
        scene.wind = new double[]{-3.0, 0.4, 1.4};
        java.util.List<java.awt.image.BufferedImage> seqOn = new java.util.ArrayList<>(), seqOff = new java.util.ArrayList<>();
        // Flimmern zeigt sich bei Bewegung unter einem Pixel je Bild: Zeitschritt 1/480 s
        for (int i = 0; i < 10; i++) { scene.hairTime = 2.0 + i / 480.0; seqOn.add(off.shot(i == 0 ? 20 : 1)); }
        scene.strands = false;
        for (int i = 0; i < 10; i++) { scene.hairTime = 2.0 + i / 480.0; seqOff.add(off.shot(i == 0 ? 4 : 1)); }
        scene.strands = true;
        // Maske: wo die einzelnen Haare das Bild ändern (an oder aus)
        boolean[] mask = new boolean[w * h];
        int masked = 0;
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++)
                if (Math.abs(lum(seqOn.get(0).getRGB(x, y)) - lum(seqOff.get(0).getRGB(x, y))) > 3) { mask[y * w + x] = true; masked++; }
        double flOn = flimmern(seqOn, mask, w), flOff = flimmern(seqOff, mask, w);
        // zum Vergleich: dieselben Haare ohne Deckung nach Pixelbreite (harte 1-Pixel-Linien)
        scene.strandsHard = true;
        java.util.List<java.awt.image.BufferedImage> seqHard = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) { scene.hairTime = 2.0 + i / 480.0; seqHard.add(off.shot(i == 0 ? 4 : 1)); }
        scene.strandsHard = false;
        double flHard = flimmern(seqHard, mask, w);
        scene.hairTime = 2.0;
        tiles.add(off.shot(2));
        caps.add("GEGENLICHT · WIND 3,3 M/S SCHRÄG VON HINTEN · STRÄHNEN VOR DEM GESICHT · " + six[0].name);
        // ---- sechs Frisuren im Gegenlicht und im Wind
        String[] names = {"LANG OFFEN", "ZOPF", "DUTT", "KURZ", "LOCKEN", "GLATZE MIT BART"};
        for (int i = 0; i < six.length; i++) {
            f.set(six[i]);
            off.shot(3);
            head = headOf(f);
            haarLicht(scene, head);
            boolean longHair = i <= 2;
            scene.cam.tx = head[0]; scene.cam.ty = head[1] - (longHair ? 0.12 : 0.05); scene.cam.tz = head[2];
            scene.cam.yawDeg = i == 2 ? 120 : (i == 1 ? 150 : 38); scene.cam.pitchDeg = 4;
            scene.cam.dist = longHair ? 0.95 : 0.68; scene.cam.fovyDeg = 28;
            scene.wind = new double[]{2.6, 0.3, 0.9};
            scene.hairTime = 3.0 + i;
            java.awt.image.BufferedImage t = off.shot(40);
            msHair[i] = scene.msHair;
            tiles.add(t);
            caps.add(String.format(Locale.GERMANY, "%s · %s · %.2f MS", names[i], f.hairNote().split(" · ")[0], msHair[i]));
        }
        String glInfo = scene.glInfo;
        String note = f.hairNote();
        off.d.destroy();
        double maxHair = 0;
        for (double v : msHair) maxHair = Math.max(maxHair, v);

        // ---- zusammensetzen
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        int bigW = 820, bigH = 820, top = 46;
        drawCrop(g, tiles.get(0), 14, top, bigW, bigH);
        caption(g, caps.get(0), 14, top + bigH + 16, TEAL);
        int tw = 368, th = 248, x0 = 14 + bigW + 14;
        for (int i = 1; i < tiles.size(); i++) {
            int col = (i - 1) % 2, row = (i - 1) / 2;
            int x = x0 + col * (tw + 12), y = top + row * (th + 28);
            drawCrop(g, tiles.get(i), x, y, tw, th);
            caption(g, caps.get(i), x, y + th + 15, INK);
        }
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        int zx = 30, zy = top + 22;
        String[] lines = {
            "ZEITEN „MITTEL“ 1600×900",
            String.format(Locale.GERMANY, "Porträt: Person %5.2f · Haare %5.2f ms", msPersonPortrait, msHairPortrait),
            String.format(Locale.GERMANY, "Haare je Frisur (Gegenlicht) max %4.2f ms", maxHair),
            String.format(Locale.GERMANY, "GPU gesamt (Porträt)        %5.2f ms", gpuPortrait),
            String.format(Locale.GERMANY, "Flimmern (Δ² auf %,d Haar-Pixeln): %.2f", masked, flOn),
            String.format(Locale.GERMANY, "  ohne Strähnen %.2f · harte 1-px-Linien %.2f", flOff, flHard),
            maxHair < 4 ? "Abnahme: unter 4 ms auf „Mittel“ – erfüllt" : "! Abnahme: über 4 ms",
            "Karten: 2 Durchgänge (deckend + Saum), ohne Sortieren",
            "Licht: Marschner (R, TT, TRT) nach Karis, tiefe Deckung"};
        g.setColor(new Color(8, 12, 16, 200));
        g.fillRoundRect(zx - 12, zy - 18, 360, lines.length * 17 + 14, 10, 10);
        g.setColor(LINE);
        g.drawRoundRect(zx - 12, zy - 18, 360, lines.length * 17 + 14, 10, 10);
        for (int i = 0; i < lines.length; i++) {
            g.setColor(i == 0 ? TEAL : (i == 6 ? AMBER : INK));
            g.drawString(lines[i], zx, zy + i * 17);
        }
        int ly = top + 3 * (th + 28) + 6;
        g.setColor(MUTED);
        String[] lic = {
            "Frisuren aus MPFB2: bob02, long01, braid01, short02, afro01 (CC0); elvs_reverse_french_braid_bun (CC BY)",
            "Bart, Brauen, Fliegenhaare: einzelne Haare, Wurzeln im verformten Netz, Feder am Kopf und Böen",
            String.format(Locale.GERMANY, "%s", glInfo.length() > 60 ? glInfo.substring(0, 60) : glInfo)};
        for (int i = 0; i < lic.length; i++) g.drawString(lic[i], x0, ly + i * 17);
        g.setColor(TEAL);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        g.drawString("AVATAR STUDIO · PHASE 6 · HAARE", 14, 28);
        g.setColor(MUTED);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.drawString("Haarkarten + einzelne Haare, Marschner-Licht, Durchsicht ohne Sortieren, Wind und Kopf", 330, 28);
        g.dispose();
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Statusbild Haare: " + out.getAbsolutePath());
        System.out.println(String.format(Locale.ROOT, "Zeiten Mittel: Porträt Person %.2f, Haare %.2f, GPU %.2f ms; Frisuren %s; max %.2f ms",
                msPersonPortrait, msHairPortrait, gpuPortrait, java.util.Arrays.toString(msHair), maxHair));
        System.out.println(String.format(Locale.ROOT, "Flimmern (%d Pixel): weich %.3f, ohne Straehnen %.3f, hart %.3f", masked, flOn, flOff, flHard));
        System.out.println("Haare: " + note);
    }

    /** Zeitreihe aus dem Minutenvideo: Zeit, Blick (Gier), Kopf (Gier), Atem, Lid, Blick (Nicken). */
    record Sample(double t, double gaze, double head, double breath, double lid, double pitch) {}

    /**
     * Statusbild von Leben, Blick und Gefühl (Abnahme Phase 7) und das Video von einer Minute ohne
     * Eingriff. Oben die Bildfolge „ruhig“ → „überrascht“ → „freudig“, darunter alle benannten Gefühle,
     * rechts die Messkurven der Minute: Lidschläge und Atem, Blick und Kopf, die Sakkaden auf der
     * Hauptfolge, dazu die Zeiten auf „Mittel“.
     */
    static void leben(File out, File video, int w, int h) throws Exception {
        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        scene.exposure = 0.85;
        scene.fixedDt = 1 / 30.0;
        Gltf model = loadModel();
        Skeleton sk = loadSkeleton(model);
        Person per = Person.wuerfeln(11).copy();
        per.hair = "lang"; per.hairColor = 0.30; per.hairRed = 0.10;
        StudioScene.Figure f = new StudioScene.Figure(new Body(model), sk, per, "stehen", 0, 0);
        f.pupilInstant = false;
        scene.figures.add(f);
        Offscreen off = new Offscreen(scene, w, h);
        scene.hairTime = 0;
        off.shot(3);
        com.dan.avatar.Behavior b = f.behavior;
        double[] head = headOf(f);
        LightRig.apply(scene.light, "Porträt", head);
        double[] range = b.eyeRange();

        // ---- eine Minute ohne Eingriff (Video), Gefühl „ruhig“, Blick zur Kamera
        portraitCam(scene, head, 10, 0.95);
        scene.cam.ty = head[1] - 0.07;
        b.feel("ruhig", 0.8);
        b.look(com.dan.avatar.Behavior.Look.CAMERA);
        for (int i = 0; i < 60; i++) { scene.hairTime = b.time(); off.shot(1); }   // einschwingen (2 s)
        double tStart = b.time();
        int b0 = b.blinks.size(), s0 = b.saccades.size();
        java.util.List<Sample> series = new java.util.ArrayList<>();
        java.awt.image.BufferedImage still = null;
        double msBeh = 0, gpu = 0, msPerson = 0, msHairT = 0;
        int frames = 60 * 30;
        MjpegAvi avi = video == null ? null : new MjpegAvi(video, 960, 540, 30, 0.82f);
        try {
            for (int i = 0; i < frames; i++) {
                scene.hairTime = b.time();
                java.awt.image.BufferedImage fr = off.shot(1);
                double[] gh = b.gazeHead();
                series.add(new Sample(b.time() - tStart, gh[0], gh[2], b.breath(), b.lid(), gh[1]));
                if (i > 30) { msBeh += scene.msBehavior; gpu += scene.gpuMs; msPerson += scene.msScene; msHairT += scene.msHair; }
                if (i == 600) still = copy(fr);
                if (avi != null) {
                    java.awt.image.BufferedImage small = new java.awt.image.BufferedImage(960, 540, java.awt.image.BufferedImage.TYPE_INT_RGB);
                    java.awt.Graphics2D g = small.createGraphics();
                    g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    g.drawImage(fr, 0, 0, 960, 540, null);
                    double tt = b.time() - tStart;
                    int nb = 0;
                    for (int k = b0; k < b.blinks.size(); k++) if (b.blinks.get(k) >= tStart) nb++;
                    g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
                    g.setColor(TEAL);
                    g.drawString("AVATAR STUDIO · PHASE 7 · EINE MINUTE OHNE EINGRIFF", 16, 26);
                    g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
                    g.setColor(INK);
                    g.drawString(String.format(Locale.GERMANY, "%02d:%05.2f · „ruhig“ 0,8 · Blick zur Kamera · Lidschläge %d · Sakkaden %d",
                            (int) (tt / 60), tt % 60, nb, b.saccades.size() - s0), 16, 520);
                    g.dispose();
                    avi.add(small);
                }
            }
        } finally { if (avi != null) avi.close(); }
        int n = frames - 31;
        msBeh /= n; gpu /= n; msPerson /= n; msHairT /= n;
        // Vergleich: dasselbe Bild ohne Verhalten (Morph und Skinning nur bei Änderung)
        f.behaviorOn = false;
        double gpuOff = 0;
        for (int i = 0; i < 90; i++) { off.shot(1); if (i >= 30) gpuOff += scene.gpuMs; }
        gpuOff /= 60;
        f.behaviorOn = true;
        for (int i = 0; i < 10; i++) off.shot(1);
        java.util.List<Double> blinkT = new java.util.ArrayList<>();
        for (int k = b0; k < b.blinks.size(); k++) if (b.blinks.get(k) >= tStart) blinkT.add(b.blinks.get(k) - tStart);
        java.util.List<double[]> sacc = new java.util.ArrayList<>();
        for (int k = s0; k < b.saccades.size(); k++) if (b.saccades.get(k)[0] >= tStart) sacc.add(b.saccades.get(k));
        int breaths = 0;
        for (int i = 1; i < series.size(); i++) if (series.get(i - 1).breath() < 0.02 && series.get(i).breath() >= 0.02 && series.get(i).breath() > series.get(i - 1).breath()) breaths++;

        // ---- Bildfolge: ruhig → überrascht → freudig (Leben aus, damit kein Lidschlag hineinfällt)
        b.alive = false;
        head = headOf(f);
        LightRig.apply(scene.light, "Porträt", head);
        scene.cam.tx = head[0]; scene.cam.ty = head[1] - 0.045; scene.cam.tz = head[2];
        scene.cam.yawDeg = 14; scene.cam.pitchDeg = 2; scene.cam.dist = 0.62; scene.cam.fovyDeg = 28;
        f.pupilInstant = true;
        b.feel("ruhig", 1);
        for (int i = 0; i < 90; i++) { scene.hairTime = 70; off.shot(1); }
        java.util.List<java.awt.image.BufferedImage> seq = new java.util.ArrayList<>();
        java.util.List<String> seqCap = new java.util.ArrayList<>();
        seq.add(copy(off.last)); seqCap.add("RUHIG");
        double[][] marks = {{0.10, 0}, {0.30, 0}, {0.90, 0}, {0.15, 1}, {0.45, 1}, {1.60, 1}};
        b.feel("überrascht", 1);
        double t0 = b.time();
        for (double[] m : marks) {
            if (m[1] == 1 && b.feelingName().equals("überrascht")) { b.feel("freudig", 1); t0 = b.time(); }
            while (b.time() - t0 < m[0] - 1e-6) off.shot(1);
            seq.add(copy(off.last));
            seqCap.add((m[1] == 0 ? "ÜBERRASCHT" : "FREUDIG") + String.format(Locale.GERMANY, " +%.0f MS", m[0] * 1000));
        }

        // ---- alle benannten Gefühle
        java.util.List<java.awt.image.BufferedImage> feel = new java.util.ArrayList<>();
        java.util.List<String> feelCap = new java.util.ArrayList<>();
        for (com.dan.avatar.Emotion.Def d : com.dan.avatar.Emotion.NAMED) {
            b.feel(d.name, 1);
            for (int i = 0; i < 75; i++) off.shot(1);
            feel.add(copy(off.last));
            feelCap.add(d.name.toUpperCase(Locale.GERMANY));
        }
        String glInfo = scene.glInfo;
        off.d.destroy();

        // ---- zusammensetzen
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(TEAL);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        g.drawString("AVATAR STUDIO · PHASE 7 · LEBEN, BLICK, GEFÜHL", 14, 28);
        g.setColor(MUTED);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.drawString("Atmen, Lidschlag, Sakkaden, Kopf folgt dem Auge, Gewicht, Gefühlsraum mit Trägheit · " + per.name, 470, 28);
        int top = 44, ts = 216, gap = 9;
        for (int i = 0; i < seq.size(); i++) {
            int x = 14 + i * (ts + gap);
            drawCrop(g, seq.get(i), x, top, ts, ts);
            caption(g, seqCap.get(i), x, top + ts + 15, i == 0 ? INK : (seqCap.get(i).startsWith("ÜBER") ? TEAL : AMBER));
            if (i > 0) { g.setColor(MUTED); g.drawString("→", x - 8, top + ts / 2); }
        }
        int y2 = top + ts + 34, tw = 134, th = 134;
        for (int i = 0; i < feel.size(); i++) {
            int col = i % 4, row = i / 4;
            int x = 14 + col * (tw + 8), y = y2 + row * (th + 24);
            drawCrop(g, feel.get(i), x, y, tw, th);
            caption(g, feelCap.get(i), x, y + th + 14, INK);
        }
        // Gefühlsraum klein in der freien Ecke
        int gx = 14 + 3 * (tw + 8), gy = y2 + 2 * (th + 24);
        g.setColor(new Color(19, 26, 32));
        g.fillRect(gx, gy, tw, th);
        g.setColor(LINE);
        g.drawRect(gx, gy, tw, th);
        g.drawLine(gx + tw / 2, gy + 4, gx + tw / 2, gy + th - 4);
        g.drawLine(gx + 4, gy + th / 2, gx + tw - 4, gy + th / 2);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 8));
        for (com.dan.avatar.Emotion.Def d : com.dan.avatar.Emotion.NAMED) {
            int px = gx + (int) ((d.valence + 1) / 2 * (tw - 16)) + 8, py = gy + (int) ((1 - (d.arousal + 1) / 2) * (th - 16)) + 8;
            g.setColor(TEAL);
            g.fillOval(px - 2, py - 2, 4, 4);
            g.setColor(MUTED);
            if (!d.name.equals("neutral")) g.drawString(d.name, Math.min(gx + tw - g.getFontMetrics().stringWidth(d.name) - 2, Math.max(gx + 2, px - 12)), py - 3);
        }
        caption(g, "GEFÜHLSRAUM", gx, gy + th + 14, MUTED);

        // Messkurven der Minute
        int px = 14 + 4 * (tw + 8) + 14, pw = w - px - 14;
        int py = y2, ph = 112;
        plotFrame(g, px, py, pw, ph, String.format(Locale.GERMANY, "LIDSCHLAG UND ATEM · %d Lidschläge/min · %d Atemzüge/min", blinkT.size(), breaths));
        drawSeries(g, series, px, py, pw, ph, 60, s2 -> s2.breath(), 0, 2.4, new Color(143, 180, 200));
        drawSeries(g, series, px, py, pw, ph, 60, s2 -> s2.lid(), 0, 1.05, AMBER);
        py += ph + 26;
        plotFrame(g, px, py, pw, ph, "BLICK (TÜRKIS) UND KOPF (BERNSTEIN) · GIER IN GRAD · ZIEL KAMERA");
        double gmin = -1, gmax = 1;
        for (Sample sm : series) { gmin = Math.min(gmin, Math.min(sm.gaze(), sm.head())); gmax = Math.max(gmax, Math.max(sm.gaze(), sm.head())); }
        gmin -= 1; gmax += 1;
        drawSeries(g, series, px, py, pw, ph, 60, s2 -> s2.gaze(), gmin, gmax, TEAL);
        drawSeries(g, series, px, py, pw, ph, 60, s2 -> s2.head(), gmin, gmax, AMBER);
        py += ph + 26;
        int sw = (pw - 16) / 2;
        plotFrame(g, px, py, sw, 150, String.format(Locale.GERMANY, "SAKKADEN · %d in 60 s · HAUPTFOLGE", sacc.size()));
        double amax = 25;
        for (double[] sc : sacc) amax = Math.max(amax, sc[1]);
        g.setColor(LINE);
        g.drawLine(px + 4, py + 146 - (int) (0.021 / 0.2 * 140), px + sw - 4, py + 146 - (int) ((0.021 + 0.0022 * amax) / 0.2 * 140));
        for (double[] sc : sacc) {
            int sx = px + 4 + (int) (sc[1] / amax * (sw - 8)), sy = py + 146 - (int) (sc[2] / 0.2 * 140);
            g.setColor(TEAL);
            g.fillOval(sx - 2, sy - 2, 5, 5);
        }
        g.setColor(MUTED);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        g.drawString(String.format(Locale.GERMANY, "Weite 0–%.0f° →  ·  Dauer bis 200 ms ↑", amax), px + 6, py + 142);
        // Zahlen
        int tx = px + sw + 16;
        double dmin = 1, dmax = 0;
        for (double[] sc : sacc) { dmin = Math.min(dmin, sc[2]); dmax = Math.max(dmax, sc[2]); }
        String[] lines = {
            "ZAHLEN DER MINUTE · „MITTEL“ 1600×900",
            String.format(Locale.GERMANY, "Lidschläge %d/min (Ziel 15–20)", blinkT.size()),
            String.format(Locale.GERMANY, "Sakkaden %d · %.0f–%.0f ms", sacc.size(), dmin * 1000, dmax * 1000),
            String.format(Locale.GERMANY, "Atem %d/min · Augen ±%.0f°/%.0f°", breaths, (range[0] + range[1]) / 2, (range[2] + range[3]) / 2),
            String.format(Locale.GERMANY, "Verhalten (CPU) %.2f ms je Bild", msBeh),
            String.format(Locale.GERMANY, "Person %.2f · Haare %.2f ms", msPerson, msHairT),
            String.format(Locale.GERMANY, "GPU gesamt %.2f ms (ohne Verhalten %.2f)", gpu, gpuOff),
            video != null ? "Video: " + video.getName() + " (60 s)" : "ohne Video"};
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        for (int i = 0; i < lines.length; i++) {
            g.setColor(i == 0 ? TEAL : INK);
            g.drawString(lines[i], tx, py + 12 + i * 17);
        }
        g.setColor(MUTED);
        g.drawString(glInfo.length() > 48 ? glInfo.substring(0, 48) : glInfo, tx, py + 12 + lines.length * 17);
        g.dispose();
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        if (still != null) ImageIO.write(still, "png", new File(out.getParentFile(), "_leben_standbild.png"));
        System.out.println("Statusbild Leben: " + out.getAbsolutePath());
        System.out.println(String.format(Locale.ROOT, "Minute: %d Lidschlaege, %d Sakkaden (%.0f-%.0f ms), %d Atemzuege; Verhalten %.3f ms, Person %.2f, Haare %.2f, GPU %.2f ms (ohne Verhalten %.2f); Augen %.1f/%.1f/%.1f/%.1f",
                blinkT.size(), sacc.size(), dmin * 1000, dmax * 1000, breaths, msBeh, msPerson, msHairT, gpu, gpuOff, range[0], range[1], range[2], range[3]));
        if (video != null) System.out.println("Video: " + video.getAbsolutePath() + " (" + video.length() / 1048576 + " MB)");
    }

    /** {@code frames} Bilder bei 1/30 s, die Umwelt im Zeitraffer, so dass {@code secs} Sekunden vergehen. */
    static void simulate(Offscreen off, StudioScene scene, double secs, int frames) {
        scene.envSpeed = secs / (frames / 30.0);
        for (int i = 0; i < frames; i++) { scene.hairTime += 1 / 30.0; off.shot(1); }
        scene.envSpeed = 1;
        for (int i = 0; i < 8; i++) { scene.hairTime += 1 / 30.0; off.shot(1); }
    }

    /**
     * Statusbild der Umwelt (Abnahme Phase 8): „Regennacht“ – dieselbe Person trocken, nach zwei Minuten
     * bei 5 mm/h und nach einer Viertelstunde unter der Kapuze; darunter die Tropfen auf Stirn und Nase groß,
     * der Atemhauch am Wintermorgen, der Sturm und die Nässe der Zonen über 15 Minuten.
     */
    static void umwelt(File out, int w, int h) throws Exception {
        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        scene.exposure = 0.85;
        scene.fixedDt = 1 / 30.0;
        Gltf model = loadModel();
        Person per = Person.wuerfeln(11).copy();
        per.hair = "zopf"; per.hairColor = 0.35;
        StudioScene.Figure f = new StudioScene.Figure(new Body(model), loadSkeleton(model), per, "stehen", 0, 0);
        f.pupilInstant = true;
        scene.figures.add(f);
        Offscreen off = new Offscreen(scene, w, h);
        scene.hairTime = 0;
        off.shot(3);
        com.dan.avatar.Behavior b = f.behavior;
        b.alive = false;
        b.feel("ruhig", 0.6);
        double[] head = headOf(f);
        LightRig.apply(scene.light, "Porträt", head);
        java.util.function.Consumer<double[]> cam = cs -> {
            double[] hd = headOf(f);
            scene.cam.tx = hd[0]; scene.cam.ty = hd[1] - cs[2]; scene.cam.tz = hd[2];
            scene.cam.yawDeg = cs[0]; scene.cam.pitchDeg = 2; scene.cam.dist = cs[1]; scene.cam.fovyDeg = 28;
        };
        java.util.List<java.awt.image.BufferedImage> tiles = new java.util.ArrayList<>();
        java.util.List<String> caps = new java.util.ArrayList<>();
        java.util.List<String> notes = new java.util.ArrayList<>();
        // 1) trocken
        com.dan.avatar.Climate dryC = com.dan.avatar.Climate.regennacht();
        dryC.rain = 0;
        scene.climate = dryC;
        cam.accept(new double[]{15, 0.62, 0.05});
        f.dry();
        simulate(off, scene, 2, 30);
        tiles.add(copy(off.last)); caps.add("TROCKEN · 9 °C · 95 %"); notes.add(f.wetness.toString());
        // 2) zwei Minuten bei 5 mm/h
        com.dan.avatar.Climate rn = com.dan.avatar.Climate.regennacht();
        scene.climate = rn;
        scene.wind = new double[]{rn.wind[0], 0.45, rn.wind[2]};
        simulate(off, scene, 120, 150);
        for (int i = 0; i < 40; i++) { scene.hairTime += 1 / 30.0; off.shot(1); }
        double gpuRain = scene.gpuMs, msEnv = scene.msEnv;
        tiles.add(copy(off.last)); caps.add("NACH 2 MIN BEI 5 MM/H · WIND 3 M/S VON VORN LINKS");
        notes.add(String.format(Locale.GERMANY, "Gesicht %.0f %% · Haar %.0f %% · Schultern %.0f %% · %d Perlen · %,d Einschläge",
                f.wetness.wet(com.dan.avatar.Wetness.FACE) * 100, f.wetness.wet(com.dan.avatar.Wetness.HAIR) * 100, f.wetness.wet(com.dan.avatar.Wetness.SHOULDERS) * 100, f.drops.beads().size(), f.drops.hits));
        // Tropfen groß (Stirn und Nase)
        cam.accept(new double[]{18, 0.28, 0.04});
        java.awt.image.BufferedImage close = copy(off.shot(4));
        scene.rainVisible = false;
        for (int i = 0; i < 40; i++) off.shot(1);
        double gpuNoRain = scene.gpuMs;
        scene.rainVisible = true;
        // 3) eine Viertelstunde unter der Kapuze
        f.dry();
        com.dan.avatar.Climate rh = rn.copy();
        rh.hood = 1;
        scene.climate = rh;
        cam.accept(new double[]{15, 0.62, 0.05});
        off.shot(2);
        simulate(off, scene, 900, 180);
        tiles.add(copy(off.last)); caps.add("NACH 15 MIN UNTER DER KAPUZE (SCHUTZ; DAS NETZ KOMMT IN PHASE 10)");
        notes.add(String.format(Locale.GERMANY, "Gesicht %.0f %% · Haar %.0f %% · Schultern %.0f %% · Brust %.0f %%",
                f.wetness.wet(com.dan.avatar.Wetness.FACE) * 100, f.wetness.wet(com.dan.avatar.Wetness.HAIR) * 100, f.wetness.wet(com.dan.avatar.Wetness.SHOULDERS) * 100, f.wetness.wet(com.dan.avatar.Wetness.CHEST) * 100));
        // 4) Wintermorgen: Atemhauch im Gegenlicht
        f.dry();
        com.dan.avatar.Climate wm = com.dan.avatar.Climate.wintermorgen();
        wm.wind = new double[]{0.25, 0, 0};          // fast windstill, sonst treibt der Hauch nach hinten
        scene.climate = wm;
        scene.wind = new double[]{0, 0, 0};
        b.alive = true;
        b.feel("frierend", 0.5);
        head = headOf(f);
        LightRig.apply(scene.light, "Gegenlicht", head);
        cam.accept(new double[]{62, 0.85, 0.03});
        for (int i = 0; i < 240; i++) off.shot(1);
        int guard = 0;
        while (guard++ < 400 && !(b.breathFlow() > 0.75 && f.breath.puffs().size() > 25)) off.shot(1);
        java.awt.image.BufferedImage winter = copy(off.last);
        int puffs = f.breath.puffs().size();
        double coldW = scene.climate.coldness(0);
        // 5) Sturm, langes Haar
        b.feel("entschlossen", 0.5);
        Person lang = per.copy();
        lang.hair = "lang";
        f.set(lang);
        f.dry();
        com.dan.avatar.Climate st = com.dan.avatar.Climate.sturm();
        scene.climate = st;
        scene.wind = new double[]{st.wind[0], 0.15 * st.windSpeed(), st.wind[2]};
        LightRig.apply(scene.light, "Gegenlicht", headOf(f));
        off.shot(3);
        cam.accept(new double[]{28, 1.05, 0.10});
        simulate(off, scene, 300, 120);
        java.awt.image.BufferedImage storm = copy(off.last);
        String stormNote = String.format(Locale.GERMANY, "Gesicht %.0f %% · Haar %.0f %% · Schultern %.0f %%",
                f.wetness.wet(com.dan.avatar.Wetness.FACE) * 100, f.wetness.wet(com.dan.avatar.Wetness.HAIR) * 100, f.wetness.wet(com.dan.avatar.Wetness.SHOULDERS) * 100);
        String glInfo = scene.glInfo;
        off.d.destroy();

        // Nässe über 15 min (ohne Bild): ohne und mit Kapuze
        int[] zones = {com.dan.avatar.Wetness.FACE, com.dan.avatar.Wetness.HAIR, com.dan.avatar.Wetness.SHOULDERS, com.dan.avatar.Wetness.LEGS};
        Color[] zc = {new Color(217, 163, 138), new Color(232, 176, 96), TEAL, MUTED};
        double[][][] curve = new double[2][zones.length][181];
        for (int hd = 0; hd < 2; hd++) {
            com.dan.avatar.Wetness ww = new com.dan.avatar.Wetness();
            com.dan.avatar.Climate cc = rn.copy(); cc.hood = hd;
            for (int i = 0; i <= 180; i++) {
                for (int z = 0; z < zones.length; z++) curve[hd][z][i] = ww.wet(zones[z]);
                for (int k = 0; k < 20; k++) ww.step(0.25, cc);
            }
        }

        // ---- zusammensetzen
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(TEAL);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        g.drawString("AVATAR STUDIO · PHASE 8 · NÄSSE, REGEN, KÄLTE", 14, 28);
        g.setColor(MUTED);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.drawString("„Regennacht“: dieselbe Person trocken, nach 2 min bei 5 mm/h, nach 15 min unter der Kapuze · " + per.name, 520, 28);
        int top = 44, tw = 520, th = 380;
        for (int i = 0; i < 3; i++) {
            int x = 14 + i * (tw + 10);
            drawCrop(g, tiles.get(i), x, top, tw, th);
            caption(g, caps.get(i), x, top + th + 15, i == 0 ? INK : (i == 1 ? TEAL : AMBER));
            g.setColor(MUTED);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
            String n = notes.get(i);
            if (n.length() > 84) n = n.substring(0, 84) + "…";
            g.drawString(n, x, top + th + 29);
        }
        int y2 = top + th + 46, sw = 390, sh = 330;
        drawCrop(g, close, 14, y2, sw, sh);
        caption(g, "TROPFEN AUF STIRN UND NASE (2 MIN)", 14, y2 + sh + 15, TEAL);
        drawCrop(g, winter, 14 + (sw + 10), y2, sw, sh);
        caption(g, String.format(Locale.GERMANY, "WINTERMORGEN −6 °C · ATEMHAUCH (%d WÖLKCHEN)", puffs), 14 + (sw + 10), y2 + sh + 15, INK);
        drawCrop(g, storm, 14 + 2 * (sw + 10), y2, sw, sh);
        caption(g, "STURM 18 MM/H · 17 M/S · 5 MIN", 14 + 2 * (sw + 10), y2 + sh + 15, AMBER);
        int px = 14 + 3 * (sw + 10), pw = w - px - 14, ph = 200;
        plotFrame(g, px, y2 + 14, pw, ph, "NÄSSE ÜBER 15 MIN · 5 MM/H · — OHNE, ┄ MIT KAPUZE");
        for (int hd = 0; hd < 2; hd++)
            for (int z = 0; z < zones.length; z++) {
                g.setColor(zc[z]);
                g.setStroke(hd == 0 ? new java.awt.BasicStroke(1.6f) : new java.awt.BasicStroke(1.4f, java.awt.BasicStroke.CAP_BUTT, java.awt.BasicStroke.JOIN_MITER, 1, new float[]{4, 4}, 0));
                int lx = -1, ly = -1;
                for (int i = 0; i <= 180; i++) {
                    int xx = px + i * pw / 180, yy = y2 + 14 + ph - 3 - (int) (curve[hd][z][i] * (ph - 6));
                    if (lx >= 0) g.drawLine(lx, ly, xx, yy);
                    lx = xx; ly = yy;
                }
            }
        g.setStroke(new java.awt.BasicStroke(1f));
        String[] zn = {"Gesicht", "Haar", "Schultern", "Beine"};
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        for (int z = 0; z < zones.length; z++) { g.setColor(zc[z]); g.drawString(zn[z], px + 6 + z * 62, y2 + 14 + ph + 14); }
        String[] lines = {
            String.format(Locale.GERMANY, "Umwelt (CPU) %.2f ms je Bild", msEnv),
            String.format(Locale.GERMANY, "GPU %.2f ms mit Regen, %.2f ohne", gpuRain, gpuNoRain),
            String.format(Locale.GERMANY, "Kälte am Wintermorgen %.2f", coldW),
            "Sturm: " + stormNote,
            "Tropfen nach Marshall–Palmer, Atlas",
            "Kapuze als Schutz; Netz ab Phase 10",
            glInfo.length() > 40 ? glInfo.substring(0, 40) : glInfo};
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        for (int i = 0; i < lines.length; i++) { g.setColor(i == 0 ? TEAL : INK); g.drawString(lines[i], px, y2 + ph + 46 + i * 15); }
        g.dispose();
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Statusbild Umwelt: " + out.getAbsolutePath());
        System.out.println(String.format(Locale.ROOT, "Umwelt %.3f ms, GPU Regen %.2f / ohne %.2f ms, Woelkchen %d", msEnv, gpuRain, gpuNoRain, puffs));
        for (String n : notes) System.out.println("  " + n);
        System.out.println("  Sturm: " + stormNote);
    }

    /** Die Frau im Stil der Vorlage (helle Haut, Sommersprossen, blaugrüne Augen, langes Haar). */
    static Person templateWoman() {
        Person p = Person.wuerfeln(11).copy();
        p.hair = "lang"; p.hairColor = 0.45; p.hairRed = 0.15;
        p.melanin = 0.3; p.freckles = 0.8; p.redness = 0.55; p.iris = 0.05; p.oil = 0.45;
        p.name = "Frau im Regen";
        return p;
    }

    /** Der Mann (kurzes Haar, Bartschatten, haselnussbraune Augen). */
    static Person templateMan() {
        Person p = Person.wuerfeln(23).copy();
        p.hair = "kurz"; p.hairColor = 0.7; p.beard = 0.55; p.beardLength = 0.05;
        p.melanin = 0.3; p.iris = 0.5; p.oil = 0.45;
        p.name = "Mann im Regen";
        return p;
    }

    /**
     * Statusbild des Bilds (Abnahme Phase 9, Meilenstein „Ein Gesicht im Regen“): die Vorlage, die Frau und
     * der Mann nebeneinander im selben Ausschnitt (85 mm, f/2,8, Nah, „Regennacht“ in Licht, Lichtern und
     * Farbe, eine Viertelstunde Regen). Darunter: Blende f/1,8 gegen f/8, Fokus auf das nähere Auge,
     * Farbstil aus und an, Kino mit Korn und Balken, die Zahlen. Mit {@code fotos} zusätzlich beide Bilder in 4K.
     */
    static void portrait(File out, File fotos, int w, int h) throws Exception {
        int tw0 = 1248, th0 = 832;                       // Größe der Vorlage
        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        scene.exposure = 0.85;
        scene.fixedDt = 1 / 30.0;
        Gltf model = loadModel();
        Person frau = templateWoman(), mann = templateMan();
        StudioScene.Figure f = new StudioScene.Figure(new Body(model), loadSkeleton(model), frau, "stehen", 0, 0);
        f.pupilInstant = true;
        scene.figures.add(f);
        Offscreen off = new Offscreen(scene, tw0, th0);
        scene.hairTime = 0;
        off.shot(3);
        com.dan.avatar.Behavior b = f.behavior;
        b.alive = false;
        b.feel("ruhig", 0.35);
        b.look(com.dan.avatar.Behavior.Look.CAMERA);
        com.dan.avatar.Climate rn = com.dan.avatar.Climate.regennacht();
        scene.climate = rn;
        scene.wind = new double[]{rn.wind[0], 0.45, rn.wind[2]};
        scene.lens.focalMm = 85; scene.lens.fNumber = 2.8;
        scene.grade = "Regennacht"; scene.bokehStyle = "Regennacht";
        java.util.function.Consumer<Double> setUp = yaw -> portraitShot(scene, f, com.dan.portrait.Shot.NAH, yaw, "Regennacht");
        setUp.accept(0.0);
        simulate(off, scene, 900, 180);
        setUp.accept(0.0);
        for (int i = 0; i < 20; i++) { scene.hairTime += 1 / 30.0; off.shot(1); }
        java.awt.image.BufferedImage imgFrau = copy(off.last);
        String wetFrau = String.format(Locale.GERMANY, "Gesicht %.0f %% · Haar %.0f %% · %d Perlen",
                f.wetness.wet(com.dan.avatar.Wetness.FACE) * 100, f.wetness.wet(com.dan.avatar.Wetness.HAIR) * 100, f.drops.beads().size());
        double gpuOn = scene.gpuMs;
        String lensFrau = scene.lens.toString();
        double distFrau = scene.cam.dist;
        if (fotos != null) { scene.photoRequest = new File(fotos, "Status_Phase9_Frau_4K.jpg"); off.shot(1); scene.awaitPhoto(); }
        // ohne Bild-Werkzeuge (GPU-Vergleich und Farbstil aus)
        scene.portraitOn = false;
        for (int i = 0; i < 30; i++) off.shot(1);
        double gpuOff = scene.gpuMs;
        scene.portraitOn = true;
        scene.gradeMix = 0;
        java.awt.image.BufferedImage noGrade = copy(off.shot(4));
        scene.gradeMix = 1;
        // Blende f/1,8 und f/8 (halbnah)
        scene.lens.fNumber = 1.8;
        portraitShot(scene, f, com.dan.portrait.Shot.HALBNAH, 0, "Regennacht");
        java.awt.image.BufferedImage f18 = copy(off.shot(6));
        scene.lens.fNumber = 8;
        java.awt.image.BufferedImage f8 = copy(off.shot(6));
        // Fokus auf das nähere Auge (groß, von der Seite)
        scene.lens.fNumber = 1.8;
        scene.lens.focalMm = 100;
        portraitShot(scene, f, com.dan.portrait.Shot.GROSS, 32, "Regennacht");
        scene.cam.dist *= 1.6;
        java.awt.image.BufferedImage eyeImg = copy(off.shot(10));
        double focusEye = scene.lens.focusM;
        // Kino: 2,39 : 1, Korn, Abendsonne
        scene.lens.focalMm = 50; scene.lens.fNumber = 2;
        scene.bars = true; scene.grain = 0.8f;
        portraitShot(scene, f, com.dan.portrait.Shot.HALBNAH, -20, "Regennacht");
        java.awt.image.BufferedImage kino = copy(off.shot(6));
        scene.bars = false; scene.grain = 0.4f;
        // der Mann
        scene.lens.focalMm = 85; scene.lens.fNumber = 2.8;
        f.set(mann);
        f.dry();
        off.shot(4);
        setUp.accept(0.0);
        simulate(off, scene, 900, 180);
        setUp.accept(0.0);
        for (int i = 0; i < 20; i++) { scene.hairTime += 1 / 30.0; off.shot(1); }
        java.awt.image.BufferedImage imgMann = copy(off.last);
        String wetMann = String.format(Locale.GERMANY, "Gesicht %.0f %% · Haar %.0f %% · %d Perlen",
                f.wetness.wet(com.dan.avatar.Wetness.FACE) * 100, f.wetness.wet(com.dan.avatar.Wetness.HAIR) * 100, f.drops.beads().size());
        if (fotos != null) { scene.photoRequest = new File(fotos, "Status_Phase9_Mann_4K.jpg"); off.shot(1); scene.awaitPhoto(); }
        String photoNote = scene.photoResult == null ? "" : scene.photoResult;
        // 4K: Zeit für ein Bild
        double gpu4k = Double.NaN;
        String glInfo = scene.glInfo;
        off.d.destroy();

        // ---- zusammensetzen
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING, java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(TEAL);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        g.drawString("AVATAR STUDIO · PHASE 9 · EIN GESICHT IM REGEN", 14, 28);
        g.setColor(MUTED);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.drawString("Meilenstein: Vorlage | Frau | Mann · " + lensFrau + " · Nah · „Regennacht“ · 15 min bei 5 mm/h", 470, 28);
        int top = 42, tw = 517, th = tw * th0 / tw0;
        java.awt.image.BufferedImage tpl = ImageIO.read(new File("templates/E_Music.jpg"));
        java.awt.image.BufferedImage[] tops = {tpl, imgFrau, imgMann};
        String[] caps = {"VORLAGE E_MUSIC.JPG", "FRAU · " + frau.name.toUpperCase(), "MANN · " + mann.name.toUpperCase()};
        String[] notes = {"Ziel: Licht, Lichter, Farbe, Nässe", wetFrau, wetMann};
        for (int i = 0; i < 3; i++) {
            int x = 14 + i * (tw + 10);
            drawCrop(g, tops[i], x, top, tw, th);
            caption(g, caps[i], x, top + th + 15, i == 0 ? MUTED : (i == 1 ? TEAL : AMBER));
            g.setColor(MUTED);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
            g.drawString(notes[i], x, top + th + 29);
        }
        int y2 = top + th + 44, sw = 308, sh = 220;
        java.awt.image.BufferedImage[] bots = {f18, f8, eyeImg, noGrade, kino};
        String[] bc = {"F/1,8 · HALBNAH", "F/8 · HALBNAH", String.format(Locale.GERMANY, "FOKUS NÄHERES AUGE · %.2f M", focusEye), "FARBSTIL AUS", "KINO 2,39 : 1 · 50 MM · KORN"};
        for (int i = 0; i < 5; i++) {
            int x = 14 + i * (sw + 6);
            drawCrop(g, bots[i], x, y2, sw, sh);
            caption(g, bc[i], x, y2 + sh + 15, i == 2 ? TEAL : INK);
        }
        com.dan.portrait.Lens l = new com.dan.portrait.Lens();
        l.focalMm = 85; l.fNumber = 2.8; l.focusM = distFrau;
        double[] dof = l.depthOfField(0.03 / 24.0 * 1080, 1080);
        com.dan.portrait.Lens l18 = l.copy(); l18.fNumber = 1.8;
        double[] dof18 = l18.depthOfField(0.03 / 24.0 * 1080, 1080);
        String[] lines = {
            String.format(Locale.GERMANY, "GPU %.2f ms mit Bild, %.2f ohne (%d × %d)", gpuOn, gpuOff, tw0, th0),
            String.format(Locale.GERMANY, "85 mm f/2,8 bei %.2f m: scharf %.1f cm", distFrau, (dof[1] - dof[0]) * 100),
            String.format(Locale.GERMANY, "85 mm f/1,8: scharf %.1f cm (Kreis 0,03 mm)", (dof18[1] - dof18[0]) * 100),
            "Hintergrund: Lichter als Zerstreuungskreise, Spiegelung auf nassem Boden",
            "Farbe: 3D-Tabelle 32³ nach ACES",
            fotos == null ? "Fotos 4K: –" : "Fotos 4K: Frau und Mann, 3840 × 2160",
            glInfo.length() > 44 ? glInfo.substring(0, 44) : glInfo};
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        for (int i = 0; i < lines.length; i++) {
            g.setColor(i == 0 ? TEAL : INK);
            g.drawString(lines[i], i < 4 ? 14 : 800, y2 + sh + 44 + (i < 4 ? i : i - 4) * 16);
        }
        g.dispose();
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Statusbild Bild: " + out.getAbsolutePath());
        System.out.println(String.format(Locale.ROOT, "GPU %.2f ms mit Bild, %.2f ohne; Fokus Auge %.3f m", gpuOn, gpuOff, focusEye));
        System.out.println("  Frau: " + wetFrau + " · Mann: " + wetMann + " · " + photoNote);
    }


    /**
     * Statusbild der Datenbank (Abnahme Phase 14): Einrichten und Prüfung gegen die Datenbank laufen
     * (DbPruefung), oben die Presets aus AV_PRESET und die Person, die das Schema DEMO in PL/SQL gewürfelt
     * und gealtert hat, so gezeigt, wie Java sie aus der Spalte REGLER liest; unten die Prüfzeilen.
     */
    @SuppressWarnings("unchecked")
    static void daten(File out, int w, int h) throws Exception {
        java.util.Map<String, Object> m = DbPruefung.lauf(new File("."), true);
        java.util.List<String> zeilen = new java.util.ArrayList<>(DbPruefung.zeilen);
        java.util.List<DbAvatar.Preset> ps = (java.util.List<DbAvatar.Preset>) m.getOrDefault("presets", java.util.List.of());
        java.util.List<Person> persons = new java.util.ArrayList<>();
        java.util.List<String> outfits = new java.util.ArrayList<>(), titles = new java.util.ArrayList<>(), notes = new java.util.ArrayList<>();
        for (DbAvatar.Preset p : ps) {
            if (persons.size() >= 4) break;
            persons.add(p.person());
            outfits.add(p.outfit() == null ? "Regenjacke" : p.outfit());
            titles.add(p.name());
            notes.add(p.herkunft() + " · seed " + p.person().seed);
        }
        String fit = (String) m.getOrDefault("demoOutfit", "Regenjacke");
        if (m.get("demoPerson") instanceof Person dp) {
            persons.add(dp); outfits.add("Alltag"); titles.add("DEMO: WUERFELN(4711)"); notes.add("PL/SQL · Wattführer im Nov.: " + fit);
        }
        if (m.get("demoAelter") instanceof Person da) {
            persons.add(da); outfits.add("Alltag"); titles.add("DEMO: AELTER(…, 30)"); notes.add(String.format(Locale.GERMANY, "PL/SQL · %.1f Jahre", da.ageYears()));
        }

        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        scene.exposure = 0.85;
        scene.fixedDt = 1 / 30.0;
        Gltf model = loadModel();
        java.util.List<java.awt.image.BufferedImage> tiles = new java.util.ArrayList<>();
        String glInfo = "";
        if (!persons.isEmpty()) {
            StudioScene.Figure f = new StudioScene.Figure(new Body(model), loadSkeleton(model), persons.get(0), "stehen", 0, 0);
            f.pupilInstant = true;
            f.behaviorOn = false;
            scene.figures.add(f);
            Offscreen off = new Offscreen(scene, 520, 940);
            scene.hairTime = 0;
            off.shot(3);
            for (int i = 0; i < persons.size(); i++) {
                if (i > 0) {
                    // jede Person frisch (Haar und Kleidung starten an ihrem eigenen Körper)
                    scene.figures.clear();
                    f = new StudioScene.Figure(new Body(model), loadSkeleton(model), persons.get(i), "stehen", 0, 0);
                    f.pupilInstant = true;
                    f.behaviorOn = false;
                    scene.figures.add(f);
                }
                f.outfit = com.dan.avatar.Outfit.preset(outfits.get(i));
                f.dry();
                off.shot(3);
                double[] head = headOf(f);
                LightRig.apply(scene.light, "Bühne", head);
                scene.cam.tx = 0; scene.cam.ty = head[1] * 0.53; scene.cam.tz = 0;
                scene.cam.yawDeg = 18; scene.cam.pitchDeg = 3; scene.cam.dist = 2.55 * head[1]; scene.cam.fovyDeg = 30;
                for (int k = 0; k < 30; k++) { scene.hairTime += 1 / 30.0; off.shot(1); }
                tiles.add(copy(off.last));
            }
            glInfo = scene.glInfo;
            off.d.destroy();
        }

        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(TEAL);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        g.drawString("AVATAR STUDIO · PHASE 14 · DATENBANK AVATAR", 14, 28);
        g.setColor(MUTED);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.drawString("Personen aus AV_PRESET und aus dem Schema DEMO (PL/SQL), gelesen als AV_PERSON_T · " + m.getOrDefault("wo", "–"), 470, 28);
        int top = 40, tw = 258, th = 430;
        for (int i = 0; i < tiles.size() && i < 6; i++) {
            int x = 14 + i * (tw + 4);
            drawCrop(g, tiles.get(i), x, top, tw, th);
            caption(g, titles.get(i).toUpperCase(), x, top + th + 15, i >= 4 ? AMBER : TEAL);
            g.setColor(MUTED);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
            String n = notes.get(i) + (i >= 4 ? "" : " · " + outfits.get(i));
            if (n.length() > 42) n = n.substring(0, 42) + "…";
            g.drawString(n, x, top + th + 29);
        }
        // Prüfzeilen in zwei Spalten
        int y0 = top + th + 52, colW = (w - 28) / 2 - 10, lh = 15;
        caption(g, "ANT DB-PRUEFUNG · EINRICHTEN, WÜRFEL JAVA = PL/SQL, OUTFITS, RECHTE, DEMO", 14, y0, INK);
        java.util.List<String> show = new java.util.ArrayList<>();
        for (String z : zeilen) show.add(z.trim());
        Object rechte = m.get("rechte");
        if (rechte != null) show.add("• " + rechte);
        int perCol = (h - y0 - 20) / lh;
        if (show.size() <= perCol) colW = w - 28;
        Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 11), sym = new Font(Font.DIALOG, Font.BOLD, 11);
        java.awt.FontMetrics fm = g.getFontMetrics(mono);
        for (int i = 0; i < show.size() && i < 2 * perCol; i++) {
            int x = 14 + (i / perCol) * (colW + 20), y = y0 + 18 + (i % perCol) * lh;
            String z = show.get(i);
            boolean good = z.startsWith("✓"), badZ = z.startsWith("✗");
            String body = (good || badZ) ? z.substring(1).trim() : z;
            g.setFont(sym);
            g.setColor(good ? TEAL : badZ ? new Color(220, 90, 90) : AMBER);
            g.drawString(good ? "✓" : badZ ? "✗" : "•", x, y);
            g.setFont(mono);
            g.setColor(INK);
            String b = body.startsWith("• ") ? body.substring(2) : body;
            while (fm.stringWidth(b) > colW - 16 && b.length() > 4) b = b.substring(0, b.length() - 2);
            if (!b.equals(body) && !body.startsWith("• ")) b = b + "…";
            g.drawString(b, x + 16, y);
        }
        g.setColor(MUTED);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        g.drawString(glInfo.length() > 60 ? glInfo.substring(0, 60) : glInfo, w - 14 - g.getFontMetrics().stringWidth(glInfo.length() > 60 ? glInfo.substring(0, 60) : glInfo), h - 8);
        g.dispose();
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Statusbild Datenbank: " + out.getAbsolutePath());
    }

    /**
     * Statusbild der Kleidung (Abnahme Phase 10): dieselbe Person in sechs Outfits (Strickkapuze wie die
     * Vorlage, Regenjacke, Winter, Sommer, 1362, Wikingerzeit), darunter die Strickkapuze nass im Regen von
     * nah, das Ölzeug nass und die Kapuze im Wind (drei Augenblicke), dazu die Zahlen.
     */
    static void kleidung(File out, int w, int h) throws Exception {
        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        scene.exposure = 0.85;
        scene.fixedDt = 1 / 30.0;
        Gltf model = loadModel();
        Person p = templateWoman();
        StudioScene.Figure f = new StudioScene.Figure(new Body(model), loadSkeleton(model), p, "stehen", 0, 0);
        f.pupilInstant = true;
        f.behaviorOn = false;
        scene.figures.add(f);
        int tw0 = 520, th0 = 940;
        Offscreen off = new Offscreen(scene, tw0, th0);
        scene.hairTime = 0;
        off.shot(3);
        String[] names = {"Strickkapuze", "Regenjacke", "Winter", "Sommer", "1362", "Wikingerzeit"};
        java.util.List<java.awt.image.BufferedImage> tiles = new java.util.ArrayList<>();
        java.util.List<String> notes = new java.util.ArrayList<>();
        double gpuSum = 0;
        for (String n : names) {
            f.outfit = com.dan.avatar.Outfit.preset(n);
            f.dry();
            off.shot(2);
            double[] head = headOf(f);
            LightRig.apply(scene.light, "Bühne", head);
            scene.cam.tx = 0; scene.cam.ty = 0.86; scene.cam.tz = 0;
            scene.cam.yawDeg = 18; scene.cam.pitchDeg = 3; scene.cam.dist = 3.6; scene.cam.fovyDeg = 30;
            for (int i = 0; i < 12; i++) { scene.hairTime += 1 / 30.0; off.shot(1); }
            tiles.add(copy(off.last));
            gpuSum += scene.gpuMs;
            StringBuilder sb = new StringBuilder();
            for (com.dan.avatar.Outfit.Piece pc : f.outfit.pieces) { if (sb.length() > 0) sb.append(", "); sb.append(pc.fabric); }
            notes.add(sb.toString());
        }
        double gpuOutfit = gpuSum / names.length;
        off.d.destroy();

        // nah: Strickkapuze nass in der Regennacht (Bild-Werkzeuge an), Ölzeug nass, Kapuze im Wind
        int cw = 1248, ch = 832;
        Offscreen o2 = new Offscreen(scene, cw, ch);
        f.outfit = com.dan.avatar.Outfit.preset("Strickkapuze");
        f.dry();
        o2.shot(3);
        com.dan.avatar.Climate rn = com.dan.avatar.Climate.regennacht();
        scene.climate = rn;
        scene.wind = new double[]{rn.wind[0], 0.45, rn.wind[2]};
        scene.lens.focalMm = 85; scene.lens.fNumber = 2.8;
        portraitShot(scene, f, com.dan.portrait.Shot.HALBNAH, 0, "Regennacht");
        simulate(o2, scene, 900, 150);
        portraitShot(scene, f, com.dan.portrait.Shot.HALBNAH, 0, "Regennacht");
        for (int i = 0; i < 20; i++) { scene.hairTime += 1 / 30.0; o2.shot(1); }
        java.awt.image.BufferedImage knit = copy(o2.last);
        double gpuKnit = scene.gpuMs;
        String wetKnit = String.format(Locale.GERMANY, "Schultern %.0f %% · Haar %.0f %% (Kapuze)",
                f.wetness.wet(com.dan.avatar.Wetness.SHOULDERS) * 100, f.wetness.wet(com.dan.avatar.Wetness.HAIR) * 100);
        f.outfit = com.dan.avatar.Outfit.preset("Regenjacke");
        o2.shot(3);
        portraitShot(scene, f, com.dan.portrait.Shot.HALBNAH, 25, "Regennacht");
        for (int i = 0; i < 20; i++) { scene.hairTime += 1 / 30.0; o2.shot(1); }
        java.awt.image.BufferedImage oil = copy(o2.last);
        // Kapuze im Wind: Sturm von vorn links, drei Augenblicke
        f.outfit = com.dan.avatar.Outfit.preset("1362");
        f.dry();
        com.dan.avatar.Climate st = com.dan.avatar.Climate.sturm();
        st.rain = 0;
        scene.climate = st;
        scene.wind = new double[]{st.wind[0], 0.15 * st.windSpeed(), st.wind[2]};
        scene.portraitOn = false;
        double[] head = headOf(f);
        LightRig.apply(scene.light, "Porträt", head);
        scene.cam.tx = head[0]; scene.cam.ty = head[1] - 0.25; scene.cam.tz = head[2];
        scene.cam.yawDeg = 70; scene.cam.pitchDeg = 3; scene.cam.dist = 1.5; scene.cam.fovyDeg = 30;
        java.awt.image.BufferedImage[] windT = new java.awt.image.BufferedImage[3];
        java.util.List<double[]> swayS = new java.util.ArrayList<>();
        for (int i = 0; i < 150; i++) {
            scene.hairTime += 1 / 30.0; o2.shot(1);
            swayS.add(new double[]{i / 30.0, f.clothSway.sway[0] * 100, f.clothSway.sway[2] * 100});
            if (i == 20) windT[0] = copy(o2.last);
            if (i == 77) windT[1] = copy(o2.last);
            if (i == 133) windT[2] = copy(o2.last);
        }
        double swayCm = f.clothSway.amount() * 100;
        String glInfo = scene.glInfo;
        o2.d.destroy();
        long glbBytes = new File("src/com/dan/avatar/res/avatar_base.glb").length();

        // ---- zusammensetzen
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(TEAL);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        g.drawString("AVATAR STUDIO · PHASE 10 · KLEIDUNG", 14, 28);
        g.setColor(MUTED);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.drawString("Eine Person in sechs Outfits · MPFB-Kleidung (CC0), die den Reglern folgt · eigene Stoffe · " + p.name, 380, 28);
        int top = 40, tw = 258, th = 430;
        for (int i = 0; i < 6; i++) {
            int x = 14 + i * (tw + 4);
            drawCrop(g, tiles.get(i), x, top, tw, th);
            caption(g, names[i].toUpperCase(), x, top + th + 15, i == 0 ? TEAL : INK);
            g.setColor(MUTED);
            g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
            String n = notes.get(i);
            if (n.length() > 40) n = n.substring(0, 40) + "…";
            g.drawString(n, x, top + th + 29);
        }
        int y2 = top + th + 42, bh = 250;
        int kw = bh * cw / ch;
        drawCrop(g, knit, 14, y2, kw, bh);
        caption(g, "STRICKKAPUZE · 15 MIN REGEN · WASSERPERLEN", 14, y2 + bh + 15, TEAL);
        drawCrop(g, oil, 14 + kw + 8, y2, kw, bh);
        caption(g, "ÖLZEUG UND GUMMI · NASS", 14 + kw + 8, y2 + bh + 15, AMBER);
        int x3 = 14 + 2 * (kw + 8), sw = 150;
        for (int i = 0; i < 3; i++) drawCrop(g, windT[i], x3 + i * (sw + 4), y2, sw, bh);
        caption(g, "KAPUZE IM STURM · 0,7 / 2,6 / 4,4 S", x3, y2 + bh + 15, INK);
        int px = x3 + 3 * (sw + 4) + 10, pw = w - px - 14, ph = 110;
        plotFrame(g, px, y2, pw, ph, "AUSLENKUNG DER FEDER (CM) · — SEITLICH, ┄ VORN/HINTEN");
        g.setStroke(new java.awt.BasicStroke(1.5f));
        for (int c = 1; c <= 2; c++) {
            g.setColor(c == 1 ? TEAL : AMBER);
            int lx = -1, ly = -1;
            for (double[] sv : swayS) {
                int xx = px + (int) (sv[0] / 5.0 * pw), yy = y2 + ph / 2 - (int) (sv[c] / 8.0 * (ph / 2 - 4));
                if (lx >= 0) g.drawLine(lx, ly, xx, yy);
                lx = xx; ly = yy;
            }
        }
        g.setStroke(new java.awt.BasicStroke(1f));
        String[] lines = {
            String.format(Locale.GERMANY, "GPU %.2f ms (ganze Person, Mittel des Outfits)", gpuOutfit),
            String.format(Locale.GERMANY, "Halbnah im Regen mit Bild: %.2f ms", gpuKnit),
            wetKnit,
            String.format(Locale.GERMANY, "Kapuze im Sturm: %.1f cm Auslenkung", swayCm),
            String.format(Locale.GERMANY, "Kleiderschrank: %d Teile, alle CC0", scene.wardrobe.items.size()),
            String.format(Locale.GERMANY, "Grundkörper %.0f MB", glbBytes / 1048576.0),
            glInfo.length() > 40 ? glInfo.substring(0, 40) : glInfo};
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        for (int i = 0; i < lines.length; i++) { g.setColor(i == 0 ? TEAL : INK); g.drawString(lines[i], px, y2 + ph + 26 + i * 16); }
        g.dispose();
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Statusbild Kleidung: " + out.getAbsolutePath());
        System.out.println(String.format(Locale.ROOT, "GPU %.2f ms je Outfit, Halbnah Regen %.2f ms, Kapuze %.1f cm", gpuOutfit, gpuKnit, swayCm));
        System.out.println("  " + wetKnit);
    }

    /** Abnahme Phase 11: Ablauf je Person (Kreis um die Bühnenmitte, Radius r, Start bei s0). */
    static com.dan.avatar.Motion abnahme(double r, double s0) {
        com.dan.avatar.Motion m = new com.dan.avatar.Motion(com.dan.avatar.Motion.circle(0, 0, r, true), s0);
        double walk1 = Math.PI * r;                          // halber Kreis: Halt an der Seite der Bühne
        m.then(com.dan.avatar.Motion.walk(walk1)).then(com.dan.avatar.Motion.stand(0.6)).then(com.dan.avatar.Motion.turn(-Math.PI / 2))
                .then(com.dan.avatar.Motion.sit(0.8)).then(com.dan.avatar.Motion.wave(2.6)).then(com.dan.avatar.Motion.stand(0.4))
                .then(com.dan.avatar.Motion.standUp()).then(com.dan.avatar.Motion.turn(Math.PI / 2)).then(com.dan.avatar.Motion.walk(3.5))
                .then(com.dan.avatar.Motion.stand(1));
        m.benchAt = new double[]{s0 + walk1 - 0.05, -Math.PI / 2};
        return m;
    }

    /**
     * Statusbild der Bewegung (Abnahme Phase 11): zwei Personen gehen im Kreis, bleiben stehen, drehen sich,
     * setzen sich auf eine Bank, winken, stehen auf und gehen weiter. Bildstreifen, Fußspuren von oben mit dem
     * Gleiten der tragenden Füße, die Zahlen; dazu ein Video.
     */
    static void bewegung(File out, File video, int w, int h) throws Exception {
        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        scene.exposure = 0.85;
        scene.fixedDt = 1 / 30.0;
        Gltf model = loadModel();
        Person frau = templateWoman().copy(), mann = templateMan().copy();
        frau.hair = "zopf"; frau.name = "Frau"; mann.name = "Mann";
        double r = 1.45;
        StudioScene.Figure fa = new StudioScene.Figure(new Body(model), loadSkeleton(model), frau, "stehen", 0, 0);
        StudioScene.Figure fb = new StudioScene.Figure(new Body(model), loadSkeleton(model), mann, "stehen", 0, 0);
        fa.outfit = com.dan.avatar.Outfit.preset("Winter");
        fb.outfit = com.dan.avatar.Outfit.preset("Alltag");
        fa.motion = abnahme(r, 0);
        fb.motion = abnahme(r, Math.PI * r);
        for (StudioScene.Figure f : new StudioScene.Figure[]{fa, fb}) { f.pupilInstant = true; scene.figures.add(f); }
        int vw = 960, vh = 540;
        Offscreen off = new Offscreen(scene, vw, vh);
        LightRig.apply(scene.light, "Bühne", new double[]{0, 1.6, 0});
        scene.cam.tx = 0; scene.cam.ty = 0.75; scene.cam.tz = 0;
        scene.cam.yawDeg = 0; scene.cam.pitchDeg = 14; scene.cam.dist = 6.4; scene.cam.fovyDeg = 38;
        scene.hairTime = 0;
        MjpegAvi avi = video == null ? null : new MjpegAvi(video, vw, vh, 30, 0.85f);
        double[] snapT = {2.0, 4.6, 5.8, 7.4, 9.5, 15.5};
        String[] snapN = {"GEHEN", "ANHALTEN", "DREHEN", "SETZEN", "WINKEN", "WEITER"};
        java.awt.image.BufferedImage[] snaps = new java.awt.image.BufferedImage[snapT.length];
        double t = 0, gpu = 0;
        int frames = 0, si = 0;
        java.util.List<double[]> trackA = new java.util.ArrayList<>(), trackB = new java.util.ArrayList<>();
        try {
            while (t < 32 && (!fa.motion.idle() || !fb.motion.idle() || t < 3)) {
                scene.hairTime += 1 / 30.0;
                off.shot(1);
                t += 1 / 30.0;
                frames++;
                if (frames > 10) gpu += scene.gpuMs;
                trackA.add(new double[]{fa.motion.x, fa.motion.z});
                trackB.add(new double[]{fb.motion.x, fb.motion.z});
                if (avi != null) avi.add(off.last);
                if (si < snapT.length && t >= snapT[si]) { snaps[si] = copy(off.last); si++; }
            }
        } finally { if (avi != null) avi.close(); }
        double secs = t;
        for (; si < snapT.length; si++) snaps[si] = copy(off.last);
        String glInfo = scene.glInfo;
        // Nahaufnahme der Füße beim Gehen (eigener Lauf)
        off.d.destroy();

        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(TEAL);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        g.drawString("AVATAR STUDIO · PHASE 11 · BEWEGUNG", 14, 28);
        g.setColor(MUTED);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.drawString("Zwei Personen gehen im Kreis, bleiben stehen, drehen sich, setzen sich, winken, stehen auf, gehen weiter · Gang aus Formeln, Füße fest", 330, 28);
        int tw = 520, th = 293, top = 42;
        for (int i = 0; i < 6; i++) {
            int x = 14 + (i % 3) * (tw + 10), y = top + (i / 3) * (th + 26);
            drawCrop(g, snaps[i], x, y, tw, th);
            caption(g, String.format(Locale.GERMANY, "%s · %.1f S", snapN[i], snapT[i]), x, y + th + 15, i == 0 ? TEAL : INK);
        }
        // Fußspuren von oben
        int py = top + 2 * (th + 26) + 4, ph = h - py - 14, pw = ph;
        int px0 = 14;
        g.setColor(PANEL);
        g.fillRect(px0, py, pw, ph);
        g.setColor(LINE);
        g.drawRect(px0, py, pw, ph);
        double sc = ph / 4.6;
        java.util.function.DoubleUnaryOperator X = xx -> px0 + pw / 2.0 + xx * sc, Z = zz -> py + ph / 2.0 - zz * sc;
        g.setColor(LINE);
        g.drawOval((int) (X.applyAsDouble(-r)), (int) (Z.applyAsDouble(r)), (int) (2 * r * sc), (int) (2 * r * sc));
        StudioScene.Figure[] fs = {fa, fb};
        Color[] fc = {TEAL, AMBER};
        for (int k = 0; k < 2; k++) {
            com.dan.avatar.Motion m = fs[k].motion;
            if (m.bench != null) {
                g.setColor(new Color(110, 80, 50));
                double c = Math.cos(m.bench[2]), s2 = Math.sin(m.bench[2]);
                int[] xs = new int[4], ys = new int[4];
                double[][] cr = {{-0.55, -0.19}, {0.55, -0.19}, {0.55, 0.19}, {-0.55, 0.19}};
                for (int q = 0; q < 4; q++) { xs[q] = (int) X.applyAsDouble(m.bench[0] + c * cr[q][0] + s2 * cr[q][1]); ys[q] = (int) Z.applyAsDouble(m.bench[1] - s2 * cr[q][0] + c * cr[q][1]); }
                g.fillPolygon(xs, ys, 4);
            }
            g.setColor(fc[k]);
            for (double[] st : m.steps) {
                double c = Math.cos(st[2]), s2 = Math.sin(st[2]);
                int cx = (int) X.applyAsDouble(st[0]), cz = (int) Z.applyAsDouble(st[1]);
                int ex = (int) X.applyAsDouble(st[0] + s2 * 0.12), ez = (int) Z.applyAsDouble(st[1] + c * 0.12);
                g.drawLine(cx, cz, ex, ez);
                g.fillOval(cx - 2, cz - 2, 5, 5);
            }
        }
        caption(g, "FUSSSPUREN VON OBEN (JE AUFSETZEN) · BÄNKE", px0, py - 4 + 0, MUTED);
        int tx = px0 + pw + 20;
        String[] lines = new String[12];
        int li = 0;
        for (int k = 0; k < 2; k++) {
            com.dan.avatar.Motion m = fs[k].motion;
            Person pp = k == 0 ? frau : mann;
            lines[li++] = String.format(Locale.GERMANY, "%s (%.2f m, %.0f Jahre): Bein %.2f m, Schritt %.2f m, %.2f Schritte/s, %.2f m/s",
                    pp.name, fs[k].body.footY(pp.weights()) * 0 + heightOf(fs[k]), pp.ageYears(), m.legLen, m.stride / 2, m.cadence * 2, m.vWalk);
            lines[li++] = String.format(Locale.GERMANY, "   %d Schritte · Gleiten der tragenden Füße höchstens %.1f mm/s, zusammen %.1f mm · Reichweite verfehlt %.1f mm", m.steps.size(), m.slideMax * 1000, m.slideSum * 1000, m.missMax * 1000);
        }
        lines[li++] = String.format(Locale.GERMANY, "Ablauf %.1f s · %d Bilder · GPU %.2f ms (zwei Personen, %d × %d)", secs, frames, gpu / Math.max(1, frames - 10), vw, vh);
        lines[li++] = "Füße in Weltkoordinaten: Aufsetzpunkt vorhergesagt, in der Stütze fest";
        lines[li++] = "Drehen auf der Stelle mit Schritten · Sitzen: Pose gemischt, Füße bleiben";
        lines[li++] = "Winken: Arm gezielt, Hand offen · Finger als Griff (locker, offen, Faust)";
        lines[li++] = video == null ? "" : "Video: " + video.getName();
        lines[li++] = glInfo.length() > 60 ? glInfo.substring(0, 60) : glInfo;
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        for (int i = 0; i < li; i++) { g.setColor(i < 4 ? (i < 2 ? TEAL : AMBER) : INK); if (i == 4) g.setColor(TEAL); g.drawString(lines[i], tx, py + 18 + i * 18); }
        g.dispose();
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Statusbild Bewegung: " + out.getAbsolutePath());
        for (int i = 0; i < li; i++) System.out.println("  " + lines[i]);
    }

    static double heightOf(StudioScene.Figure f) {
        int hj = f.skeleton.joint("head");
        double[][] rs = f.skeleton.restFor(f.person.weights());
        return com.dan.avatar.Skeleton.position(rs, hj)[1] + 0.13 - f.body.footY(f.person.weights());
    }

    /** Entwicklung: eine Person geht geradeaus (Profil), Bilder zu festen Zeiten nach {@code dir}. */
    static void gehDev(File dir, String mode) throws Exception {
        dir.mkdirs();
        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        scene.exposure = 0.9;
        scene.fixedDt = 1 / 30.0;
        Gltf model = loadModel();
        Person p = templateMan();
        StudioScene.Figure f = new StudioScene.Figure(new Body(model), loadSkeleton(model), p, "stehen", 0, 0);
        f.outfit = com.dan.avatar.Outfit.preset("Sommer");
        com.dan.avatar.Motion m = new com.dan.avatar.Motion(com.dan.avatar.Motion.line(0, -2.2, 0), 0);
        if (mode.equals("sit")) { m.then(com.dan.avatar.Motion.stand(0.5)).then(com.dan.avatar.Motion.sit(1)).then(com.dan.avatar.Motion.wave(3)); m.benchAt = new double[]{0, 0}; }
        else if (mode.equals("turn")) m.then(com.dan.avatar.Motion.turn(Math.PI / 2)).then(com.dan.avatar.Motion.stand(1));
        else m.then(com.dan.avatar.Motion.walk(4.2)).then(com.dan.avatar.Motion.stand(1));
        f.motion = m;
        f.pupilInstant = true;
        scene.figures.add(f);
        Offscreen off = new Offscreen(scene, 900, 900);
        LightRig.apply(scene.light, "Porträt", new double[]{0, 1.6, 0});
        scene.cam.tx = 0; scene.cam.ty = 0.9; scene.cam.tz = 0;
        scene.cam.yawDeg = mode.equals("walk") ? 90 : 35; scene.cam.pitchDeg = 4; scene.cam.dist = 4.2; scene.cam.fovyDeg = 40;
        double t = 0;
        double[] snap = {1.0, 1.6, 1.8, 2.0, 2.2, 3.5, 4.5};
        int si = 0;
        while (t < 6 && si < snap.length) {
            scene.hairTime += 1 / 30.0;
            off.shot(1);
            t += 1 / 30.0;
            if (t >= snap[si]) { ImageIO.write(off.last, "png", new File(dir, mode + "_" + si + ".png")); System.out.println(String.format(Locale.ROOT, "%.2f s: %s · Gleiten %.1f mm/s", t, m, m.slideMax * 1000)); si++; }
        }
        off.d.destroy();
    }

    /** Kamera für die Fahrt: Abstand d vor der Gruppe (Ziel), Höhe mit dem Abstand. */
    static void crowdCam(StudioScene scene, double[] target, double d) {
        scene.cam.tx = target[0]; scene.cam.ty = target[1]; scene.cam.tz = target[2];
        scene.cam.yawDeg = 90 - 12 * Math.min(1, d / 30);
        scene.cam.pitchDeg = 2 + 16 * Math.min(1, Math.log10(Math.max(1, d)) / 2.5);
        scene.cam.dist = d;
        scene.cam.fovyDeg = 40;
        scene.cam.near = Math.max(0.02, d * 0.02);
        scene.cam.far = Math.max(80, d * 3 + 60);
    }

    /**
     * Statusbild der Detailstufen (Abnahme Phase 12): eine Gruppe von 40 Personen geht; die Kamera fährt in 24 s
     * von 0,5 m vor einem Gesicht auf 300 m zurück. Bildstreifen normal und mit den Stufen in Farbe, Kosten je
     * Abstand mit und ohne Menge, Zahl der Personen je Stufe; dazu ein Video.
     */
    static void mengen(File out, File video, int w, int h) throws Exception {
        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        scene.exposure = 0.85;
        scene.fixedDt = 1 / 30.0;
        Gltf model = loadModel();
        for (int i = 0; i < 2; i++) {
            StudioScene.Figure f = new StudioScene.Figure(new Body(model), loadSkeleton(model), Person.wuerfeln(5 + i), "stehen", 0, 0);
            f.pupilInstant = true;
            f.visible = false;
            scene.figures.add(f);
            scene.pool.add(f);
        }
        com.dan.avatar.Motion.Path route = com.dan.avatar.Motion.line(-4, 0, Math.PI / 2);      // nach +x
        com.dan.avatar.Crowd cr = com.dan.avatar.Crowd.group(40, route, 0, 400, 10, 2026);
        int vw = 960, vh = 540;
        Offscreen off = new Offscreen(scene, vw, vh);
        // Licht für das weite Feld: Mond hoch vorn links (weit weg, breiter Kegel), Kante von hinten, Aufhellung
        LightRig.apply(scene.light, "Bühne", new double[]{0, 1.6, 0});
        float[] kp = {-40, 55, 45};
        System.arraycopy(kp, 0, scene.light.keyPos, 0, 3);
        scene.light.keyDir[0] = 40; scene.light.keyDir[1] = -55; scene.light.keyDir[2] = -45;
        scene.light.keyConeInner = 40; scene.light.keyConeOuter = 60;
        double kd2 = 40 * 40 + 55 * 55 + 45 * 45;
        scene.light.keyCol[0] = (float) (0.75 * kd2); scene.light.keyCol[1] = (float) (0.80 * kd2); scene.light.keyCol[2] = (float) (0.90 * kd2);
        float[] rp = {35, 25, -40};
        System.arraycopy(rp, 0, scene.light.rimPos, 0, 3);
        double rd2 = 35 * 35 + 25 * 25 + 40 * 40;
        scene.light.rimCol[0] = (float) (0.25 * rd2); scene.light.rimCol[1] = (float) (0.55 * rd2); scene.light.rimCol[2] = (float) (0.65 * rd2);
        scene.light.fillCol[0] = 0.05f; scene.light.fillCol[1] = 0.06f; scene.light.fillCol[2] = 0.075f;
        scene.crowd = cr;
        scene.hairTime = 0;
        // erst backen lassen (Gruppe steht noch, die Wege laufen erst mit der Fahrt)
        com.dan.avatar.Crowd.Member lead = cr.members.get(2);
        for (int i = 0; i < 600 && scene.crowdBaked() < cr.members.size(); i++) { crowdCam(scene, new double[]{lead.motion.x, 1.5, lead.motion.z}, 0.5); off.shot(1); Thread.sleep(20); }
        double T = 24;
        int frames = (int) (T * 30);
        double[] snapD = {0.5, 2, 8, 30, 100, 300};
        java.awt.image.BufferedImage[][] snaps = new java.awt.image.BufferedImage[2][6];
        java.util.List<double[]> rec = new java.util.ArrayList<>();
        for (int pass = 0; pass < 2; pass++) {
            scene.lodTint = pass == 1;
            if (pass == 1) {   // gleiche Gruppe von vorn
                cr = com.dan.avatar.Crowd.group(40, route, 0, 400, 10, 2026);
                lead = cr.members.get(2);
                scene.crowd = cr;
                for (int i = 0; i < 600 && scene.crowdBaked() < cr.members.size(); i++) { crowdCam(scene, new double[]{lead.motion.x, 1.5, lead.motion.z}, 0.5); off.shot(1); Thread.sleep(20); }
            }
            for (int i = 0; i < 30; i++) { crowdCam(scene, new double[]{lead.motion.x, 1.55, lead.motion.z}, 0.5); scene.hairTime += 1 / 30.0; off.shot(1); }
            MjpegAvi avi = pass == 0 && video != null ? new MjpegAvi(video, vw, vh, 30, 0.85f) : null;
            int si = 0;
            try {
                for (int i = 0; i < frames; i++) {
                    double t = i / 30.0, u = t / T;
                    double d = 0.5 * Math.pow(600, u);
                    double[] c = cr.centre();
                    double k = smoothstep(0.35, 0.75, u);          // erst ab gut 5 m zur Mitte der Gruppe
                    double[] tg = {lead.motion.x + (c[0] - lead.motion.x) * k, 1.55 - 0.7 * k, lead.motion.z + (c[1] - lead.motion.z) * k};
                    crowdCam(scene, tg, d);
                    scene.hairTime += 1 / 30.0;
                    off.shot(1);
                    if (pass == 0) rec.add(new double[]{d, scene.gpuMs, scene.lodCount[0], scene.lodCount[1], scene.lodCount[2], scene.lodCount[3], scene.msCrowd});
                    if (avi != null) avi.add(off.last);
                    if (si < 6 && d >= snapD[si]) { snaps[pass][si] = copy(off.last); si++; }
                }
            } finally { if (avi != null) avi.close(); }
            for (; si < 6; si++) snaps[pass][si] = copy(off.last);
        }
        // Kosten je Abstand: mit und ohne Menge (stehende Kamera, eingeschwungen)
        scene.lodTint = false;
        double[] costD = {2, 8, 30, 100, 300};
        double[][] cost = new double[costD.length][2];
        for (int q = 0; q < costD.length; q++) {
            double[] c = cr.centre();
            for (int pass = 0; pass < 2; pass++) {
                scene.crowdVisible = pass == 0;
                double sum = 0;
                for (int i = 0; i < 40; i++) {
                    crowdCam(scene, new double[]{c[0], 0.9, c[1]}, costD[q]);
                    off.shot(1);
                    if (i >= 15) sum += scene.gpuMs;
                }
                cost[q][pass] = sum / 25;
            }
            scene.crowdVisible = true;
        }
        double[] tri = scene.crowdTriangles();
        String glInfo = scene.glInfo;
        off.d.destroy();

        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(TEAL);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        g.drawString("AVATAR STUDIO · PHASE 12 · DETAILSTUFEN UND MENGEN", 14, 28);
        g.setColor(MUTED);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.drawString("Eine Gruppe von 40 Personen · Kamerafahrt von 0,5 m auf 300 m in 24 s · oben normal, unten die Stufen in Farbe", 480, 28);
        int tw = 255, th = 143, top = 40;
        String[] lv = {"L0", "L1", "L2", "L3"};
        for (int r = 0; r < 2; r++)
            for (int i = 0; i < 6; i++) {
                int x = 14 + i * (tw + 7), y = top + r * (th + 22);
                drawCrop(g, snaps[r][i], x, y, tw, th);
                caption(g, String.format(Locale.GERMANY, "%s M%s", snapD[i] < 1 ? "0,5" : String.valueOf((int) snapD[i]), r == 1 ? " · STUFEN" : ""), x, y + th + 14, r == 0 ? INK : MUTED);
            }
        // Verlauf: Personen je Stufe und GPU über dem Abstand
        int py = top + 2 * (th + 22) + 10, ph = 250, pw = 760, px = 14;
        plotFrame(g, px, py, pw, ph, "PERSONEN JE STUFE ÜBER DEM ABSTAND (LOG) · L0 WEISS, L1 TÜRKIS, L2 BERNSTEIN, L3 ROT");
        Color[] lc = {INK, TEAL, AMBER, new Color(230, 80, 60)};
        java.util.function.DoubleUnaryOperator X = d -> px + Math.log(d / 0.5) / Math.log(600) * pw;
        for (int k = 0; k < 4; k++) {
            g.setColor(lc[k]);
            g.setStroke(new java.awt.BasicStroke(1.6f));
            int lx = -1, ly = -1;
            for (double[] r : rec) {
                int xx = (int) X.applyAsDouble(r[0]), yy = py + ph - 4 - (int) (r[2 + k] / 40.0 * (ph - 20));
                if (lx >= 0) g.drawLine(lx, ly, xx, yy);
                lx = xx; ly = yy;
            }
        }
        g.setStroke(new java.awt.BasicStroke(1f));
        int px2 = px + pw + 20, pw2 = w - px2 - 14;
        plotFrame(g, px2, py, pw2, ph, "GPU (MS) DER GANZEN SZENE WÄHREND DER FAHRT");
        double gmax = 1;
        for (double[] r : rec) gmax = Math.max(gmax, r[1]);
        g.setColor(TEAL);
        g.setStroke(new java.awt.BasicStroke(1.4f));
        int lx = -1, ly = -1;
        for (double[] r : rec) {
            int xx = px2 + (int) (Math.log(r[0] / 0.5) / Math.log(600) * pw2), yy = py + ph - 4 - (int) (r[1] / gmax * (ph - 24));
            if (lx >= 0) g.drawLine(lx, ly, xx, yy);
            lx = xx; ly = yy;
        }
        g.setStroke(new java.awt.BasicStroke(1f));
        g.setColor(MUTED);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
        for (double d : new double[]{0.5, 2, 10, 50, 300}) {
            int xx = (int) X.applyAsDouble(d);
            g.drawString((d < 1 ? "0,5" : String.valueOf((int) d)) + " m", xx - 8, py + ph + 12);
            int xx2 = px2 + (int) (Math.log(d / 0.5) / Math.log(600) * pw2);
            g.drawString((d < 1 ? "0,5" : String.valueOf((int) d)) + " m", xx2 - 8, py + ph + 12);
        }
        g.drawString(String.format(Locale.GERMANY, "%.1f ms", gmax), px2 + 4, py + 30);
        int ty = py + ph + 34;
        String costS = "";
        for (int q = 0; q < costD.length; q++) costS += String.format(Locale.GERMANY, "%s%.0f m: %.2f (ohne %.2f)", q == 0 ? "" : " · ", costD[q], cost[q][0], cost[q][1]);
        double crowdMs = 0;
        java.util.List<Double> cms = new java.util.ArrayList<>();
        for (int i = 60; i < rec.size(); i++) { crowdMs = Math.max(crowdMs, rec.get(i)[6]); cms.add(rec.get(i)[6]); }
        java.util.Collections.sort(cms);
        double crowdMed = cms.isEmpty() ? 0 : cms.get(cms.size() / 2);
        String[] lines = {
            "GPU je Abstand, Menge sichtbar (ohne): " + costS,
            String.format(Locale.GERMANY, "Netze: L0/L1 voll (14 517 Ecken Körper, Kleidung, Haar), L2 %.0f Dreiecke, L3 %.0f Dreiecke je Person (Mittel)", tri[0], tri[1]),
            String.format(Locale.GERMANY, "Grenzen (Personengröße im Bild): L0 ab %.0f px, L1 ab %.0f px, L2 ab %.0f px; Übergänge über ±10 %% als Rasterblende", scene.lod0, scene.lod1, scene.lod2),
            String.format(Locale.GERMANY, "Höchstens zwei Personen voll (F20); Prozessor für die Menge %.2f ms je Bild im Mittel, höchstens %.2f ms", crowdMed, crowdMs),
            glInfo.length() > 70 ? glInfo.substring(0, 70) : glInfo};
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        for (int i = 0; i < lines.length; i++) { g.setColor(i == 0 ? TEAL : INK); g.drawString(lines[i], 14, ty + i * 17); }
        g.dispose();
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Statusbild Mengen: " + out.getAbsolutePath());
        for (String l : lines) System.out.println("  " + l);
    }

    static double smoothstep(double a, double b, double v) { double t = Math.max(0, Math.min(1, (v - a) / (b - a))); return t * t * (3 - 2 * t); }

    static java.awt.image.BufferedImage copy(java.awt.image.BufferedImage s) {
        java.awt.image.BufferedImage c = new java.awt.image.BufferedImage(s.getWidth(), s.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = c.createGraphics();
        g.drawImage(s, 0, 0, null);
        g.dispose();
        return c;
    }

    static void plotFrame(java.awt.Graphics2D g, int x, int y, int w, int h, String title) {
        g.setColor(new Color(15, 20, 25));
        g.fillRect(x, y, w, h);
        g.setColor(LINE);
        g.drawRect(x, y, w, h);
        for (int s = 10; s < 60; s += 10) { int gx = x + s * w / 60; g.drawLine(gx, y + 1, gx, y + h - 1); }
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 10));
        g.setColor(MUTED);
        g.drawString(title, x + 6, y - 5);
    }

    static void drawSeries(java.awt.Graphics2D g, java.util.List<Sample> s, int x, int y, int w, int h, double secs,
                           java.util.function.ToDoubleFunction<Sample> v, double lo, double hi, Color c) {
        g.setColor(c);
        g.setStroke(new java.awt.BasicStroke(1.3f));
        int lx = -1, ly = -1;
        for (Sample sm : s) {
            int px = x + (int) Math.round(sm.t() / secs * w);
            int py = y + h - 3 - (int) Math.round((v.applyAsDouble(sm) - lo) / (hi - lo) * (h - 6));
            if (lx >= 0) g.drawLine(lx, ly, px, py);
            lx = px; ly = py;
        }
        g.setStroke(new java.awt.BasicStroke(1f));
    }

    /** Entwicklung: die sechs Frisuren einzeln in voller Größe nach {@code dir}. */
    static void haareDev(File dir) throws Exception {
        dir.mkdirs();
        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        scene.exposure = 0.7;
        Gltf model = loadModel();
        Skeleton sk = loadSkeleton(model);
        Person[] six = sechs();
        StudioScene.Figure f = new StudioScene.Figure(new Body(model), sk, six[0], "stehen", 0, 0);
        scene.figures.add(f);
        Offscreen off = new Offscreen(scene, 1200, 900);
        off.shot(3);
        String light = System.getProperty("avatar.licht", "Gegenlicht");
        double wind = Double.parseDouble(System.getProperty("avatar.wind", "2.6"));
        for (int i = 0; i < six.length; i++) {
            f.set(six[i]);
            off.shot(3);
            double[] head = headOf(f);
            if (light.equals("Gegenlicht")) haarLicht(scene, head); else LightRig.apply(scene.light, light, head);
            portraitCam(scene, head, Double.parseDouble(System.getProperty("avatar.gier", "38")), 0.8);
            scene.cam.ty -= 0.06;
            scene.wind = new double[]{wind, 0.1 * wind, 0.35 * wind};
            scene.hairTime = 3.0;
            ImageIO.write(off.shot(20), "png", new File(dir, "haar_" + six[i].hair + ".png"));
            System.out.println(six[i].hair + ": " + f.hairNote() + String.format(Locale.ROOT, " · %.2f ms", scene.msHair));
        }
        off.d.destroy();
    }

    /**
     * Entwicklung: eine Person von vorn ins Bild. Argumente {@code schlüssel=wert}: seed, hair, gender,
     * ageYears, height, proportions, weight, muscle, yaw, dist, name (Dateiname), live (0/1).
     */
    static void probe(File dir, String[] kv) throws Exception {
        dir.mkdirs();
        java.util.Map<String, String> a = new java.util.HashMap<>();
        for (String s : kv) { int i = s.indexOf('='); if (i > 0) a.put(s.substring(0, i), s.substring(i + 1)); }
        Person p = Person.wuerfeln(Long.parseLong(a.getOrDefault("seed", "11"))).copy();
        if (a.containsKey("hair")) p.hair = a.get("hair");
        if (a.containsKey("gender")) p.gender = Double.parseDouble(a.get("gender"));
        if (a.containsKey("ageYears")) p.age = Person.ageFromYears(Double.parseDouble(a.get("ageYears")));
        if (a.containsKey("height")) p.height = Double.parseDouble(a.get("height"));
        if (a.containsKey("proportions")) p.proportions = Double.parseDouble(a.get("proportions"));
        if (a.containsKey("weight")) p.weight = Double.parseDouble(a.get("weight"));
        if (a.containsKey("muscle")) p.muscle = Double.parseDouble(a.get("muscle"));
        for (String k : new String[]{"melanin", "freckles", "iris", "hairColor", "beard", "redness", "oil", "hairRed"})
            if (a.containsKey(k)) { try { Person.class.getField(k).setDouble(p, Double.parseDouble(a.get(k))); } catch (ReflectiveOperationException e) { throw new RuntimeException(e); } }
        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        scene.exposure = 0.8;
        scene.fixedDt = 1 / 30.0;
        Gltf model = loadModel();
        StudioScene.Figure f = new StudioScene.Figure(new Body(model), loadSkeleton(model), p, "stehen", 0, 0);
        f.behaviorOn = !"0".equals(a.getOrDefault("live", "0"));
        if ("0".equals(a.get("sway"))) scene.clothSwayOn = false;
        if (a.containsKey("outfit")) f.outfit = com.dan.avatar.Outfit.preset(a.get("outfit").replace('_', ' '));
        scene.figures.add(f);
        Offscreen off = new Offscreen(scene, 1200, 900);
        scene.hairTime = 1;
        off.shot(3);
        double[] head = headOf(f);
        LightRig.apply(scene.light, a.getOrDefault("licht", "Porträt"), head);
        portraitCam(scene, head, Double.parseDouble(a.getOrDefault("yaw", "0")), Double.parseDouble(a.getOrDefault("dist", "0.8")));
        scene.cam.ty -= 0.05;
        if (a.containsKey("env")) {
            com.dan.avatar.Climate c = switch (a.get("env")) {
                case "sturm" -> com.dan.avatar.Climate.sturm();
                case "winter" -> com.dan.avatar.Climate.wintermorgen();
                case "sommer" -> com.dan.avatar.Climate.sommerabend();
                default -> com.dan.avatar.Climate.regennacht();
            };
            if (a.containsKey("hood")) c.hood = Double.parseDouble(a.get("hood"));
            if (a.containsKey("rain")) c.rain = Double.parseDouble(a.get("rain"));
            scene.climate = c;
            scene.wind = new double[]{c.wind[0], 0.15 * c.windSpeed(), c.wind[2]};
            double secs = Double.parseDouble(a.getOrDefault("secs", "120"));
            scene.envSpeed = secs / 4.0;
            if ("frei".equals(a.get("look")) && f.behavior != null) f.behavior.look(com.dan.avatar.Behavior.Look.FREE);
            for (int i = 0; i < 120; i++) { scene.hairTime = 1 + i / 30.0; off.shot(1); }
            scene.envSpeed = 1;
            for (int i = 0; i < 30; i++) { scene.hairTime = 5 + i / 30.0; off.shot(1); }
            System.out.println(f.wetness + " · Perlen " + f.drops.beads().size() + " · Treffer " + f.drops.hits + " · " + c);
        }
        if (a.containsKey("shot")) {
            if (a.containsKey("f")) scene.lens.focalMm = Double.parseDouble(a.get("f"));
            if (a.containsKey("N")) scene.lens.fNumber = Double.parseDouble(a.get("N"));
            if (a.containsKey("grade")) scene.grade = a.get("grade");
            if (a.containsKey("bokeh")) scene.bokehStyle = a.get("bokeh");
            if (a.containsKey("bars")) scene.bars = true;
            if (a.containsKey("envrefl")) scene.envReflect = !"0".equals(a.get("envrefl"));
            if (a.containsKey("exp")) scene.exposure = Double.parseDouble(a.get("exp"));
            portraitShot(scene, f, com.dan.portrait.Shot.valueOf(a.get("shot")), Double.parseDouble(a.getOrDefault("yaw", "0")), a.getOrDefault("licht", "Regennacht"));
            for (int i = 0; i < 20; i++) { scene.hairTime += 1 / 30.0; off.shot(1); }
            System.out.println(scene.lens + " · Abstand " + String.format(Locale.ROOT, "%.2f", scene.cam.dist) + " m · GPU " + String.format(Locale.ROOT, "%.2f", scene.gpuMs) + " ms");
        }
        String name = a.getOrDefault("name", "probe");
        ImageIO.write(off.shot(10), "png", new File(dir, name + ".png"));
        System.out.println(name + ": " + p + " · " + f.hairNote());
        off.d.destroy();
    }

    static void eyeCam(StudioScene scene, double[] c, double yaw, double dist) {
        scene.cam.tx = c[0]; scene.cam.ty = c[1]; scene.cam.tz = c[2];
        scene.cam.yawDeg = yaw; scene.cam.pitchDeg = 3; scene.cam.dist = dist; scene.cam.fovyDeg = 28;
    }

    /** Entwicklung: einzelne Nahaufnahmen in voller Größe nach {@code dir}. */
    static void hautDev(File dir) throws Exception {
        dir.mkdirs();
        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        Gltf model = loadModel();
        Skeleton sk = loadSkeleton(model);
        Person mann = Person.wuerfeln(23), frau = Person.wuerfeln(11);
        StudioScene.Figure f = new StudioScene.Figure(new Body(model), sk, mann, "stehen", 0, 0);
        f.hidden.add("bob02");
        scene.exposure = 0.7;
        System.out.println("Mann: Bart " + mann.beard + ", Melanin " + mann.melanin + ", Sommersprossen " + mann.freckles);
        scene.figures.add(f);
        Offscreen off = new Offscreen(scene, 1200, 900);
        off.shot(2);
        double[] head = headOf(f);
        String[][] shots = {{"gegenlicht", "Gegenlicht", "48", "0.5"}, {"mann", "Porträt", "20", "0.42"}, {"frau_hell", "Porträt", "-12", "0.42"}};
        for (String[] s : shots) {
            if (s[0].equals("frau_hell")) {
                f.hidden.clear();
                Person hell = frau.copy(); hell.melanin = 0.08; hell.freckles = 0.85; hell.redness = 0.6;
                f.set(hell);
                off.shot(2);
                head = headOf(f);
            }
            LightRig.apply(scene.light, s[1], head);
            portraitCam(scene, head, Double.parseDouble(s[2]), Double.parseDouble(s[3]));
            scene.cam.ty -= 0.04;
            ImageIO.write(off.shot(20), "png", new File(dir, s[0] + ".png"));
        }
        off.d.destroy();
        System.out.println("Nahaufnahmen: " + dir.getAbsolutePath());
    }

    /** Augenmitte der Person (Welt) oder, solange die Augen noch nicht gerechnet sind, die Kopfmitte. */
    static double[] eyeMid(StudioScene.Figure f) {
        com.dan.avatar.EyeRig.Frame[] e = f.eyes;
        if (e == null) { double[] h = headOf(f); h[1] -= 0.05; return h; }
        return new double[]{(e[0].c[0] + e[1].c[0]) / 2 + f.x, (e[0].c[1] + e[1].c[1]) / 2 + f.baseY(), (e[0].c[2] + e[1].c[2]) / 2 + f.z};
    }

    /**
     * Porträt einrichten (Phase 9): Bild-Werkzeuge an, Kamera für die Einstellung {@code shot} mit dem
     * Bildwinkel des Objektivs auf die Augen gerichtet (von vorn um {@code yaw} gedreht), Lichtsetzung
     * {@code light} aus portrait im Rahmen der Kamera.
     */
    static void portraitShot(StudioScene scene, StudioScene.Figure f, com.dan.portrait.Shot shot, double yaw, String light) {
        scene.portraitOn = true;
        scene.lensFov = true;
        double[] e = eyeMid(f);
        double fov = scene.lens.fovyDeg();
        scene.cam.fovyDeg = fov;
        scene.cam.tx = e[0]; scene.cam.ty = e[1] + shot.offsetY; scene.cam.tz = e[2];
        scene.cam.yawDeg = yaw; scene.cam.pitchDeg = 1;
        scene.cam.dist = shot.distance(fov);
        scene.lens.focusM = scene.cam.dist;
        double[] head = headOf(f);
        LightRig.applySet(scene.light, light, head, scene.cam.eye());
    }

    static void portraitCam(StudioScene scene, double[] head, double yaw, double dist) {
        scene.cam.tx = head[0]; scene.cam.ty = head[1] - 0.02; scene.cam.tz = head[2];
        scene.cam.yawDeg = yaw; scene.cam.pitchDeg = 2; scene.cam.dist = dist; scene.cam.fovyDeg = 28;
    }

    /** Mittleren Ausschnitt im Seitenverhältnis des Ziels zeichnen. */
    static void drawCrop(java.awt.Graphics2D g, java.awt.image.BufferedImage src, int x, int y, int w, int h) {
        double a = (double) w / h;
        int sh = src.getHeight(), sw = (int) Math.round(sh * a);
        if (sw > src.getWidth()) { sw = src.getWidth(); sh = (int) Math.round(sw / a); }
        int sx = (src.getWidth() - sw) / 2, sy = (src.getHeight() - sh) / 2;
        g.drawImage(src, x, y, x + w, y + h, sx, sy, sx + sw, sy + sh, null);
        g.setColor(LINE);
        g.drawRect(x, y, w, h);
    }

    static void caption(java.awt.Graphics2D g, String s, int x, int y, Color c) {
        g.setColor(c);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 11));
        g.drawString(s, x, y);
    }

    /**
     * Statusbild der Posen: fünf Posen an Frau und Mann, dazu der gedrehte Unterarm groß mit
     * Dual-Quaternion und linear. Prüft dabei Grafikkarte gegen Prozessor.
     */
    static void bild(File out, int w, int h) throws Exception {
        StudioScene scene = new StudioScene();
        scene.hud.body = summary();
        Gltf model = loadModel();
        Skeleton sk = loadSkeleton(model);
        Person frau = Person.wuerfeln(11), mann = Person.wuerfeln(23);
        String[] poses = {"stehen", "sitzen", "lehnen", "hand_ans_gesicht", "unterarm_drehen"};
        for (int i = 0; i < poses.length; i++) {
            Person p = i % 2 == 0 ? frau : mann;
            scene.figures.add(new StudioScene.Figure(new Body(model), sk, p, poses[i], (i - 2) * 1.0f, 0));
        }
        scene.cam.yawDeg = 6; scene.cam.pitchDeg = 5; scene.cam.dist = 7.9; scene.cam.ty = 0.88;
        Offscreen off = new Offscreen(scene, w, h);
        java.awt.image.BufferedImage main = off.shot(60);
        double gpuMs = scene.gpuMs;
        String glInfo = scene.glInfo;

        // GPU gegen CPU: Person 0 (stehen) und Person 4 (unterarm_drehen)
        double[] diff = new double[2];
        for (int fi : new int[]{0, 4}) {
            StudioScene.Figure f = scene.figures.get(fi);
            float[][] gpuP = new float[f.body.parts.size()][];
            off.inGl = () -> {
                com.jogamp.opengl.GL4 gl = com.jogamp.opengl.GLContext.getCurrentGL().getGL4();
                for (int k = 0; k < gpuP.length; k++) gpuP[k] = f.gl().readback(gl, k, false, true);
            };
            off.shot(1);
            java.util.Map<String, Double> wts = f.person.weights();
            f.body.mix(wts);
            double[][] rest = sk.restFor(wts);
            double[][] sm = Skeleton.skin(sk.pose(sk.poses.get(f.pose), rest), rest);
            float[][] cp = new float[f.body.parts.size()][], cn = new float[f.body.parts.size()][];
            Skinning.apply(f.body, Skeleton.toDq(sm), Skeleton.toMat(sm), true, cp, cn);
            double mx = 0;
            for (int k = 0; k < cp.length; k++)
                for (int i = 0; i < cp[k].length; i++) mx = Math.max(mx, Math.abs(cp[k][i] - gpuP[k][i]));
            diff[fi == 0 ? 0 : 1] = mx;
        }
        System.out.println(String.format(Locale.ROOT, "GPU gegen CPU (Morphs + Skinning, alle Netze): stehen %.6f mm, unterarm_drehen %.6f mm", diff[0] * 1000, diff[1] * 1000));

        // Unterarm groß: Dual-Quaternion gegen linear
        scene.hudOn = false;
        StudioScene.Figure tw = scene.figures.get(4);
        double[] hand = Skeleton.position(tw.jointWorld, sk.joint("hand_l"));
        // Handgelenk (Unterarm 45° + Hand 70° gedreht): dort zeigt lineares Skinning den „Bonbon“-Knick.
        // Die Hand hängt neben der Hose: dunkler Grund, der Ärmel endet über dem Gelenk.
        scene.cam.tx = tw.x + hand[0];
        scene.cam.ty = hand[1] + tw.baseY() - 0.035;
        scene.cam.tz = tw.z + hand[2];
        scene.cam.yawDeg = 32; scene.cam.pitchDeg = 4; scene.cam.dist = 0.42; scene.cam.fovyDeg = 30;
        java.awt.image.BufferedImage dq = off.shot(3);
        tw.setDualQuaternion(false);
        java.awt.image.BufferedImage lin = off.shot(3);
        off.d.destroy();

        // zusammensetzen
        int iw = 280, ih = 196;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.drawImage(main, 0, 0, null);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        int x0 = w - iw - 16;
        for (int k = 0; k < 2; k++) {
            int y0 = h - 2 * (ih + 30) - 6 + k * (ih + 30);
            g.drawImage(k == 0 ? dq : lin, x0, y0, iw, ih, null);
            g.setColor(new Color(37, 49, 58));
            g.drawRect(x0, y0, iw, ih);
            g.setColor(k == 0 ? TEAL : new Color(224, 163, 63));
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
            g.drawString(k == 0 ? "UNTERARM GEDREHT · DUAL-QUATERNION" : "UNTERARM GEDREHT · LINEAR (KNICKT EIN)", x0, y0 + ih + 16);
        }
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.setColor(new Color(236, 238, 240));
        for (int i = 0; i < poses.length; i++) {
            String t = poses[i].replace('_', ' ');
            double half = Math.tan(Math.toRadians(15)) * 7.9 * w / (double) h;
            int sx = (int) (w / 2.0 + (i - 2) / half * (w / 2.0));
            g.drawString(t, sx - t.length() * 4, h - 22);
        }
        g.dispose();
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Statusbild: " + out.getAbsolutePath() + " (" + w + " × " + h + ")");
        System.out.println("Karte: " + glInfo);
        System.out.println(String.format(Locale.ROOT, "GPU: %.2f ms (5 Personen)", gpuMs));
    }

    /** Bildtafel: die 52 Gesichtsformen an einer Frau und einem Mann (Abnahme Phase 3). */
    static void tafel(File out) throws Exception {
        int tw = 168, th = 190, cols = 13;
        StudioScene scene = new StudioScene();
        scene.hudOn = false;
        Gltf model = loadModel();
        Skeleton sk = loadSkeleton(model);
        Person frau = Person.wuerfeln(11), mann = Person.wuerfeln(23);
        StudioScene.Figure f = new StudioScene.Figure(new Body(model), sk, frau, "stehen", 0, 0);
        f.hidden.add("bob02");          // Frisur aus, damit Stirn und Augen frei sind
        scene.figures.add(f);
        scene.exposure = 0.62;
        Offscreen off = new Offscreen(scene, tw, th);
        int rowsPer = (ARKIT.length + cols - 1) / cols;
        int head = 70, gap = 34;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(cols * tw, head + 2 * (rowsPer * (th + 18) + gap), java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(TEAL);
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
        g.drawString("AVATAR STUDIO · PHASE 3 · DIE 52 GESICHTSFORMEN (ARKit, faceunits01) AN FRAU UND MANN · STÄRKE 1,0 · OHNE FRISUR", 14, 26);
        g.setColor(MUTED);
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.drawString(frau + "  |  " + mann, 14, 48);
        int y = head;
        for (Person base : new Person[]{frau, mann}) {
            for (int i = 0; i < ARKIT.length; i++) {
                Person p = base.copy();
                p.forms.put("arkit." + ARKIT[i], 1.0);
                f.set(p);
                if (i == 0) {
                    off.shot(2);
                    double[] hd = Skeleton.position(f.jointWorld, sk.joint("head"));
                    scene.cam.tx = hd[0]; scene.cam.ty = hd[1] + f.baseY() + 0.03; scene.cam.tz = hd[2] + 0.06;
                    scene.cam.yawDeg = 0; scene.cam.pitchDeg = 1; scene.cam.dist = 0.62; scene.cam.fovyDeg = 17;
                }
                java.awt.image.BufferedImage t = off.shot(2);
                int cx = (i % cols) * tw, cy = y + (i / cols) * (th + 18);
                g.drawImage(t, cx, cy, null);
                g.setColor(new Color(236, 238, 240));
                g.drawString(ARKIT[i], cx + 6, cy + th + 13);
            }
            y += rowsPer * (th + 18) + gap;
        }
        off.d.destroy();
        g.dispose();
        out.getAbsoluteFile().getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("Formtafel: " + out.getAbsolutePath() + " (" + img.getWidth() + " × " + img.getHeight() + ")");
    }
}
