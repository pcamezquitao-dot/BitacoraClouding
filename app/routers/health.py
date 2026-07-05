from fastapi import APIRouter
from app.schemas.common import HealthOut

router = APIRouter(tags=["health"])

@router.get("/health", response_model=HealthOut)
def health():
    return HealthOut(status="ok")
