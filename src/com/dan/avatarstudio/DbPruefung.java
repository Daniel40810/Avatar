package com.dan.avatarstudio;

import com.dan.avatar.Emotion;
import com.dan.avatar.Outfit;
import com.dan.avatar.Person;
import com.dan.store.Db;
import com.dan.store.Einrichter;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Prüfung der Datenbank (Abnahme Phase 14), mit Verbindung: richtet AVATAR ein (zweimal), vergleicht den Würfel in
 * PL/SQL mit dem in Java für viele seeds, dazu älter, Geschwister, Outfit nach Monat, Rolle und Epoche und die Gefühle,
 * prüft Presets, Speichern und den Rückfall auf Dateien. Dann aus dem Schema DEMO: eine Tabelle mit einer Spalte
 * AV_PERSON_T anlegen, eine Person würfeln und anmelden, über die Metadaten-Sichten finden, lesen und mit Java
 * vergleichen; danach räumt sie auf. Ende: „Alles in Ordnung.“
 *
 * <pre>ant db-pruefung   (DEMO-Passwort aus -Davatar.demo.password, Vorgabe wie in den Szenen-Projekten)</pre>
 */
public final class DbPruefung {

    private DbPruefung() {}

    private static int ok, bad;
    private static final List<String> fails = new ArrayList<>();
    static final List<String> zeilen = new ArrayList<>();

    static void check(String what, boolean pass) {
        if (pass) ok++; else { bad++; fails.add(what); }
        String z = (pass ? "  ✓ " : "  ✗ ") + what;
        zeilen.add(z);
        System.out.println(z);
    }

    public static void main(String[] args) throws Exception {
        File projekt = new File(args.length > 0 ? args[0] : ".");
        System.out.println("Avatar · Prüfung der Datenbank (Phase 14)");
        lauf(projekt, true);
        System.out.println();
        if (bad == 0) System.out.println(ok + " Prüfungen bestanden. Alles in Ordnung.");
        else {
            System.out.println(bad + " von " + (ok + bad) + " Prüfungen fehlgeschlagen:");
            for (String f : fails) System.out.println("  ✗ " + f);
            System.exit(1);
        }
    }

