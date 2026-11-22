package io.github.unknown27s.dsucontroller

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import io.github.unknown27s.dsucontroller.core.ServerStatus

/**
 * Scan, save and immediately announce. The announcement is sent from the phone's
 * bound DSU socket so the PC's subscription returns to that same port.
 */
@Composable
fun ManagerQrCard(
    prefs: SharedPreferences,
    status: ServerStatus,
    compact: Boolean = false,
    onEnsureServer: () -> Boolean,
    onAnnounce: (ManagerEndpoint) -> Boolean,
) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(ManagerEndpoint.load(prefs)) }
    var message by rememberSaveable { mutableStateOf("") }
    var denied by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var manualIp by rememberSaveable { mutableStateOf("") }
    var manualPort by rememberSaveable { mutableStateOf("") }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    var restartAfterScan by remember { mutableStateOf(false) }

    fun connect(endpoint: ManagerEndpoint) {
        endpoint.save(prefs)
        saved = endpoint
        val started = onEnsureServer()
        val sent = started && onAnnounce(endpoint)
        message = when {
            !started -> "Saved ${endpoint.ip}:${endpoint.port}, but the phone server could not start."
            !sent -> "Saved ${endpoint.ip}:${endpoint.port}. Press Send again once the server is running."
            else -> "Sent DSU announcement to ${endpoint.ip}:${endpoint.port}."
        }
    }

    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val restart = restartAfterScan
        restartAfterScan = false
        // The camera activity paused this app, which stopped the foreground server.
        if (restart) onEnsureServer()
        if (result.contents == null) {
            message = "Scanning cancelled. " + if (restart) "The phone server was restarted." else "Nothing was sent."
        } else {
            runCatching { ManagerEndpoint.fromQr(result.contents) }
                .onSuccess { connect(it) }
                .onFailure { message = it.message ?: "Unsupported QR code. Nothing was sent." }
        }
    }
    fun scan() {
        runCatching {
            scanner.launch(ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                setPrompt("CraftController: scan the QR shown by Windows DSU Manager")
                setBeepEnabled(false)
                setBarcodeImageEnabled(false)
                setOrientationLocked(false)
            })
        }.onFailure {
            restartAfterScan = false
            message = "Unable to open the scanner. Use manual entry instead."
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) scan() else {
            if (restartAfterScan) { onEnsureServer(); restartAfterScan = false }
            message = "Camera access was denied. Use manual entry or enable camera access in Android settings."
        }
    }
    fun beginScan() {
        // Capture this before the camera or permission dialog pauses the activity.
        restartAfterScan = status.running
        when {
            !context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) -> {
                restartAfterScan = false
                message = "No camera is available. Use manual entry."
            }
            context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> scan()
            else -> permission.launch(Manifest.permission.CAMERA)
        }
    }

    val state = when {
        saved == null -> "No PC endpoint yet"
        status.peers > 0 -> "${status.peers} PC subscriber(s) · controller data is being sent"
        status.running -> "Waiting for the PC to subscribe"
        else -> "Phone server is off · press Send again to restart it"
    }

    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF182631)) {
        Column(Modifier.fillMaxWidth().padding(if (compact) 16.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
            Text("Connect to your PC", style = MaterialTheme.typography.titleLarge)
            Text(if (compact) "Scan the code shown by DSU Manager. It is sent immediately." else "On the Windows PC, open DSU Manager and press Start to show its QR. Scanning sends the DSU announcement straight away, then the PC subscribes to this phone.", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = ::beginScan) { Text("Scan QR code") }
                OutlinedButton(onClick = {
                    manualIp = saved?.ip ?: ""; manualPort = saved?.port?.toString() ?: ""
                    message = ""; manual = true
                }) { Text("Enter address") }
                if (saved != null) OutlinedButton(onClick = {
                    saved?.let { endpoint ->
                        val started = onEnsureServer()
                        val sent = started && onAnnounce(endpoint)
                        message = when {
                            !started -> "Phone server could not start. Open Connection settings."
                            !sent -> "Server is starting. Try again in a moment."
                            else -> "Sent DSU announcement to ${endpoint.ip}:${endpoint.port} again."
                        }
                    }
                }) { Text("Send again") }
                if (denied) TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                        .onFailure { message = "Open Android app settings manually to enable camera access." }
                }) { Text("Camera settings") }
            }
            if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.primary)
            if (saved != null) {
                Text("PC endpoint: ${saved!!.ip}:${saved!!.port}", style = MaterialTheme.typography.titleMedium)
                Text(state, style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { saved?.forget(prefs); saved = null; message = "PC endpoint removed." }) { Text("Forget endpoint") }
            } else {
                Text(state, style = MaterialTheme.typography.bodyMedium)
            }
            if (compact) TextButton(onClick = { showDetails = !showDetails }) { Text(if (showDetails) "Hide details" else "How this works") }
            if (!compact || showDetails) {
                Text("Scanning sends a DSU version announcement over UDP to the Manager port in the QR. The PC then subscribes to this phone's listener; that subscription is what makes controller data flow.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0A8BC))
                Text("Opening the camera pauses the foreground server. It restarts automatically when you return, before the announcement is sent.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0A8BC))
                Text("A saved address alone is not a connection. The subscriber count is the evidence that the PC is receiving controller data.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0A8BC))
            }
        }
    }
    if (manual) AlertDialog(onDismissRequest = { manual = false }, title = { Text("PC endpoint from DSU Manager") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter the PC IPv4 address and the Manager port it advertises. The phone sends the announcement as soon as you connect.")
                OutlinedTextField(manualIp, { if (it.length <= 15 && it.all { c -> c in '0'..'9' || c == '.' }) manualIp = it },
                    label = { Text("PC IPv4 address") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(manualPort, { if (it.length <= 5 && it.all { c -> c in '0'..'9' }) manualPort = it },
                    label = { Text("Manager port") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (message.isNotEmpty()) Text(message)
            }
        },
        confirmButton = { TextButton(onClick = {
            runCatching { ManagerEndpoint.fromManual(manualIp, manualPort) }
                .onSuccess { manual = false; connect(it) }
                .onFailure { message = it.message ?: "Invalid address or port." }
        }) { Text("Connect") } },
        dismissButton = { TextButton(onClick = { manual = false }) { Text("Cancel") } })
}
