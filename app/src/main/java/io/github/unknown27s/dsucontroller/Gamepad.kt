package io.github.unknown27s.dsucontroller

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import io.github.unknown27s.dsucontroller.core.ControllerState
import io.github.unknown27s.dsucontroller.core.PadButton
import io.github.unknown27s.dsucontroller.core.StickMath
import kotlin.math.abs

private data class Control(val id: String, val x: Float, val y: Float, val button: PadButton? = null)
private val controls = listOf(
    Control("L2", .075f, .13f, PadButton.L2), Control("L1", .23f, .13f, PadButton.L1),
    Control("R1", .77f, .13f, PadButton.R1), Control("R2", .925f, .13f, PadButton.R2),
    Control("Dpad", .32f, .72f),
    Control("Y", .86f, .302f, PadButton.Y), Control("X", .796f, .43f, PadButton.X),
    Control("B", .924f, .43f, PadButton.B), Control("A", .86f, .558f, PadButton.A),
    Control("Share", .43f, .24f, PadButton.SHARE), Control("Home", .50f, .26f, PadButton.HOME),
    Control("Start", .57f, .24f, PadButton.OPTIONS),
    Control("L3", .135f, .85f, PadButton.L3), Control("R3", .865f, .85f, PadButton.R3),
    Control("LS", .135f, .43f), Control("RS", .68f, .72f),
)
private val definitions = controls.associateBy { it.id }
private val shoulders = setOf("L1", "L2", "R1", "R2")
private val menuControls = setOf("Share", "Home", "Start")

fun defaultLayout(): List<ControlPlacement> = controls.map { ControlPlacement(it.id, it.x, it.y) }

private fun ControlPlacement.center(g: ControlGeometry) = Offset(g.x(x), g.y(y))
private fun controlSize(id: String): Size = when (id) {
    "LS", "RS" -> Size(152f, 152f)
    "Dpad" -> Size(132f, 132f)
    "L1", "R1" -> Size(110f, 44f)
    "L2", "R2" -> Size(80f, 58f)
    "Share", "Start" -> Size(48f, 48f)
    "Home" -> Size(58f, 58f)
    "L3", "R3" -> Size(52f, 42f)
    else -> Size(60f, 60f)
}

private fun hit(placement: ControlPlacement, point: Offset, g: ControlGeometry): Boolean {
    val delta = point - placement.center(g)
    val shape = controlSize(placement.id) * (placement.scale * g.unit)
    return if (placement.id in shoulders || placement.id == "Dpad" || placement.id in setOf("L3", "R3")) {
        abs(delta.x) <= shape.width * .58f && abs(delta.y) <= shape.height * .62f
    } else delta.getDistance() <= shape.width * .58f
}

