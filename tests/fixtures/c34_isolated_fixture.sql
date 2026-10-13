USE bitacora_c34_test;

INSERT INTO tipos_documentos(tipo_documento,descripcion) VALUES(1,'CP_C34_DOCUMENTO');
INSERT INTO tipos_participante(codigo,descripcion,activo) VALUES
 (9341,'CP_C34_ROL',1),(9342,'CP_C34_SIN_RAIZ',1),(9343,'CP_C34_DUPLICADA',1),
 (9344,'CP_C34_SIN_HIJOS',1);
INSERT INTO areas_administrativas(id_Area_Administrativa,descripcion,nombre_corto)
VALUES(93401,'CP_C34_AREA','CP34AREA');
INSERT INTO jornadas_de_trabajo
 (id_jornada,codigo_jornada,nombre_jornada,minutos_objetivo_semana,vigencia_desde,activo,aplica_control_horario)
VALUES(93401,'CP_C34_JORNADA','CP C34 Jornada',2400,'2026-01-01',1,1);
INSERT INTO participante
 (id_participante,tipo_documento,documento,identificacion_participante,nombre,apellido,fecha_entrada,fecha_salida)
VALUES
 (93401,1,'93401','CP-C34-ACTIVO','Activo','C34','2026-01-01',NULL),
 (93402,1,'93402','CP-C34-SIN-RAIZ','Sin Raiz','C34','2026-01-01',NULL),
 (93403,1,'93403','CP-C34-DUPLICADA','Duplicada','C34','2026-01-01',NULL),
 (93404,1,'93404','CP-C34-INACTIVO','Inactivo','C34','2025-01-01','2025-12-31'),
 (93405,1,'93405','CP-C34-MULTI','Multiple','C34','2026-01-01',NULL),
 (93406,1,'93406','CP-C34-SIN-TIPO','Sin Tipo','C34','2026-01-01',NULL),
 (93407,1,'93407','CP-C34-SIN-HIJOS','Sin Hijos','C34','2026-01-01',NULL);
INSERT INTO empleado_area(id_participante,id_area,cargo,descripcion,fecha_inicia,activo,id_jornada)
VALUES
 (93401,93401,9341,'CP C34','2026-01-01',1,93401),
 (93402,93401,9342,'CP C34','2026-01-01',1,93401),
 (93403,93401,9343,'CP C34','2026-01-01',1,93401),
 (93405,93401,9341,'CP C34','2026-01-01',1,93401),
 (93405,93401,9342,'CP C34','2026-01-01',1,93401),
 (93407,93401,9344,'CP C34','2026-01-01',1,93401);

INSERT INTO dim_proceso(id_proceso,nombre,descripcion,nombre_corto)
VALUES
 (93421,'CP_C34_PROCESO','Proceso con BPM','CP34_PROC'),
 (93422,'CP_C34_SIN_BPM','Proceso sin BPM','CP34_NO_BPM');
INSERT INTO bpm_proceso(id_bpm_proceso,nombre,version,descripcion,activo)
VALUES(93431,'CP_C34_BPM',1,'Flujo aislado C34',1);
INSERT INTO bpm_etapa(id_bpm_proceso,id_etapa,rol_responsable,plazo_horas,es_inicial,es_final)
VALUES(93431,93421,'CP_C34_ROL',1,1,1);
INSERT INTO bpm_etapa_config(id_bpm_proceso,id_etapa,instrucciones,resultados_json,documentos_requeridos,regla_asignacion)
VALUES(93431,93421,'Etapa de prueba','[]',0,'AFECTADO');

INSERT INTO dim_accion(id_accion,tipo_accion,nombre,descripcion,id_accion_padre,id_proceso)
VALUES(93440,1,'CP_C34_SIN_HIJOS','Raiz sin hijos',NULL,NULL);

INSERT INTO dim_accion(id_accion,tipo_accion,nombre,descripcion,id_accion_padre,id_proceso) VALUES
 (93410,1,'CP_C34_ROL','Raíz activa',NULL,NULL),
 (93411,1,'Proceso disponible','Opción válida',93410,93421),
 (93412,1,'Opción sin proceso','Sin destino',93410,NULL),
 (93413,1,'Proceso sin flujo BPM','Destino incompleto',93410,93422),
 (93414,1,'Etiqueta extremadamente larga para comprobar varias líneas sin cortar el texto','Texto largo',93410,93421),
 (93430,1,'CP_C34_DUPLICADA','Raíz duplicada A',NULL,NULL),
 (93431,1,'cp_c34_duplicada','Raíz duplicada B',NULL,NULL);

