# Política de células funcionales de BITÁCORA

## Autoridad y alcance

Este archivo es la autoridad principal para cualquier trabajo de Codex en este repositorio. El objetivo es preservar la estabilidad de BITÁCORA mediante cambios pequeños, trazables y aislados.

El Decálogo permanente es:

1. Trabajar con cambios pequeños y controlados.
2. No modificar funciones probadas sin autorización expresa.
3. Hacer diagnóstico técnico antes de modificar.
4. Preservar datos y estructura existentes.
5. Mantener compatibilidad entre Android, FastAPI, MariaDB y SQLite/Room.
6. Ejecutar pruebas rápidas después de cada cambio.
7. Evitar regresiones.
8. Compilar APK cuando corresponda a la fase de desarrollo.
9. Hacer prueba física en celular antes de declarar una versión estable.
10. Hacer commit o tag estable únicamente tras validar técnica y funcionalmente.

REGLA — BLOQUEO ESTRICTO DEL ALCANCE

Codex debe ejecutar únicamente la tarea solicitada por Patricia.

- No debe investigar otros módulos ni otras células.
- No debe agregar pruebas adicionales que Patricia no haya solicitado.
- No debe mejorar, refactorizar, reorganizar ni corregir nada que no sea indispensable para cumplir exactamente la tarea autorizada.
- No debe convertir una prueba sencilla en una auditoría o validación general.
- La autonomía técnica permite resolver la tarea solicitada, pero nunca ampliar su alcance.
- Si encuentra un problema ajeno, debe registrarlo brevemente y dejarlo intacto.
- Solo puede revisar otro archivo cuando sea directamente indispensable para resolver la tarea autorizada, explicándolo en una sola línea.
- Debe detenerse inmediatamente cuando obtenga el resultado concreto solicitado.

REGLA — AUTONOMÍA TÉCNICA Y NO INTERRUPCIÓN

Una vez Patricia haya autorizado una implementación y definido su alcance,
Codex debe continuar autónomamente hasta completar la implementación, las
pruebas, la compilación y la generación del artefacto solicitado.

Codex no debe detener el trabajo ni trasladar a Patricia decisiones técnicas
rutinarias que pueda resolver dentro del alcance autorizado, entre ellas:

- configuración del SDK y herramientas locales;
- local.properties y rutas del entorno;
- comandos Gradle;
- dependencias ya contempladas por el proyecto;
- imports;
- errores de sintaxis, tipos o compilación;
- pruebas fallidas causadas por los cambios autorizados;
- correcciones internas necesarias para cumplir el contrato aprobado;
- repetición de pruebas;
- generación y verificación de la APK.

Codex no debe presentar como terminado un trabajo que no haya compilado y
superado las pruebas correspondientes.

Codex solo debe interrumpir y solicitar decisión de Patricia cuando exista:

1. Necesidad de borrar o modificar datos reales.
2. Migración o cambio estructural de MariaDB o Room no autorizado.
3. Acción destructiva o difícilmente reversible.
4. Decisión funcional no definida en el contrato.
5. Ampliación necesaria fuera de la célula autorizada.
6. Contradicción que permita resultados funcionales distintos y no pueda
   resolverse con el CATALOGO o los contratos vigentes.
7. Necesidad de desplegar, instalar, hacer commit o push sin autorización.
8. Solicitud de credenciales o permisos que no estén disponibles.

Los controles obligatorios de seguridad de la herramienta, como el botón
“Allow”, pueden aparecer, pero Codex debe agrupar los comandos seguros cuando
sea posible y no acompañarlos de preguntas funcionales innecesarias.

## Protocolo obligatorio de ejecución por células

### 1. Definición completa antes de implementar

Toda célula debe comenzar con una definición que incluya:

- Objetivo.
- Alcance autorizado.
- Comportamiento esperado.
- Módulos y archivos permitidos.
- Exclusiones.
- Casos de prueba.
- Criterios para pasar a `VALIDADA`.

El alcance aprobado debe permanecer estable durante la implementación. Los requerimientos adicionales deben asignarse a otra célula o a una nueva versión.

### 2. Autonomía dentro de la célula

La aprobación inicial de una célula autoriza a Codex para:

- Inspeccionar.
- Implementar.
- Corregir errores propios.
- Ejecutar pruebas focalizadas.
- Compilar.
- Instalar en el dispositivo autorizado conservando sus datos.
- Revisar registros y evidencias.
- Repetir automáticamente las correcciones y pruebas necesarias.
- Actualizar la documentación de la célula.
- Realizar el commit final exclusivo de la célula.