/** Independent captured pointers; visuals and hit regions share one geometry model. */
@Composable
fun Gamepad(
    modifier: Modifier,
    enabled: Boolean,
    deadZone: Float,
    layout: List<ControlPlacement>,
    accent: Color,
    editing: Boolean,
    selected: String?,
    onSelect: (String) -> Unit,
    onMove: (String, Float, Float) -> Unit,
    onState: (ControllerState) -> Unit,
    diagonals: Boolean = true,
    stickyDpad: Boolean = true,
    floatingSticks: Boolean = false,
    opacity: Float = 1f,
) {
    var dimensions by remember { mutableStateOf(IntSize.Zero) }
    var snapshot by remember { mutableStateOf(ControllerState()) }
    var stickOrigins by remember { mutableStateOf(emptyMap<String, Offset>()) }
    val latestOnState by rememberUpdatedState(onState)
    val latestLayout by rememberUpdatedState(layout)
    val latestMove by rememberUpdatedState(onMove)
    val latestSelect by rememberUpdatedState(onSelect)
    fun publish(value: ControllerState) { snapshot = value; latestOnState(value) }
    DisposableEffect(Unit) { onDispose { latestOnState(ControllerState()) } }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    } }
    Canvas(modifier.onSizeChanged { dimensions = it }
        .semantics { contentDescription = if (editing) "Layout editor: drag a control to move it" else "Multitouch gamepad" }
        .pointerInput(enabled, deadZone, dimensions, editing, diagonals, stickyDpad, floatingSticks, if (editing) null else layout) {
            if (!enabled && !editing) { publish(ControllerState()); return@pointerInput }
            if (dimensions.width == 0 || dimensions.height == 0) return@pointerInput
            val g = ControlGeometry(dimensions.width.toFloat(), dimensions.height.toFloat())
            val owners = mutableMapOf<PointerId, String>()
            val grabs = mutableMapOf<PointerId, Offset>()
            val origins = mutableMapOf<String, Offset>()
            try {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val held = mutableSetOf<PadButton>()
                        var lx = 128; var ly = 128; var rx = 128; var ry = 128
                        for (change in event.changes) {
                            if (!change.pressed) {
                                owners.remove(change.id)?.let { origins.remove(it) }
                                grabs.remove(change.id)
                                continue
                            }
                            if (!change.previousPressed && !change.isConsumed) {
                                // Prefer the nearest eligible control instead of list-order overlap wins.
                                latestLayout.filter { (editing || it.visible) && it.id !in owners.values && hit(it, change.position, g) }
                                    .minByOrNull { (change.position - it.center(g)).getDistance() }?.let {
                                        owners[change.id] = it.id
                                        grabs[change.id] = change.position - it.center(g)
                                        if (editing) latestSelect(it.id)
                                        else if (floatingSticks && it.id in setOf("LS", "RS")) origins[it.id] = change.position
                                    }
                            }
                            val id = owners[change.id] ?: continue
                            val placement = latestLayout.first { it.id == id }
                            val delta = change.position - (origins[id] ?: placement.center(g))
                            if (editing) {
                                val center = change.position - (grabs[change.id] ?: Offset.Zero)
                                latestMove(id, g.normalizedX(center.x).coerceIn(.04f, .96f), g.normalizedY(center.y).coerceIn(.08f, .92f))
                            } else when (id) {
                                "Dpad" -> held.addAll(dpadButtons(delta.x, delta.y, 66f * g.unit * placement.scale, diagonals, stickyDpad))
                                "LS", "RS" -> {
                                    val travel = 43f * g.unit * placement.scale
                                    val (x, y) = StickMath.encode(delta.x / travel, -delta.y / travel, deadZone)
                                    if (id == "LS") { lx = x; ly = y } else { rx = x; ry = y }
                                }
                                else -> if (hit(placement.copy(scale = placement.scale * 1.15f), change.position, g)) {
                                    definitions[id]?.button?.let(held::add)
                                }
                            }
                            change.consume()
                        }
                        stickOrigins = origins.toMap()
                        publish(if (editing) ControllerState() else ControllerState(held, lx, ly, rx, ry))
                    }
                }
            } finally { stickOrigins = emptyMap(); publish(ControllerState()) }
        }) {
        val g = ControlGeometry(size.width, size.height)
        drawRect(Color(0xFF090D13))
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .065f), Color.Transparent),
            Offset(size.width * .13f, size.height * .50f), size.height * .75f), size.height * .75f,
            Offset(size.width * .13f, size.height * .50f))
        drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = .045f), Color.Transparent),
            Offset(size.width * .87f, size.height * .50f), size.height * .75f), size.height * .75f,
            Offset(size.width * .87f, size.height * .50f))
        if (editing) {
            for (i in 1..9) drawLine(Color.White.copy(alpha = .04f), Offset(size.width * i / 10, 0f), Offset(size.width * i / 10, size.height))
            for (i in 1..4) drawLine(Color.White.copy(alpha = .04f), Offset(0f, size.height * i / 5), Offset(size.width, size.height * i / 5))
        }
        for (placement in layout) {
            if (!placement.visible && !editing) continue
            val center = placement.center(g)
            val u = g.unit * placement.scale
            val alpha = if (!placement.visible) .25f else opacity
            val shape = controlSize(placement.id) * u
            val r = shape.width / 2f
            val pressed = definitions[placement.id]?.button in snapshot.buttons
            val ink = when (placement.id) {
                "A" -> Color(0xFF8BE36B); "B" -> Color(0xFFFF787B)
                "X" -> Color(0xFF70BBFF); "Y" -> Color(0xFFFFD770)
                else -> accent
            }
            if (editing && selected == placement.id) drawRoundRect(accent, center - Offset(shape.width / 2 + 6 * u, shape.height / 2 + 6 * u),
                Size(shape.width + 12 * u, shape.height + 12 * u), CornerRadius(16 * u), style = Stroke(2 * u))
            when (placement.id) {
                "Dpad" -> drawDpad(center, 66f * u, snapshot.buttons, accent, alpha)
                "LS", "RS" -> {
                    val base = stickOrigins[placement.id] ?: center
                    drawCircle(Color(0xFF05090F).copy(alpha = alpha), r, base)
                    drawCircle(Color(0xFF263443).copy(alpha = alpha), r, base, style = Stroke(2 * u))
                    drawCircle(accent.copy(alpha = .13f * alpha), r * .80f, base, style = Stroke(u))
                    val x = if (placement.id == "LS") snapshot.leftX else snapshot.rightX
                    val y = if (placement.id == "LS") snapshot.leftY else snapshot.rightY
                    val vector = Offset((x - 128) / 128f, -(y - 128) / 128f)
                    val knob = base + vector * 30f * u
                    val active = x != 128 || y != 128
                    if (active) drawCircle(accent.copy(alpha = .12f * alpha), r + 7 * u, base)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF394859), Color(0xFF202C39)), knob - Offset(12 * u, 12 * u), r), r * .58f, knob, alpha = alpha)
                    drawCircle((if (active) accent else Color(0xFF62778A)).copy(alpha = alpha), r * .58f, knob, style = Stroke(2 * u))
                    drawCircle(Color.White.copy(alpha = .10f * alpha), r * .43f, knob, style = Stroke(u))
                    drawCircle(accent.copy(alpha = (if (active) .9f else .30f) * alpha), 3 * u, knob)
                }
                else -> {
                    val rectangular = placement.id in shoulders || placement.id in setOf("L3", "R3")
                    val fill = if (pressed) ink.copy(alpha = .28f * alpha) else Color(0xFF1B2632).copy(alpha = alpha)
                    val line = ink.copy(alpha = (if (pressed) 1f else if (placement.id in menuControls) .22f else .48f) * alpha)
                    if (pressed) {
                        if (rectangular) drawRoundRect(ink.copy(alpha = .09f * alpha), center - Offset(shape.width / 2 + 5 * u, shape.height / 2 + 5 * u),
                            Size(shape.width + 10 * u, shape.height + 10 * u), CornerRadius(16 * u))
                        else drawCircle(ink.copy(alpha = .12f * alpha), r + 6 * u, center)
                    }
                    if (rectangular) {
                        drawRoundRect(fill, center - Offset(shape.width / 2, shape.height / 2), shape, CornerRadius(13 * u))
                        drawRoundRect(line, center - Offset(shape.width / 2, shape.height / 2), shape, CornerRadius(13 * u), style = Stroke((if (pressed) 2.5f else 1.2f) * u))
                        if (placement.id.endsWith("2")) drawRoundRect(ink.copy(alpha = (if (pressed) 1f else .28f) * alpha),
                            center + Offset(-shape.width * .20f, shape.height * .30f), Size(shape.width * .4f, 3 * u), CornerRadius(2 * u))
                    } else {
                        drawCircle(Color.Black.copy(alpha = .3f * alpha), r, center + Offset(0f, 3 * u))
                        drawCircle(fill, r, center)
                        drawCircle(line, r, center, style = Stroke((if (pressed) 2.5f else 1.2f) * u))
                    }
                    if (placement.id in menuControls) drawControlSymbol(placement.id, center, 12f * u, (if (pressed) Color.White else Color(0xFFB3C2D1)).copy(alpha = alpha))
                    else {
                        val label = when (placement.id) { "L1" -> "LB"; "R1" -> "RB"; "L2" -> "LT"; "R2" -> "RT"; else -> placement.id }
                        paint.textSize = (if (placement.id in setOf("A", "B", "X", "Y")) 25f else 16f) * u
                        paint.color = android.graphics.Color.WHITE
                        paint.alpha = (alpha * 255).toInt()
                        val textColor = if (pressed) Color.White else if (placement.id in setOf("A", "B", "X", "Y")) ink else Color(0xFFCCD8E4)
                        paint.color = android.graphics.Color.argb((alpha * 255).toInt(), (textColor.red * 255).toInt(), (textColor.green * 255).toInt(), (textColor.blue * 255).toInt())
                        drawContext.canvas.nativeCanvas.drawText(label, center.x, center.y - (paint.ascent() + paint.descent()) / 2f, paint)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawDpad(center: Offset, r: Float, held: Set<PadButton>, accent: Color, alpha: Float) {
    drawCircle(Color(0xFF060B11).copy(alpha = alpha), r * 1.12f, center)
    drawCircle(Color(0xFF273747).copy(alpha = .65f * alpha), r * 1.12f, center, style = Stroke(r * .015f))
    val a = r * .34f
    val points = listOf(Offset(-a, -r), Offset(a, -r), Offset(a, -a), Offset(r, -a), Offset(r, a), Offset(a, a),
        Offset(a, r), Offset(-a, r), Offset(-a, a), Offset(-r, a), Offset(-r, -a), Offset(-a, -a))
    val path = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(center.x + p.x, center.y + p.y) else lineTo(center.x + p.x, center.y + p.y) }; close() }
    drawPath(path, Color(0xFF24313F).copy(alpha = alpha))
    drawPath(path, Color(0xFF66798B).copy(alpha = .7f * alpha), style = Stroke(r * .018f, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    listOf(PadButton.UP, PadButton.RIGHT, PadButton.DOWN, PadButton.LEFT).forEachIndexed { index, button ->
        rotate(index * 90f, center) {
            if (button in held) drawRoundRect(accent.copy(alpha = .55f * alpha), center + Offset(-a + r * .04f, -r + r * .04f),
                Size(a * 2 - r * .08f, r - a + r * .12f), CornerRadius(r * .08f))
            val y = center.y - r * .68f
            val arrow = Path().apply { moveTo(center.x - r * .105f, y + r * .06f); lineTo(center.x, y - r * .06f); lineTo(center.x + r * .105f, y + r * .06f) }
            drawPath(arrow, (if (button in held) Color.White else Color(0xFFB9C9D8)).copy(alpha = alpha), style = Stroke(r * .035f, cap = StrokeCap.Round))
        }
    }
    drawCircle(Color(0xFF101923).copy(alpha = alpha), r * .17f, center)
}

/** Original vector marks, not font glyphs or third-party controller artwork. */
internal fun DrawScope.drawControlSymbol(id: String, center: Offset, r: Float, color: Color) {
    val stroke = Stroke(r * .15f, cap = StrokeCap.Round)
    when (id) {
        "Start" -> for (i in -1..1) drawLine(color, center + Offset(-r * .7f, i * r * .5f), center + Offset(r * .7f, i * r * .5f), r * .15f, StrokeCap.Round)
        "Share" -> {
            drawRoundRect(color, center + Offset(-r * .75f, -r * .65f), Size(r * 1.1f, r * .9f), CornerRadius(r * .12f), style = stroke)
            drawRoundRect(Color(0xFF1B2632), center + Offset(-r * .20f, -r * .10f), Size(r * 1.1f, r * .9f), CornerRadius(r * .12f))
            drawRoundRect(color, center + Offset(-r * .20f, -r * .10f), Size(r * 1.1f, r * .9f), CornerRadius(r * .12f), style = stroke)
        }
        "Home" -> {
            val roof = Path().apply { moveTo(center.x - r * .8f, center.y); lineTo(center.x, center.y - r * .7f); lineTo(center.x + r * .8f, center.y) }
            drawPath(roof, color, style = stroke)
            drawLine(color, center + Offset(-r * .55f, -r * .05f), center + Offset(-r * .55f, r * .65f), r * .15f, StrokeCap.Round)
            drawLine(color, center + Offset(r * .55f, -r * .05f), center + Offset(r * .55f, r * .65f), r * .15f, StrokeCap.Round)
            drawLine(color, center + Offset(-r * .55f, r * .65f), center + Offset(r * .55f, r * .65f), r * .15f, StrokeCap.Round)
        }
        "Settings" -> {
            drawCircle(color, r * .58f, center, style = Stroke(r * .22f))
            drawCircle(color, r * .14f, center)
            repeat(8) { i -> rotate(i * 45f, center) { drawLine(color, center + Offset(0f, -r * .73f), center + Offset(0f, -r), r * .25f, StrokeCap.Round) } }
        }
    }
}
