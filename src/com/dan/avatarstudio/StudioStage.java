package com.dan.avatarstudio;

import com.dan.avatar.Actor;
import com.dan.avatar.Body;
import com.dan.avatar.Climate;
import com.dan.avatar.Motion;
import com.dan.avatar.Outfit;
import com.dan.avatar.Person;
import com.dan.avatar.Regie;
import com.dan.avatar.World;
import com.dan.gltf.Gltf;

import java.util.List;
import java.util.Locale;

/**
 * Die Szene des Studios für Regiebücher (Phase 13): {@code szene} (Steg, Bühne), {@code person},
 * {@code kamera} / {@code ansicht}, {@code wetter}, {@code bild}, {@code licht}; {@code warte vorspann}
 * hält, bis der Titel des Kinos aufgeblendet ist. So bindet jede Szene die Regie an: die Befehle der Personen
 * führt {@link Actor} aus, alles andere landet hier.
 *
 * <pre>
 * person  mara Strickkapuze vorlage=frau ort=Ufer dreh=Steg_Ende    (oder seed=4711 frau|mann alter=31)
 * kamera  Halbnah mara winkel=30 folgt=ja                           (Totale, Halbtotale, Amerikanisch, Halbnah, Nah, Groß, frei)
 * wetter  Regennacht nass=12                                        (Minuten Regen vorab)
 * bild    an                                                        (Schärfentiefe, Lichter, Farbe)
 * </pre>
 */
public final class StudioStage implements Regie.Stage {

    final StudioScene scene;
    final Gltf model;
    /** Personen, die die Regie angelegt hat (die Szene räumt sie am Ende wieder ab). */
    public final java.util.List<StudioScene.Figure> created = new java.util.ArrayList<>();

    public StudioStage(StudioScene scene, Gltf model) { this.scene = scene; this.model = model; }

    @Override public World world() { return scene.world; }

    @Override public boolean done(String verb) {
        return switch (verb) {
            case "vorspann" -> scene.afterTitle();
            default -> true;
        };
    }

    @Override public boolean command(String verb, String args, Regie regie) {
        List<String> w = Regie.words(args);
        try {
            switch (verb) {
                case "szene" -> {
                    String n = w.isEmpty() ? "" : w.get(0).toLowerCase(Locale.ROOT);
                    if (n.equals("steg")) { if (scene.set == null) scene.set = new JettySet(); }
                    else if (n.startsWith("b")) scene.set = null;
                    else return false;
                    return true;
                }
                case "person" -> { return person(w, regie); }
                case "kamera", "ansicht" -> {
                    if (w.isEmpty()) return false;
                    StudioScene.Figure who = w.size() > 1 && !w.get(1).contains("=") ? figure(regie, w.get(1)) : null;
                    String a = Regie.option(w, "winkel", null), fo = Regie.option(w, "folgt", null);
                    Double ang = a == null ? null : Double.parseDouble(a.replace(',', '.'));
                    Boolean follow = fo == null ? null : fo.startsWith("j");
                    String name = w.get(0);
                    if (!java.util.Arrays.asList(CameraRig.NAMES).stream().anyMatch(s -> s.equalsIgnoreCase(name) || s.replace("ß", "ss").equalsIgnoreCase(name))) return false;
                    scene.cut(() -> scene.rig.set(name, who, ang, follow));
                    return true;
                }
                case "wetter" -> {
                    if (w.isEmpty()) return false;
                    Climate c = switch (w.get(0).toLowerCase(Locale.ROOT)) {
                        case "regennacht" -> Climate.regennacht();
                        case "sturm" -> Climate.sturm();
                        case "wintermorgen" -> Climate.wintermorgen();
                        case "sommerabend" -> Climate.sommerabend();
                        case "trocken" -> Climate.of(0, 1, 14, 0.7, 0);
                        default -> null;
                    };
                    if (c == null) return false;
                    scene.climate = c;
                    double min = Double.parseDouble(Regie.option(w, "nass", "0").replace(',', '.'));
                    if (min > 0) for (StudioScene.Figure f : scene.figures) prewet(f, c, min);
                    pendingWet = min;
                    return true;
                }
                case "bild" -> {
                    boolean on = w.isEmpty() || w.get(0).equalsIgnoreCase("an");
                    scene.portraitOn = on;
                    scene.lensFov = false;
                    String g = Regie.option(w, "farbe", null);
                    if (g != null) scene.grade = g;
                    String b = Regie.option(w, "blende", null);
                    if (b != null) scene.lens.fNumber = Double.parseDouble(b.replace(',', '.'));
                    return true;
                }
                case "licht" -> {
                    if (w.isEmpty()) return false;
                    scene.setLight = w.get(0).equalsIgnoreCase("laternen");
                    return true;
                }
                default -> { return false; }
            }
        } catch (Exception e) {
            regie.errors.add(verb + ": " + e.getMessage());
            return true;
        }
    }

