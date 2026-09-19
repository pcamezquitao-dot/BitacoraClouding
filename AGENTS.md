# AGENTS.md — Entrada obligatoria para Codex

## 1. Propósito

Este archivo es la puerta de entrada para todo trabajo de Codex en el proyecto Bitácora.

No contiene ni redefine las reglas metodológicas. La única autoridad metodológica superior es `INVARIANTES.md`, ubicado en la raíz del repositorio o worktree.

El término anterior “Decálogo” queda reemplazado por `INVARIANTES.md`.

## 2. Inicio obligatorio

Antes de planear, diagnosticar, ejecutar comandos o modificar archivos del proyecto, Codex debe:

1. Localizar `INVARIANTES.md` en la raíz del repositorio o worktree actual.
2. Leerlo completamente.
3. Comprobar su versión y estado.
4. Aplicar el procedimiento y los límites definidos allí.
5. Presentar el `CONTROL_PREVIO` exigido por `INVARIANTES.md` antes de cualquier modificación.

La lectura de `AGENTS.md` o `INVARIANTES.md` no constituye autorización para modificar el proyecto.

## 3. Falta, daño o contradicción

Si `INVARIANTES.md`:

- no existe;
- no puede abrirse;
- está vacío o incompleto;
- no identifica una versión vigente;
- contiene referencias imposibles de cumplir;
- o contradice otro documento aplicable;

Codex debe declarar `BLOQUEADO_ANTES_DE_ESCRIBIR`, mostrar la evidencia y solicitar una decisión a Patricia.

No puede reconstruir las reglas usando recuerdos, conversaciones anteriores, el antiguo Decálogo ni versiones de otras ramas.

## 4. Documentos subordinados

Después de leer `INVARIANTES.md`, Codex consultará únicamente los documentos necesarios para la tarea, como:

- la ficha de la célula objetivo;
- el inventario vigente de células;
- los contratos directamente consumidos;
- los documentos de procesos o usuarios aplicables;
- las evidencias y estados relacionados.

Estos documentos no pueden rebajar ni modificar implícitamente las invariantes.

Los documentos históricos sirven como evidencia, pero no se consideran vigentes automáticamente.

## 5. Protección documental

`AGENTS.md` e `INVARIANTES.md` no pueden modificarse incidentalmente durante una intervención funcional.

Su modificación requiere:

1. una tarea documental independiente;
2. autorización expresa de Patricia;
3. diagnóstico de contradicciones;
4. propuesta completa;
5. aprobación de Patricia antes de instalarla;
6. conservación de la versión anterior en Git.

## 6. Regla final

Ante cualquier diferencia entre este archivo e `INVARIANTES.md`, prevalece `INVARIANTES.md`.

Codex debe informar la contradicción y detenerse antes de escribir.
