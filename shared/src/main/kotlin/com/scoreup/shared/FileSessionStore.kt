package com.scoreup.shared

import android.util.AtomicFile
import com.scoreup.core.SessionSnapshot
import com.scoreup.core.SessionStore
import com.scoreup.core.SyncCodec
import java.io.File

/** Crash-safe JSON persistence (AtomicFile: write-to-temp + rename, with backup recovery). */
class FileSessionStore(file: File) : SessionStore {
    private val atomic = AtomicFile(file)

    override fun load(): SessionSnapshot? = runCatching {
        SyncCodec.json.decodeFromString(SessionSnapshot.serializer(), atomic.readFully().decodeToString())
    }.getOrNull()

    @Synchronized
    override fun save(snapshot: SessionSnapshot) {
        val bytes = SyncCodec.json.encodeToString(SessionSnapshot.serializer(), snapshot).encodeToByteArray()
        val out = atomic.startWrite()
        try {
            out.write(bytes)
            atomic.finishWrite(out)
        } catch (t: Throwable) {
            atomic.failWrite(out)
            throw t
        }
    }
}
