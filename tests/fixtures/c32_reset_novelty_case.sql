-- Limpieza focal y repetible del caso sintético de apertura desde la novedad 932001.
SET @case_id := (
  SELECT id_caso FROM bpm_caso_control
  WHERE origen_tipo='NOVEDAD' AND origen_id='932001' LIMIT 1
);
DELETE FROM bpm_evidencia WHERE id_caso=@case_id;
DELETE FROM bpm_enlace_actividad WHERE id_caso=@case_id;
DELETE FROM bpm_historial WHERE id_caso=@case_id;
DELETE FROM bpm_operacion WHERE id_caso=@case_id;
DELETE FROM bpm_identidad_local
 WHERE (tipo_entidad='TAREA' AND id_servidor IN (
          SELECT id_actividad FROM bpm_actividad WHERE id_caso=@case_id
       ))
    OR (tipo_entidad='CASO' AND id_servidor=@case_id);
DELETE FROM bpm_actividad WHERE id_caso=@case_id;
DELETE FROM bpm_caso_control WHERE id_caso=@case_id;
DELETE FROM bpm_caso WHERE id_caso=@case_id;
