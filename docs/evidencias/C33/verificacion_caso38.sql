SELECT a.operacion_uuid,a.id_caso,a.id_actividad,a.id_bitacora,a.id_novedad,
       a.resultado,COUNT(e.id_evidencia) AS evidencias
FROM bpm_supervisor_rh_actuacion a
LEFT JOIN bpm_supervisor_rh_evidencia e
  ON e.operacion_uuid=a.operacion_uuid
WHERE a.operacion_uuid='68066ed6-d0b1-4d66-bd31-0563810cb99e'
GROUP BY a.operacion_uuid,a.id_caso,a.id_actividad,a.id_bitacora,a.id_novedad,
         a.resultado;

SELECT id_novedad,tipo_novedad,id_participante,id_supervisor,id_area,
       fecha_inicio,fecha_final,estado,origen,id_bitacora_registrada
FROM supervisor_novedad
WHERE client_uuid='68066ed6-d0b1-4d66-bd31-0563810cb99e';

SELECT COUNT(*) AS operaciones
FROM bpm_operacion
WHERE operacion_uuid='68066ed6-d0b1-4d66-bd31-0563810cb99e';
