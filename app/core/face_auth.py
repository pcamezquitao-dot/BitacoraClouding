import hmac

from fastapi import HTTPException, Security, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.config import settings

bearer = HTTPBearer(auto_error=False)


def require_face_sync_access(
    credentials: HTTPAuthorizationCredentials | None = Security(bearer),
) -> str:
    configured = settings.FACE_TEMPLATE_API_TOKEN.strip()
    provided = credentials.credentials if credentials else ""
    if not configured or not provided or not hmac.compare_digest(configured, provided):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Credenciales de sincronización facial inválidas",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return provided
