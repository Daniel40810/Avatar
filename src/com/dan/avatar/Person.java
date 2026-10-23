package com.dan.avatar;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Wer jemand ist: eine Handvoll Zahlen und ein {@code seed}. Makro-Regler 0…1 (Mitte 0,5),
 * Gesichtsregler −1…+1, Gesichtsformen und Viseme 0…1. {@link #weights()} rechnet daraus die
 * Gewichte der Morphziele im Grundkörper, genau wie MPFB seine Zieldateien gewichtet
 * (Stützkurven aus macro.json, auf vier Stellen gerundet). Gleicher {@code seed}, gleiche Person.
 */
public final class Person {

    /** 0 weiblich … 1 männlich. */
    public double gender = 0.5;
    /** 0 = 1 Jahr, 0,1875 = 11, 0,5 = 25, 1 = 90 Jahre. */
    public double age = 0.5;
    public double muscle = 0.5, weight = 0.5, height = 0.5, proportions = 0.5, cupsize = 0.5, firmness = 0.5;
    /** Gesichtsregler −1…+1, Schlüssel wie {@link #FACE} (z. B. "nose.breite"). */
    public final Map<String, Double> face = new TreeMap<>();
    /** Gesichtsformen und Viseme 0…1, Schlüssel wie im Modell (z. B. "arkit.jawOpen", "visem.viseme_O"). */
    public final Map<String, Double> forms = new TreeMap<>();
    public long seed;
    public String name = "";

    // ------------------------------------------------------------ Haut (Phase 4)
    /** Hautton als Melanin: 0 sehr hell … 1 sehr dunkel. */
    public double melanin = 0.25;
    /** Durchblutung (Rötung) 0…1, Mitte 0,5. */
    public double redness = 0.5;
    /** Sommersprossen 0…1. */
    public double freckles = 0;
    /** Bartschatten (Stoppeln) 0…1. */
    public double beard = 0;
    /** Poren und feines Relief 0…1, Mitte 0,5. */
    public double pores = 0.5;
    /** Fettglanz 0…1. */
    public double oil = 0.35;
    /** Iris (Phase 5): 0 blau, 0,25 grün, 0,5 haselnuss, 0,75 braun, 1 dunkelbraun. */
    public double iris = 0.6;

    // ------------------------------------------------------------ Haare (Phase 6)
    /** Frisuren der Bibliothek (Schlüssel). */
    public static final String[] HAIR_STYLES = {"bob", "lang", "zopf", "dutt", "kurz", "locken", "glatze"};
    /** Frisur (einer aus {@link #HAIR_STYLES}). */
    public String hair = "bob";
    /** Haarfarbe als Eumelanin: 0 platinblond … 1 schwarz. */
    public double hairColor = 0.6;
    /** Rotanteil (Phäomelanin) 0…1. */
    public double hairRed = 0.1;
    /** Anteil grauer Haare 0…1. */
    public double grey = 0;
    /** Bartlänge 0 (nur Schatten) … 1 (Vollbart, etwa 3 cm). */
    public double beardLength = 0;

    /** Die 33 Gesichtsregler der Blender-Kette. */
    public static final String[] FACE = {
        "nose.breite", "nose.laenge", "nose.tiefe", "nose.hoecker", "nose.spitze", "nose.nasenloecher", "nose.hoehe",
        "mouth.breite", "mouth.oberlippe", "mouth.unterlippe", "mouth.winkel", "mouth.amorbogen", "mouth.hoehe",
        "chin.breite", "chin.vorstehen", "chin.hoehe", "chin.kiefer", "cheek.knochen", "cheek.fuelle",
        "eye.groesse", "eye.hoehe", "eye.abstand", "eye.lidfalte", "eye.traenensack", "brow.hoehe", "brow.winkel",
        "ear.groesse", "ear.abstehen", "forehead.hoehe", "head.fuelle", "head.breite", "head.hoehe", "neck.breite"};

    public Person() {}

    public Person copy() {
        Person p = new Person();
        p.gender = gender; p.age = age; p.muscle = muscle; p.weight = weight; p.height = height;
        p.proportions = proportions; p.cupsize = cupsize; p.firmness = firmness;
        p.face.putAll(face); p.forms.putAll(forms); p.seed = seed; p.name = name;
        p.melanin = melanin; p.redness = redness; p.freckles = freckles; p.beard = beard; p.pores = pores; p.oil = oil;
        p.iris = iris;
        p.hair = hair; p.hairColor = hairColor; p.hairRed = hairRed; p.grey = grey; p.beardLength = beardLength;
        return p;
    }

    /** Alter in Jahren (MakeHuman: 1, 11, 25, 90 an den Stützstellen). */
    public double ageYears() {
        if (age < 0.1875) return 1 + age / 0.1875 * 10;
        if (age < 0.5) return 11 + (age - 0.1875) / 0.3125 * 14;
        return 25 + (age - 0.5) / 0.5 * 65;
    }

    /** Alter in Jahren auf den Regler. */
    public static double ageFromYears(double y) {
        if (y < 11) return Math.max(0, (y - 1) / 10 * 0.1875);
        if (y < 25) return 0.1875 + (y - 11) / 14 * 0.3125;
        return Math.min(1, 0.5 + (y - 25) / 65 * 0.5);
    }

    // ------------------------------------------------------------ Würfel

    /**
     * Eine erfundene, glaubhafte erwachsene Person aus {@code seed} (F21): Geschlecht eher deutlich,
     * Alter 18–75, Gewicht steigt mit dem Alter, Männer etwas muskulöser, Gesicht leicht gestreut.
     */
    public static Person wuerfeln(long seed) {
        Rng r = new Rng(seed);
        Person p = new Person();
        p.seed = seed;
        boolean mann = r.next() < 0.5;
        p.gender = mann ? 0.75 + 0.25 * r.next() : 0.25 * r.next();
        double years = 18 + 57 * Math.pow(r.next(), 1.3);
        p.age = ageFromYears(years);
        double alt = (years - 18) / 57;
        p.weight = clamp(0.42 + 0.18 * alt + 0.14 * r.gauss(), 0.15, 0.95);
        p.muscle = clamp(0.48 + (mann ? 0.08 : -0.04) - 0.1 * alt + 0.14 * r.gauss(), 0.1, 0.95);
        p.height = clamp(0.5 + 0.16 * r.gauss(), 0.1, 0.9);
        p.proportions = clamp(0.55 + 0.2 * r.gauss(), 0.15, 0.95);
        p.cupsize = mann ? 0.5 : clamp(0.5 + 0.18 * r.gauss(), 0.15, 0.9);
        p.firmness = mann ? 0.5 : clamp(0.6 - 0.25 * alt + 0.12 * r.gauss(), 0.1, 0.9);
        for (String f : FACE) {
            double v = clamp(0.28 * r.gauss(), -0.8, 0.8);
            if (Math.abs(v) > 0.02) p.face.put(f, round3(v));
        }
        // Haut (Phase 4): nach allen anderen Würfen, damit die Körper aus Phase 2/3 gleich bleiben
        p.melanin = 0.03 + 0.92 * Math.pow(r.next(), 1.25);
        double sp = r.next();
        p.freckles = p.melanin < 0.35 && sp < 0.35 ? 0.2 + 0.7 * r.next() : 0.06 * r.next();
        double bt = r.next();
        p.beard = mann ? 0.2 + 0.7 * bt : 0;
        p.redness = clamp(0.5 + 0.14 * r.gauss(), 0.15, 0.9);
        p.pores = clamp(0.5 + 0.14 * r.gauss(), 0.15, 0.9);
        p.oil = clamp(0.35 + 0.14 * r.gauss(), 0.05, 0.85);
        // Augen (Phase 5): helle Haut alle Farben, dunkle Haut meist braun
        double iu = r.next();
        p.iris = clamp(p.melanin < 0.4 ? iu : 0.55 + 0.45 * Math.pow(iu, 0.6), 0, 1);
        p.iris = round3(p.iris);
        // Haare (Phase 6): nach allen anderen Würfen
        double hs = r.next();
        if (mann) p.hair = hs < 0.40 ? "kurz" : hs < 0.55 ? "glatze" : hs < 0.70 ? "locken" : hs < 0.85 ? "bob" : "lang";
        else p.hair = hs < 0.25 ? "lang" : hs < 0.42 ? "bob" : hs < 0.58 ? "zopf" : hs < 0.74 ? "dutt" : hs < 0.88 ? "locken" : "kurz";
        double hc = r.next();
        // dunkle Haut fast immer dunkles Haar, helle Haut alle Farben
        p.hairColor = round3(clamp(p.melanin > 0.45 ? 0.82 + 0.18 * hc : 0.1 + 0.85 * hc, 0, 1));
        double hr = r.next();
        p.hairRed = round3(p.melanin < 0.3 && hr < 0.15 ? 0.6 + 0.4 * r.next() : 0.15 * r.next());
        p.grey = round3(clamp((years - 38) / 40 + 0.15 * r.gauss(), 0, 0.95));
        double bl = r.next();
        p.beardLength = round3(mann && bl < 0.45 ? 0.15 + 0.85 * r.next() : 0);
        p.melanin = round3(p.melanin); p.freckles = round3(p.freckles); p.beard = round3(p.beard);
        p.redness = round3(p.redness); p.pores = round3(p.pores); p.oil = round3(p.oil);
        p.gender = round3(p.gender); p.age = round3(p.age); p.weight = round3(p.weight); p.muscle = round3(p.muscle);
        p.height = round3(p.height); p.proportions = round3(p.proportions); p.cupsize = round3(p.cupsize); p.firmness = round3(p.firmness);
        p.name = (mann ? "Mann" : "Frau") + ", " + Math.round(years) + " · " + seed;
        return p;
    }

    // ------------------------------------------------------------ Ableiten (Phase 14, gleich in AV_API)

    /**
     * Dieselbe Person {@code jahre} älter (negativ: jünger), 1 bis 90 Jahre: Gewicht steigt, Muskeln und (bei Frauen)
     * Festigkeit nehmen ab wie beim Würfeln, das Haar wird grauer. Gleiche Rechnung wie {@code AV_API.aelter}.
     */
    public Person aelter(double jahre) {
        double y0 = ageYears();
        double y1 = clamp(y0 + jahre, 1, 90);
        double d = (y1 - y0) / 57;
        Person q = copy();
        q.age = round3(ageFromYears(y1));
        q.weight = round3(clamp(weight + 0.18 * d, 0.15, 0.95));
        q.muscle = round3(clamp(muscle - 0.1 * d, 0.1, 0.95));
        if (gender < 0.5) q.firmness = round3(clamp(firmness - 0.25 * d, 0.1, 0.9));
        q.grey = round3(clamp(grey + (y1 - y0) / 40, 0, 0.95));
        return q;
    }

    /**
     * Bruder oder Schwester: eine gewürfelte Person aus {@code seed}, die Gesicht, Haut, Haar- und Augenfarbe zur Hälfte
     * oder mehr von dieser Person hat. Gleiche Rechnung wie {@code AV_API.geschwister}.
     */
    public Person geschwister(long seed) {
        Person w = wuerfeln(seed);
        Person q = w.copy();
        q.face.clear();
        for (String f : FACE) {
            double v = round3((face.getOrDefault(f, 0.0) + w.face.getOrDefault(f, 0.0)) / 2);
            if (Math.abs(v) > 0.02) q.face.put(f, v);
        }
        q.melanin = round3(0.7 * melanin + 0.3 * w.melanin);
        q.hairColor = round3(0.7 * hairColor + 0.3 * w.hairColor);
        q.hairRed = round3(0.7 * hairRed + 0.3 * w.hairRed);
        q.iris = round3(0.7 * iris + 0.3 * w.iris);
        q.freckles = round3((freckles + w.freckles) / 2);
        q.height = round3((height + w.height) / 2);
        q.proportions = round3((proportions + w.proportions) / 2);
        q.name = (w.gender >= 0.5 ? "Bruder" : "Schwester") + " von " + name;
        return q;
    }

    // ------------------------------------------------------------ JSON (Phase 14, Spalte REGLER von AV_PERSON_T)

    /** Namen der Zahlenregler im JSON (wie die Felder), in fester Reihenfolge. */
    public static final String[] NUMBERS = {"gender", "age", "muscle", "weight", "height", "proportions", "cupsize", "firmness",
        "melanin", "redness", "freckles", "beard", "pores", "oil", "iris", "hairColor", "hairRed", "grey", "beardLength"};

    /** Wert eines Zahlenreglers nach Namen (siehe {@link #NUMBERS}). */
    public double number(String n) {
        return switch (n) {
            case "gender" -> gender; case "age" -> age; case "muscle" -> muscle; case "weight" -> weight; case "height" -> height;
            case "proportions" -> proportions; case "cupsize" -> cupsize; case "firmness" -> firmness; case "melanin" -> melanin;
            case "redness" -> redness; case "freckles" -> freckles; case "beard" -> beard; case "pores" -> pores; case "oil" -> oil;
            case "iris" -> iris; case "hairColor" -> hairColor; case "hairRed" -> hairRed; case "grey" -> grey; case "beardLength" -> beardLength;
            default -> throw new IllegalArgumentException("Regler unbekannt: " + n);
        };
    }

    /** Zahlenregler setzen. */
    public void number(String n, double v) {
        switch (n) {
            case "gender" -> gender = v; case "age" -> age = v; case "muscle" -> muscle = v; case "weight" -> weight = v; case "height" -> height = v;
            case "proportions" -> proportions = v; case "cupsize" -> cupsize = v; case "firmness" -> firmness = v; case "melanin" -> melanin = v;
            case "redness" -> redness = v; case "freckles" -> freckles = v; case "beard" -> beard = v; case "pores" -> pores = v; case "oil" -> oil = v;
            case "iris" -> iris = v; case "hairColor" -> hairColor = v; case "hairRed" -> hairRed = v; case "grey" -> grey = v; case "beardLength" -> beardLength = v;
            default -> throw new IllegalArgumentException("Regler unbekannt: " + n);
        }
    }

    /**
     * Alle Regler als JSON (ohne Namen und {@code seed}, die stehen im Typ): Zahlen, {@code hair}, {@code face} und
     * {@code forms} als Objekte. So steht es in der Spalte REGLER von {@code AV_PERSON_T}.
     */
    public String toJson() {
        StringBuilder b = new StringBuilder("{");
        for (String n : NUMBERS) b.append('"').append(n).append("\":").append(num(number(n))).append(',');
        b.append("\"hair\":\"").append(hair).append('"');
        b.append(",\"face\":{");
        boolean first = true;
        for (Map.Entry<String, Double> e : face.entrySet()) { if (!first) b.append(','); first = false; b.append('"').append(e.getKey()).append("\":").append(num(e.getValue())); }
        b.append("},\"forms\":{");
        first = true;
        for (Map.Entry<String, Double> e : forms.entrySet()) { if (!first) b.append(','); first = false; b.append('"').append(e.getKey()).append("\":").append(num(e.getValue())); }
        return b.append("}}").toString();
    }

    /** Person aus JSON (wie {@link #toJson}); fehlende Regler bleiben auf der Mitte. */
    @SuppressWarnings("unchecked")
    public static Person fromJson(String json, long seed, String name) {
        Object o = com.dan.gltf.Json.parse(json);
        Person p = new Person();
        p.seed = seed;
        p.name = name == null ? "" : name;
        Map<String, Object> m = (Map<String, Object>) o;
        for (String n : NUMBERS) if (m.get(n) instanceof Number x) p.number(n, x.doubleValue());
        if (m.get("hair") instanceof String h) p.hair = h;
        if (m.get("face") instanceof Map<?, ?> f) for (Map.Entry<?, ?> e : f.entrySet()) p.face.put((String) e.getKey(), ((Number) e.getValue()).doubleValue());
        if (m.get("forms") instanceof Map<?, ?> f) for (Map.Entry<?, ?> e : f.entrySet()) p.forms.put((String) e.getKey(), ((Number) e.getValue()).doubleValue());
        return p;
    }

    /** Gleich in allen Reglern (Name und seed zählen nicht)? */
    public boolean sameAs(Person o) {
        for (String n : NUMBERS) if (number(n) != o.number(n)) return false;
        return hair.equals(o.hair) && face.equals(o.face) && forms.equals(o.forms);
    }

    private static String num(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    private static double round3(double v) { return Math.round(v * 1000) / 1000.0; }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    /** SplitMix64, gleich auf jeder Maschine. */
    public static final class Rng {
        private long s;
        public Rng(long seed) { s = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L; }
        public long nextLong() {
            long z = (s += 0x9E3779B97F4A7C15L);
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }
        public double next() { return (nextLong() >>> 11) * 0x1.0p-53; }
        /** Normalverteilt (Box-Muller, eine Hälfte). */
        public double gauss() {
            double u = Math.max(next(), 1e-12), v = next();
            return Math.sqrt(-2 * Math.log(u)) * Math.cos(2 * Math.PI * v);
        }
    }

    // ------------------------------------------------------------ Gewichte

    /** Stützkurven aus MPFB macro.json: {unten, oben, Komponente unten, Komponente oben}. */
    private static final Object[][] PARTS_GENDER = {{-0.01, 1.01, "female", "male"}};
    private static final Object[][] PARTS_AGE = {{-0.01, 0.1874998, "baby", "child"}, {0.1874999, 0.49998, "child", "young"}, {0.49999, 1.01, "young", "old"}};
    private static final Object[][] PARTS_MUSCLE = {{-0.01, 0.49998, "minmuscle", "averagemuscle"}, {0.49999, 1.01, "averagemuscle", "maxmuscle"}};
    private static final Object[][] PARTS_WEIGHT = {{-0.01, 0.49998, "minweight", "averageweight"}, {0.49999, 1.01, "averageweight", "maxweight"}};
    private static final Object[][] PARTS_PROP = {{-0.01, 0.4999, "uncommonproportions", null}, {0.5, 1.01, null, "idealproportions"}};
    private static final Object[][] PARTS_HEIGHT = {{-0.01, 0.49, "minheight", null}, {0.51, 1.01, null, "maxheight"}};
    private static final Object[][] PARTS_CUP = {{-0.01, 0.49998, "mincup", "averagecup"}, {0.49999, 1.01, "averagecup", "maxcup"}};
    private static final Object[][] PARTS_FIRM = {{-0.01, 0.4998, "minfirmness", "averagefirmness"}, {0.4999, 1.01, "averagefirmness", "maxfirmness"}};

    /** Komponenten eines Reglers wie MPFB _interpolate_macro_components (Python round, also halbe auf gerade). */
    static Map<String, Double> part(Object[][] parts, double v) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Object[] p : parts) {
            double lo = (Double) p[0], hi = (Double) p[1];
            if (v > lo && v < hi) {
                double pct = (v - lo) / (hi - lo);
                if (p[2] != null) out.merge((String) p[2], round4(1 - pct), Double::sum);
                if (p[3] != null) out.merge((String) p[3], round4(pct), Double::sum);
            }
        }
        return out;
    }

    static double round4(double x) {
        return new BigDecimal(x).setScale(4, RoundingMode.HALF_EVEN).doubleValue();
    }

    private static final Map<String, String> G = Map.of("female", "w", "male", "m");
    private static final Map<String, String> A = Map.of("baby", "baby", "child", "kind", "young", "jung", "old", "alt");

    /**
     * Gewichte aller Morphziele für diese Person, Schlüssel = Zielname im Modell.
     * Gleiche Rechnung wie {@code makro_gewichte} und {@code gewichte} in tools/blender/avatar_build.py.
     */
    public Map<String, Double> weights() {
        Map<String, Double> w = new LinkedHashMap<>();
        w.put("macro.basis", -1.0);
        Map<String, Double> cg = part(PARTS_GENDER, gender), ca = part(PARTS_AGE, age), cm = part(PARTS_MUSCLE, muscle),
                cw = part(PARTS_WEIGHT, weight), ch = part(PARTS_HEIGHT, height), cp = part(PARTS_PROP, proportions),
                cc = part(PARTS_CUP, cupsize), cf = part(PARTS_FIRM, firmness);
        for (Map.Entry<String, Double> g : cg.entrySet())
            for (Map.Entry<String, Double> a : ca.entrySet()) {
                String e = "macro." + G.get(g.getKey()) + "." + A.get(a.getKey());
                double ga = g.getValue() * a.getValue();
                add(w, e, ga, false);
                for (String m : new String[]{"minmuscle", "maxmuscle"}) add(w, e + ".muscle." + m.substring(0, 3), ga * cm.getOrDefault(m, 0.0), false);
                for (String x : new String[]{"minweight", "maxweight"}) add(w, e + ".weight." + x.substring(0, 3), ga * cw.getOrDefault(x, 0.0), false);
                for (String m : new String[]{"minmuscle", "maxmuscle"})
                    for (String x : new String[]{"minweight", "maxweight"})
                        add(w, e + ".mw." + m.substring(0, 3) + "." + x.substring(0, 3), ga * cm.getOrDefault(m, 0.0) * cw.getOrDefault(x, 0.0), false);
                for (String h : new String[]{"minheight", "maxheight"}) add(w, e + ".height." + h.substring(0, 3), ga * ch.getOrDefault(h, 0.0), true);
                for (String pr : new String[]{"uncommonproportions", "idealproportions"})
                    add(w, e + ".prop." + pr.substring(0, pr.length() - 11), ga * cp.getOrDefault(pr, 0.0), true);
            }
        for (Map.Entry<String, Double> a : ca.entrySet()) {
            if (a.getKey().equals("baby")) continue;
            for (Map.Entry<String, Double> c : cc.entrySet())
                for (Map.Entry<String, Double> f : cf.entrySet()) {
                    if (c.getKey().equals("averagecup") && f.getKey().equals("averagefirmness")) continue;
                    String cu = c.getKey().substring(0, c.getKey().length() - 3), fi = f.getKey().substring(0, f.getKey().length() - 8);
                    add(w, "macro.brust." + A.get(a.getKey()) + "." + cu + "." + fi, a.getValue() * c.getValue() * f.getValue(), true);
                }
        }
        for (Map.Entry<String, Double> e : face.entrySet()) {
            double v = e.getValue();
            if (v < 0) w.put("face." + e.getKey() + ".decr", -v);
            else if (v > 0) w.put("face." + e.getKey() + ".incr", v);
        }
        for (Map.Entry<String, Double> e : forms.entrySet())
            if (e.getValue() != 0) w.put(e.getKey(), e.getValue());
        return w;
    }

    private static void add(Map<String, Double> w, String k, double x, boolean cut) {
        // MPFB lässt Ziele mit Gewicht ≤ 0,01 weg; für Größe, Proportionen und Brust nachgebildet
        if (x != 0 && !(cut && x <= 0.01)) w.merge(k, x, Double::sum);
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.GERMANY, "%s (Geschlecht %.2f, %.0f Jahre, Muskeln %.2f, Gewicht %.2f, Größe %.2f)",
                name.isEmpty() ? "Person" : name, gender, ageYears(), muscle, weight, height);
    }
}
