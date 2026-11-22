package io.github.unknown27s.dsucontroller

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.unknown27s.dsucontroller.core.ServerStatus

/** Connection guidance only. Never invents a DSU Manager pairing handshake. */
@Composable
fun ReceiverSetupScreen(
    prefs: SharedPreferences,
    status: ServerStatus,
    addresses: String,
    port: String,
    validPort: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleServer: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val context = LocalContext.current
    var notice by remember { mutableStateOf("") }
    val ips = addresses.split(" / ").filter { address ->
        val octets = address.split('.')
        octets.size == 4 && octets.all { it.toIntOrNull()?.let { value -> value in 0..255 } == true }
    }
    val details = "CraftController ${BuildConfig.VERSION_NAME}\nPhone IPv4: $addresses\nUDP port: $port\n" +
        "Protocol: DSU 1001; slot 0\nThese are phone listener details, not DSU Manager pairing data."
    Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹  Settings") }
            Column(Modifier.weight(1f)) {
                Text("Windows receiver setup", style = MaterialTheme.typography.headlineSmall)
                Text("CraftController + your existing DSU Manager", style = MaterialTheme.typography.bodySmall)
            }
        }
        ManagerQrCard(prefs)
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF142536)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("This phone’s DSU listener", style = MaterialTheme.typography.titleLarge)
                SelectionContainer { Text("IPv4: $addresses\nUDP: $port · Protocol 1001 · Slot 0") }
                Text(when {
                    !status.running -> status.message
                    status.peers == 0 -> "Listening. No active DSU subscriber."
                    else -> "${status.peers} DSU subscriber(s). This does not confirm Windows gamepad output."
                }, color = MaterialTheme.colorScheme.primary)
                Text("${status.received} requests · ${status.rejected} rejected packets", style = MaterialTheme.typography.bodySmall)
                Text("${status.sendFailures} send failures · subscriptions expire after 5 seconds without PC renewal; this does not stop the listener.", style = MaterialTheme.typography.bodySmall)
                status.lastSendError?.let { Text("Last send error: $it", style = MaterialTheme.typography.bodySmall) }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(enabled = validPort || status.running, onClick = onToggleServer) { Text(if (status.running) "Stop server" else "Start server") }
                    OutlinedButton(onClick = onRefresh) { Text("Refresh addresses") }
                    OutlinedButton(enabled = validPort && ips.isNotEmpty(), onClick = {
                        notice = runCatching {
                            context.getSystemService(ClipboardManager::class.java)
                                .setPrimaryClip(ClipData.newPlainText("CraftController connection", details))
                            "Phone listener details copied. This does not pair DSU Manager."
                        }.getOrDefault("Unable to copy. You can select the address text manually.")
                    }) { Text("Copy phone details") }
                    OutlinedButton(enabled = validPort && ips.isNotEmpty(), onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, details) }
                        runCatching { context.startActivity(Intent.createChooser(send, "Share phone connection details")) }
                            .onFailure { notice = "No sharing app is available. Copy or select the address text instead." }
                    }) { Text("Share details") }
                }
                if (notice.isNotEmpty()) Text(notice, style = MaterialTheme.typography.bodySmall)
                Text("Sharing or opening another app stops this foreground-only server. Return and restart it explicitly.",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFFA5ADBC))
            }
        }
        Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF2B2119)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pairing is still an integration requirement", style = MaterialTheme.typography.titleMedium)
                Text("CraftController can now scan the published Manager connection QR format and save its PC endpoint. It does not yet send the subsequent pairing/control exchange. QR read or endpoint saved does not mean paired.")
                Text("If your receiver requires that exchange, it still cannot subscribe through this workflow until it is implemented. Keep your exact Manager version available, and do not publish QR codes containing pairing secrets.")
            }
        }
        Text("Setup checklist", style = MaterialTheme.typography.titleLarge)
        Text("1. Keep the Windows PC and phone on the same trusted LAN.\n" +
            "2. Open your existing DSU Manager installation. No new receiver is required.\n" +
            "3. Note the Manager version and how it accepts a phone connection. The original guide uses QR pairing.\n" +
            "4. For a receiver that supports directly adding a standard DSU source, use the phone IPv4 and UDP port above. This path has not been established for DSU Manager.\n" +
            "5. Later, verify all three stages separately: DSU subscription, a Windows virtual gamepad, and input reaching the game.")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { onOpenLink("https://github.com/breeze2/dsu-controller-guides#run-with-dsu-manager-on-windows") }) { Text("Official setup guide") }
            OutlinedButton(onClick = { onOpenLink("https://github.com/breeze2/dsu-manager-guides/releases") }) { Text("DSU Manager releases") }
            TextButton(onClick = { onOpenLink("https://docs.nefarius.at/projects/ViGEm/End-of-Life/") }) { Text("Windows driver lifecycle notice") }
        }
        Text("DSU does not create an Xbox controller by itself. The Windows receiver and its virtual-device backend do that. Older guides reference ViGEmBus, which is retired; review the official lifecycle notice instead of blindly installing old drivers.",
            style = MaterialTheme.typography.bodySmall, color = Color(0xFFA5ADBC), modifier = Modifier.padding(bottom = 24.dp))
    }
}