Codex no debe solicitar aprobaciones intermedias para actividades técnicas ordinarias que permanezcan dentro del alcance aprobado.

### 3. Motivos únicos de interrupción

Codex solamente debe detenerse y consultar al usuario cuando exista:

1. `IMPACTO_TRANSVERSAL` sobre otra célula o módulo.
2. Riesgo de borrar, sobrescribir o modificar datos reales.
3. Una decisión funcional ambigua que pueda producir resultados materialmente diferentes.
4. Necesidad de credenciales, permisos o intervención física del usuario.
5. Más de un dispositivo conectado sin poder identificar de forma segura el autorizado.
6. Imposibilidad técnica demostrada de continuar dentro del alcance aprobado.

Los errores normales de código, pruebas o compilación deben corregirse automáticamente.

### 4. Verificación de la realidad antes de modificar

Antes de implementar, Codex debe inspeccionar:

- Código y arquitectura existentes.
- Documento de la célula.
- Esquemas reales.
- Ejemplos funcionales de los datos.
- Pantallas y navegación actuales.
- Pruebas existentes.
- Estado del repositorio y cambios no relacionados.

No debe deducir la lógica funcional únicamente por los nombres de tablas o columnas.

### 5. Pruebas escalonadas

Para reducir tiempo y consumo de recursos, ejecutar en este orden:

1. Pruebas específicas de la célula.
2. Pruebas de integración relacionadas.
3. Compilación incremental.
4. Instalación cuando la implementación esté estable.
5. Validaciones automatizadas en el dispositivo.
6. Una única validación física final del usuario.
7. Suite completa una sola vez al cierre.
8. Commit final.

No ejecutar `clean`, la suite completa o reinstalaciones repetidas salvo que exista una razón técnica documentada.

### 6. Preparación previa

Antes de iniciar la ejecución debe comprobarse, cuando corresponda:

- Dispositivo conectado, desbloqueado y autorizado.
- Servidor disponible.
- Base de datos preparada.
- Datos de prueba identificados como provisionales.
- Requerimiento consolidado.
- Ausencia de cambios ajenos que puedan ser sobrescritos.

### 7. Cierre obligatorio

Una célula solamente podrá pasar a `VALIDADA` cuando cuente con:

- Pruebas focalizadas aprobadas.
- Compilación exitosa.
- Instalación realizada, cuando corresponda.
- Validación física satisfactoria.
- Evidencias actualizadas.
- Confirmación de ausencia de regresiones atribuibles a la célula.
- Commit exclusivo y claramente identificado.
- Registro de cualquier falla preexistente ajena.

### 8. Protección del alcance

- No modificar otras células para solucionar problemas propios.
- No incluir cambios ajenos en el commit.
- No borrar datos ni cambiar esquemas sin autorización expresa.
- Ante cualquier impacto adicional, declarar `IMPACTO_TRANSVERSAL` antes de actuar.
- Las reglas de seguridad y protección de datos del DECÁLOGO prevalecen sobre la autonomía operativa.

### 9. Comunicación eficiente

Durante la ejecución, Codex debe:

- Evitar reportes repetitivos.
- No pedir confirmaciones ya concedidas.
- Comunicar únicamente bloqueos reales.
- Solicitar una sola validación humana al final.
- Entregar un cierre breve con célula, versión, pruebas, instalación, resultado, estado y commit.

### Aplicación

Este protocolo es obligatorio para todas las células nuevas y para las células activas cuando no contradiga un alcance previamente aprobado.

El DECÁLOGO y este protocolo, contenidos en `AGENTS.md`, constituyen la única fuente oficial de estas reglas. Los demás documentos del repositorio solo pueden enlazarlos o registrar evidencia de cumplimiento; no deben copiarlos ni redefinirlos.

## Política de célula objetivo

Toda tarea de desarrollo debe declarar, antes de cualquier modificación:

```text
CELULA_OBJETIVO = CXX_NOMBRE
```

Solo pueden modificarse los archivos listados como `ARCHIVOS MODIFICABLES` en `cells/CXX_NOMBRE.md`. Todo archivo fuera de esa lista está en modo **SOLO LECTURA**. Una tarea documental puede modificar únicamente los documentos expresamente autorizados.

Regla superior: Codex trabaja sobre **una sola célula objetivo por vez**. Todas las demás células se consideran congeladas para la tarea activa.

## Estados de una célula

Estados permitidos:

```text
ANALISIS
DESARROLLO
PRUEBAS
VALIDADA
CONGELADA
```

