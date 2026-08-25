# C20A_AUTENTICACION_SUPERVISOR

**Estado:** PRUEBAS

## Objetivo

Proveer a CONTROL una identidad firmada y de corta duración durante el prototipo de desarrollo. No constituye autenticación personal definitiva.

## Contrato

- `POST /control/supervisor/session`: habilitado solamente cuando `APP_ENV` es `development`, `dev` o `test` y `SUPERVISOR_DEV_AUTH_ENABLED=true`.
- `GET /control/supervisor/me`: exige `Authorization: Bearer <token>`.
- Token HS256 con `sub`, `codigo`, `rol=supervisor`, `areas`, `iat` y `exp`; vigencia máxima de 600 segundos.
- Secreto exclusivamente en `SUPERVISOR_TOKEN_SECRET`, mínimo 32 caracteres; no existe valor por defecto útil.
- MariaDB revalida participante, cargo vigente y áreas. Un cambio de alcance obliga a iniciar otra sesión.
- Android conserva el token solamente en memoria y lo descarta ante HTTP 401.

## Advertencia

`PROTOTIPO DE DESARROLLO — IDENTIDAD NO AUTENTICADA CON CREDENCIAL PERSONAL`.
La emisión por código queda bloqueada por defecto y no es apropiada para producción.

## Evidencia

- Pruebas: token válido, ausente, alterado, vencido, rol incorrecto, doble bloqueo de producción, dos supervisores y no-supervisor.
- Desplegado únicamente en `https://161-22-47-89.sslip.io/bitacora/` como ambiente de desarrollo.
- Configuración externa: `/etc/bitacora/supervisor-development.env`, propietario `root`, modo `600`; secreto aleatorio de 64 caracteres, no mostrado ni guardado en el repositorio.
- P0002 y P0003: sesión y `/me` HTTP 200; P0100: 403.
- Token ausente, alterado y vencido: 401; ruta anterior `/{codigo}`: 404.
