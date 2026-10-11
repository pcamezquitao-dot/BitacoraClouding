-- Reversion exclusiva de estructuras C33. No elimina novedades ni Bitacoras.
DROP TABLE IF EXISTS bpm_supervisor_rh_evidencia;
DROP TABLE IF EXISTS bpm_supervisor_rh_actuacion;
-- Las columnas C08 son compartidas y se conservan para evitar perdida de datos.
