# PLAN_C16_Pruebas_Administracion_Areas — revisión 03

## CONTROL DOCUMENTAL
Identificador: PLAN_C16_Pruebas_Administracion_Areas.
Revisión: 03 — decisiones contractuales de C16 incorporadas.
Fecha de elaboración: 2026-09-30. Esta fecha no sustituye la fecha de ejecución.
Cambio: se formalizan la unicidad de descripción bajo el mismo padre, la obligatoriedad y unicidad global de nombre_corto, la normalización de espacios externos, la comparación según utf8mb4_uca1400_ai_ci y el orden jerárquico con hermanos alfabéticos. Se incorporan **Referencia** como término visible, el rechazo seguro de respuestas remotas con cero áreas, la acreditación de acceso propia de C16 sin autenticación C24 y la referencia visual de Participantes para los controles por área. Se conservan las diez condiciones, las referencias a ENT-001 y el registro de ejecución.
Estado: documento de planificación; no acredita ejecución ni aprobación de C16.

## REFERENCIA COMÚN DEL ENTORNO
Ficha: ENT-001, revisión 01.
Archivo: docs/entornos/ENT-001_rev01.md.
Evidencias comunes: evidencias/ENT-001/rev01/.
Rama documental publicada: fix/c03-c11-c12-c16-areas-sync.
Commit documental de referencia: 29ca399b4632586f8e79aa5e0dbef2394854b27d, según el informe de CODEX suministrado por Patricia.
La ficha concentra rama y commit del código probado, APK instalada, ruta del código, MariaDB y servidor, SQLite y versión Room, usuario, dispositivo y comprobaciones del catálogo. El commit documental no sustituye al commit del código probado ni demuestra correspondencia entre código y APK.

## CONTRATO C16 Y CAMBIO DE BASE REGISTRADO
Contrato: cells/C16_ADMINISTRACION_AREAS.md, revisión 03.
Registro del cambio de base ya ejecutado manualmente por Patricia: docs/cambios-base-datos/20260930_C16_areas_nombre_corto.md.
No volver a ejecutar el DDL registrado. La preparación de las pruebas no autoriza cambios adicionales de esquema o datos.

## CONTROL DE REVISIONES
- Revisión 02: plan base con diez condiciones, referencia común a ENT-001 y registro de ejecución.
- Revisión 03, 2026-09-30: incorpora las reglas acordadas de unicidad y orden; registra el DDL ejecutado manualmente por Patricia; sustituye el título visible **estructura** por **Referencia**; añade la respuesta remota vacía; define la acreditación de acceso a C16 sin incorporar C24; adopta como base visual los controles ojo–lápiz–X de Participantes, con consulta de detalles no editable para el ojo de C16; y registra como referencia aprobada los símbolos y la sangría actuales del árbol obtenidos del código.

## REFERENCIA DE PRESENTACIÓN DE CONTROLES
- Código base, consultado en modo solo lectura: `ParticipantsAdminPanel()`, en `android/bitacora-android/app/src/main/java/com/cactus/bitacora/ui/admin/ParticipantsAdminPanel.kt`, fila de acciones de cada participante, líneas 97–115 de la versión inspeccionada.
- Disposición: tres `IconButton` consecutivos, en orden ojo, lápiz y X, dentro de una `Row`; cada botón mide `36.dp` y cada `Icon`, `20.dp`.
- Ojo: `R.drawable.ic_participant_view`, azul `Color(0xFF1976D2)`.
- Lápiz: `R.drawable.ic_participant_edit`, naranja `Color(0xFFF9A825)`.
- X: `R.drawable.ic_participant_retire`, roja `Color(0xFFD32F2F)`.
- Recursos vectoriales de referencia: `res/drawable/ic_participant_view.xml`, `ic_participant_edit.xml` e `ic_participant_retire.xml`.
- Esta referencia define iconos, colores, tamaños y disposición. No define el comportamiento funcional de Participantes ni los símbolos o la sangría del árbol de C16.
- La acción `+` para adicionar un área permanece separada de los controles por fila y conserva el comportamiento ya acordado.

