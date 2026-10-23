package com.dan.voice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.dan.voice.Phon.Kind;

/**
 * Ein gesprochener Satz als Spur der Mundbilder (Phase 16): Laute mit Anfang und Ende, daraus je Zeitpunkt die Gewichte
 * der 15 Viseme mit Koartikulation. Entsteht aus Text ({@link #fromText}, Dauern aus den Regeln) oder aus einer Tonspur
 * ({@link Audio#speech}: aus dem Ton allein oder Text an den Ton angelegt); dann folgt die Stärke der Vokale der
 * Lautstärke.
 *
 * <ul>
 * <li><b>Übergänge:</b> jedes Mundbild steigt vor seinem Laut an und fällt danach ab (je 40 % der Lautdauer, 25–70 ms),
 * so überlappen Nachbarn wie beim Sprechen.</li>
 * <li><b>Lippen zu:</b> bei p, b, m schließt der Mund ganz; andere Mundbilder treten zurück. Ebenso beim f die Unterlippe
 * an den Zähnen.</li>
 * <li><b>Vorausrundung:</b> Konsonanten vor o und u nehmen schon 40 % der Rundung.</li>
 * <li><b>Betonung:</b> betonte Vokale voll, unbetonte 70 %, Schwa 45 %.</li>
 * <li><b>Atem und Lidschlag:</b> {@link #breaths} (vor jedem Satz einatmen), {@link #phraseEnds} (dort oft ein
 * Lidschlag), {@link #talking} (beim Sprechen blinzelt man öfter).</li>
 * </ul>
 */
public final class Speech {

    /** Ein Laut mit Zeit (s). */
    public record Seg(Phon p, double t0, double t1) {
        public double mid() { return (t0 + t1) / 2; }
    }

    public final String text;
    public final List<Seg> segs;
    /** Dauer bis zum Ende des letzten Lauts (s). */
    public final double duration;
    /** Einatmen vor Sätzen (Zeitpunkte, s). */
    public final List<Double> breaths;
    /** Ende von Phrasen (Pausen), s. */
    public final List<Double> phraseEnds;
    /** Lautstärke je 10 ms (0 … 1) aus dem Ton oder null. */
    private final float[] env;
    /** Woher: „Text“, „Ton“, „Text am Ton“. */
    public final String source;

    Speech(String text, List<Seg> segs, float[] env, String source) {
        this.text = text;
        this.segs = Collections.unmodifiableList(segs);
        this.env = env;
        this.source = source;
        double d = 0;
        List<Double> b = new ArrayList<>(), pe = new ArrayList<>();
        boolean inPause = true;
        for (Seg s : segs) {
            if (s.p().pause()) {
                if (!inPause) pe.add(s.t0());
                inPause = true;
            } else {
                if (inPause) b.add(Math.max(0, s.t0() - 0.42));
                inPause = false;
                d = s.t1();
            }
        }
        this.duration = d;
        this.breaths = Collections.unmodifiableList(b);
        this.phraseEnds = Collections.unmodifiableList(pe);
    }

    /** Vorlauf (s) vor dem ersten Laut: Zeit zum Einatmen. */
    public static final double LEAD = 0.45;

    /** Spur aus Text; {@code tempo} 1 = normal (rund 12–13 Laute je Sekunde), größer = schneller. */
    public static Speech fromText(String text, double tempo) {
        List<Phon> ph = G2P.phonemes(text);
        List<Seg> s = new ArrayList<>();
        double t = LEAD;
        for (Phon p : ph) {
            double d = p.ms() / 1000 / Math.max(0.3, tempo);
            s.add(new Seg(p, t, t + d));
            t += d;
        }
        return new Speech(text, s, null, "Text");
    }

