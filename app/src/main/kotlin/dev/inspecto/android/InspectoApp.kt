package dev.inspecto.android

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import dev.inspecto.android.feature.photos.PhotoUploadWorker
import javax.inject.Inject

/**
 * Application entry point; sets up dependency injection (Hilt), also for background work:
 * WorkManager is created on first use with this configuration (its start-up initializer is removed
 * in AndroidManifest.xml).
 */
@HiltAndroidApp
class InspectoApp :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Photos left in the queue by an earlier run (app killed, failed pass) are sent again.
        PhotoUploadWorker.schedule(WorkManager.getInstance(this))
    }
}
