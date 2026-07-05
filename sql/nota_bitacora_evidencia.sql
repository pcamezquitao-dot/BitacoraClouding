-- ============================================================
-- AJUSTE RECOMENDADO PARA bitacora_area_evidencia
-- Base: novedades
-- Objetivo:
--   1. Permitir que id_evidencia se genere automáticamente.
--   2. Permitir varias evidencias por una misma bitácora.
--   3. Evitar que la evidencia dependa obligatoriamente de
--      bitacora_area_observacion cuando ya existe id_bitacora.
-- ============================================================

USE novedades;

-- 1) id_evidencia actualmente NO es AUTO_INCREMENT.
--    Esto obliga al backend a calcular MAX(id_evidencia)+1,
--    lo cual no es recomendable en producción.
ALTER TABLE bitacora_area_evidencia
  MODIFY id_evidencia BIGINT(20) UNSIGNED NOT NULL AUTO_INCREMENT;

-- 2) Actualmente existe:
--       UNIQUE KEY uq_bitacora_evidencia (id_bitacora)
--    Eso permite máximo UNA evidencia por cada bitacora_diaria.
--    Si una bitácora puede tener foto + audio + video, debe quitarse.
ALTER TABLE bitacora_area_evidencia
  DROP INDEX uq_bitacora_evidencia;

-- No se crea otro índice sobre id_bitacora porque la tabla ya tiene:
--       KEY idx_bae_id_bitacora (id_bitacora)

-- 3) OPCIONAL, pero recomendado si la evidencia debe depender directamente
--    de bitacora_diaria mediante id_bitacora.
--
--    La tabla tiene esta restricción:
--       fk_bae_observacion
--    que obliga a que exista previamente un registro en
--    bitacora_area_observacion con:
--       (id_empleado, id_supervisor, ts_in_min)
--
--    Si el flujo será:
--       POST /bitacora_diaria
--       POST /bitacora_area_evidencia/upload
--    entonces esta FK puede impedir insertar evidencias.
--    En ese caso, ejecutar:
--
-- ALTER TABLE bitacora_area_evidencia
--   DROP FOREIGN KEY fk_bae_observacion;
--
--    Si desea conservar el control por área, NO ejecute ese DROP.
--    En ese caso, antes de subir una evidencia debe existir primero
--    la observación de área correspondiente.

-- 4) Verificación final
SHOW CREATE TABLE bitacora_area_evidencia;
