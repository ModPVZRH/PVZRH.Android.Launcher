package com.bepinex.android.bridge

import android.app.Activity
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.bepinex.android.BepInExLog
import java.util.concurrent.CountDownLatch

/**
 * Per-plugin floating FAB + panel added to the game Activity DecorView.
 * Each session owns its own window; only those bounds intercept touches.
 */
object UiHost {

    private val handler = Handler(Looper.getMainLooper())

    private var activity: Activity? = null
    private var density = 1f

    private val plugins = LinkedHashMap<String, PluginWindow>()

    private class PluginWindow(
        val sessionId: String,
        var name: String,
        var tree: UiNode,
        var onEvent: (widgetId: String, name: String, value: Any?) -> Unit,
        var inflater: UiInflater? = null,
        var content: View? = null,
        var icon: Bitmap? = null,
        var panelStyle: PanelStyle = PanelStyle(),
        var fabStyle: FabStyle = FabStyle(),
        var fab: View? = null,
        var panel: View? = null,
        var contentHost: FrameLayout? = null,
        var titleView: TextView? = null,
        var panelVisible: Boolean = false,
        var fabLeftMargin: Int? = null,
        var fabBottomMargin: Int? = null,
        var panelLeftMargin: Int? = null,
        var panelTopMargin: Int? = null,
    )

    fun attach(activity: Activity) {
        runOnMain {
            detachViews()
            attachInternal(activity)
        }
    }

    fun detach() {
        runOnMain { detachViews() }
    }

    fun upsertPlugin(
        sessionId: String,
        name: String,
        tree: UiNode,
        onEvent: (widgetId: String, name: String, value: Any?) -> Unit,
    ) {
        runOnMain {
            val existing = plugins[sessionId]
            if (existing != null) {
                existing.name = name
                existing.tree = tree
                existing.onEvent = onEvent
                val act = activity
                if (act != null) {
                    inflatePlugin(act, existing)
                    existing.titleView?.text = displayName(existing)
                    bindContent(existing)
                }
            } else {
                val window = PluginWindow(sessionId, name, tree, onEvent)
                plugins[sessionId] = window
                activity?.let { mountWindow(it, window) }
            }
        }
    }

    fun updateWidget(sessionId: String, widgetId: String, props: Map<String, Any?>) {
        runOnMain {
            val window = plugins[sessionId] ?: return@runOnMain
            window.tree = window.tree.updateById(widgetId, props)
            window.inflater?.applyUpdate(widgetId, props)
        }
    }

    fun removePlugin(sessionId: String) {
        runOnMain {
            val window = plugins.remove(sessionId) ?: return@runOnMain
            unmountWindow(window, clearContent = true)
        }
    }

    fun showToast(text: String) {
        runOnMain {
            val act = activity ?: return@runOnMain
            Toast.makeText(act, text, Toast.LENGTH_SHORT).show()
        }
    }

    fun setIcon(sessionId: String, icon: Bitmap) {
        runOnMain {
            val window = plugins[sessionId] ?: return@runOnMain
            window.icon = icon
            rebuildChrome(window)
        }
    }

    fun setPanelStyle(sessionId: String, style: PanelStyle) {
        runOnMain {
            val window = plugins[sessionId] ?: return@runOnMain
            window.panelStyle = style
            rebuildChrome(window)
        }
    }

    fun setFabStyle(sessionId: String, style: FabStyle) {
        runOnMain {
            val window = plugins[sessionId] ?: return@runOnMain
            window.fabStyle = style
            rebuildChrome(window)
        }
    }

    private fun attachInternal(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (activity.window?.decorView !is ViewGroup) {
            BepInExLog.w("UiHost: no decorView")
            return
        }

        this.activity = activity
        UiTheme.bind(activity)
        density = activity.resources.displayMetrics.density

        for (window in plugins.values) {
            mountWindow(activity, window)
        }
        BepInExLog.i("UiHost attached")
    }

    private fun detachViews() {
        for (window in plugins.values) {
            capturePosition(window)
            unmountWindow(window, clearContent = true)
        }
        activity = null
    }