    /** Die Prüfung; {@code einrichten}: vorher einrichten (sonst nur prüfen). Ergebnis: Zahlen für das Statusbild. */
    static Map<String, Object> lauf(File projekt, boolean einrichten) throws Exception {
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        DbAvatar d = new DbAvatar(projekt);
        try (Connection c = d.oeffnen()) {
            String wo = Db.einWert(c, "SELECT USER || ' auf ' || SYS_CONTEXT('USERENV', 'CON_NAME') FROM DUAL");
            check("Verbindung: " + wo, wo != null && wo.startsWith("AVATAR"));
            out.put("wo", wo);
            if (einrichten) {
                long t0 = System.nanoTime();
                Einrichter.Ergebnis r1 = d.einrichten(c, null);
                for (String z : r1.zeilen()) System.out.println("    " + z);
                check(String.format(Locale.GERMANY, "Einrichten: %s (%d Anweisungen, %.1f s)", r1.kurz(), r1.ausgefuehrt(), (System.nanoTime() - t0) / 1e9), r1.ok());
                Einrichter.Ergebnis r2 = d.einrichten(c, null);
                check(String.format(Locale.GERMANY, "Noch einmal einrichten: %s (%d schon vorhanden, nichts gelöscht)", r2.kurz(), r2.vorhanden()), r2.ok() && r2.vorhanden() > 0);
                out.put("einrichten", r2.zeilen());
            } else {
                Einrichter.Ergebnis r = d.pruefen(c);
                check("Prüfen: " + r.kurz(), r.ok());
                out.put("einrichten", r.zeilen());
            }
            out.put("rechte", DbAvatar.rechte(c));

            // ---- Würfel: Java gegen PL/SQL
            List<Long> seeds = new ArrayList<>();
            for (long s = 0; s < 1000; s++) seeds.add(s);
            for (long s : new long[]{-1, -4711, 4711, 1362, 2026, Long.MAX_VALUE, Long.MIN_VALUE, 123456789012345L, -987654321098765L}) seeds.add(s);
            int same = 0, sameName = 0;
            String first = null;
            long t0 = System.nanoTime();
            try (PreparedStatement st = c.prepareStatement("SELECT t.p.SEED, t.p.NAME, t.p.REGLER, t.p.GESCHLECHT, t.p.ALTER_J FROM (SELECT AV_API.WUERFELN(?) p FROM DUAL) t")) {
                for (long s : seeds) {
                    st.setLong(1, s);
                    try (ResultSet r = st.executeQuery()) {
                        r.next();
                        Person db = DbAvatar.person(r.getLong(1), r.getString(2), r.getString(3));
                        Person j = Person.wuerfeln(s);
                        boolean eq = db.sameAs(j) && r.getDouble(4) == j.gender && Math.abs(r.getDouble(5) - Math.round(j.ageYears() * 10) / 10.0) < 1e-9;
                        if (eq) same++;
                        else if (first == null) first = "seed " + s + ": " + diff(j, db);
                        if (j.name.equals(db.name)) sameName++;
                    }
                }
            }
            double ms = (System.nanoTime() - t0) / 1e6 / seeds.size();
            check(String.format(Locale.GERMANY, "Würfel: %d von %d seeds in PL/SQL und Java dieselbe Person (alle Regler, Frisur, Gesicht), %.1f ms je Person%s",
                    same, seeds.size(), ms, first == null ? "" : " · " + first), same == seeds.size());
            check(String.format(Locale.GERMANY, "Würfel: Namen gleich (%d von %d)", sameName, seeds.size()), sameName == seeds.size());
            out.put("wuerfel", same + " von " + seeds.size());
            out.put("wuerfelMs", ms);
            String z5 = Db.einWert(c, "SELECT AV_API.ZUFALL(-4711, 8) FROM DUAL");
            Person.Rng rng = new Person.Rng(-4711);
            boolean zok = true;
            for (String x : z5.split(" ")) zok &= Long.parseLong(x) == (rng.nextLong() >>> 11);
            check("Zufallszahlen (SplitMix64) Bit für Bit gleich: " + z5.substring(0, Math.min(40, z5.length())) + " …", zok);

            // ---- Ableiten
            int aOk = 0, gOk = 0;
            String aFirst = null;
            try (PreparedStatement st = c.prepareStatement("SELECT t.p.REGLER, t.p.NAME FROM (SELECT AV_API.AELTER(AV_API.WUERFELN(?), ?) p FROM DUAL) t");
                 PreparedStatement sg = c.prepareStatement("SELECT t.p.REGLER, t.p.NAME FROM (SELECT AV_API.GESCHWISTER(AV_API.WUERFELN(?), ?) p FROM DUAL) t")) {
                double[] jahre = {20, -10, 80, -80, 7.5};
                for (int i = 0; i < 100; i++) {
                    long s = i * 37L + 5;
                    double y = jahre[i % jahre.length];
                    st.setLong(1, s); st.setDouble(2, y);
                    try (ResultSet r = st.executeQuery()) {
                        r.next();
                        Person db = Person.fromJson(r.getString(1), s, r.getString(2)), j = Person.wuerfeln(s).aelter(y);
                        if (db.sameAs(j)) aOk++; else if (aFirst == null) aFirst = "seed " + s + " " + y + " J.: " + diff(j, db);
                    }
                    sg.setLong(1, s); sg.setLong(2, s + 1000);
                    try (ResultSet r = sg.executeQuery()) {
                        r.next();
                        Person db = Person.fromJson(r.getString(1), s + 1000, r.getString(2)), j = Person.wuerfeln(s).geschwister(s + 1000);
                        if (db.sameAs(j) && db.name.equals(j.name)) gOk++; else if (aFirst == null) aFirst = "Geschwister seed " + s + ": " + diff(j, db);
                    }
                }
            }
            check(String.format(Locale.GERMANY, "Ableiten: älter %d von 100, Geschwister %d von 100 wie in Java%s", aOk, gOk, aFirst == null ? "" : " · " + aFirst), aOk == 100 && gOk == 100);
            Person p20 = Person.wuerfeln(4711);
            Person a20 = p20.aelter(20);
            check(String.format(Locale.GERMANY, "Älter: %.1f → %.1f Jahre, grau %.3f → %.3f", p20.ageYears(), a20.ageYears(), p20.grey, a20.grey),
                    Math.abs(a20.ageYears() - p20.ageYears() - 20) < 0.2 && a20.grey >= p20.grey);

            // ---- Outfit
            int oOk = 0, oAll = 0;
            String oFirst = null;
            String[] arten = {"ALLTAG", "DRAUSSEN", "ARBEIT"};
            String[] epochen = {"HEUTE", "MITTELALTER_1362", "WIKINGERZEIT"};
            try (PreparedStatement st = c.prepareStatement("SELECT AV_API.OUTFIT(?, ?, ?, ?) FROM DUAL")) {
                for (int m = 1; m <= 12; m++) for (int a = 0; a < 3; a++) for (int e = 0; e < 3; e++) for (long s : new long[]{0, 1, 4711, -5}) {
                    st.setInt(1, m); st.setString(2, arten[a]); st.setString(3, epochen[e]); st.setLong(4, s);
                    String js;
                    try (ResultSet r = st.executeQuery()) { r.next(); js = r.getString(1); }
                    Outfit j = Outfit.forMonth(m, Outfit.Role.values()[a], Outfit.Epoch.values()[e], s);
                    oAll++;
                    String w = outfitDiff(j, js);
                    if (w == null) oOk++; else if (oFirst == null) oFirst = m + "/" + arten[a] + "/" + epochen[e] + "/" + s + ": " + w;
                }
            }
            check(String.format(Locale.GERMANY, "Outfit nach Monat, Kleiderart, Epoche und seed: %d von %d wie Outfit.forMonth%s", oOk, oAll, oFirst == null ? "" : " · " + oFirst), oOk == oAll);
            String wr = Db.einWert(c, "SELECT JSON_VALUE(AV_API.OUTFIT(11, 'Wärter', NULL, 4711), '$.name') FROM DUAL");
            String hd = Db.einWert(c, "SELECT JSON_VALUE(AV_API.OUTFIT(7, 'Händler', NULL, 4711), '$.name') FROM DUAL");
            check("Rollen: Wärter im November „" + wr + "“, Händler im Juli „" + hd + "“", Outfit.forMonth(11, Outfit.Role.DRAUSSEN, Outfit.Epoch.HEUTE, 4711).name.equals(wr) && "Wikingerzeit".equals(hd));
            out.put("outfit", oOk + " von " + oAll);

            // ---- Gefühle
            int eOk = 0;
            for (Emotion.Def def : Emotion.NAMED) {
                String js = Db.einWert(c, "SELECT AV_API.GEFUEHL(?, 0.7) FROM DUAL", def.name);
                Object o = com.dan.gltf.Json.parse(js);
                Map<String, Object> f = com.dan.gltf.Json.obj(o, "formen");
                boolean g = f.size() == def.forms.size() && com.dan.gltf.Json.num(o, "valenz", 9) == def.valence;
                for (Map.Entry<String, Double> e : def.forms.entrySet()) g &= Math.abs(((Number) f.get(e.getKey())).doubleValue() - e.getValue() * 0.7) < 1e-9;
                if (g) eOk++;
            }
            check(String.format(Locale.GERMANY, "Gefühle: %d von %d als Mischung der Formen wie com.dan.avatar.Emotion", eOk, Emotion.NAMED.size()), eOk == Emotion.NAMED.size());

            // ---- Prüfen und Fehler
            String ok1 = Db.einWert(c, "SELECT NVL(AV_API.PRUEFEN(AV_API.WUERFELN(7)), 'stimmt') FROM DUAL");
            Person kaputt = Person.wuerfeln(7);
            kaputt.melanin = 1.5;
            String err = Db.einWert(c, "SELECT AV_API.PRUEFEN(AV_PERSON_T(7, 1, ?, ?, NULL, 'kaputt', ?)) FROM DUAL",
                    new java.math.BigDecimal(DbStartdaten.n(kaputt.gender)), new java.math.BigDecimal(DbStartdaten.n(Math.round(kaputt.ageYears() * 10) / 10.0)), kaputt.toJson());
            check("Prüfen: gewürfelte Person stimmt, Melanin 1,5 wird gemeldet („" + err + "“)", "stimmt".equals(ok1) && err != null && err.startsWith("melanin"));
            int code = 0;
            try { Db.einWert(c, "SELECT t.p.NAME FROM (SELECT AV_API.SETZEN(AV_API.WUERFELN(7), 'iris', 2) p FROM DUAL) t"); }
            catch (SQLException e) { code = e.getErrorCode(); }
            int code2 = 0;
            try { Db.einWert(c, "SELECT AV_API.GEFUEHL('wütend') FROM DUAL"); } catch (SQLException e) { code2 = e.getErrorCode(); }
            check("Eigene Fehlernummern: Wert außerhalb ORA-20802, Gefühl unbekannt ORA-20803", code == 20802 && code2 == 20803);

            // ---- Presets
            List<DbAvatar.Preset> ps = DbAvatar.presets(c);
            DbAvatar.Preset rn = ps.stream().filter(p -> p.name().equals("Regennacht")).findFirst().orElse(null);
            check("Presets: " + ps.size() + " in AV_PRESET, „Regennacht“ ist die Frau im Regen aus Java (Outfit " + (rn == null ? "–" : rn.outfit()) + ")",
                    ps.size() >= 4 && rn != null && rn.person().sameAs(AvatarStudio.templateWoman()) && "Strickkapuze".equals(rn.outfit()));
            out.put("presets", ps);
            Person mine = Person.wuerfeln(99).aelter(5);
            mine.name = "Prüfperson";
            DbAvatar.speichern(c, "~Prüfung", mine, "Winter");
            DbAvatar.Preset back = DbAvatar.presets(c).stream().filter(p -> p.name().equals("~Prüfung")).findFirst().orElse(null);
            boolean refused = false;
            try { DbAvatar.speichern(c, "Regennacht", mine, null); } catch (SQLException e) { refused = true; }
            check("Eigenes Preset speichern und lesen (EIGEN), mitgeliefertes bleibt unberührt", back != null && back.person().sameAs(mine) && "EIGEN".equals(back.herkunft()) && refused
                    && DbAvatar.presets(c).stream().filter(p -> p.name().equals("Regennacht")).findFirst().get().person().sameAs(AvatarStudio.templateWoman()));
            // Rückfall auf Dateien und Übernahme
            File tmp = new File(System.getProperty("java.io.tmpdir"), "avatar_pruefung_" + System.nanoTime());
            DbAvatar dt = new DbAvatar(tmp);
            Person fp = Person.wuerfeln(77);
            dt.speichernDatei("~Prüfung Datei", fp, "Sommer");
            List<DbAvatar.Preset> fl = dt.presetsDatei();
            int n = dt.uebernehmen(c);
            DbAvatar.Preset fb = DbAvatar.presets(c).stream().filter(p -> p.name().equals("~Prüfung Datei")).findFirst().orElse(null);
            check("Ohne Datenbank: Preset als Datei, beim Verbinden übernommen (" + n + ")", fl.size() == 1 && fl.get(0).person().sameAs(fp) && n == 1 && fb != null && fb.person().sameAs(fp)
                    && dt.presetsDatei().isEmpty());
            try (Statement st = c.createStatement()) { st.executeUpdate("DELETE FROM AV_PRESET WHERE NAME LIKE '~Prüfung%'"); }

            // ---- aus dem Schema DEMO (F29 A, F33 A)
            demo(c, out);
        }
        return out;
    }

