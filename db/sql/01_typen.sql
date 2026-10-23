-- =====================================================================================================
-- Avatar · Datenbank · 01_typen.sql
-- Schema AVATAR auf PDBORCL, Präfix AV_. Werkbuch Abschnitt 8, F29–F40 „alle A“ (22.10.2026).
--
-- Zwei kleine, feste Objekttypen nach dem Vorbild von MDSYS.SDO_GEOMETRY (F31 A): Sie stehen später in Tabellen
-- anderer Schemata (RUNGHOLT, GLACIER, DEMO …) und lassen sich dann kaum noch ändern. Darum tragen sie nur, was
-- sicher bleibt, und alles Wachsende als JSON. Die Methoden stehen im Rumpf (TYPE BODY) in 03_api.sql, weil
-- sie AV_API brauchen.
--
-- Wiederholbar: Steht ein Typ schon, meldet Oracle ORA-00955 (harmlos). Dieses Skript löscht nichts.
-- Der Einrichter der App führt es vor den übrigen Skripten aus; in SQL Developer als Skript (F5).
-- =====================================================================================================


-- -----------------------------------------------------------------------------------------------------
-- AV_PERSON_T: wer jemand ist. SEED (gleicher Wert, gleiche Person, in Java und PL/SQL), VERSION des Typs (1),
-- GESCHLECHT 0 weiblich … 1 männlich, ALTER_J in Jahren, PRESET (Name, wenn aus einem Preset), NAME und alle
-- übrigen Regler als JSON in REGLER (Schlüssel wie die Felder von com.dan.avatar.Person, siehe AV_REGLER).
-- -----------------------------------------------------------------------------------------------------
CREATE TYPE AV_PERSON_T AS OBJECT (
  SEED       NUMBER(20),
  VERSION    NUMBER(4),
  GESCHLECHT NUMBER(6,3),
  ALTER_J    NUMBER(5,1),
  PRESET     VARCHAR2(40 CHAR),
  NAME       VARCHAR2(80 CHAR),
  REGLER     VARCHAR2(4000 CHAR),
  -- Wert eines Reglers: „melanin“, „face.nose.breite“, „forms.arkit.jawOpen“ (NULL, wenn nicht gesetzt)
  MEMBER FUNCTION WERT (P_REGLER VARCHAR2) RETURN NUMBER,
  -- Frisur und andere Texte: „hair“
  MEMBER FUNCTION TEXT (P_REGLER VARCHAR2) RETURN VARCHAR2,
  -- NULL, wenn alles stimmt; sonst die erste Abweichung (Bereich, unbekannter Regler, kein JSON)
  MEMBER FUNCTION PRUEFEN RETURN VARCHAR2,
  -- „Frau, 31 · 4711 (Strickkapuze …)“ für Listen
  MEMBER FUNCTION KURZ RETURN VARCHAR2,
  -- Dieselbe Person wie Person.wuerfeln(seed) in Java
  STATIC FUNCTION WUERFELN (P_SEED NUMBER) RETURN AV_PERSON_T
);
/

-- -----------------------------------------------------------------------------------------------------
-- AV_ZUSTAND_T: ein Augenblick einer Person für gemerkte Szenen (Gefühl, Blick, Pose, Kapuze, Outfit),
-- alles Weitere (Nässe je Zone, Ort, Richtung) als JSON in MEHR. Entspricht com.dan.avatar.Snapshot.
-- -----------------------------------------------------------------------------------------------------
CREATE TYPE AV_ZUSTAND_T AS OBJECT (
  VERSION  NUMBER(4),
  GEFUEHL  VARCHAR2(30 CHAR),
  STAERKE  NUMBER(4,2),
  BLICK    VARCHAR2(40 CHAR),
  POSE     VARCHAR2(30 CHAR),
  KAPUZE   NUMBER(1),
  OUTFIT   VARCHAR2(40 CHAR),
  MEHR     VARCHAR2(4000 CHAR),
  MEMBER FUNCTION KURZ RETURN VARCHAR2
);
/

-- Ende 01_typen.sql
