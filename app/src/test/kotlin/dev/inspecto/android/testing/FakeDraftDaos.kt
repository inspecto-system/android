package dev.inspecto.android.testing

import dev.inspecto.android.core.db.CachedJson
import dev.inspecto.android.core.db.CachedJsonDao
import dev.inspecto.android.core.db.PendingAction
import dev.inspecto.android.core.db.PendingActionDao
import dev.inspecto.android.core.network.NetworkModule
import dev.inspecto.android.feature.drafts.DraftNotices
import dev.inspecto.android.feature.drafts.DraftRepository
import dev.inspecto.android.feature.drafts.SyncScheduler
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory queue of changes with the semantics of the Room queries. */
class FakePendingActionDao : PendingActionDao {
    val rows = MutableStateFlow<List<PendingAction>>(emptyList())
    private var nextSeq = 1L

    override suspend fun insert(action: PendingAction) {
        rows.value += action.copy(seq = nextSeq++)
    }

    override suspend fun deleteTarget(
        inspectionId: String,
        type: String,
        targetId: String,
    ) {
        rows.value =
            rows.value.filterNot { it.inspectionId == inspectionId && it.type == type && it.targetId == targetId }
    }

    override suspend fun next(): PendingAction? = rows.value.filter { it.errorCode == null }.minByOrNull { it.seq }

    override fun observeInspection(inspectionId: String): Flow<List<PendingAction>> =
        rows.map { all -> all.filter { it.inspectionId == inspectionId }.sortedBy { it.seq } }

    override suspend fun ofInspection(inspectionId: String): List<PendingAction> =
        rows.value.filter { it.inspectionId == inspectionId }.sortedBy { it.seq }

    override suspend fun setError(
        seq: Long,
        errorCode: String,
    ) {
        rows.value = rows.value.map { if (it.seq == seq) it.copy(errorCode = errorCode) else it }
    }

    override suspend fun delete(seq: Long) {
        rows.value = rows.value.filterNot { it.seq == seq }
    }

    override suspend fun deleteInspection(inspectionId: String): Int {
        val count = rows.value.count { it.inspectionId == inspectionId }
        rows.value = rows.value.filterNot { it.inspectionId == inspectionId }
        return count
    }

    override suspend fun deleteFailed(inspectionId: String) {
        rows.value = rows.value.filterNot { it.inspectionId == inspectionId && it.errorCode != null }
    }
}

class FakeCachedJsonDao : CachedJsonDao {
    val rows = MutableStateFlow<Map<String, CachedJson>>(emptyMap())

    override suspend fun put(entry: CachedJson) {
        rows.value += entry.key to entry
    }

    override suspend fun get(key: String): CachedJson? = rows.value[key]

    override fun observe(key: String): Flow<CachedJson?> = rows.map { it[key] }

    override suspend fun delete(key: String) {
        rows.value -= key
    }
}

/** Real draft storage over in-memory tables; scheduling of the background pass is mocked. */
class TestDrafts {
    val actions = FakePendingActionDao()
    val cache = FakeCachedJsonDao()
    val scheduler = mockk<SyncScheduler>(relaxed = true)
    val repository = DraftRepository(scheduler, actions, cache, NetworkModule.provideJson())
    val notices = DraftNotices(cache)
}
