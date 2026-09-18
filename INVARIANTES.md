# INVARIANTES DE DESARROLLO DE BITÁCORA

**Aprobadas por Patricia Amézquita:** 16 de septiembre de 2026  
**Estado:** VIGENTES  
**Autoridad:** Reglas superiores para todo trabajo de Codex en Bitácora.

## Propósito

Permitir correcciones y avances sin ampliar silenciosamente el alcance, inventar requerimientos, afectar otras células, perder datos ni dañar funcionalidades validadas.

## 1. Alcance de la autorización

Toda tarea debe indicar su objetivo y la célula autorizada. Una corrección o desarrollo autorizado permite:

- Diagnosticar dentro de la célula objetivo.
- Modificar únicamente los archivos autorizados.
- Corregir errores técnicos propios del cambio.
- Ejecutar la prueba focalizada indispensable.
- Compilar para demostrar que el cambio funciona.

No autoriza por sí solo modificar otra célula, agregar funcionalidades, cambiar datos reales o esquemas, instalar, desplegar, publicar, hacer commit, tag, push o integración.

## 2. Protección de las demás células

Codex trabajará sobre una sola célula objetivo. Todas las demás quedan congeladas y en modo solo lectura.

Puede consultar un archivo externo únicamente cuando sea indispensable para comprender un contrato o dependencia directa. Debe informar qué archivo consultó y por qué. Consultar no autoriza modificar.

Si necesita modificar otra célula, archivo compartido no autorizado, API, tabla, esquema o contrato, debe detenerse antes del cambio y presentar:

    IMPACTO_TRANSVERSAL
    CÉLULA O ARCHIVO AFECTADO:
    MOTIVO INDISPENSABLE:
    RIESGO:
    ALTERNATIVA QUE EVITA EL CAMBIO:
    PRUEBA ADICIONAL NECESARIA:
    DECISIÓN DE PATRICIA:

## 3. Resumen estructurado en cada parada

Una parada ocurre cuando termina la acción solicitada, aparece un bloqueo, se necesita autorización, existe impacto transversal, se requiere intervención física o se cambia de etapa.

Codex entregará:

    RESUMEN ESTRUCTURADO DE LA PARADA
    1. CÉLULA OBJETIVO:
    2. OBJETIVO AUTORIZADO:
    3. TRABAJO REALIZADO:
    4. ARCHIVOS MODIFICADOS:
    5. DATOS O ESQUEMAS MODIFICADOS:
    6. PRUEBAS EJECUTADAS:
    7. RESULTADO OBTENIDO:
    8. ELEMENTOS NO MODIFICADOS:
    9. PROBLEMAS O RIESGOS ENCONTRADOS:
    10. ESTADO ACTUAL:
    11. DECISIONES QUE DEBE TOMAR PATRICIA:
    12. SIGUIENTE ACCIÓN PROPUESTA:

Si un elemento no existe, debe indicarse NINGUNO. Las decisiones serán preguntas numeradas con sus consecuencias. Si no existen, debe decir DECISIONES PENDIENTES DE PATRICIA: NINGUNA.

Una acción propuesta no constituye autorización.

## 4. Prohibición de inventar o ampliar requerimientos

Solamente se considera requerimiento aquello que Patricia haya solicitado o aprobado expresamente. Codex no puede convertir una suposición, recomendación, práctica técnica, nombre de tabla o interpretación propia en requerimiento.

Antes de desarrollar presentará:

    REQUERIMIENTO AUTORIZADO:
    RESULTADO ESPERADO:
    EXCLUSIONES:
    PRUEBA MÍNIMA ACORDADA:
    ELEMENTOS PROPUESTOS POR CODEX: NINGUNO

Reglas:

- Si una ambigüedad puede producir resultados funcionales diferentes, debe preguntar.
- El silencio de Patricia no significa aprobación.
- Autorizar una célula no autoriza funcionalidades nuevas.
- Corregir un error no autoriza cambiar el comportamiento esperado.
- Los requerimientos nuevos van a pendientes o a otra versión; no se incorporan aprovechando el cambio.
- Una recomendación se identifica como PROPUESTA NO AUTORIZADA y no se implementa hasta ser aprobada.
- Se conserva la solicitud original de Patricia como referencia.

## 5. Protección de datos, esquemas y credenciales

Las consultas de solo lectura necesarias para diagnosticar la célula están permitidas.

Ninguna autorización de desarrollo permite insertar, actualizar, eliminar, migrar, renombrar o alterar datos reales, tablas, columnas, claves, índices o relaciones.

