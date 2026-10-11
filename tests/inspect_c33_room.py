"""Consulta reproducible y de solo lectura de la copia de Room C33."""
from __future__ import annotations

import sqlite3
import sys

path = sys.argv[1]
connection = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
print("OPERATIONS")
for row in connection.execute(
    "SELECT operationUuid,operationType,syncState,caseClientUuid,taskClientUuid,attempts "
    "FROM bpm_operations_local ORDER BY sequence DESC LIMIT 10"
):
    print(row)
print("TASK_99")
for row in connection.execute(
    "SELECT clientUuid,remoteId,state,revision,syncState,lastError "
    "FROM bpm_tasks_local WHERE remoteId=99"
):
    print(row)
print("NON_CONFIRMED_TASKS")
for row in connection.execute(
    "SELECT clientUuid,remoteId,state,revision,syncState,lastError "
    "FROM bpm_tasks_local WHERE syncState<>'CONFIRMED' ORDER BY remoteId"
):
    print(row)
print("EVIDENCES")
for row in connection.execute(
    "SELECT clientUuid,taskClientUuid,filename,sha256,syncState "
    "FROM bpm_evidences_local ORDER BY capturedAt DESC LIMIT 10"
):
    print(row)
