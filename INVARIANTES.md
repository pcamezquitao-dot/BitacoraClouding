# INVARIANTES DE DESARROLLO DE BITÁCORA

**Versión:** 2.0
**Aprobadas por Patricia Amézquita:** 18 de septiembre de 2026
**Estado:** VIGENTES
**Autoridad:** Regla metodológica superior para todo trabajo de Codex en Bitácora.

## 1. Propósito y visión

Permitir la recuperación, continuidad y evolución de Bitácora mediante correcciones y requerimientos nuevos, desarrollados desde la visión dimensional de las reglas del negocio, con cambios pequeños, demostrables, trazables y reversibles.

Estas invariantes protegen lo validado, pero no congelan la evolución del proyecto. Un requerimiento nuevo puede desarrollarse después de definir su modelo de negocio, impacto, célula, contratos, alcance y pruebas, y de recibir autorización expresa de Patricia.

Bitácora no puede abandonarse, sustituirse ni reescribirse desde cero por inferencia de Codex. Una solución temporal, como Google Forms o Google Sheets, pertenece a un proyecto separado y no modifica el estado, los contratos ni el alcance de Bitácora.

## 2. Principios no abandonables

1. La arquitectura parte de la visión dimensional de las reglas del negocio.
2. Cada registro debe tener significado, granularidad, fuente y trazabilidad definidos.
3. MariaDB es la fuente central de los datos de negocio; Room/SQLite es persistencia local y debe converger mediante contratos de sincronización explícitos.
4. Debe preservarse la operación online y offline autorizada.
5. Los registros de eventos deben conservar identidad e idempotencia cuando aplique.
6. Las funciones, contratos y datos validados se protegen contra regresiones.
7. El desarrollo se divide en células con propiedad, alcance, contratos y pruebas definidos.
8. Toda evolución debe ser compatible o declarar y autorizar expresamente la ruptura de compatibilidad.
9. La evidencia técnica no reemplaza la validación funcional de Patricia.
10. Git debe conservar puntos de retorno verificables y no sobrescribir versiones validadas.

## 3. Autoridad documental

`INVARIANTES.md` es la única autoridad metodológica superior. `AGENTS.md` solamente obliga a localizarlo, leerlo y aplicarlo.

Las fichas de células, inventarios, documentos técnicos, conversaciones y requerimientos están subordinados a estas invariantes.

Un documento histórico no se presume vigente. Su información debe contrastarse con la rama, commit, tag, pruebas y evidencias aplicables.

Codex no puede modificar `INVARIANTES.md` por iniciativa propia ni como parte incidental de una intervención funcional.

## 4. Unidad de trabajo

Toda intervención debe ser una de estas clases:

1. `INTERVENCIÓN_DE_CÉLULA`: afecta una sola célula.
2. `INTERVENCIÓN_TRANSVERSAL`: afecta varias células o componentes compartidos, todos enumerados y autorizados expresamente por Patricia.
3. `INTERVENCIÓN_DOCUMENTAL`: modifica únicamente documentación expresamente autorizada.

Una intervención transversal no autoriza modificar cualquier componente relacionado. Su alcance se mantiene en una lista cerrada de células, contratos y archivos.

Todas las células y archivos no incluidos quedan congelados y en modo solo lectura.

## 5. Control previo obligatorio

Antes de modificar cualquier archivo, Codex debe presentar:

```text
CONTROL_PREVIO
INVARIANTES_LEÍDAS: SÍ
VERSIÓN:
TIPO_DE_INTERVENCIÓN:
OBJETIVO_AUTORIZADO:
CÉLULA_PRINCIPAL:
CÉLULAS_TRANSVERSALES_AUTORIZADAS: NINGUNA
RAMA_ACTUAL:
COMMIT_INICIAL:
ESTADO_GIT:
BASE_ESTABLE_PROPUESTA:
EVIDENCIA_DE_ESTABILIDAD:
BASE_ESTABLE_APROBADA_POR_PATRICIA: SÍ/NO
RAMA_O_WORKTREE_DE_TRABAJO:
ARCHIVOS_YA_MODIFICADOS:
ARCHIVOS_MODIFICABLES:
ARCHIVOS_COMPARTIDOS_AFECTADOS: NINGUNO
CONTRATOS_AFECTADOS: NINGUNO
DATOS_O_ESQUEMAS_AFECTADOS: NINGUNO
PRUEBA_FOCALIZADA_ACORDADA:
PRUEBAS_DE_CONTRATO_ACORDADAS: NINGUNA
PRUEBAS_DE_NO_REGRESIÓN_ACORDADAS: NINGUNA
CICLO_TÉCNICO_AUTORIZADO:
EXCEPCIONES_AUTORIZADAS: NINGUNA
```

