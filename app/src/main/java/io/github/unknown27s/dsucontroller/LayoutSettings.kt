package io.github.unknown27s.dsucontroller

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Normalized coordinates keep the saved layout independent of screen pixels. */
data class ControlPlacement(
    val id: String,
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val visible: Boolean = true,
)

/** Versioned, local-only layout persistence. Invalid data falls back to the default layout. */
object LayoutSettings {
    fun load(prefs: SharedPreferences): List<ControlPlacement> = decode(runCatching { prefs.getString("layout", null) }.getOrNull())

    internal fun decode(json: String?): List<ControlPlacement> = runCatching {
        val root = JSONObject(json ?: return defaultLayout())
        val version = root.getInt("version")
        require(version in 1..2)
        val entries = root.getJSONArray("controls")
        val legacyDirections = setOf("↑", "←", "→", "↓")
        require(entries.length() == defaultLayout().size + if (version == 1) 3 else 0)
        val layout = (0 until entries.length()).map { index ->
            val item = entries.getJSONObject(index)
            ControlPlacement(
                item.getString("id"), item.getDouble("x").toFloat(), item.getDouble("y").toFloat(),
                item.getDouble("scale").toFloat(), item.getBoolean("visible"),
            ).also {
                require(it.x.isFinite() && it.x in 0.04f..0.96f)
                require(it.y.isFinite() && it.y in 0.08f..0.92f)
                require(it.scale.isFinite() && it.scale in 0.55f..1.8f)
            }
        }
        val expected = if (version == 1) defaultLayout().map { it.id }.toSet() - "Dpad" + legacyDirections else defaultLayout().map { it.id }.toSet()
        require(layout.map { it.id }.toSet() == expected)
        require(layout.map { it.id }.distinct().size == layout.size)
        if (version == 1) {
            val pads = layout.filter { it.id in legacyDirections }
            val group = ControlPlacement("Dpad", pads.map { it.x }.average().toFloat(), pads.map { it.y }.average().toFloat(),
                pads.map { it.scale }.average().toFloat(), pads.any { it.visible })
            val converted = (layout.filter { it.id !in legacyDirections } + group).associateBy { it.id }
            defaultLayout().map { converted.getValue(it.id) }
        } else layout
    }.getOrElse { defaultLayout() }

    fun save(prefs: SharedPreferences, layout: List<ControlPlacement>) {
        prefs.edit().putString("layout", encode(layout)).apply()
    }

    internal fun encode(layout: List<ControlPlacement>): String {
        val controls = JSONArray()
        layout.forEach {
            controls.put(JSONObject().put("id", it.id).put("x", it.x).put("y", it.y)
                .put("scale", it.scale).put("visible", it.visible))
        }
        return JSONObject().put("version", 2).put("controls", controls).toString()
    }
}
