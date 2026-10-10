-- Reversion C32. Requiere respaldo y comprobacion previa de que no existen
-- actuaciones C32 que deban conservarse. No ejecutar automaticamente.
DROP TABLE IF EXISTS bpm_evidencia;
DROP TABLE IF EXISTS bpm_historial;
DROP TABLE IF EXISTS bpm_operacion;
DROP TABLE IF EXISTS bpm_identidad_local;
DROP TABLE IF EXISTS bpm_caso_control;
DROP TABLE IF EXISTS bpm_etapa_config;
DROP TABLE IF EXISTS bpm_definicion_snapshot;
ALTER TABLE bpm_actividad DROP COLUMN IF EXISTS revision;
ALTER TABLE bpm_caso DROP COLUMN IF EXISTS revision;