## REFERENCIA APROBADA DE SÍMBOLOS Y SANGRÍA
- Patricia confirmó conservar los símbolos y la sangría actuales implementados en `buildAreaTreeRows()` y `AreaTreeRows()` de `android/bitacora-android/app/src/main/java/com/cactus/bitacora/ui/admin/AdminCatalogScreen.kt`.
- `▼`: área con hijas expandida; `▶`: área con hijas contraída; `└─`: área sin hijas.
- Sangría: `10.dp` por nivel mediante `.padding(start = (row.depth * 10).dp)`.
- Las raíces válidas empiezan en nivel 0; cada descendiente incrementa el nivel en 1. La profundidad se calcula por recursión sobre `id_area`/`id_padre`, no con `area.nivel`.
- Solo se muestran las descendientes de áreas expandidas. Los nodos no alcanzables desde una raíz válida se conservan visibles como reserva en nivel 0.
- Los símbolos y `10.dp` proceden del commit `5e9a1e28c198524bd8a8c1fe022df4ecd479ad5c`. La versión inspeccionada parte de `HEAD 9a9f2500f4362a340a04294f54165a7b2406661b`; `buildAreaTreeRows()`, el uso de `row.depth` y la reserva de nodos no alcanzables son cambios locales sin commit.
- Evidencia de la versión local inspeccionada: SHA-256 de `AdminCatalogScreen.kt` `D36CCA5F5FE1A71547C2FF53555EF9F6CE291341E2694F55507D13854F8A3DA1` y el siguiente fragmento literal:

```kotlin
fun visit(node: AreaTreeNodeOut, depth: Int) {
    val children = childrenByParent[node.id_area].orEmpty()
    val expanded = children.isNotEmpty() && node.id_area in expandedAreaIds
    rows += AreaTreeRow(node, depth, children.isNotEmpty(), expanded)
    if (expanded) children.forEach { visit(it, depth + 1) }
}
.padding(start = (row.depth * 10).dp)
when {
    row.hasChildren && row.expanded -> "▼ ${area.descripcion}"
    row.hasChildren -> "▶ ${area.descripcion}"
    else -> "└─ ${area.descripcion}"
}
```

## REGLAS DE VIGENCIA Y EJECUCIÓN
Antes de cada sesión comprobar versión del código, APK, configuración, acceso a Administración y conexión ADB del V2035. No es necesario repetir comprobaciones dentro de la misma sesión si no cambian las condiciones; registrar la referencia a la comprobación vigente.
El acceso a C16 se acredita abriendo Administración → Áreas administrativas y suministrando el identificador de auditoría vigente en el registro de ejecución. No se exige ni se incorpora autenticación C24.
Registrar la fecha y hora real de ejecución en America/Bogota. No reutilizar la fecha de elaboración como fecha de prueba.
Si cambia el entorno, documentar la ficha vigente y su revisión/commit en el registro de ejecución. Conservar la referencia histórica de las ejecuciones anteriores.
La evidencia inicial del catálogo debe corresponder al estado previo de cada condición. Si una condición anterior modificó datos, obtener una nueva evidencia para la siguiente. Conservar la consulta ejecutada, su resultado y fecha/hora.
No sobrescribir las evidencias iniciales de ENT-001 con resultados posteriores; guardar las evidencias de C16 por ejecución y condición en la estructura documental del proyecto.
Todas las precondiciones aplicables deben estar en CUMPLE antes de ejecutar una condición. Si alguna está NO CUMPLE o PENDIENTE, registrar la condición como BLOQUEADA y explicar el motivo.
La conexión a Internet no se exige durante C16.07 ni al inicio de C16.08: en esos momentos se aplica el estado sin conexión previsto por la prueba. La conexión ADB y la conectividad de red son comprobaciones diferentes.
La preparación, modificación en servidor y limpieza de datos deben seguir el procedimiento autorizado del ambiente de desarrollo/pruebas, limitarse a los datos exclusivos de esta ejecución y conservar evidencia.

