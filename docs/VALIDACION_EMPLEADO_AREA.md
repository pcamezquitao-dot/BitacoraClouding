# Validación obligatoria de `empleado_area`

El backend expone `GET /empleado-area/{id_participante}/activa`. Android debe usar
este endpoint después de resolver el QR con `GET /participante/by_qr/{qr}`. La
creación de bitácoras también repite la validación en el servidor para evitar que
un cliente desactualizado la omita.

La estructura observable en este repositorio usa `fecha_final` y considera vigente
una asignación cuando `fecha_final IS NULL OR fecha_final >= CURDATE()`. No existen
en el código ni en los scripts versionados columnas `fecha_in`/`fecha_out` para
`empleado_area`, y las credenciales locales configuradas no permiten inspeccionar
la tabla. Si la base desplegada usa esos nombres, se debe ajustar exclusivamente
`app/services/empleado_area_service.py` y `app/services/jerarquia_service.py` para
aplicar `fecha_in <= CURDATE()` y `(fecha_out IS NULL OR fecha_out > CURDATE())`.

El empleado debe coincidir exactamente con el área leída. Para el supervisor no se
impone igualdad: la regla existente en `jerarquia_service.py` permite encontrarlo
en el área actual o en un área ancestro mediante `nodo_padre`.
