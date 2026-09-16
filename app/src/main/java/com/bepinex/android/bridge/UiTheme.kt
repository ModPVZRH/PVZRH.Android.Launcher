package com.bepinex.android.bridge

import android.graphics.Color
import android.graphics.drawable.GradientDrawable

/**
 * Shared colors and drawables for the in-game TCP UI overlay.
 *
 * Palette matches [com.bepinex.android.log.GameLogOverlay]; [PRIMARY] is launcher mint.
 */
object UiTheme {

    val PANEL_BG: Int = Color.parseColor("#DD1E1E2E")
    val TEXT: Int = Color.parseColor("#CDD6F4")
    val TEXT_MUTED: Int = Color.parseColor("#A6ADC8")
    val ACCENT: Int = Color.parseColor("#89B4FA")
    val PRIMARY: Int = Color.parseColor("#FF00897B")
    val SURFACE: Int = Color.parseColor("#331E1E2E")
    val ERROR: Int = Color.parseColor("#FFFF6B6B")
    /** Slider track. */
    val TRACK: Int = Color.parseColor("#4489B4FA")
    val FAB_BG: Int = PRIMARY

    private const val DISABLED_ALPHA = 0.4f

    fun dp(density: Float, value: Float): Int = (value * density + 0.5f).toInt()

    /** Returns [value] unchanged; [android.widget.TextView.setTextSize] already treats it as sp. */
    @Suppress("UNUSED_PARAMETER")
    fun sp(density: Float, value: Float): Float = value

    /** Overlay panel with 10dp corners. */
    fun panelBackground(density: Float): GradientDrawable =
        roundedRect(density, PANEL_BG, cornerDp = 10f)

    /** Circular FAB filled with [FAB_BG]. */
    @Suppress("UNUSED_PARAMETER")
    fun fabBackground(density: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(FAB_BG)
        }

    fun buttonBackground(
        density: Float,
        filled: Boolean = true,
        enabled: Boolean = true,
    ): GradientDrawable {
        val alpha = if (enabled) 1f else DISABLED_ALPHA
        return if (filled) {
            roundedRect(density, withAlpha(PRIMARY, alpha), cornerDp = 8f)
        } else {
            roundedRect(
                density,
                withAlpha(SURFACE, alpha),
                cornerDp = 8f,
                strokeColor = withAlpha(PRIMARY, alpha),
            )
        }
    }

    fun groupBackground(density: Float): GradientDrawable =
        roundedRect(density, SURFACE, cornerDp = 8f, strokeColor = withAlpha(TEXT, 0.12f))

    fun inputBackground(density: Float): GradientDrawable =
        roundedRect(density, SURFACE, cornerDp = 8f, strokeColor = TEXT_MUTED)

    fun chipBackground(density: Float, selected: Boolean): GradientDrawable =
        if (selected) {
            roundedRect(density, PRIMARY, cornerDp = 16f)
        } else {
            roundedRect(density, SURFACE, cornerDp = 16f, strokeColor = TEXT_MUTED)
        }

    fun tabBackground(density: Float, selected: Boolean): GradientDrawable =
        if (selected) {
            roundedRect(density, PRIMARY, cornerDp = 8f)
        } else {
            roundedRect(density, SURFACE, cornerDp = 8f)
        }

    private fun roundedRect(
        density: Float,
        color: Int,
        cornerDp: Float,
        strokeColor: Int? = null,
        strokeDp: Float = 1f,
    ): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = cornerDp * density
        setColor(color)
        if (strokeColor != null) {
            setStroke(dp(density, strokeDp), strokeColor)
        }
    }

    private fun withAlpha(color: Int, factor: Float): Int {
        if (factor >= 1f) return color
        val a = (Color.alpha(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color))
    }
}