## 1. DESCRIPCIÓN
Comprobar la consulta, creación, edición y eliminación de áreas administrativas, conservando la integridad de sus relaciones y la concordancia entre MariaDB, SQLite y la pantalla Android.
Verificar que la consulta presente las columnas arbol_de_areas y Referencia, con el orden, símbolos y sangría definidos por la referencia de código aprobada. Referencia presenta el campo técnico nombre_corto; nombre_corto conserva ese nombre en MariaDB, SQLite y API. El orden contractual es preorden jerárquico: cada padre seguido de sus descendientes y los hermanos ordenados alfabéticamente por descripción.
El alcance comprende actualización de pantalla, sincronización, consulta sin conexión, rechazo de datos duplicados y protección de áreas con dependencias.
Las pruebas se ejecutarán en un ambiente identificado de desarrollo o pruebas, utilizando áreas creadas exclusivamente para esta ejecución.
## 2. CONDICIONES DE EJECUCIÓN
### C16.01 — Consulta inicial del árbol de áreas
Descripción de la condición: comprobar la presentación inicial del árbol y los controles disponibles.
#### 1. PRECONDICIONES
- Precondiciones comunes: ENT-001, revisión 01, y commit documental indicados en REFERENCIA COMÚN DEL ENTORNO.
- Comprobación de vigencia: [referencia de verificación, fecha/hora y estado CUMPLE]. Si existe una revisión posterior, identificar la realmente utilizada.
- Registro de ejecución: [ID de ejecución, responsable y fecha/hora America/Bogota].
- Acceso comprobado abriendo Administración → Áreas administrativas; identificador de auditoría vigente: [valor]. V2035 conectado por ADB; conectividad de red según esta condición.
- Evidencia inicial: [query/consulta de referencia, resultado, fecha/hora y ruta de evidencia de esta condición]. Registrar el estado inicial en MariaDB y SQLite pertinente a la operación.
- Precondiciones específicas:
- Catálogo de áreas sincronizado antes de comenzar; comparación de contenido y jerarquía entre MariaDB y SQLite documentada. Query recursivo y referencia de código aprobada identificados y disponibles.
#### 2. ENTRADAS
- Abrir Administración → Áreas administrativas.
- Pulsar el ojo azul de un área y revisar sus detalles.
- Intentar localizar controles de modificación o guardado dentro de la consulta de detalles y cerrar la vista sin alterar el área.
#### 3. PUNTOS DE OBSERVACIÓN
- Encabezados arbol_de_areas y Referencia; no se presenta el título anterior estructura.
- Descripciones, códigos, símbolos, sangrías y secuencia de filas.
- Correspondencia de cada área con su padre.
- Controles por área en orden ojo azul, lápiz naranja y X roja, con botones de `36.dp` e iconos de `20.dp`, conforme a la referencia de Participantes.
- Acción `+` de adición separada de los controles por área.
- Contenido y carácter no editable de la consulta abierta por el ojo.
#### 4. PUNTOS DE CONTROL
- Finalización de la carga del catálogo.
- Si ocurre un error de carga, registrar el mensaje y comprobar que no se presente un árbol incompleto como actualizado.
- Apertura de detalles mediante el ojo y cierre sin operación de escritura.
#### 5. RESULTADOS ESPERADOS
- Cada área aparece una sola vez, bajo su padre y con su `nombre_corto` en la columna Referencia.
- La presentación conserva cada padre seguido de sus descendientes y ordena alfabéticamente por descripción a los hermanos.
- Las raíces no tienen sangría; cada nivel descendiente agrega `10.dp`. Las áreas con hijas usan `▼` cuando están expandidas y `▶` cuando están contraídas; las áreas sin hijas usan `└─`.
- El ID no aparece concatenado con la descripción ni como columna adicional.
- Los controles por área replican los iconos, colores, tamaños, orden y disposición de la referencia de Participantes: ojo azul para consultar, lápiz naranja para editar y X roja para eliminar.
- El ojo muestra los detalles del área sin campos editables, sin acción de guardado y sin modificar MariaDB, SQLite ni el árbol visible.
- El lápiz abre la modificación; la X mantiene confirmación y protección por dependencias.
- La acción `+` permanece separada y permite iniciar la adición acordada.
#### 6. POSTCONDICIONES
- Pantalla abierta y catálogo disponible para las siguientes pruebas.
- Ningún dato modificado.
### C16.02 — Creación de un área administrativa
Descripción de la condición: comprobar el alta y su propagación a pantalla y bases de datos.
#### 1. PRECONDICIONES
- Precondiciones comunes: ENT-001, revisión 01, y commit documental indicados en REFERENCIA COMÚN DEL ENTORNO.
- Comprobación de vigencia: [referencia de verificación, fecha/hora y estado CUMPLE]. Si existe una revisión posterior, identificar la realmente utilizada.
- Registro de ejecución: [ID de ejecución, responsable y fecha/hora America/Bogota].
- Usuario con acceso a Administración comprobado y V2035 conectado por ADB; conectividad de red según esta condición.
- Evidencia inicial: [query/consulta de referencia, resultado, fecha/hora y ruta de evidencia de esta condición]. Registrar el estado inicial en MariaDB y SQLite pertinente a la operación.
- Precondiciones específicas:
- C16.01 aprobada.
- Padre de prueba existente: [ID y descripción].
- No existen la descripción PRUEBA_C16_AREA_A ni la Referencia C16_A.
#### 2. ENTRADAS
- Pulsar +.
- Descripción: PRUEBA_C16_AREA_A.
- Referencia: C16_A.
- Área padre: [ID del padre de prueba].
- Guardar y completar la sincronización.
#### 3. PUNTOS DE OBSERVACIÓN
- Padre mostrado en el formulario.
- Respuesta al guardar.
- Nueva fila en el árbol.
- ID generado y valores almacenados en MariaDB y SQLite.
#### 4. PUNTOS DE CONTROL
- Validación de campos antes de guardar.
- Normalización de espacios externos de nombre_corto antes de validar y enviar.
- Confirmación del guardado en el servidor.
- Finalización de la sincronización local.
#### 5. RESULTADOS ESPERADOS
- Se crea exactamente un área.
- Aparece inmediatamente bajo el padre seleccionado.
- MariaDB conserva los valores ingresados.
- nombre_corto queda almacenado sin espacios externos.
- Después de sincronizar, SQLite contiene el mismo ID y valores.
- Si el guardado falla, se informa el error y no se muestra la operación como completada.
#### 6. POSTCONDICIONES
- Área de prueba creada y sincronizada.
- Su ID queda registrado como AREA_A para las pruebas siguientes.
### C16.03 — Edición de descripción y Referencia
Descripción de la condición: comprobar la modificación de los nombres conservando la identidad del área.
#### 1. PRECONDICIONES
- Precondiciones comunes: ENT-001, revisión 01, y commit documental indicados en REFERENCIA COMÚN DEL ENTORNO.
- Comprobación de vigencia: [referencia de verificación, fecha/hora y estado CUMPLE]. Si existe una revisión posterior, identificar la realmente utilizada.
- Registro de ejecución: [ID de ejecución, responsable y fecha/hora America/Bogota].
- Usuario con acceso a Administración comprobado y V2035 conectado por ADB; conectividad de red según esta condición.
- Evidencia inicial: [query/consulta de referencia, resultado, fecha/hora y ruta de evidencia de esta condición]. Registrar el estado inicial en MariaDB y SQLite pertinente a la operación.
- Precondiciones específicas:
- AREA_A existe y está sincronizada.
- Los valores PRUEBA_C16_AREA_A_EDITADA y C16_A_EDIT no existen en otra área.
#### 2. ENTRADAS
- Pulsar el lápiz de AREA_A.
- Descripción: PRUEBA_C16_AREA_A_EDITADA.
- Referencia: C16_A_EDIT.
- Conservar el padre.
- Guardar y sincronizar.
#### 3. PUNTOS DE OBSERVACIÓN
- Valores iniciales del formulario.
- Actualización inmediata de la fila.
- ID, descripción, Referencia visible y campo técnico `nombre_corto`, y padre en ambas bases.
#### 4. PUNTOS DE CONTROL
- Validación previa al guardado.
- Confirmación del servidor y finalización de la sincronización.
#### 5. RESULTADOS ESPERADOS
- Se modifica la misma área, conservando su ID y padre.
- La pantalla presenta los valores nuevos sin duplicación.
- MariaDB y SQLite contienen los valores modificados después de sincronizar.
- Una validación rechazada no altera los valores anteriores.
#### 6. POSTCONDICIONES
- AREA_A queda con los nombres modificados y disponible para otras pruebas.
### C16.04 — Cambio de área padre
Descripción de la condición: comprobar el traslado de un área a otra rama y el rechazo de relaciones circulares.
#### 1. PRECONDICIONES
- Precondiciones comunes: ENT-001, revisión 01, y commit documental indicados en REFERENCIA COMÚN DEL ENTORNO.
- Comprobación de vigencia: [referencia de verificación, fecha/hora y estado CUMPLE]. Si existe una revisión posterior, identificar la realmente utilizada.
- Registro de ejecución: [ID de ejecución, responsable y fecha/hora America/Bogota].
- Usuario con acceso a Administración comprobado y V2035 conectado por ADB; conectividad de red según esta condición.
- Evidencia inicial: [query/consulta de referencia, resultado, fecha/hora y ruta de evidencia de esta condición]. Registrar el estado inicial en MariaDB y SQLite pertinente a la operación.
- Precondiciones específicas:
- AREA_A sincronizada.
- Segundo padre válido: [ID y descripción], diferente de AREA_A y de sus descendientes.
- Para la prueba negativa, existe una hija de prueba bajo AREA_A.
#### 2. ENTRADAS
- Editar AREA_A y seleccionar el segundo padre válido.
- Guardar y sincronizar.
- Intentar seleccionar AREA_A como su propio padre.
- Intentar seleccionar una descendiente de AREA_A como padre.
#### 3. PUNTOS DE OBSERVACIÓN
- Opciones del selector de padre.
- Ubicación final de AREA_A y de sus descendientes.
- Mensajes de rechazo.
- Valor de nodo_padre en ambas bases.
#### 4. PUNTOS DE CONTROL
- Selección y validación del padre.
- Rechazo o exclusión de opciones que producirían un ciclo.
- Guardado y sincronización del cambio válido.
#### 5. RESULTADOS ESPERADOS
- El cambio válido mueve AREA_A y su subárbol a la rama correspondiente.
- Conserva su ID y no duplica filas.
- Los intentos de crear ciclos se impiden sin modificar los datos.
- Pantalla, MariaDB y SQLite mantienen la misma jerarquía después de sincronizar.
#### 6. POSTCONDICIONES
- AREA_A queda bajo el segundo padre válido.
- No existen ciclos introducidos por la prueba.
### C16.05 — Rechazo de valores duplicados
Descripción de la condición: comprobar la unicidad de descripción y Referencia establecida para C16.
#### 1. PRECONDICIONES
- Precondiciones comunes: ENT-001, revisión 01, y commit documental indicados en REFERENCIA COMÚN DEL ENTORNO.
- Comprobación de vigencia: [referencia de verificación, fecha/hora y estado CUMPLE]. Si existe una revisión posterior, identificar la realmente utilizada.
- Registro de ejecución: [ID de ejecución, responsable y fecha/hora America/Bogota].
- Usuario con acceso a Administración comprobado y V2035 conectado por ADB; conectividad de red según esta condición.
- Evidencia inicial: [query/consulta de referencia, resultado, fecha/hora y ruta de evidencia de esta condición]. Registrar el estado inicial en MariaDB y SQLite pertinente a la operación.
- Precondiciones específicas:
- Dos áreas de prueba existentes, con valores diferentes.
- Contrato C16 revisión 03 disponible: descripción única bajo el mismo padre y nombre_corto obligatorio y único globalmente.
- La comparación de nombre_corto usa utf8mb4_uca1400_ai_ci y, por tanto, ignora mayúsculas y acentos.
#### 2. ENTRADAS
- Intentar crear bajo el mismo padre un área con la descripción de AREA_A y una Referencia nueva.
- Intentar crear bajo un padre diferente un área con la descripción de AREA_A y una Referencia nueva.
- Intentar crear un área con Referencia vacía y repetir usando únicamente espacios.
- Intentar crear un área con una descripción nueva y la Referencia de AREA_A.
- Repetir la Referencia duplicada cambiando únicamente mayúsculas o acentos.
- Editar una segunda área para asignarle la descripción de AREA_A bajo el mismo padre y, por separado, la Referencia de AREA_A.
- Editar AREA_A sin cambiar su propia Referencia y guardar otros cambios válidos.
- Ingresar una Referencia válida con espacios externos y guardar.
#### 3. PUNTOS DE OBSERVACIÓN
- Mensajes de validación.
- Cantidad de filas antes y después.
- Valores de las áreas existentes.
#### 4. PUNTOS DE CONTROL
- Validación de unicidad durante la creación y edición.
- Normalización mediante eliminación de espacios externos antes de validar y guardar.
- Exclusión del área editada al comparar su propia Referencia (`nombre_corto`).
- Rechazo antes de confirmar la modificación.
#### 5. RESULTADOS ESPERADOS
- La descripción repetida bajo el mismo padre se rechaza con un mensaje comprensible.
- La misma descripción bajo un padre diferente se acepta si los demás campos son válidos.
- La Referencia vacía o compuesta solo por espacios se rechaza y el mensaje usa el término **Referencia**.
- La Referencia duplicada globalmente se rechaza aunque cambien mayúsculas o acentos y el mensaje usa el término **Referencia**.
- AREA_A puede editarse conservando su propia Referencia sin recibir un falso duplicado.
- Los espacios externos se eliminan antes de validar y el valor se guarda normalizado.
- No se crean filas ni se modifican las áreas existentes.
- MariaDB y SQLite conservan los datos previos.
#### 6. POSTCONDICIONES
- Catálogo sin duplicados introducidos por la prueba.
- Áreas de prueba originales disponibles.
### C16.06 — Actualización del árbol desde el servidor
Descripción de la condición: comprobar que Actualizar reemplaza los datos anteriores solo cuando recibe un catálogo remoto válido y rechaza una respuesta con cero áreas.
#### 1. PRECONDICIONES
- Precondiciones comunes: ENT-001, revisión 01, y commit documental indicados en REFERENCIA COMÚN DEL ENTORNO.
- Comprobación de vigencia: [referencia de verificación, fecha/hora y estado CUMPLE]. Si existe una revisión posterior, identificar la realmente utilizada.
- Registro de ejecución: [ID de ejecución, responsable y fecha/hora America/Bogota].
- Usuario con acceso a Administración comprobado y V2035 conectado por ADB; conectividad de red según esta condición.
- Evidencia inicial: [query/consulta de referencia, resultado, fecha/hora y ruta de evidencia de esta condición]. Registrar el estado inicial en MariaDB y SQLite pertinente a la operación.
- Precondiciones específicas:
- Celular conectado y pantalla abierta.
- AREA_A visible con sus valores actuales.
- El catálogo local previo y el árbol visible quedan registrados para compararlos después del caso de respuesta vacía.
#### 2. ENTRADAS
- Mediante el procedimiento autorizado del servidor, cambiar la descripción de AREA_A a PRUEBA_C16_ACTUALIZADA.
- Registrar el resultado del query de referencia.
- Pulsar Actualizar en el celular.
- Mediante un mecanismo de prueba autorizado que no elimine datos del servidor, hacer que una actualización separada reciba una respuesta remota con cero áreas.
#### 3. PUNTOS DE OBSERVACIÓN
- Estado de carga.
- Cambio de la descripción en pantalla.
- Conservación de orden, sangría y estructura.
- Valores finales en SQLite.
- Mensaje y árbol visible después de la respuesta remota con cero áreas.
#### 4. PUNTOS DE CONTROL
- Inicio y finalización de la actualización.
- Si falla la conexión, observar el tratamiento del error y la conservación del catálogo local.
- Comprobar que la respuesta con cero áreas se valida antes de reemplazar SQLite o el árbol visible.
#### 5. RESULTADOS ESPERADOS
- Al completar la actualización, la pantalla y SQLite muestran el cambio del servidor.
- No es necesario cerrar la pantalla ni reiniciar la app.
- No aparecen duplicados.
- Ante un fallo, no se informa actualización exitosa ni se borra el catálogo local válido.
- Una respuesta remota con cero áreas se rechaza con un error comprensible, conserva sin cambios el catálogo anterior de SQLite y el árbol visible, y no anuncia una actualización exitosa.
#### 6. POSTCONDICIONES
- Catálogo actualizado y listo para la prueba sin conexión.
### C16.07 — Consulta del árbol sin conexión
Descripción de la condición: comprobar la disponibilidad del último catálogo sincronizado.
#### 1. PRECONDICIONES
- Precondiciones comunes: ENT-001, revisión 01, y commit documental indicados en REFERENCIA COMÚN DEL ENTORNO.
- Comprobación de vigencia: [referencia de verificación, fecha/hora y estado CUMPLE]. Si existe una revisión posterior, identificar la realmente utilizada.
- Registro de ejecución: [ID de ejecución, responsable y fecha/hora America/Bogota].
- Usuario con acceso a Administración comprobado y V2035 conectado por ADB; conectividad de red según esta condición.
- Evidencia inicial: [query/consulta de referencia, resultado, fecha/hora y ruta de evidencia de esta condición]. Registrar el estado inicial en MariaDB y SQLite pertinente a la operación.
- Precondiciones específicas:
- C16.06 aprobada.
- Último árbol sincronizado registrado como referencia.
#### 2. ENTRADAS
- Desactivar Wi-Fi y datos móviles.
- Cerrar y volver a abrir la aplicación.
- Abrir Áreas administrativas.
#### 3. PUNTOS DE OBSERVACIÓN
- Carga desde SQLite.
- Filas, orden, jerarquía y estructura.
- Mensajes mostrados al usuario.
#### 4. PUNTOS DE CONTROL
- Detección de ausencia de conexión.
- Continuación de la consulta mediante el catálogo local.
#### 5. RESULTADOS ESPERADOS
- Se presenta el último árbol sincronizado con sus dos columnas.
- La falta de conexión no impide la consulta.
- No aparece un error técnico de resolución de host como resultado de abrir la consulta.
- No se muestra el catálogo como recién actualizado desde el servidor.
#### 6. POSTCONDICIONES
- Catálogo local intacto.
- Celular permanece sin conexión para C16.08.
### C16.08 — Reconexión y sincronización de cambios
Descripción de la condición: comprobar la incorporación de cambios realizados mientras el celular estaba desconectado.
#### 1. PRECONDICIONES
- Precondiciones comunes: ENT-001, revisión 01, y commit documental indicados en REFERENCIA COMÚN DEL ENTORNO.
- Comprobación de vigencia: [referencia de verificación, fecha/hora y estado CUMPLE]. Si existe una revisión posterior, identificar la realmente utilizada.
- Registro de ejecución: [ID de ejecución, responsable y fecha/hora America/Bogota].
- Usuario con acceso a Administración comprobado y V2035 conectado por ADB; conectividad de red según esta condición.
- Evidencia inicial: [query/consulta de referencia, resultado, fecha/hora y ruta de evidencia de esta condición]. Registrar el estado inicial en MariaDB y SQLite pertinente a la operación.
- Precondiciones específicas:
- C16.07 aprobada y celular desconectado.
- AREA_A disponible para modificar en el servidor.
#### 2. ENTRADAS
- Cambiar en el servidor la Referencia (`nombre_corto`) de AREA_A a C16_RECON.
- Reconectar el celular.
- Ejecutar la sincronización prevista por C16.
- Consultar el árbol.
#### 3. PUNTOS DE OBSERVACIÓN
- Inicio y finalización de la sincronización.
- Valor de Referencia en pantalla y del campo técnico `nombre_corto` en SQLite.
- Orden, símbolos y niveles respecto del query.
#### 4. PUNTOS DE CONTROL
- Recuperación de conectividad.
- Confirmación de sincronización completada.
- Tratamiento de una sincronización interrumpida, si ocurre.
#### 5. RESULTADOS ESPERADOS
- El cambio se incorpora una sola vez.
- Pantalla y SQLite coinciden con MariaDB.
- El árbol conserva cada padre seguido de sus descendientes y los hermanos ordenados alfabéticamente por descripción.
- Una interrupción no se presenta como sincronización completada.
#### 6. POSTCONDICIONES
- Celular conectado y catálogo sincronizado.
### C16.09 — Protección de eliminación por dependencias
Descripción de la condición: comprobar el rechazo de borrados que afectarían relaciones existentes.
#### 1. PRECONDICIONES
- Precondiciones comunes: ENT-001, revisión 01, y commit documental indicados en REFERENCIA COMÚN DEL ENTORNO.
- Comprobación de vigencia: [referencia de verificación, fecha/hora y estado CUMPLE]. Si existe una revisión posterior, identificar la realmente utilizada.
- Registro de ejecución: [ID de ejecución, responsable y fecha/hora America/Bogota].
- Usuario con acceso a Administración comprobado y V2035 conectado por ADB; conectividad de red según esta condición.
- Evidencia inicial: [query/consulta de referencia, resultado, fecha/hora y ruta de evidencia de esta condición]. Registrar el estado inicial en MariaDB y SQLite pertinente a la operación.
- Precondiciones específicas:
- Área de prueba con una hija.
- Área de prueba con una relación en empleado_area.
- Registrar si la asignación está activa o inactiva. Este plan conserva la protección existente también para relaciones históricas.
#### 2. ENTRADAS
- Pulsar eliminar y confirmar para el área con hija.
- Repetir para el área con relación en empleado_area.
#### 3. PUNTOS DE OBSERVACIÓN
- Mensaje que explica la dependencia.
- Permanencia del área, hijas y asignaciones.
- Estado de pantalla y ambas bases.
#### 4. PUNTOS DE CONTROL
- Confirmación del borrado.
- Validación de dependencias y rechazo de la operación.
#### 5. RESULTADOS ESPERADOS
- Ambos borrados se rechazan.
- Se informa el motivo.
- No se elimina ni modifica el área o sus relaciones en MariaDB o SQLite.
- El árbol conserva su integridad.
#### 6. POSTCONDICIONES
- Áreas y dependencias de prueba permanecen intactas.
- Se preparan únicamente las áreas de prueba sin dependencias para C16.10.
### C16.10 — Eliminación permitida y permanencia del resultado
Descripción de la condición: comprobar el borrado de un área sin dependencias y que no reaparezca.
#### 1. PRECONDICIONES
- Precondiciones comunes: ENT-001, revisión 01, y commit documental indicados en REFERENCIA COMÚN DEL ENTORNO.
- Comprobación de vigencia: [referencia de verificación, fecha/hora y estado CUMPLE]. Si existe una revisión posterior, identificar la realmente utilizada.
- Registro de ejecución: [ID de ejecución, responsable y fecha/hora America/Bogota].
- Usuario con acceso a Administración comprobado y V2035 conectado por ADB; conectividad de red según esta condición.
- Evidencia inicial: [query/consulta de referencia, resultado, fecha/hora y ruta de evidencia de esta condición]. Registrar el estado inicial en MariaDB y SQLite pertinente a la operación.
- Precondiciones específicas:
- Área de prueba sin hijas ni relaciones dependientes.
- ID registrado y presencia comprobada en ambas bases.
#### 2. ENTRADAS
- Pulsar eliminar y cancelar la confirmación.
- Comprobar que el área continúa existiendo.
- Pulsar eliminar nuevamente y confirmar.
- Sincronizar.
- Reiniciar la app y consultar.
- Desconectar el celular y repetir la consulta.
#### 3. PUNTOS DE OBSERVACIÓN
- Resultado de cancelar y de confirmar.
- Ausencia del ID eliminado en ambas bases.
- Árbol después de sincronizar, reiniciar y desconectar.
#### 4. PUNTOS DE CONTROL
- Cancelación: interrumpe el borrado.
- Confirmación: permite continuar.
- Finalización del borrado y sincronización.
#### 5. RESULTADOS ESPERADOS
- Cancelar conserva el área.
- Confirmar elimina el área de MariaDB y de la pantalla.
- Después de sincronizar, el área desaparece de SQLite.
- No reaparece al reiniciar ni al consultar sin conexión.
- Las demás áreas conservan sus datos y relaciones.
#### 6. POSTCONDICIONES
- Área eliminada y catálogo consistente.
- Se retiran los demás datos exclusivos de prueba mediante el procedimiento autorizado.
- Se restablece la conexión y se verifica el árbol final.
- Quedan registradas las evidencias y el resultado de cada condición.
Criterio de aceptación del plan: todas las condiciones deben quedar aprobadas con evidencia vinculada a la versión probada. Una condición fallida o pendiente impide declarar C16 validada. Este documento define las pruebas; no constituye evidencia de que ya se hayan ejecutado.



