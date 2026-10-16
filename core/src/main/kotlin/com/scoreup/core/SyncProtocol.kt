package com.scoreup.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Wire protocol between phone and watch. Transport-agnostic: any lossy, reorderable,
 * duplicating channel is fine because every message is idempotent.
 *
 *  - [Summary] advertises "what I have" (active header + per-device contiguous seq vector).
 *  - [Events] carries events the peer is missing (or a fresh local event, eagerly).
 */
@Serializable
sealed interface SyncMessage {
    val from: String

    @Serializable @SerialName("summary")
    data class Summary(
        override val from: String,
        val header: MatchHeader,
        val vector: Map<String, Long>,
        val appVersionCode: Long = 0,
        /** True when the sender wants an immediate summary back (handshake). */
        val reply: Boolean = false,
    ) : SyncMessage

    @Serializable @SerialName("events")
    data class Events(
        override val from: String,
        val header: MatchHeader,
        val events: List<MatchEvent>,
    ) : SyncMessage
}

object SyncCodec {
    val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "t" }

    fun encode(m: SyncMessage): ByteArray = json.encodeToString(SyncMessage.serializer(), m).encodeToByteArray()

    /** Returns null for malformed / incompatible payloads instead of crashing the listener. */
    fun decode(bytes: ByteArray): SyncMessage? =
        runCatching { json.decodeFromString(SyncMessage.serializer(), bytes.decodeToString()) }.getOrNull()
}