`ARCHIVOS_MODIFICABLES` debe contener rutas exactas. Expresiones como “archivos relacionados”, “los anteriores” o “los necesarios” no constituyen una lista cerrada.

Si una ficha histórica no contiene rutas exactas, Codex propondrá la lista y esperará aprobación de Patricia antes de escribir.

Si falta información obligatoria, existe una contradicción o no puede demostrarse una base estable, el estado será `BLOQUEADO_ANTES_DE_ESCRIBIR`.

## 6. Base estable y protección de lo validado

Ninguna intervención comienza desde un estado desconocido. Debe identificarse el commit o tag que Patricia reconoce como base funcional aplicable.

- Una rama reciente no se presume estable.
- Un commit existente no se presume validado.
- Una compilación exitosa no demuestra funcionamiento.
- Una versión instalada no demuestra que el código actual corresponda a ella.
- No se mezclan ramas de recuperación sin comparar contratos y cambios.
- Un tag protegido no se mueve ni reemplaza.
- Si la base contiene una falla que impide la intervención y su origen no está demostrado, se detiene antes de escribir.

Toda funcionalidad previamente validada se presume protegida.

## 7. Requerimiento autorizado

Antes de desarrollar se presentará:

```text
CONTRATO_DE_TRABAJO
SOLICITUD_LITERAL_DE_PATRICIA:
RESULTADO_ESPERADO:
EXCLUSIONES:
ARCHIVOS_PERMITIDOS:
PRUEBA_MÍNIMA:
CRITERIO_DE_ÉXITO:
ELEMENTOS_PROPUESTOS_POR_CODEX: NINGUNO
```

Solamente constituye requerimiento aquello que Patricia haya solicitado o aprobado expresamente.

- El silencio no significa aprobación.
- Una recomendación es `PROPUESTA_NO_AUTORIZADA`.
- Corregir un error no autoriza cambiar el comportamiento funcional.
- Una ambigüedad con resultados funcionales diferentes debe resolverse antes de implementar.
- Los requerimientos nuevos no se incorporan aprovechando otro cambio.
- No se agregan autenticaciones, usuarios, PIN, permisos, menús ni controles no solicitados.

## 8. Incorporación de requerimientos nuevos

Bitácora puede recibir nuevas funcionalidades, procesos, hechos, dimensiones, medidas, consultas, pantallas, APIs y automatizaciones.

Un requerimiento nuevo no está prohibido. Primero debe convertirse en un requerimiento definido, dimensionalmente coherente, ubicado arquitectónicamente y autorizado.

### 8.1 Registro inicial

```text
NUEVO_REQUERIMIENTO
SOLICITUD_LITERAL_DE_PATRICIA:
NECESIDAD_DE_NEGOCIO:
USUARIOS:
PROCESO:
RESULTADO_ESPERADO:
PRIORIDAD:
URGENCIA:
RESTRICCIONES:
ESTADO: DEFINICIÓN
```

En esta etapa Codex puede analizar, preguntar, documentar y proponer alternativas, pero no modificar código, datos ni esquemas.

### 8.2 Definición dimensional

Antes de desarrollar un requerimiento nuevo se definirá, cuando aplique:

```text
DEFINICIÓN_DIMENSIONAL
PROCESO_DE_NEGOCIO:
HECHO_O_EVENTO:
GRANULARIDAD:
MEDIDAS:
DIMENSIONES:
DIMENSIÓN_TIEMPO:
IDENTIFICADORES:
RELACIONES:
FUENTE_OFICIAL:
REGLAS_DE_NEGOCIO:
VIGENCIAS:
TRATAMIENTO_OFFLINE:
SINCRONIZACIÓN:
TRAZABILIDAD:
```

No se crearán tablas, campos o estructuras únicamente para reproducir una pantalla o formulario sin establecer qué hecho, dimensión, relación o regla de negocio representan.

Si la definición dimensional no aplica, se indicará `NO_APLICA` y se justificará.

### 8.3 Ubicación en células

Se determinará si el requerimiento:

1. pertenece completamente a una célula existente;
2. requiere una nueva versión de una célula existente;
3. requiere una nueva célula;
4. constituye una intervención transversal.

No se incorporará silenciosamente a una célula por conveniencia técnica.

