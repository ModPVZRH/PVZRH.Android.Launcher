package com.bepinex.android.bridge

import android.graphics.Color
import org.json.JSONObject

/** Overlay panel chrome. Null color fields fall back to [UiTheme]. */
data class PanelStyle(
    val widthDp: Int = 320,
    val heightDp: Int = 420,
    val cornerRadiusDp: Float = 8f,
    /** Null uses [UiTheme.PANEL_BG]. */
    val backgroundArgb: Int? = null,
    /** Null uses [UiTheme.PRIMARY_CONTAINER]. */
    val headerArgb: Int? = null,
    /** Null uses [UiTheme.ON_PRIMARY_CONTAINER]. */
    val titleArgb: Int? = null,
    val elevationDp: Float = 16f,
)

/** Floating action button label and size. Empty [label] is filled by the host. */
data class FabStyle(
    val label: String = "",
    val sizeDp: Int = 48,
)

/** Parses plugin-supplied overlay chrome from JSON. */
object PanelStyleParser {

    /**
     * Reads panel chrome from [obj].
     *
     * Keys: `width`, `height`, `cornerRadius`, `background`, `headerColor`,
     * `titleColor`, `elevation`. Missing or invalid values use defaults.
     * Width is clamped to 200..480, height 220..640, radius 0..16, elevation 0..24.
     */
    fun panel(obj: JSONObject?): PanelStyle {
        if (obj == null) return PanelStyle()
        return PanelStyle(
            widthDp = jsonInt(obj, "width", 320).coerceIn(200, 480),
            heightDp = jsonInt(obj, "height", 420).coerceIn(220, 640),
            cornerRadiusDp = jsonFloat(obj, "cornerRadius", 8f).coerceIn(0f, 16f),
            backgroundArgb = parseColor(jsonString(obj, "background")),
            headerArgb = parseColor(jsonString(obj, "headerColor")),
            titleArgb = parseColor(jsonString(obj, "titleColor")),
            elevationDp = jsonFloat(obj, "elevation", 16f).coerceIn(0f, 24f),
        )
    }

    /** Parses `#RRGGBB` or `#AARRGGBB`. Invalid or missing values return null. */
    fun parseColor(hex: String?): Int? {
        val value = hex?.trim().orEmpty()
        if (value.length != 7 && value.length != 9) return null
        if (value[0] != '#') return null
        return try {
            Color.parseColor(value)
        } catch (_: Exception) {
            null
        }
    }

    /** Clamps [sizeDp] to 36..64 and keeps at most two characters of [label]. */
    fun fab(label: String, sizeDp: Int): FabStyle =
        FabStyle(
            label = label.trim().take(2),
            sizeDp = sizeDp.coerceIn(36, 64),
        )

    private fun jsonInt(obj: JSONObject, key: String, default: Int): Int {
        if (!obj.has(key) || obj.isNull(key)) return default
        return when (val value = obj.opt(key)) {
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
                ?: value.trim().toDoubleOrNull()?.toInt()
                ?: default
            else -> default
        }
    }

    private fun jsonFloat(obj: JSONObject, key: String, default: Float): Float {
        if (!obj.has(key) || obj.isNull(key)) return default
        val parsed = when (val value = obj.opt(key)) {
            is Number -> value.toFloat()
            is String -> value.trim().toFloatOrNull() ?: return default
            else -> return default
        }
        return if (parsed.isFinite()) parsed else default
    }

    private fun jsonString(obj: JSONObject, key: String): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        return obj.opt(key) as? String
    }
}
