package com.dan.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.dan.voice.Phon.Kind;

/**
 * Deutscher Text → Laute (Phase 16), mit Regeln statt Wörterbuch [G: Regeln nach der deutschen Rechtschreibung,
 * vereinfacht]. Es geht um das Mundbild, nicht um die Aussprache im Ohr: ob ein Vokal lang oder kurz ist, ändert die
 * Dauer; welcher Konsonant genau, nur das Visem.
 *
 * <ul>
 * <li>Mehrbuchstaben zuerst: tsch, sch, chs, ch (ach-Laut nach a, o, u, au, sonst ich-Laut), ck, ph, pf, qu, th, dt, tz,
 * ng, nk; sp und st am Wortanfang wie schp, scht; z wie ts, x wie ks, v wie f, w wie v, ß wie s.</li>
 * <li>Vokale: Doppellaute ei/ai/ey/ay, eu/äu, au; ie lang; Doppelvokal und Dehnungs-h lang; ein Vokal vor einem
 * einzelnen Konsonanten und einem Vokal (offene Silbe) lang, vor zwei Konsonanten kurz.</li>
 * <li>Unbetontes e am Wortende und in den Endungen -en, -el, -em, -es, -et wird zum Schwa; -er am Ende und r nach
 * Vokal vor Konsonant werden zum vokalischen r.</li>
 * <li>Betonung auf der ersten Silbe, nach den Vorsilben be-, ge-, ver-, zer-, ent-, emp- auf der zweiten; kurze
 * Funktionswörter (der, und, ich, im …) bleiben unbetont.</li>
 * <li>Zahlen bis 999 werden als Wörter gelesen; Satzzeichen geben Pausen (Komma 0,22 s, Satzende 0,42 s).</li>
 * </ul>
 */
public final class G2P {
    private G2P() { }

    /** Dauern in ms bei normalem Tempo [G: um 12–13 Laute je Sekunde]. */
    public static final double SHORT = 75, LONG = 125, DIPH = 155, SCHWA = 50, VOCR = 70, PLOS = 72, FRIC = 92, NAS = 68, LIQ = 58, HH = 55, GLIDE = 55,
            COMMA = 220, STOP = 420;

    static final Set<String> FUNCTION = Set.of("der", "die", "das", "den", "dem", "des", "ein", "eine", "einen", "einem", "einer", "und", "oder", "ich", "du",
            "er", "sie", "es", "wir", "ihr", "im", "in", "am", "an", "auf", "aus", "bei", "mit", "von", "vom", "zu", "zum", "zur", "ist", "bin", "hat", "mir",
            "dir", "mich", "dich", "sich", "nicht", "so", "wie", "als", "noch", "nur", "doch", "ja", "the", "a");
    static final String[] PREFIX = {"emp", "ent", "ver", "zer", "be", "ge"};

    /** Laute eines Texts, mit Pausen an Satzzeichen (und einer am Ende). */
    public static List<Phon> phonemes(String text) {
        List<Phon> out = new ArrayList<>();
        String t = numbers(text.toLowerCase(Locale.GERMAN));
        StringBuilder w = new StringBuilder();
        int word = 0;
        for (int i = 0; i <= t.length(); i++) {
            char c = i < t.length() ? t.charAt(i) : ' ';
            if (Character.isLetter(c)) { w.append(c); continue; }
            if (w.length() > 0) { out.addAll(word(w.toString(), word++)); w.setLength(0); }
            if (c == ',' || c == ';' || c == ':' || c == '–' || c == '-' && i > 0 && t.charAt(i - 1) == ' ') pause(out, COMMA);
            else if (c == '.' || c == '!' || c == '?' || c == '…') pause(out, STOP);
        }
        if (!out.isEmpty() && !out.get(out.size() - 1).pause()) pause(out, STOP);
        // am Phrasenende länger: der letzte Vokal vor einer Pause um 30 %
        for (int i = 1; i < out.size(); i++)
            if (out.get(i).pause())
                for (int k = i - 1; k >= 0 && !out.get(k).pause(); k--)
                    if (out.get(k).vowel()) { out.set(k, out.get(k).withMs(out.get(k).ms() * 1.3)); break; }
        return out;
    }

    private static void pause(List<Phon> out, double ms) {
        if (out.isEmpty()) return;
        Phon last = out.get(out.size() - 1);
        if (last.pause()) { if (last.ms() < ms) out.set(out.size() - 1, last.withMs(ms)); return; }
        out.add(new Phon("_", Kind.PAUSE, Viseme.SIL, ms, false, -1));
    }

