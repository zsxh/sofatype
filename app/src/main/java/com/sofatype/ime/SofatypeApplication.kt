package com.sofatype.ime

import android.app.Application
import com.sofatype.ime.debug.DebugLogController
import timber.log.Timber

class SofatypeApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())

        DebugLogController.init(this)
    }
}