Toda escritura en MariaDB o SQLite/Room requiere autorización expresa y debe presentar:

    OPERACIÓN PROPUESTA:
    BASE DE DATOS Y TABLAS AFECTADAS:
    DATOS REALES O DE PRUEBA:
    CANTIDAD ESTIMADA DE REGISTROS:
    RESPALDO DISPONIBLE:
    FORMA DE REVERSIÓN:
    RIESGOS:
    SCRIPT EXACTO:
    DECISIÓN DE PATRICIA:

Reglas:

- Antes de UPDATE o DELETE, ejecutar primero el SELECT equivalente.
- Las condiciones deben identificar con precisión los registros.
- No ejecutar migraciones, ETL o limpiezas por defecto.
- Identificar datos productivos y de prueba.
- Una autorización para una tabla no se extiende a tablas relacionadas.
- Cambiar MariaDB no autoriza cambiar Room, ni al contrario.
- Detenerse si la cantidad afectada difiere de la esperada.
- No guardar credenciales o secretos en código, documentos, commits o mensajes finales.

## 6. Control de versiones, aislamiento y trazabilidad

Antes de modificar código, Codex registrará:

    RAMA ACTUAL:
    COMMIT INICIAL:
    CÉLULA OBJETIVO:
    ESTADO DEL REPOSITORIO:
    ARCHIVOS YA MODIFICADOS:
    RAMA O WORKTREE DE TRABAJO:

Reglas:

- Trabajar en rama o worktree aislado.
- Preservar cambios anteriores o ajenos.
- No ejecutar reset destructivo, descartar cambios, borrar ramas o sobrescribir archivos sin autorización.
- Separar los cambios de cada célula.
- Mostrar el resumen de git diff antes del cierre.
- No incluir archivos ajenos en un commit.
- Commit, tag, push e integración se realizan únicamente dentro de una autorización final expresa.
- Un tag estable requiere validación funcional de Patricia.
- No mover ni reemplazar tags protegidos.
- Informar si los cambios están locales, confirmados o enviados a GitHub.

Una versión validada registrará célula, versión, commit, tag, artefacto, hash, pruebas y validación de Patricia.

## 7. Pruebas proporcionales y fallas externas

Una corrección incluye únicamente la prueba focalizada indispensable y la compilación necesaria. Ninguna prueba autoriza modificar otras funciones, datos, células o contratos.

Niveles:

1. Prueba focalizada: incluida.
2. Compilación: incluida.
3. Consulta de contrato directo: permitida en solo lectura e informada.
4. Integración entre células: autorización expresa.
5. Instalación: incluida solo cuando el ciclo técnico la autoriza.
6. Prueba física: intervención de Patricia.
7. Suite completa: autorización expresa.

No ejecutar clean, reinstalaciones repetidas ni suite completa por defecto. Solo Patricia declara satisfactoria una prueba física.

### Falla en otra célula

- No modificar automáticamente la otra célula.
- Si el cambio actual rompió un contrato, corregir el propio cambio dentro de la célula objetivo.
- Si la falla era previa, registrarla como FALLA PREEXISTENTE EXTERNA.
- Si no puede confirmarse el origen sin modificar, registrar ORIGEN NO CONFIRMADO.
- Si no bloquea, continuar únicamente dentro del alcance.
- Si bloquea, detenerse y presentar decisiones.
- Si exige modificar otra célula, declarar IMPACTO_TRANSVERSAL.

Una falla externa autorizada se corrige mediante una intervención sobre su célula propietaria, en rama o worktree separado desde el último commit estable aplicable.

La versión anterior nunca se sobrescribe. La corrección genera un nuevo commit y una nueva versión tras validación. Las ramas se integran controladamente; los conflictos funcionales requieren decisión de Patricia. Una rama correctiva solo puede eliminarse tras integración, validación, tag y autorización.

## 8. Ciclo técnico completo y cierre

Patricia puede autorizar desde el inicio un ciclo completo:

    CICLO TÉCNICO AUTORIZADO
    DESARROLLAR:
    PROBAR FOCALMENTE:
    COMPILAR:
    INSTALAR EN DISPOSITIVO IDENTIFICADO:
    CONSERVAR DATOS: OBLIGATORIO
    DESPLEGAR O PUBLICAR: NO, salvo autorización expresa

Codex no pedirá autorizaciones intermedias dentro del ciclo aprobado si no cambia de célula, no amplía el requerimiento, no borra datos y no aparece un motivo de parada.

Después de la validación física, Patricia podrá autorizar conjuntamente:

    CIERRE AUTORIZADO
    COMMIT:
    TAG:
    PUSH:
    INTEGRACIÓN:
    DESPLIEGUE:
    PRODUCCIÓN:

Producción debe quedar expresamente incluida. Una instalación exitosa no equivale a validación funcional.

## 9. Motivos cerrados de parada

Codex se detendrá únicamente ante:

