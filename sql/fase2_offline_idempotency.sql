-- Ejecutar una sola vez después de revisar que no existan UUID duplicados.
-- Las aplicaciones usan parámetros preparados; este script manual no contiene placeholders.

CREATE UNIQUE INDEX uq_bitacora_diaria_client_uuid
    ON bitacora_diaria (client_uuid);

CREATE UNIQUE INDEX uq_bitacora_area_evidencia_uuid_cliente
    ON bitacora_area_evidencia (uuid_cliente);
