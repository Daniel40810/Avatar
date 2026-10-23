package com.dan.avatar;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Eine Person in der Regie (Phase 13): nimmt die Befehle eines Regiebuchs an ({@code mara.blick Wasser}),
 * gibt sie an Verhalten, Bewegung und Kleidung weiter und meldet, wann jeder Befehl fertig ist, damit
 * {@code warte mara.gehe} warten kann. Die Szene stellt die Person über {@link Host}; der Avatar kennt
 * die Szene nur über {@link World}.
 *
 * <p>Befehle (Werkbuch Abschnitt 6): {@code blick} (Kamera, Horizont, frei, Ort, Person, x z),
 * {@code gefuehl} (Name, Stärke, Dauer), {@code geste} (winken, zeigen, buecken, Kapuze_auf, Kapuze_ab),
 * {@code pose} (stehen, sitzen), {@code gehe} (Ort oder x z, Tempo), {@code dreh} (Ort, Person, Kamera
 * oder Grad), {@code outfit} (Preset), {@code kapuze} (auf, ab, sofort), {@code sage} ("Text", mit Mund, wenn die Szene sprechen kann, als
 * Untertitel; Lippen in der Kür), {@code halt} (stehen bleiben).
 */
public final class Actor {

    /** Was die Szene für eine Person bereitstellt. */
    public interface Host {
        /** Verhalten (Blick, Gefühl); {@code null}, solange die Person noch nicht gerechnet wurde. */
        Behavior behavior();
        /** Bewegung; legt sie an, wenn es noch keine gibt (Person steht dann am Ort). */
        Motion motion();
        Outfit outfit();
        void outfit(Outfit o);
        /** Kopf in Weltkoordinaten oder {@code null}. */
        double[] head();
        /** Bank (Sitzfläche) hinter der Person aufstellen, bevor sie sich setzt. */
        default void seat() { }
        /**
         * Phase 16: einen Satz sprechen (Mund, Atem, Lidschlag führt die Szene, z. B. mit {@code com.dan.voice});
         * Dauer in s oder −1, wenn die Szene nicht sprechen kann (dann nur Untertitel).
         */
        default double say(String text) { return -1; }
    }

    public final String name;
    public final Host host;
    /** Fehler beim letzten Befehl (oder {@code null}). */
    public String error;
    /** Untertitel aus {@code sage} (die Regie leitet ihn an die Szene weiter). */
    public String said;

    // Blick, wie die Regie ihn will: null = von selbst (beim Gehen voraus)
    private String lookMode;
    private double[] lookWorld;
    private Actor lookActor;
    private final Map<String, Object> last = new HashMap<>();
    private double clock, sayUntil = -1, feelUntil = -1, lookSince = -1, feelSince = -1;

    public Actor(String name, Host host) { this.name = name; this.host = host; }

    /** Zeit weiter (für {@code sage} und Gefühle mit Dauer). */
    public void step(double dt) {
        clock += Math.max(0, dt);
        if (feelUntil >= 0 && clock >= feelUntil) {
            Behavior b = host.behavior();
            if (b != null) b.feel("ruhig", 0.5);
            feelUntil = -1;
        }
    }

    /**
     * Blickziel in Weltkoordinaten für dieses Bild ({@code null}: die Szene entscheidet, z. B. beim Gehen
     * voraus). Folgt einer anderen Person, wenn sie sich bewegt.
     */
    public double[] gazeTarget() {
        if (lookActor != null) { double[] h = lookActor.host.head(); return h == null ? null : h.clone(); }
        return lookWorld == null ? null : lookWorld.clone();
    }

    /** Hat die Regie den Blick festgelegt (dann schaut die Person auch beim Gehen dorthin)? */
    public boolean gazeDirected() { return lookMode != null && !lookMode.equals("frei"); }

    /** Momentaufnahme mit dem Namen der Regie. */
    public Snapshot snapshot(World world, double x, double z, Wetness wetness) {
        Motion m = host.motion();
        double gx = m != null ? m.x : x, gz = m != null ? m.z : z;
        return Snapshot.of(name, world == null ? 0 : world.ground(gx, gz), m, host.behavior(), wetness, host.outfit(), x, z, host.head(), gazeTarget());
    }

    // ------------------------------------------------------------ Befehle

