# Datenbank AVATAR (Phase 14)

Das Schema **AVATAR** in `PDBORCL` hält die Personen als eigenen Typ, die Kataloge (Regler, Frisuren,
Formen, Viseme, Gefühle, Posen, Gesten, Epochen, Rollen, Stoffe, Kleidung, Outfits) und die Presets.
Andere Projekte (DEMO, RUNGHOLT, GLACIER, HAITHABU, LIGHTHOUSE, KILAUEA, LOGISTIK) legen in ihren
eigenen Tabellen Spalten vom Typ `AV_PERSON_T` an und würfeln, altern oder prüfen Personen mit
`AV_API` – mit demselben Würfel wie Java: gleicher seed, gleiche Person, Bit für Bit.

## Dateien

| Datei | Inhalt |
|---|---|
| `sql/01_typen.sql` | `AV_PERSON_T` (SEED, VERSION, GESCHLECHT, ALTER_J, PRESET, NAME, REGLER als JSON; Methoden WERT, TEXT, PRUEFEN, KURZ, WUERFELN), `AV_ZUSTAND_T` |
| `sql/02_tabellen.sql` | 19 Tabellen `AV_…` mit Sequenzen und Triggern; HERKUNFT `MITGELIEFERT` oder `EIGEN` |
| `sql/03_api.sql` | Package `AV_API` (Würfel, NEU, AELTER, GESCHWISTER, SETZEN, PRUEFEN, PRESET, OUTFIT, GEFUEHL, ANMELDEN/ABMELDEN, LOG), `AV_KATALOG`, Typ-Rümpfe |
| `sql/04_sichten.sql` | Sichten `AV_…_V`, `USER_AV_METADATA` und `ALL_AV_METADATA` (wie `USER_SDO_GEOM_METADATA`) |
| `sql/05_rechte.sql` | Rechte: Typen an PUBLIC, Package und Sichten an die Rolle `AVATAR_USER` (oder, solange sie fehlt, direkt an die Schemata), öffentliche Synonyme, wenn erlaubt |
| `sql/06_startdaten.sql` | Kataloge und Presets – **erzeugt** aus dem Java-Code (`ant db-startdaten`), nicht von Hand ändern |
| `sql/99_entfernen.sql` | Alles wieder entfernen (nur Objekte `AV_…`) |
| `dba/00_einmalig_als_dba.sql` | **Einmal als DBA:** Rolle `AVATAR_USER`, Recht auf öffentliche Synonyme |
| `db.properties.example` | Vorlage für den Zugang; `db.properties` kommt nicht ins Git |

Alle Objekte heißen `AV_…` (außer den beiden Metadaten-Sichten).

## Einrichten

1. **Einmal als DBA** (SYSTEM oder PDBADMIN, an PDBORCL angemeldet) `dba/00_einmalig_als_dba.sql` ausführen.
   Ohne diesen Schritt geht alles auch – die Rechte gehen dann direkt an die sieben Schemata, und die anderen
   Projekte schreiben `AVATAR.AV_API` statt `AV_API`.
2. Im Avatar Studio im Block **DATEN** auf **Einrichten** – oder `ant db-pruefung`. Der Einrichter
   (`com.dan.store.Einrichter`) führt 01–06 aus, legt nur an, was fehlt, löscht nichts, kompiliert, was
   ungültig ist, vergleicht Spalten, Trigger, Sichten, Prüfsummen und rechnet zur Probe
   (`WUERFELN(4711)`, `PRUEFEN(PRESET('Regennacht'))`, `OUTFIT(11, 'DRAUSSEN')`). Er ist beliebig oft
   wiederholbar; am Ende steht „alles in Ordnung“ oder die erste Fehlerzeile.
3. Neue Projekte: `GRANT AVATAR_USER TO <SCHEMA>;` (als DBA).

Ohne Datenbank speichert das Studio Presets als Dateien unter `daten/personen/*.person`; beim nächsten
Verbinden werden sie in `AV_PRESET` übernommen (`*.person.uebernommen`).

## Aus einem anderen Projekt (Beispiel DEMO)

```sql
CREATE TABLE WAECHTER (
  ID      NUMBER(10) PRIMARY KEY,
  ROLLE   VARCHAR2(30 CHAR),
  PERSON  AV_PERSON_T              -- ohne Synonyme: AVATAR.AV_PERSON_T
);

INSERT INTO WAECHTER VALUES (1, 'Wattführer', AV_API.WUERFELN(4711));
INSERT INTO WAECHTER VALUES (2, 'Wärter',     AV_API.AELTER(AV_API.WUERFELN(4711), 30));
INSERT INTO WAECHTER VALUES (3, 'Tochter',    AV_API.GESCHWISTER(AV_API.WUERFELN(4711), 99));

-- anmelden, damit Werkzeuge die Spalte finden
INSERT INTO USER_AV_METADATA (TABLE_NAME, COLUMN_NAME, ROLLE, BEMERKUNG)
VALUES ('WAECHTER', 'PERSON', 'Wattführer', 'Leuchtturm');

SELECT w.ROLLE, w.PERSON.KURZ(), w.PERSON.WERT('age'), w.PERSON.PRUEFEN()
  FROM WAECHTER w;

-- was trägt er im November?
SELECT JSON_VALUE(AV_API.OUTFIT(11, 'Wattführer', NULL, 4711), '$.name') FROM DUAL;   -- Strickkapuze
```

Java liest dieselbe Spalte mit `Person.fromJson(regler, seed, name)` (`DbAvatar.person`).
Fehlernummern von `AV_API`: ORA-20801 bis ORA-20809 (z. B. 20802 Wert außerhalb, 20803 Gefühl unbekannt).

## Prüfen

- `ant check` – ohne Datenbank: JSON hin und zurück, Älter, Geschwister, Skripte zerlegbar, `06_startdaten.sql` aktuell, Kataloge passend.
- `ant db-pruefung` – mit Datenbank: einrichten (zweimal), 1009 seeds Java gegen PL/SQL, 432 Outfits, Gefühle, Presets, Rückfall auf Dateien, Probe aus DEMO.
- `ant db-startdaten` – `06_startdaten.sql` neu aus dem Java-Code schreiben (nach Änderungen an Reglern, Outfits, Presets).
