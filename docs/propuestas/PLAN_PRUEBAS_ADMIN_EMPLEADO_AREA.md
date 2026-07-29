# Plan de pruebas: administración jerárquica de empleado-área

Estado: propuesta. No autoriza aplicar migraciones ni modificar datos.

## Línea base confirmada

- 38 áreas, una raíz y profundidad máxima de cuatro.
- Cero ciclos, padres huérfanos o autorreferencias.
- 51 participantes y 51 asignaciones.
- Cero rangos inválidos, solapamientos o referencias inexistentes.
- Punto Android de retorno: `jaime05-checkpoint`.

## Catálogo dinámico de tipos

1. Crear un tipo con descripción única y al menos una capacidad.
2. Rechazar una descripción vacía o duplicada normalizada.
3. Editar la descripción sin cambiar `codigo`.
4. Agregar y retirar capacidades dentro de una transacción.
5. Impedir retirar todas las capacidades de un tipo usado y activo.
6. Desactivar un tipo conservando asignaciones históricas.
7. Impedir nuevas asignaciones con un tipo inactivo.
8. Impedir eliminar físicamente un tipo referenciado por `empleado_area`.
9. Registrar actor, dispositivo, valores anterior/nuevo y fecha.
10. Rechazar escrituras sin autenticación administrativa.

## Árbol de áreas

1. Cargar los 38 nodos y alcanzar todos desde la raíz.
2. Mostrar ruta, nivel y padre de cada nodo.
3. Rechazar padre inexistente, autorreferencia y ciclos.
4. Conservar la selección al expandir o contraer ramas.
5. Asignar al nodo exacto sin duplicar filas en ancestros o descendientes.
6. Resolver capacidades de supervisión por ascenso jerárquico.

## Vigencias de empleado_area

1. Exigir `fecha_inicia`.
2. Aceptar `fecha_final` nula.
3. Rechazar `fecha_final < fecha_inicia`.
4. Considerar vigente solo si:
   `fecha_inicia <= hoy AND (fecha_final IS NULL OR fecha_final >= hoy)`.
5. Rechazar periodos incompatibles solapados.
6. Trasladar cerrando la asignación anterior y creando una nueva.
7. Ejecutar el traslado completo en una única transacción.
8. Conservar el historial y la auditoría.

## Catálogo offline y Android

1. Sincronizar tipos, capacidades, padre y nivel de las áreas.
2. Conservar el catálogo anterior si una actualización falla.
3. Ver el árbol y las asignaciones vigentes sin red.
4. No permitir escrituras maestras offline en la primera versión.
5. Confirmar que P0001 conserva el comportamiento aprobado.
6. Confirmar que empleado, supervisor y gerente se resuelven por capacidad,
   no por números codificados en Android.

## No regresión

Ejecutar todas las pruebas Android y backend existentes, además de creación,
consulta y sincronización de bitácoras con texto, foto, audio y video; comprobar
reconocimiento y persistencia facial, actualización de catálogos y operación
online/offline. No generar APK final ante cualquier fallo.
