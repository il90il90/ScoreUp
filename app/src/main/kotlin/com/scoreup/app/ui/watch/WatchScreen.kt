package com.scoreup.app.ui.watch

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.scoreup.app.R
import com.scoreup.app.install.ErrorKind
import com.scoreup.app.install.InstallPhase
import com.scoreup.app.install.InstallerState
import com.scoreup.app.ui.common.ScreenScaffold
import com.scoreup.shared.WearSync

private const val STEPS = 4

/**
 * Computer-free watch install, designed as a 4-step wizard:
 * 1-2 enable Wireless debugging on the watch, 3 type the pairing code (the watch is found
 * automatically and pairing starts on the 6th digit), 4 install (starts by itself after pairing).
 * Once paired, the screen collapses to a single "Install/Update now" button.
 */
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

    var step by rememberSaveable { mutableStateOf(if (installer.paired) 0 else 1) } // 0 = quick (already paired)
    var host by rememberSaveable { mutableStateOf(lastHost) }
    var pairPort by rememberSaveable { mutableStateOf("") }
    var connectPort by rememberSaveable { mutableStateOf("") }
    var manual by rememberSaveable { mutableStateOf(false) }
    var autoInstallArmed by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(installer.pairingService) { installer.pairingService?.let { host = it.host; pairPort = it.port.toString() } }
    LaunchedEffect(installer.connectService) { installer.connectService?.let { host = it.host; connectPort = it.port.toString() } }

    // Pairing succeeded → jump to install and start it automatically as soon as the watch is reachable.
    LaunchedEffect(installer.paired, step) { if (installer.paired && step == 3) { step = 4; autoInstallArmed = true } }
    LaunchedEffect(autoInstallArmed, connectPort, installer.phase) {
        val port = connectPort.toIntOrNull()
        if (autoInstallArmed && port != null && host.isNotBlank() && !installer.busy && installer.phase != InstallPhase.ERROR) {
            autoInstallArmed = false
            onInstall(host, port)
        }
    }

    val isUpdate = peers.any { (it.appVersionCode ?: 0) > 0 }
    val canInstall = host.isNotBlank() && connectPort.toIntOrNull() != null && installer.bundledVersionCode > 0 && !installer.busy

    ScreenScaffold(stringResource(R.string.installer_title), onBack) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(installer, peers, connected)
            if (Build.VERSION.SDK_INT < 30) Text(stringResource(R.string.installer_needs_android11), color = MaterialTheme.colorScheme.error)

            if (step > 0) StepDots(step)

            AnimatedContent(step, transitionSpec = {
                val fwd = targetState > initialState
                (slideInHorizontally { if (fwd) it / 3 else -it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { if (fwd) -it / 3 else it / 3 } + fadeOut())
            }, label = "wizard") { s ->
                when (s) {
                    0 -> QuickCard(installer, isUpdate, canInstall, onInstall = { onInstall(host, connectPort.toInt()) }, onGuided = { step = 1 })
                    1 -> StepCard(1, stringResource(R.string.wiz_s1_title), stringResource(R.string.wiz_s1_body), "⚙️") {
                        BigButton(stringResource(R.string.wiz_done_btn), "wiz_next_1") { step = 2 }
                    }
                    2 -> StepCard(2, stringResource(R.string.wiz_s2_title), stringResource(R.string.wiz_s2_body), "📶") {
                        BigButton(stringResource(R.string.wiz_done_btn), "wiz_next_2") { step = 3 }
                    }
                    3 -> StepCard(3, stringResource(R.string.wiz_s3_title), stringResource(R.string.wiz_s3_body), "🔗") {
                        Discovery(installer.pairingService?.toString(), installer.discovering)
                        if (manual || (installer.pairingService == null && pairPort.isEmpty())) {
                            AddressFields(host, { host = it }, pairPort, { pairPort = it }, stringResource(R.string.pair_port))
                        }
                        CodeBoxes(enabled = !installer.busy) { code ->
                            val p = pairPort.toIntOrNull()
                            if (p != null && host.isNotBlank()) onPair(host, p, code)
                        }
                        if (installer.phase == InstallPhase.PAIRING) Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.connecting))
                        }
                    }
                    else -> StepCard(4, stringResource(R.string.wiz_s4_title), stringResource(R.string.wiz_s4_body), "⌚") {
                        Discovery(installer.connectService?.toString(), installer.discovering)
                        if (manual || (installer.connectService == null && connectPort.isEmpty())) {
                            AddressFields(host, { host = it }, connectPort, { connectPort = it }, stringResource(R.string.connect_port))
                        }
                        if (installer.phase == InstallPhase.SUCCESS) {
                            BigButton(stringResource(R.string.wiz_finish), "wiz_finish") { onReset(); step = 0 }
                        } else {
                            BigButton(stringResource(if (isUpdate) R.string.update else R.string.install), "install_button", enabled = canInstall) {
                                onInstall(host, connectPort.toInt())
                            }
                        }
                    }
                }
            }

            InstallProgress(installer, onRetry = { onReset(); if (step == 4) autoInstallArmed = false }, onRepair = { onReset(); step = 3 })

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (step in 2..4) TextButton(onClick = { onReset(); step -= 1 }) { Text(stringResource(R.string.back)) } else Spacer(Modifier)
                TextButton(onClick = { manual = !manual }) { Text(stringResource(R.string.manual_entry)) }
            }
            Troubleshooting()
            com.scoreup.app.ui.common.MadeWithLove()
        }
    }
}

