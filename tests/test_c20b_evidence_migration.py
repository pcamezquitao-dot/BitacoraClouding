from pathlib import Path


def test_forward_migration_is_additive_nullable_and_uses_real_participant_type():
    sql = Path("migrations/20260822_c20b_evidence_supervisor_actor.sql").read_text("utf-8")
    normalized = " ".join(sql.upper().split())
    assert "ADD COLUMN ID_SUPERVISOR_ACTOR INT(11) NULL" in normalized
    assert "FOREIGN KEY (ID_SUPERVISOR_ACTOR)" in normalized
    assert "REFERENCES PARTICIPANTE (ID_PARTICIPANTE)" in normalized
    assert "UPDATE BITACORA_AREA_EVIDENCIA" not in normalized
    assert "DROP TABLE" not in normalized


def test_rollback_removes_only_added_constraint_index_and_column():
    sql = Path("migrations/20260822_c20b_evidence_supervisor_actor_rollback.sql").read_text("utf-8")
    normalized = " ".join(sql.upper().split())
    assert "DROP FOREIGN KEY FK_BAE_SUPERVISOR_ACTOR" in normalized
    assert "DROP INDEX IDX_BAE_SUPERVISOR_ACTOR" in normalized
    assert "DROP COLUMN ID_SUPERVISOR_ACTOR" in normalized
    assert "DELETE FROM" not in normalized
    assert "DROP TABLE" not in normalized