Para ampliar una célula existente se presentará:

```text
AMPLIACIÓN_DE_CÉLULA
CÉLULA:
VERSIÓN_ACTUAL:
NUEVA_VERSIÓN_PROPUESTA:
CONTRATO_ACTUAL:
NUEVO_COMPORTAMIENTO:
COMPATIBILIDAD:
FUNCIONES_PROTEGIDAS:
```

Para crear una célula se presentará:

```text
PROPUESTA_DE_NUEVA_CÉLULA
CÓDIGO_PROPUESTO:
NOMBRE:
OBJETIVO:
PROPIETARIA_DE:
CONTRATOS_PUBLICADOS:
CONTRATOS_CONSUMIDOS:
DATOS_Y_TABLAS:
ARCHIVOS_PREVISTOS:
USUARIOS:
PROCESOS:
DEPENDENCIAS:
PRUEBA_MÍNIMA:
RIESGOS:
```

La numeración y creación de la nueva célula requieren aprobación de Patricia.

### 8.4 Análisis de impacto

Antes de autorizar el desarrollo se identificará el impacto sobre:

- reglas dimensionales;
- células existentes;
- contratos y consumidores;
- tablas y esquemas;
- MariaDB;
- API y modelos;
- Room/SQLite;
- sincronización online/offline;
- navegación y pantallas;
- datos históricos;
- funcionalidades previamente validadas.

El análisis debe distinguir entre componentes que se consultarán y componentes que deberán modificarse.

### 8.5 Contrato de nuevo desarrollo

El requerimiento pasa de `DEFINICIÓN` a `AUTORIZADA` cuando Patricia aprueba:

```text
CONTRATO_DE_NUEVO_DESARROLLO
REQUERIMIENTO:
CÉLULA_O_INTERVENCIÓN:
DEFINICIÓN_DIMENSIONAL:
ALCANCE:
EXCLUSIONES:
ARCHIVOS_MODIFICABLES:
DATOS_O_ESQUEMAS:
CONTRATOS:
PRUEBA_FOCALIZADA:
PRUEBAS_DE_CONTRATO:
PRUEBAS_DE_NO_REGRESIÓN:
CRITERIO_DE_ÉXITO:
BASE_ESTABLE:
CICLO_TÉCNICO_AUTORIZADO:
DECISIÓN_DE_PATRICIA:
```

Después de la aprobación, Codex implementará dentro del alcance y ciclo autorizados sin volver a preguntar por decisiones ya aprobadas.

### 8.6 Protección de los desarrollos nuevos

Un desarrollo nuevo debe:

- conservar compatibilidad con contratos vigentes, salvo cambio aprobado;
- mantener separadas las reglas de negocio y la presentación;
- preservar la granularidad y el significado de los datos;
- evitar duplicar dimensiones, catálogos o fuentes oficiales;
- definir el comportamiento online y offline;
- incluir trazabilidad e idempotencia cuando registre eventos;
- identificar la reversión;
- producir evidencia técnica y funcional;
- actualizar la ficha de la célula y el inventario oficial después de ser aprobado y validado.

## 9. Soluciones temporales

Una solución rápida puede aprobarse como proyecto separado mediante:

```text
SOLUCIÓN_TEMPORAL
NECESIDAD_URGENTE:
ALCANCE:
DURACIÓN_ESPERADA:
DATOS_CAPTURADOS:
FUENTE_OFICIAL:
RELACIÓN_CON_BITÁCORA:
MIGRACIÓN_FUTURA:
RESPONSABLE:
```

La solución temporal no sustituye Bitácora, no modifica automáticamente sus contratos y no se integra sin un requerimiento posterior autorizado.

## 10. Alcance y archivos

Codex puede leer la intervención autorizada, consultar dependencias directas indispensables, modificar únicamente las rutas aprobadas, corregir errores técnicos introducidos por su cambio y ejecutar únicamente las etapas autorizadas.

La lectura de una dependencia no autoriza modificarla.

Si aparece un archivo necesario que no está en la lista, Codex se detiene antes de editarlo y presenta:

```text
IMPACTO_TRANSVERSAL
CÉLULA_O_ARCHIVO_AFECTADO:
MOTIVO_INDISPENSABLE:
CONTRATO_AFECTADO:
CONSUMIDORES_IDENTIFICADOS:
RIESGO:
ALTERNATIVA_COMPATIBLE:
PRUEBA_ADICIONAL_NECESARIA:
DECISIÓN_DE_PATRICIA:
```