    static boolean vowelChar(char c) { return "aeiouäöüy".indexOf(c) >= 0; }

    /** Laute eines Worts (Kleinbuchstaben). */
    public static List<Phon> word(String w, int nr) {
        List<Phon> p = new ArrayList<>();
        int n = w.length();
        boolean func = FUNCTION.contains(w);
        int stressAt = func ? -1 : stressVowel(w);
        int i = 0;
        while (i < n) {
            char c = w.charAt(i);
            String r = w.substring(i);
            if (vowelChar(c)) {
                boolean st = i == stressAt;
                if (r.startsWith("ei") || r.startsWith("ai") || r.startsWith("ey") || r.startsWith("ay")) { p.add(new Phon("aI", Kind.VOWEL, Viseme.AA, Viseme.I, DIPH, st, nr)); i += 2; continue; }
                if (r.startsWith("eu") || r.startsWith("äu")) { p.add(new Phon("OY", Kind.VOWEL, Viseme.O, Viseme.I, DIPH, st, nr)); i += 2; continue; }
                if (r.startsWith("au")) { p.add(new Phon("aU", Kind.VOWEL, Viseme.AA, Viseme.U, DIPH, st, nr)); i += 2; continue; }
                if (r.startsWith("ie")) { p.add(vowel('i', true, st, nr)); i += 2; if (i < n && w.charAt(i) == 'h') i++; continue; }
                if (i + 1 < n && w.charAt(i + 1) == c && c != 'u' && c != 'i') { p.add(vowel(c, true, st, nr)); i += 2; continue; }
                // Dehnungs-h: Vokal + h + (Konsonant oder Ende)
                if (i + 1 < n && w.charAt(i + 1) == 'h' && (i + 2 >= n || !vowelChar(w.charAt(i + 2)))) { p.add(vowel(c, true, st, nr)); i += 2; continue; }
                // unbetontes e: Schwa am Ende und in Endsilben; -er am Ende und vor Konsonant vokalisch
                if (c == 'e' && !st && i > 0 && i + 1 < n && w.charAt(i + 1) == 'r' && (i + 2 >= n || !vowelChar(w.charAt(i + 2)))) {
                    p.add(new Phon("6", Kind.VOWEL, Viseme.E, VOCR, false, nr)); i += 2; continue;
                }
                if (c == 'e' && !st && i > 0) {
                    String rest = w.substring(i + 1);
                    if (rest.equals("r") || (rest.equals("rn"))) { p.add(new Phon("6", Kind.VOWEL, Viseme.E, VOCR, false, nr)); i += 2; continue; }
                    if (rest.isEmpty() || rest.equals("n") || rest.equals("l") || rest.equals("m") || rest.equals("s") || rest.equals("t") || rest.equals("nd")) {
                        p.add(new Phon("@", Kind.VOWEL, Viseme.E, SCHWA, false, nr)); i++; continue;
                    }
                }
                if (c == 'e' && !st && i <= 2 && (w.startsWith("be") || w.startsWith("ge")) && i == 1) { p.add(new Phon("@", Kind.VOWEL, Viseme.E, SCHWA, false, nr)); i++; continue; }
                p.add(vowel(c, longVowel(w, i, st), st, nr));
                i++;
                continue;
            }
            // Konsonanten, mehrere Buchstaben zuerst
            if (r.startsWith("tsch")) { cons(p, "t", nr); cons(p, "S", nr); i += 4; continue; }
            if (r.startsWith("sch")) { cons(p, "S", nr); i += 3; continue; }
            if (r.startsWith("chs")) { cons(p, "k", nr); cons(p, "s", nr); i += 3; continue; }
            if (r.startsWith("ch")) {
                boolean ach = i > 0 && "aou".indexOf(w.charAt(i - 1)) >= 0 && !(i > 1 && (w.startsWith("eu", i - 2) || w.startsWith("äu", i - 2)));
                if (i == 0) cons(p, "k", nr); else cons(p, ach ? "x" : "C", nr);
                i += 2;
                continue;
            }
            if (r.startsWith("ck")) { cons(p, "k", nr); i += 2; continue; }
            if (r.startsWith("ph")) { cons(p, "f", nr); i += 2; continue; }
            if (r.startsWith("pf")) { cons(p, "p", nr); cons(p, "f", nr); i += 2; continue; }
            if (r.startsWith("qu")) { cons(p, "k", nr); cons(p, "v", nr); i += 2; continue; }
            if (r.startsWith("th")) { cons(p, "t", nr); i += 2; continue; }
            if (r.startsWith("dt")) { cons(p, "t", nr); i += 2; continue; }
            if (r.startsWith("tz")) { cons(p, "t", nr); cons(p, "s", nr); i += 2; continue; }
            if (r.startsWith("ng")) { cons(p, "N", nr); i += 2; continue; }
            if (r.startsWith("nk")) { cons(p, "N", nr); cons(p, "k", nr); i += 2; continue; }
            if (i == 0 && (r.startsWith("sp") || r.startsWith("st"))) { cons(p, "S", nr); i++; continue; }
            // doppelte Konsonanten einmal
            if (i + 1 < n && w.charAt(i + 1) == c) { i++; continue; }
            switch (c) {
                case 'b', 'p' -> cons(p, "p", nr);
                case 'd', 't' -> cons(p, "t", nr);
                case 'g', 'k', 'c' -> cons(p, "k", nr);
                case 'f', 'v' -> cons(p, "f", nr);
                case 'w' -> cons(p, "v", nr);
                case 's', 'ß' -> cons(p, "s", nr);
                case 'z' -> { cons(p, "t", nr); cons(p, "s", nr); }
                case 'x' -> { cons(p, "k", nr); cons(p, "s", nr); }
                case 'm' -> cons(p, "m", nr);
                case 'n' -> cons(p, "n", nr);
                case 'l' -> cons(p, "l", nr);
                case 'j' -> cons(p, "j", nr);
                case 'h' -> { if (i + 1 < n && vowelChar(w.charAt(i + 1))) cons(p, "h", nr); }
                case 'r' -> {
                    boolean afterV = i > 0 && (vowelChar(w.charAt(i - 1)) || w.charAt(i - 1) == 'h' && i > 1 && vowelChar(w.charAt(i - 2)));
                    boolean beforeV = i + 1 < n && vowelChar(w.charAt(i + 1));
                    if (afterV && !beforeV) p.add(new Phon("6", Kind.VOWEL, Viseme.E, VOCR, false, nr));
                    else cons(p, "R", nr);
                }
                default -> { }
            }
            i++;
        }
        return p;
    }

