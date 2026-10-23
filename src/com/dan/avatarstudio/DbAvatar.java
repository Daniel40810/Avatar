package com.dan.avatarstudio;

import com.dan.avatar.Person;
import com.dan.store.Db;
import com.dan.store.Einrichter;
import com.dan.store.Skript;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Datenseite des Avatars (Phase 14, F38 A): Verbindung zum Schema AVATAR über {@code com.dan.store}, Einrichten mit den
 * Skripten in {@code db/sql}, Presets lesen und speichern, Würfeln in der Datenbank. Ohne Datenbank stehen die eigenen
 * Presets als Dateien in {@code daten/personen}; beim nächsten Verbinden übernimmt {@link #uebernehmen} sie.
 */
public final class DbAvatar {

    /** Ein Preset: Name, Person, Outfit, Herkunft (MITGELIEFERT, EIGEN oder DATEI). */
    public record Preset(String name, Person person, String outfit, String herkunft) {
        @Override public String toString() { return name + ("MITGELIEFERT".equals(herkunft) ? "" : " · " + herkunft.toLowerCase(Locale.ROOT)); }
    }

    public static final String PRAEFIX = "AV_";
    /** Skripte nach 01_typen.sql (das der Einrichter vorab ausführt, weil die Tabellen die Typen brauchen). */
    public static final String[] SKRIPTE = {"02_tabellen.sql", "03_api.sql", "04_sichten.sql", "05_rechte.sql", "06_startdaten.sql"};
    public static final String[] SICHTEN = {"AV_REGLER_V", "AV_GEFUEHL_V", "AV_OUTFIT_V", "AV_ROLLE_V", "AV_PRESET_V", "USER_AV_METADATA", "ALL_AV_METADATA"};
    /** PL/SQL-Einheiten, die gültig sein müssen (Typ, Name). */
    static final String[][] EINHEITEN = {{"TYPE", "AV_PERSON_T"}, {"TYPE BODY", "AV_PERSON_T"}, {"TYPE", "AV_ZUSTAND_T"}, {"TYPE BODY", "AV_ZUSTAND_T"},
            {"PACKAGE", "AV_API"}, {"PACKAGE BODY", "AV_API"}, {"PACKAGE", "AV_KATALOG"}, {"PACKAGE BODY", "AV_KATALOG"}};

    /** Ordner db (mit sql/ und db.properties). */
    public final File db;
    /** Ordner für die Presets ohne Datenbank. */
    public final File personen;

    public DbAvatar(File projekt) {
        this.db = new File(projekt, "db");
        this.personen = new File(projekt, "daten/personen");
    }

    /** Zugang aus db/db.properties (Vorlage db.properties.example), Passwort sonst aus AVATAR_DB_PASSWORD. */
    public Db.Konfig konfig() throws IOException {
        File f = new File(db, "db.properties");
        if (!f.exists()) {
            File ex = new File(db, "db.properties.example");
            if (ex.exists()) Files.copy(ex.toPath(), f.toPath());
        }
        return Db.konfig(f, "AVATAR", "AVATAR_DB_PASSWORD");
    }

    public Connection oeffnen() throws IOException, SQLException { return Db.oeffnen(konfig()); }

    // ------------------------------------------------------------------ Einrichten

    public Einrichter einrichter() {
        Einrichter e = new Einrichter();
        e.praefix = PRAEFIX;
        e.skripte = SKRIPTE;
        e.version = 1;
        e.sichten = SICHTEN;
        e.startdaten = DbStartdaten::soll;
        e.rechnet = () -> List.of(
                new Einrichter.Soll("Würfel AV_API.WUERFELN(4711)", "SELECT t.p.NAME FROM (SELECT AV_API.WUERFELN(4711) p FROM DUAL) t", -1),
                new Einrichter.Soll("Preset Regennacht geprüft", "SELECT NVL(AV_API.PRUEFEN(AV_API.PRESET('Regennacht')), 'stimmt') FROM DUAL", -1),
                new Einrichter.Soll("Outfit im November draußen", "SELECT JSON_VALUE(AV_API.OUTFIT(11, 'DRAUSSEN', NULL, 4711), '$.name') FROM DUAL", -1));
        return e;
    }

    /**
     * Einrichten: erst 01_typen.sql (die Typen, ORA-00955 harmlos), dann der Einrichter mit den übrigen Skripten und
     * seiner Prüfung, dann die PL/SQL-Einheiten (ungültige werden neu übersetzt), die Rechte und die Prüfsummen.
     */
    public Einrichter.Ergebnis einrichten(Connection c, Einrichter.Fortschritt f) throws IOException, SQLException {
        File sql = new File(db, "sql");
        List<String> vorab = new ArrayList<>();
        int run = 0;
        try (Statement st = c.createStatement()) {
            for (Skript.Anweisung a : Skript.zerlegen(Files.readString(new File(sql, "01_typen.sql").toPath(), StandardCharsets.UTF_8))) {
                try { st.execute(a.sql()); run++; }
                catch (SQLException e) {
                    if (!Einrichter.HARMLOS.contains(e.getErrorCode())) vorab.add("FEHLER 01_typen.sql Zeile " + a.zeile() + ": " + Db.erste(e));
                }
            }
        }
        Einrichter.Ergebnis r = einrichter().einrichten(c, sql, f);
        List<String> z = new ArrayList<>();
        z.add("ok 01_typen.sql: " + run + " Typen neu");
        z.addAll(vorab);
        z.addAll(r.zeilen());
        int fehler = vorab.size() + r.fehler();
        List<String> nach = nachpruefen(c, true);
        for (String s : nach) if (s.startsWith("FEHLT") || s.startsWith("FEHLER")) fehler++;
        z.addAll(nach);
        z.removeIf(s -> s.equals("alles in Ordnung"));
        if (fehler == 0) z.add("alles in Ordnung");
        return new Einrichter.Ergebnis(fehler == 0, z, r.ausgefuehrt() + run, r.vorhanden(), fehler);
    }

    /** Nur prüfen (schreibt nichts außer dem Übersetzen ungültiger Einheiten). */
    public Einrichter.Ergebnis pruefen(Connection c) throws IOException, SQLException {
        Einrichter.Ergebnis r = einrichter().pruefen(c, new File(db, "sql"));
        List<String> z = new ArrayList<>(r.zeilen());
        z.removeIf(s -> s.equals("alles in Ordnung"));
        int fehler = r.fehler();
        for (String s : nachpruefen(c, false)) { z.add(s); if (s.startsWith("FEHLT") || s.startsWith("FEHLER")) fehler++; }
        if (fehler == 0) z.add("alles in Ordnung");
        return new Einrichter.Ergebnis(fehler == 0, z, 0, 0, fehler);
    }

    /** PL/SQL-Einheiten gültig, Rechte (Rolle, Synonyme), Prüfsummen des Packages gegen AV_DATEI (F37). */
    List<String> nachpruefen(Connection c, boolean uebersetzen) throws SQLException {
        List<String> z = new ArrayList<>();
        if (uebersetzen) {
            try (Statement st = c.createStatement()) {
                for (String[] e : EINHEITEN) {
                    String s = Db.einWert(c, "SELECT STATUS FROM USER_OBJECTS WHERE OBJECT_TYPE = ? AND OBJECT_NAME = ?", e[0], e[1]);
                    if ("INVALID".equals(s)) {
                        String what = e[0].startsWith("PACKAGE") ? "PACKAGE " + e[1] + " COMPILE" + (e[0].endsWith("BODY") ? " BODY" : "")
                                : "TYPE " + e[1] + " COMPILE" + (e[0].endsWith("BODY") ? " BODY" : "");
                        try { st.execute("ALTER " + what); } catch (SQLException x) { /* Prüfung meldet es */ }
                    }
                }
            }
        }
        int bad = 0;
        for (String[] e : EINHEITEN) {
            String s = Db.einWert(c, "SELECT STATUS FROM USER_OBJECTS WHERE OBJECT_TYPE = ? AND OBJECT_NAME = ?", e[0], e[1]);
            if (!"VALID".equals(s)) {
                bad++;
                String fe = Db.einWert(c, "SELECT MIN('Zeile ' || LINE || ': ' || TEXT) KEEP (DENSE_RANK FIRST ORDER BY SEQUENCE) FROM USER_ERRORS WHERE NAME = ? AND TYPE = ? AND LINE > 0", e[1], e[0]);
                z.add("FEHLT " + e[0] + " " + e[1] + (s == null ? "" : " ist " + s) + (fe == null ? "" : " · " + fe.strip()));
            }
        }
        if (bad == 0) z.add("ok " + EINHEITEN.length + " PL/SQL-Einheiten gültig (Typen, AV_API, AV_KATALOG)");
        z.add(rechte(c));
        // Prüfsummen (F37)
        try {
            for (DbStartdaten.Katalog k : DbStartdaten.kataloge()) {
                if (!k.tabelle().equals("AV_DATEI")) continue;
                int same = 0;
                for (var row : k.zeilen()) {
                    String ist = Db.einWert(c, "SELECT SHA256 FROM AV_DATEI WHERE NAME = ?", row.get("NAME"));
                    if (row.get("SHA256").equals(ist)) same++;
                    else z.add("FEHLT Prüfsumme " + row.get("NAME") + ": Package und Schema passen nicht zusammen (neu einrichten)");
                }
                if (same == k.zeilen().size()) z.add("ok Package und Schema passen zusammen (" + same + " Prüfsummen)");
            }
        } catch (Exception e) {
            z.add("FEHLER Prüfsummen: " + e.getMessage());
        }
        return z;
    }

    /** „ok Rolle … / HINWEIS …“: was 05_rechte.sql erreicht hat. */
    public static String rechte(Connection c) throws SQLException {
        String role = Db.einWert(c, "SELECT TO_CHAR(COUNT(*)) FROM USER_TAB_PRIVS_MADE WHERE GRANTEE = 'AVATAR_USER'");
        String syn = Db.einWert(c, "SELECT TO_CHAR(COUNT(*)) FROM ALL_SYNONYMS WHERE OWNER = 'PUBLIC' AND TABLE_OWNER = 'AVATAR'");
        String dir = Db.einWert(c, "SELECT TO_CHAR(COUNT(DISTINCT GRANTEE)) FROM USER_TAB_PRIVS_MADE WHERE TABLE_NAME = 'AV_API' AND GRANTEE NOT IN ('AVATAR_USER', 'PUBLIC')");
        boolean r = !"0".equals(role), s = !"0".equals(syn);
        if (r && s) return "ok Rechte an die Rolle AVATAR_USER, " + syn + " öffentliche Synonyme";
        return "HINWEIS Rechte " + (r ? "an die Rolle AVATAR_USER" : "direkt an " + dir + " Schemata (Rolle AVATAR_USER fehlt)")
                + (s ? ", " + syn + " öffentliche Synonyme" : ", keine öffentlichen Synonyme") + " · einmal als DBA: db/dba/00_einmalig_als_dba.sql, dann neu einrichten";
    }

    /** Stufe des Schemas (0 leer, −1 ohne AV_SCHEMA, sonst VERSION). */
    public int stufe(Connection c) throws SQLException { return einrichter().stufe(c); }

    // ------------------------------------------------------------------ Personen

    /** Person aus den Feldern von AV_PERSON_T. */
    public static Person person(long seed, String name, String regler) { return Person.fromJson(regler, seed, name); }

    /** AV_API.WUERFELN(seed) als Person. */
    public static Person wuerfeln(Connection c, long seed) throws SQLException {
        try (PreparedStatement st = c.prepareStatement("SELECT t.p.SEED, t.p.NAME, t.p.REGLER FROM (SELECT AV_API.WUERFELN(?) p FROM DUAL) t")) {
            st.setLong(1, seed);
            try (ResultSet r = st.executeQuery()) {
                r.next();
                return person(r.getLong(1), r.getString(2), r.getString(3));
            }
        }
    }

    /** Alle Presets der Datenbank (mitgelieferte zuerst). */
    public static List<Preset> presets(Connection c) throws SQLException {
        List<Preset> l = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet r = st.executeQuery("SELECT p.NAME, p.PERSON.SEED, p.PERSON.NAME, p.PERSON.REGLER, p.OUTFIT, p.HERKUNFT FROM AV_PRESET p "
                     + "ORDER BY CASE p.HERKUNFT WHEN 'MITGELIEFERT' THEN 0 ELSE 1 END, p.ID")) {
            while (r.next()) l.add(new Preset(r.getString(1), person(r.getLong(2), r.getString(3), r.getString(4)), r.getString(5), r.getString(6)));
        }
        return l;
    }

    /**
     * Eigenes Preset speichern (HERKUNFT EIGEN). Ein mitgeliefertes wird nie überschrieben: dann Fehler. Die Person wird
     * mit AV_API.PRUEFEN geprüft; ist sie falsch, wird nichts gespeichert.
     */
    public static void speichern(Connection c, String name, Person p, String outfit) throws SQLException {
        String her = Db.einWert(c, "SELECT HERKUNFT FROM AV_PRESET WHERE NAME = ?", name);
        if ("MITGELIEFERT".equals(her)) throw new SQLException("„" + name + "“ ist mitgeliefert und bleibt, wie es ist; bitte einen eigenen Namen wählen");
        String typ = "AV_PERSON_T(?, 1, ?, ?, ?, ?, ?)";
        String fehler;
        try (PreparedStatement st = c.prepareStatement("SELECT AV_API.PRUEFEN(" + typ + ") FROM DUAL")) {
            binde(st, 1, name, p);
            try (ResultSet r = st.executeQuery()) { r.next(); fehler = r.getString(1); }
        }
        if (fehler != null) throw new SQLException("Person nicht gültig: " + fehler);
        String o = outfit != null && Db.einWert(c, "SELECT NAME FROM AV_OUTFIT WHERE NAME = ?", outfit) != null ? outfit : null;
        try (PreparedStatement st = c.prepareStatement("MERGE INTO AV_PRESET t USING (SELECT ? NAME FROM DUAL) s ON (t.NAME = s.NAME) "
                + "WHEN MATCHED THEN UPDATE SET t.PERSON = " + typ + ", t.OUTFIT = ? "
                + "WHEN NOT MATCHED THEN INSERT (NAME, PERSON, OUTFIT, BEMERKUNG, HERKUNFT) VALUES (s.NAME, " + typ + ", ?, 'im Avatar Studio gespeichert', 'EIGEN')")) {
            st.setString(1, name);
            binde(st, 2, name, p);
            st.setString(8, o);
            binde(st, 9, name, p);
            st.setString(15, o);
            st.executeUpdate();
        }
        if (!c.getAutoCommit()) c.commit();
    }

    private static void binde(PreparedStatement st, int i, String preset, Person p) throws SQLException {
        st.setLong(i, p.seed);
        st.setBigDecimal(i + 1, new java.math.BigDecimal(DbStartdaten.n(p.gender)));
        st.setBigDecimal(i + 2, new java.math.BigDecimal(DbStartdaten.n(Math.round(p.ageYears() * 10) / 10.0)));
        st.setString(i + 3, preset);
        st.setString(i + 4, p.name == null ? null : p.name.length() > 80 ? p.name.substring(0, 80) : p.name);
        st.setString(i + 5, p.toJson());
    }

    // ------------------------------------------------------------------ ohne Datenbank (F38 A)

    /** Preset als Datei speichern: daten/personen/<name>.person (Zeilen schluessel=wert). */
    public void speichernDatei(String name, Person p, String outfit) throws IOException {
        personen.mkdirs();
        String t = "# Avatar · Preset ohne Datenbank (wird beim nächsten Verbinden in AV_PRESET übernommen)\n"
                + "name=" + name + "\nseed=" + p.seed + "\nperson=" + p.name + "\noutfit=" + (outfit == null ? "" : outfit) + "\nregler=" + p.toJson() + "\n";
        File f = new File(personen, datei(name) + ".person");
        File tmp = new File(personen, datei(name) + ".person.tmp");
        Files.writeString(tmp.toPath(), t, StandardCharsets.UTF_8);
        Files.move(tmp.toPath(), f.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    /** Presets aus daten/personen. */
    public List<Preset> presetsDatei() throws IOException {
        List<Preset> l = new ArrayList<>();
        File[] fs = personen.listFiles((d, n) -> n.endsWith(".person"));
        if (fs == null) return l;
        java.util.Arrays.sort(fs);
        for (File f : fs) {
            java.util.Map<String, String> m = new java.util.HashMap<>();
            for (String z : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                int e = z.indexOf('=');
                if (!z.startsWith("#") && e > 0) m.put(z.substring(0, e), z.substring(e + 1));
            }
            if (m.get("regler") == null) continue;
            l.add(new Preset(m.getOrDefault("name", f.getName()), person(Long.parseLong(m.getOrDefault("seed", "0")), m.get("person"), m.get("regler")),
                    m.getOrDefault("outfit", "").isEmpty() ? null : m.get("outfit"), "DATEI"));
        }
        return l;
    }

    /** Presets aus daten/personen in die Datenbank übernehmen; die Datei heißt danach *.uebernommen. Anzahl. */
    public int uebernehmen(Connection c) throws IOException, SQLException {
        int n = 0;
        File[] fs = personen.listFiles((d, x) -> x.endsWith(".person"));
        if (fs == null) return 0;
        for (Preset p : presetsDatei()) {
            speichern(c, p.name(), p.person(), p.outfit());
            File f = new File(personen, datei(p.name()) + ".person");
            if (f.exists()) Files.move(f.toPath(), new File(personen, datei(p.name()) + ".person.uebernommen").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            n++;
        }
        return n;
    }

    static String datei(String name) { return name.replaceAll("[^A-Za-z0-9ÄÖÜäöüß _-]", "_").trim(); }
}
