package com.bepinex.android.ui.theme

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * True when the root [LiquidGlassHost] is showing the liquid-glass wallpaper.
 * Surfaces opt in with [glassSurface] and [glassContainerColor].
 */
val LocalLiquidGlass = staticCompositionLocalOf { false }

/** Backdrop captured by [LiquidGlassHost], or null when the effect is off. */
val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/**
 * When [enabled], draws the wallpaper into a layer backdrop and places [content]
 * as a sibling above it. Content must not sit inside [layerBackdrop], or glass
 * would sample itself. No full-screen glass sheet is drawn.
 */
@Composable
fun LiquidGlassHost(
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        CompositionLocalProvider(
            LocalLiquidGlass provides false,
            LocalBackdrop provides null
        ) {
            content()
        }
        return
    }
    val backdrop = rememberLayerBackdrop()
    CompositionLocalProvider(
        LocalLiquidGlass provides true,
        LocalBackdrop provides backdrop
    ) {
        Box(Modifier.fillMaxSize()) {
            LiquidGlassWallpaper(Modifier.layerBackdrop(backdrop).fillMaxSize())
            content()
        }
    }
}

/**
 * Platform dialog windows cannot sample the activity backdrop, so when
 * [LocalLiquidGlass] is on this records its own wallpaper into a layer backdrop
 * and draws [content] as a sibling. When glass is off, this is a plain [Dialog].
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    if (!LocalLiquidGlass.current) {
        Dialog(onDismissRequest, properties, content)
        return
    }
    Dialog(onDismissRequest, properties) {
        val wallpaper = rememberGlassWallpaper()
        val backdrop = remember(wallpaper) { DialogWallpaperBackdrop(wallpaper) }
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
            window.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window.decorView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            window.setDimAmount(0.45f)
            view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        CompositionLocalProvider(
            LocalBackdrop provides backdrop,
            LocalLiquidGlass provides true
        ) {
            content()
        }
    }
}

/**
 * Alert panel on [GlassDialog]. Glass replaces [containerColor]; when glass is
 * off, [containerColor] is forwarded only if the caller specified one.
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(28.dp),
    containerColor: Color = Color.Unspecified,
    properties: DialogProperties = DialogProperties()
) {
    if (!LocalLiquidGlass.current) {
        if (containerColor.isSpecified) {
            AlertDialog(
                onDismissRequest = onDismissRequest,
                confirmButton = confirmButton,
                modifier = modifier,
                dismissButton = dismissButton,
                icon = icon,
                title = title,
                text = text,
                shape = shape,
                containerColor = containerColor,
                properties = properties
            )
        } else {
            AlertDialog(
                onDismissRequest = onDismissRequest,
                confirmButton = confirmButton,
                modifier = modifier,
                dismissButton = dismissButton,
                icon = icon,
                title = title,
                text = text,
                shape = shape,
                properties = properties
            )
        }
        return
    }
    GlassDialog(onDismissRequest, properties) {
        Column(
            modifier = Modifier
                .then(modifier)
                .glassSurface(shape)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            icon?.invoke()
            if (title != null) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                    title()
                }
            }
            if (text != null) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    text()
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

@Composable
fun Modifier.glassSurface(shape: Shape = RoundedCornerShape(20.dp)): Modifier {
    val backdrop = LocalBackdrop.current ?: return this
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return liquidGlass(backdrop, shape, dark)
}

@Composable
fun glassContainerColor(solid: Color): Color =
    if (LocalLiquidGlass.current) Color.Transparent else solid

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun glassTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = glassContainerColor(MaterialTheme.colorScheme.surface),
    scrolledContainerColor = glassContainerColor(MaterialTheme.colorScheme.surface)
)

fun Modifier.liquidGlass(backdrop: Backdrop, shape: Shape, dark: Boolean): Modifier {
    return this
        .clip(shape)
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(8f.dp.toPx())
                lens(16f.dp.toPx(), 32f.dp.toPx(), chromaticAberration = true)
            },
            onDrawSurface = {
                drawRect(
                    if (dark) Color.Black.copy(alpha = 0.34f) else Color.White.copy(alpha = 0.46f)
                )
            }
        )
}

@Immutable
private data class GlassWallpaper(
    val background: Color,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)

@Composable
private fun rememberGlassWallpaper(): GlassWallpaper {
    val scheme = MaterialTheme.colorScheme
    return GlassWallpaper(
        background = scheme.background,
        primary = scheme.primary,
        secondary = scheme.secondary,
        tertiary = scheme.tertiary,
    )
}

@Composable
private fun LiquidGlassWallpaper(modifier: Modifier = Modifier) {
    val wallpaper = rememberGlassWallpaper()
    Canvas(modifier) { drawThemedWallpaper(wallpaper) }
}

private fun DrawScope.drawThemedWallpaper(wallpaper: GlassWallpaper) {
    drawRect(
        Brush.linearGradient(
            listOf(
                wallpaper.background,
                wallpaper.primary.copy(alpha = 0.22f),
                wallpaper.secondary.copy(alpha = 0.16f),
            )
        )
    )
    drawCircle(
        color = wallpaper.primary.copy(alpha = 0.28f),
        radius = size.minDimension * 0.42f,
        center = Offset(size.width * 0.12f, size.height * 0.18f)
    )
    drawCircle(
        color = wallpaper.secondary.copy(alpha = 0.22f),
        radius = size.minDimension * 0.48f,
        center = Offset(size.width * 0.92f, size.height * 0.34f)
    )
    drawCircle(
        color = wallpaper.tertiary.copy(alpha = 0.18f),
        radius = size.minDimension * 0.32f,
        center = Offset(size.width * 0.5f, size.height * 0.88f)
    )
}

/** Draws the wallpaper inside the glass panel so dialog blur cannot spill past the window. */
private class DialogWallpaperBackdrop(val wallpaper: GlassWallpaper) : Backdrop {
    override val isCoordinatesDependent: Boolean = false

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        drawThemedWallpaper(wallpaper)
    }
}

val LiquidGlassPanelShape = RoundedCornerShape(28.dp)
val LiquidGlassBarShape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)