    /** Vokal nach Buchstabe: Mundbild und Dauer. */
    static Phon vowel(char c, boolean lng, boolean st, int nr) {
        Viseme v = switch (c) {
            case 'a' -> Viseme.AA;
            case 'e', 'ä' -> Viseme.E;
            case 'i', 'y' -> Viseme.I;
            case 'o', 'ö' -> Viseme.O;
            case 'u', 'ü' -> Viseme.U;
            default -> Viseme.E;
        };
        double ms = (lng ? LONG : SHORT) * (st ? 1.25 : 1);
        return new Phon(c + (lng ? ":" : ""), Kind.VOWEL, v, ms, st, nr);
    }

    static void cons(List<Phon> p, String s, int nr) {
        Phon x = switch (s) {
            case "p" -> new Phon("p", Kind.PLOSIVE, Viseme.PP, PLOS, false, nr);
            case "m" -> new Phon("m", Kind.NASAL, Viseme.PP, NAS, false, nr);
            case "t" -> new Phon("t", Kind.PLOSIVE, Viseme.DD, PLOS, false, nr);
            case "k" -> new Phon("k", Kind.PLOSIVE, Viseme.KK, PLOS, false, nr);
            case "f" -> new Phon("f", Kind.FRICATIVE, Viseme.FF, FRIC, false, nr);
            case "v" -> new Phon("v", Kind.FRICATIVE, Viseme.FF, FRIC * 0.8, false, nr);
            case "s" -> new Phon("s", Kind.FRICATIVE, Viseme.SS, FRIC, false, nr);
            case "S" -> new Phon("S", Kind.FRICATIVE, Viseme.CH, FRIC * 1.15, false, nr);
            case "C" -> new Phon("C", Kind.FRICATIVE, Viseme.CH, FRIC, false, nr);
            case "x" -> new Phon("x", Kind.FRICATIVE, Viseme.KK, FRIC, false, nr);
            case "n" -> new Phon("n", Kind.NASAL, Viseme.NN, NAS, false, nr);
            case "N" -> new Phon("N", Kind.NASAL, Viseme.KK, NAS, false, nr);
            case "l" -> new Phon("l", Kind.LIQUID, Viseme.NN, LIQ, false, nr);
            case "R" -> new Phon("R", Kind.LIQUID, Viseme.RR, LIQ, false, nr);
            case "j" -> new Phon("j", Kind.LIQUID, Viseme.I, GLIDE, false, nr);
            case "h" -> new Phon("h", Kind.H, Viseme.SIL, HH, false, nr);
            default -> null;
        };
        if (x != null) p.add(x);
    }

