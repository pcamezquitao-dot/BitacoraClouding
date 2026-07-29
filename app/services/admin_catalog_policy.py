from datetime import date
import re
from typing import Iterable


EMPLOYEE_CAPABILITY = "EMPLEADO"
SUPERVISOR_CAPABILITY = "SUPERVISOR"
MANAGER_CAPABILITY = "GERENTE"

_LEGACY_CAPABILITIES = {
    1: {EMPLOYEE_CAPABILITY},
    2: {EMPLOYEE_CAPABILITY},
    3: {SUPERVISOR_CAPABILITY},
}


def legacy_capabilities(type_code: int) -> set[str]:
    return set(_LEGACY_CAPABILITIES.get(type_code, set()))


def normalize_type_description(value: str) -> str:
    normalized = re.sub(r"\s+", " ", value or "").strip()
    if not normalized:
        raise ValueError("La descripción del tipo es obligatoria")
    if len(normalized) > 100:
        raise ValueError("La descripción del tipo no puede superar 100 caracteres")
    return normalized


def validate_capabilities(
    values: Iterable[str],
    active_capabilities: set[str],
) -> set[str]:
    normalized = {
        value.strip().upper()
        for value in values
        if value and value.strip()
    }
    if not normalized:
        raise ValueError("El tipo requiere al menos una capacidad")
    inactive = normalized - active_capabilities
    if inactive:
        raise ValueError(
            "La capacidad no existe o está inactiva: "
            + ", ".join(sorted(inactive))
        )
    return normalized


def validate_assignment_period(
    start_date: date | None,
    end_date: date | None,
) -> None:
    if start_date is None:
        raise ValueError("La fecha inicial es obligatoria")
    if end_date is not None and end_date < start_date:
        raise ValueError("La fecha final no puede ser anterior a la fecha inicial")
