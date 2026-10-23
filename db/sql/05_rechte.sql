-- =====================================================================================================
-- Avatar · Datenbank · 05_rechte.sql
-- Zugriff für die Projekte (F34 A): Rolle AVATAR_USER mit EXECUTE auf die Packages, SELECT auf die Sichten,
-- Schreiben in USER_AV_METADATA, dazu öffentliche Synonyme wie bei MDSYS.
--
-- Die Rolle und das Recht CREATE PUBLIC SYNONYM hat AVATAR nicht (Rolle DB_CONNECT, siehe db/Role_Granntee.sql).
-- Beides richtet einmal ein DBA mit db/dba/00_einmalig_als_dba.sql ein. Bis dahin gibt dieses Skript die Rechte direkt
-- an die Szenen-Schemata, die es gibt (DEMO, RUNGHOLT, GLACIER, HAITHABU, LIGHTHOUSE, KILAUEA, LOGISTIK), und lässt die
-- Synonyme weg; die Projekte schreiben dann AVATAR.AV_API. Was es getan hat, steht in AV_LOG (QUELLE 05_rechte).
--
-- Die Typen bekommen EXECUTE für PUBLIC wie MDSYS.SDO_GEOMETRY: Wer eine Spalte vom Typ AV_PERSON_T anlegt, braucht
-- das Recht direkt, über eine Rolle reicht es für CREATE TABLE nicht.
-- Wiederholbar. Dieses Skript löscht nichts.
-- =====================================================================================================

DECLARE
  TYPE T_L IS TABLE OF VARCHAR2(200);
  RAUS   T_L := T_L('EXECUTE ON AV_API', 'EXECUTE ON AV_KATALOG',
                    'SELECT ON AV_REGLER_V', 'SELECT ON AV_GEFUEHL_V', 'SELECT ON AV_OUTFIT_V', 'SELECT ON AV_ROLLE_V',
                    'SELECT ON AV_PRESET_V', 'SELECT ON ALL_AV_METADATA',
                    'SELECT, INSERT, UPDATE, DELETE ON USER_AV_METADATA');
  SYN    T_L := T_L('AV_PERSON_T', 'AV_ZUSTAND_T', 'AV_API', 'AV_KATALOG', 'AV_REGLER_V', 'AV_GEFUEHL_V', 'AV_OUTFIT_V',
                    'AV_ROLLE_V', 'AV_PRESET_V', 'USER_AV_METADATA', 'ALL_AV_METADATA');
  ZIELE  T_L := T_L('DEMO', 'RUNGHOLT', 'GLACIER', 'HAITHABU', 'LIGHTHOUSE', 'KILAUEA', 'LOGISTIK');
  ROLLE  BOOLEAN := TRUE;
  NSYN   PLS_INTEGER := 0;
  NDIR   PLS_INTEGER := 0;
  N      PLS_INTEGER;
  PROCEDURE LOGGE (T VARCHAR2, S VARCHAR2 DEFAULT 'INFO') IS
    PRAGMA AUTONOMOUS_TRANSACTION;
  BEGIN
    INSERT INTO AV_LOG (STUFE, QUELLE, TEXT) VALUES (S, '05_rechte', SUBSTR(T, 1, 2000));
    COMMIT;
  END;
BEGIN
  -- Typen für alle (wie MDSYS)
  EXECUTE IMMEDIATE 'GRANT EXECUTE ON AV_PERSON_T TO PUBLIC';
  EXECUTE IMMEDIATE 'GRANT EXECUTE ON AV_ZUSTAND_T TO PUBLIC';
  -- Rolle, wenn es sie gibt
  FOR I IN 1 .. RAUS.COUNT LOOP
    BEGIN
      EXECUTE IMMEDIATE 'GRANT ' || RAUS(I) || ' TO AVATAR_USER';
    EXCEPTION WHEN OTHERS THEN
      IF SQLCODE IN (-1917, -1919, -1924) THEN ROLLE := FALSE; EXIT; ELSE RAISE; END IF;
    END;
  END LOOP;
  IF ROLLE THEN
    LOGGE('Rechte an die Rolle AVATAR_USER gegeben');
  ELSE
    -- ohne Rolle: direkt an die Szenen-Schemata, die es gibt
    FOR J IN 1 .. ZIELE.COUNT LOOP
      SELECT COUNT(*) INTO N FROM ALL_USERS WHERE USERNAME = ZIELE(J);
      IF N > 0 THEN
        FOR I IN 1 .. RAUS.COUNT LOOP
          EXECUTE IMMEDIATE 'GRANT ' || RAUS(I) || ' TO ' || ZIELE(J);
        END LOOP;
        NDIR := NDIR + 1;
      END IF;
    END LOOP;
    LOGGE('Rolle AVATAR_USER fehlt (db/dba/00_einmalig_als_dba.sql): Rechte direkt an ' || NDIR || ' Schemata gegeben', 'WARNUNG');
  END IF;
  -- Öffentliche Synonyme, wenn AVATAR sie anlegen darf
  FOR I IN 1 .. SYN.COUNT LOOP
    BEGIN
      EXECUTE IMMEDIATE 'CREATE OR REPLACE PUBLIC SYNONYM ' || SYN(I) || ' FOR AVATAR.' || SYN(I);
      NSYN := NSYN + 1;
    EXCEPTION WHEN OTHERS THEN
      IF SQLCODE = -1031 THEN
        LOGGE('CREATE PUBLIC SYNONYM fehlt (db/dba/00_einmalig_als_dba.sql): keine öffentlichen Synonyme, Projekte schreiben AVATAR.' || SYN(I), 'WARNUNG');
        EXIT;
      ELSE RAISE;
      END IF;
    END;
  END LOOP;
  IF NSYN > 0 THEN LOGGE(NSYN || ' öffentliche Synonyme angelegt'); END IF;
END;
/

-- Ende 05_rechte.sql