    /**
     * Befehl ausführen. {@code args}: der Rest der Zeile ohne Untertitel. Gibt {@code false} zurück, wenn der
     * Befehl unbekannt oder falsch ist ({@link #error} sagt warum).
     */
    public boolean command(String verb, String args, World world, Map<String, Actor> cast) {
        error = null;
        List<String> a = Regie.words(args);
        Behavior b = host.behavior();
        try {
            switch (norm(verb)) {
                case "blick" -> {
                    if (a.isEmpty()) return fail("blick: Ziel fehlt");
                    String t = norm(a.get(0));
                    lookActor = null; lookWorld = null;
                    switch (t) {
                        case "kamera" -> { lookMode = "kamera"; if (b != null) b.look(Behavior.Look.CAMERA); }
                        case "horizont" -> { lookMode = "horizont"; if (b != null) b.look(Behavior.Look.HORIZON); }
                        case "frei" -> { lookMode = "frei"; if (b != null) b.look(Behavior.Look.FREE); }
                        default -> {
                            Actor other = find(cast, a.get(0));
                            if (other != null && other != this) { lookActor = other; lookMode = "person"; }
                            else {
                                double[] p = place(world, a, 0);
                                if (p == null) return fail("blick: Ort unbekannt: " + a.get(0));
                                lookWorld = p; lookMode = "punkt";
                            }
                        }
                    }
                    lookSince = clock;
                    return true;
                }
                case "gefuehl" -> {
                    if (a.isEmpty()) return fail("gefuehl: Name fehlt");
                    String n = umlaut(a.get(0).toLowerCase(Locale.ROOT));
                    if (Emotion.named(n) == null) return fail("gefuehl: unbekannt: " + a.get(0));
                    double st = a.size() > 1 ? num(a.get(1)) : 0.8;
                    if (b == null) return fail("gefuehl: Person noch nicht bereit");
                    b.feel(n, st);
                    feelUntil = a.size() > 2 ? clock + seconds(a.get(2)) : -1;
                    feelSince = clock;
                    return true;
                }
                case "geste" -> {
                    if (a.isEmpty()) return fail("geste: Name fehlt");
                    String g = norm(a.get(0));
                    double d = a.size() > 1 ? seconds(a.get(1)) : 2.5;
                    Motion m = host.motion();
                    Motion.Cmd c = switch (g) {
                        case "winken" -> Motion.wave(d);
                        case "zeigen" -> Motion.point(d);
                        case "buecken", "bücken" -> Motion.bend(d);
                        case "kapuze_auf", "kapuzeauf" -> host.outfit().hasHood() ? Motion.hood(true) : null;
                        case "kapuze_ab", "kapuzeab" -> host.outfit().hasHood() ? Motion.hood(false) : null;
                        default -> null;
                    };
                    if (c == null) return fail("geste: unbekannt oder ohne Kapuze: " + a.get(0));
                    m.then(c);
                    last.put("geste", c);
                    return true;
                }
                case "pose" -> {
                    if (a.isEmpty()) return fail("pose: Name fehlt");
                    Motion m = host.motion();
                    Motion.Cmd c;
                    switch (norm(a.get(0))) {
                        case "sitzen" -> { host.seat(); c = Motion.sit(0.8); }
                        case "stehen", "aufstehen" -> c = m.sit > 0.5 ? Motion.standUp() : Motion.stand(0.2);
                        default -> { return fail("pose: unbekannt: " + a.get(0)); }
                    }
                    m.then(c);
                    last.put("pose", c);
                    return true;
                }
                case "gehe" -> {
                    if (a.isEmpty()) return fail("gehe: Ziel fehlt");
                    double[] p = place(world, a, 0);
                    int used = p != null && isNum(a.get(0)) ? 2 : 1;
                    if (p == null) { Actor o = find(cast, a.get(0)); if (o != null && o.host.head() != null) p = o.host.head(); }
                    if (p == null) return fail("gehe: Ort unbekannt: " + a.get(0));
                    double tempo = a.size() > used ? num(a.get(used)) : 1;
                    Motion m = host.motion();
                    Motion.Cmd c = Motion.goTo(p[0], p[2], tempo);
                    m.then(c);
                    last.put("gehe", c);
                    return true;
                }
                case "dreh" -> {
                    if (a.isEmpty()) return fail("dreh: Ziel fehlt");
                    Motion m = host.motion();
                    Motion.Cmd c;
                    String t = norm(a.get(0));
                    if (a.size() == 1 && isNum(a.get(0))) c = Motion.turn(Math.toRadians(num(a.get(0))));
                    else {
                        double[] p = t.equals("kamera") && world != null ? world.camera() : null;
                        if (p == null) { Actor o = find(cast, a.get(0)); if (o != null && o != this) p = o.host.head(); }
                        if (p == null) p = place(world, a, 0);
                        if (p == null) return fail("dreh: Ziel unbekannt: " + a.get(0));
                        c = Motion.turnTo(p[0], p[2]);
                    }
                    m.then(c);
                    last.put("dreh", c);
                    return true;
                }
                case "halt" -> { host.motion().interrupt(); return true; }
                case "outfit" -> {
                    if (a.isEmpty()) return fail("outfit: Name fehlt");
                    Outfit o = Outfit.preset(a.get(0));
                    if (o.name.equals("ohne") && !a.get(0).equals("ohne")) return fail("outfit: unbekannt: " + a.get(0));
                    host.outfit(o);
                    return true;
                }
                case "kapuze" -> {
                    if (a.isEmpty()) return fail("kapuze: auf oder ab");
                    boolean up = norm(a.get(0)).equals("auf");
                    host.outfit(host.outfit().withHood(up));
                    return true;
                }
                case "sage" -> {
                    said = args.trim();
                    if (said.length() > 1 && said.startsWith("\"") && said.endsWith("\"")) said = said.substring(1, said.length() - 1);
                    int words = said.isBlank() ? 0 : said.trim().split("\\s+").length;
                    double spoken = host.say(said);
                    // Sprechdauer aus der Szene (Phase 16) oder geschätzt [G]: rund 2,6 Wörter je Sekunde
                    sayUntil = clock + (spoken > 0 ? spoken + 0.15 : 1.2 + 0.38 * words);
                    return true;
                }
                default -> { return fail("unbekannter Befehl: " + verb); }
            }
        } catch (NumberFormatException e) {
            return fail(verb + ": Zahl erwartet (" + e.getMessage() + ")");
        }
    }

