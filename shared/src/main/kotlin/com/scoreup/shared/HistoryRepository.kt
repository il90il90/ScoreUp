package com.scoreup.shared

import android.util.AtomicFile
import com.scoreup.core.HistoryMerge
import com.scoreup.core.MatchSummary
import com.scoreup.core.SyncCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** Locally saved match history / saved scores (newest first), persisted atomically as JSON. Used by phone and watch. */
class HistoryRepository(dir: File) {
    private val file = AtomicFile(File(dir, "history.json"))
    private val serializer = ListSerializer(MatchSummary.serializer())
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<MatchSummary>> = _items.asStateFlow()

    private fun load(): List<MatchSummary> = runCatching {
        SyncCodec.json.decodeFromString(serializer, file.readFully().decodeToString())
    }.getOrDefault(emptyList())

    @Synchronized fun add(item: MatchSummary) = save(HistoryMerge.upsert(_items.value, item))
    @Synchronized fun delete(matchId: String) = save(_items.value.filter { it.matchId != matchId })
    @Synchronized fun clear() = save(emptyList())

    private fun save(list: List<MatchSummary>) {
        _items.value = list
        val out = file.startWrite()
        try {
            out.write(SyncCodec.json.encodeToString(serializer, list).encodeToByteArray())
            file.finishWrite(out)
        } catch (t: Throwable) { file.failWrite(out) }
    }
}
