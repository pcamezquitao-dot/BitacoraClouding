from dataclasses import dataclass
import hmac

from fastapi import Header, HTTPException, Security, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.config import settings


bearer = HTTPBearer(auto_error=False)


@dataclass(frozen=True)
class AdminIdentity:
    actor: str
    device: str | None


def require_admin_access(
    credentials: HTTPAuthorizationCredentials | None = Security(bearer),
    actor: str | None = Header(None, alias="X-Admin-Actor"),
    device: str | None = Header(None, alias="X-Admin-Device"),
    access_key: str | None = Header(None, alias="X-Admin-Key"),
) -> AdminIdentity:
    configured = settings.ADMIN_API_TOKEN.strip()
    configured_key = settings.ADMIN_ACCESS_KEY.strip()
    provided = credentials.credentials if credentials else ""
    normalized_actor = (actor or "").strip()
    if (
        not configured
        or not configured_key
        or not provided
        or not hmac.compare_digest(configured, provided)
        or not access_key
        or not hmac.compare_digest(configured_key, access_key.strip())
        or not normalized_actor
    ):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Credenciales administrativas inválidas",
            headers={"WWW-Authenticate": "Bearer"},
        )
    return AdminIdentity(normalized_actor, (device or "").strip() or None)
