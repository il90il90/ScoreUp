package com.scoreup.app

import com.google.common.truth.Truth.assertThat
import com.scoreup.app.install.Endpoint
import com.scoreup.app.install.InstallerState
import org.junit.Test

/** Regression for the field bug: pairing went to another LAN device's IP (192.168.1.3) instead of the watch. */
class WatchTargetTest {
    private val watchPair = Endpoint("192.168.1.52", 41299, "adb-WATCH-pair")
    private val watchConnect = Endpoint("192.168.1.52", 34439, "adb-WATCH-conn")
    private val otherDevice = Endpoint("192.168.1.3", 37000, "adb-TV-conn")

    @Test fun picksConnectServiceOfTheWatchBeingPaired() {
        val s = InstallerState(pairingService = watchPair, connectServices = listOf(otherDevice, watchConnect))
        assertThat(s.connectServiceFor("192.168.1.52")).isEqualTo(watchConnect)
        assertThat(s.connectService).isEqualTo(watchConnect) // falls back to the pairing service's host
    }

    @Test fun neverPicksAnotherDeviceWhenTheWatchIsNotAdvertisingYet() {
        val s = InstallerState(pairingService = watchPair, connectServices = listOf(otherDevice))
        assertThat(s.connectServiceFor("192.168.1.52")).isNull()
    }

    @Test fun usesRememberedPairedWatch() {
        val s = InstallerState(pairedHost = "192.168.1.52", connectServices = listOf(otherDevice, watchConnect))
        assertThat(s.connectService).isEqualTo(watchConnect)
    }

    @Test fun singleUnknownDeviceIsOfferedOnlyWhenNothingIsKnown() {
        assertThat(InstallerState(connectServices = listOf(watchConnect)).connectService).isEqualTo(watchConnect)
        assertThat(InstallerState(connectServices = listOf(otherDevice, watchConnect)).connectService).isNull()
    }
}