    private fun rebuildChrome(window: PluginWindow) {
        val act = activity ?: return
        capturePosition(window)
        unmountWindow(window, clearContent = false)
        mountWindow(act, window)
    }

    private fun mountWindow(activity: Activity, window: PluginWindow) {
        if (activity.isFinishing || activity.isDestroyed) return
        val decorView = activity.window?.decorView as? ViewGroup ?: return

        val dm = activity.resources.displayMetrics
        val decorW = decorView.width.takeIf { it > 0 } ?: dm.widthPixels
        val decorH = decorView.height.takeIf { it > 0 } ?: dm.heightPixels
        val margin = UiTheme.dp(density, 12f)
        val style = window.panelStyle
        val fabSizeDp = window.fabStyle.sizeDp.coerceAtLeast(1)
        val fabSize = UiTheme.dp(density, fabSizeDp.toFloat())
        val index = plugins.keys.indexOf(window.sessionId).coerceAtLeast(0)

        val panelWidth = UiTheme.dp(density, style.widthDp.coerceAtLeast(1).toFloat())
            .coerceAtMost((decorW - margin * 2).coerceAtLeast(fabSize))
        val panelHeight = UiTheme.dp(density, style.heightDp.coerceAtLeast(1).toFloat())
            .coerceAtMost((decorH - margin * 2).coerceAtLeast(fabSize))

        val fabView = buildFab(activity, window, fabSize)
        val fabParams = FrameLayout.LayoutParams(fabSize, fabSize).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            val maxLeft = (decorW - fabSize).coerceAtLeast(0)
            val maxBottom = (decorH - fabSize).coerceAtLeast(0)
            leftMargin = (window.fabLeftMargin ?: margin).coerceIn(0, maxLeft)
            bottomMargin = (
                window.fabBottomMargin ?: (margin + index * (fabSize + margin))
            ).coerceIn(0, maxBottom)
        }

