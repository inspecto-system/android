package dev.inspecto.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** Application entry point; sets up dependency injection (Hilt). */
@HiltAndroidApp
class InspectoApp : Application()