    /** Aus DEMO: Tabelle mit AV_PERSON_T, würfeln, anmelden, finden, lesen, vergleichen, aufräumen. */
    static void demo(Connection av, Map<String, Object> out) throws SQLException {
        String pw = System.getProperty("avatar.demo.password", System.getenv().getOrDefault("DEMO_DB_PASSWORD", "de"));
        String url = Db.einWert(av, "SELECT 'jdbc:oracle:thin:@//localhost:1521/' || SYS_CONTEXT('USERENV', 'SERVICE_NAME') FROM DUAL");
        try (Connection c = DriverManager.getConnection(url, "DEMO", pw)) {
            String syn = Db.einWert(c, "SELECT TO_CHAR(COUNT(*)) FROM ALL_SYNONYMS WHERE OWNER = 'PUBLIC' AND SYNONYM_NAME = 'AV_API' AND TABLE_OWNER = 'AVATAR'");
            String q = "0".equals(syn) ? "AVATAR." : "";
            check("DEMO verbunden, AVATAR " + (q.isEmpty() ? "über öffentliche Synonyme" : "mit Schemanamen (noch keine Synonyme)"), true);
            try (Statement st = c.createStatement()) {
                try { st.execute("DROP TABLE AV_PROBE_PERSON PURGE"); } catch (SQLException e) { /* nicht da */ }
                st.execute("CREATE TABLE AV_PROBE_PERSON (ID NUMBER(10) PRIMARY KEY, ROLLE VARCHAR2(30 CHAR), PERSON " + q + "AV_PERSON_T)");
                st.execute("INSERT INTO AV_PROBE_PERSON VALUES (1, 'Wattführer', " + q + "AV_API.WUERFELN(4711))");
                st.execute("INSERT INTO AV_PROBE_PERSON VALUES (2, 'Wärter', " + q + "AV_API.AELTER(" + q + "AV_API.WUERFELN(4711), 30))");
                try { st.execute("DELETE FROM " + q + "USER_AV_METADATA WHERE TABLE_NAME = 'AV_PROBE_PERSON'"); } catch (SQLException e) { /* leer */ }
                st.execute("INSERT INTO " + q + "USER_AV_METADATA (TABLE_NAME, COLUMN_NAME, ROLLE, BEMERKUNG) VALUES ('AV_PROBE_PERSON', 'PERSON', 'Wattführer', 'DbPruefung')");
            }
            String meta = Db.einWert(c, "SELECT TABLE_NAME || '.' || COLUMN_NAME || ' (' || ROLLE || ')' FROM " + q + "USER_AV_METADATA WHERE TABLE_NAME = 'AV_PROBE_PERSON'");
            String all = Db.einWert(av, "SELECT OWNER || '.' || TABLE_NAME || '.' || COLUMN_NAME FROM ALL_AV_METADATA WHERE OWNER = 'DEMO' AND TABLE_NAME = 'AV_PROBE_PERSON'");
            check("Angemeldet: DEMO sieht „" + meta + "“ in USER_AV_METADATA, AVATAR sieht „" + all + "“ in ALL_AV_METADATA", meta != null && "DEMO.AV_PROBE_PERSON.PERSON".equals(all));
            Person db, older;
            String pruef;
            try (Statement st = c.createStatement();
                 ResultSet r = st.executeQuery("SELECT t.PERSON.SEED, t.PERSON.NAME, t.PERSON.REGLER, NVL(t.PERSON.PRUEFEN(), 'stimmt'), t.PERSON.KURZ() FROM AV_PROBE_PERSON t ORDER BY t.ID")) {
                r.next();
                db = DbAvatar.person(r.getLong(1), r.getString(2), r.getString(3));
                pruef = r.getString(4);
                out.put("demoKurz", r.getString(5));
                r.next();
                older = DbAvatar.person(r.getLong(1), r.getString(2), r.getString(3));
            }
            out.put("demoPerson", db);
            out.put("demoAelter", older);
            Person j = Person.wuerfeln(4711);
            check("Aus DEMO gelesen: seed 4711 ist in PL/SQL und Java dieselbe Person (" + j.name + ", Methode PRUEFEN: " + pruef + ")",
                    db.sameAs(j) && db.name.equals(j.name) && "stimmt".equals(pruef));
            check("Aus DEMO: AV_API.AELTER(…, 30) ist Person.aelter(30)", older.sameAs(j.aelter(30)));
            String fit = Db.einWert(c, "SELECT JSON_VALUE(" + q + "AV_API.OUTFIT(11, 'Wattführer', NULL, 4711), '$.name') FROM DUAL");
            check("Aus DEMO: Outfit des Wattführers im November „" + fit + "“", Outfit.forMonth(11, Outfit.Role.DRAUSSEN, Outfit.Epoch.HEUTE, 4711).name.equals(fit));
            out.put("demo", j.name + " · " + meta);
            out.put("demoOutfit", fit);
            try (Statement st = c.createStatement()) {
                st.execute("DELETE FROM " + q + "USER_AV_METADATA WHERE TABLE_NAME = 'AV_PROBE_PERSON'");
                st.execute("DROP TABLE AV_PROBE_PERSON PURGE");
            }
            String gone = Db.einWert(av, "SELECT TO_CHAR(COUNT(*)) FROM ALL_AV_METADATA WHERE OWNER = 'DEMO' AND TABLE_NAME = 'AV_PROBE_PERSON'");
            check("Aufgeräumt: Probe-Tabelle in DEMO entfernt, abgemeldet", "0".equals(gone));
        }
    }

