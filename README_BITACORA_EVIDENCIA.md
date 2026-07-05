# Cambios realizados para bitacora_diaria y bitacora_area_evidencia

Se agregaron/ajustaron endpoints REST para crear bitácoras y subir evidencias al servidor.

## Endpoints principales

### 1. Crear sólo bitácora diaria

`POST /bitacora_diaria`

JSON de ejemplo:

```json
{
  "id_empleado": 1001,
  "id_supervisor": 2001,
  "tipo_anotacion": 1,
  "observaciones": "Registro desde Android",
  "client_uuid": "uuid-generado-en-android"
}
```

Respuesta:

```json
{
  "id_bitacora": 6,
  "id_empleado": 1001,
  "id_supervisor": 2001,
  "ts_in_min": 29684240,
  "ts_out_min": null,
  "tipo_anotacion": 1,
  "observaciones": "Registro desde Android"
}
```

### 2. Subir evidencia separada

`POST /bitacora_area_evidencia/upload`

Tipo: `multipart/form-data`

Campos:

- `id_bitacora`
- `id_empleado`
- `id_supervisor`
- `ts_in_min`
- `tipo_evidencia`: `FOTO`, `AUDIO` o `VIDEO`
- `archivo`: archivo físico
- `duracion_seg` opcional
- `orden` opcional

Importante: por la llave foránea existente, antes debe existir el registro relacionado en `bitacora_area_observacion`.

### 3. Crear bitácora completa + subir archivo

`POST /bitacora_completa/upload`

Tipo: `multipart/form-data`

Campos mínimos:

- `id_empleado`
- `tipo_evidencia`: `FOTO`, `AUDIO` o `VIDEO`
- `archivo`
- `qr_area`: recomendado, porque permite crear también `bitacora_area_observacion`

Campos opcionales:

- `id_supervisor`
- `ts_in_min`
- `ts_out_min`
- `tipo_anotacion`
- `observaciones`
- `client_uuid`
- `duracion_seg`
- `orden`

## Archivos modificados

- `app/routers/bitacora_uc03.py`
- `app/schemas/bitacora.py`
- `app/schemas/evidencia.py`
- `app/main.py`

## Nota de base de datos

Ver `sql/nota_bitacora_evidencia.sql`.
