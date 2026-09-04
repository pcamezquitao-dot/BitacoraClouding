# C24 — Autenticación administrativa

**Estado:** CANCELADA POR ALCANCE

## Decisión

Patricia retiró expresamente el PIN personal, su validación y toda sesión o
requisito sustituto para la administración de jornadas. C24 no publica
endpoints, tokens, almacenamiento local ni contratos de autenticación.

## Compatibilidad con C23

C23 conserva listado, consulta, creación, edición y cambio de estado de
jornadas sin autenticación administrativa temporal. No acepta ni requiere
`X-Admin-Actor`, `Authorization`, PIN, contraseña o confirmación adicional.

## Límites

No se modifican bases de datos ni se eliminan las tablas creadas durante las
pruebas anteriores de C24; quedan fuera de uso y no forman parte del contrato
de la aplicación. Una futura autenticación requerirá una nueva célula y una
autorización expresa.
