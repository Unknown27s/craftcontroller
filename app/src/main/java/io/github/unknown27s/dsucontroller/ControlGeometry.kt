package io.github.unknown27s.dsucontroller

import io.github.unknown27s.dsucontroller.core.PadButton
import kotlin.math.abs
import kotlin.math.hypot

/** A height-scaled design space with clusters anchored to the two hand grips. */
internal class ControlGeometry(val width: Float, val height: Float) {
    val unit = minOf(width / 1000f, height / 500f)
    fun x(value: Float): Float = when {
        value <= .4f -> width * .16f + (value - .16f) * 1000f * unit
        value >= .6f -> width * .84f + (value - .84f) * 1000f * unit
        else -> {
            val left = width * .16f + 240f * unit
            val right = width * .84f - 240f * unit
            left + (value - .4f) / .2f * (right - left)
        }
    }
    fun y(value: Float): Float = height * .5f + (value - .5f) * 500f * unit

    // Continuous piecewise mapping; inverse is used by the layout editor.
    fun normalizedX(value: Float): Float = when {
        value <= x(.4f) -> .16f + (value - width * .16f) / (1000f * unit)
        value >= x(.6f) -> .84f + (value - width * .84f) / (1000f * unit)
        else -> .4f + (value - x(.4f)) / (x(.6f) - x(.4f)) * .2f
    }
    fun normalizedY(value: Float) = .5f + (value - height * .5f) / (500f * unit)
}

/** One pointer owns the entire D-pad, including its center and diagonal regions. */
internal fun dpadButtons(x: Float, y: Float, radius: Float, diagonals: Boolean, sticky: Boolean): Set<PadButton> {
    if (!sticky && maxOf(abs(x), abs(y)) > radius * 1.35f) return emptySet()
    if (hypot(x, y) < radius * .20f) return emptySet()
    if (!diagonals) return setOf(if (abs(x) > abs(y)) {
        if (x < 0) PadButton.LEFT else PadButton.RIGHT
    } else if (y < 0) PadButton.UP else PadButton.DOWN)
    return buildSet {
        if (abs(x) > abs(y) * .41421356f) add(if (x < 0) PadButton.LEFT else PadButton.RIGHT)
        if (abs(y) > abs(x) * .41421356f) add(if (y < 0) PadButton.UP else PadButton.DOWN)
    }
}
