-- C09: conserva separadamente la salida automática y la corrección administrativa.
-- MariaDB realiza commit implícito para ALTER TABLE; esta migración no es transaccional.
ALTER TABLE evidencia_transcripcion
    ADD COLUMN texto_automatico LONGTEXT NULL AFTER texto_transcrito,
    ADD COLUMN texto_corregido LONGTEXT NULL AFTER texto_automatico,
    ADD COLUMN corregido_en DATETIME(6) NULL AFTER completado_en;

UPDATE evidencia_transcripcion
SET texto_automatico = texto_transcrito
WHERE texto_automatico IS NULL
  AND texto_transcrito IS NOT NULL;

SELECT ROW_COUNT() AS textos_automaticos_preservados;
SHOW COLUMNS FROM evidencia_transcripcion;
