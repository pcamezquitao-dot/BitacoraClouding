-- C33 Supervisor RH: vinculo transaccional entre decision BPM, Bitacora,
-- novedad C08 y evidencias. Migracion aditiva e idempotente.

ALTER TABLE supervisor_novedad
  ADD COLUMN IF NOT EXISTS request_sha256 CHAR(64) NULL,
  ADD COLUMN IF NOT EXISTS id_bitacora_registrada BIGINT UNSIGNED NULL,
  ADD COLUMN IF NOT EXISTS id_bitacora_causada BIGINT UNSIGNED NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_supervisor_novedad_bitacora_registrada
  ON supervisor_novedad(id_bitacora_registrada);
CREATE INDEX IF NOT EXISTS idx_supervisor_novedad_bitacora_causada
  ON supervisor_novedad(id_bitacora_causada);

CREATE TABLE IF NOT EXISTS bpm_supervisor_rh_actuacion (
  operacion_uuid VARCHAR(40) NOT NULL,
  id_caso BIGINT UNSIGNED NOT NULL,
  id_actividad BIGINT UNSIGNED NOT NULL,
  id_bitacora BIGINT UNSIGNED NOT NULL,
  id_novedad BIGINT NOT NULL,
  id_novedad_origen BIGINT NULL,
  resultado VARCHAR(100) NOT NULL,
  actor_id INT NOT NULL,
  fecha_confirmacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (operacion_uuid),
  UNIQUE KEY uq_c33_actividad (id_actividad),
  UNIQUE KEY uq_c33_bitacora (id_bitacora),
  UNIQUE KEY uq_c33_novedad (id_novedad),
  KEY idx_c33_caso (id_caso,fecha_confirmacion),
  KEY idx_c33_novedad_origen (id_novedad_origen),
  CONSTRAINT fk_c33_operacion FOREIGN KEY (operacion_uuid)
    REFERENCES bpm_operacion(operacion_uuid),
  CONSTRAINT fk_c33_caso FOREIGN KEY (id_caso) REFERENCES bpm_caso(id_caso),
  CONSTRAINT fk_c33_actividad FOREIGN KEY (id_actividad)
    REFERENCES bpm_actividad(id_actividad),
  CONSTRAINT fk_c33_bitacora FOREIGN KEY (id_bitacora)
    REFERENCES bitacora_diaria(id_bitacora),
  CONSTRAINT fk_c33_novedad FOREIGN KEY (id_novedad)
    REFERENCES supervisor_novedad(id_novedad),
  CONSTRAINT fk_c33_novedad_origen FOREIGN KEY (id_novedad_origen)
    REFERENCES supervisor_novedad(id_novedad),
  CONSTRAINT fk_c33_actor FOREIGN KEY (actor_id)
    REFERENCES participante(id_participante)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS bpm_supervisor_rh_evidencia (
  operacion_uuid VARCHAR(40) NOT NULL,
  id_evidencia BIGINT UNSIGNED NOT NULL,
  id_bitacora BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (operacion_uuid,id_evidencia),
  UNIQUE KEY uq_c33_evidencia (id_evidencia),
  KEY idx_c33_evidencia_bitacora (id_bitacora),
  CONSTRAINT fk_c33_evidencia_operacion FOREIGN KEY (operacion_uuid)
    REFERENCES bpm_supervisor_rh_actuacion(operacion_uuid),
  CONSTRAINT fk_c33_evidencia_bpm FOREIGN KEY (id_evidencia)
    REFERENCES bpm_evidencia(id_evidencia),
  CONSTRAINT fk_c33_evidencia_bitacora FOREIGN KEY (id_bitacora)
    REFERENCES bitacora_diaria(id_bitacora)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
