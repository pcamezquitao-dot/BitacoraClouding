"""Idempotencia, concurrencia, permisos y rollback tardío en bitacora_c32_test."""
from concurrent.futures import ThreadPoolExecutor
from integration_c32_isolated import evidence, op, request, send
from uuid import uuid4


def definition():
    value = next(x for x in request("bpm/definitions") if x["nombre"] == "CP_C32_INASISTENCIA")
    return value["id_bpm_proceso"], value["sha256"]


def open_started(label):
    process_id, sha = definition()
    case_uuid, task_uuid = str(uuid4()), str(uuid4())
    opened = send(op("OPEN_CASE", "P0001", process_id=process_id, definition_sha256=sha,
                     case_client_uuid=case_uuid, task_client_uuid=task_uuid,
                     subject=f"CP_C32 {label}", affected_code="CP_C32_EMP", source_type="MANUAL"))
    send(op("START_TASK", "CP_C32_EMP", case_uuid, task_uuid, 1,
            process_id=process_id, definition_sha256=sha))
    return process_id, sha, case_uuid, task_uuid, opened["case_id"]


def run():
    process_id, sha, case_uuid, task_uuid, case_id = open_started("sin soporte")
    missing = op("COMPLETE_TASK", "CP_C32_EMP", case_uuid, task_uuid, 2,
                 process_id=process_id, definition_sha256=sha,
                 successor_client_uuid=str(uuid4()), result="SOPORTE_PRESENTADO")
    request("bpm/operations", missing, 422)
    detail = request(f"bpm/cases/{case_id}?actor=CP_C32_EMP")
    assert detail["tasks"][0]["estado"] == "EN_EJECUCION"
    assert detail["tasks"][0]["id_bitacora"] is None

    denied = op("START_TASK", "CP_C32_RH", case_uuid, task_uuid, 2,
                process_id=process_id, definition_sha256=sha)
    request("bpm/operations", denied, 403)

    # Fallo tardío: UUID sucesor ya asociado en otra ejecución. Debe revertir
    # evidencia, Bitácora, finalización y tarea sucesora.
    duplicate_successor = "c32-task-00000000-0000-000000000002"
    late = op("COMPLETE_TASK", "CP_C32_EMP", case_uuid, task_uuid, 2,
              process_id=process_id, definition_sha256=sha,
              successor_client_uuid=duplicate_successor, result="SOPORTE_PRESENTADO",
              evidences=[evidence("rollback")])
    request("bpm/operations", late, 409)
    detail = request(f"bpm/cases/{case_id}?actor=CP_C32_EMP")
    assert len(detail["tasks"]) == 1 and detail["tasks"][0]["estado"] == "EN_EJECUCION"
    assert not detail["evidences"] and detail["tasks"][0]["id_bitacora"] is None

    # Construir una revisión RH y enviar dos decisiones simultáneas.
    successor = str(uuid4())
    send(op("COMPLETE_TASK", "CP_C32_EMP", case_uuid, task_uuid, 2,
            process_id=process_id, definition_sha256=sha,
            successor_client_uuid=successor, result="SOPORTE_PRESENTADO",
            evidences=[evidence("concurrencia")]))
    send(op("START_TASK", "CP_C32_RH", case_uuid, successor, 1,
            process_id=process_id, definition_sha256=sha))
    a = op("COMPLETE_TASK", "CP_C32_RH", case_uuid, successor, 2,
           process_id=process_id, definition_sha256=sha,
           successor_client_uuid=str(uuid4()), result="ACEPTADO")
    b = op("COMPLETE_TASK", "CP_C32_RH", case_uuid, successor, 2,
           process_id=process_id, definition_sha256=sha,
           successor_client_uuid=str(uuid4()), result="REQUIERE_CORRECCION")

    def raw(payload):
        try:
            return 200, send(payload)
        except AssertionError as error:
            return "conflict", str(error)

    with ThreadPoolExecutor(max_workers=2) as pool:
        results = list(pool.map(raw, (a, b)))
    assert sum(1 for status, _ in results if status == 200) == 1, results
    detail = request(f"bpm/cases/{case_id}?actor=CP_C32_RH")
    successors = [x for x in detail["tasks"] if x["id_actividad"] != detail["tasks"][0]["id_actividad"] and x["estado"] == "PENDIENTE"]
    assert len(successors) == 1, detail["tasks"]

    # Administración auditable y revisión optimista.
    _, _, admin_case_uuid, admin_task_uuid, admin_case_id = open_started("administración")
    admin_detail = request(f"bpm/cases/{admin_case_id}?actor=P0001")
    revision = admin_detail["case"]["revision"]
    reassigned = send(op("REASSIGN_TASK", "P0001", base_revision=revision,
                         case_id=admin_case_id,
                         task_id=admin_detail["tasks"][0]["id_actividad"],
                         responsible_id=93201, reason="CP_C32 reasignación controlada"))
    revision = reassigned["case_revision"]
    denied_admin = op("SUSPEND_CASE", "CP_C32_EMP", base_revision=revision,
                      case_id=admin_case_id, reason="CP_C32 sin permiso")
    request("bpm/operations", denied_admin, 403)
    suspended = send(op("SUSPEND_CASE", "P0001", base_revision=revision,
                        case_id=admin_case_id, reason="CP_C32 suspensión controlada"))
    assert suspended["case_state"] == "SUSPENDIDO"
    stale = op("RESUME_CASE", "P0001", base_revision=revision,
               case_id=admin_case_id, reason="CP_C32 revisión obsoleta")
    request("bpm/operations", stale, 409)
    resumed = send(op("RESUME_CASE", "P0001", base_revision=suspended["case_revision"],
                      case_id=admin_case_id, reason="CP_C32 reanudación controlada"))
    cancelled = send(op("CANCEL_CASE", "P0001", base_revision=resumed["case_revision"],
                        case_id=admin_case_id, reason="CP_C32 cancelación controlada"))
    assert cancelled["case_state"] == "CANCELADO"
    admin_detail = request(f"bpm/cases/{admin_case_id}?actor=P0001")
    assert admin_detail["tasks"][0]["estado"] == "CANCELADA"
    assert [x["evento"] for x in admin_detail["history"]][-4:] == [
        "REASSIGN_TASK", "SUSPEND_CASE", "RESUME_CASE", "CANCEL_CASE"
    ]

    # El servidor también exige la dependencia causal declarada por el cliente.
    process_id, sha = definition()
    causal_case, causal_task = str(uuid4()), str(uuid4())
    causal_open = op("OPEN_CASE", "P0001", process_id=process_id, definition_sha256=sha,
                     case_client_uuid=causal_case, task_client_uuid=causal_task,
                     subject="CP_C32 causal", affected_code="CP_C32_EMP", source_type="MANUAL")
    send(causal_open)
    causal_start = op("START_TASK", "CP_C32_EMP", causal_case, causal_task, 1,
                      process_id=process_id, definition_sha256=sha,
                      dependency_uuid=causal_open["operation_uuid"])
    assert send(causal_start)["task_state"] == "EN_EJECUCION"

    missing_case, missing_task = str(uuid4()), str(uuid4())
    missing_open = op("OPEN_CASE", "P0001", process_id=process_id, definition_sha256=sha,
                      case_client_uuid=missing_case, task_client_uuid=missing_task,
                      subject="CP_C32 dependencia ausente", affected_code="CP_C32_EMP", source_type="MANUAL")
    send(missing_open)
    missing_dependency = op("START_TASK", "CP_C32_EMP", missing_case, missing_task, 1,
                            process_id=process_id, definition_sha256=sha,
                            dependency_uuid=str(uuid4()))
    request("bpm/operations", missing_dependency, 409)
    print({"permissions": "APPROVED", "missing_support": "APPROVED",
           "late_rollback": "APPROVED", "concurrency": "APPROVED",
           "administration": "APPROVED", "stale_revision": "APPROVED",
           "causal_dependency": "APPROVED"})


if __name__ == "__main__":
    run()