@Composable
private fun StepDots(step: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 1..STEPS) {
                val w by animateFloatAsState(if (i == step) 28f else 10f, label = "dot")
                Box(Modifier.height(10.dp).width(w.dp).background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.wiz_step, step, STEPS), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StepCard(n: Int, title: String, body: String, emoji: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.testTag("wiz_step_$n")) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 44.sp)
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            content()
        }
    }
}

@Composable
private fun QuickCard(s: InstallerState, isUpdate: Boolean, canInstall: Boolean, onInstall: () -> Unit, onGuided: () -> Unit) {
    Surface(shape = RoundedCornerShape(26.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.testTag("wiz_quick")) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Watch, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Text(stringResource(R.string.wiz_ready_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.wiz_ready_body), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Discovery(s.connectService?.toString(), s.discovering)
            BigButton(stringResource(if (isUpdate) R.string.wiz_quick_update else R.string.wiz_quick_install), "install_button", enabled = canInstall, onClick = onInstall)
            TextButton(onClick = onGuided) { Text(stringResource(R.string.wiz_start_guided)) }
        }
    }
}

@Composable
private fun BigButton(text: String, tag: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(56.dp).testTag(tag)) {
        Text(text, fontWeight = FontWeight.Black, fontSize = 18.sp)
    }
}

/** Six large digit boxes; pairing fires automatically once all 6 digits are entered. */
@Composable
private fun CodeBoxes(enabled: Boolean, onComplete: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BasicTextField(
        value = code, enabled = enabled,
        onValueChange = { v ->
            val digits = v.filter { it.isDigit() }.take(6)
            code = digits
            if (digits.length == 6) onComplete(digits)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.focusRequester(focus).testTag("pair_code"),
        decorationBox = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until 6) {
                    val c = code.getOrNull(i)
                    val active = i == code.length
                    Box(
                        Modifier.size(width = 44.dp, height = 56.dp)
                            .border(2.dp, if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(c?.toString() ?: "", fontSize = 26.sp, fontWeight = FontWeight.Black) }
                }
            }
        },
    )
}

@Composable
private fun StatusCard(s: InstallerState, peers: List<WearSync.Peer>, connected: List<WearSync.Peer>) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.testTag("watch_status")) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.installer_bundled, s.bundledVersionName ?: "—"), fontWeight = FontWeight.Bold)
            if (connected.isEmpty()) Text(stringResource(R.string.installer_no_nodes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            connected.forEach { node ->
                val v = peers.firstOrNull { it.id == node.id }?.appVersionCode
                when {
                    v == null || v <= 0 -> Text(stringResource(R.string.installer_watch_missing, node.name))
                    v < s.bundledVersionCode -> Text(stringResource(R.string.installer_update_available), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    else -> Text(stringResource(R.string.installer_up_to_date), color = Color(0xFF00E58F))
                }
            }
        }
    }
}

@Composable
private fun Discovery(found: String?, discovering: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (found == null && discovering) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
        if (found != null) { Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF00E58F), modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(if (found != null) stringResource(R.string.wiz_found, found) else stringResource(R.string.wiz_looking), style = MaterialTheme.typography.bodyMedium)
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
private fun InstallProgress(s: InstallerState, onRetry: () -> Unit, onRepair: () -> Unit) {
    AnimatedVisibility(s.phase in setOf(InstallPhase.CONNECTING, InstallPhase.INSTALLING, InstallPhase.LAUNCHING, InstallPhase.SUCCESS, InstallPhase.ERROR)) {
        Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.testTag("install_progress")) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (s.phase) {
                    InstallPhase.SUCCESS -> {
                        Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFF00E58F), modifier = Modifier.size(56.dp))
                        Text(stringResource(R.string.wiz_open_on_watch), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    }
                    InstallPhase.ERROR -> {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
                        val msg = s.error ?: ""
                        Text(
                            when (s.errorKind) {
                                ErrorKind.PAIR -> stringResource(R.string.pair_failed, msg)
                                ErrorKind.CONNECT -> stringResource(R.string.connect_failed, msg)
                                else -> stringResource(R.string.install_failed, msg)
                            },
                            color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.testTag("install_error"),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                            if (s.errorKind == ErrorKind.CONNECT) Button(onClick = onRepair, modifier = Modifier.testTag("pair_again")) { Text(stringResource(R.string.pair_again)) }
                        }
                    }
                    else -> {
                        val determinate = s.phase != InstallPhase.CONNECTING
                        Box(contentAlignment = Alignment.Center) {
                            if (determinate) CircularProgressIndicator(progress = { s.progress }, modifier = Modifier.size(96.dp), strokeWidth = 8.dp)
                            else CircularProgressIndicator(Modifier.size(96.dp), strokeWidth = 8.dp)
                            if (determinate) Text("${(s.progress * 100).toInt()}%", fontWeight = FontWeight.Black, fontSize = 22.sp)
                        }
                        Text(if (determinate) stringResource(R.string.installing, (s.progress * 100).toInt()) else stringResource(R.string.connecting))
                    }
                }
            }
        }
    }
}

@Composable
private fun Troubleshooting() {
    var open by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton(onClick = { open = !open }) { Text(stringResource(R.string.wiz_troubleshoot)) }
        AnimatedVisibility(open) {
            Text(stringResource(R.string.wiz_troubleshoot_body) + "\n\n" + stringResource(R.string.install_tip),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