    /** Ist der zuletzt gegebene Befehl dieser Art fertig ({@code warte mara.gehe})? Leer: alles fertig. */
    public boolean done(String verb) {
        if (verb == null || verb.isBlank()) {
            for (String v : new String[]{"blick", "gefuehl", "geste", "pose", "gehe", "dreh", "sage"}) if (!done(v)) return false;
            return true;
        }
        Behavior b = host.behavior();
        switch (norm(verb)) {
            case "blick": return lookSince < 0 || (clock - lookSince > 0.15 && (b == null || b.done("blick")));
            case "gefuehl": return feelSince < 0 || (clock - feelSince > 0.15 && (b == null || b.done("gefuehl")));
            case "sage": return clock >= sayUntil;
            case "geste": case "pose": case "gehe": case "dreh": {
                Object c = last.get(norm(verb));
                Motion m = host.motion();
                return c == null || !m.pending((Motion.Cmd) c);
            }
            default: return true;
        }
    }

    // ------------------------------------------------------------ Hilfen

    private boolean fail(String msg) { error = msg; return false; }

    /** Ort aus den Wörtern ab {@code i}: Name der Szene oder „x z“ / „x,z“ (Boden). */
    static double[] place(World world, List<String> a, int i) {
        if (a.size() > i && isNum(a.get(i)) && a.size() > i + 1 && isNum(a.get(i + 1))) {
            double x = num(a.get(i)), z = num(a.get(i + 1));
            return new double[]{x, world == null ? 0 : world.ground(x, z), z};
        }
        if (world == null || a.size() <= i) return null;
        double[] p = world.place(a.get(i));
        if (p == null) for (String n : world.places()) if (n.equalsIgnoreCase(a.get(i))) p = world.place(n);
        return p == null ? null : p.clone();
    }

    static Actor find(Map<String, Actor> cast, String n) {
        if (cast == null) return null;
        Actor a = cast.get(n);
        if (a != null) return a;
        for (Map.Entry<String, Actor> e : cast.entrySet()) if (e.getKey().equalsIgnoreCase(n)) return e.getValue();
        return null;
    }

    static boolean isNum(String s) {
        try { Double.parseDouble(s.replace(',', '.')); return true; }
        catch (NumberFormatException e) { return false; }
    }

    static double num(String s) { return Double.parseDouble(s.replace(',', '.')); }

    /** Sekunden aus „3“, „3s“, „3 s“ oder „1,5“. */
    static double seconds(String s) { return num(s.replaceAll("\\s*s$", "")); }

    /** Befehl und Schlüsselwörter: klein, Umlaute ausgeschrieben gleich behandelt. */
    static String norm(String s) { return s.toLowerCase(Locale.ROOT).replace("ü", "ue").replace("ö", "oe").replace("ä", "ae").replace("ß", "ss"); }

    /** „ueberrascht“ → „überrascht“ (Namen der Gefühle stehen mit Umlauten). */
    static String umlaut(String s) {
        if (Emotion.named(s) != null) return s;
        String u = s.replace("ue", "ü").replace("oe", "ö").replace("ae", "ä");
        return Emotion.named(u) != null ? u : s;
    }

    @Override public String toString() { return name; }
}
