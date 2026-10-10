-- C32 Motor BPM: contratos aditivos para versionado, idempotencia,
-- concurrencia, evidencias, historial y sincronizacion online/offline.
-- Ejecutar solo despues de respaldo y contra la base efectiva verificada.

ALTER TABLE bpm_caso
  ADD COLUMN IF NOT EXISTS revision INT UNSIGNED NOT NULL DEFAULT 1;

ALTER TABLE bpm_actividad
  ADD COLUMN IF NOT EXISTS revision INT UNSIGNED NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS bpm_definicion_snapshot (
  id_bpm_proceso BIGINT UNSIGNED NOT NULL,
  definicion_sha256 CHAR(64) NOT NULL,
  definicion_json LONGTEXT NOT NULL,
  fecha_captura DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_bpm_proceso),
  UNIQUE KEY uq_bpm_snapshot_hash (definicion_sha256),
  CONSTRAINT fk_bpm_snapshot_proceso FOREIGN KEY (id_bpm_proceso)
    REFERENCES bpm_proceso(id_bpm_proceso)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bpm_etapa_config (
  id_bpm_proceso BIGINT UNSIGNED NOT NULL,
  id_etapa INT NOT NULL,
  instrucciones TEXT NULL,
  resultados_json LONGTEXT NOT NULL,
  documentos_requeridos SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  regla_asignacion VARCHAR(40) NOT NULL DEFAULT 'UNICO_O_EXPLICITO',
  PRIMARY KEY (id_bpm_proceso, id_etapa),
  CONSTRAINT fk_bpm_etapa_config_etapa FOREIGN KEY (id_bpm_proceso,id_etapa)
    REFERENCES bpm_etapa(id_bpm_proceso,id_etapa),
  CONSTRAINT chk_bpm_etapa_config_documentos CHECK (documentos_requeridos >= 0),
  CONSTRAINT chk_bpm_etapa_config_regla CHECK
    (regla_asignacion IN ('AFECTADO','UNICO_O_EXPLICITO','EXPLICITO')),
  CONSTRAINT chk_bpm_etapa_config_resultados CHECK (JSON_VALID(resultados_json))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bpm_caso_control (
  id_caso BIGINT UNSIGNED NOT NULL,
  client_uuid VARCHAR(40) NOT NULL,
  solicitud_sha256 CHAR(64) NOT NULL,
  origen_tipo VARCHAR(30) NOT NULL,
  origen_id VARCHAR(100) NULL,
  definicion_sha256 CHAR(64) NOT NULL,
  dispositivo VARCHAR(100) NULL,
  fecha_captura DATETIME NULL,
  fecha_confirmacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_caso),
  UNIQUE KEY uq_bpm_caso_control_uuid (client_uuid),
  UNIQUE KEY uq_bpm_caso_origen (origen_tipo,origen_id),
  CONSTRAINT fk_bpm_caso_control_caso FOREIGN KEY (id_caso)
    REFERENCES bpm_caso(id_caso)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bpm_identidad_local (
  tipo_entidad ENUM('CASO','TAREA') NOT NULL,
  client_uuid VARCHAR(40) NOT NULL,
  id_servidor BIGINT UNSIGNED NOT NULL,
  dispositivo VARCHAR(100) NULL,
  fecha_confirmacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (tipo_entidad,client_uuid),
  UNIQUE KEY uq_bpm_identidad_servidor (tipo_entidad,id_servidor)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bpm_operacion (
  operacion_uuid VARCHAR(40) NOT NULL,
  dependencia_uuid VARCHAR(40) NULL,
  id_caso BIGINT UNSIGNED NULL,
  id_actividad BIGINT UNSIGNED NULL,
  tipo VARCHAR(30) NOT NULL,
  actor_codigo VARCHAR(50) NOT NULL,
  dispositivo VARCHAR(100) NULL,
  revision_base INT UNSIGNED NULL,
  id_bpm_proceso BIGINT UNSIGNED NULL,
  definicion_sha256 CHAR(64) NULL,
  fecha_captura DATETIME NULL,
  fecha_recepcion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  fecha_confirmacion DATETIME NULL,
  solicitud_sha256 CHAR(64) NOT NULL,
  solicitud_json LONGTEXT NOT NULL,
  estado ENUM('CONFIRMADA','CONFLICTO','RECHAZADA') NOT NULL,
  resultado_json LONGTEXT NULL,
  error TEXT NULL,
  PRIMARY KEY (operacion_uuid),
  KEY idx_bpm_operacion_caso (id_caso,fecha_recepcion),
  KEY idx_bpm_operacion_actividad (id_actividad,fecha_recepcion),
  KEY idx_bpm_operacion_dependencia (dependencia_uuid),
  CONSTRAINT fk_bpm_operacion_caso FOREIGN KEY (id_caso)
    REFERENCES bpm_caso(id_caso),
  CONSTRAINT fk_bpm_operacion_actividad FOREIGN KEY (id_actividad)
    REFERENCES bpm_actividad(id_actividad),
  CONSTRAINT chk_bpm_operacion_solicitud CHECK (JSON_VALID(solicitud_json)),
  CONSTRAINT chk_bpm_operacion_resultado CHECK
    (resultado_json IS NULL OR JSON_VALID(resultado_json))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Compatibilidad si una revisión anterior de esta migración ya creó la tabla.
ALTER TABLE bpm_operacion
  ADD COLUMN IF NOT EXISTS dependencia_uuid VARCHAR(40) NULL AFTER operacion_uuid;
CREATE INDEX IF NOT EXISTS idx_bpm_operacion_dependencia
  ON bpm_operacion (dependencia_uuid);

CREATE TABLE IF NOT EXISTS bpm_historial (
  id_historial BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  id_caso BIGINT UNSIGNED NOT NULL,
  id_actividad BIGINT UNSIGNED NULL,
  operacion_uuid VARCHAR(40) NOT NULL,
  evento VARCHAR(40) NOT NULL,
  actor_codigo VARCHAR(50) NOT NULL,
  actor_id INT NULL,
  estado_anterior VARCHAR(40) NULL,
  estado_nuevo VARCHAR(40) NULL,
  motivo TEXT NULL,
  detalle_json LONGTEXT NULL,
  fecha_captura DATETIME NULL,
  fecha_servidor DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_historial),
  UNIQUE KEY uq_bpm_historial_operacion_evento (operacion_uuid,evento),
  KEY idx_bpm_historial_caso (id_caso,fecha_servidor),
  CONSTRAINT fk_bpm_historial_caso FOREIGN KEY (id_caso)
    REFERENCES bpm_caso(id_caso),
  CONSTRAINT fk_bpm_historial_actividad FOREIGN KEY (id_actividad)
    REFERENCES bpm_actividad(id_actividad),
  CONSTRAINT fk_bpm_historial_actor FOREIGN KEY (actor_id)
    REFERENCES participante(id_participante),
  CONSTRAINT fk_bpm_historial_operacion FOREIGN KEY (operacion_uuid)
    REFERENCES bpm_operacion(operacion_uuid),
  CONSTRAINT chk_bpm_historial_detalle CHECK
    (detalle_json IS NULL OR JSON_VALID(detalle_json))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bpm_evidencia (
  id_evidencia BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  client_uuid VARCHAR(40) NOT NULL,
  id_caso BIGINT UNSIGNED NOT NULL,
  id_actividad BIGINT UNSIGNED NOT NULL,
  autor_id INT NOT NULL,
  nombre_archivo VARCHAR(255) NOT NULL,
  tipo_mime VARCHAR(100) NOT NULL,
  sha256 CHAR(64) NOT NULL,
  tamano BIGINT UNSIGNED NOT NULL,
  contenido LONGBLOB NOT NULL,
  fecha_captura DATETIME NULL,
  fecha_confirmacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id_evidencia),
  UNIQUE KEY uq_bpm_evidencia_uuid (client_uuid),
  UNIQUE KEY uq_bpm_evidencia_tarea_hash (id_actividad,sha256),
  KEY idx_bpm_evidencia_caso (id_caso,id_actividad),
  CONSTRAINT fk_bpm_evidencia_caso FOREIGN KEY (id_caso)
    REFERENCES bpm_caso(id_caso),
  CONSTRAINT fk_bpm_evidencia_actividad FOREIGN KEY (id_actividad)
    REFERENCES bpm_actividad(id_actividad),
  CONSTRAINT fk_bpm_evidencia_autor FOREIGN KEY (autor_id)
    REFERENCES participante(id_participante),
  CONSTRAINT chk_bpm_evidencia_tamano CHECK (tamano > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
