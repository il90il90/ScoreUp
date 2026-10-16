package com.scoreup.core

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

/**
 * Simulates phone + watch over an unreliable link: drops, duplicates, reordering, offline
 * periods and reconnects. Both replicas must converge with no lost and no double-counted points.
 */
class SyncTest {
    private var now = 10_000L

    private class Link(val rnd: Random) {
        val queue = ArrayList<Pair<ScoreEngine, ByteArray>>()
        var online = true
        var dropRate = 0.0
        var dupRate = 0.0
        fun send(to: ScoreEngine, m: SyncMessage) {
            if (!online || rnd.nextDouble() < dropRate) return
            val bytes = SyncCodec.encode(m)
            queue += to to bytes
            if (rnd.nextDouble() < dupRate) queue += to to bytes
        }
        /** Delivers queued messages in random order until quiet. */
        fun pump(shuffle: Boolean = true, maxSteps: Int = 100_000) {
            var steps = 0
            while (queue.isNotEmpty() && steps++ < maxSteps) {
                val i = if (shuffle) rnd.nextInt(queue.size) else 0
                val (to, bytes) = queue.removeAt(i)
                SyncCodec.decode(bytes)?.let { to.receive(it) }
            }
        }
    }

    private fun pair(seed: Int = 1): Triple<ScoreEngine, ScoreEngine, Link> {
        val link = Link(Random(seed))
        val phone = ScoreEngine("phone", InMemorySessionStore(), clock = { now })
        val watch = ScoreEngine("watch", InMemorySessionStore(), clock = { now })
        phone.outbound = { link.send(watch, it) }
        watch.outbound = { link.send(phone, it) }
        // Handshake: watch adopts phone's match because it was created... whichever header is newer wins.
        watch.broadcastSummary(); link.pump()
        return Triple(phone, watch, link)
    }

    private fun assertConverged(a: ScoreEngine, b: ScoreEngine) {
        val sa = a.state.value; val sb = b.state.value
        assertThat(sb.matchId).isEqualTo(sa.matchId)
        assertThat(sb.home).isEqualTo(sa.home)
        assertThat(sb.away).isEqualTo(sa.away)
        assertThat(sb.config).isEqualTo(sa.config)
        assertThat(sb.sets).isEqualTo(sa.sets)
        assertThat(sb.eventCount).isEqualTo(sa.eventCount)
    }

    @Test fun handshakeAgreesOnOneMatch() {
        val (phone, watch, _) = pair()
        assertThat(watch.state.value.matchId).isEqualTo(phone.state.value.matchId)
    }

    @Test fun realtimeTapsPropagate() {
        val (phone, watch, link) = pair()
        watch.addPoints(Side.HOME, 2); link.pump()
        assertThat(phone.state.value.home).isEqualTo(2)
        phone.renameTeam(Side.AWAY, "Tigers"); link.pump()
        assertThat(watch.state.value.config.away.name).isEqualTo("Tigers")
    }

    @Test fun duplicateDeliveryNeverDoubleCounts() {
        val (phone, watch, link) = pair()
        link.dupRate = 1.0
        repeat(50) { watch.addPoints(Side.HOME, 1) }
        link.pump()
        // Replay every event again explicitly.
        phone.receive(SyncMessage.Events("watch", watch.activeLog.header, watch.activeLog.events.values.toList()))
        assertThat(phone.state.value.home).isEqualTo(50)
        assertConverged(phone, watch)
    }

    @Test fun offlineScoringOnBothSidesMergesOnReconnect() {
        val (phone, watch, link) = pair()
        link.online = false
        repeat(7) { watch.addPoints(Side.HOME, 2) }
        repeat(3) { phone.addPoints(Side.AWAY, 3) }
        phone.renameTeam(Side.HOME, "Sharks")
        assertThat(phone.state.value.home).isEqualTo(0)
        link.online = true
        watch.broadcastSummary(); link.pump()
        assertThat(phone.state.value.home).isEqualTo(14)
        assertThat(phone.state.value.away).isEqualTo(9)
        assertThat(watch.state.value.config.home.name).isEqualTo("Sharks")
        assertConverged(phone, watch)
    }

    @Test fun concurrentUndoOfSamePointUndoesOnce() {
        val (phone, watch, link) = pair()
        watch.addPoints(Side.HOME, 2); watch.addPoints(Side.HOME, 3); link.pump()
        link.online = false
        phone.undo(); watch.undo() // both revoke the same +3
        link.online = true
        phone.broadcastSummary(); link.pump()
        assertThat(phone.state.value.home).isEqualTo(2)
        assertConverged(phone, watch)
    }

    @Test fun newMatchOnOneDeviceSwitchesTheOther() {
        val (phone, watch, link) = pair()
        val archived = mutableListOf<MatchState>()
        phone.onArchive = { archived += it }
        watch.addPoints(Side.AWAY, 5); link.pump()
        watch.newMatch(); link.pump()
        assertThat(phone.state.value.matchId).isEqualTo(watch.state.value.matchId)
        assertThat(phone.state.value.away).isEqualTo(0)
        assertThat(archived.single().away).isEqualTo(5)
    }

    @Test fun lateEventsForPreviousMatchAreKeptNotAppliedToNewMatch() {
        val (phone, watch, link) = pair()
        link.online = false
        watch.addPoints(Side.HOME, 2) // watch offline, still on old match
        phone.newMatch()
        link.online = true
        val archived = mutableListOf<MatchState>()
        phone.onArchive = { archived += it }
        watch.broadcastSummary(); link.pump()
        assertThat(phone.state.value.home).isEqualTo(0)
        assertThat(watch.state.value.matchId).isEqualTo(phone.state.value.matchId)
        assertThat(watch.state.value.home).isEqualTo(0)
        // The late point is preserved in the archived previous match on the phone.
        assertThat(archived.last().home).isEqualTo(2)
    }

