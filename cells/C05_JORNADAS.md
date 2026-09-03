# C05_JORNADAS

**ID:** C05_JORNADAS
**Objetivo:** definir jornadas, tramos y control horario.
**Responsabilidad:** cabeceras, detalles, reglas de activación y contrato de
`id_jornada`.

## Propietario de

- `jornadas_de_trabajo`, `jornadas_de_trabajo_detalle`, router
  `work_schedules.py` y servicio `work_schedule_service.py`.
- `WorkSchedulesAdminPanel.kt`, `WorkScheduleLocal.kt` y pruebas de
  migración/formulario.

## Archivos modificables

Router, schema, servicio, panel, entidades locales y pruebas de jornadas.

## Archivos solo lectura relacionados

`empleado_area`, calendario, informes, Room central y catálogo offline.

## Dependencias y contratos

- Publica código, tramos, objetivo semanal y `aplicaControlHorario`.
- C04 referencia la jornada; C13/C14 clasifican tiempo usando sus contratos.

## Datos / tablas

MariaDB `jornadas_de_trabajo`, `jornadas_de_trabajo_detalle`; Room equivalentes
locales.

## Pruebas

- **Propia:** objetivo semanal, límites, tramos y control horario.
- **Contrato:** serialización backend/Room de booleanos y detalles.
- **Global mínima:** jornada visible en Empleado–Área e informes.

## Autorización transversal puntual C05 → C23

Patricia autorizó exclusivamente que C23 ajuste la presentación del objetivo
semanal en `WorkSchedulesAdminPanel.kt` mediante Horas y Minutos (`00`, `15`,
`30`, `45`). El archivo y el contrato de múltiplos de 15 permanecen bajo C05;
no se autoriza cambiar almacenamiento, reglas ni esquema.

**Estado:** ANALISIS
**Versión:** PENDIENTE
**Último commit estable:** PENDIENTE
**Riesgos:** migraciones Room y nulabilidad histórica de `idJornada`.
**Observaciones:** no codificar límites legales dentro de pantallas.
