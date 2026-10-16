package com.scoreup.shared

import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.scoreup.core.ScoreEngine
import java.io.File

/** Process-wide engine + transport. Both the phone and the watch app initialise it in Application.onCreate. */
class ScoreRuntime private constructor(context: Context, role: String, versionCode: Long) {
    val deviceId: String = DeviceIdentity.get(context, role)
    val engine = ScoreEngine(deviceId, FileSessionStore(File(context.filesDir, "session.json")), appVersionCode = versionCode)
    val sync = WearSync(context, engine)

    companion object {
        @Volatile private var instance: ScoreRuntime? = null

        fun init(context: Context, role: String, versionCode: Long): ScoreRuntime =
            instance ?: synchronized(this) {
                instance ?: ScoreRuntime(context.applicationContext, role, versionCode).also { instance = it; it.sync.start() }
            }

        fun get(): ScoreRuntime = checkNotNull(instance) { "ScoreRuntime.init not called" }

        /** Tests only: drop the process singleton so each test starts from a fresh app. */
        fun resetForTests() { instance?.sync?.stop(); instance = null }
        fun getOrNull(): ScoreRuntime? = instance
    }
}

/** Wakes the app for sync messages while its UI is closed. */
class SyncListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        ScoreRuntime.getOrNull()?.sync?.onMessage(event)
    }

    override fun onDataChanged(events: com.google.android.gms.wearable.DataEventBuffer) {
        ScoreRuntime.getOrNull()?.sync?.onDataChanged(events) ?: events.release()
    }
}
