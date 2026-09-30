# C16 — Administración de áreas

**Revisión contractual:** 03
**Estado:** DEFINICIÓN

## Control de revisión

- Revisión 03, 2026-09-30: incorpora las reglas de unicidad y orden del árbol,
  la presentación de `nombre_corto` como **Referencia**, el rechazo seguro de
  respuestas remotas sin áreas, la forma de acreditar el acceso a C16 y la
  referencia visual de los controles por área basada en Participantes.

## Objetivo

Consultar y administrar el árbol de `areas_administrativas` desde
Administrador → Administrar maestros, manteniendo concordancia entre MariaDB,
SQLite/Room y la presentación Android.

## Contrato funcional acordado

### Identidad y jerarquía

- `id_Area_Administrativa` identifica el área y no cambia al editarla.
- `nodo_padre` define la relación jerárquica.
- Un área no puede ser su propio padre ni depender de una descendiente.
- Cada padre se presenta seguido por todos sus descendientes.
- Los hermanos se ordenan alfabéticamente por `descripcion`.

### Descripción

- `descripcion` es obligatoria.
- La descripción debe ser única únicamente entre áreas que tienen el mismo
  padre.
- La misma descripción puede existir bajo padres diferentes.

### Referencia (`nombre_corto`)

- `nombre_corto` es obligatorio y tiene longitud máxima de 25 caracteres.
- La aplicación quita espacios externos antes de validar y guardar.
- Después de quitar espacios externos, el valor no puede quedar vacío.
- `nombre_corto` es único globalmente en el árbol.
- La comparación usa la collation existente `utf8mb4_uca1400_ai_ci`, por lo
  cual la unicidad no distingue mayúsculas ni acentos.
- Al editar un área, conservar su propio `nombre_corto` no constituye un
  duplicado.

La estructura física que respalda este contrato fue aplicada manualmente por
Patricia y está registrada en
`docs/cambios-base-datos/20260930_C16_areas_nombre_corto.md`. Ese DDL ya fue
ejecutado y no debe repetirse.

## Consulta y presentación

- La consulta presenta las columnas `arbol_de_areas` y **Referencia**; el título
  anterior **estructura** queda sustituido por **Referencia**.
- `arbol_de_areas` muestra la descripción, símbolos y sangría jerárquica sin
  concatenar el ID.
- **Referencia** muestra el valor técnico `nombre_corto`.
- Los formularios de creación y edición usan **Referencia** como etiqueta y
  los mensajes de validación usan ese mismo término.
- `nombre_corto` se conserva sin cambios como nombre técnico en MariaDB,
  SQLite/Room y API.

### Símbolos y sangría aprobados del árbol

Patricia confirmó conservar la presentación actual de C16, implementada en
`android/bitacora-android/app/src/main/java/com/cactus/bitacora/ui/admin/AdminCatalogScreen.kt`,
funciones `buildAreaTreeRows()` y `AreaTreeRows()`:

- `▼` identifica un área con hijas que está expandida.
- `▶` identifica un área con hijas que está contraída.
- `└─` identifica un área sin hijas.
- La sangría izquierda es `10.dp` por nivel: `row.depth * 10.dp`.
- Las raíces válidas empiezan en profundidad 0. Cada descendiente visitada por
  la recursión incrementa la profundidad en 1.
- La profundidad se obtiene de la relación `id_area`/`id_padre`; no se toma de
  `area.nivel`.
- Solo se agregan a las filas visibles las descendientes de un área expandida.
- Un nodo no alcanzable desde una raíz válida se muestra como reserva en
  profundidad 0, sin ocultarlo ni inventar una relación jerárquica.

Evidencia literal del fragmento aprobado:

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

