package com.scoreup.app.ui.watch

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.scoreup.app.R
import com.scoreup.app.install.ErrorKind
import com.scoreup.app.install.InstallPhase
import com.scoreup.app.install.InstallerState
import com.scoreup.app.ui.common.ScreenScaffold
import com.scoreup.shared.WearSync

/** Guided, computer-free install/update of the watch app over Wireless debugging. */
@Composable
fun WatchScreen(
    installer: InstallerState,
    peers: List<WearSync.Peer>,
    connected: List<WearSync.Peer>,
    lastHost: String,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onPair: (String, Int, String) -> Unit,
    onInstall: (String, Int) -> Unit,
    onReset: () -> Unit,
    onBack: () -> Unit,
) {
    DisposableEffect(Unit) { onStartDiscovery(); onDispose { onStopDiscovery() } }

    var host by rememberSaveable { mutableStateOf(lastHost) }
    var pairPort by rememberSaveable { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var manual by rememberSaveable { mutableStateOf(false) }

    // Auto-fill from mDNS discovery.
    LaunchedEffect(installer.pairingService) { installer.pairingService?.let { host = it.host; pairPort = it.port.toString() } }
    LaunchedEffect(installer.connectService) { installer.connectService?.let { host = it.host; connectPort = it.port.toString() } }

    ScreenScaffold(stringResource(R.string.installer_title), onBack) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.installer_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
            StatusCard(installer, peers, connected)

            if (Build.VERSION.SDK_INT < 30) {
                Text(stringResource(R.string.installer_needs_android11), color = MaterialTheme.colorScheme.error)
            }

            Step(stringResource(R.string.step_dev_options), stringResource(R.string.step_dev_options_body))
            Step(stringResource(R.string.step_wireless), stringResource(R.string.step_wireless_body))

            Step(stringResource(R.string.step_pair), stringResource(R.string.step_pair_body)) {
                if (installer.paired) {
                    Text(stringResource(R.string.paired), color = Color(0xFF00E58F), fontWeight = FontWeight.Bold, modifier = Modifier.testTag("paired"))
                }
                DiscoveryLine(installer.pairingService?.let { stringResource(R.string.found_pairing, it.toString()) }, installer.discovering)
                OutlinedTextField(
                    value = code, onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) }, singleLine = true,
                    label = { Text(stringResource(R.string.pairing_code)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().testTag("pair_code"),
                )
                if (manual || installer.pairingService == null) {
                    AddressFields(host, { host = it }, pairPort, { pairPort = it }, stringResource(R.string.pair_port))
                }
                Button(
                    onClick = { onPair(host, pairPort.toInt(), code) },
                    enabled = !installer.busy && code.length == 6 && host.isNotBlank() && pairPort.toIntOrNull() != null,
                    modifier = Modifier.fillMaxWidth().testTag("pair_button"),
                ) { Text(stringResource(R.string.pair)) }
            }

            Step(stringResource(R.string.step_install), stringResource(R.string.step_install_body)) {
                DiscoveryLine(installer.connectService?.let { stringResource(R.string.found_connect, it.toString()) }, installer.discovering)
                if (manual || installer.connectService == null) {
                    AddressFields(host, { host = it }, connectPort, { connectPort = it }, stringResource(R.string.connect_port))
                }
                val isUpdate = peers.any { (it.appVersionCode ?: 0) > 0 }
                Button(
                    onClick = { onInstall(host, connectPort.toInt()) },
                    enabled = !installer.busy && host.isNotBlank() && connectPort.toIntOrNull() != null && installer.bundledVersionCode > 0,
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("install_button"),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(stringResource(if (isUpdate) R.string.update else R.string.install), fontWeight = FontWeight.Black) }
                Progress(installer, onReset)
            }

            TextButton(onClick = { manual = !manual }) { Text(stringResource(R.string.manual_entry)) }
            Text(stringResource(R.string.install_tip), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusCard(s: InstallerState, peers: List<WearSync.Peer>, connected: List<WearSync.Peer>) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.testTag("watch_status")) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Watch, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.installer_bundled, s.bundledVersionName ?: "—"), fontWeight = FontWeight.Bold)
            }
            if (connected.isEmpty()) Text(stringResource(R.string.installer_no_nodes), color = MaterialTheme.colorScheme.onSurfaceVariant)
            connected.forEach { node ->
                val peer = peers.firstOrNull { it.id == node.id }
                val v = peer?.appVersionCode
                when {
                    peer == null -> Text(stringResource(R.string.installer_watch_missing, node.name))
                    v != null && v > 0 && v < s.bundledVersionCode -> Text(stringResource(R.string.installer_update_available), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    v != null && v > 0 -> Text(stringResource(R.string.installer_up_to_date), color = Color(0xFF00E58F))
                    else -> Text(stringResource(R.string.watch_connected))
                }
            }
        }
    }
}

@Composable
private fun Step(title: String, body: String, content: @Composable () -> Unit = {}) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun DiscoveryLine(found: String?, discovering: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (found == null && discovering) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
        Text(found ?: stringResource(R.string.searching), style = MaterialTheme.typography.bodySmall,
            color = if (found != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddressFields(host: String, onHost: (String) -> Unit, port: String, onPort: (String) -> Unit, portLabel: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(host, { v -> onHost(v.filter { it.isDigit() || it == '.' }.take(15)) }, singleLine = true,
            label = { Text(stringResource(R.string.host)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.weight(1.6f).testTag("host_field"))
        OutlinedTextField(port, { v -> onPort(v.filter { it.isDigit() }.take(5)) }, singleLine = true,
            label = { Text(portLabel) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f).testTag("port_field"))
    }
}

@Composable
private fun Progress(s: InstallerState, onReset: () -> Unit) {
    AnimatedVisibility(s.phase != InstallPhase.IDLE) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("install_progress")) {
            when (s.phase) {
                InstallPhase.PAIRING, InstallPhase.CONNECTING -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.connecting)) }
                InstallPhase.INSTALLING, InstallPhase.LAUNCHING -> {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.installing, (s.progress * 100).toInt()))
                }
                InstallPhase.SUCCESS -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF00E58F)); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.install_success), fontWeight = FontWeight.Bold)
                }
                InstallPhase.ERROR -> Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(8.dp))
                        val msg = s.error ?: ""
                        Text(
                            when (s.errorKind) {
                                ErrorKind.PAIR -> stringResource(R.string.pair_failed, msg)
                                ErrorKind.CONNECT -> stringResource(R.string.connect_failed, msg)
                                else -> stringResource(R.string.install_failed, msg)
                            },
                            color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("install_error"),
                        )
                    }
                    TextButton(onClick = onReset) { Text(stringResource(R.string.retry)) }
                }
                InstallPhase.IDLE -> {}
            }
        }
    }
}
