package com.sentinel.app.core

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Clase Application de Sentinel.
 * El punto de entrada de Hilt y la inicialización de Timber.
 */
@HiltAndroidApp
class SentinelApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Logging en debug builds únicamente
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