    /**
     * Gesang (Phase 17): Zeilen mit Anfang und Ende (s). In einer Zeile sind die Konsonanten kurz (60 % ihrer Regeldauer,
     * höchstens 90 ms, zusammen höchstens 45 % der Zeile), die Vokale teilen sich den Rest nach ihrer Regeldauer, betonte
     * doppelt und der letzte dreifach (gehaltene Töne); Kommas singen durch. Zwischen den Zeilen Pausen (mit Atem).
     */
    public static Speech sung(List<String> texts, double[] t0, double[] t1) {
        List<Seg> s = new ArrayList<>();
        StringBuilder all = new StringBuilder();
        double t = 0;
        for (int k = 0; k < texts.size(); k++) {
            double a = Math.max(t, t0[k]), e = Math.max(a + 0.2, t1[k]);
            if (a > t) s.add(new Seg(new Phon("_", Kind.PAUSE, Viseme.SIL, (a - t) * 1000, false, -1), t, a));
            List<Phon> ph = new ArrayList<>();
            for (Phon p : G2P.phonemes(texts.get(k))) if (!p.pause()) ph.add(p);
            if (ph.isEmpty()) { t = a; continue; }
            int lastV = -1;
            for (int i = 0; i < ph.size(); i++) if (ph.get(i).vowel() && !ph.get(i).sym().equals("@") && !ph.get(i).sym().equals("6")) lastV = i;
            double T = e - a, cons = 0, wv = 0;
            double[] d = new double[ph.size()];
            for (int i = 0; i < ph.size(); i++) {
                Phon p = ph.get(i);
                if (p.vowel()) { double w = p.ms() * (p.stressed() ? 2 : 1) * (i == lastV ? 3 : 1); d[i] = w; wv += w; }
                else { d[i] = Math.min(0.09, 0.6 * p.ms() / 1000); cons += d[i]; }
            }
            double cScale = cons > 0.45 * T ? 0.45 * T / cons : 1;
            double vt = T - cons * cScale;
            double x = a;
            for (int i = 0; i < ph.size(); i++) {
                Phon p = ph.get(i);
                double dd = p.vowel() ? (wv > 0 ? vt * d[i] / wv : 0) : d[i] * cScale;
                s.add(new Seg(p.withMs(dd * 1000), x, x + dd));
                x += dd;
            }
            t = e;
            if (all.length() > 0) all.append(" / ");
            all.append(texts.get(k));
        }
        s.add(new Seg(new Phon("_", Kind.PAUSE, Viseme.SIL, 300, false, -1), t, t + 0.3));
        return new Speech(all.toString(), s, null, "Gesang");
    }

    /** Spur aus Lauten mit gemessenen Zeiten (Anlegen an den Ton) und Lautstärke je 10 ms. */
    static Speech timed(String text, List<Phon> ph, double[] t0, double[] t1, float[] env, String source) {
        List<Seg> s = new ArrayList<>();
        for (int i = 0; i < ph.size(); i++) s.add(new Seg(ph.get(i), t0[i], t1[i]));
        return new Speech(text, s, env, source);
    }

    /** Spricht gerade jemand (zwischen erstem und letztem Laut, ohne lange Pausen)? */
    public boolean talking(double t) {
        Seg s = segAt(t);
        return s != null && !s.p().pause() || s != null && s.t1() - s.t0() < 0.3 && t < duration;
    }

    /** Der Laut zur Zeit t oder null. */
    public Seg segAt(double t) {
        int lo = 0, hi = segs.size() - 1;
        while (lo <= hi) {
            int m = (lo + hi) >>> 1;
            Seg s = segs.get(m);
            if (t < s.t0()) hi = m - 1;
            else if (t >= s.t1()) lo = m + 1;
            else return s;
        }
        return null;
    }

    /** Lautstärke 0 … 1 zur Zeit t (aus dem Ton; ohne Ton 1 beim Sprechen). */
    public double loudness(double t) {
        if (env == null) return 1;
        int i = (int) Math.floor(t * 100);
        if (i < 0 || i >= env.length) return 0;
        return env[i];
    }

    /** Gewichte der 15 Viseme zur Zeit t (Summe der Mundbilder höchstens 1, Rest „sil“). */
    public Map<Viseme, Double> weights(double t) {
        EnumMap<Viseme, Double> w = new EnumMap<>(Viseme.class);
        int i0 = first(t - 0.3);
        for (int i = i0; i < segs.size(); i++) {
            Seg s = segs.get(i);
            if (s.t0() > t + 0.3) break;
            Phon p = s.p();
            if (p.pause() || p.v() == Viseme.SIL) continue;
            double d = s.t1() - s.t0(), a = Math.max(0.025, Math.min(0.07, 0.4 * d));
            double amp = amp(p);
            if (env != null && p.vowel()) amp *= Math.max(0.45, Math.min(1.15, 0.45 + 0.9 * loudness(s.mid())));
            if (p.v2() == null) add(w, p.v(), amp * window(t, s.t0(), s.t1(), a));
            else {
                double m = s.t0() + d * 0.5;
                add(w, p.v(), amp * window(t, s.t0(), m, a));
                add(w, p.v2(), amp * 0.85 * window(t, m, s.t1(), a));
            }
            // Vorausrundung: Konsonanten vor einem gerundeten Vokal (bis zwei Laute weiter)
            if (!p.vowel())
                for (int k = i + 1; k <= i + 2 && k < segs.size(); k++) {
                    Phon q = segs.get(k).p();
                    if (q.pause()) break;
                    if (q.vowel()) { if (q.v().rounded()) add(w, q.v(), 0.4 * amp(q) * window(t, s.t0(), s.t1(), a)); break; }
                }
        }
        // Lippen zu (p, b, m) und Unterlippe an den Zähnen (f, v, w) haben Vorrang
        double pp = w.getOrDefault(Viseme.PP, 0.0), ff = w.getOrDefault(Viseme.FF, 0.0);
        double keep = 1 - Math.max(Math.min(1, pp), 0.8 * Math.min(1, ff));
        double sum = 0;
        for (Map.Entry<Viseme, Double> e : w.entrySet()) {
            if (e.getKey() != Viseme.PP && e.getKey() != Viseme.FF) e.setValue(e.getValue() * keep);
            sum += e.getValue();
        }
        if (sum > 1) for (Map.Entry<Viseme, Double> e : w.entrySet()) e.setValue(e.getValue() / sum);
        w.put(Viseme.SIL, Math.max(0, 1 - Math.min(1, sum)));
        return w;
    }

