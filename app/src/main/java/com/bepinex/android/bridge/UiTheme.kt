package com.bepinex.android.bridge

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
        const val GROUP_PAD_TOP = 12
        const val LIST_ITEM_PAD = 8
        const val FAB_DEFAULT = 48
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
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(PRIMARY)
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

    fun tabBackground(density: Float, selected: Boolean): GradientDrawable =
        if (selected) {
            roundedRect(density, PRIMARY_CONTAINER, cornerDp = 8f)
        } else {
            roundedRect(density, SURFACE_VARIANT, cornerDp = 8f)
        }

    fun tabText(selected: Boolean): Int =
        if (selected) ON_PRIMARY_CONTAINER else TEXT_MUTED

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
}
