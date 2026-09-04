# C25 - ASIGNACIÓN DE JORNADAS A PARTICIPANTES

## Estado
VALIDADA

## Evidencia de validación física
Patricia validó físicamente en el dispositivo V2035, serial 3065397347006YM:
- seleccionó participante;
- seleccionó rol;
- seleccionó jornada;
- seleccionó área;
- pulsó Asignar;
- guardó correctamente en empleado_area;
- actualizó correctamente el árbol;
- retiró y permitió una nueva asignación;
- no apareció el mensaje "Identifique el administrador".

## Alcance y contrato
- Relación de persistencia: empleado_area.id_jornada.
- Campo de jornada: pull-down obligatorio (lista desplegable).
- Asignación y retiro operativos sin PIN ni identificación administrativa.
- Refresco automático del árbol tras Asignar y Retirar.
- Sin migraciones de MariaDB ni Room.

## Criterio de cierre
La célula queda validada cuando la revisión física confirma que la asignación, el retiro y el árbol se comportan correctamente sin pasos adicionales ni prompts administrativos.