## 3. REGISTRO DE EJECUCIÓN Y RESULTADOS
Completar un registro por cada condición C16.01 a C16.10:
- ID de ejecución y condición: [valor].
- Revisión/commit del plan utilizado: [valor; completar después de registrarlo en Git].
- Revisión contractual C16 utilizada: [revisión 03 o posterior realmente utilizada].
- Ficha ENT-001 utilizada, revisión y commit documental: [valor].
- Commit del código y versión APK probados: [valores de la ficha vigente].
- Fecha/hora de inicio y fin, America/Bogota: [valores].
- Responsable: [identificador].
- Precondiciones comunes y específicas: [CUMPLE / NO CUMPLE / PENDIENTE, evidencia].
- Datos utilizados: [IDs de padres, AREA_A, hija, segunda área y demás datos aplicables].
- Evidencias iniciales y finales: [consultas, resultados, capturas y rutas].
- Resultado obtenido: [descripción de lo observado].
- Comparación con los resultados esperados: [resultado por cada punto].
- Estado de la condición: [APROBADA / FALLIDA / BLOQUEADA / NO EJECUTADA].
- Incidencias y desviaciones: [referencia y descripción].
- Postcondiciones y limpieza: [resultado y evidencia; conservar las dependencias históricas protegidas].

## 4. CONTROL DE CAMBIO Y CIERRE
Conservar la revisión anterior del plan y registrar esta revisión en Git mediante un commit documental que incluya únicamente los archivos pertinentes.
La modificación documental no autoriza cambios de código ni acredita instalación o despliegue.
Registrar el commit de este plan después de crearlo; no confundirlo con el commit de ENT-001 o del código probado.
C16 solo puede declararse validada cuando las diez condiciones estén APROBADAS, con evidencias y versión probada identificadas, y Patricia registre la aprobación del cierre.
