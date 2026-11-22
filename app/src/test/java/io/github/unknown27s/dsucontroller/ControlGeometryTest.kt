package io.github.unknown27s.dsucontroller

import io.github.unknown27s.dsucontroller.core.PadButton
import org.junit.Assert.*
import org.junit.Test

class ControlGeometryTest {
    @Test fun faceButtonSpacingStaysSquareAcrossAspectRatios() {
        listOf(1000f to 500f, 2220f to 1080f, 2400f to 1080f).forEach { (w, h) ->
            val g = ControlGeometry(w, h)
            assertEquals(g.x(.924f) - g.x(.86f), g.y(.43f) - g.y(.302f), .01f)
            assertEquals(64f * g.unit, g.x(.86f) - g.x(.796f), .01f)
        }
    }

    @Test fun editorCoordinatesRoundTripAcrossGripAndCenterRegions() {
        listOf(1000f to 500f, 2400f to 1080f).forEach { (w, h) ->
            val g = ControlGeometry(w, h)
            listOf(.04f, .16f, .399f, .4f, .43f, .5f, .57f, .6f, .601f, .84f, .96f).forEach {
                assertEquals(it, g.normalizedX(g.x(it)), .0001f)
                assertEquals(it, g.normalizedY(g.y(it)), .0001f)
            }
            assertTrue(g.x(.3999f) < g.x(.4f))
            assertTrue(g.x(.4f) < g.x(.4001f))
            assertTrue(g.x(.5999f) < g.x(.6f))
            assertTrue(g.x(.6f) < g.x(.6001f))
        }
    }

    @Test fun dpadSupportsNeutralCardinalAndDiagonalInput() {
        assertTrue(dpadButtons(0f, 0f, 66f, true, true).isEmpty())
        assertEquals(setOf(PadButton.UP), dpadButtons(0f, -50f, 66f, true, true))
        assertEquals(setOf(PadButton.DOWN, PadButton.RIGHT), dpadButtons(40f, 40f, 66f, true, true))
        assertEquals(setOf(PadButton.LEFT), dpadButtons(-50f, 20f, 66f, false, true))
    }

    @Test fun stickyCaptureDoesNotCreateOpposingDirections() {
        assertEquals(setOf(PadButton.RIGHT), dpadButtons(150f, 0f, 66f, true, true))
        assertTrue(dpadButtons(150f, 0f, 66f, true, false).isEmpty())
        for (x in -150..150 step 10) for (y in -150..150 step 10) {
            val held = dpadButtons(x.toFloat(), y.toFloat(), 66f, true, true)
            assertFalse(PadButton.LEFT in held && PadButton.RIGHT in held)
            assertFalse(PadButton.UP in held && PadButton.DOWN in held)
        }
    }
}