        val panelView = buildPanel(activity, window, panelWidth, panelHeight)
        val panelParams = if (window.panelLeftMargin != null && window.panelTopMargin != null) {
            FrameLayout.LayoutParams(panelWidth, panelHeight).apply {
                gravity = Gravity.TOP or Gravity.START
                val maxLeft = (decorW - panelWidth).coerceAtLeast(0)
                val maxTop = (decorH - panelHeight).coerceAtLeast(0)
                leftMargin = window.panelLeftMargin!!.coerceIn(0, maxLeft)
                topMargin = window.panelTopMargin!!.coerceIn(0, maxTop)
            }
        } else {
            FrameLayout.LayoutParams(panelWidth, panelHeight).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                setMargins(0, 0, margin, 0)
            }
        }
        panelView.visibility = if (window.panelVisible) View.VISIBLE else View.GONE

        installFabDrag(window, fabView, fabParams, decorView, fabSize)
        window.titleView?.let { title ->
            installPanelDrag(window, panelView, title, decorView, panelWidth, panelHeight)
        }

        window.fab = fabView
        window.panel = panelView

        if (window.content == null) {
            inflatePlugin(activity, window)
        }
        bindContent(window)

        decorView.addView(panelView, panelParams)
        decorView.addView(fabView, fabParams)
    }

    private fun unmountWindow(window: PluginWindow, clearContent: Boolean) {
        window.content?.let { view ->
            if (clearContent || view.parent != null) {
                (view.parent as? ViewGroup)?.removeView(view)
            }
        }
        window.fab?.let { view -> (view.parent as? ViewGroup)?.removeView(view) }
        window.panel?.let { view -> (view.parent as? ViewGroup)?.removeView(view) }
        window.fab = null
        window.panel = null
        window.contentHost = null
        window.titleView = null
        if (clearContent) {
            window.inflater = null
            window.content = null
        }
    }

    private fun buildFab(activity: Activity, window: PluginWindow, fabSize: Int): View {
        val icon = window.icon
        val hasIcon = icon != null
        val style = window.fabStyle
        val child: View = if (hasIcon) {
            ImageView(activity).apply {
                setImageBitmap(icon)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(Color.TRANSPARENT)
            }
        } else {
            TextView(activity).apply {
                text = fabLabel(window)
                setTextColor(UiTheme.ON_PRIMARY)
                textSize = if (text.length <= 1) 16f else 11f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        }
        val bgColor = style.backgroundArgb ?: if (hasIcon) Color.TRANSPARENT else UiTheme.PRIMARY
        val transparentBg = Color.alpha(bgColor) == 0
        return FrameLayout(activity).apply {
            if (transparentBg) {
                setBackgroundColor(Color.TRANSPARENT)
                elevation = 0f
            } else {
                background = UiTheme.fabDrawable(
                    density,
                    bgColor,
                    style.shape,
                    style.cornerRadiusDp,
                )
                elevation = 10f * density
            }
            contentDescription = displayName(window)
            isClickable = true
            addView(
                child,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                ),
            )
            layoutParams = FrameLayout.LayoutParams(fabSize, fabSize)
        }
    }

    private fun buildPanel(
        activity: Activity,
        window: PluginWindow,
        panelWidth: Int,
        panelHeight: Int,
    ): View {
        val style = window.panelStyle
        val titleColor = UiTheme.resolvedTitle(style)
        val titleView = TextView(activity).apply {
            text = displayName(window)
            setTextColor(titleColor)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            val padH = UiTheme.dp(density, 8f)
            val padV = UiTheme.dp(density, 6f)
            setPadding(padH, padV, UiTheme.dp(density, 4f), padV)
        }
        val minimizeBtn = TextView(activity).apply {
            text = "−"
            contentDescription = str(
                activity,
                com.bepinex.android.R.string.bridge_mods_minimize,
                "Minimize",
            )
            setTextColor(titleColor)
            textSize = 20f
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = null
            val pad = UiTheme.dp(density, 8f)
            setPadding(pad, 0, pad, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            setOnClickListener {
                window.panelVisible = false
                window.panel?.visibility = View.GONE
            }
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiTheme.headerDrawable(density, style)
            addView(
                titleView,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(minimizeBtn)
        }

        val host = FrameLayout(activity).apply {
            val pad = UiTheme.dp(density, 4f)
            setPadding(pad, pad, pad, pad)
        }

        val panelLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.panelDrawable(density, style)
            elevation = style.elevationDp * density
            isClickable = true
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            addView(
                header,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                host,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }

        window.titleView = titleView
        window.contentHost = host

        return FrameLayout(activity).apply {
            isClickable = true
            layoutParams = FrameLayout.LayoutParams(panelWidth, panelHeight)
            addView(
                panelLayout,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }

    private fun installFabDrag(
        window: PluginWindow,
        fabView: View,
        fabParams: FrameLayout.LayoutParams,
        decorView: ViewGroup,
        fabSize: Int,
    ) {
        var downX = 0f
        var downY = 0f
        var downFabX = 0f
        var downFabY = 0f
        var dragging = false

        fabView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    downFabX = fabParams.leftMargin.toFloat()
                    downFabY = fabParams.bottomMargin.toFloat()
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (dx * dx + dy * dy) > 25 * density * density) {
                        dragging = true
                    }
                    if (dragging) {
                        val maxLeft = (decorView.width - fabSize).coerceAtLeast(0)
                        val maxBottom = (decorView.height - fabSize).coerceAtLeast(0)
                        fabParams.leftMargin = (downFabX + dx).toInt().coerceIn(0, maxLeft)
                        fabParams.bottomMargin = (downFabY - dy).toInt().coerceIn(0, maxBottom)
                        fabParams.gravity = Gravity.BOTTOM or Gravity.START
                        fabView.layoutParams = fabParams
                        window.fabLeftMargin = fabParams.leftMargin
                        window.fabBottomMargin = fabParams.bottomMargin
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        window.panelVisible = !window.panelVisible
                        window.panel?.visibility =
                            if (window.panelVisible) View.VISIBLE else View.GONE
                        if (window.panelVisible) {
                            window.panel?.bringToFront()
                        }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun installPanelDrag(
        window: PluginWindow,
        panelView: View,
        dragHandle: View,
        decorView: ViewGroup,
        panelWidth: Int,
        panelHeight: Int,
    ) {
        var downX = 0f
        var downY = 0f
        var downPanelX = 0f
        var downPanelY = 0f
        var dragging = false

        dragHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    downPanelX = panelView.left.toFloat()
                    downPanelY = panelView.top.toFloat()
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (dx * dx + dy * dy) > 25 * density * density) {
                        dragging = true
                    }
                    if (dragging) {
                        val maxLeft = (decorView.width - panelWidth).coerceAtLeast(0)
                        val maxTop = (decorView.height - panelHeight).coerceAtLeast(0)
                        val updated = panelView.layoutParams as FrameLayout.LayoutParams
                        updated.gravity = Gravity.TOP or Gravity.START
                        updated.leftMargin = (downPanelX + dx).toInt().coerceIn(0, maxLeft)
                        updated.topMargin = (downPanelY + dy).toInt().coerceIn(0, maxTop)
                        panelView.layoutParams = updated
                        window.panelLeftMargin = updated.leftMargin
                        window.panelTopMargin = updated.topMargin
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    true
                }
                else -> false
            }
        }
    }

    private fun inflatePlugin(activity: Activity, window: PluginWindow) {
        val inflater = UiInflater(activity, window.onEvent)
        window.inflater = inflater
        window.content = try {
            wrapPanelContent(activity, inflater.inflate(window.tree), window.tree)
        } catch (t: Throwable) {
            BepInExLog.e("UiHost: inflate failed for ${window.sessionId}", t)
            TextView(activity).apply {
                text = displayName(window)
                setTextColor(UiTheme.ERROR)
                textSize = 12f
                val pad = UiTheme.dp(density, 8f)
                setPadding(pad, pad, pad, pad)
            }
        }
    }

    private fun bindContent(window: PluginWindow) {
        val host = window.contentHost ?: return
        host.removeAllViews()
        val content = window.content ?: return
        (content.parent as? ViewGroup)?.removeView(content)
        host.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun wrapPanelContent(activity: Activity, content: View, tree: UiNode): View {
        if (tree.type == WidgetType.TABS || tree.type == WidgetType.SCROLL) return content
        return ScrollView(activity).apply {
            isFillViewport = true
            addView(
                content,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }
    }

    private fun capturePosition(window: PluginWindow) {
        val fab = window.fab
        val fabLp = fab?.layoutParams as? FrameLayout.LayoutParams
        if (fabLp != null) {
            window.fabLeftMargin = fabLp.leftMargin
            window.fabBottomMargin = fabLp.bottomMargin
        }
        val panel = window.panel
        val panelLp = panel?.layoutParams as? FrameLayout.LayoutParams
        if (panel != null && panelLp != null) {
            if (panel.isLaidOut && (panel.visibility == View.VISIBLE || window.panelLeftMargin != null)) {
                window.panelLeftMargin = panel.left
                window.panelTopMargin = panel.top
            } else if (panelLp.gravity == (Gravity.TOP or Gravity.START)) {
                window.panelLeftMargin = panelLp.leftMargin
                window.panelTopMargin = panelLp.topMargin
            }
        }
    }

    private fun displayName(window: PluginWindow): String =
        window.name.ifBlank { window.sessionId }

    private fun fabLabel(window: PluginWindow): String {
        val styled = window.fabStyle.label
        if (styled.isNotBlank()) return styled
        val ch = window.name.firstOrNull()
        return ch?.toString() ?: "M"
    }

    private fun str(activity: Activity, id: Int, fallback: String): String {
        try {
            return activity.getString(id)
        } catch (_: Resources.NotFoundException) {
        }
        return try {
            activity.createPackageContext(
                "com.pvzrh.android.launcher",
                android.content.Context.CONTEXT_IGNORE_SECURITY,
            ).getString(id)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return
        }
        val latch = CountDownLatch(1)
        var error: Throwable? = null
        val posted = handler.post {
            try {
                block()
            } catch (t: Throwable) {
                error = t
            } finally {
                latch.countDown()
            }
        }
        if (!posted) return
        try {
            latch.await()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        error?.let { throw it }
    }
}