    /** Wie {@link #weights}, als Formnamen des Grundkörpers ({@code visem.viseme_…}); Stille weggelassen. */
    public Map<String, Double> forms(double t) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<Viseme, Double> e : weights(t).entrySet())
            if (e.getKey() != Viseme.SIL && e.getValue() > 1e-4) out.put(e.getKey().morph(), e.getValue());
        return out;
    }

    /** Das stärkste Mundbild zur Zeit t (SIL in Pausen). */
    public Viseme dominant(double t) {
        Viseme best = Viseme.SIL;
        double bw = 0.2;
        for (Map.Entry<Viseme, Double> e : weights(t).entrySet())
            if (e.getKey() != Viseme.SIL && e.getValue() > bw) { bw = e.getValue(); best = e.getKey(); }
        return best;
    }

    /** Öffnung des Munds 0 … 1 zur Zeit t (für Kiefer und Atem). */
    public double openness(double t) {
        double o = 0;
        for (Map.Entry<Viseme, Double> e : weights(t).entrySet()) o += e.getKey().open * e.getValue();
        return o;
    }

    /** Laute als Text mit Zeiten (Prüfung, Bedienfeld). */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (Seg s : segs) sb.append(String.format(java.util.Locale.GERMAN, "%s %.2f–%.2f%n", s.p(), s.t0(), s.t1()));
        return sb.toString();
    }

    private int first(double t) {
        int lo = 0, hi = segs.size();
        while (lo < hi) { int m = (lo + hi) >>> 1; if (segs.get(m).t1() < t) lo = m + 1; else hi = m; }
        return lo;
    }

    static double amp(Phon p) {
        return switch (p.kind()) {
            case VOWEL -> p.sym().equals("@") ? 0.45 : p.sym().equals("6") ? 0.5 : p.stressed() ? 1.0 : 0.7;
            case PLOSIVE -> p.v() == Viseme.PP ? 1.0 : 0.55;
            case NASAL -> p.v() == Viseme.PP ? 1.0 : 0.5;
            case FRICATIVE -> p.v() == Viseme.FF ? 0.9 : p.v() == Viseme.CH ? 0.8 : 0.65;
            case LIQUID -> 0.5;
            default -> 0;
        };
    }

    /** Fenster: steigt von t0 − a bis t0 + a an, fällt von t1 − a bis t1 + a ab. */
    static double window(double t, double t0, double t1, double a) {
        if (t1 - t0 < 2 * a) { double m = (t0 + t1) / 2; t0 = m - a; t1 = m + a; }
        return smooth((t - (t0 - a)) / (2 * a)) * (1 - smooth((t - (t1 - a)) / (2 * a)));
    }

    static double smooth(double x) { x = Math.max(0, Math.min(1, x)); return x * x * (3 - 2 * x); }

    private static void add(Map<Viseme, Double> w, Viseme v, double x) { if (x > 0) w.merge(v, x, Double::sum); }

    /** Kurzbeschreibung. */
    @Override public String toString() {
        int n = 0;
        for (Seg s : segs) if (!s.p().pause()) n++;
        return String.format(java.util.Locale.GERMAN, "„%s“ · %d Laute · %.2f s · %s", text, n, duration, source);
    }

    /** Für die Zuordnung: Lautklasse (0 Pause, 1 Vokal, 2 Verschluss, 3 Reibelaut, 4 Nasal/Liquid, 5 h). */
    static int cls(Phon p) {
        Kind k = p.kind();
        return switch (k) { case PAUSE -> 0; case VOWEL -> 1; case PLOSIVE -> 2; case FRICATIVE -> 3; case NASAL, LIQUID -> 4; case H -> 5; };
    }
}