Los símbolos y la medida de `10.dp` proceden del commit
`5e9a1e28c198524bd8a8c1fe022df4ecd479ad5c`. La versión inspeccionada parte de
`HEAD 9a9f2500f4362a340a04294f54165a7b2406661b`, pero el cálculo actual mediante
`row.depth`, `buildAreaTreeRows()` y la regla de reserva son cambios locales sin
commit. Como evidencia de esa versión local se registra el SHA-256 del archivo:
`D36CCA5F5FE1A71547C2FF53555EF9F6CE291341E2694F55507D13854F8A3DA1`.
- El orden es preorden jerárquico: cada padre, seguido de sus descendientes;
  dentro de cada nivel, los hermanos se ordenan alfabéticamente por
  `descripcion`.
### Controles por área

- Cada fila dispone los controles en el mismo orden horizontal usado por el
  módulo Participantes: ojo, lápiz y X.
- El ojo azul abre los detalles del área en modo de solo consulta; no presenta
  campos editables ni permite guardar modificaciones.
- El lápiz naranja abre la edición o modificación del área.
- La X roja inicia la eliminación y conserva tanto la confirmación explícita
  como todas las protecciones por dependencias definidas por C16.
- La acción `+` para adicionar un área permanece separada de los controles de
  cada fila y conserva el comportamiento de adición ya acordado.

La base de presentación se encuentra en
`android/bitacora-android/app/src/main/java/com/cactus/bitacora/ui/admin/ParticipantsAdminPanel.kt`,
en `ParticipantsAdminPanel()` y su `Row` de acciones de cada participante
(líneas 97–115 en la versión inspeccionada): `IconButton` de `36.dp`, con
`Icon` de `20.dp`, dispuestos como ojo, lápiz y X. Se reutilizan como referencia
los recursos `R.drawable.ic_participant_view`,
`R.drawable.ic_participant_edit` y `R.drawable.ic_participant_retire`, y sus
colores respectivos `Color(0xFF1976D2)`, `Color(0xFFF9A825)` y
`Color(0xFFD32F2F)`. Los vectores están definidos en
`res/drawable/ic_participant_view.xml`, `ic_participant_edit.xml` e
`ic_participant_retire.xml`.

Esta referencia define iconos, colores, tamaños y disposición de los controles;
los símbolos y la sangría se rigen por la referencia de código aprobada en la
sección anterior. La referencia tampoco modifica el módulo Participantes ni
adopta su comportamiento funcional para el ojo.

## Persistencia y sincronización

- Las escrituras se confirman primero en el servidor y solo se presentan como
  completadas después de una respuesta exitosa.
- El catálogo confirmado se conserva en `areas_administrativas_locales` para
  consulta sin conexión.
- Una actualización fallida o estructuralmente inválida no debe borrar ni
  reemplazar el último catálogo local válido.
- Una respuesta remota con cero áreas es inválida para C16: se rechaza, no
  reemplaza `areas_administrativas_locales`, conserva el árbol ya visible,
  muestra un error comprensible y no anuncia una actualización exitosa.
- La repetición de una actualización no crea áreas duplicadas.

## Eliminación

- Se rechaza eliminar un área con hijas.
- Se rechaza eliminar un área referenciada por `empleado_area`, incluidas sus
  relaciones históricas.
- Se conservan las protecciones de referencias de observaciones y evidencias.
- Un área sin dependencias puede eliminarse con confirmación explícita.

## Límites

- El acceso se acredita abriendo Administración → Áreas administrativas y
  suministrando el identificador de auditoría vigente en el registro de la
  ejecución.
- C16 no incorpora autenticación C24 ni redefine la autenticación
  administrativa.
- C16 no modifica los contratos funcionales de empleado–área, jornadas,
  bitácoras, evidencias ni reconocimiento facial.
- La validación de C16 requiere ejecutar las diez condiciones del plan de
  pruebas revisión 03; la inspección del código no equivale a validación.

## Plan de pruebas

`docs/propuestas/PLAN_C16_Pruebas_Administracion_Areas.md`, revisión 03, con
referencia común a ENT-001.
