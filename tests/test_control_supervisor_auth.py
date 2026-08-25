import base64
import hashlib
import hmac
import json
import time

import pytest
from fastapi import HTTPException
from fastapi.security import HTTPAuthorizationCredentials

from app.core.config import settings
from app.core.supervisor_auth import issue_supervisor_token, require_supervisor_identity
from app.routers import control_supervisor as router
from app.schemas.control_supervisor import ControlSupervisorSessionIn
from app.services.supervisor_service import SupervisorAuthorizationError


@pytest.fixture(autouse=True)
def dev_auth(monkeypatch):
    monkeypatch.setattr(settings, "APP_ENV", "test")
    monkeypatch.setattr(settings, "SUPERVISOR_DEV_AUTH_ENABLED", True)
    monkeypatch.setattr(settings, "SUPERVISOR_TOKEN_SECRET", "s" * 40)
    monkeypatch.setattr(settings, "SUPERVISOR_TOKEN_TTL_SECONDS", 600)


def credentials(token):
    return HTTPAuthorizationCredentials(scheme="Bearer", credentials=token)


def test_valid_token_contains_identity_role_and_max_ten_minutes():
    now = int(time.time())
    token, expires = issue_supervisor_token(2, "P0002", [7, 8], now)
    identity = require_supervisor_identity(credentials(token))
    assert identity.participant_id == 2
    assert identity.code == "P0002"
    assert identity.role == "supervisor"
    assert identity.area_ids == (7, 8)
    assert expires - now == 600


def test_missing_altered_and_expired_token_return_401():
    with pytest.raises(HTTPException) as missing:
        require_supervisor_identity(None)
    assert missing.value.status_code == 401
    token, _ = issue_supervisor_token(2, "P0002", [7])
    with pytest.raises(HTTPException) as altered:
        require_supervisor_identity(credentials(token[:-1] + ("a" if token[-1] != "a" else "b")))
    assert altered.value.status_code == 401
    expired, _ = issue_supervisor_token(2, "P0002", [7], 1)
    with pytest.raises(HTTPException) as old:
        require_supervisor_identity(credentials(expired))
    assert old.value.status_code == 401


def test_development_session_is_blocked_outside_development(monkeypatch):
    monkeypatch.setattr(settings, "SUPERVISOR_DEV_AUTH_ENABLED", False)
    with pytest.raises(HTTPException) as error:
        issue_supervisor_token(2, "P0002", [7])
    assert error.value.status_code == 404


def test_code_session_cannot_be_enabled_in_production_by_one_flag(monkeypatch):
    monkeypatch.setattr(settings, "APP_ENV", "production")
    monkeypatch.setattr(settings, "SUPERVISOR_DEV_AUTH_ENABLED", True)
    with pytest.raises(HTTPException) as error:
        issue_supervisor_token(2, "P0002", [7])
    assert error.value.status_code == 404


def test_token_with_another_role_is_rejected():
    now = int(time.time())
    header = base64.urlsafe_b64encode(b'{"alg":"HS256","typ":"JWT"}').rstrip(b"=").decode()
    payload = base64.urlsafe_b64encode(json.dumps({
        "sub": "2", "codigo": "P0002", "rol": "administrador",
        "iat": now, "exp": now + 600, "areas": [7],
    }, separators=(",", ":")).encode()).rstrip(b"=").decode()
    signature = base64.urlsafe_b64encode(
        hmac.new(("s" * 40).encode(), f"{header}.{payload}".encode(), hashlib.sha256).digest()
    ).rstrip(b"=").decode()
    with pytest.raises(HTTPException) as error:
        require_supervisor_identity(credentials(f"{header}.{payload}.{signature}"))
    assert error.value.status_code == 401


@pytest.mark.parametrize(("code", "participant_id", "area"), [
    ("P0002", 2, 7), ("P0003", 3, 8),
])
def test_session_is_generic_for_valid_supervisors(monkeypatch, code, participant_id, area):
    monkeypatch.setattr(router, "identify_supervisor", lambda _db, requested: {
        "id_supervisor": participant_id, "codigo": requested,
        "nombre_completo": "Supervisor", "estado": "Supervisor identificado",
        "areas": [{"id_area": area, "area": "Area"}],
    })
    response = router.crear_sesion(ControlSupervisorSessionIn(codigo=code), object())
    identity = require_supervisor_identity(credentials(response["access_token"]))
    assert identity.participant_id == participant_id
    assert identity.code == code and identity.area_ids == (area,)


def test_session_rejects_participant_without_supervisor_role(monkeypatch):
    monkeypatch.setattr(router, "identify_supervisor", lambda *_args: (_ for _ in ()).throw(
        SupervisorAuthorizationError("no es supervisor")
    ))
    with pytest.raises(HTTPException) as error:
        router.crear_sesion(ControlSupervisorSessionIn(codigo="P0015"), object())
    assert error.value.status_code == 403
