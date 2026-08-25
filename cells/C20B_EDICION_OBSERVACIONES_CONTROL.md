# C20B_EDICION_OBSERVACIONES_CONTROL

**Estado:** PRUEBAS

## Objetivo

Permitir que el supervisor autenticado seleccione un día de CONTROL y modifique exclusivamente `observaciones` de una fila tipo 4 o 5 dentro de su alcance.

## Contratos aditivos

- `GET /control/supervisor/me/participantes/{participant_id}/bitacoras?fecha=YYYY-MM-DD`.
- `PATCH /control/supervisor/me/bitacoras/{bitacora_id}/observaciones`.
- Ambos exigen Bearer y obtienen el supervisor exclusivamente del token.
- El PATCH recibe el valor anterior para comparación optimista; una diferencia devuelve HTTP 409.

## Atomicidad y auditoría

El servicio ejecuta `SELECT ... FOR UPDATE`, revalida tipo y alcance, actualiza solo `observaciones` e inserta una evidencia tipo texto con antes, después, participante, bitácora, supervisor actor y hora `America/Bogota`. El router confirma una sola vez. Cualquier error ejecuta rollback. Si no hay cambio, no actualiza ni crea evidencia.

## Android

- Una barra seleccionada carga por servidor las filas reales 4 y 5, sin combinarlas.
- Cada fila muestra identificador, hora, tipo y observación.
- `ACTUALIZAR Y CERRAR` cierra solamente tras respuesta exitosa y refresca detalle e histograma.
- HTTP 409 u otro error conserva abierto el editor y el texto escrito.
- La edición requiere conexión y no escribe directamente Room/SQLite.

## Evidencia de pruebas

- Backend focalizado: autorización, tipos, fila única, no-cambio, evidencia única, 403, 409 y rollback.
- Android: contrato de selección, filas independientes, comparación optimista, cierre/refresco y zona Bogotá.
- Backend desplegado únicamente en desarrollo; APK no instalada.

## Integración real 2026-08-22

- P0002: 17 supervisados; P0003: 11 supervisados.
- Participante fuera del alcance de P0002: PATCH HTTP 403 y cero evidencias nuevas.
- P0015, 2026-06-01: filas 318 tipo 4 y 319 tipo 5 separadas.
- Bitácora 318: `DATOS_PRUEBA_P0015` → `C20B_PRUEBA_INTEGRACION_20260822`.
- Evidencia 260: tipo texto, bitácora 318, actor 2, antes y después completos.
- Segundo PATCH con valor anterior obsoleto: HTTP 409 y ninguna evidencia adicional.
- PATCH sin cambio: `modificada=false`, sin evidencia.
- Falla forzada de evidencia mediante UUID duplicado dentro del servicio real: rollback confirmado, observación intacta y conteo 16 → 16.
- Servicio `bitacora-api`: activo; salud HTTPS 200; sin advertencias posteriores a la estabilización.
