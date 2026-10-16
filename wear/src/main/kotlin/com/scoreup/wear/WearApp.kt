package com.scoreup.wear

import android.app.Application
import com.scoreup.shared.ScoreRuntime

class WearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ScoreRuntime.init(this, "watch", BuildConfig.VERSION_CODE.toLong())
    }
}
