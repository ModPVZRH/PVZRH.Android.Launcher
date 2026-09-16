package com.bepinex.android.bridge

import android.app.Activity
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.bepinex.android.BepInExLog
import java.util.concurrent.CountDownLatch

/**
 * Floating Mods FAB + panel added to the game Activity DecorView.
 * Only FAB/panel bounds intercept touches — the game stays playable.
 */
object UiHost {

    private val handler = Handler(Looper.getMainLooper())

    private var activity: Activity? = null
    private var fab: View? = null
    private var panel: View? = null
    private var panelVisible = false

    private var headerPluginName: TextView? = null
    private var chipScroll: HorizontalScrollView? = null
    private var chipRow: LinearLayout? = null
    private var contentHost: FrameLayout? = null
    private var emptyView: TextView? = null

    private var density = 1f
    private var panelWidth = 0
    private var panelHeight = 0

    private val plugins = LinkedHashMap<String, PluginEntry>()
    private var selectedId: String? = null

    private class PluginEntry(
        val sessionId: String,
        var name: String,
        var tree: UiNode,
        var onEvent: (widgetId: String, name: String, value: Any?) -> Unit,
        var inflater: UiInflater? = null,
        var content: View? = null,
    )

    fun attach(activity: Activity) {
        runOnMain {
            if (this.activity === activity && fab?.parent != null) return@runOnMain
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
            val entry = existing?.apply {
                this.name = name
                this.tree = tree
                this.onEvent = onEvent
            } ?: PluginEntry(sessionId, name, tree, onEvent).also { plugins[sessionId] = it }
            if (selectedId == null) selectedId = sessionId
            val act = activity
            if (act != null) {
                inflatePlugin(act, entry)
                refreshChrome()
            }
        }
    }

    fun updateWidget(sessionId: String, widgetId: String, props: Map<String, Any?>) {
        runOnMain {
            val entry = plugins[sessionId] ?: return@runOnMain
            entry.tree = entry.tree.updateById(widgetId, props)
            entry.inflater?.applyUpdate(widgetId, props)
        }
    }

    fun removePlugin(sessionId: String) {
        runOnMain {
            plugins.remove(sessionId) ?: return@runOnMain
            if (selectedId == sessionId) {
                selectedId = plugins.keys.firstOrNull()
            }
            refreshChrome()
        }
    }

