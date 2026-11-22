package io.github.unknown27s.dsucontroller

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutSettingsTest {
    @Test fun oldFourPartDpadMigratesWithoutDiscardingOtherControls() {
        val legacy = defaultLayout().filter { it.id != "Dpad" }.map { it.copy(scale = 1.3f, visible = it.id != "L2") } +
            listOf(ControlPlacement("↑", .3f, .5f), ControlPlacement("←", .2f, .6f),
                ControlPlacement("→", .4f, .6f), ControlPlacement("↓", .3f, .7f))
        val root = JSONObject(LayoutSettings.encode(legacy)).put("version", 1)
        val converted = LayoutSettings.decode(root.toString())
        assertEquals(defaultLayout().size, converted.size)
        assertEquals(legacy.first { it.id == "LS" }, converted.first { it.id == "LS" })
        assertEquals(false, converted.first { it.id == "L2" }.visible)
        val dpad = converted.first { it.id == "Dpad" }
        assertEquals(.3f, dpad.x, .0001f)
        assertEquals(.6f, dpad.y, .0001f)
        assertEquals(converted, LayoutSettings.decode(LayoutSettings.encode(converted)))
    }

    @Test fun customLayoutRoundTripsPositionSizeAndVisibility() {
        val custom = defaultLayout().mapIndexed { i, control ->
            control.copy(x = .2f + i * .02f, y = .5f, scale = 1.4f, visible = i % 2 == 0)
        }
        assertEquals(custom, LayoutSettings.decode(LayoutSettings.encode(custom)))
    }

    @Test fun missingCorruptOrUnsupportedLayoutsRecoverToDefaults() {
        listOf(null, "", "garbage", "{}", "{\"version\":99}").forEach {
            assertEquals(defaultLayout(), LayoutSettings.decode(it))
        }
    }

    @Test fun invalidCoordinatesAndUnknownControlsAreRejected() {
        for ((field, value) in listOf("x" to -1, "y" to 9, "scale" to 0, "id" to "unknown")) {
            val root = JSONObject(LayoutSettings.encode(defaultLayout()))
            root.getJSONArray("controls").getJSONObject(0).put(field, value)
            assertEquals(defaultLayout(), LayoutSettings.decode(root.toString()))
        }
        val root = JSONObject(LayoutSettings.encode(defaultLayout()))
        val controls = root.getJSONArray("controls")
        controls.getJSONObject(0).put("id", controls.getJSONObject(1).getString("id"))
        assertEquals(defaultLayout(), LayoutSettings.decode(root.toString()))
    }
}
