package dev.inspecto.android.feature.photos

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
import java.util.concurrent.TimeUnit

/**
 * Background pass over the photo queue. It runs only with a network and is repeated with growing
 * delays while the server is unreachable, also after the app is closed or the phone restarts.
 */
@HiltWorker
class PhotoUploadWorker
    @AssistedInject
    constructor(
        @Assisted context: Context,
        @Assisted params: WorkerParameters,
        private val uploader: PhotoUploader,
    ) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result =
            when (uploader.sendReady()) {
                QueueResult.DONE -> Result.success()
                QueueResult.RETRY -> Result.retry()
            }

        companion object {
            private const val NAME = "photo-upload"
            private const val FIRST_RETRY_SECONDS = 10L

            /**
             * Schedules a pass. A pass already running is followed by a new one, so a photo added
             * during it is not missed.
             */
            fun schedule(workManager: WorkManager) {
                val request =
                    OneTimeWorkRequestBuilder<PhotoUploadWorker>()
                        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, FIRST_RETRY_SECONDS, TimeUnit.SECONDS)
                        .build()
                workManager.enqueueUniqueWork(NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
            }
        }
    }
