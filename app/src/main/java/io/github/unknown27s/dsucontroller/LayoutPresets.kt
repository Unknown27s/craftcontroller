package io.github.unknown27s.dsucontroller

/** Screen arrangements only: these do not emulate a different hardware protocol. */
enum class LayoutPreset(val title: String, val description: String) {
    STANDARD("Xbox-style gamepad", "Offset sticks, eight-way D-pad and colored ABXY buttons"),
    COMPACT("Compact gamepad", "A simpler arrangement with fewer menu controls"),
    LEFT("Left-hand controls", "Left stick, D-pad and left shoulders"),
    RIGHT("Right-hand controls", "Right stick, face buttons and right shoulders"),
    CUSTOM("Custom layout", "Your saved positions, sizes and visibility");

    fun layout(custom: List<ControlPlacement>): List<ControlPlacement> = when (this) {
        STANDARD -> defaultLayout()
        COMPACT -> defaultLayout().map { it.copy(visible = it.id !in setOf("L3", "R3", "Share", "Home")) }
        LEFT -> defaultLayout().map {
            it.copy(visible = it.id in setOf("LS", "Dpad", "L1", "L2", "L3", "Start", "Home"))
        }
        RIGHT -> defaultLayout().map {
            it.copy(visible = it.id in setOf("RS", "A", "B", "X", "Y", "R1", "R2", "R3", "Start", "Home"))
        }
        CUSTOM -> custom
    }

    companion object {
        fun fromName(value: String?): LayoutPreset = entries.firstOrNull { it.name == value } ?: CUSTOM
    }
}