## 11. Componentes compartidos

Un componente consumido por varias células se considera de solo lectura hasta que su modificación sea autorizada transversalmente.

Esto incluye, cuando corresponda, el arranque y configuración común del backend, conexión a bases, modelos compartidos, `app/main.py`, navegación y shell Android, `MainActivity.kt`, base Room y migraciones, repositorios de catálogos, sincronizadores, workers y contratos API compartidos.

Esta enumeración identifica riesgos; no autoriza modificaciones.

## 12. Datos, esquemas y credenciales

Las consultas de solo lectura necesarias para el diagnóstico están permitidas.

Las pruebas unitarias pueden utilizar bases temporales, simuladas o desechables únicamente cuando no contienen datos reales, se crean dentro del entorno de pruebas y no afectan MariaDB, SQLite del dispositivo ni archivos persistentes del usuario.

Toda escritura sobre datos reales, bases persistentes, MariaDB, SQLite/Room del dispositivo o esquemas requiere autorización expresa mediante:

```text
OPERACIÓN_DE_DATOS_PROPUESTA
BASE_Y_TABLAS:
AMBIENTE:
DATOS_REALES_O_PRUEBA:
REGISTROS_ESPERADOS:
SELECT_PREVIO:
RESPALDO:
REVERSIÓN:
RIESGOS:
SCRIPT_EXACTO:
DECISIÓN_DE_PATRICIA:
```

Antes de `UPDATE` o `DELETE` se ejecuta el `SELECT` equivalente. Se detiene si la cantidad real difiere de la prevista. Autorizar MariaDB no autoriza SQLite/Room ni viceversa. Nunca se exponen ni guardan credenciales o secretos.

## 13. Control de versiones y aislamiento

Toda intervención funcional se realiza en una rama o worktree aislado desde la base estable aprobada.

- Se preservan cambios ajenos.
- No se usa reset destructivo.
- No se descartan cambios sin autorización.
- No se borran ramas, tags, datos ni archivos para limpiar el entorno.
- No se mezclan cambios de distintas intervenciones.
- Los archivos no autorizados permanecen intactos.

Después de modificar debe ejecutarse:

```text
git diff --name-only
git diff --stat
```

Cada archivo mostrado debe pertenecer a `ARCHIVOS_MODIFICABLES`. Si aparece un archivo externo, Codex se detiene y no lo incluye, corrige, revierte ni descarta automáticamente.

## 14. Ciclo técnico autorizado

Toda autorización debe indicar cada etapa:

```text
CICLO_TÉCNICO_AUTORIZADO
DIAGNOSTICAR: SÍ/NO
MODIFICAR: SÍ/NO
PROBAR_FOCALMENTE: SÍ/NO
PROBAR_CONTRATOS: lista o NINGUNA
PROBAR_NO_REGRESIÓN: lista o NINGUNA
COMPILAR: SÍ/NO
INSTALAR: SÍ/NO
DISPOSITIVO:
CONSERVAR_DATOS: OBLIGATORIO
CREAR_COMMIT_TÉCNICO: SÍ/NO
TAG_ESTABLE: SÍ/NO
PUSH: SÍ/NO
INTEGRAR: SÍ/NO
DESPLEGAR_DESARROLLO: SÍ/NO
DESPLEGAR_PRODUCCIÓN: SÍ/NO
```

Ninguna etapa se infiere de otra.

Codex puede resolver autónomamente errores rutinarios propios del cambio —sintaxis, imports, tipos y compilación— únicamente dentro de los archivos autorizados.

## 15. Pruebas

Se distinguen:

1. `PRUEBA_FOCALIZADA`: demuestra el comportamiento solicitado.
2. `PRUEBA_DE_CONTRATO`: verifica que el contrato directo se conserve.
3. `PRUEBA_DE_NO_REGRESIÓN`: verifica funciones protegidas relacionadas y se acuerda antes de editar.
4. `COMPILACIÓN`: demuestra consistencia técnica; no funcionamiento.
5. `PRUEBA_FÍSICA`: la realiza o confirma Patricia.
6. `SUITE_COMPLETA`: requiere autorización expresa.

No se ejecutan por defecto `clean`, reinstalaciones, suites completas, sincronizaciones reales ni pruebas sobre datos productivos. Una prueba no autoriza reparar componentes fuera del alcance.

Si una prueba falla:

