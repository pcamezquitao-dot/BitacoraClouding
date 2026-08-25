from pathlib import Path
import os
from threading import Thread

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
from app.routers.bitacora_area_evidencia import router as bitacora_area_evidencia_router
from app.routers.face_templates import router as face_templates_router
from app.routers.catalogos import router as catalogos_router
from app.routers.admin_catalog import router as admin_catalog_router
from app.routers.administrative_areas import router as administrative_areas_router
from app.routers.employee_area import router as employee_area_admin_router
from app.routers.calendar_general import router as calendar_general_router
from app.routers.participants_admin import router as participants_admin_router
from app.routers.satelital import router as satelital_router
from app.routers.supervisor import router as supervisor_router
from app.routers.control_supervisor import router as control_supervisor_router
from app.services.evidencia_file_service import evidencia_root
from app.services.audio_transcription_service import recover_pending_transcriptions

# =========================
# 3) Crear app
# =========================
app = FastAPI(title="BACKEND_FASTAPI_BITACORA3")

# Servir archivos subidos por los endpoints de evidencia
upload_dir = os.getenv("UPLOAD_DIR", "uploads")
os.makedirs(upload_dir, exist_ok=True)
app.mount("/uploads", StaticFiles(directory=upload_dir), name="uploads")
satelital_dir = os.getenv(
    "SATELITAL_DIR",
    str(Path.cwd() / "satelital" / "embalses"),
)
os.makedirs(satelital_dir, exist_ok=True)
app.mount(
    "/satelital-files",
    StaticFiles(directory=satelital_dir),
    name="satelital-files",
)
evidencia_root()

app.include_router(health_router)
app.include_router(participante_router)
app.include_router(areas_router)
app.include_router(empleados_router)
app.include_router(bitacora_uc03_router)
app.include_router(empleado_area_router)
app.include_router(bitacora_area_evidencia_router)
app.include_router(face_templates_router)
app.include_router(catalogos_router)
app.include_router(admin_catalog_router)
app.include_router(administrative_areas_router)
app.include_router(employee_area_admin_router)
app.include_router(calendar_general_router)
app.include_router(participants_admin_router)
app.include_router(satelital_router)
app.include_router(supervisor_router)
app.include_router(control_supervisor_router)


@app.on_event("startup")
def recover_audio_transcriptions():
    Thread(
        target=recover_pending_transcriptions,
        name="audio-transcription-recovery",
        daemon=True,
    ).start()

@app.get("/")
def root():
    return {"ok": True, "service": "BACKEND_FASTAPI_BITACORA3"}
