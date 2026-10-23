package com.dan.beat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liedtext mit Zeitmarken im LRC-Format (Phase 17): {@code [mm:ss.xx]Text}, mehrere Marken vor einer Zeile erlaubt;
 * eine Marke ohne Text beendet die Zeile davor (sonst endet sie 0,3 s vor der nächsten). Kopfzeilen wie {@code [ti:]},
 * {@code [ar:]}, {@code [la:]} stehen in {@link #tags}; Zeilen mit {@code #} sind Bemerkungen.
 *
 * <p>Erweiterung für den Mund: {@code Text | Lautung}. Links steht, was als Untertitel erscheint, rechts, wie es
 * gesungen klingt, in deutscher Schreibung (für fremdsprachige Lieder, z. B. {@code Hush, little baby | Hasch, littel bebi}).
 */
public final class Lyrics {

    /** Eine Zeile: Anfang, Ende (s), Untertitel, Lautung für den Mund. */
    public record Line(double t0, double t1, String text, String sung) {
        public double mid() { return (t0 + t1) / 2; }
    }

    public final List<Line> lines;
    public final Map<String, String> tags;

    private Lyrics(List<Line> lines, Map<String, String> tags) {
        this.lines = Collections.unmodifiableList(lines);
        this.tags = Collections.unmodifiableMap(tags);
    }

    static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2}(?:[.,]\\d{1,3})?)\\]");
    static final Pattern TAG = Pattern.compile("^\\[([a-zA-Z]+):(.*)\\]\\s*$");

    public static Lyrics read(File f) throws IOException { return parse(Files.readString(f.toPath(), StandardCharsets.UTF_8)); }

    /** LRC lesen. */
    public static Lyrics parse(String lrc) {
        Map<String, String> tags = new LinkedHashMap<>();
        List<double[]> times = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (String raw : lrc.split("\\R")) {
            String l = raw.strip();
            if (l.isEmpty() || l.startsWith("#")) continue;
            Matcher tm = TAG.matcher(l);
            if (tm.matches() && !STAMP.matcher(l).lookingAt()) { tags.put(tm.group(1).toLowerCase(), tm.group(2).strip()); continue; }
            Matcher m = STAMP.matcher(l);
            List<Double> ts = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                ts.add(Integer.parseInt(m.group(1)) * 60 + Double.parseDouble(m.group(2).replace(',', '.')));
                end = m.end();
            }
            if (ts.isEmpty()) continue;
            String text = l.substring(end).strip();
            for (double t : ts) { times.add(new double[]{t}); texts.add(text); }
        }
        // nach Zeit ordnen
        Integer[] idx = new Integer[times.size()];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        java.util.Arrays.sort(idx, (a, b) -> Double.compare(times.get(a)[0], times.get(b)[0]));
        double off = 0;
        if (tags.containsKey("offset")) try { off = Double.parseDouble(tags.get("offset")) / 1000; } catch (NumberFormatException ignore) { }
        List<Line> out = new ArrayList<>();
        for (int k = 0; k < idx.length; k++) {
            String text = texts.get(idx[k]);
            if (text.isEmpty()) continue;
            double t0 = times.get(idx[k])[0] + off;
            double t1;
            if (k + 1 < idx.length) {
                double next = times.get(idx[k + 1])[0] + off;
                t1 = texts.get(idx[k + 1]).isEmpty() ? next : Math.max(t0 + 0.3, next - 0.3);
            } else t1 = t0 + 4;
            String shown = text, sung = text;
            int bar = text.indexOf('|');
            if (bar >= 0) { shown = text.substring(0, bar).strip(); sung = text.substring(bar + 1).strip(); }
            out.add(new Line(t0, t1, shown, sung));
        }
        return new Lyrics(out, tags);
    }

    /** Die Zeile zur Zeit t oder null. */
    public Line at(double t) {
        for (Line l : lines) if (t >= l.t0() && t < l.t1()) return l;
        return null;
    }

    /** Titel aus {@code [ti:]} (oder leer). */
    public String title() { return tags.getOrDefault("ti", ""); }

    /** Interpret aus {@code [ar:]} (oder leer). */
    public String artist() { return tags.getOrDefault("ar", ""); }
}