    fun showToast(text: String) {
        runOnMain {
            val act = activity ?: return@runOnMain
            Toast.makeText(act, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun attachInternal(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        val decorView = activity.window?.decorView as? ViewGroup ?: run {
            BepInExLog.w("UiHost: no decorView")
            return
        }

        this.activity = activity
        density = activity.resources.displayMetrics.density
        val dm = activity.resources.displayMetrics
        val decorW = decorView.width.takeIf { it > 0 } ?: dm.widthPixels
        val decorH = decorView.height.takeIf { it > 0 } ?: dm.heightPixels

        val margin = UiTheme.dp(density, 12f)
        val fabSize = UiTheme.dp(density, 40f)
        val fabGap = UiTheme.dp(density, 8f)
        panelWidth = UiTheme.dp(density, 320f).coerceAtMost(
            (decorW - margin * 2).coerceAtLeast(fabSize)
        )
        panelHeight = UiTheme.dp(density, 280f).coerceAtMost(
            (decorH - margin * 2).coerceAtLeast(fabSize)
        )

        val titleText = str(activity, com.bepinex.android.R.string.bridge_mods_title, "Mods")
        val emptyText = str(activity, com.bepinex.android.R.string.bridge_mods_empty, "No plugins connected")
        val fabDesc = str(activity, com.bepinex.android.R.string.bridge_mods_fab, "Open mod menu")
        val minimizeDesc = str(activity, com.bepinex.android.R.string.bridge_mods_minimize, "−")

        val fabLabel = TextView(activity).apply {
            text = "M"
            setTextColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        val fabView = FrameLayout(activity).apply {
            background = UiTheme.fabBackground(density)
            elevation = 10f * density
            contentDescription = fabDesc
            isClickable = true
            addView(
                fabLabel,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
                )
            )
        }
        val fabParams = FrameLayout.LayoutParams(fabSize, fabSize).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            leftMargin = margin + fabSize + fabGap
            bottomMargin = margin
        }

        val titleView = TextView(activity).apply {
            text = titleText
            setTextColor(UiTheme.ACCENT)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(UiTheme.dp(density, 8f), UiTheme.dp(density, 6f), UiTheme.dp(density, 6f), UiTheme.dp(density, 4f))
        }
        val pluginNameView = TextView(activity).apply {
            setTextColor(UiTheme.TEXT_MUTED)
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, UiTheme.dp(density, 6f), UiTheme.dp(density, 4f), UiTheme.dp(density, 4f))
        }
        val minimizeBtn = TextView(activity).apply {
            text = "−"
            contentDescription = minimizeDesc
            setTextColor(UiTheme.TEXT)
            textSize = 20f
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8f * density
                setColor(UiTheme.SURFACE)
            }
            layoutParams = LinearLayout.LayoutParams(
                UiTheme.dp(density, 32f),
                UiTheme.dp(density, 32f)
            ).apply {
                marginStart = UiTheme.dp(density, 3f)
                marginEnd = UiTheme.dp(density, 6f)
                gravity = Gravity.CENTER_VERTICAL
            }
        }
        val headerDrag = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(titleView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            addView(pluginNameView, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ))
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(headerDrag, LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ))
            addView(minimizeBtn)
        }

        val chips = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UiTheme.dp(density, 6f), 0, UiTheme.dp(density, 6f), UiTheme.dp(density, 4f))
        }
        val chipsScroll = HorizontalScrollView(activity).apply {
            isHorizontalScrollBarEnabled = false
            visibility = View.GONE
            addView(chips, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        val empty = TextView(activity).apply {
            text = emptyText
            setTextColor(UiTheme.TEXT_MUTED)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(UiTheme.dp(density, 12f), UiTheme.dp(density, 16f),
                UiTheme.dp(density, 12f), UiTheme.dp(density, 16f))
        }
        val host = FrameLayout(activity)
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            addView(host, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        val panelLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.panelBackground(density)
            elevation = 12f * density
            isClickable = true
            addView(header, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(chipsScroll, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(scroll, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ))
        }
        val panelParams = FrameLayout.LayoutParams(panelWidth, panelHeight).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            setMargins(0, 0, margin, 0)
        }
        val panelContainer = FrameLayout(activity).apply {
            isClickable = true
            visibility = View.GONE
            addView(panelLayout, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }

        var downX = 0f
        var downY = 0f
        var downFabX = 0f
        var downFabY = 0f
        var isDragging = false
        var downPanelX = 0f
        var downPanelY = 0f
        var isPanelDragging = false

        headerDrag.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    downPanelX = panelContainer.left.toFloat()
                    downPanelY = panelContainer.top.toFloat()
                    isPanelDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!isPanelDragging && (dx * dx + dy * dy) > 25 * density * density) {
                        isPanelDragging = true
                    }
                    if (isPanelDragging) {
                        val maxLeft = (decorView.width - panelWidth).coerceAtLeast(0)
                        val maxTop = (decorView.height - panelHeight).coerceAtLeast(0)
                        val updated = panelContainer.layoutParams as FrameLayout.LayoutParams
                        updated.gravity = Gravity.TOP or Gravity.START
                        updated.leftMargin = (downPanelX + dx).toInt().coerceIn(0, maxLeft)
                        updated.topMargin = (downPanelY + dy).toInt().coerceIn(0, maxTop)
                        panelContainer.layoutParams = updated
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isPanelDragging = false
                    true
                }
                else -> false
            }
        }

        fabView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    downFabX = fabParams.leftMargin.toFloat()
                    downFabY = fabParams.bottomMargin.toFloat()
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!isDragging && (dx * dx + dy * dy) > 25 * density * density) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val maxLeft = (decorView.width - fabSize).coerceAtLeast(0)
                        val maxBottom = (decorView.height - fabSize).coerceAtLeast(0)
                        fabParams.leftMargin = (downFabX + dx).toInt().coerceIn(0, maxLeft)
                        fabParams.bottomMargin = (downFabY - dy).toInt().coerceIn(0, maxBottom)
                        fabParams.gravity = Gravity.BOTTOM or Gravity.START
                        fabView.layoutParams = fabParams
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        panelVisible = !panelVisible
                        panelContainer.visibility = if (panelVisible) View.VISIBLE else View.GONE
                    }
                    true
                }
                else -> false
            }
        }

        minimizeBtn.setOnClickListener {
            panelVisible = false
            panelContainer.visibility = View.GONE
        }

        headerPluginName = pluginNameView
        chipScroll = chipsScroll
        chipRow = chips
        contentHost = host
        emptyView = empty
        fab = fabView
        panel = panelContainer

        decorView.addView(panelContainer, panelParams)
        decorView.addView(fabView, fabParams)

        for (entry in plugins.values) {
            inflatePlugin(activity, entry)
        }
        refreshChrome()
        BepInExLog.i("UiHost attached")
    }

    private fun detachViews() {
        panel?.let { view -> (view.parent as? ViewGroup)?.removeView(view) }
        fab?.let { view -> (view.parent as? ViewGroup)?.removeView(view) }
        fab = null
        panel = null
        headerPluginName = null
        chipScroll = null
        chipRow = null
        contentHost = null
        emptyView = null
        panelVisible = false
        activity = null
        for (entry in plugins.values) {
            entry.inflater = null
            entry.content = null
        }
    }

    private fun inflatePlugin(activity: Activity, entry: PluginEntry) {
        val inflater = UiInflater(activity, entry.onEvent)
        entry.inflater = inflater
        entry.content = try {
            inflater.inflate(entry.tree)
        } catch (t: Throwable) {
            BepInExLog.e("UiHost: inflate failed for ${entry.sessionId}", t)
            TextView(activity).apply {
                text = entry.name.ifBlank { entry.sessionId }
                setTextColor(UiTheme.ERROR)
                textSize = 12f
                setPadding(UiTheme.dp(density, 8f), UiTheme.dp(density, 8f),
                    UiTheme.dp(density, 8f), UiTheme.dp(density, 8f))
            }
        }
    }

    private fun refreshChrome() {
        val host = contentHost ?: return
        host.removeAllViews()
        val entry = selectedId?.let { plugins[it] }
        val content = entry?.content
        if (content == null) {
            emptyView?.let { empty ->
                (empty.parent as? ViewGroup)?.removeView(empty)
                host.addView(
                    empty,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }
            headerPluginName?.text = ""
        } else {
            (content.parent as? ViewGroup)?.removeView(content)
            host.addView(
                content,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
            headerPluginName?.text = entry.name
        }
        refreshChips()
    }

    private fun refreshChips() {
        val row = chipRow ?: return
        val scroll = chipScroll ?: return
        val act = activity ?: return
        row.removeAllViews()
        if (plugins.size <= 1) {
            scroll.visibility = View.GONE
            return
        }
        scroll.visibility = View.VISIBLE
        val padH = UiTheme.dp(density, 10f)
        val padV = UiTheme.dp(density, 4f)
        val gap = UiTheme.dp(density, 6f)
        for (entry in plugins.values) {
            val selected = entry.sessionId == selectedId
            val chip = TextView(act).apply {
                text = entry.name.ifBlank { entry.sessionId }
                setTextColor(if (selected) Color.WHITE else UiTheme.TEXT)
                textSize = 11f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                background = UiTheme.chipBackground(density, selected)
                setPadding(padH, padV, padH, padV)
                setOnClickListener { selectPlugin(entry.sessionId) }
            }
            row.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = gap })
        }
    }

    private fun selectPlugin(sessionId: String) {
        if (selectedId == sessionId) return
        if (!plugins.containsKey(sessionId)) return
        selectedId = sessionId
        refreshChrome()
    }

    private fun str(activity: Activity, id: Int, fallback: String): String {
        try {
            return activity.getString(id)
        } catch (_: Resources.NotFoundException) {
        }
        return try {
            activity.createPackageContext(
                "com.pvzrh.android.launcher",
                android.content.Context.CONTEXT_IGNORE_SECURITY
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
