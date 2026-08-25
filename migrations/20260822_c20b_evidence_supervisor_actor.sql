-- C09/C20B: relacion auditable del supervisor actor. Aditiva y no destructiva.
-- Precondicion comprobada: participante.id_participante es INT(11).
ALTER TABLE bitacora_area_evidencia
    ADD COLUMN id_supervisor_actor INT(11) NULL AFTER id_bitacora,
    ADD INDEX idx_bae_supervisor_actor (id_supervisor_actor),
    ADD CONSTRAINT fk_bae_supervisor_actor
        FOREIGN KEY (id_supervisor_actor)
        REFERENCES participante (id_participante)
        ON UPDATE CASCADE
        ON DELETE RESTRICT;
