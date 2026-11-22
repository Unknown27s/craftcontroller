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

/** Offline scanning, validation and explicit endpoint confirmation; not a pairing client. */
@Composable
fun ManagerQrCard(prefs: SharedPreferences, compact: Boolean = false) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf(ManagerEndpoint.load(prefs)) }
    var message by rememberSaveable { mutableStateOf("") }
    var denied by rememberSaveable { mutableStateOf(false) }
    var manual by rememberSaveable { mutableStateOf(false) }
    var manualIp by rememberSaveable { mutableStateOf("") }
    var manualPort by rememberSaveable { mutableStateOf("") }
    var review by rememberSaveable { mutableStateOf(false) }
    var reviewIp by rememberSaveable { mutableStateOf("") }
    var reviewPort by rememberSaveable { mutableStateOf("") }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    fun showReview(endpoint: ManagerEndpoint) {
        reviewIp = endpoint.ip; reviewPort = endpoint.port.toString(); review = true
    }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents == null) {
            message = "Scanning cancelled or camera unavailable. You can try again or use manual entry."
        } else {
            runCatching { ManagerEndpoint.fromQr(result.contents) }
                .onSuccess { showReview(it); message = "QR read. Review the PC endpoint before saving." }
                .onFailure { message = it.message ?: "Unsupported QR code. Nothing was saved." }
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
        }.onFailure { message = "Unable to open the scanner. Use manual entry instead." }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) scan() else message = "Camera access was denied. Use manual entry or enable camera access in Android settings."
    }
    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF182631)) {
        Column(Modifier.fillMaxWidth().padding(if (compact) 16.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
            Text("Add your PC", style = MaterialTheme.typography.titleLarge)
            Text(if (compact) "Scan the code shown by DSU Manager, or enter its address." else "On the Windows PC, open DSU Manager and press Start to display its QR. CraftController reads the PC IP and advertised port locally.", style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = {
                    when {
                        !context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) -> message = "No camera is available. Use manual entry."
                        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> scan()
                        else -> permission.launch(Manifest.permission.CAMERA)
                    }
                }) { Text("Scan QR code") }
                OutlinedButton(onClick = {
                    manualIp = saved?.ip ?: ""; manualPort = saved?.port?.toString() ?: ""
                    message = ""; manual = true
                }) { Text("Enter manually") }
                if (denied) TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }
                        .onFailure { message = "Open Android app settings manually to enable camera access." }
                }) { Text("Camera settings") }
            }
            if (message.isNotEmpty()) Text(message, color = MaterialTheme.colorScheme.primary)
            saved?.let { endpoint ->
                Text("Saved PC endpoint: ${endpoint.ip}:${endpoint.port}", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { endpoint.forget(prefs); saved = null; message = "Saved PC endpoint removed." }) { Text("Forget endpoint") }
            }
            Text("Status: ${if (saved == null) "no PC endpoint saved" else "endpoint saved, NOT paired"}.")
            Text("QR saves an address only. DSU Manager pairing is not implemented yet.",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0A8BC))
            if (compact) TextButton(onClick = { showDetails = !showDetails }) { Text(if (showDetails) "Hide details" else "How this works") }
            if (!compact || showDetails) {
            Text("Scanning does not create a Windows gamepad. The PC's advertised Manager port is separate from this phone's DSU listener port.",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0A8BC))
            Text("Camera permission is requested only for scanning. QR images are not saved or uploaded. Opening the scanner pauses the foreground server; restart it explicitly afterward.",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFFB0A8BC))
            }
        }
    }
    if (manual) AlertDialog(onDismissRequest = { manual = false }, title = { Text("PC endpoint from DSU Manager") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Enter the PC address and Manager port—not the phone's UDP listener port. Nothing will be contacted automatically.")
                OutlinedTextField(manualIp, { if (it.length <= 15 && it.all { c -> c in '0'..'9' || c == '.' }) manualIp = it },
                    label = { Text("PC IPv4 address") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(manualPort, { if (it.length <= 5 && it.all { c -> c in '0'..'9' }) manualPort = it },
                    label = { Text("Manager port") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (message.isNotEmpty()) Text(message)
            }
        },
        confirmButton = { TextButton(onClick = {
            runCatching { ManagerEndpoint.fromManual(manualIp, manualPort) }
                .onSuccess { manual = false; showReview(it); message = "" }
                .onFailure { message = it.message ?: "Invalid address or port." }
        }) { Text("Review endpoint") } },
        dismissButton = { TextButton(onClick = { manual = false }) { Text("Cancel") } })
    if (review) AlertDialog(onDismissRequest = { review = false }, title = { Text("Save this PC endpoint?") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("PC: $reviewIp\nManager port: $reviewPort")
            Text("Only save a PC you trust on your local network. Saving stores the address; it does not make a connection or complete pairing.")
        } },
        confirmButton = { TextButton(onClick = {
            runCatching { ManagerEndpoint.fromManual(reviewIp, reviewPort) }
                .onSuccess { it.save(prefs); saved = it; review = false; message = "PC endpoint saved. Pairing is not implemented yet." }
                .onFailure { review = false; message = "Invalid endpoint. Nothing was saved." }
        }) { Text("Save endpoint") } },
        dismissButton = { TextButton(onClick = { review = false; message = "Endpoint not saved." }) { Text("Cancel") } })
}
