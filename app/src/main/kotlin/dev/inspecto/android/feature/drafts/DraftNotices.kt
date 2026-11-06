package dev.inspecto.android.feature.drafts

import dev.inspecto.android.core.db.CachedJson
import dev.inspecto.android.core.db.CachedJsonDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

/**
 * Notice that unsent changes of an inspection were dropped because it was completed on the server
 * (another device or the web): the server wins, and the inspector is told how many changes were lost.
 */
class DraftNotices
    @Inject
    constructor(
        private val cache: CachedJsonDao,
    ) {
        suspend fun completedElsewhere(
            inspectionId: UUID,
            dropped: Int,
        ) = cache.put(CachedJson(key(inspectionId), dropped.toString(), System.currentTimeMillis()))

        /** Number of dropped changes, or null without a notice. */
        fun observe(inspectionId: UUID): Flow<Int?> = cache.observe(key(inspectionId)).map { it?.json?.toIntOrNull() }

        suspend fun dismiss(inspectionId: UUID) = cache.delete(key(inspectionId))

        private fun key(inspectionId: UUID) = "completed-elsewhere:$inspectionId"
    }
