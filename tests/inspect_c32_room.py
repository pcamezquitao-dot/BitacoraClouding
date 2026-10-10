"""Read-only evidence extractor for the isolated C32 Room database."""
from __future__ import annotations

import json
import sqlite3
import sys


database = sys.argv[1]
subject_prefix = sys.argv[2] if len(sys.argv) > 2 else "CP_C32_FISICO"
connection = sqlite3.connect(f"file:{database}?mode=ro", uri=True)
connection.row_factory = sqlite3.Row


def rows(sql: str, params: tuple = ()) -> list[dict]:
    return [dict(row) for row in connection.execute(sql, params)]


cases = rows(
    """SELECT clientUuid,remoteId,subject,state,revision,syncState,lastError
       FROM bpm_cases_local WHERE subject LIKE ? ORDER BY capturedAt""",
    (subject_prefix + "%",),
)
case_ids = [case["clientUuid"] for case in cases]
placeholders = ",".join("?" for _ in case_ids) or "NULL"
result = {
    "cases": cases,
    "tasks": rows(
        f"""SELECT clientUuid,remoteId,caseClientUuid,state,revision,provisional,
                    syncState,lastError FROM bpm_tasks_local
             WHERE caseClientUuid IN ({placeholders}) ORDER BY clientUuid""",
        tuple(case_ids),
    ),
    "operations": rows(
        f"""SELECT operationUuid,dependencyUuid,operationType,caseClientUuid,
                    taskClientUuid,syncState,attempts,lastError
             FROM bpm_operations_local WHERE caseClientUuid IN ({placeholders})
             ORDER BY sequence""",
        tuple(case_ids),
    ),
    "evidences": rows(
        f"""SELECT clientUuid,caseClientUuid,taskClientUuid,filename,mimeType,
                    sha256,length(contentBase64) AS base64Length,syncState,lastError
             FROM bpm_evidences_local WHERE caseClientUuid IN ({placeholders})
             ORDER BY clientUuid""",
        tuple(case_ids),
    ),
}
print(json.dumps(result, ensure_ascii=False, indent=2))