    /** Index des betonten Vokals: erste Silbe, nach einer Vorsilbe die zweite; −1 ohne Vokal. */
    static int stressVowel(String w) {
        int start = 0;
        for (String pf : PREFIX)
            if (w.startsWith(pf) && w.length() >= pf.length() + 4 && !vowelChar(w.charAt(pf.length())) && w.charAt(pf.length()) != 'h') { start = pf.length(); break; }
        for (int i = start; i < w.length(); i++) if (vowelChar(w.charAt(i))) return i;
        for (int i = 0; i < w.length(); i++) if (vowelChar(w.charAt(i))) return i;
        return -1;
    }

    /** Lang oder kurz nach den Buchstaben danach (offene Silbe lang, zwei Konsonanten kurz). */
    static boolean longVowel(String w, int i, boolean stressed) {
        int n = w.length(), k = i + 1, cons = 0;
        while (k < n && !vowelChar(w.charAt(k))) { cons++; k++; }
        if (k >= n) return cons == 0 && w.length() <= 3;          // so, du, je lang; im, in, bin kurz
        String after = w.substring(i + 1, i + 1 + cons);
        if (cons == 1) return stressed && !after.equals("x");
        // „sch“, „ch“ zählen als ein Laut, sind aber meist nach kurzem Vokal
        return false;
    }

    /** Zahlen bis 999 als Wörter. */
    static String numbers(String t) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < t.length()) {
            char c = t.charAt(i);
            if (Character.isDigit(c)) {
                int j = i;
                while (j < t.length() && Character.isDigit(t.charAt(j))) j++;
                String d = t.substring(i, j);
                String w = d.length() <= 3 ? number(Integer.parseInt(d)) : spell(d);
                // Ordnungszahl: „22. Oktober“ → zweiundzwanzigsten (Punkt mitten im Satz, danach ein Wort)
                if (d.length() <= 3 && j + 2 < t.length() && t.charAt(j) == '.' && t.charAt(j + 1) == ' '
                        && Character.isLetter(t.charAt(j + 2))) {
                    int v = Integer.parseInt(d);
                    w = v == 1 ? "ersten" : v == 3 ? "dritten" : v == 7 ? "siebten" : v == 8 ? "achten"
                            : w + (v % 100 >= 20 || v >= 100 && v % 100 == 0 ? "sten" : "ten");
                    j++;
                }
                sb.append(w);
                i = j;
            } else { sb.append(c); i++; }
        }
        return sb.toString();
    }

    static final String[] ONES = {"null", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht", "neun", "zehn", "elf", "zwölf",
        "dreizehn", "vierzehn", "fünfzehn", "sechzehn", "siebzehn", "achtzehn", "neunzehn"};
    static final String[] TENS = {"", "", "zwanzig", "dreißig", "vierzig", "fünfzig", "sechzig", "siebzig", "achtzig", "neunzig"};

    static String number(int v) {
        if (v < 20) return ONES[v];
        if (v < 100) return (v % 10 == 0 ? "" : (v % 10 == 1 ? "ein" : ONES[v % 10]) + "und") + TENS[v / 10];
        return (v / 100 == 1 ? "ein" : ONES[v / 100]) + "hundert" + (v % 100 == 0 ? "" : number(v % 100));
    }

    static String spell(String d) {
        StringBuilder sb = new StringBuilder();
        for (char c : d.toCharArray()) sb.append(ONES[c - '0']).append(' ');
        return sb.toString();
    }

    /** Laute als Zeichenkette (Prüfung, Bedienfeld): Wörter durch Leerzeichen, Pausen als |. */
    public static String show(List<Phon> ps) {
        StringBuilder sb = new StringBuilder();
        int w = -2;
        for (Phon p : ps) {
            if (p.pause()) { sb.append(" |"); w = -2; continue; }
            if (p.word() != w && sb.length() > 0) sb.append(' ');
            w = p.word();
            sb.append(p);
        }
        return sb.toString().trim();
    }
}