    @Test fun chaosConvergence() {
        for (seed in 1..30) {
            val (phone, watch, link) = pair(seed)
            val rnd = Random(seed * 7)
            link.dropRate = 0.3; link.dupRate = 0.3
            var expectedHome = 0; var expectedAway = 0
            repeat(300) {
                now += rnd.nextLong(1, 400)
                if (rnd.nextInt(20) == 0) link.online = !link.online
                val dev = if (rnd.nextBoolean()) phone else watch
                val pts = rnd.nextInt(1, 4)
                if (rnd.nextBoolean()) { dev.addPoints(Side.HOME, pts); expectedHome += pts }
                else { dev.addPoints(Side.AWAY, pts); expectedAway += pts }
                if (rnd.nextInt(5) == 0) link.pump()
            }
            // Heal the network and run anti-entropy rounds until quiet.
            link.online = true; link.dropRate = 0.0
            repeat(4) { phone.broadcastSummary(); watch.broadcastSummary(); link.pump() }
            assertThat(phone.state.value.home).isEqualTo(expectedHome)
            assertThat(phone.state.value.away).isEqualTo(expectedAway)
            assertConverged(phone, watch)
        }
    }

    @Test fun malformedMessagesAreIgnored() {
        assertThat(SyncCodec.decode("garbage".encodeToByteArray())).isNull()
        assertThat(SyncCodec.decode(byteArrayOf())).isNull()
    }

    @Test fun ownEchoIsIgnored() {
        val (phone, _, _) = pair()
        phone.addPoints(Side.HOME, 1)
        phone.receive(SyncMessage.Events("phone", phone.activeLog.header, phone.activeLog.events.values.toList()))
        assertThat(phone.state.value.home).isEqualTo(1)
    }
}

class FreshDeviceJoinTest {
    @Test fun newlyInstalledWatchAdoptsPhonesMatchInProgress() {
        val phone = ScoreEngine("phone-1", InMemorySessionStore())
        repeat(3) { phone.addPoints(Side.HOME, 2) }
        phone.newMatch(); phone.addPoints(Side.AWAY, 3)
        val watch = ScoreEngine("watch-9", InMemorySessionStore())
        phone.outbound = { watch.receive(it) }
        watch.outbound = { phone.receive(it) }
        watch.broadcastSummary()
        assertThat(watch.state.value.matchId).isEqualTo(phone.state.value.matchId)
        assertThat(phone.state.value.away).isEqualTo(3)
        assertThat(watch.state.value.away).isEqualTo(3)
    }

    @Test fun twoFreshDevicesShareTheInitialMatch() {
        val phone = ScoreEngine("phone-1", InMemorySessionStore())
        val watch = ScoreEngine("watch-1", InMemorySessionStore())
        phone.outbound = { watch.receive(it) }
        watch.outbound = { phone.receive(it) }
        phone.addPoints(Side.HOME, 1)
        watch.addPoints(Side.HOME, 1)
        watch.broadcastSummary()
        assertThat(phone.state.value.home).isEqualTo(2)
        assertThat(watch.state.value.home).isEqualTo(2)
    }
}

/** "Whatever was updated last is the result" — even when the devices were on different matches. */
class LastUpdateWinsTest {
    private var now = 1_000L
    private fun dev(id: String) = ScoreEngine(id, InMemorySessionStore(), clock = { now })
    private fun link(a: ScoreEngine, b: ScoreEngine) { a.outbound = { b.receive(it) }; b.outbound = { a.receive(it) } }

    @Test fun watchTapsAfterPhoneStartedAMatchWin() {
        val phone = dev("phone-1"); val watch = dev("watch-1")
        phone.newMatch(); phone.addPoints(Side.HOME, 2)          // phone at t=1000
        now = 5_000
        repeat(3) { watch.addPoints(Side.AWAY, 1) }               // watch (offline, own match) later
        link(phone, watch)
        watch.broadcastSummary(); phone.broadcastSummary()
        assertThat(phone.state.value.matchId).isEqualTo(watch.state.value.matchId)
        assertThat(phone.state.value.away).isEqualTo(3)
        assertThat(watch.state.value.away).isEqualTo(3)
    }

    @Test fun phoneUpdatedLastWins() {
        val phone = dev("phone-1"); val watch = dev("watch-1")
        repeat(3) { watch.addPoints(Side.AWAY, 1) }
        now = 9_000
        phone.newMatch(); phone.addPoints(Side.HOME, 5)
        link(phone, watch)
        watch.broadcastSummary()
        assertThat(watch.state.value.home).isEqualTo(5)
        assertThat(watch.state.value.away).isEqualTo(0)
        assertThat(phone.state.value.matchId).isEqualTo(watch.state.value.matchId)
    }

    @Test fun onTheSameMatchBothDevicesTapsAddUp() {
        val phone = dev("phone-1"); val watch = dev("watch-1")
        link(phone, watch)
        watch.addPoints(Side.HOME, 1); phone.addPoints(Side.HOME, 1); watch.addPoints(Side.AWAY, 1)
        assertThat(phone.state.value.home).isEqualTo(2)
        assertThat(watch.state.value.home).isEqualTo(2)
        assertThat(watch.state.value.away).isEqualTo(1)
    }

    @Test fun explicitNewMatchStartsFreshOnBoth() {
        val phone = dev("phone-1"); val watch = dev("watch-1")
        link(phone, watch)
        watch.addPoints(Side.HOME, 4)
        now = 20_000
        phone.newMatch()
        assertThat(watch.state.value.home).isEqualTo(0)
        assertThat(watch.state.value.matchId).isEqualTo(phone.state.value.matchId)
    }
}
