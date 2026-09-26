-- C09: compatibiliza la tabla existente con el contrato FastAPI vigente.
-- IMPORTANTE: ALTER TABLE produce commit implicito en MariaDB; esta migracion
-- no es transaccional. Requiere respaldo verificado y script de reversion.
ALTER TABLE evidencia_transcripcion
    ALGORITHM=INPLACE,
    CHANGE COLUMN id_evidencia_audio id_evidencia BIGINT UNSIGNED NOT NULL;

ALTER TABLE evidencia_transcripcion
    ALGORITHM=COPY,
    MODIFY COLUMN texto_transcrito LONGTEXT NULL,
    MODIFY COLUMN idioma VARCHAR(20) NULL,
    MODIFY COLUMN proveedor VARCHAR(100) NULL,
    MODIFY COLUMN modelo VARCHAR(150) NULL,
    MODIFY COLUMN confianza DECIMAL(6,5) NULL,
    CHANGE COLUMN fecha_transcripcion completado_en DATETIME(6) NULL,
    CHANGE COLUMN created_at creado_en DATETIME(6)
        NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CHANGE COLUMN updated_at actualizado_en DATETIME(6)
        NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6);

SELECT COUNT(*) AS filas_despues FROM evidencia_transcripcion;
SHOW COLUMNS FROM evidencia_transcripcion;
