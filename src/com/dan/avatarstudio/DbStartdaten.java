package com.dan.avatarstudio;

import com.dan.avatar.Emotion;
import com.dan.avatar.Fabric;
import com.dan.avatar.Outfit;
import com.dan.avatar.Person;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die Startdaten des Schemas AVATAR (Phase 14) aus dem Java-Code: Regler, Frisuren, Gesichtsformen, Viseme,
 * Gefühle mit ihren Formen, Posen, Gesten, Epochen, Rollen, Stoffe, Kleidung, Outfits mit Stücken, Presets und die
 * Prüfsummen der Ressourcen. So steht jeder Katalog an einer Stelle: im Package. {@link #main} schreibt
 * {@code db/sql/06_startdaten.sql}; der Einrichter prüft mit {@link #soll}, ob alle mitgelieferten Zeilen da sind.
 *
 * <p>Jede Zeile ist ein MERGE: fehlt sie, wird sie angelegt; ist sie MITGELIEFERT, wird sie auf den Stand des Packages
 * gebracht; ist sie EIGEN, bleibt sie, wie sie ist.
 */
public final class DbStartdaten {

    private DbStartdaten() {}

    /** Ein Katalog: Tabelle, Schlüsselspalten, Zeilen (Spalte → Wert; String, Number, null oder {@link Roh}). */
    record Katalog(String tabelle, String[] schluessel, List<Map<String, Object>> zeilen) {}

    /** SQL-Ausdruck, der unverändert eingesetzt wird (Unterabfrage, Typ-Konstruktor). */
    record Roh(String sql) {}

    static Map<String, Object> zeile(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    // ------------------------------------------------------------------ Kataloge

    static final String[][] REGLER = {
        // Name, Gruppe, Bezeichnung, Morphziel
        {"gender", "KOERPER", "Geschlecht (0 weiblich … 1 männlich)", "macro.<w|m>.*"},
        {"age", "KOERPER", "Alter (0 = 1, 0,1875 = 11, 0,5 = 25, 1 = 90 Jahre)", "macro.*.<baby|kind|jung|alt>"},
        {"muscle", "KOERPER", "Muskeln", "macro.*.muscle.*"},
        {"weight", "KOERPER", "Gewicht", "macro.*.weight.*"},
        {"height", "KOERPER", "Größe", "macro.*.height.*"},
        {"proportions", "KOERPER", "Proportionen", "macro.*.prop.*"},
        {"cupsize", "KOERPER", "Brust", "macro.brust.*"},
        {"firmness", "KOERPER", "Festigkeit", "macro.brust.*"},
        {"melanin", "HAUT", "Hautton (Melanin, 0 sehr hell … 1 sehr dunkel)", null},
        {"redness", "HAUT", "Rötung", null},
        {"freckles", "HAUT", "Sommersprossen", null},
        {"beard", "HAUT", "Bartschatten", null},
        {"pores", "HAUT", "Poren und Relief", null},
        {"oil", "HAUT", "Fettglanz", null},
        {"iris", "AUGEN", "Iris (0 blau, 0,25 grün, 0,5 haselnuss, 0,75 braun, 1 dunkelbraun)", null},
        {"hairColor", "HAARE", "Haarfarbe (Eumelanin, 0 platinblond … 1 schwarz)", null},
        {"hairRed", "HAARE", "Rotanteil (Phäomelanin)", null},
        {"grey", "HAARE", "Anteil grauer Haare", null},
        {"beardLength", "HAARE", "Bartlänge (0 nur Schatten … 1 Vollbart)", null}};

    static final Map<String, Double> VORGABE = Map.ofEntries(
            Map.entry("gender", 0.5), Map.entry("age", 0.5), Map.entry("muscle", 0.5), Map.entry("weight", 0.5), Map.entry("height", 0.5),
            Map.entry("proportions", 0.5), Map.entry("cupsize", 0.5), Map.entry("firmness", 0.5), Map.entry("melanin", 0.25),
            Map.entry("redness", 0.5), Map.entry("freckles", 0.0), Map.entry("beard", 0.0), Map.entry("pores", 0.5), Map.entry("oil", 0.35),
            Map.entry("iris", 0.6), Map.entry("hairColor", 0.6), Map.entry("hairRed", 0.1), Map.entry("grey", 0.0), Map.entry("beardLength", 0.0));

    static final String[][] FRISUR = {
        {"bob", "Bob", "bob02", "CC0"}, {"lang", "Lang offen", "long01", "CC0"}, {"zopf", "Zopf", "braid01", "CC0"},
        {"dutt", "Dutt (Zopfkrone)", "rev_fr_braidbun1", "CC BY"}, {"kurz", "Kurz", "short02", "CC0"},
        {"locken", "Locken", "afro01", "CC0"}, {"glatze", "Glatze", null, null}};

    static final String[][] VISEM = {
        {"visem.viseme_sil", "Stille"}, {"visem.viseme_PP", "p, b, m"}, {"visem.viseme_FF", "f, v, w"}, {"visem.viseme_TH", "th"},
        {"visem.viseme_DD", "t, d"}, {"visem.viseme_kk", "k, g"}, {"visem.viseme_CH", "sch, tsch, ch"}, {"visem.viseme_SS", "s, z"},
        {"visem.viseme_nn", "n, l"}, {"visem.viseme_RR", "r"}, {"visem.viseme_aa", "a"}, {"visem.viseme_E", "e, ä"},
        {"visem.viseme_I", "i, ü"}, {"visem.viseme_O", "o, ö"}, {"visem.viseme_U", "u"}};

    static final String[][] POSE = {
        {"stehen", "POSE", "Stehen (Ruhe, Grundlage des Gangs)"}, {"sitzen", "POSE", "Sitzen auf einer Bank"},
        {"lehnen", "POSE", "Lehnen (linkes Standbein)"}, {"hand_ans_gesicht", "POSE", "Hand ans Gesicht"},
        {"unterarm_drehen", "POSE", "Unterarm gedreht (Prüfpose)"}, {"gehen", "BEWEGUNG", "Gehen (Formel und IK)"},
        {"drehen", "BEWEGUNG", "Drehen auf der Stelle"}, {"aufstehen", "BEWEGUNG", "Aufstehen von der Bank"}};

    static final Object[][] GESTE = {
        {"winken", "Winken mit der rechten Hand", 2.5}, {"zeigen", "Zeigen mit dem Zeigefinger", 2.5},
        {"buecken", "Bücken", 2.5}, {"kapuze_auf", "Kapuze mit beiden Händen aufsetzen", 1.8},
        {"kapuze_ab", "Kapuze mit beiden Händen abnehmen", 1.8}};

    static final Object[][] EPOCHE = {
        {"HEUTE", "Gegenwart", null, null}, {"MITTELALTER_1362", "Nordfriesland 1362 (Rungholt)", 1300, 1362},
        {"WIKINGERZEIT", "Wikingerzeit (Haithabu)", 770, 1066}};

    static final String[][] ROLLE = {
        // Name, Bezeichnung, Kleiderart, Epoche, Szene
        {"Wärter", "Leuchtturmwärterin oder -wärter auf der Galerie", "DRAUSSEN", "HEUTE", "Lighthouse"},
        {"Wattführer", "Wattführerin oder Wattführer", "DRAUSSEN", "HEUTE", "Rungholt"},
        {"Wanderer", "Mitglied einer Wandergruppe", "DRAUSSEN", "HEUTE", "Rungholt"},
        {"Forscher", "Forscherin oder Forscher am Gletscher", "DRAUSSEN", "HEUTE", "Glacier"},
        {"Spaziergänger", "Spaziergängerin oder Spaziergänger am Steg", "ALLTAG", "HEUTE", "Avatar Studio"},
        {"Erzähler", "Erzählerin oder Erzähler im Kino", "ALLTAG", "HEUTE", "Kino"},
        {"Händler", "Händlerin oder Händler", "ARBEIT", "WIKINGERZEIT", "Haithabu"},
        {"Schmied", "Schmiedin oder Schmied", "ARBEIT", "WIKINGERZEIT", "Haithabu"},
        {"Bootsbauer", "Bootsbauerin oder Bootsbauer", "ARBEIT", "WIKINGERZEIT", "Haithabu"},
        {"Bauer 1362", "Bäuerin oder Bauer in Rungholt", "ARBEIT", "MITTELALTER_1362", "Rungholt"},
        {"Fischer 1362", "Fischerin oder Fischer in Rungholt", "DRAUSSEN", "MITTELALTER_1362", "Rungholt"}};

    static final Map<String, String[]> KLEIDUNG = new LinkedHashMap<>();
    static {
        KLEIDUNG.put("male_casualsuit01", new String[]{"Hemd und Jeans", "GANZ"});
        KLEIDUNG.put("sweater_fisherman", new String[]{"Fischerpullover", "OBERTEIL"});
        KLEIDUNG.put("Monks_Hood", new String[]{"Kapuze (auf)", "KAPUZE"});
        KLEIDUNG.put("Monks_Hood_Down", new String[]{"Kapuze (ab, im Nacken)", "KAPUZE"});
        KLEIDUNG.put("Monks_Robe", new String[]{"Kutte", "GANZ"});
        KLEIDUNG.put("pants_wool", new String[]{"Wollhose", "HOSE"});
        KLEIDUNG.put("cargo_pants", new String[]{"Arbeitshose", "HOSE"});
        KLEIDUNG.put("jean_shorts", new String[]{"Jeansshorts", "HOSE"});
        KLEIDUNG.put("t_shirt_basic_tucked", new String[]{"T-Shirt", "OBERTEIL"});
        KLEIDUNG.put("boots_ankle", new String[]{"Stiefeletten", "SCHUHE"});
        KLEIDUNG.put("hero_boots_2", new String[]{"Gummistiefel", "SCHUHE"});
        KLEIDUNG.put("mj_shoes", new String[]{"Stoffschuhe", "SCHUHE"});
        KLEIDUNG.put("tunicviking", new String[]{"Tunika (Wikingerzeit)", "OBERTEIL"});
        KLEIDUNG.put("pantsviking", new String[]{"Hose (Wikingerzeit)", "HOSE"});
        KLEIDUNG.put("bootsviking", new String[]{"Wendeschuhe", "SCHUHE"});
        // ab Fassung 1.8, nach Dateinamen (die Netznamen vergibt die .mhclo)
        KLEIDUNG.put(Outfit.FEMALE_CASUAL, new String[]{"Damen-Freizeitkleidung", "GANZ"});
        KLEIDUNG.put(Outfit.FEMALE_CASUAL2, new String[]{"Damen-Freizeitkleidung 2", "GANZ"});
        KLEIDUNG.put(Outfit.FEMALE_ELEGANT, new String[]{"Elegante Damenkleidung", "GANZ"});
        KLEIDUNG.put(Outfit.FEMALE_SPORT, new String[]{"Damen-Sportkleidung", "GANZ"});
    }

    static final Map<String, String> OUTFIT_TEXT = Map.ofEntries(
            Map.entry("Wollkleid", "Langes Kleid aus weinroter Wolle, Stiefeletten"),
            Map.entry("Damen Alltag", "Damen-Freizeitkleidung (Textur der Kleidung)"),
            Map.entry("Damen Freizeit", "Damen-Freizeitkleidung, zweite Fassung (Textur der Kleidung)"),
            Map.entry("Elegant", "Elegante Damenkleidung (Textur der Kleidung)"),
            Map.entry("Sport", "Damen-Sportkleidung (Textur der Kleidung)"),
            Map.entry("Strickkapuze", "Dunkle Wolle mit Kapuze, Wasserperlen auf dem Strick (wie die Vorlage)"),
            Map.entry("Regenjacke", "Ölzeug (Friesennerz): Kittel und Kapuze gelb, Gummistiefel"),
            Map.entry("Winter", "Fischerpullover, Wollhose, Stiefeletten"),
            Map.entry("Sommer", "T-Shirt, Jeansshorts, Stoffschuhe"),
            Map.entry("1362", "Kutte aus ungefärbter Wolle mit Kapuze, Wendeschuhe"),
            Map.entry("Wikingerzeit", "Tunika aus Leinen, Hose aus Wolle, Lederschuhe"),
            Map.entry("Alltag", "Hemd und Jeans (Textur der Kleidung)"));

    /** Mitgelieferte Personen: Name des Presets, Person, Outfit, Bemerkung. */
    static List<Object[]> presets() {
        List<Object[]> l = new ArrayList<>();
        Person w = AvatarStudio.templateWoman();
        Person m = AvatarStudio.templateMan();
        l.add(new Object[]{"Regennacht", w, "Strickkapuze", "Frau im Regen: Stimmung der Vorlage templates/E_Music.jpg, nicht ihr Gesicht (F21)"});
        l.add(new Object[]{"Mann im Regen", m, "Regenjacke", "Der Mann aus dem Meilenstein „Ein Gesicht im Regen“"});
        l.add(new Object[]{"Würfel 4711", Person.wuerfeln(4711), "Alltag", "Person.wuerfeln(4711), wie AV_API.WUERFELN(4711)"});
        l.add(new Object[]{"Würfel 1362", Person.wuerfeln(1362), "1362", "Person.wuerfeln(1362) in der Tracht von Rungholt"});
        return l;
    }

    /** Alle Kataloge in der Reihenfolge, in der sie geschrieben werden (Eltern vor Kindern). */
    static List<Katalog> kataloge() throws Exception {
        List<Katalog> k = new ArrayList<>();
        List<Map<String, Object>> z;

        z = new ArrayList<>();
        int r = 0;
        for (String[] g : REGLER)
            z.add(zeile("NAME", g[0], "GRUPPE", g[1], "BEZEICHNUNG", g[2], "MIN_WERT", 0.0, "MAX_WERT", 1.0, "VORGABE", VORGABE.get(g[0]), "MORPHZIEL", g[3], "REIHENFOLGE", ++r));
        for (String f : Person.FACE)
            z.add(zeile("NAME", "face." + f, "GRUPPE", "GESICHT", "BEZEICHNUNG", "Gesicht: " + f.replace('.', ' '), "MIN_WERT", -1.0, "MAX_WERT", 1.0,
                    "VORGABE", 0.0, "MORPHZIEL", "face." + f + ".decr/.incr", "REIHENFOLGE", ++r));
        k.add(new Katalog("AV_REGLER", new String[]{"NAME"}, z));

        z = new ArrayList<>();
        for (String[] f : FRISUR) z.add(zeile("NAME", f[0], "BEZEICHNUNG", f[1], "NETZ", f[2], "LIZENZ", f[3]));
        k.add(new Katalog("AV_FRISUR", new String[]{"NAME"}, z));

        z = new ArrayList<>();
        for (String a : Emotion.ARKIT) {
            String g = a.replaceAll("^([a-z]+).*", "$1").toUpperCase(Locale.ROOT);
            String s = a.endsWith("Left") ? "L" : a.endsWith("Right") ? "R" : null;
            z.add(zeile("NAME", "arkit." + a, "GRUPPE", g, "SEITE", s));
        }
        k.add(new Katalog("AV_FORM", new String[]{"NAME"}, z));

        z = new ArrayList<>();
        r = 0;
        for (String[] v : VISEM) z.add(zeile("NAME", v[0], "LAUTE", v[1], "REIHENFOLGE", ++r));
        k.add(new Katalog("AV_VISEM", new String[]{"NAME"}, z));

        z = new ArrayList<>();
        List<Map<String, Object>> zf = new ArrayList<>();
        r = 0;
        for (Emotion.Def d : Emotion.NAMED) {
            z.add(zeile("NAME", d.name, "VALENZ", d.valence, "ERREGUNG", d.arousal, "KOPF_SENKEN", d.headDown, "BLICK_SENKEN", d.gazeDown,
                    "KOPF_NEIGEN", d.headTilt, "SCHULTERN", d.shoulders, "LIDSCHLAG", d.blinkGap, "ATEM_TAKT", d.breathRate,
                    "ATEM_TIEFE", d.breathDepth, "SAKKADEN", d.saccadeRate, "ABWENDEN", d.aversion, "REIHENFOLGE", ++r));
            for (Map.Entry<String, Double> e : d.forms.entrySet())
                zf.add(zeile("GEFUEHL_ID", new Roh("(SELECT ID FROM AV_GEFUEHL WHERE NAME = " + q(d.name) + ")"), "FORM", e.getKey(), "GEWICHT", e.getValue()));
        }
        k.add(new Katalog("AV_GEFUEHL", new String[]{"NAME"}, z));
        k.add(new Katalog("AV_GEFUEHL_FORM", new String[]{"GEFUEHL_ID", "FORM"}, zf));

        z = new ArrayList<>();
        for (String[] p : POSE) z.add(zeile("NAME", p[0], "ART", p[1], "BEZEICHNUNG", p[2]));
        k.add(new Katalog("AV_POSE", new String[]{"NAME"}, z));

        z = new ArrayList<>();
        for (Object[] g : GESTE) z.add(zeile("NAME", g[0], "BEZEICHNUNG", g[1], "DAUER_S", g[2]));
        k.add(new Katalog("AV_GESTE", new String[]{"NAME"}, z));

        z = new ArrayList<>();
        for (Object[] e : EPOCHE) z.add(zeile("NAME", e[0], "BEZEICHNUNG", e[1], "VON_JAHR", e[2], "BIS_JAHR", e[3]));
        k.add(new Katalog("AV_EPOCHE", new String[]{"NAME"}, z));

        z = new ArrayList<>();
        for (String[] o : ROLLE) z.add(zeile("NAME", o[0], "BEZEICHNUNG", o[1], "KLEIDERART", o[2], "EPOCHE", o[3], "SZENE", o[4]));
        k.add(new Katalog("AV_ROLLE", new String[]{"NAME"}, z));

        z = new ArrayList<>();
        for (Fabric f : Fabric.values())
            z.add(zeile("NAME", f.name(), "BEZEICHNUNG", f.label, "RAUHEIT", f.rough, "FLAUM", f.sheen, "BINDUNG", f.weave, "MASSSTAB", f.scale,
                    "SAUGT", f.absorb, "PERLEN", f.beading, "NASSGLANZ", f.wetGloss, "FARBE_R", f.color[0], "FARBE_G", f.color[1], "FARBE_B", f.color[2]));
        k.add(new Katalog("AV_STOFF", new String[]{"NAME"}, z));

        z = new ArrayList<>();
        Object kj = com.dan.gltf.Json.parse(new String(GlbInfo.resource(DbStartdaten.class, "/com/dan/avatar/res/avatar_base.kleidung.json"), StandardCharsets.UTF_8));
        for (Object t : com.dan.gltf.Json.arr(kj, "teile")) {
            String netz = com.dan.gltf.Json.str(t, "netz");
            String[] b = KLEIDUNG.getOrDefault(netz, KLEIDUNG.getOrDefault(com.dan.gltf.Json.str(t, "datei").replace(".mhclo", ""), new String[]{netz, "OBERTEIL"}));
            String liz = com.dan.gltf.Json.str(t, "lizenz").replaceAll("[^A-Za-z0-9 ]", "").trim().replace("CC0", "CC0").toUpperCase(Locale.ROOT).replace(" ", "");
            z.add(zeile("NETZ", netz, "BEZEICHNUNG", b[0], "ART", b[1], "BIT", (int) com.dan.gltf.Json.num(t, "bit", -1),
                    "DATEI", com.dan.gltf.Json.str(t, "datei"), "LIZENZ", liz));
        }
        k.add(new Katalog("AV_KLEIDUNG", new String[]{"NETZ"}, z));

        z = new ArrayList<>();
        List<Map<String, Object>> zt = new ArrayList<>();
        r = 0;
        com.dan.avatar.Wardrobe w = com.dan.avatar.Wardrobe.parse(new String(GlbInfo.resource(DbStartdaten.class, "/com/dan/avatar/res/avatar_base.kleidung.json"), StandardCharsets.UTF_8));
        for (String n : Outfit.PRESETS) {
            if (n.equals("ohne") || !Outfit.preset(n).complete(w)) continue;      // Teile noch nicht im Grundkörper
            z.add(zeile("NAME", n, "BEZEICHNUNG", OUTFIT_TEXT.getOrDefault(n, n), "REIHENFOLGE", ++r));
            int i = 0;
            for (Outfit.Piece p : Outfit.preset(n).pieces)
                zt.add(zeile("OUTFIT_ID", new Roh("(SELECT ID FROM AV_OUTFIT WHERE NAME = " + q(n) + ")"), "REIHENFOLGE", ++i, "NETZ", w.find(p.mesh).mesh,
                        "STOFF", p.fabric == null ? null : p.fabric.name(), "FARBE_R", p.color == null ? null : p.color[0],
                        "FARBE_G", p.color == null ? null : p.color[1], "FARBE_B", p.color == null ? null : p.color[2]));
        }
        k.add(new Katalog("AV_OUTFIT", new String[]{"NAME"}, z));
        k.add(new Katalog("AV_OUTFIT_TEIL", new String[]{"OUTFIT_ID", "REIHENFOLGE"}, zt));

        z = new ArrayList<>();
        for (Object[] p : presets())
            z.add(zeile("NAME", p[0], "PERSON", new Roh(typ((Person) p[1], (String) p[0])), "OUTFIT", p[2], "BEMERKUNG", p[3]));
        k.add(new Katalog("AV_PRESET", new String[]{"NAME"}, z));

        z = new ArrayList<>();
        byte[] pj = GlbInfo.resource(DbStartdaten.class, AvatarStudio.PROTOKOLL);
        Object proto = com.dan.gltf.Json.parse(new String(pj, StandardCharsets.UTF_8));
        z.add(zeile("NAME", "avatar_base.glb", "ART", "GRUNDKOERPER", "SHA256", com.dan.gltf.Json.str(proto, "sha256"),
                "BYTES", (long) com.dan.gltf.Json.num(proto, "bytes", 0), "FASSUNG", "1.7", "LIZENZ", "CC0"));
        String[][] res = {{"avatar_base.rig.json", "RIG"}, {"avatar_base.kleidung.json", "KLEIDUNG"}, {"avatar_base.protokoll.json", "PROTOKOLL"}, {"haut/haut.json", "HAUT"}};
        for (String[] f : res) {
            byte[] b = GlbInfo.resource(DbStartdaten.class, "/com/dan/avatar/res/" + f[0]);
            z.add(zeile("NAME", f[0], "ART", f[1], "SHA256", sha256(b), "BYTES", (long) b.length, "FASSUNG", "1.7", "LIZENZ", "CC0"));
        }
        k.add(new Katalog("AV_DATEI", new String[]{"NAME"}, z));
        return k;
    }

    /** AV_PERSON_T-Konstruktor für eine Person. */
    static String typ(Person p, String preset) {
        double alterJ = Math.round(p.ageYears() * 10) / 10.0;
        return "AV_PERSON_T(" + p.seed + ", 1, " + n(p.gender) + ", " + n(alterJ) + ", " + q(preset) + ", " + q(p.name) + ", " + q(p.toJson()) + ")";
    }

    // ------------------------------------------------------------------ SQL

    /** Der ganze Text von 06_startdaten.sql. */
    public static String sql() throws Exception {
        StringBuilder b = new StringBuilder();
        b.append("-- =====================================================================================================\n");
        b.append("-- Avatar · Datenbank · 06_startdaten.sql\n");
        b.append("-- Erzeugt aus dem Java-Code mit com.dan.avatarstudio.DbStartdaten (ant db-startdaten), nicht von Hand ändern.\n");
        b.append("-- Jede Zeile ein MERGE: fehlt sie, wird sie angelegt; MITGELIEFERT wird auf den Stand des Packages gebracht;\n");
        b.append("-- EIGEN bleibt, wie es ist. Wiederholbar. Dieses Skript löscht nichts.\n");
        b.append("-- =====================================================================================================\n\n");
        b.append("INSERT INTO AV_SCHEMA (VERSION, APP_VERSION, TEXT) VALUES (1, 'Phase 14', 'Typen, Kataloge, Presets, AV_API, Metadaten');\n\n");
        for (Katalog k : kataloge()) {
            b.append("-- ").append(k.tabelle()).append(": ").append(k.zeilen().size()).append(" Zeilen\n");
            for (Map<String, Object> z : k.zeilen()) b.append(merge(k, z)).append('\n');
            b.append('\n');
        }
        b.append("COMMIT;\n\n-- Ende 06_startdaten.sql\n");
        return b.toString();
    }

    static String merge(Katalog k, Map<String, Object> z) {
        StringBuilder sel = new StringBuilder(), on = new StringBuilder(), upd = new StringBuilder(), cols = new StringBuilder(), vals = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> e : z.entrySet()) {
            if (!first) sel.append(", ");
            sel.append(wert(e.getValue())).append(' ').append(e.getKey());
            first = false;
        }
        for (String s : k.schluessel()) {
            if (on.length() > 0) on.append(" AND ");
            on.append("t.").append(s).append(" = s.").append(s);
        }
        java.util.Set<String> key = java.util.Set.of(k.schluessel());
        for (String c : z.keySet()) {
            if (!key.contains(c)) { if (upd.length() > 0) upd.append(", "); upd.append("t.").append(c).append(" = s.").append(c); }
            cols.append(c).append(", ");
            vals.append("s.").append(c).append(", ");
        }
        return "MERGE INTO " + k.tabelle() + " t USING (SELECT " + sel + " FROM DUAL) s ON (" + on + ")"
                + (upd.length() > 0 ? " WHEN MATCHED THEN UPDATE SET " + upd + " WHERE t.HERKUNFT = 'MITGELIEFERT'" : "")
                + " WHEN NOT MATCHED THEN INSERT (" + cols + "HERKUNFT) VALUES (" + vals + "'MITGELIEFERT');";
    }

    static String wert(Object v) {
        if (v == null) return "NULL";
        if (v instanceof Roh r) return r.sql();
        if (v instanceof Number x) return n(x.doubleValue());
        return q(v.toString());
    }

    static String q(String s) { return s == null ? "NULL" : "'" + s.replace("'", "''") + "'"; }

    static String n(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    static String sha256(byte[] b) throws Exception {
        StringBuilder s = new StringBuilder();
        for (byte x : MessageDigest.getInstance("SHA-256").digest(b)) s.append(String.format("%02x", x));
        return s.toString();
    }

    /** Soll-Zahlen der mitgelieferten Zeilen je Tabelle für den Einrichter. */
    public static List<com.dan.store.Einrichter.Soll> soll() {
        List<com.dan.store.Einrichter.Soll> l = new ArrayList<>();
        try {
            for (Katalog k : kataloge())
                l.add(new com.dan.store.Einrichter.Soll(k.tabelle() + " (Startdaten)",
                        "SELECT TO_CHAR(COUNT(*)) FROM " + k.tabelle() + " WHERE HERKUNFT = 'MITGELIEFERT'", k.zeilen().size()));
        } catch (Exception e) {
            throw new IllegalStateException("Startdaten: " + e.getMessage(), e);
        }
        return l;
    }

    /** Schreibt db/sql/06_startdaten.sql (Argument: Ordner db/sql). */
    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "db/sql");
        File f = new File(dir, "06_startdaten.sql");
        String s = sql();
        Files.writeString(f.toPath(), s, StandardCharsets.UTF_8);
        int n = 0;
        for (Katalog k : kataloge()) n += k.zeilen().size();
        System.out.println("06_startdaten.sql: " + n + " Zeilen in " + kataloge().size() + " Tabellen → " + f.getAbsolutePath());
    }
}
