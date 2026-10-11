SELECT DATABASE() AS base_efectiva;
SHOW CREATE TABLE supervisor_novedad;
SHOW CREATE TABLE bpm_operacion;
SHOW CREATE TABLE bpm_actividad;
SHOW CREATE TABLE bpm_evidencia;
SELECT COUNT(*) AS actuaciones_c33
FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name='bpm_supervisor_rh_actuacion';
SELECT COUNT(*) AS enlaces_evidencia_c33
FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name='bpm_supervisor_rh_evidencia';
SELECT COUNT(*) AS filas_supervisor_novedad FROM supervisor_novedad;
SELECT COUNT(*) AS filas_bpm_actividad FROM bpm_actividad;
