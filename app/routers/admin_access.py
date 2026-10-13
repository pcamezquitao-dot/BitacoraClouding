from fastapi import APIRouter, Depends

from app.core.admin_auth import AdminIdentity, require_admin_access

router = APIRouter(prefix="/admin/access", tags=["acceso administrativo"])

@router.get("/verify")
def verify_admin_access(identity: AdminIdentity = Depends(require_admin_access)):
    return {"ok": True, "actor": identity.actor}
