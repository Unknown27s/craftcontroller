package io.github.unknown27s.dsucontroller

import org.junit.Assert.*
import org.junit.Test

class LayoutPresetsTest {
    @Test fun presetsKeepEveryControlIdAndDoNotModifyCustomLayout() {
        val custom = defaultLayout().map { it.copy(scale = 1.5f) }
        for (preset in LayoutPreset.entries) {
            val layout = preset.layout(custom)
            assertEquals(defaultLayout().map { it.id }, layout.map { it.id })
            assertTrue(layout.any { it.visible })
        }
        assertEquals(custom, LayoutPreset.CUSTOM.layout(custom))
        assertTrue(custom.all { it.scale == 1.5f })
    }

    @Test fun halfLayoutsExposeOnlyTheirIntendedInputs() {
        val left = LayoutPreset.LEFT.layout(defaultLayout()).filter { it.visible }.map { it.id }
        val right = LayoutPreset.RIGHT.layout(defaultLayout()).filter { it.visible }.map { it.id }
        assertTrue("LS" in left && "RS" !in left && "A" !in left)
        assertTrue("RS" in right && "LS" !in right && "Dpad" !in right)
        assertTrue("Dpad" in left)
        assertEquals(LayoutPreset.CUSTOM, LayoutPreset.fromName("unknown"))
        assertEquals(LayoutPreset.COMPACT, LayoutPreset.fromName("COMPACT"))
    }
}
