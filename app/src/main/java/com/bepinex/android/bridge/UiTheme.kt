package com.bepinex.android.bridge

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.util.StateSet
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import com.bepinex.android.settings.AppSettings

/**
 * In-game overlay palette matching [com.bepinex.android.ui.theme.BepInExTheme].
 *
 * Call [bind] before inflating views so light/dark follows launcher settings.
 */
object UiTheme {

    private data class Palette(
        val panel: Int,
        val surface: Int,
        val surfaceVariant: Int,
        val onSurface: Int,
        val onSurfaceVariant: Int,
        val primary: Int,
        val onPrimary: Int,
        val primaryContainer: Int,
        val onPrimaryContainer: Int,
        val outline: Int,
        val error: Int,
        val track: Int,
    )

    private val Dark = Palette(
        panel = Color.argb(0xF2, 0x19, 0x1C, 0x1B),
        surface = Color.argb(0xFF, 0x19, 0x1C, 0x1B),
        surfaceVariant = Color.argb(0xFF, 0x3F, 0x49, 0x46),
        onSurface = Color.parseColor("#E1E3E1"),
        onSurfaceVariant = Color.parseColor("#BEC9C6"),
        primary = Color.parseColor("#80CBC4"),
        onPrimary = Color.parseColor("#003731"),
        primaryContainer = Color.parseColor("#005047"),
        onPrimaryContainer = Color.parseColor("#B2DFDB"),
        outline = Color.parseColor("#899390"),
        error = Color.parseColor("#FFB4AB"),
        track = Color.argb(0x66, 0x80, 0xCB, 0xC4),
    )

    private val Light = Palette(
        panel = Color.argb(0xF5, 0xF5, 0xFB, 0xF9),
        surface = Color.parseColor("#F5FBF9"),
        surfaceVariant = Color.parseColor("#DAE5E2"),
        onSurface = Color.parseColor("#191C1B"),
        onSurfaceVariant = Color.parseColor("#3F4946"),
        primary = Color.parseColor("#00897B"),
        onPrimary = Color.WHITE,
        primaryContainer = Color.parseColor("#B2DFDB"),
        onPrimaryContainer = Color.parseColor("#00332E"),
        outline = Color.parseColor("#6F7976"),
        error = Color.parseColor("#BA1A1A"),
        track = Color.argb(0x66, 0x00, 0x89, 0x7B),
    )

    var PANEL_BG: Int = Dark.panel
        private set
    var SURFACE: Int = Dark.surface
        private set
    var SURFACE_VARIANT: Int = Dark.surfaceVariant
        private set
    var TEXT: Int = Dark.onSurface
        private set
    var TEXT_MUTED: Int = Dark.onSurfaceVariant
        private set
    var PRIMARY: Int = Dark.primary
        private set
    var ON_PRIMARY: Int = Dark.onPrimary
        private set
    var PRIMARY_CONTAINER: Int = Dark.primaryContainer
        private set
    var ON_PRIMARY_CONTAINER: Int = Dark.onPrimaryContainer
        private set
    var OUTLINE: Int = Dark.outline
        private set
    var ERROR: Int = Dark.error
        private set
    var TRACK: Int = Dark.track
        private set
    var ACCENT: Int = Dark.primary
        private set
    var FAB_BG: Int = Dark.primary
        private set

    private const val DISABLED_ALPHA = 0.4f

    object Metrics {
        const val BUTTON_MIN_H = 32
        const val ROW_MIN_H = 36
        const val INPUT_PAD = 8
        const val INPUT_MIN_H = 32
        const val INPUT_TEXT = 13f
        const val GROUP_PAD = 8
        const val GROUP_BODY_PAD = 12
        const val LIST_ITEM_PAD = 8
        const val FAB_DEFAULT = 48
        const val TAB_MIN_H = 36
        const val TAB_TEXT = 12f
        const val TAB_INDICATOR_H = 3
        const val TAB_BAR_RADIUS = 12f
    }

    fun bind(context: Context) {
        val app = context.applicationContext
        val dark = isDark(context)
        if (AppSettings.isDynamicColorEnabled(app) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scheme = if (dark) dynamicDarkColorScheme(app) else dynamicLightColorScheme(app)
            val surface = scheme.surface.toArgb()
            val primary = scheme.primary.toArgb()
            apply(
                Palette(
                    panel = Color.argb(
                        if (dark) 0xF2 else 0xF5,
                        Color.red(surface),
                        Color.green(surface),
                        Color.blue(surface),
                    ),
                    surface = surface,
                    surfaceVariant = scheme.surfaceVariant.toArgb(),
                    onSurface = scheme.onSurface.toArgb(),
                    onSurfaceVariant = scheme.onSurfaceVariant.toArgb(),
                    primary = primary,
                    onPrimary = scheme.onPrimary.toArgb(),
                    primaryContainer = scheme.primaryContainer.toArgb(),
                    onPrimaryContainer = scheme.onPrimaryContainer.toArgb(),
                    outline = scheme.outline.toArgb(),
                    error = scheme.error.toArgb(),
                    track = Color.argb(0x66, Color.red(primary), Color.green(primary), Color.blue(primary)),
                )
            )
        } else {
            apply(if (dark) Dark else Light)
        }
    }

    fun dp(density: Float, value: Float): Int = (value * density + 0.5f).toInt()

    @Suppress("UNUSED_PARAMETER")
    fun sp(density: Float, value: Float): Float = value

    fun panelBackground(density: Float): GradientDrawable =
        roundedRect(density, PANEL_BG, cornerDp = 16f)

    @Suppress("UNUSED_PARAMETER")
    fun fabBackground(density: Float): GradientDrawable =
        fabDrawable(density, PRIMARY, FabShape.CIRCLE, 12f)