- si el cambio actual causó la falla, se corrige dentro del alcance;
- si era previa, se registra `FALLA_PREEXISTENTE`;
- si el origen no está demostrado, se registra `ORIGEN_NO_CONFIRMADO`;
- si requiere otra célula o archivo, se presenta `IMPACTO_TRANSVERSAL`;
- no se declara éxito parcial como solución terminada.

## 16. Commit, validación y tag

### Commit técnico

Puede autorizarse después de completar el cambio, superar las pruebas técnicas acordadas, comprobar el diff y confirmar que no contiene cambios ajenos.

El commit técnico crea un punto seguro y trazable, pero no declara validación funcional.

### Validación funcional

Solo Patricia puede declarar satisfactoria una prueba física y cambiar el estado a `VALIDADA`.

### Tag estable

Un tag estable requiere validación física de Patricia, commit exacto, artefacto y hash, fallas conocidas documentadas y autorización expresa.

Push, integración y despliegue requieren autorización separada.

## 17. Estados oficiales

Los únicos estados oficiales son:

- `DEFINICIÓN`
- `AUTORIZADA`
- `DESARROLLO`
- `PRUEBA_TÉCNICA`
- `VALIDACIÓN_FÍSICA`
- `VALIDADA`
- `INTEGRADA`
- `CONGELADA`
- `SUSPENDIDA`
- `REABIERTA`

Los estados históricos `ANALISIS` y `PRUEBAS` se traducirán respectivamente a `DEFINICIÓN` y `PRUEBA_TÉCNICA` cuando se actualice la documentación.

Para declarar `CONGELADA` deben existir:

```text
REQUERIMIENTO_APROBADO:
PRUEBA_MÍNIMA_APROBADA:
VALIDACIÓN_DE_PATRICIA:
COMMIT:
TAG:
ARTEFACTO_Y_HASH:
RAMA_INTEGRADA:
FALLAS_CONOCIDAS:
```

## 18. Motivos de parada

Codex se detiene ante:

1. `CONTROL_PREVIO` incompleto o contradictorio.
2. Base estable no identificada o no aprobada.
3. Archivo requerido fuera de la lista permitida.
4. Decisión funcional ambigua.
5. Riesgo para datos reales o persistentes.
6. Cambio de esquema o contrato no autorizado.
7. Acción destructiva o difícil de revertir.
8. Impacto transversal no autorizado.
9. Conflicto de ramas con consecuencias funcionales.
10. Diferencia inesperada entre ambientes.
11. Resultado o cantidad de registros inesperados.
12. Credenciales, permisos o dispositivo no disponibles.
13. Necesidad de una etapa marcada `NO`.
14. Modificación inesperada fuera del alcance.
15. Documento obligatorio ausente, incompleto o contradictorio.
16. Imposibilidad técnica demostrada.

## 19. Cierre

En cada parada o cierre se entregará:

```text
RESUMEN_DE_CIERRE
TIPO_DE_INTERVENCIÓN:
CÉLULA_PRINCIPAL:
CÉLULAS_TRANSVERSALES:
OBJETIVO_AUTORIZADO:
BASE_ESTABLE_USADA:
ARCHIVOS_LEÍDOS:
ARCHIVOS_MODIFICADOS:
ARCHIVOS_FUERA_DE_ALCANCE_MODIFICADOS: NINGUNO
DATOS_O_ESQUEMAS_MODIFICADOS: NINGUNO
CONTRATOS_MODIFICADOS: NINGUNO
PRUEBAS_EJECUTADAS:
PRUEBAS_NO_EJECUTADAS:
RESULTADOS:
FUNCIONES_PROTEGIDAS_VERIFICADAS:
REGRESIONES_DETECTADAS:
ESTADO_GIT_FINAL:
COMMIT_TAG_PUSH_INTEGRACIÓN_DESPLIEGUE:
ESTADO_DE_LA_INTERVENCIÓN:
DECISIONES_DE_PATRICIA: NINGUNA
SIGUIENTE_ACCIÓN_PROPUESTA:
```

Toda afirmación de éxito debe asociarse a evidencia concreta. Si una prueba no se ejecutó, debe indicarse expresamente.

## 20. Regla final

Ante la duda entre avanzar y proteger el sistema, Codex debe detenerse antes de escribir, mostrar el hecho comprobado, el riesgo, las alternativas y la decisión concreta que corresponde a Patricia.

Bitácora se conserva y evoluciona desde una base estable mediante intervenciones pequeñas y trazables. Los nuevos requerimientos son parte legítima de esa evolución cuando respetan la visión dimensional, los contratos, la protección de lo validado y la autorización expresa.
