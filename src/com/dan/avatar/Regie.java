package com.dan.avatar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Ein Regiebuch (Phase 13, gleiche Form wie in Glacier und Rungholt): Text, jede Zeile ein Stichwort. Die
 * Regie führt die Zeilen der Reihe nach aus, bis eine {@code warte}-Zeile hält; was danach kommt, läuft im
 * nächsten Schritt weiter. Befehle mit Namen vor dem Punkt gehen an die Person ({@link Actor}), alle anderen
 * an die Szene ({@link Stage}): {@code ansicht}, {@code kamera}, {@code wetter}, {@code person} …
 *
 * <pre>
 * titel   Am Steg | Eine Regennacht am Wasser
 * person  mara Strickkapuze seed=4711 frau ort=Ufer
 * kamera  Totale
 * mara.gehe     Steg_Ende 0.9 | Sie kommt jeden Abend her.
 * warte   mara.gehe
 * mara.geste    Kapuze_ab
 * warte   2 s
 * </pre>
 *
 * <p>Der Teil nach „ | “ ist der Untertitel ({@link #onText}); {@code sage "…"} gibt seinen Text ebenso weiter.
 * Kopfzeilen: {@code titel <Titel> | <Unterzeile>} und {@code abspann <Rolle: Name>} (beliebig oft) werden
 * vorab gelesen, nicht ausgeführt. {@code #} beginnt einen Kommentar. {@code warte} hält: {@code warte 8 s},
 * {@code warte mara.gehe}, {@code warte mara} (alles von mara), {@code warte kamera} (Szene), {@code warte alle}.
 */
public final class Regie {

    /** Die Szene: führt ihre Befehle aus und meldet, wann sie fertig sind. */
    public interface Stage {
        /** Befehl der Szene; {@code false}, wenn unbekannt (die Regie meldet einen Fehler und geht weiter). */
        boolean command(String verb, String args, Regie regie);
        /** Ist der Befehl der Szene fertig ({@code warte kamera})? */
        default boolean done(String verb) { return true; }
        /** Die Umwelt für die Personen (Orte, Boden, Kamera). */
        World world();
    }

    /** Eine Zeile: Nummer, Person (oder {@code null}), Befehl, Rest, Untertitel (oder {@code null}). */
    public record Line(int nr, String actor, String verb, String args, String caption) {
        @Override public String toString() { return (actor == null ? "" : actor + ".") + verb + (args.isEmpty() ? "" : " " + args); }
    }

    public final String name;
    public final List<Line> lines;
    /** Titel und Unterzeile (aus {@code titel}), Zeilen des Abspanns (aus {@code abspann}). */
    public final String title, subtitle;
    public final List<String> credits;
    /** Besetzung: Name in der Regie → Person (die Szene trägt sie bei {@code person} ein). */
    public final Map<String, Actor> cast = new LinkedHashMap<>();
    /** Untertitel jeder Zeile und jedes {@code sage}. */
    public Consumer<String> onText;
    /** Jede ausgeführte Zeile (Protokoll, HUD). */
    public Consumer<Line> onLine;
    /** Fehler mit Zeilennummer. */
    public final List<String> errors = new ArrayList<>();

    private int next;
    private double clock, waitUntil = -1;
    private Line waiting;
    private Stage stage;
    private boolean started;

    public Regie(String name, String text) {
        this.name = name;
        List<Line> ls = new ArrayList<>();
        List<String> cr = new ArrayList<>();
        String t = name, sub = "";
        String[] raw = text.replace("\r", "").split("\n");
        for (int i = 0; i < raw.length; i++) {
            String s = raw[i];
            int hash = s.indexOf('#');
            if (hash >= 0 && !inQuotes(s, hash)) s = s.substring(0, hash);
            s = s.strip();
            if (s.isEmpty()) continue;
            String caption = null;
            int bar = s.indexOf(" | ");
            if (bar >= 0) { caption = s.substring(bar + 3).strip(); s = s.substring(0, bar).strip(); }
            else if (s.endsWith(" |")) s = s.substring(0, s.length() - 2).strip();
            int sp = firstSpace(s);
            String head = sp < 0 ? s : s.substring(0, sp);
            String rest = sp < 0 ? "" : s.substring(sp + 1).strip();
            String verb = head, actor = null;
            int dot = head.indexOf('.');
            if (dot > 0 && dot < head.length() - 1) { actor = head.substring(0, dot); verb = head.substring(dot + 1); }
            String v = Actor.norm(verb);
            if (actor == null && v.equals("titel")) { t = rest; sub = caption == null ? "" : caption; continue; }
            if (actor == null && v.equals("abspann")) { cr.add(rest + (caption == null ? "" : " | " + caption)); continue; }
            ls.add(new Line(i + 1, actor, v, rest, caption));
        }
        this.lines = Collections.unmodifiableList(ls);
        this.title = t;
        this.subtitle = sub;
        this.credits = Collections.unmodifiableList(cr);
    }

    /** Regiebuch aus einer Ressource (UTF-8) neben einer Klasse. */
    public static Regie resource(Class<?> near, String file) throws java.io.IOException {
        try (java.io.InputStream in = near.getResourceAsStream(file)) {
            if (in == null) throw new java.io.FileNotFoundException(file);
            String n = file.replaceAll("^.*/", "").replaceAll("\\.[^.]*$", "");
            return new Regie(n, new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /** Beginnen (setzt alles zurück; die Besetzung bleibt). */
    public void start(Stage stage) {
        this.stage = stage;
        next = 0; clock = 0; waitUntil = -1; waiting = null; started = true;
        errors.clear();
    }

    /** Läuft das Regiebuch (begonnen und nicht zu Ende)? */
    public boolean running() { return started && !finished(); }

    /** Alle Zeilen ausgeführt und nichts wartet mehr. */
    public boolean finished() { return started && next >= lines.size() && waiting == null; }

    /** Zeit seit dem Beginn (s). */
    public double time() { return clock; }

    /** Zeile, auf die gerade gewartet wird (oder {@code null}). */
    public Line waiting() { return waiting; }

    /** Nummer der nächsten Zeile (0 …). */
    public int position() { return next; }

    /**
     * Zeit dt (s) weiter: Personen ({@link Actor#step}), dann Zeilen ausführen, bis eine {@code warte}-Zeile
     * hält. Höchstens 200 Zeilen je Schritt (Schutz gegen ein Regiebuch ohne {@code warte}).
     */
    public void step(double dt) {
        if (!started) return;
        clock += Math.max(0, dt);
        for (Actor a : cast.values()) a.step(dt);
        for (int guard = 0; guard < 200; guard++) {
            if (waiting != null) {
                if (!ready(waiting)) return;
                waiting = null;
            }
            if (next >= lines.size()) return;
            Line l = lines.get(next++);
            if (onLine != null) onLine.accept(l);
            if (l.caption() != null && onText != null) onText.accept(l.caption());
            if (l.actor() == null && l.verb().equals("warte")) {
                waiting = l;
                waitUntil = -1;
                List<String> w = words(l.args());
                if (!w.isEmpty() && Actor.isNum(w.get(0))) waitUntil = clock + Actor.num(w.get(0));
                if (w.isEmpty()) error(l, "warte: worauf?");
                continue;
            }
            execute(l);
        }
    }

    private void execute(Line l) {
        if (l.actor() != null) {
            Actor a = Actor.find(cast, l.actor());
            if (a == null) { error(l, "keine Person „" + l.actor() + "“ (erst person … anlegen)"); return; }
            boolean ok = a.command(l.verb(), l.args(), stage == null ? null : stage.world(), cast);
            if (!ok) error(l, a.error);
            else if (l.verb().equals("sage") && onText != null && a.said != null && !a.said.isBlank()) onText.accept(a.said);
            return;
        }
        if (stage == null || !stage.command(l.verb(), l.args(), this)) error(l, "unbekannter Befehl der Szene: " + l.verb());
    }

    private boolean ready(Line w) {
        if (waitUntil >= 0) return clock >= waitUntil;
        List<String> ws = words(w.args());
        if (ws.isEmpty()) return true;
        String what = ws.get(0);
        if (Actor.norm(what).equals("alle")) {
            for (Actor a : cast.values()) if (!a.done(null)) return false;
            return stage == null || stage.done("kamera");
        }
        int dot = what.indexOf('.');
        String an = dot > 0 ? what.substring(0, dot) : what, verb = dot > 0 ? what.substring(dot + 1) : null;
        Actor a = Actor.find(cast, an);
        if (a != null) return a.done(verb);
        return stage == null || stage.done(Actor.norm(what));
    }

    private void error(Line l, String msg) {
        String e = "Zeile " + l.nr() + " (" + l + "): " + msg;
        errors.add(e);
    }

    // ------------------------------------------------------------ Hilfen

    /** Wörter einer Zeile; Text in Anführungszeichen bleibt ein Wort (ohne die Zeichen). */
    public static List<String> words(String s) {
        List<String> r = new ArrayList<>();
        if (s == null) return r;
        StringBuilder b = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '„' || c == '“' || c == '”') {
                if (q) { r.add(b.toString()); b.setLength(0); }
                else if (b.length() > 0) { r.add(b.toString()); b.setLength(0); }
                q = !q;
                continue;
            }
            if (!q && Character.isWhitespace(c)) { if (b.length() > 0) { r.add(b.toString()); b.setLength(0); } continue; }
            b.append(c);
        }
        if (b.length() > 0) r.add(b.toString());
        return r;
    }

    /** Wert „schluessel=wert“ aus den Wörtern (oder {@code def}). */
    public static String option(List<String> w, String key, String def) {
        for (String s : w) {
            int e = s.indexOf('=');
            if (e > 0 && s.substring(0, e).equalsIgnoreCase(key)) return s.substring(e + 1);
        }
        return def;
    }

    private static int firstSpace(String s) {
        for (int i = 0; i < s.length(); i++) if (Character.isWhitespace(s.charAt(i))) return i;
        return -1;
    }

    private static boolean inQuotes(String s, int at) {
        int n = 0;
        for (int i = 0; i < at; i++) if (s.charAt(i) == '"') n++;
        return n % 2 == 1;
    }

    @Override public String toString() {
        return String.format(Locale.GERMANY, "%s · %d Zeilen · %.1f s%s", title, lines.size(), clock, waiting == null ? "" : " · warte " + waiting.args());
    }
}
