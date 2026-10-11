-- Datos sinteticos exclusivos de bitacora_c33_test.
SET @area_id := (SELECT MIN(id_Area_Administrativa) FROM areas_administrativas);
SET @jornada_id := (SELECT MIN(id_jornada) FROM jornadas_de_trabajo);
SET @operario := (SELECT codigo FROM tipos_participante WHERE LOWER(TRIM(descripcion))='operario' AND activo=1 LIMIT 1);
SET @rh := (SELECT codigo FROM tipos_participante WHERE LOWER(TRIM(descripcion))='supervisor_rh' AND activo=1 LIMIT 1);

INSERT INTO participante
 (id_participante,tipo_documento,documento,identificacion_participante,nombre,apellido,
  fecha_entrada,observaciones)
VALUES
 (93301,1,'CP-C33-EMP','CP_C33_EMP','Empleado','Prueba C33',CURDATE(),'Fixture C33'),
 (93302,1,'CP-C33-RH','CP_C33_RH','Supervisor RH','Prueba C33',CURDATE(),'Fixture C33')
ON DUPLICATE KEY UPDATE nombre=VALUES(nombre),apellido=VALUES(apellido),fecha_salida=NULL;

INSERT INTO empleado_area(id_participante,id_area,cargo,descripcion,fecha_inicia,activo,id_jornada)
SELECT 93301,@area_id,@operario,'Fixture C33 empleado',CURDATE(),1,@jornada_id
WHERE NOT EXISTS (SELECT 1 FROM empleado_area WHERE id_participante=93301 AND cargo=@operario AND activo=1);
INSERT INTO empleado_area(id_participante,id_area,cargo,descripcion,fecha_inicia,activo,id_jornada)
SELECT 93302,@area_id,@rh,'Fixture C33 Supervisor RH',CURDATE(),1,@jornada_id
WHERE NOT EXISTS (SELECT 1 FROM empleado_area WHERE id_participante=93302 AND cargo=@rh AND activo=1);

INSERT INTO cat_actividad(nombre,descripcion) VALUES
 ('CP_C33_PRESENTAR_SOPORTE','Fixture C33: presentar soporte'),
 ('CP_C33_REVISAR_RH','Fixture C33: decision Supervisor RH'),
 ('CP_C33_CERRAR','Fixture C33: cierre')
ON DUPLICATE KEY UPDATE descripcion=VALUES(descripcion);
SET @a1 := (SELECT id_actividad FROM cat_actividad WHERE nombre='CP_C33_PRESENTAR_SOPORTE');
SET @a2 := (SELECT id_actividad FROM cat_actividad WHERE nombre='CP_C33_REVISAR_RH');
SET @a3 := (SELECT id_actividad FROM cat_actividad WHERE nombre='CP_C33_CERRAR');

INSERT INTO dim_proceso
 (id_proceso,nombre,descripcion,id_proceso_padre,tiempo_estimado,costo_estimado,
  tipo_proceso,precondicion,id_actividad,nombre_corto)
VALUES
 (933100,'CP_C33_INASISTENCIA','Fixture raiz C33',NULL,0,0,1,NULL,NULL,'C33_INASISTENCIA'),
 (933101,'CP_C33_SOPORTE','Fixture soporte',933100,1,0,1,NULL,@a1,'C33_SOPORTE'),
 (933102,'CP_C33_REVISION_RH','Fixture revision',933100,1,0,1,NULL,@a2,'C33_REVISION'),
 (933103,'CP_C33_FINAL','Fixture final',933100,1,0,1,NULL,@a3,'C33_FINAL')
ON DUPLICATE KEY UPDATE descripcion=VALUES(descripcion),id_actividad=VALUES(id_actividad);

INSERT INTO bpm_proceso(nombre,version,descripcion,activo)
SELECT 'CP_C33_INASISTENCIA',1,'Fixture C33 Supervisor RH',1
WHERE NOT EXISTS (SELECT 1 FROM bpm_proceso WHERE nombre='CP_C33_INASISTENCIA' AND version=1);
SET @bpm := (SELECT id_bpm_proceso FROM bpm_proceso WHERE nombre='CP_C33_INASISTENCIA' AND version=1);
UPDATE bpm_proceso SET activo=1 WHERE id_bpm_proceso=@bpm;

INSERT IGNORE INTO bpm_etapa
 (id_bpm_proceso,id_etapa,rol_responsable,plazo_horas,es_inicial,es_final)
VALUES
 (@bpm,933101,'operario',24,1,0),
 (@bpm,933102,'supervisor_RH',24,0,0),
 (@bpm,933103,'supervisor_RH',24,0,1);
INSERT INTO bpm_etapa_config
 (id_bpm_proceso,id_etapa,instrucciones,resultados_json,documentos_requeridos,regla_asignacion)
VALUES
 (@bpm,933101,'Adjunte soporte CP_C33','["SOPORTE_PRESENTADO"]',1,'AFECTADO'),
 (@bpm,933102,'Revise soporte y decida','["ACEPTADO","REQUIERE_CORRECCION"]',0,'UNICO_O_EXPLICITO'),
 (@bpm,933103,'Confirme cierre CP_C33','["CERRAR"]',0,'UNICO_O_EXPLICITO')
ON DUPLICATE KEY UPDATE instrucciones=VALUES(instrucciones),resultados_json=VALUES(resultados_json),
 documentos_requeridos=VALUES(documentos_requeridos),regla_asignacion=VALUES(regla_asignacion);

INSERT INTO bpm_transicion(id_bpm_proceso,id_etapa_origen,id_etapa_destino,codigo_regla,resultado,activo)
SELECT @bpm,933101,933102,'RESULTADO','SOPORTE_PRESENTADO',1
WHERE NOT EXISTS (SELECT 1 FROM bpm_transicion WHERE id_bpm_proceso=@bpm AND id_etapa_origen=933101 AND resultado='SOPORTE_PRESENTADO');
INSERT INTO bpm_transicion(id_bpm_proceso,id_etapa_origen,id_etapa_destino,codigo_regla,resultado,activo)
SELECT @bpm,933102,933101,'RESULTADO','REQUIERE_CORRECCION',1
WHERE NOT EXISTS (SELECT 1 FROM bpm_transicion WHERE id_bpm_proceso=@bpm AND id_etapa_origen=933102 AND resultado='REQUIERE_CORRECCION');
INSERT INTO bpm_transicion(id_bpm_proceso,id_etapa_origen,id_etapa_destino,codigo_regla,resultado,activo)
SELECT @bpm,933102,933103,'RESULTADO','ACEPTADO',1
WHERE NOT EXISTS (SELECT 1 FROM bpm_transicion WHERE id_bpm_proceso=@bpm AND id_etapa_origen=933102 AND resultado='ACEPTADO');

SELECT @bpm AS id_bpm_proceso,@area_id AS id_area,@operario AS rol_empleado,@rh AS rol_rh;
