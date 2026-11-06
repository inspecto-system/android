package dev.inspecto.android.core.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * Change made during an inspection that the server does not have yet (T-308). Changes are sent in
 * the order of [seq]; a newer change of the same target replaces the older one.
 *
 * @property type name of `feature.drafts.ActionType`
 * @property targetId what is changed: a checklist item, a defect of an earlier inspection, or the
 *     local key of a new defect (also its `Idempotency-Key`)
 * @property payload JSON of the change
 * @property errorCode API code of a failure that repeating cannot fix; the change waits for the
 *     inspector to discard it
 */
@Entity(tableName = "pending_action", indices = [Index("inspectionId")])
data class PendingAction(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val inspectionId: String,
    val type: String,
    val targetId: String,
    val payload: String,
    val errorCode: String? = null,
    val createdAt: Long,
)

@Dao
interface PendingActionDao {
    @Insert
    suspend fun insert(action: PendingAction)

    @Query("DELETE FROM pending_action WHERE inspectionId = :inspectionId AND type = :type AND targetId = :targetId")
    suspend fun deleteTarget(
        inspectionId: String,
        type: String,
        targetId: String,
    )

    /** Queues a change; an unsent change of the same target is replaced, only the latest counts. */
    @Transaction
    suspend fun replace(action: PendingAction) {
        deleteTarget(action.inspectionId, action.type, action.targetId)
        insert(action)
    }

    /** The oldest change that can be sent now. */
    @Query("SELECT * FROM pending_action WHERE errorCode IS NULL ORDER BY seq LIMIT 1")
    suspend fun next(): PendingAction?

    @Query("SELECT * FROM pending_action WHERE inspectionId = :inspectionId ORDER BY seq")
    fun observeInspection(inspectionId: String): Flow<List<PendingAction>>

    @Query("SELECT * FROM pending_action WHERE inspectionId = :inspectionId ORDER BY seq")
    suspend fun ofInspection(inspectionId: String): List<PendingAction>

    @Query("UPDATE pending_action SET errorCode = :errorCode WHERE seq = :seq")
    suspend fun setError(
        seq: Long,
        errorCode: String,
    )

    @Query("DELETE FROM pending_action WHERE seq = :seq")
    suspend fun delete(seq: Long)

    /** Drops every change of an inspection; returns how many there were. */
    @Query("DELETE FROM pending_action WHERE inspectionId = :inspectionId")
    suspend fun deleteInspection(inspectionId: String): Int

    @Query("DELETE FROM pending_action WHERE inspectionId = :inspectionId AND errorCode IS NOT NULL")
    suspend fun deleteFailed(inspectionId: String)
}

/** Last answer of the server kept for working without a network (snapshots, notices). */
@Entity(tableName = "cached_json")
data class CachedJson(
    @PrimaryKey val key: String,
    val json: String,
    val savedAt: Long,
)

@Dao
interface CachedJsonDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: CachedJson)

    @Query("SELECT * FROM cached_json WHERE `key` = :key")
    suspend fun get(key: String): CachedJson?

    @Query("SELECT * FROM cached_json WHERE `key` = :key")
    fun observe(key: String): Flow<CachedJson?>

    @Query("DELETE FROM cached_json WHERE `key` = :key")
    suspend fun delete(key: String)
}
