-- =====================================================================================================
-- Avatar · Datenbank · 00_einmalig_als_dba.sql
-- EINMAL als DBA (SYSTEM oder PDBADMIN) in PDBORCL ausführen, z. B. in SQL Developer als Skript (F5):
--
--   1. Rolle AVATAR_USER anlegen (F34 A) und an die Szenen-Schemata geben, die es gibt
--   2. AVATAR darf öffentliche Synonyme anlegen und wieder entfernen (wie MDSYS)
--
-- Danach im Avatar Studio unter DATEN noch einmal „Einrichten“ (oder 05_rechte.sql als AVATAR): dann gehen die
-- Rechte an die Rolle und die öffentlichen Synonyme entstehen. Neue Projekte bekommen später nur
--   GRANT AVATAR_USER TO <SCHEMA>;
-- Wiederholbar: ORA-01921 (Rolle schon da) ist harmlos. Dieses Skript löscht nichts.
-- =====================================================================================================

-- Nur, wenn du an der CDB (ORCL) angemeldet bist; an PDBORCL angemeldet diese Zeile weglassen:
-- ALTER SESSION SET CONTAINER = PDBORCL;

DECLARE
  TYPE T_L IS TABLE OF VARCHAR2(128);
  ZIELE T_L := T_L('DEMO', 'RUNGHOLT', 'GLACIER', 'HAITHABU', 'LIGHTHOUSE', 'KILAUEA', 'LOGISTIK');
  N PLS_INTEGER;
BEGIN
  BEGIN
    EXECUTE IMMEDIATE 'CREATE ROLE AVATAR_USER';
  EXCEPTION WHEN OTHERS THEN
    IF SQLCODE <> -1921 THEN RAISE; END IF;
  END;
  EXECUTE IMMEDIATE 'GRANT CREATE PUBLIC SYNONYM TO AVATAR';
  EXECUTE IMMEDIATE 'GRANT DROP PUBLIC SYNONYM TO AVATAR';
  FOR I IN 1 .. ZIELE.COUNT LOOP
    SELECT COUNT(*) INTO N FROM DBA_USERS WHERE USERNAME = ZIELE(I);
    IF N > 0 THEN EXECUTE IMMEDIATE 'GRANT AVATAR_USER TO ' || ZIELE(I); END IF;
  END LOOP;
END;
/

-- Kontrolle
SELECT GRANTEE, GRANTED_ROLE FROM DBA_ROLE_PRIVS WHERE GRANTED_ROLE = 'AVATAR_USER' ORDER BY 1;
SELECT GRANTEE, PRIVILEGE FROM DBA_SYS_PRIVS WHERE GRANTEE = 'AVATAR' AND PRIVILEGE LIKE '%PUBLIC SYNONYM' ORDER BY 2;

-- Ende 00_einmalig_als_dba.sql
