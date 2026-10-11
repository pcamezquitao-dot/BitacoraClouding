SELECT DATABASE() AS base_efectiva;
SELECT COUNT(*) AS columnas_c33_novedad
FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name='supervisor_novedad'
  AND column_name IN ('request_sha256','id_bitacora_registrada','id_bitacora_causada');
SELECT COUNT(*) AS tablas_c33
FROM information_schema.tables
WHERE table_schema=DATABASE()
  AND table_name IN ('bpm_supervisor_rh_actuacion','bpm_supervisor_rh_evidencia');
SELECT COUNT(*) AS filas_supervisor_novedad FROM supervisor_novedad;
SELECT COUNT(*) AS filas_bpm_actividad FROM bpm_actividad;
SELECT COUNT(*) AS filas_actuacion_c33 FROM bpm_supervisor_rh_actuacion;
SELECT COUNT(*) AS filas_evidencia_c33 FROM bpm_supervisor_rh_evidencia;
