-- =====================================================================================================
-- Avatar · Datenbank · 99_entfernen.sql
-- ACHTUNG: Entfernt alle AV_-Objekte samt Inhalt (auch eigene Presets und die Anmeldungen der Projekte), die
-- Metadaten-Sichten, die Typen und die öffentlichen Synonyme auf AVATAR. Tabellen anderer Schemata mit einer Spalte
-- vom Typ AV_PERSON_T verhindern das Entfernen der Typen (ORA-02303); sie bleiben dann stehen.
-- Die App führt dieses Skript NIE aus; es ist nur für einen Neuanfang von Hand gedacht (SQL Developer, F5, als AVATAR).
-- =====================================================================================================

BEGIN
  FOR s IN (SELECT SYNONYM_NAME FROM ALL_SYNONYMS WHERE OWNER = 'PUBLIC' AND TABLE_OWNER = 'AVATAR') LOOP
    BEGIN EXECUTE IMMEDIATE 'DROP PUBLIC SYNONYM ' || s.SYNONYM_NAME; EXCEPTION WHEN OTHERS THEN NULL; END;
  END LOOP;
  FOR v IN (SELECT VIEW_NAME FROM USER_VIEWS WHERE VIEW_NAME LIKE 'AV\_%' ESCAPE '\' OR VIEW_NAME IN ('USER_AV_METADATA', 'ALL_AV_METADATA')) LOOP
    EXECUTE IMMEDIATE 'DROP VIEW ' || v.VIEW_NAME;
  END LOOP;
  FOR p IN (SELECT OBJECT_NAME FROM USER_OBJECTS WHERE OBJECT_TYPE = 'PACKAGE' AND OBJECT_NAME LIKE 'AV\_%' ESCAPE '\') LOOP
    EXECUTE IMMEDIATE 'DROP PACKAGE ' || p.OBJECT_NAME;
  END LOOP;
  FOR t IN (SELECT TABLE_NAME FROM USER_TABLES WHERE TABLE_NAME LIKE 'AV\_%' ESCAPE '\' AND NESTED = 'NO' AND IOT_TYPE IS NULL) LOOP
    EXECUTE IMMEDIATE 'DROP TABLE ' || t.TABLE_NAME || ' CASCADE CONSTRAINTS PURGE';
  END LOOP;
  FOR s IN (SELECT SEQUENCE_NAME FROM USER_SEQUENCES WHERE SEQUENCE_NAME LIKE 'AV\_%' ESCAPE '\') LOOP
    EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.SEQUENCE_NAME;
  END LOOP;
  FOR y IN (SELECT TYPE_NAME FROM USER_TYPES WHERE TYPE_NAME IN ('AV_ZUSTAND_T', 'AV_PERSON_T')) LOOP
    BEGIN EXECUTE IMMEDIATE 'DROP TYPE ' || y.TYPE_NAME; EXCEPTION WHEN OTHERS THEN NULL; END;
  END LOOP;
END;
/

-- Ende 99_entfernen.sql
