-- Reversion C09/C20B. No elimina evidencias; retira solo la relacion agregada.
ALTER TABLE bitacora_area_evidencia
    DROP FOREIGN KEY fk_bae_supervisor_actor,
    DROP INDEX idx_bae_supervisor_actor,
    DROP COLUMN id_supervisor_actor;
