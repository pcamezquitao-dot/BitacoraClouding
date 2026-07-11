from pathlib import Path
import os

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from dotenv import load_dotenv

# =========================
# 1) Cargar .env (raíz del proyecto)
# =========================
ENV_PATH = Path(__file__).resolve().parents[1] / ".env"
load_dotenv(dotenv_path=ENV_PATH)

# =========================
# 2) Importar routers (DESPUÉS de cargar .env)
# =========================
from app.routers.health import router as health_router
from app.routers.participante import router as participante_router
from app.routers.areas import router as areas_router
from app.routers.empleados import router as empleados_router
from app.routers.bitacora_uc03 import router as bitacora_uc03_router
from app.routers.empleado_area import router as empleado_area_router

# =========================
# 3) Crear app
# =========================
app = FastAPI(title="BACKEND_FASTAPI_BITACORA3")

# Servir archivos subidos por los endpoints de evidencia
upload_dir = os.getenv("UPLOAD_DIR", "uploads")
os.makedirs(upload_dir, exist_ok=True)
app.mount("/uploads", StaticFiles(directory=upload_dir), name="uploads")

app.include_router(health_router)
app.include_router(participante_router)
app.include_router(areas_router)
app.include_router(empleados_router)
app.include_router(bitacora_uc03_router)
app.include_router(empleado_area_router)

@app.get("/")
def root():
    return {"ok": True, "service": "BACKEND_FASTAPI_BITACORA3"}
