package io.github.unknown27s.dsucontroller

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.view.Window
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.unknown27s.dsucontroller.core.ControllerState
import io.github.unknown27s.dsucontroller.core.DsuServer
import io.github.unknown27s.dsucontroller.core.PadButton

private val accents = listOf(Color(0xFF8BD5CA), Color(0xFF8AADF4), Color(0xFFC6A0F6), Color(0xFFF5A97F))
private val accentNames = listOf("Mint", "Blue", "Violet", "Peach")
private val panelColor = Color(0xFF15202C)

/** Controller-first UI. Local touch feedback is independent of network availability. */
@Composable
fun ControllerApp(
    prefs: SharedPreferences,
    server: DsuServer,
    window: Window,
    addressesProvider: () -> String,
    inputFocused: Boolean,
    onStart: (Int) -> Unit,
) {
    val status by server.status.collectAsState()
    var page by rememberSaveable { mutableStateOf("play") }
    var category by rememberSaveable { mutableStateOf("Connection") }
    var custom by remember { mutableStateOf(LayoutSettings.load(prefs)) }
    var preset by remember { mutableStateOf(LayoutPreset.fromName(runCatching {
        prefs.getString("preset", if (prefs.contains("layout")) "CUSTOM" else "STANDARD")
    }.getOrNull())) }
    var draft by remember { mutableStateOf(custom) }
    var selected by remember { mutableStateOf<String?>(null) }
    var port by remember { mutableStateOf(runCatching { prefs.getInt("port", 26760) }.getOrDefault(26760)
        .let { if (it in 1024..65535) it.toString() else "26760" }) }
    var deadZone by remember { mutableFloatStateOf(prefs.safeFloat("deadZone", .12f, 0f.. .4f)) }
    var opacity by remember { mutableFloatStateOf(prefs.safeFloat("opacity", 1f, .5f..1f)) }
    var accentIndex by remember { mutableIntStateOf(runCatching { prefs.getInt("accent", 0) }.getOrDefault(0).coerceIn(0, 3)) }
    var haptics by remember { mutableStateOf(prefs.safeBoolean("haptics", false)) }
    var diagonals by remember { mutableStateOf(prefs.safeBoolean("diagonals", true)) }
    var stickyDpad by remember { mutableStateOf(prefs.safeBoolean("stickyDpad", true)) }
    var floatingSticks by remember { mutableStateOf(prefs.safeBoolean("floatingSticks", false)) }
    var addresses by remember { mutableStateOf(addressesProvider()) }
    var showDiscard by remember { mutableStateOf(false) }
    val previousButtons = remember { arrayOf(emptySet<PadButton>()) }
    val view = LocalView.current
    val context = LocalContext.current
    val accent = accents[accentIndex]
    val validPort = port.toIntOrNull()?.takeIf { it in 1024..65535 }
    fun goSettings() {
        server.input.set(ControllerState()); previousButtons[0] = emptySet()
        addresses = addressesProvider(); page = "settings"
    }
    fun editLayout() {
        server.stop("Stopped: layout editor opened")
        draft = preset.layout(custom); selected = null; page = "editor"
    }
    fun toggleServer() {
        if (status.running) server.stop("Stopped by you") else validPort?.let {
            prefs.edit().putInt("port", it).apply(); addresses = addressesProvider(); onStart(it)
        }
    }
    fun openLink(url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
    BackHandler(page != "play") {
        when (page) { "editor" -> showDiscard = true; "receiver" -> page = "settings"; else -> page = "play" }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = accent, onPrimary = Color(0xFF08161B),
        background = Color(0xFF090D13), surface = Color(0xFF101923), onSurface = Color(0xFFE2EAF3),
        secondaryContainer = Color(0xFF243342), onSecondaryContainer = Color(0xFFE2EAF3))) {
        Surface(Modifier.fillMaxSize()) {
            when (page) {
                "receiver" -> ReceiverSetupScreen(prefs, status, addresses, port, validPort != null,
                    onBack = { page = "settings" }, onRefresh = { addresses = addressesProvider() },
                    onToggleServer = ::toggleServer, onOpenLink = ::openLink)
                "settings" -> Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { page = "play" }) { Text("Back to controller") }
                        Text("Settings", Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text("CraftController", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8DA2B6))
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Connection", "Controls", "Appearance", "Advanced").forEach { title ->
                            FilterChip(selected = category == title, onClick = { category = title }, label = { Text(title) })
                        }
                    }
                    key(category) {
                        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Spacer(Modifier.height(2.dp))
                            when (category) {
                                "Connection" -> {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                                    Column(Modifier.weight(.46f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    SettingsGroup("Phone server") {
                                        SettingRow(if (status.running) "Server is running" else "Start your controller", status.message) {
                                            Switch(status.running, { toggleServer() }, enabled = validPort != null || status.running)
                                        }
                                        SettingRow("PC subscription", if (status.peers == 0) "Waiting for a PC. A saved QR is not a connection." else "${status.peers} active DSU subscriber(s) · Windows output not verified")
                                        SettingRow("Phone address", "$addresses · UDP $port", onClick = { addresses = addressesProvider() }) { Text("Refresh", color = accent) }
                                    }
                                    OutlinedButton(onClick = { addresses = addressesProvider(); page = "receiver" }, modifier = Modifier.fillMaxWidth()) { Text("PC setup & troubleshooting") }
                                    }
                                    Column(Modifier.weight(.54f)) {
                                        Spacer(Modifier.height(4.dp))
                                        ManagerQrCard(prefs, compact = true)
                                    }
                                    }
                                }
                                "Controls" -> {
                                    SettingsGroup("Controller layout") {
                                        Column(Modifier.selectableGroup()) {
                                            LayoutPreset.entries.forEach { option ->
                                                Row(Modifier.fillMaxWidth().selectable(preset == option, role = Role.RadioButton, onClick = {
                                                    preset = option; prefs.edit().putString("preset", option.name).apply()
                                                }).padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Column(Modifier.weight(1f)) {
                                                        Text(option.title, style = MaterialTheme.typography.titleMedium)
                                                        Text(option.description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF92A7BB))
                                                    }
                                                    RadioButton(selected = preset == option, onClick = null)
                                                }
                                            }
                                        }
                                        SettingRow("Layout studio", "Move, resize and hide controls. D-pad edits as one group.", onClick = ::editLayout) { Text("Customize", color = accent) }
                                    }
                                    SettingsGroup("Touch behavior") {
                                        SettingRow("Floating sticks", "The stick centers on your first touch inside its ring") {
                                            Switch(floatingSticks, { floatingSticks = it; prefs.edit().putBoolean("floatingSticks", it).apply() })
                                        }
                                        SettingRow("D-pad diagonals", "Eight directions; turn off for four-way movement") {
                                            Switch(diagonals, { diagonals = it; prefs.edit().putBoolean("diagonals", it).apply() })
                                        }
                                        SettingRow("Sticky D-pad", "Keep steering when your thumb slides outside; center releases") {
                                            Switch(stickyDpad, { stickyDpad = it; prefs.edit().putBoolean("stickyDpad", it).apply() })
                                        }
                                        SettingRow("Haptic feedback", "Short system feedback on new button presses") {
                                            Switch(haptics, { haptics = it; prefs.edit().putBoolean("haptics", it).apply() })
                                        }
                                        Column(Modifier.padding(18.dp)) {
                                            Text("Stick dead zone · ${(deadZone * 100).toInt()}%")
                                            Slider(deadZone, { deadZone = it }, onValueChangeFinished = { prefs.edit().putFloat("deadZone", deadZone).apply() }, valueRange = 0f.. .4f)
                                        }
                                    }
                                }
                                "Appearance" -> {
                                    SettingsGroup("Make it yours") {
                                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Text("Accent color", style = MaterialTheme.typography.titleMedium)
                                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                accentNames.forEachIndexed { index, name -> FilterChip(accentIndex == index, {
                                                    accentIndex = index; prefs.edit().putInt("accent", index).apply()
                                                }, label = { Text(name, color = accents[index]) }) }
                                            }
                                            Text("Control contrast · ${(opacity * 100).toInt()}%")
                                            Slider(opacity, { opacity = it }, onValueChangeFinished = { prefs.edit().putFloat("opacity", opacity).apply() }, valueRange = .5f..1f)
                                            Text("ABXY keeps Xbox-style colors. Play is always full screen; swipe an edge to reveal system bars.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF92A7BB))
                                        }
                                    }
                                    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFF090D13)) {
                                        Gamepad(Modifier.fillMaxWidth().height(230.dp), false, deadZone, LayoutPreset.STANDARD.layout(custom), accent, false, null,
                                            {}, { _, _, _ -> }, {}, opacity = opacity)
                                    }
                                }
                                "Advanced" -> {
                                    SettingsGroup("Network & diagnostics") {
                                        SettingRow("Phone UDP port", "Stop the server before changing. PC Manager's advertised port is separate.") {
                                            OutlinedTextField(port, { if (it.length <= 5 && it.all(Char::isDigit)) port = it }, enabled = !status.running,
                                                singleLine = true, isError = validPort == null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.width(130.dp), label = { Text("Port") })
                                        }
                                        SettingRow("Requests", "${status.received} received · ${status.rejected} invalid · ${status.sendFailures} send failures")
                                        SettingRow("Last send error", status.lastSendError ?: "None in this server session")
                                        SettingRow("Subscription timeout", "A PC must renew within 5 seconds. Expiry removes the peer, not the listener.")
                                        SettingRow("Foreground only", "Screen lock, another app, QR scanner or a real IPv4/network change stops the server. Return and restart explicitly.")
                                    }
                                    SettingsGroup("About & compatibility") {
                                        SettingRow("DSU Manager pairing", "QR scanning stores the PC endpoint only. The post-scan handshake is not implemented; Windows Xbox output is not verified.")
                                        SettingRow("Protocol & limits", "DSU 1001 · digital triggers · no motion, rumble or external-gamepad forwarding")
                                        SettingRow("DSU Manager guide", "Official receiver documentation", onClick = { openLink("https://github.com/breeze2/dsu-manager-guides") }) { Text("Open", color = accent) }
                                        SettingRow("Protocol guide", "Public cemuhook specification", onClick = { openLink("https://v1993.github.io/cemuhook-protocol/") }) { Text("Open", color = accent) }
                                        SettingRow("CraftController", "${BuildConfig.VERSION_NAME} · Independent implementation. Thanks to breeze2 and the cemuhook contributors.")
                                    }
                                }
                            }
                        }
                    }
                }
                else -> Box(Modifier.fillMaxSize().displayCutoutPadding()) {
                    Column(Modifier.fillMaxSize()) {
                        if (page == "editor") {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Layout studio", fontWeight = FontWeight.SemiBold)
                                    Text("Drag a control to move it", style = MaterialTheme.typography.labelSmall, color = Color(0xFF92A7BB))
                                }
                                TextButton(onClick = { showDiscard = true }) { Text("Cancel") }
                                Button(onClick = {
                                    LayoutSettings.save(prefs, draft); custom = draft; preset = LayoutPreset.CUSTOM
                                    prefs.edit().putString("preset", preset.name).apply(); page = "play"
                                }) { Text("Save layout") }
                            }
                            val current = draft.firstOrNull { it.id == selected }
                            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                                Text(current?.id ?: "Select a control", color = accent, modifier = Modifier.widthIn(min = 100.dp))
                                if (current != null) {
                                    Text("Size")
                                    Slider(current.scale, { scale -> draft = draft.map { if (it.id == selected) it.copy(scale = scale) else it } }, valueRange = .55f..1.8f, modifier = Modifier.width(160.dp))
                                    TextButton(onClick = { draft = draft.map { if (it.id == selected) it.copy(visible = !it.visible) else it } }) { Text(if (current.visible) "Hide" else "Show") }
                                }
                                TextButton(onClick = { draft = defaultLayout(); selected = null }) { Text("Reset") }
                            }
                        }
                        Gamepad(Modifier.fillMaxWidth().weight(1f), inputFocused && page == "play", deadZone,
                            if (page == "editor") draft else preset.layout(custom), accent, page == "editor", selected,
                            onSelect = { selected = it },
                            onMove = { id, x, y -> draft = draft.map { if (it.id == id) it.copy(x = x, y = y) else it } },
                            onState = { state ->
                                if (haptics && (state.buttons - previousButtons[0]).isNotEmpty()) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                                previousButtons[0] = state.buttons
                                server.input.set(if (status.running && inputFocused) state else ControllerState())
                            }, diagonals = diagonals, stickyDpad = stickyDpad, floatingSticks = floatingSticks, opacity = opacity)
                    }
                    if (page == "play") {
                        FilledTonalIconButton(onClick = ::goSettings, modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp)
                            .semantics { contentDescription = "Settings and connection" },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0xFF1A2835))) {
                            Canvas(Modifier.size(22.dp)) { drawControlSymbol("Settings", Offset(size.width / 2, size.height / 2), size.width * .40f, accent) }
                        }
                        val stateText = when {
                            !status.running -> if (status.message == "Stopped") "Offline · start in Settings" else status.message
                            status.peers > 0 -> "${status.peers} DSU subscriber(s)"
                            status.sendFailures > 0 -> "Listening · PC send failed"
                            else -> "Listening · waiting for PC"
                        }
                        Surface(onClick = ::goSettings, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp).widthIn(max = 420.dp),
                            shape = RoundedCornerShape(50), color = Color(0xFF15202C).copy(alpha = .9f)) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                Canvas(Modifier.size(6.dp)) { drawCircle(if (status.running && status.peers > 0) accent else Color(0xFFDAAF73)) }
                                Text(stateText, style = MaterialTheme.typography.labelSmall, color = Color(0xFFB2C2D2), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        if (showDiscard) AlertDialog(onDismissRequest = { showDiscard = false }, title = { Text("Discard layout changes?") },
            text = { Text("Your previously saved custom layout will not change.") },
            confirmButton = { TextButton(onClick = { showDiscard = false; page = "play" }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Keep editing") } })
        DisposableEffect(status.running) {
            if (status.running) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        DisposableEffect(page, inputFocused) {
            val bars = WindowCompat.getInsetsController(window, view)
            if (page == "play" && inputFocused) {
                bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                bars.hide(WindowInsetsCompat.Type.systemBars())
            } else bars.show(WindowInsetsCompat.Type.systemBars())
            onDispose { bars.show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
}

private fun SharedPreferences.safeBoolean(key: String, fallback: Boolean) = runCatching { getBoolean(key, fallback) }.getOrDefault(fallback)
private fun SharedPreferences.safeFloat(key: String, fallback: Float, range: ClosedFloatingPointRange<Float>) =
    runCatching { getFloat(key, fallback) }.getOrDefault(fallback).takeIf { it.isFinite() }?.coerceIn(range.start, range.endInclusive) ?: fallback

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = Color(0xFF91A7BB), modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        Surface(shape = RoundedCornerShape(20.dp), color = panelColor) { Column(Modifier.fillMaxWidth(), content = content) }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color(0xFF96AABD))
        }
        trailing()
    }
    HorizontalDivider(color = Color.White.copy(alpha = .04f))
}
