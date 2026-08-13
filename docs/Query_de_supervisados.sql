WITH RECURSIVE areas_supervisadas AS (
    SELECT aa.id_Area_Administrativa
    FROM empleado_area es
    INNER JOIN areas_administrativas aa
        ON aa.id_Area_Administrativa = es.id_area
    INNER JOIN participante ps
        ON ps.id_participante = es.id_participante
    WHERE ps.identificacion_participante = 'P0002'
      AND es.activo = 1

    UNION

    SELECT hija.id_Area_Administrativa
    FROM areas_administrativas hija
    INNER JOIN areas_supervisadas padre
        ON hija.nodo_padre = padre.id_Area_Administrativa
)
SELECT DISTINCT
    p.id_participante,
    p.identificacion_participante,
    p.nombre,
    p.apellido,
    aa.id_Area_Administrativa AS id_area,
    aa.descripcion AS area,
    ea.cargo,
    ea.activo
FROM areas_supervisadas ars
INNER JOIN empleado_area ea
    ON ea.id_area = ars.id_Area_Administrativa
INNER JOIN participante p
    ON p.id_participante = ea.id_participante
INNER JOIN areas_administrativas aa
    ON aa.id_Area_Administrativa = ea.id_area
WHERE ea.activo = 1
ORDER BY
    aa.descripcion,
    p.apellido,
    p.nombre;