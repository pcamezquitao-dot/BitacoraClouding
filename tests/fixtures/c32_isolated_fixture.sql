-- Datos sintéticos exclusivos de bitacora_c32_test.
SET @area_id := (SELECT MIN(id_Area_Administrativa) FROM areas_administrativas);
SET @jornada_id := (SELECT MIN(id_jornada) FROM jornadas_de_trabajo);

INSERT INTO tipos_participante(codigo,descripcion,activo)
VALUES (9321,'CP_C32_EMPLEADO',1),(9322,'CP_C32_SUPERVISOR_RH',1)
ON DUPLICATE KEY UPDATE descripcion=VALUES(descripcion),activo=1;

INSERT INTO participante
 (id_participante,tipo_documento,documento,identificacion_participante,nombre,apellido,
  fecha_entrada,observaciones)
VALUES
 (93201,1,'CP-C32-EMP','CP_C32_EMP','Empleado','Prueba C32',CURDATE(),'Fixture C32'),
 (93202,1,'CP-C32-RH','CP_C32_RH','Revisor RH','Prueba C32',CURDATE(),'Fixture C32')
ON DUPLICATE KEY UPDATE nombre=VALUES(nombre),apellido=VALUES(apellido),fecha_salida=NULL;

INSERT INTO empleado_area(id_participante,id_area,cargo,descripcion,fecha_inicia,activo,id_jornada)
SELECT 93201,@area_id,9321,'Fixture C32 empleado',CURDATE(),1,@jornada_id
WHERE NOT EXISTS (SELECT 1 FROM empleado_area WHERE id_participante=93201 AND cargo=9321 AND activo=1);
INSERT INTO empleado_area(id_participante,id_area,cargo,descripcion,fecha_inicia,activo,id_jornada)
SELECT 93202,@area_id,9322,'Fixture C32 RH',CURDATE(),1,@jornada_id
WHERE NOT EXISTS (SELECT 1 FROM empleado_area WHERE id_participante=93202 AND cargo=9322 AND activo=1);

INSERT INTO supervisor_novedad
 (id_novedad,client_uuid,tipo_novedad,id_participante,id_supervisor,id_area,fecha_inicio,
  fecha_final,observaciones,estado,origen)
SELECT 932001,'CP_C32_NOVEDAD_001',1,93201,1,@area_id,CURDATE(),CURDATE(),
       'Fixture C32 apertura desde Novedades','ACTIVO','SUPERVISOR'
WHERE NOT EXISTS (
  SELECT 1 FROM supervisor_novedad WHERE client_uuid='CP_C32_NOVEDAD_001'
);

INSERT INTO cat_actividad(nombre,descripcion) VALUES
 ('CP_C32_PRESENTAR_SOPORTE','Fixture: presentar soporte de inasistencia'),
 ('CP_C32_REVISAR_SOPORTE','Fixture: revisión RH'),
 ('CP_C32_CERRAR_CASO','Fixture: cierre controlado')
ON DUPLICATE KEY UPDATE descripcion=VALUES(descripcion);

SET @act_soporte := (SELECT id_actividad FROM cat_actividad WHERE nombre='CP_C32_PRESENTAR_SOPORTE');
SET @act_revision := (SELECT id_actividad FROM cat_actividad WHERE nombre='CP_C32_REVISAR_SOPORTE');
SET @act_cierre := (SELECT id_actividad FROM cat_actividad WHERE nombre='CP_C32_CERRAR_CASO');

INSERT INTO dim_proceso
 (id_proceso,nombre,descripcion,id_proceso_padre,tiempo_estimado,costo_estimado,
  tipo_proceso,precondicion,id_actividad,nombre_corto)
VALUES
 (932100,'CP_C32_INASISTENCIA','Fixture raíz C32',NULL,0,0,1,NULL,NULL,'C32_INASISTENCIA'),
 (932101,'CP_C32_SOPORTE','Fixture etapa soporte',932100,1,0,1,NULL,@act_soporte,'C32_SOPORTE'),
 (932102,'CP_C32_REVISION_RH','Fixture etapa revisión',932100,1,0,1,NULL,@act_revision,'C32_REVISION'),
 (932103,'CP_C32_FINAL','Fixture etapa final',932100,1,0,1,NULL,@act_cierre,'C32_FINAL')
ON DUPLICATE KEY UPDATE descripcion=VALUES(descripcion),id_actividad=VALUES(id_actividad);

INSERT INTO bpm_proceso(nombre,version,descripcion,activo)
SELECT 'CP_C32_INASISTENCIA',1,'Fixture C32 motor online/offline',1
WHERE NOT EXISTS (SELECT 1 FROM bpm_proceso WHERE nombre='CP_C32_INASISTENCIA' AND version=1);
SET @bpm := (SELECT id_bpm_proceso FROM bpm_proceso WHERE nombre='CP_C32_INASISTENCIA' AND version=1);
UPDATE bpm_proceso SET activo=1 WHERE id_bpm_proceso=@bpm;

INSERT IGNORE INTO bpm_etapa
 (id_bpm_proceso,id_etapa,rol_responsable,plazo_horas,es_inicial,es_final)
VALUES
 (@bpm,932101,'CP_C32_EMPLEADO',24,1,0),
 (@bpm,932102,'CP_C32_SUPERVISOR_RH',24,0,0),
 (@bpm,932103,'CP_C32_SUPERVISOR_RH',24,0,1);

INSERT INTO bpm_etapa_config
 (id_bpm_proceso,id_etapa,instrucciones,resultados_json,documentos_requeridos,regla_asignacion)
VALUES
 (@bpm,932101,'Adjunte el soporte de inasistencia','["SOPORTE_PRESENTADO"]',1,'AFECTADO'),
 (@bpm,932102,'Revise el soporte','["ACEPTADO","REQUIERE_CORRECCION"]',0,'UNICO_O_EXPLICITO'),
 (@bpm,932103,'Confirme el cierre','["CERRAR"]',0,'UNICO_O_EXPLICITO')
ON DUPLICATE KEY UPDATE instrucciones=VALUES(instrucciones),resultados_json=VALUES(resultados_json),
 documentos_requeridos=VALUES(documentos_requeridos),regla_asignacion=VALUES(regla_asignacion);

INSERT INTO bpm_transicion(id_bpm_proceso,id_etapa_origen,id_etapa_destino,codigo_regla,resultado,activo)
SELECT @bpm,932101,932102,'RESULTADO','SOPORTE_PRESENTADO',1
WHERE NOT EXISTS (SELECT 1 FROM bpm_transicion WHERE id_bpm_proceso=@bpm AND id_etapa_origen=932101 AND resultado='SOPORTE_PRESENTADO');
INSERT INTO bpm_transicion(id_bpm_proceso,id_etapa_origen,id_etapa_destino,codigo_regla,resultado,activo)
SELECT @bpm,932102,932101,'RESULTADO','REQUIERE_CORRECCION',1
WHERE NOT EXISTS (SELECT 1 FROM bpm_transicion WHERE id_bpm_proceso=@bpm AND id_etapa_origen=932102 AND resultado='REQUIERE_CORRECCION');
INSERT INTO bpm_transicion(id_bpm_proceso,id_etapa_origen,id_etapa_destino,codigo_regla,resultado,activo)
SELECT @bpm,932102,932103,'RESULTADO','ACEPTADO',1
WHERE NOT EXISTS (SELECT 1 FROM bpm_transicion WHERE id_bpm_proceso=@bpm AND id_etapa_origen=932102 AND resultado='ACEPTADO');

SELECT @bpm AS id_bpm_proceso,@area_id AS id_area,@jornada_id AS id_jornada;
