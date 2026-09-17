package com.bepinex.android.bridge

import org.json.JSONArray
import org.json.JSONObject

/** Widget type names matching JSON `"type"` values. */
object WidgetType {
    // Layout
    const val COLUMN = "column"
    const val ROW = "row"
    const val SCROLL = "scroll"
    const val WRAP = "wrap"
    const val GROUP = "group"
    const val STACK = "stack"
    const val GRID = "grid"
    const val TABS = "tabs"
    const val COLLAPSIBLE = "collapsible"
    const val SPACER = "spacer"
    const val DIVIDER = "divider"

    // Display
    const val TEXT = "text"
    const val MARKDOWN = "markdown"
    const val IMAGE = "image"
    const val PROGRESS = "progress"
    const val BADGE = "badge"
    const val ICON = "icon"

    // Input
    const val BUTTON = "button"
    const val ICON_BUTTON = "icon_button"
    const val SWITCH = "switch"
    const val CHECKBOX = "checkbox"
    const val RADIO = "radio"
    const val RADIO_GROUP = "radio_group"
    const val SLIDER = "slider"
    const val STEPPER = "stepper"
    const val TEXT_FIELD = "text_field"
    const val NUMBER_FIELD = "number_field"
    const val SELECT = "select"
    const val LIST = "list"
    const val COLOR_PICKER = "color_picker"
    const val TOGGLE_GROUP = "toggle_group"
}

/** Option row for select, radio_group, list, and toggle_group widgets. */
data class UiOption(
    val id: String,
    val label: String,
    val subtitle: String = ""
)

/**
 * Immutable node in a TCP declarative UI tree.
 *
 * @param id Widget id used for events and [updateById].
 * @param type JSON `"type"` value; see [WidgetType].
 * @param props Converted JSON props (maps, lists, scalars, or null).
 * @param children Nested widgets.
 */
data class UiNode(
    val id: String,
    val type: String,
    val props: Map<String, Any?>,
    val children: List<UiNode> = emptyList()
) {
    /** String prop, or [default] when missing or malformed. */
    fun propString(key: String, default: String = ""): String =
        scalarString(props[key]) ?: default

    /** Boolean prop, or [default] when missing or malformed. */
    fun propBool(key: String, default: Boolean = false): Boolean =
        scalarBool(props[key], default)

    /** Double prop, or [default] when missing or malformed. */
    fun propDouble(key: String, default: Double = 0.0): Double =
        scalarDouble(props[key], default)

    /** Int prop, or [default] when missing or malformed. */
    fun propInt(key: String, default: Int = 0): Int =
        scalarInt(props[key], default)

    /** String list prop; skips malformed entries. */
    fun propStringList(key: String): List<String> = when (val value = props[key]) {
        null, JSONObject.NULL -> emptyList()
        is List<*> -> value.mapNotNull(::scalarString)
        is JSONArray -> buildList(value.length()) {
            for (i in 0 until value.length()) {
                scalarString(value.opt(i))?.let(::add)
            }
        }
        else -> scalarString(value)?.let { listOf(it) } ?: emptyList()
    }

    /**
     * Options for select / radio_group / list / toggle_group.
     *
     * Accepts a JSON array of strings or `{id, label, subtitle?}` objects.
     */
    fun propOptions(key: String = "options"): List<UiOption> =
        parseOptions(props[key])

    /** Copy with [newProps] merged over existing props. */
    fun withProps(newProps: Map<String, Any?>): UiNode =
        copy(props = props + newProps)

    /** Immutable tree update: merge [newProps] onto the first node whose [id] is [widgetId]. */
    fun updateById(widgetId: String, newProps: Map<String, Any?>): UiNode {
        if (id == widgetId) return withProps(newProps)
        var changed = false
        val updatedChildren = children.map { child ->
            val updated = child.updateById(widgetId, newProps)
            if (updated !== child) changed = true
            updated
        }
        return if (changed) copy(children = updatedChildren) else this
    }

    /** Depth-first search for [widgetId]. */
    fun findById(widgetId: String): UiNode? {
        if (id == widgetId) return this
        return children.firstNotNullOfOrNull { it.findById(widgetId) }
    }

    companion object {
        fun fromJson(obj: JSONObject): UiNode = UiNodeParser.fromJson(obj)
        fun propsFromJson(obj: JSONObject?): Map<String, Any?> = UiNodeParser.propsFromJson(obj)
    }
}

/** Parses JSON widget trees into [UiNode] instances. */
object UiNodeParser {

