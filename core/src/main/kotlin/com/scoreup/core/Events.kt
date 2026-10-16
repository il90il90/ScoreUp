package com.scoreup.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Globally unique event id: the authoring device plus its per-match sequence number (1, 2, 3, ...). */
@Serializable
data class EventId(val device: String, val seq: Long) : Comparable<EventId> {
    override fun compareTo(other: EventId): Int = compareValuesBy(this, other, { it.device }, { it.seq })
    override fun toString() = "$device#$seq"
}

/**
 * An immutable, idempotent match event. Devices exchange events, never derived scores, so
 * re-delivery or replays can never double-count: an event id is applied at most once.
 */
@Serializable
data class MatchEvent(
    val id: EventId,
    /** Lamport timestamp: total, device-independent ordering (ties broken by id). */
    val lamport: Long,
    val wallMs: Long,
    val payload: Payload,
)

@Serializable
sealed interface Payload {
    @Serializable @SerialName("add")
    data class ScoreAdded(val side: Side, val points: Int) : Payload

    @Serializable @SerialName("set")
    data class ScoreSet(val side: Side, val value: Int) : Payload

    /** Revokes a previous score/period event. Idempotent: undoing an id twice is a no-op. */
    @Serializable @SerialName("undo")
    data class Undo(val target: EventId) : Payload

    @Serializable @SerialName("team")
    data class TeamUpdated(val side: Side, val name: String? = null, val color: Long? = null) : Payload

    @Serializable @SerialName("config")
    data class ConfigUpdated(val config: MatchConfig) : Payload

    @Serializable @SerialName("tap")
    data class TapPointsUpdated(val tapPoints: Int) : Payload

    @Serializable @SerialName("clock_start")
    data object ClockStarted : Payload

    @Serializable @SerialName("clock_pause")
    data object ClockPaused : Payload

    @Serializable @SerialName("clock_reset")
    data object ClockReset : Payload

    @Serializable @SerialName("period_next")
    data object PeriodAdvanced : Payload

    @Serializable @SerialName("finish")
    data object Finished : Payload

    @Serializable @SerialName("reopen")
    data object Reopened : Payload
}

/** Identifies the match all devices should be showing. Last-writer-wins by (lamport, device). */
@Serializable
data class MatchHeader(
    val matchId: String,
    val createdAtMs: Long,
    val lamport: Long,
    val device: String,
    val initialConfig: MatchConfig,
) {
    fun isNewerThan(other: MatchHeader?): Boolean =
        other == null || lamport > other.lamport || (lamport == other.lamport && device > other.device) ||
            (lamport == other.lamport && device == other.device && matchId > other.matchId)
}
