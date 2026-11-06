package dev.inspecto.android.feature.drafts

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.inspecto.android.feature.photos.PhotoUploader
import dev.inspecto.android.feature.photos.QueueResult
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Background pass over the offline queue: first the changes of inspections, then the photos (a photo
 * of a defect created without a network becomes ready once the defect exists on the server). It runs
 * only with a network and is repeated with growing delays while the server is unreachable, also after
 * the app is closed or the phone restarts.
 */
@HiltWorker
class SyncWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val drafts: DraftSender,
        private val photos: PhotoUploader,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val changes = drafts.sendReady()
            val sent = photos.sendReady()
            return if (changes == QueueResult.DONE && sent == QueueResult.DONE) Result.success() else Result.retry()
        }

        companion object {
            private const val NAME = "sync"
            private const val FIRST_RETRY_SECONDS = 10L

            /**
             * Schedules a pass. A pass already running is followed by a new one, so a change or photo
             * added during it is not missed.
             */
            fun schedule(workManager: WorkManager) {
                val request =
                    OneTimeWorkRequestBuilder<SyncWorker>()
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, FIRST_RETRY_SECONDS, TimeUnit.SECONDS)
                        .build()
                workManager.enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
            }
        }
    }

/** Schedules a pass of [SyncWorker]; a separate class so that queues can be tested on the JVM. */
class SyncScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun schedule() = SyncWorker.schedule(WorkManager.getInstance(context))
    }
