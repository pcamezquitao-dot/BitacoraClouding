# BACKEND_FASTAPI_BITACORA3

Backend FastAPI para BITÁCORA 3 (MariaDB) con:
- /health
- Resolución QR participante (empleado/supervisor)
- Resolución QR de AREA_ADMINISTRATIVA
- Cálculo supervisor por empleado (CTE ascenso)
- UC-03: creación de observación de área (inserta en bitacora_diaria + bitacora_area_observacion)
- Upload de evidencias (foto/audio/video) a disco + registro en bitacora_area_evidencia

## Instalación
```bash
python -m venv .venv
# Windows:
.venv\Scripts\activate
pip install -r requirements.txt
copy .env.example .env
# editar .env con tu conexión
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

## Nota sobre nombres de tablas
Si tus tablas se llaman diferente (ej: participante1), ajusta en `.env`:
- PARTICIPANTE_TABLE=participante1
