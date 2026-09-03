# C23 — CRUD Jornadas de trabajo

Estado: VALIDADA

## Objetivo

Administrar en línea las cabeceras de `jornadas_de_trabajo` y sus detalles
`jornadas_de_trabajo_detalle` desde Administrador → Administrar maestros.

## Alcance

Lista, consulta, creación, edición y cambio de estado sin eliminación física.
Las operaciones se realizan en una transacción sin autenticación administrativa
temporal. C23 no modifica asignaciones, Room ni la sincronización offline.

## Restricciones de una jornada referenciada

Si existe una asignación activa en `empleado_area`, solo se permiten cambios de
nombre y observaciones; no se permite inactivarla.

## Archivos modificables

Archivos propios C23 bajo `app/schemas`, `app/services`, `app/routers`,
`tests` y `android/.../ui/admin`. Conexiones autorizadas: `app/main.py`,
`Api.kt`, `OpenApiModels.kt`, `BitacoraRepository.kt` y
`AdminCatalogScreen.kt`.

## Autorización transversal puntual C05 → C23

Patricia autorizó exclusivamente el ajuste de presentación del objetivo semanal
en `WorkSchedulesAdminPanel.kt`: campos Horas y Minutos (`00`, `15`, `30`,
`45`). El archivo mantiene su propiedad en C05; C23 no modifica el contrato,
la validación múltiplo de 15 ni el almacenamiento `minutos_objetivo_semana`.

## Pruebas

Validación de esquema, reglas de jornadas, restricciones de referencia,
transacciones y contrato de interfaz sin PIN ni inicio de sesión.

## Validación física

Patricia validó satisfactoriamente C23 en V2035 `3065397347006YM`: lista,
creación y edición directa; cambio persistente Activa/Inactiva; y objetivo
semanal mediante Horas y Minutos en cuartos de hora. La validación usó
`JOR-TEST-01`; tras la prueba se restauró a 2520 minutos, Activa y con total
programado de 2520 minutos.
