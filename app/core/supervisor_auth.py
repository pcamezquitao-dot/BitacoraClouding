"""Token corto de CONTROL para desarrollo; no es autenticacion personal definitiva."""

import base64
import hashlib
import hmac
import json
import time
from dataclasses import dataclass

from fastapi import HTTPException, Security, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.core.config import settings


bearer = HTTPBearer(auto_error=False)


@dataclass(frozen=True)
class SupervisorIdentity:
    participant_id: int
    code: str
    area_ids: tuple[int, ...]
    role: str = "supervisor"


def _b64(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def _unb64(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def _secret() -> bytes:
    secret = settings.SUPERVISOR_TOKEN_SECRET.strip()
    if len(secret) < 32:
        raise HTTPException(status_code=503, detail="La sesion de supervisor no esta configurada")
    return secret.encode("utf-8")


def issue_supervisor_token(
    participant_id: int, code: str, area_ids: list[int] | tuple[int, ...], now: int | None = None
) -> tuple[str, int]:
    if (not settings.SUPERVISOR_DEV_AUTH_ENABLED or
            settings.APP_ENV.strip().lower() not in {"development", "dev", "test"}):
        raise HTTPException(status_code=404, detail="Sesion de desarrollo no habilitada")
    issued = int(time.time() if now is None else now)
    ttl = min(max(int(settings.SUPERVISOR_TOKEN_TTL_SECONDS), 1), 600)
    expires = issued + ttl
    header = _b64(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
    payload = _b64(json.dumps({
        "sub": str(participant_id), "codigo": code, "rol": "supervisor",
        "areas": sorted({int(value) for value in area_ids}),
        "iat": issued, "exp": expires,
    }, separators=(",", ":")).encode())
    signature = _b64(hmac.new(_secret(), f"{header}.{payload}".encode(), hashlib.sha256).digest())
    return f"{header}.{payload}.{signature}", expires


def require_supervisor_identity(
    credentials: HTTPAuthorizationCredentials | None = Security(bearer),
) -> SupervisorIdentity:
    if (not settings.SUPERVISOR_DEV_AUTH_ENABLED or
            settings.APP_ENV.strip().lower() not in {"development", "dev", "test"}):
        raise HTTPException(status_code=404, detail="Sesion de desarrollo no habilitada")
    unauthorized = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="Sesion de supervisor ausente, invalida o vencida",
        headers={"WWW-Authenticate": "Bearer"},
    )
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise unauthorized
    try:
        header, payload, signature = credentials.credentials.split(".")
        expected = _b64(hmac.new(_secret(), f"{header}.{payload}".encode(), hashlib.sha256).digest())
        if not hmac.compare_digest(signature, expected):
            raise unauthorized
        claims = json.loads(_unb64(payload))
        if claims.get("rol") != "supervisor" or int(claims["exp"]) <= int(time.time()):
            raise unauthorized
        participant_id = int(claims["sub"])
        code = str(claims["codigo"]).strip().upper()
        if participant_id < 1 or not code:
            raise unauthorized
        area_ids = tuple(sorted({int(value) for value in claims["areas"]}))
        if not area_ids or any(value < 1 for value in area_ids):
            raise unauthorized
        return SupervisorIdentity(participant_id, code, area_ids)
    except HTTPException:
        raise
    except Exception as error:
        raise unauthorized from error
