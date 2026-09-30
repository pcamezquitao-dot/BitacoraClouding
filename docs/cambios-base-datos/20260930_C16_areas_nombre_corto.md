# C16 — Restricción de `nombre_corto` en áreas administrativas

## Estado

**EJECUTADO MANUALMENTE POR PATRICIA. NO VOLVER A EJECUTAR.**

- Fecha informada: 2026-09-30.
- Base: `bitacora`.
- Tabla: `areas_administrativas`.
- Responsable de la ejecución: Patricia.
- Verificación informada: `SHOW CREATE TABLE bitacora.areas_administrativas`.
- Resultado informado: `Query OK, 7 rows affected`, `Duplicates: 0` y
  `Warnings: 0`.
- Este documento es un registro histórico; no es una migración pendiente ni un script autorizado para ejecución.

Antes del cambio, Patricia comprobó que no existían valores duplicados, nulos,
vacíos ni con espacios externos en `nombre_corto`.

## SQL registrado

La siguiente es la sentencia literal ejecutada manualmente por Patricia. Se
conserva solo como registro histórico:

```sql
ALTER TABLE bitacora.areas_administrativas
MODIFY COLUMN nombre_corto VARCHAR(25) NOT NULL,
ADD CONSTRAINT uq_areas_nombre_corto
UNIQUE (nombre_corto),
ADD CONSTRAINT chk_areas_nombre_corto_valido
CHECK (
CHAR_LENGTH(TRIM(nombre_corto)) > 0
AND OCTET_LENGTH(nombre_corto)
= OCTET_LENGTH(TRIM(nombre_corto))
);
```

## Estructura confirmada después del cambio

- `nombre_corto VARCHAR(25) NOT NULL`.
- `UNIQUE KEY uq_areas_nombre_corto (nombre_corto)`.
- `CHECK chk_areas_nombre_corto_valido` exige contenido no vacío después de
  `TRIM` y prohíbe espacios externos.
- Collation existente: `utf8mb4_uca1400_ai_ci`.
- Se conservaron la clave primaria y la relación de `nodo_padre`.
- `SHOW CREATE TABLE` confirmó las restricciones después de la ejecución.

La unicidad se evalúa con la collation existente, que no distingue mayúsculas
ni acentos. El contrato C16 exige que la aplicación quite espacios externos
antes de validar y guardar.

## Alcance de este registro

No se ejecutó SQL al crear este documento. No se modificaron datos, esquema,
código Android, API ni APK.
