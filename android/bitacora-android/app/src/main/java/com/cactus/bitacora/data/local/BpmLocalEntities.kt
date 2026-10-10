package com.cactus.bitacora.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

internal object BpmSyncState {
    const val LOCAL_PENDING = "LOCAL_PENDING"
    const val SENDING = "SENDING"
    const val CONFIRMED = "CONFIRMED"
    const val CONFLICT = "CONFLICT"
    const val REJECTED = "REJECTED"
    const val STALE = "STALE"
}

@Entity(tableName = "bpm_definitions_local", indices = [Index("active")])
data class BpmDefinitionLocalEntity(
    @PrimaryKey val processId: Long,
    val name: String,
    val version: Int,
    val description: String?,
    val sha256: String,
    val definitionJson: String,
    val active: Boolean,
    val syncedAtMillis: Long
)

@Entity(tableName = "bpm_cases_local", indices = [Index("remoteId", unique = true), Index("syncState")])
data class BpmCaseLocalEntity(
    @PrimaryKey val clientUuid: String,
    val remoteId: Long?,
    val processId: Long,
    val definitionSha256: String,
    val subject: String,
    val affectedCode: String,
    val creatorCode: String,
    val sourceType: String,
    val sourceId: String?,
    val state: String,
    val revision: Int,
    val capturedAt: String,
    val confirmedAt: String?,
    val syncState: String,
    val lastError: String?
)

@Entity(
    tableName = "bpm_tasks_local",
    indices = [Index("remoteId", unique = true), Index("caseClientUuid"), Index("responsibleCode"), Index("syncState")]
)
data class BpmTaskLocalEntity(
    @PrimaryKey val clientUuid: String,
    val remoteId: Long?,
    val caseClientUuid: String,
    val processId: Long,
    val stageId: Int,
    val stageName: String,
    val responsibleCode: String,
    val state: String,
    val revision: Int,
    val result: String?,
    val observations: String?,
    val dueAt: String?,
    val provisional: Boolean,
    val syncState: String,
    val lastError: String?
)

@Entity(
    tableName = "bpm_operations_local",
    indices = [Index("sequence"), Index("dependencyUuid"), Index("syncState")]
)
data class BpmOperationLocalEntity(
    @PrimaryKey val operationUuid: String,
    val sequence: Long,
    val dependencyUuid: String?,
    val operationType: String,
    val actorCode: String,
    val device: String,
    val caseClientUuid: String?,
    val taskClientUuid: String?,
    val successorClientUuid: String?,
    val baseRevision: Int?,
    val definitionSha256: String?,
    val capturedAt: String,
    val payloadJson: String,
    val syncState: String,
    val serverResultJson: String?,
    val lastError: String?,
    val attempts: Int
)

@Entity(tableName = "bpm_evidences_local", indices = [Index("taskClientUuid"), Index("syncState")])
data class BpmEvidenceLocalEntity(
    @PrimaryKey val clientUuid: String,
    val caseClientUuid: String,
    val taskClientUuid: String,
    val filename: String,
    val mimeType: String,
    val sha256: String,
    val contentBase64: String,
    val capturedAt: String,
    val syncState: String,
    val lastError: String?
)

