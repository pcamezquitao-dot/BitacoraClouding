-- Persistencia independiente de transcripciones de evidencias de audio.
-- Idempotente: no elimina ni reemplaza tablas o datos existentes.
CREATE TABLE IF NOT EXISTS evidencia_transcripcion (
    id_transcripcion BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    id_evidencia BIGINT UNSIGNED NOT NULL,
    id_bitacora BIGINT UNSIGNED NOT NULL,
    estado ENUM('PENDIENTE', 'PROCESANDO', 'COMPLETADA', 'ERROR')
        NOT NULL DEFAULT 'PENDIENTE',
    proveedor VARCHAR(100) NULL,
    modelo VARCHAR(150) NULL,
    idioma VARCHAR(20) NULL,
    confianza DECIMAL(6,5) NULL,
    texto_transcrito LONGTEXT NULL,
    numero_reintentos INT UNSIGNED NOT NULL DEFAULT 0,
    ultimo_error TEXT NULL,
    creado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    actualizado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    completado_en DATETIME(6) NULL,
    PRIMARY KEY (id_transcripcion),
    UNIQUE KEY uq_evidencia_transcripcion_evidencia (id_evidencia),
    KEY idx_evidencia_transcripcion_bitacora (id_bitacora),
    KEY idx_evidencia_transcripcion_estado (estado),
    CONSTRAINT fk_evidencia_transcripcion_evidencia
        FOREIGN KEY (id_evidencia)
        REFERENCES bitacora_area_evidencia (id_evidencia)
        ON UPDATE CASCADE ON DELETE CASCADE,
    CONSTRAINT fk_evidencia_transcripcion_bitacora
        FOREIGN KEY (id_bitacora)
        REFERENCES bitacora_diaria (id_bitacora)
        ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Debe devolver BIGINT UNSIGNED para ambas columnas relacionadas.
SELECT
    c.TABLE_NAME AS tabla,
    c.COLUMN_NAME AS columna,
    c.COLUMN_TYPE AS tipo_exacto
FROM information_schema.COLUMNS AS c
WHERE c.TABLE_SCHEMA = DATABASE()
  AND (
      (c.TABLE_NAME = 'evidencia_transcripcion'
       AND c.COLUMN_NAME IN ('id_evidencia', 'id_bitacora'))
      OR
      (c.TABLE_NAME = 'bitacora_area_evidencia' AND c.COLUMN_NAME = 'id_evidencia')
      OR
      (c.TABLE_NAME = 'bitacora_diaria' AND c.COLUMN_NAME = 'id_bitacora')
  )
ORDER BY c.TABLE_NAME, c.ORDINAL_POSITION;

-- Validación solicitada de llaves foráneas.
SELECT
    k.CONSTRAINT_NAME AS nombre_restriccion,
    k.COLUMN_NAME AS columna_local,
    k.REFERENCED_TABLE_NAME AS tabla_referenciada,
    k.REFERENCED_COLUMN_NAME AS columna_referenciada
FROM information_schema.KEY_COLUMN_USAGE AS k
WHERE k.TABLE_SCHEMA = DATABASE()
  AND k.TABLE_NAME = 'evidencia_transcripcion'
  AND k.REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY k.CONSTRAINT_NAME;

-- Prueba manual no persistente con una evidencia de audio existente.
-- START TRANSACTION;
-- INSERT INTO evidencia_transcripcion
--     (id_evidencia, id_bitacora, estado, proveedor, modelo, idioma)
-- SELECT e.id_evidencia, e.id_bitacora, 'PENDIENTE',
--        'PRUEBA_ROLLBACK', 'PRUEBA_ROLLBACK', 'es'
-- FROM bitacora_area_evidencia AS e
-- JOIN tipo_evidencia AS t
--   ON t.id_tipo_evidencia = e.id_tipo_evidencia
-- WHERE UPPER(t.nombre) = 'AUDIO'
--   AND NOT EXISTS (
--       SELECT 1 FROM evidencia_transcripcion AS et
--       WHERE et.id_evidencia = e.id_evidencia
--   )
-- ORDER BY e.id_evidencia
-- LIMIT 1;
-- SELECT ROW_COUNT() AS filas_insertadas_para_prueba;
-- ROLLBACK;