    /**
     * Parses one widget object.
     *
     * Missing [id] defaults to `""`, missing [type] defaults to [WidgetType.COLUMN].
     * Unknown keys are ignored. Malformed children are skipped.
     */
    fun fromJson(obj: JSONObject): UiNode {
        val id = jsonString(obj, "id", "")
        val type = jsonString(obj, "type", WidgetType.COLUMN).ifBlank { WidgetType.COLUMN }
        val props = propsFromJson(obj.optJSONObject("props"))
        val children = parseChildren(obj.optJSONArray("children"))
        return UiNode(id = id, type = type, props = props, children = children)
    }

    /**
     * Recursively converts a JSON object to a map.
     *
     * [JSONObject] → [Map], [JSONArray] → [List], [JSONObject.NULL] → `null`;
     * [Boolean], [Number], and [String] are kept as-is.
     */
    fun propsFromJson(obj: JSONObject?): Map<String, Any?> {
        if (obj == null) return emptyMap()
        return try {
            val result = LinkedHashMap<String, Any?>(obj.length())
            for (key in obj.keys()) {
                result[key] = jsonToValue(obj.opt(key))
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parseChildren(array: JSONArray?): List<UiNode> {
        if (array == null) return emptyList()
        return buildList(array.length()) {
            for (i in 0 until array.length()) {
                val child = array.optJSONObject(i) ?: continue
                val node = try {
                    fromJson(child)
                } catch (_: Exception) {
                    continue
                }
                add(node)
            }
        }
    }

    private fun jsonToValue(value: Any?): Any? = try {
        when (value) {
            null, JSONObject.NULL -> null
            is JSONObject -> propsFromJson(value)
            is JSONArray -> jsonArrayToList(value)
            is Boolean, is Number, is String -> value
            else -> value.toString()
        }
    } catch (_: Exception) {
        null
    }

    private fun jsonArrayToList(array: JSONArray): List<Any?> =
        buildList(array.length()) {
            for (i in 0 until array.length()) {
                add(jsonToValue(array.opt(i)))
            }
        }

    private fun jsonString(obj: JSONObject, key: String, default: String): String {
        if (!obj.has(key) || obj.isNull(key)) return default
        return scalarString(obj.opt(key)) ?: default
    }
}

private fun scalarString(value: Any?): String? = when (value) {
    null, JSONObject.NULL -> null
    is String -> value
    is Number, is Boolean -> value.toString()
    else -> null
}

private fun scalarBool(value: Any?, default: Boolean): Boolean = when (value) {
    null, JSONObject.NULL -> default
    is Boolean -> value
    is Number -> value.toInt() != 0
    is String -> when (value.trim().lowercase()) {
        "true", "1", "yes", "on" -> true
        "false", "0", "no", "off" -> false
        else -> default
    }
    else -> default
}

private fun scalarDouble(value: Any?, default: Double): Double = when (value) {
    null, JSONObject.NULL -> default
    is Number -> value.toDouble()
    is Boolean -> if (value) 1.0 else 0.0
    is String -> value.toDoubleOrNull() ?: default
    else -> default
}

private fun scalarInt(value: Any?, default: Int): Int = when (value) {
    null, JSONObject.NULL -> default
    is Number -> value.toInt()
    is Boolean -> if (value) 1 else 0
    is String -> value.toIntOrNull() ?: value.toDoubleOrNull()?.toInt() ?: default
    else -> default
}

private fun parseOptions(value: Any?): List<UiOption> = when (value) {
    null, JSONObject.NULL -> emptyList()
    is List<*> -> value.mapNotNull(::optionFromValue)
    is JSONArray -> buildList(value.length()) {
        for (i in 0 until value.length()) {
            optionFromValue(value.opt(i))?.let(::add)
        }
    }
    else -> optionFromValue(value)?.let { listOf(it) } ?: emptyList()
}

private fun optionFromValue(value: Any?): UiOption? = when (value) {
    null, JSONObject.NULL -> null
    is String -> value.takeIf { it.isNotEmpty() }?.let { UiOption(id = it, label = it) }
    is Number, is Boolean -> value.toString().let { UiOption(id = it, label = it) }
    is Map<*, *> -> optionFromMap(value)
    is JSONObject -> optionFromMap(UiNodeParser.propsFromJson(value))
    else -> null
}

private fun optionFromMap(map: Map<*, *>): UiOption? {
    val id = scalarString(map["id"])
    val label = scalarString(map["label"])
    val subtitle = scalarString(map["subtitle"]).orEmpty()
    val resolvedId = id ?: label ?: return null
    return UiOption(
        id = resolvedId,
        label = label ?: resolvedId,
        subtitle = subtitle
    )
}
