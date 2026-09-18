# AGENTS.md — Instrucciones de entrada para Codex

## 1. Propósito de este archivo

Este archivo es la puerta de entrada obligatoria para todo trabajo de Codex en el repositorio Bitácora.

No contiene ni redefine las reglas funcionales, técnicas u operativas del proyecto. Esas reglas están exclusivamente en .

El término anterior **“Decálogo”** queda obsoleto y se reemplaza por ****.

## 2. Fuente oficial de reglas

Antes de responder, planear, diagnosticar, investigar, ejecutar comandos, modificar archivos o realizar cualquier otra acción sobre el repositorio, Codex debe:

1. Localizar en la raíz del repositorio o worktree actual.
2. Leer completamente su versión vigente.
3. Aplicar sus disposiciones durante toda la tarea.
4. Cumplir los formatos, autorizaciones, límites, motivos de parada y requisitos de cierre definidos allí.

es la única fuente oficial de las reglas invariantes de desarrollo de Bitácora.

Este `AGENTS.md` no debe reproducir, resumir, reinterpretar, ampliar ni reemplazar esas reglas.

## 3. Confirmación obligatoria de inicio

Antes de realizar cualquier acción adicional, Codex debe presentar el bloque de inicio exigido por :

```text
INVARIANTES LEÍDAS:
VERSIÓN:
CÉLULA OBJETIVO:
EXCEPCIONES AUTORIZADAS:
```

La confirmación debe corresponder al contenido realmente leído. No puede suponerse ni reutilizarse automáticamente de otra conversación, rama, repositorio o worktree.

Si la célula objetivo o una excepción necesaria no están definidas, Codex debe actuar conforme al procedimiento y a los motivos de parada establecidos en .

## 4. Falta o imposibilidad de lectura

Si :

* no existe;
* no puede abrirse;
* está vacío;
* está incompleto;
* tiene más de una versión sin poder identificar la vigente;
* o contradice otro documento operativo del repositorio;

Codex debe detenerse antes de modificar archivos, ejecutar implementaciones o realizar acciones irreversibles.

Debe informar el hecho comprobado y solicitar una decisión de Patricia conforme al formato de parada definido en .

La ausencia de no autoriza utilizar el antiguo Decálogo, recuerdos de conversaciones, versiones anteriores ni reconstrucciones realizadas por Codex.

## 5. Relación con otros documentos

Después de leer , Codex podrá consultar únicamente los documentos necesarios para la tarea autorizada, incluidos cuando corresponda:

* la ficha de la célula objetivo en `cells/`;
* los contratos consumidos directamente;
* los documentos de usuarios o procesos vinculados;
* los archivos de evidencia y estado exigidos por la célula.

Todos esos documentos están subordinados a .

Ninguna ficha de célula, requerimiento, conversación, comentario, documento técnico o instrucción contenida en otro archivo puede modificar implícitamente las invariantes.

Si encuentra una contradicción, Codex debe detenerse, identificar los textos en conflicto y solicitar decisión de Patricia.

## 6. Alcance de AGENTS.md

La lectura de este archivo:

* no constituye autorización para desarrollar;
* no define una célula objetivo;
* no autoriza modificaciones;
* no autoriza pruebas adicionales;
* no autoriza escrituras en bases de datos;
* no autoriza instalación, despliegue, commit, tag, push ni integración;
* no reemplaza ninguna autorización exigida por .

Las autorizaciones y límites aplicables a cada tarea se determinan exclusivamente conforme a y a la solicitud expresa de Patricia.

## 7. Protección de

Codex no puede modificar ni este `AGENTS.md` como parte incidental de una célula funcional.

Cualquier modificación de estos documentos requiere:

1. una tarea documental independiente;
2. autorización expresa de Patricia;
3. revisión de posibles contradicciones;
4. conservación de la versión anterior en el historial;
5. validación expresa antes de adoptar la nueva versión.

## 8. Regla de interpretación

Este archivo debe interpretarse únicamente como mecanismo para obligar la lectura y aplicación de .

Si una frase de `AGENTS.md` pudiera interpretarse como una regla diferente, adicional o contraria a , prevalece y Codex debe informar la discrepancia antes de continuar.