    fun fabDrawable(
        density: Float,
        color: Int,
        shape: FabShape,
        cornerRadiusDp: Float = 12f,
    ): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        when (shape) {
            FabShape.CIRCLE -> {
                this.shape = GradientDrawable.OVAL
            }
            FabShape.SQUARE -> {
                this.shape = GradientDrawable.RECTANGLE
                cornerRadius = 0f
            }
            FabShape.ROUNDED -> {
                this.shape = GradientDrawable.RECTANGLE
                cornerRadius = cornerRadiusDp * density
            }
        }
    }

    fun buttonBackground(
        density: Float,
        filled: Boolean = true,
        enabled: Boolean = true,
    ): Drawable {
        val alpha = if (enabled) 1f else DISABLED_ALPHA
        val rest = if (filled) {
            roundedRect(density, withAlpha(PRIMARY, alpha), cornerDp = 8f)
        } else {
            roundedRect(
                density,
                withAlpha(SURFACE, alpha),
                cornerDp = 8f,
                strokeColor = withAlpha(PRIMARY, alpha),
            )
        }
        if (!enabled) return rest

        val pressed = if (filled) {
            roundedRect(density, darken(PRIMARY, 0.78f), cornerDp = 8f)
        } else {
            roundedRect(
                density,
                withAlpha(PRIMARY, 0.22f),
                cornerDp = 8f,
                strokeColor = PRIMARY,
            )
        }
        val states = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), pressed)
            addState(StateSet.WILD_CARD, rest)
        }
        val rippleColor = if (filled) {
            withAlpha(Color.WHITE, 0.35f)
        } else {
            withAlpha(PRIMARY, 0.32f)
        }
        val mask = roundedRect(density, Color.WHITE, cornerDp = 8f)
        return RippleDrawable(ColorStateList.valueOf(rippleColor), states, mask)
    }

    fun buttonText(filled: Boolean): Int = if (filled) ON_PRIMARY else PRIMARY

    fun groupBackground(density: Float): GradientDrawable =
        roundedRect(density, SURFACE_VARIANT, cornerDp = 12f, strokeColor = withAlpha(OUTLINE, 0.5f))

    fun inputBackground(density: Float): GradientDrawable =
        roundedRect(density, SURFACE_VARIANT, cornerDp = 8f, strokeColor = OUTLINE)

    fun chipBackground(density: Float, selected: Boolean): GradientDrawable =
        if (selected) {
            roundedRect(density, PRIMARY_CONTAINER, cornerDp = 16f)
        } else {
            roundedRect(density, SURFACE_VARIANT, cornerDp = 16f, strokeColor = OUTLINE)
        }

    fun chipText(selected: Boolean): Int =
        if (selected) ON_PRIMARY_CONTAINER else TEXT

    fun tabBarBackground(density: Float): GradientDrawable =
        roundedRect(density, SURFACE_VARIANT, cornerDp = Metrics.TAB_BAR_RADIUS)

    fun tabBackground(density: Float, selected: Boolean): GradientDrawable =
        if (selected) {
            roundedRect(density, PRIMARY_CONTAINER, cornerDp = 8f)
        } else {
            roundedRect(density, SURFACE_VARIANT, cornerDp = 8f)
        }

    fun tabRipple(density: Float): Drawable {
        val color = withAlpha(PRIMARY, 0.18f)
        val mask = roundedRect(density, Color.WHITE, cornerDp = 8f)
        return RippleDrawable(ColorStateList.valueOf(color), null, mask)
    }

    fun tabIndicator(density: Float): GradientDrawable =
        roundedRect(density, PRIMARY, cornerDp = 1.5f)

    fun tabText(selected: Boolean): Int =
        if (selected) PRIMARY else TEXT_MUTED

    fun controlTint(): ColorStateList = ColorStateList.valueOf(PRIMARY)

    fun resolvedPanelBg(style: PanelStyle): Int = style.backgroundArgb ?: PANEL_BG

    fun resolvedHeader(style: PanelStyle): Int = style.headerArgb ?: PRIMARY_CONTAINER

    fun resolvedTitle(style: PanelStyle): Int = style.titleArgb ?: ON_PRIMARY_CONTAINER

    fun panelDrawable(density: Float, style: PanelStyle): GradientDrawable =
        roundedRect(density, resolvedPanelBg(style), cornerDp = style.cornerRadiusDp)

    fun headerDrawable(density: Float, style: PanelStyle): GradientDrawable {
        val r = style.cornerRadiusDp * density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(resolvedHeader(style))
            setCornerRadii(floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f))
        }
    }

    private fun apply(p: Palette) {
        PANEL_BG = p.panel
        SURFACE = p.surface
        SURFACE_VARIANT = p.surfaceVariant
        TEXT = p.onSurface
        TEXT_MUTED = p.onSurfaceVariant
        PRIMARY = p.primary
        ON_PRIMARY = p.onPrimary
        PRIMARY_CONTAINER = p.primaryContainer
        ON_PRIMARY_CONTAINER = p.onPrimaryContainer
        OUTLINE = p.outline
        ERROR = p.error
        TRACK = p.track
        ACCENT = p.primary
        FAB_BG = p.primary
    }

    private fun isDark(context: Context): Boolean {
        val app = context.applicationContext
        return when (AppSettings.getThemeMode(app)) {
            AppSettings.ThemeMode.DARK -> true
            AppSettings.ThemeMode.LIGHT -> false
            AppSettings.ThemeMode.SYSTEM -> {
                val night = app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                night == Configuration.UI_MODE_NIGHT_YES
            }
        }
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

    private fun darken(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(color), r, g, b)
    }
}
