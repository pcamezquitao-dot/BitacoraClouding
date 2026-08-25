"""Comprobaciones destructivas-controladas de C20B sobre el ambiente de desarrollo."""

import json
import sys
import urllib.error
import urllib.request
from uuid import UUID

from sqlalchemy import text

from app.core.config import settings
from app.core.db import SessionLocal
from app.core.supervisor_auth import SupervisorIdentity, issue_supervisor_token
from app.services import control_supervisor_service as service


def expired_token_check() -> None:
    token, _ = issue_supervisor_token(2, "P0002", [2], now=1)
    request = urllib.request.Request(
        "http://127.0.0.1:8001/control/supervisor/me?anio=2026&mes=8",
        headers={"Authorization": f"Bearer {token}"},
    )
    try:
        urllib.request.urlopen(request, timeout=10)
        raise AssertionError("El token vencido fue aceptado")
    except urllib.error.HTTPError as error:
        assert error.code == 401, error.code
        print(json.dumps({"expired_token_status": error.code}))


def non_supervisor_token_check() -> None:
    token, _ = issue_supervisor_token(100, "P0100", [2])
    request = urllib.request.Request(
        "http://127.0.0.1:8001/control/supervisor/me?anio=2026&mes=8",
        headers={"Authorization": f"Bearer {token}"},
    )
    try:
        urllib.request.urlopen(request, timeout=10)
        raise AssertionError("El participante sin cargo supervisor fue aceptado")
    except urllib.error.HTTPError as error:
        assert error.code == 403, error.code
        print(json.dumps({"non_supervisor_token_status": error.code}))


def rollback_check(bitacora_id: int, expected: str) -> None:
    db = SessionLocal()
    try:
        before = db.execute(
            text(f"SELECT observaciones FROM {settings.BITACORA_DIARIA_TABLE} WHERE id_bitacora=:id"),
            {"id": bitacora_id},
        ).scalar_one()
        assert before == expected
        evidence_before = db.execute(
            text(f"SELECT COUNT(*) FROM {settings.BAE_TABLE}")
        ).scalar_one()
        duplicate = db.execute(
            text(f"SELECT uuid_cliente FROM {settings.BAE_TABLE} ORDER BY id_evidencia LIMIT 1")
        ).scalar_one()
        original_uuid4 = service.uuid4
        service.uuid4 = lambda: UUID(str(duplicate))
        failed = False
        try:
            service.update_control_observation(
                db,
                SupervisorIdentity(2, "P0002", (2,)),
                bitacora_id,
                expected,
                "C20B_NO_DEBE_PERSISTIR_POR_ROLLBACK",
            )
            db.commit()
        except Exception:
            failed = True
            db.rollback()
        finally:
            service.uuid4 = original_uuid4
        after = db.execute(
            text(f"SELECT observaciones FROM {settings.BITACORA_DIARIA_TABLE} WHERE id_bitacora=:id"),
            {"id": bitacora_id},
        ).scalar_one()
        evidence_after = db.execute(
            text(f"SELECT COUNT(*) FROM {settings.BAE_TABLE}")
        ).scalar_one()
        assert failed
        assert after == before
        assert evidence_after == evidence_before
        print(json.dumps({
            "rollback_triggered": failed,
            "observation_unchanged": after == before,
            "evidence_before": evidence_before,
            "evidence_after": evidence_after,
        }))
    finally:
        db.close()


if __name__ == "__main__":
    if sys.argv[1:] == ["expired"]:
        expired_token_check()
    elif sys.argv[1:] == ["non-supervisor"]:
        non_supervisor_token_check()
    elif len(sys.argv) == 4 and sys.argv[1] == "rollback":
        rollback_check(int(sys.argv[2]), sys.argv[3])
    else:
        raise SystemExit("Uso: expired | rollback ID OBSERVACION")
