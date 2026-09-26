-- Reversión estructural. Ejecutar solo tras respaldar nuevamente.
-- MariaDB realiza commit implícito para ALTER TABLE; no es transaccional.
ALTER TABLE evidencia_transcripcion
    DROP COLUMN corregido_en,
    DROP COLUMN texto_corregido,
    DROP COLUMN texto_automatico;

SHOW COLUMNS FROM evidencia_transcripcion;