1. Decisión funcional no definida con resultados posibles diferentes.
2. Necesidad de modificar otra célula o archivo no autorizado.
3. Riesgo de borrar, sobrescribir o alterar datos reales.
4. Cambio de estructura MariaDB o Room no autorizado.
5. Acción destructiva o difícil de revertir.
6. Conflicto entre ramas con consecuencias funcionales.
7. Diferencia inesperada entre desarrollo y producción.
8. Credenciales o permisos no disponibles.
9. Imposibilidad técnica comprobada dentro del alcance.
10. Resultado inesperado que pueda afectar otras funciones.
11. Cantidad de registros diferente de la prevista.
12. Necesidad de una acción excluida del ciclo autorizado.

Codex resolverá autónomamente errores rutinarios dentro del alcance: sintaxis, imports, configuración local, compilación, errores propios y repetición de la prueba focalizada.

Al detenerse entregará:

    MOTIVO DE LA PARADA:
    HECHO COMPROBADO:
    TRABAJO COMPLETADO:
    TRABAJO PENDIENTE:
    RIESGO:
    ALTERNATIVAS:
    RECOMENDACIÓN DE CODEX:
    DECISIONES DE PATRICIA:

## 10. Estados, validación y congelamiento

Estados permitidos:

- DEFINICIÓN
- AUTORIZADA
- DESARROLLO
- PRUEBA_TÉCNICA
- VALIDACIÓN_FÍSICA
- VALIDADA
- INTEGRADA
- CONGELADA
- SUSPENDIDA
- REABIERTA

Solo Patricia puede declarar VALIDADA. Compilar o pasar una prueba técnica no equivale a validación física. VALIDADA no significa automáticamente CONGELADA.

Antes de congelar deben existir:

    REQUERIMIENTO APROBADO:
    PRUEBA MÍNIMA APROBADA:
    VALIDACIÓN DE PATRICIA:
    COMMIT:
    TAG:
    ARTEFACTO Y HASH:
    RAMA INTEGRADA:
    FALLAS CONOCIDAS:

Una célula congelada queda en solo lectura. Para corregirla se crea una intervención desde su último commit estable. La corrección genera una nueva versión y conserva la anterior.

## 11. Autoridad, cambios y excepciones

INVARIANTES.md es la autoridad superior y Codex debe leerlo antes de toda tarea.

Ninguna conversación, recomendación, ficha de célula, requerimiento posterior o modificación de AGENTS.md puede contradecirlo o reemplazarlo implícitamente.

- Codex no puede modificar este archivo por iniciativa propia.
- Una solicitud de desarrollo no autoriza cambiar invariantes.
- Las invariantes no se modifican dentro del commit de una célula funcional.
- Todo cambio se trata como tarea documental independiente.
- Patricia debe aprobarlo expresamente.
- La versión anterior permanece en el historial.
- AGENTS.md debe remitir fielmente a estas reglas y no crear reglas diferentes.
- Ante contradicción, Codex se detiene, la muestra y solicita decisión.
- Patricia indicará si una excepción es temporal o permanente.

Antes de iniciar:

    INVARIANTES LEÍDAS:
    VERSIÓN:
    CÉLULA OBJETIVO:
    EXCEPCIONES AUTORIZADAS:

## 12. Protección de contratos y funciones existentes

Toda funcionalidad previamente validada se presume protegida.

Una corrección debe conservar APIs, tablas, modelos, campos, tipos, nulabilidad, rutas, navegación y comportamiento observable, salvo autorización expresa.

- Identificar consumidores antes de modificar un componente compartido.
- No renombrar tablas, columnas, endpoints, campos JSON, clases o métodos públicos por conveniencia.
- No cambiar el significado de valores existentes.
- No eliminar campos porque aparentemente no se utilizan.
- No hacer sustituciones masivas entre células.
- No agregar autenticaciones, usuarios, PIN, permisos o controles no solicitados.
- No reorganizar navegación o menús fuera del requerimiento.
- Considerar archivos compartidos de solo lectura hasta demostrar que modificarlos es indispensable.
- Preferir soluciones compatibles.
- Limitar la búsqueda de referencias al contrato indispensable y hacerla en solo lectura.

Si debe alterarse un contrato:

    CONTRATO ACTUAL:
    CAMBIO NECESARIO:
    CÉLULAS CONSUMIDORAS:
    COMPATIBILIDAD:
    RIESGO DE REGRESIÓN:
    ALTERNATIVA COMPATIBLE:
    PRUEBAS NECESARIAS:
    DECISIÓN DE PATRICIA:

El resumen final indicará contratos modificados, funcionalidades protegidas verificadas y regresiones detectadas.

## Regla final

Cuando exista duda entre avanzar y proteger el sistema, Codex no ampliará el alcance por inferencia. Presentará el hecho, el riesgo, las alternativas y la decisión concreta que corresponde a Patricia.

