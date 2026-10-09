from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def test_cu30_migration_uses_own_tables_and_restricts_history_deletion():
    sql = (ROOT / "migrations/20261008_cu30_normaliza_proceso.sql").read_text(encoding="utf-8").lower()
    assert "create table if not exists cu30_trabajo" in sql
    assert "create table if not exists cu30_version" in sql
    assert "create table if not exists cu30_auditoria" in sql
    assert sql.count("on delete restrict") >= 2
    assert "create table cat_actividad" not in sql
    assert "alter table cat_actividad" not in sql
    assert "dim_proceso" not in sql


def test_cu30_service_never_writes_catalog_or_bpm_tables():
    source = (ROOT / "app/services/normaliza_proceso_service.py").read_text(encoding="utf-8").lower()
    forbidden = (
        "insert into cat_actividad", "update cat_actividad", "delete from cat_actividad",
        "insert into dim_proceso", "update dim_proceso", "delete from dim_proceso",
        "insert into bpm_", "update bpm_", "delete from bpm_",
    )
    assert not any(value in source for value in forbidden)
    assert "select id_actividad,nombre,descripcion from cat_actividad" in source


def test_cu30_router_requires_admin_access_and_does_not_expose_cu31_execution():
    source = (ROOT / "app/routers/normaliza_proceso.py").read_text(encoding="utf-8")
    assert "dependencies=[Depends(require_admin_access)]" in source
    assert "cu31" not in source.lower()
    assert "motor" not in source.lower()


def test_xml_contract_forbids_integration_execution():
    xsd = (ROOT / "docs/contratos/CU30_XML_1.xsd").read_text(encoding="utf-8")
    assert '<xs:enumeration value="false"/>' in xsd
    assert 'name="id_actividad"' in xsd
    assert 'name="codigo_provisional"' in xsd
