-- C09: restaura el esquema observado antes de la migracion del 2026-09-25.
-- IMPORTANTE: ALTER TABLE produce commit implicito en MariaDB; esta reversion
-- no es transaccional. Si existen filas con texto NULL, debe restaurarse el
-- dump previo en vez de ejecutar este ALTER.
ALTER TABLE evidencia_transcripcion
    ALGORITHM=COPY,
    MODIFY COLUMN texto_transcrito TEXT NOT NULL,
    MODIFY COLUMN idioma VARCHAR(10) NULL DEFAULT 'es',
    MODIFY COLUMN proveedor VARCHAR(50) NULL,
    MODIFY COLUMN modelo VARCHAR(100) NULL,
    MODIFY COLUMN confianza DECIMAL(5,4) NULL,
    CHANGE COLUMN completado_en fecha_transcripcion DATETIME NULL,
    CHANGE COLUMN creado_en created_at DATETIME
        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHANGE COLUMN actualizado_en updated_at DATETIME
        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

ALTER TABLE evidencia_transcripcion
    ALGORITHM=INPLACE,
    CHANGE COLUMN id_evidencia id_evidencia_audio BIGINT UNSIGNED NOT NULL;

SELECT COUNT(*) AS filas_despues_reversion FROM evidencia_transcripcion;
SHOW COLUMNS FROM evidencia_transcripcion;