@Dao
interface BpmLocalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putDefinitions(values: List<BpmDefinitionLocalEntity>)

    @Query("SELECT * FROM bpm_definitions_local WHERE active=1 ORDER BY name,version")
    suspend fun definitions(): List<BpmDefinitionLocalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCase(value: BpmCaseLocalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putTasks(values: List<BpmTaskLocalEntity>)

    @Query("SELECT * FROM bpm_cases_local WHERE clientUuid=:uuid")
    suspend fun caseByUuid(uuid: String): BpmCaseLocalEntity?

    @Query("SELECT DISTINCT c.* FROM bpm_cases_local c LEFT JOIN bpm_tasks_local t ON t.caseClientUuid=c.clientUuid WHERE c.creatorCode=:actor OR t.responsibleCode=:actor ORDER BY c.capturedAt DESC")
    suspend fun cases(actor: String): List<BpmCaseLocalEntity>

    @Query("SELECT * FROM bpm_tasks_local WHERE clientUuid=:uuid")
    suspend fun taskByUuid(uuid: String): BpmTaskLocalEntity?

    @Query("SELECT * FROM bpm_tasks_local WHERE responsibleCode=:actor ORDER BY CASE syncState WHEN 'LOCAL_PENDING' THEN 0 WHEN 'CONFLICT' THEN 1 WHEN 'REJECTED' THEN 2 ELSE 3 END, CASE state WHEN 'PENDIENTE' THEN 0 WHEN 'EN_EJECUCION' THEN 1 ELSE 2 END, dueAt IS NULL,dueAt,clientUuid")
    suspend fun tasks(actor: String): List<BpmTaskLocalEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun putOperation(value: BpmOperationLocalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putEvidence(value: BpmEvidenceLocalEntity)

    @Query("SELECT * FROM bpm_evidences_local WHERE taskClientUuid=:taskUuid ORDER BY capturedAt")
    suspend fun evidences(taskUuid: String): List<BpmEvidenceLocalEntity>

    @Query("UPDATE bpm_evidences_local SET syncState=:state,lastError=:error WHERE taskClientUuid=:taskUuid")
    suspend fun updateEvidenceSyncState(taskUuid: String, state: String, error: String?)

    @Query("UPDATE bpm_evidences_local SET syncState='CONFIRMED',lastError=NULL WHERE EXISTS (SELECT 1 FROM bpm_operations_local o WHERE o.taskClientUuid=bpm_evidences_local.taskClientUuid AND o.operationType='COMPLETE_TASK' AND o.syncState='CONFIRMED')")
    suspend fun reconcileConfirmedEvidences()

    @Query("SELECT * FROM bpm_operations_local WHERE syncState IN ('LOCAL_PENDING','SENDING') ORDER BY sequence")
    suspend fun pendingOperations(): List<BpmOperationLocalEntity>

    @Query("SELECT * FROM bpm_operations_local WHERE operationUuid=:uuid")
    suspend fun operation(uuid: String): BpmOperationLocalEntity?

    @Query("UPDATE bpm_operations_local SET syncState=:state,serverResultJson=:result,lastError=:error,attempts=attempts+1 WHERE operationUuid=:uuid")
    suspend fun updateOperation(uuid: String, state: String, result: String?, error: String?)

    @Query("UPDATE bpm_cases_local SET remoteId=:remoteId,state=:state,revision=:revision,confirmedAt=:confirmedAt,syncState=:syncState,lastError=:error WHERE clientUuid=:uuid")
    suspend fun confirmCase(uuid: String, remoteId: Long?, state: String, revision: Int, confirmedAt: String?, syncState: String, error: String?)

    @Query("UPDATE bpm_cases_local SET state=:state,revision=:revision,syncState=:syncState,lastError=:error WHERE clientUuid=:uuid")
    suspend fun updateLocalCase(uuid: String, state: String, revision: Int, syncState: String, error: String?)

    @Query("UPDATE bpm_tasks_local SET state='CANCELADA',revision=revision+1,syncState=:syncState WHERE caseClientUuid=:caseUuid AND state IN ('PENDIENTE','EN_EJECUCION')")
    suspend fun cancelLocalTasks(caseUuid: String, syncState: String)

    @Query("UPDATE bpm_tasks_local SET remoteId=:remoteId,state=:state,revision=:revision,provisional=:provisional,syncState=:syncState,lastError=:error WHERE clientUuid=:uuid")
    suspend fun confirmTask(uuid: String, remoteId: Long?, state: String, revision: Int, provisional: Boolean, syncState: String, error: String?)

    @Query("UPDATE bpm_tasks_local SET state=:state,revision=:revision,result=:result,observations=:observations,provisional=:provisional,syncState=:syncState,lastError=:error WHERE clientUuid=:uuid")
    suspend fun updateLocalTask(uuid: String, state: String, revision: Int, result: String?, observations: String?, provisional: Boolean, syncState: String, error: String?)

    @Query("UPDATE bpm_tasks_local SET responsibleCode=:responsible,revision=revision+1,syncState=:syncState,lastError=NULL WHERE clientUuid=:uuid")
    suspend fun reassignLocalTask(uuid: String, responsible: String, syncState: String)
}
