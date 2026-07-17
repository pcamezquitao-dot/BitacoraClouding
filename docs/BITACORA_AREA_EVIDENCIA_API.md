# API de evidencias de área

La tabla fue creada manualmente. No se requiere migración SQL.

## Crear dos evidencias para la misma bitácora

```bash
curl -X POST http://127.0.0.1:8001/bitacora-area-evidencias/upload \
  -F 'file=@/ruta/foto-1.jpg;type=image/jpeg' \
  -F id_bitacora=100 -F id_area=2 -F ts_in_min=29735040 \
  -F id_tipo_evidencia=1 \
  -F latitud=4.7110325 -F longitud=-74.0720920 -F precision_gps=6.50 \
  -F uuid_cliente=550e8400-e29b-41d4-a716-446655440001

curl -X POST http://127.0.0.1:8001/bitacora-area-evidencias/upload \
  -F 'file=@/ruta/foto-2.jpg;type=image/jpeg' \
  -F id_bitacora=100 -F id_area=2 -F ts_in_min=29735041 \
  -F id_tipo_evidencia=1 \
  -F latitud=4.7110325 -F longitud=-74.0720920 -F precision_gps=6.50 \
  -F uuid_cliente=550e8400-e29b-41d4-a716-446655440002
```

La repetición de cualquiera de los comandos con el mismo `uuid_cliente` devuelve
la evidencia existente y no crea un duplicado.

## Listar en orden de creación

```bash
curl 'http://127.0.0.1:8001/bitacora-area-evidencias?id_bitacora=100'
```
