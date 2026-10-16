package com.scoreup.core

import kotlinx.serialization.Serializable

/**
 * The replicated record of one match: a header plus a grow-only set of events keyed by id.
 * Merging is a set union, so it is commutative, associative and idempotent (a CRDT).
 */
@Serializable
data class MatchLog(val header: MatchHeader, val events: Map<String, MatchEvent> = emptyMap()) {

    val matchId: String get() = header.matchId

    fun contains(id: EventId) = id.toString() in events

    /** Adds events belonging to this match; returns the same instance if nothing was new. */
    fun merge(incoming: Collection<MatchEvent>): MatchLog {
        var changed = false
        val next = HashMap(events)
        for (e in incoming) {
            val k = e.id.toString()
            if (k !in next) { next[k] = e; changed = true }
        }
        return if (changed) copy(events = next) else this
    }

    /** Highest contiguous sequence number received from each device (gaps are re-requested). */
    fun vector(): Map<String, Long> {
        val byDevice = events.values.groupBy({ it.id.device }, { it.id.seq })
        return byDevice.mapValues { (_, seqs) ->
            val set = seqs.toHashSet()
            var n = 0L
            while ((n + 1) in set) n++
            n
        }
    }

    /** Events the holder of [peerVector] has not acknowledged yet (includes anything past a gap). */
    fun missingFor(peerVector: Map<String, Long>): List<MatchEvent> =
        events.values.filter { it.id.seq > (peerVector[it.id.device] ?: 0L) }.sortedWith(MatchReducer.ORDER)

    fun maxLamport(): Long = maxOf(header.lamport, events.values.maxOfOrNull { it.lamport } ?: 0L)

    fun nextSeqFor(device: String): Long = (events.values.filter { it.id.device == device }.maxOfOrNull { it.id.seq } ?: 0L) + 1

    fun state(): MatchState = MatchReducer.reduce(header, events.values)
}

/** Persisted per-device state: the active match and the most recent previous logs (for late sync). */
@Serializable
data class SessionSnapshot(
    val active: MatchLog,
    val previous: List<MatchLog> = emptyList(),
    val lamport: Long = 0,
)

/** Durable storage for the session. Implementations must make [save] atomic. */
interface SessionStore {
    fun load(): SessionSnapshot?
    fun save(snapshot: SessionSnapshot)
}

class InMemorySessionStore(var snapshot: SessionSnapshot? = null) : SessionStore {
    var saves = 0
    override fun load() = snapshot
    override fun save(snapshot: SessionSnapshot) { this.snapshot = snapshot; saves++ }
}
