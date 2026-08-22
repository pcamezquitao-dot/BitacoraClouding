# C21_NAVEGACION_PRINCIPAL

**ID:** C21_NAVEGACION_PRINCIPAL

**Estado:** VALIDADA

**Versión:** Maria48-C21

**Último commit estable:** PENDIENTE

## Objetivo

Organizar la navegación principal de Bitácora en este orden:

1. Inicio.
2. Crear.
3. Consultar.
4. Sincronizar.
5. Más.

## Propiedad

- Destinos, orden, etiquetas e iconos de la navegación principal.
- Política de interpretación del criterio de consulta: vacío, código o `*`.
- Integración visual mínima con las pantallas existentes.

## Contratos consumidos

- C01, C03 y C04: identidad y alcance autorizado.
- C07: creación y consulta de entradas/salidas 4 y 5.
- C11: consultas Room parametrizadas, sin cambios de esquema.
- C12: pantalla y proceso de sincronización existentes.
- C18: actividad, shell y componentes visuales Android.
- C20: sesión y supervisados del modo Supervisor.

## Restricciones

- No posee ni modifica tablas.
- No duplica Crear ni Sincronizar.
- Un criterio vacío nunca equivale a `*`.
- `*` conserva el alcance de la identidad activa.
- Los códigos fuera del alcance del supervisor se presentan como no encontrados.

## Pruebas

- **Propia:** orden de destinos, Inicio predeterminado e interpretación de vacío/código/`*`.
- **Contrato:** consulta parametrizada de tipos 4/5 y alcance de supervisor.
- **Global mínima:** navegación, Atrás, Crear, Consultar, Sincronizar, offline y compilación Android.

## Evidencia técnica — 2026-08-22

- Pruebas propias: 7 aprobadas, 0 fallas.
- La prueba Room comprobó tipos 4/5 separados, filtrado por participantes autorizados y orden descendente por fecha/hora.
- Suite Android: 214 ejecutadas; 213 aprobadas y una falla preexistente ajena en `BitacoraQueryPolicyTest`, que todavía espera la URL HTTP anterior a HTTPS.
- `assembleProductionDebug`: `BUILD SUCCESSFUL`.
- No se modificaron esquemas, entidades Room, SQLite, MariaDB, API ni el proceso interno de sincronización.
- La compilación quedó lista para la validación física posterior.

## Evidencia física — 2026-08-22

- Dispositivo único autorizado: V2035, serial `3065397347006YM`.
- Instalación mediante `adb install -r`: satisfactoria, sin desinstalar ni limpiar almacenamiento.
- Versión instalada: `versionCode 48`, `versionName maria48-control-supervisor-dev`.
- UID y archivos `bitacora_local.db`, WAL y SHM conservados antes y después de instalar.
- Barra comprobada: `Inicio | Crear | Consultar | Sincronizar | Más`; Inicio fue el destino inicial.
- Crear abrió el formulario existente y Atrás regresó a Inicio sin guardar datos.
- P0015 mostró exclusivamente registros del empleado 15.
- `*` mostró movimientos y conservó entrada 4 y salida 5 como filas separadas.
- P0002 consultó `*` dentro de su alcance; P0003 fue rechazado como `Participante no encontrado`.
- Un campo vacío mostró `Ingrese un código de participante o *` y no se interpretó como comodín.
- Sincronizar y Más abrieron sus pantallas existentes; no se ejecutó sincronización ni actualización de catálogos.
- Atrás regresó tanto a Inicio como al menú del Supervisor.
- Logcat: sin cierres, excepciones de la aplicación, errores Room/SQLite, TLS ni red. Solo aparecieron mensajes del sistema Vivo sin relación con Bitácora.
- Estado final: VALIDADA. El congelamiento requiere commit/tag estable aislado.
