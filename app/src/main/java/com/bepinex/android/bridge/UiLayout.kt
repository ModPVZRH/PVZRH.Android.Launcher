package com.bepinex.android.bridge

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

/** Applies optional size/padding/textSize props from a UiNode onto an Android View. */
object UiLayout {

    /**
     * Props (dp unless noted): width, height, minWidth, minHeight, maxWidth, maxHeight,
     * padding / paddingH / paddingV / paddingLeft|Top|Right|Bottom|Start|End,
     * textSize (sp). 0 or missing size = leave default.
     */
    fun apply(view: View, node: UiNode, density: Float) {
        fun dp(value: Int): Int = UiTheme.dp(density, value.toFloat())

        val width = node.propInt("width", 0)
        val height = node.propInt("height", 0)
        if (width > 0 || height > 0) {
            val existing = view.layoutParams
            val w = if (width > 0) dp(width) else existing?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT
            val h = if (height > 0) dp(height) else existing?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT
            if (existing != null) {
                existing.width = w
                existing.height = h
                view.layoutParams = existing
            } else {
                view.layoutParams = ViewGroup.LayoutParams(w, h)
            }
        }

        val minWidth = node.propInt("minWidth", 0)
        if (minWidth > 0) view.minimumWidth = dp(minWidth)
        val minHeight = node.propInt("minHeight", 0)
        if (minHeight > 0) view.minimumHeight = dp(minHeight)

        val maxWidth = node.propInt("maxWidth", 0)
        val maxHeight = node.propInt("maxHeight", 0)
        if (maxWidth > 0 || maxHeight > 0) {
            when (view) {
                is TextView -> {
                    if (maxWidth > 0) view.maxWidth = dp(maxWidth)
                    if (maxHeight > 0) view.maxHeight = dp(maxHeight)
                }
                is ImageView -> {
                    if (maxWidth > 0) view.maxWidth = dp(maxWidth)
                    if (maxHeight > 0) view.maxHeight = dp(maxHeight)
                }
            }
        }

        applyPadding(view, node, density)

        if (view is TextView) {
            val textSize = node.propDouble("textSize", 0.0)
            if (textSize > 0.0) view.textSize = textSize.toFloat()
        }
    }

    /**
     * Writes margin props onto [lp] (dp): `margin`, `marginH`, `marginV`,
     * `marginLeft` / `marginTop` / `marginRight` / `marginBottom`,
     * `marginStart` / `marginEnd`. Unspecified sides stay as they are.
     */
    fun applyMargin(lp: ViewGroup.MarginLayoutParams, node: UiNode, density: Float) {
        val box = resolveBox(node, "margin") ?: return
        fun px(value: Int): Int = UiTheme.dp(density, value.toFloat())
        if (box.left >= 0) lp.leftMargin = px(box.left)
        if (box.top >= 0) lp.topMargin = px(box.top)
        if (box.right >= 0) lp.rightMargin = px(box.right)
        if (box.bottom >= 0) lp.bottomMargin = px(box.bottom)
    }

    private fun applyPadding(view: View, node: UiNode, density: Float) {
        val box = resolveBox(node, "padding") ?: return
        fun px(value: Int): Int = UiTheme.dp(density, value.toFloat())
        view.setPadding(
            if (box.left >= 0) px(box.left) else view.paddingLeft,
            if (box.top >= 0) px(box.top) else view.paddingTop,
            if (box.right >= 0) px(box.right) else view.paddingRight,
            if (box.bottom >= 0) px(box.bottom) else view.paddingBottom,
        )
    }

    private data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

    /** -1 means unspecified. */
    private fun resolveBox(node: UiNode, prefix: String): Box? {
        fun side(name: String): Int = node.propInt(name, -1)
        val all = side(prefix)
        var left = all
        var top = all
        var right = all
        var bottom = all
        val horizontal = side(prefix + "H")
        val vertical = side(prefix + "V")
        if (horizontal >= 0) {
            left = horizontal
            right = horizontal
        }
        if (vertical >= 0) {
            top = vertical
            bottom = vertical
        }
        val explicitLeft = side(prefix + "Left")
        val explicitTop = side(prefix + "Top")
        val explicitRight = side(prefix + "Right")
        val explicitBottom = side(prefix + "Bottom")
        val start = side(prefix + "Start")
        val end = side(prefix + "End")
        if (explicitLeft >= 0) left = explicitLeft
        if (explicitTop >= 0) top = explicitTop
        if (explicitRight >= 0) right = explicitRight
        if (explicitBottom >= 0) bottom = explicitBottom
        if (start >= 0) left = start
        if (end >= 0) right = end
        if (left < 0 && top < 0 && right < 0 && bottom < 0) return null
        return Box(left, top, right, bottom)
    }
}
