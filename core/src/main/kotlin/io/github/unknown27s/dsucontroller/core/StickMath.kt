package io.github.unknown27s.dsucontroller.core

import kotlin.math.hypot
import kotlin.math.roundToInt

/** Radial dead zone with continuous rescaling; x points right and y points up. */
object StickMath {
    fun encode(x: Float, y: Float, deadZone: Float): Pair<Int, Int> {
        require(deadZone.isFinite() && deadZone in 0f..0.4f)
        if (!x.isFinite() || !y.isFinite()) return 128 to 128
        val length = hypot(x, y)
        if (length <= deadZone || length == 0f) return 128 to 128
        val magnitude = (length.coerceAtMost(1f) - deadZone) / (1f - deadZone)
        fun axis(v: Float): Int {
            val normalized = v / length * magnitude
            return (128f + normalized * if (normalized < 0) 128f else 127f).roundToInt().coerceIn(0, 255)
        }
        return axis(x) to axis(y)
    }
}