    static String diff(Person a, Person b) {
        for (String n : Person.NUMBERS) if (a.number(n) != b.number(n)) return n + " Java " + a.number(n) + " / PL/SQL " + b.number(n);
        if (!a.hair.equals(b.hair)) return "Frisur " + a.hair + " / " + b.hair;
        for (String f : Person.FACE) if (!java.util.Objects.equals(a.face.get(f), b.face.get(f))) return "face." + f + " " + a.face.get(f) + " / " + b.face.get(f);
        if (!a.name.equals(b.name)) return "Name " + a.name + " / " + b.name;
        return "gleich";
    }

    static String outfitDiff(Outfit j, String js) {
        Object o = com.dan.gltf.Json.parse(js);
        if (!j.name.equals(com.dan.gltf.Json.str(o, "name"))) return "Name " + j.name + " / " + com.dan.gltf.Json.str(o, "name");
        List<Object> t = com.dan.gltf.Json.arr(o, "teile");
        if (t.size() != j.pieces.size()) return "Stücke " + j.pieces.size() + " / " + t.size();
        for (int i = 0; i < t.size(); i++) {
            Outfit.Piece p = j.pieces.get(i);
            Object x = t.get(i);
            if (!p.mesh.equals(com.dan.gltf.Json.str(x, "netz"))) return "Netz " + p.mesh + " / " + com.dan.gltf.Json.str(x, "netz");
            String sf = ((Map<?, ?>) x).get("stoff") == null ? null : com.dan.gltf.Json.str(x, "stoff");
            if (!java.util.Objects.equals(p.fabric == null ? null : p.fabric.name(), sf)) return "Stoff " + p.fabric + " / " + sf;
            List<Object> f = ((Map<?, ?>) x).get("farbe") == null ? null : com.dan.gltf.Json.arr(x, "farbe");
            if ((p.color == null) != (f == null)) return "Farbe fehlt";
            if (f != null) for (int k = 0; k < 3; k++) if (Math.abs(((Number) f.get(k)).doubleValue() - p.color[k]) > 1e-9) return "Farbe " + p.mesh;
        }
        return null;
    }
}