Una célula `CONGELADA` no se modifica sin autorización explícita. `VALIDADA` no equivale a `CONGELADA`: solo se congela cuando existe evidencia de versión, pruebas y commit estable.

## Propiedad y contratos

Cada tabla, endpoint, modelo, DAO, repositorio, pantalla, servicio, migración y prueba tiene una sola célula propietaria, propuesta en `CELULAS.md` y detallada en `cells/`.

Una célula consumidora puede usar contratos públicos de otra célula —API, interfaz, repositorio, claves estables o modelo compartido estrictamente necesario— pero no puede modificar su implementación. Los contratos deben conservar nombres, nulabilidad, tipos y semántica documentados.

## Cambios transversales

Si una tarea requiere una segunda célula, Codex debe detenerse antes de modificar y presentar un `IMPACTO_TRANSVERSAL` con:

- células y archivos afectados;
- contratos involucrados;
- motivo técnico e impacto esperado;
- riesgos y regresiones posibles;
- pruebas necesarias;
- alternativa que evite el cambio.

Solo una autorización expresa permite continuar. No se sustituyen masivamente valores, nombres de tablas ni contratos entre células.

## Política de datos y base de datos

Una tabla tiene una sola célula propietaria. Cambios de columnas, tipos, claves, FK, índices, constraints, migraciones MariaDB o Room requieren autorización expresa y análisis de impacto. No se borran datos ni se ejecutan ETL, migraciones o APK por defecto.

`participante` es la tabla productiva de participantes definida por configuración; no se inventan ni renombran tablas sin inspección previa.

## Política de pruebas

Cada célula debe mantener:

```text
PRUEBA_PROPIA
PRUEBA_CONTRATO
PRUEBA_GLOBAL_MINIMA
```

Las pruebas se ejecutan en proporción al cambio. Una modificación de Android exige compilación y, cuando la fase lo autorice, prueba física; una modificación de API o esquema exige pruebas backend y de contrato. Ninguna prueba autoriza cambios de datos fuera del alcance.

## Política de Git y estabilidad

Una célula validada registra:

```text
VERSION_CELULA
COMMIT_ESTABLE
TAG_ESTABLE
```

Si no existe evidencia verificable, se registra `PENDIENTE`. Nunca se etiqueta como estable una versión no validada. Los cambios locales ajenos se preservan; no se hacen reset, checkout destructivo ni commit automático.

## Archivos compartidos críticos

Los siguientes archivos son puntos de alto acoplamiento y se consideran solo lectura salvo autorización transversal: `app/main.py`, `app/core/config.py`, `app/core/db.py`, `android/bitacora-android/app/src/main/java/com/cactus/bitacora/MainActivity.kt`, `data/Api.kt`, `model/OpenApiModels.kt`, `data/local/BitacoraDatabase.kt`, `data/ReferenceCatalogRepository.kt` y `sync/OfflineSyncWorker.kt`.

## Criterio de congelamiento funcional

Una célula no puede declararse `CONGELADA` únicamente porque sus pruebas técnicas pasen. Antes de congelarla deben estar identificados y documentados:

1. los usuarios afectados;
2. los procesos funcionales afectados;
3. los contratos que publica y consume;
4. su prueba propia;
5. las pruebas de contrato;
6. las pruebas funcionales mínimas de cada usuario afectado.

La evidencia se conserva en la ficha de la célula, en `users/` y en `PROCESOS.md`. Si falta una de estas evidencias, el estado máximo es `VALIDADA`.

## Procedimiento obligatorio

Antes de modificar una célula, Codex debe informar:

```text
CELULA_OBJETIVO:
USUARIOS_AFECTADOS:
PROCESOS_AFECTADOS:
ARCHIVOS_MODIFICABLES:
CELULAS_CONGELADAS_RELACIONADAS:
PRUEBAS_A_EJECUTAR:
```

Si durante el trabajo aparece otra célula que requiere modificación, Codex debe detenerse, emitir `IMPACTO_TRANSVERSAL` y esperar autorización expresa.

1. Declarar `CELULA_OBJETIVO` con el bloque anterior.
2. Leer su documento en `cells/` y sus contratos consumidos.
3. Diagnosticar sin escribir.
4. Confirmar que los archivos a cambiar pertenecen a la célula.
5. Si aparece otra célula, emitir `IMPACTO_TRANSVERSAL` y esperar autorización.
6. Implementar el cambio mínimo, probarlo y registrar evidencia.
7. Actualizar estado, versión y commit solo cuando exista evidencia suficiente.