    private double pendingWet;

    /** Nässe vorab: {@code min} Minuten im Klima {@code c} (Kapuze des Outfits schützt). */
    static void prewet(StudioScene.Figure f, Climate c, double min) {
        Climate cf = c;
        if (f.outfit.hoodUp() && c.hood < 1) { cf = c.copy(); cf.hood = 1; }
        for (int i = 0; i < min * 60; i++) f.wetness.step(1, cf);
    }

    /** {@code person <name> [Outfit] [vorlage=frau|mann] [seed=N] [frau|mann] [alter=J] [ort=Ort] [dreh=Ort]}. */
    private boolean person(List<String> w, Regie regie) throws java.io.IOException {
        if (w.isEmpty()) return false;
        String name = w.get(0);
        String vor = Regie.option(w, "vorlage", null);
        Person p;
        if ("frau".equalsIgnoreCase(vor)) p = AvatarStudio.templateWoman();
        else if ("mann".equalsIgnoreCase(vor)) p = AvatarStudio.templateMan();
        else p = Person.wuerfeln(Long.parseLong(Regie.option(w, "seed", String.valueOf(Math.abs(name.hashCode()))))).copy();
        for (String s : w) {
            if (s.equalsIgnoreCase("frau")) p.gender = Math.min(p.gender, 0.12);
            if (s.equalsIgnoreCase("mann")) p.gender = Math.max(p.gender, 0.88);
        }
        String al = Regie.option(w, "alter", null);
        if (al != null) p.age = Person.ageFromYears(Double.parseDouble(al));
        p.name = name;
        Outfit o = Outfit.preset("Alltag");
        for (int i = 1; i < w.size(); i++) {
            String s = w.get(i);
            if (s.contains("=")) continue;
            for (String pr : Outfit.PRESETS) if (pr.equalsIgnoreCase(s)) o = Outfit.preset(pr);
        }
        String kap = Regie.option(w, "kapuze", null);
        if (kap != null) o = o.withHood(kap.equalsIgnoreCase("auf"));
        World wo = scene.world;
        double[] at = place(wo, Regie.option(w, "ort", null), new double[]{0, 0, 0});
        double[] to = place(wo, Regie.option(w, "dreh", null), null);
        double yaw = to == null ? 0 : Math.atan2(to[0] - at[0], to[2] - at[2]);
        StudioScene.Figure f = new StudioScene.Figure(new Body(model), AvatarStudio.loadSkeleton(model), p, "stehen", (float) at[0], (float) at[2]);
        f.outfit = o;
        f.motion = new Motion(Motion.line(at[0], at[2], yaw), 0);
        Actor a = new Actor(name, f);
        f.actor = a;
        regie.cast.put(name, a);
        if (pendingWet > 0) prewet(f, scene.climate, pendingWet);
        created.add(f);
        scene.figures.add(f);
        return true;
    }

    static double[] place(World wo, String n, double[] def) {
        if (n == null) return def;
        double[] p = wo.place(n);
        return p == null ? def : p;
    }

    StudioScene.Figure figure(Regie regie, String name) {
        Actor a = regie.cast.get(name);
        if (a == null) for (var e : regie.cast.entrySet()) if (e.getKey().equalsIgnoreCase(name)) a = e.getValue();
        return a != null && a.host instanceof StudioScene.Figure f ? f : null;
    }

    /** Personen der Regie wieder abräumen. */
    public void clear() {
        for (StudioScene.Figure f : created) scene.figures.remove(f);
        created.clear();
    }
}
