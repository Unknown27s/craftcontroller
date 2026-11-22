package io.github.unknown27s.dsucontroller.core

/** Protocol button positions, not Android key codes. */
enum class PadButton(val bank: Int, val mask: Int, val analogOffset: Int? = null) {
    LEFT(0, 128, 24), DOWN(0, 64, 25), RIGHT(0, 32, 26), UP(0, 16, 27),
    OPTIONS(0, 8), R3(0, 4), L3(0, 2), SHARE(0, 1),
    Y(1, 16, 31), B(1, 32, 30), A(1, 64, 29), X(1, 128, 28),
    R1(1, 8, 32), L1(1, 4, 33), R2(1, 2, 34), L2(1, 1, 35), HOME(2, 1),
}

/** Immutable snapshot; motion and touch packets are intentionally neutral in this prototype. */
data class ControllerState(
    val buttons: Set<PadButton> = emptySet(),
    val leftX: Int = 128,
    val leftY: Int = 128,
    val rightX: Int = 128,
    val rightY: Int = 128,
)
