package com.bepinex.android.bridge

import android.app.Activity
import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Space
import android.widget.Switch
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import java.io.File
import kotlin.math.roundToInt

/**
 * Inflates a [UiNode] tree into classic Android Views for the in-game overlay.
 */
class UiInflater(
    private val activity: Activity,
    private val onEvent: (widgetId: String, name: String, value: Any?) -> Unit,
) {
    private val density = activity.resources.displayMetrics.density
    private val binders = mutableMapOf<String, (Map<String, Any?>) -> Unit>()
    private var applying = false

    init {
        UiTheme.bind(activity)
    }

    fun inflate(node: UiNode): View {
        binders.clear()
        return build(node)
    }

    fun applyUpdate(widgetId: String, props: Map<String, Any?>): Boolean {
        val binder = binders[widgetId] ?: return false
        applying = true
        try {
            binder(props)
        } finally {
            applying = false
        }
        return true
    }

    private fun emit(id: String, name: String, value: Any?) {
        if (!applying && id.isNotEmpty()) onEvent(id, name, value)
    }

    private fun build(node: UiNode): View {
        val view = when (node.type) {
            WidgetType.COLUMN -> linear(node, LinearLayout.VERTICAL)
            WidgetType.ROW -> linear(node, LinearLayout.HORIZONTAL)
            WidgetType.SCROLL -> scroll(node)
            WidgetType.WRAP -> wrap(node)
            WidgetType.GROUP, WidgetType.COLLAPSIBLE -> group(node)
            WidgetType.STACK -> stack(node)
            WidgetType.GRID -> grid(node)
            WidgetType.TABS -> tabs(node)
            WidgetType.SPACER -> spacer(node)
            WidgetType.DIVIDER -> divider()
            WidgetType.TEXT -> text(node)
            WidgetType.MARKDOWN -> markdown(node)
            WidgetType.IMAGE -> image(node)
            WidgetType.PROGRESS -> progress(node)
            WidgetType.BADGE -> badge(node)
            WidgetType.ICON -> icon(node)
            WidgetType.BUTTON -> button(node, icon = false)
            WidgetType.ICON_BUTTON -> button(node, icon = true)
            WidgetType.SWITCH -> toggle(node, switch = true)
            WidgetType.CHECKBOX -> toggle(node, switch = false)
            WidgetType.RADIO -> radio(node)
            WidgetType.RADIO_GROUP -> radioGroup(node)
            WidgetType.SLIDER -> slider(node)
            WidgetType.STEPPER -> stepper(node)
            WidgetType.TEXT_FIELD -> textField(node)
            WidgetType.NUMBER_FIELD -> numberField(node)
            WidgetType.SELECT -> select(node)
            WidgetType.LIST -> list(node)
            WidgetType.COLOR_PICKER -> colorPicker(node)
            WidgetType.TOGGLE_GROUP -> toggleGroup(node)
            else -> text(node.copy(props = node.props + ("text" to "[${node.type}]")))
        }
        if (node.id.isNotEmpty()) view.tag = node.id
        UiLayout.apply(view, node, density)
        return view
    }

    private fun pad(view: View, node: UiNode) {
        val p = node.propInt("padding", 0)
        if (p > 0) {
            val px = UiTheme.dp(density, p.toFloat())
            view.setPadding(px, px, px, px)
        }
    }

    private fun gap(node: UiNode): Int = UiTheme.dp(density, node.propInt("gap", 6).toFloat())

    private fun childLp(vertical: Boolean, stretch: Boolean): LinearLayout.LayoutParams {
        return if (vertical) {
            LinearLayout.LayoutParams(
                if (stretch) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        } else {
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                if (stretch) LinearLayout.LayoutParams.MATCH_PARENT else LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    private fun linear(node: UiNode, orientation: Int): View {
        val vertical = orientation == LinearLayout.VERTICAL
        val stretch = node.propString("align", "stretch") == "stretch"
        val layout = LinearLayout(activity).apply {
            this.orientation = orientation
            gravity = when (node.propString("align", "start")) {
                "center" -> Gravity.CENTER
                "end" -> Gravity.END or Gravity.CENTER_VERTICAL
                else -> Gravity.START
            }
        }
        pad(layout, node)
        val g = gap(node)
        node.children.forEachIndexed { index, child ->
            val childView = build(child)
            val lp = childLp(vertical, stretch)
            childView.layoutParams?.let { existing ->
                if (existing.width > 0) lp.width = existing.width
                if (existing.height > 0) lp.height = existing.height
            }
            UiLayout.applyMargin(lp, child, density)
            if (index > 0) {
                if (vertical) lp.topMargin += g else lp.marginStart += g
            }
            layout.addView(childView, lp)
        }
        return layout
    }

    private fun scroll(node: UiNode): View {
        val content = LinearLayout(activity).apply {
            orientation = if (node.propString("axis", "vertical") == "horizontal") {
                LinearLayout.HORIZONTAL
            } else {
                LinearLayout.VERTICAL
            }
        }
        pad(content, node)
        node.children.forEach { child ->
            val childView = build(child)
            val lp = LinearLayout.LayoutParams(
                childView.layoutParams?.width ?: LinearLayout.LayoutParams.WRAP_CONTENT,
                childView.layoutParams?.height ?: LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            UiLayout.applyMargin(lp, child, density)
            content.addView(childView, lp)
        }
        return if (node.propString("axis", "vertical") == "horizontal") {
            HorizontalScrollView(activity).apply { addView(content) }
        } else {
            ScrollView(activity).apply { addView(content) }
        }
    }

    private fun wrap(node: UiNode): View {
        val flow = FlowLayout(activity, gap(node))
        pad(flow, node)
        node.children.forEach { child ->
            val childView = build(child)
            val lp = ViewGroup.MarginLayoutParams(
                childView.layoutParams?.width ?: ViewGroup.LayoutParams.WRAP_CONTENT,
                childView.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            UiLayout.applyMargin(lp, child, density)
            flow.addView(childView, lp)
        }
        return flow
    }

    private fun group(node: UiNode): View {
        val title = TextView(activity).apply {
            text = node.propString("title").ifEmpty { node.propString("label") }
            setTextColor(UiTheme.PRIMARY)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val bodyGap = UiTheme.dp(density, 8f)
        node.children.forEachIndexed { index, child ->
            val childView = build(child)
            val lp = when (val existing = childView.layoutParams) {
                is LinearLayout.LayoutParams -> existing
                is ViewGroup.MarginLayoutParams -> LinearLayout.LayoutParams(existing)
                is ViewGroup.LayoutParams -> LinearLayout.LayoutParams(existing)
                else -> LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            }
            UiLayout.applyMargin(lp, child, density)
            if (index > 0) lp.topMargin += bodyGap
            body.addView(childView, lp)
        }
        var expanded = !node.propBool("collapsed", false)
        if (node.type == WidgetType.GROUP && !node.propBool("collapsible", false)) {
            expanded = true
        }
        body.visibility = if (expanded) View.VISIBLE else View.GONE
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = UiTheme.groupBackground(density)
            val p = UiTheme.dp(density, UiTheme.Metrics.GROUP_PAD.toFloat())
            setPadding(p, p, p, p)
        }
        pad(root, node)
        root.addView(title)
        root.addView(body)
        if (node.propBool("collapsible", node.type == WidgetType.COLLAPSIBLE)) {
            title.setOnClickListener {
                expanded = !expanded
                body.visibility = if (expanded) View.VISIBLE else View.GONE
                emit(node.id, "change", expanded)
            }
        }
        register(node.id) { props ->
            val n = UiNode(node.id, node.type, props)
            val t = n.propString("title").ifEmpty { n.propString("label") }
            if (t.isNotEmpty()) title.text = t
            if (props.containsKey("collapsed")) {
                expanded = !n.propBool("collapsed", false)
                body.visibility = if (expanded) View.VISIBLE else View.GONE
            }
        }
        return root
    }

    private fun stack(node: UiNode): View {
        val frame = FrameLayout(activity)
        pad(frame, node)
        node.children.forEach { child ->
            val childView = build(child)
            val lp = FrameLayout.LayoutParams(
                childView.layoutParams?.width ?: FrameLayout.LayoutParams.WRAP_CONTENT,
                childView.layoutParams?.height ?: FrameLayout.LayoutParams.WRAP_CONTENT,
            )
            UiLayout.applyMargin(lp, child, density)
            frame.addView(childView, lp)
        }
        return frame
    }

    private fun grid(node: UiNode): View {
        val columns = node.propInt("columns", 2).coerceAtLeast(1)
        val table = TableLayout(activity).apply { isStretchAllColumns = true }
        pad(table, node)
        val g = gap(node)
        node.children.chunked(columns).forEach { rowNodes ->
            val row = TableRow(activity)
            rowNodes.forEach { child ->
                val lp = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(g / 2, g / 2, g / 2, g / 2)
                }
                UiLayout.applyMargin(lp, child, density)
                row.addView(build(child), lp)
            }
            table.addView(row)
        }
        return table
    }

    private fun tabs(node: UiNode): View {
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        pad(root, node)
        val header = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val content = FrameLayout(activity)
        var active = node.propString("active").ifEmpty { node.children.firstOrNull()?.id.orEmpty() }
        val pages = mutableListOf<Pair<String, View>>()
        fun refresh() {
            pages.forEachIndexed { index, (id, page) ->
                page.visibility = if (id == active) View.VISIBLE else View.GONE
                val chip = header.getChildAt(index) as? TextView ?: return@forEachIndexed
                val selected = id == active
                chip.background = UiTheme.tabBackground(density, selected)
                chip.setTextColor(UiTheme.tabText(selected))
            }
        }
        node.children.forEach { child ->
            val page = build(child)
            pages += child.id to page
            content.addView(page)
            val label = child.propString("title").ifEmpty { child.propString("label").ifEmpty { child.id } }
            val chip = TextView(activity).apply {
                text = label
                textSize = 12f
                setPadding(
                    UiTheme.dp(density, 12f),
                    UiTheme.dp(density, 8f),
                    UiTheme.dp(density, 12f),
                    UiTheme.dp(density, 8f),
                )
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { marginEnd = UiTheme.dp(density, 8f) }
                layoutParams = lp
                setOnClickListener {
                    active = child.id
                    refresh()
                    emit(node.id, "change", child.id)
                }
            }
            header.addView(chip)
        }
        root.addView(header)
        root.addView(content)
        refresh()
        register(node.id) { props ->
            val n = UiNode(node.id, node.type, props)
            val next = n.propString("active")
            if (next.isNotEmpty()) {
                active = next
                refresh()
            }
        }
        return root
    }

    private fun spacer(node: UiNode): View {
        val size = UiTheme.dp(density, node.propInt("size", 8).toFloat())
        return Space(activity).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
        }
    }

    private fun divider(): View {
        return View(activity).apply {
            setBackgroundColor(UiTheme.OUTLINE)
            alpha = 0.55f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UiTheme.dp(density, 1f),
            ).apply {
                topMargin = UiTheme.dp(density, 4f)
                bottomMargin = UiTheme.dp(density, 4f)
            }
        }
    }

    private fun text(node: UiNode): View {
        val tv = TextView(activity)
        applyText(tv, node)
        register(node.id) { applyText(tv, UiNode(node.id, node.type, it)) }
        return tv
    }

    private fun applyText(tv: TextView, node: UiNode) {
        tv.text = node.propString("text")
        tv.setTextColor(
            when (node.propString("style", "body")) {
                "error" -> UiTheme.ERROR
                "caption" -> UiTheme.TEXT_MUTED
                else -> UiTheme.TEXT
            }
        )
        tv.textSize = when (node.propString("style", "body")) {
            "title" -> 16f
            "subtitle" -> 14f
            "caption" -> 11f
            else -> 13f
        }
        tv.typeface = if (node.propString("style") == "title") Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun markdown(node: UiNode): View {
        val tv = TextView(activity).apply {
            setTextColor(UiTheme.TEXT)
            textSize = 13f
        }
        fun render(src: String) {
            tv.text = simpleMarkdown(src)
        }
        render(node.propString("text"))
        register(node.id) { render(UiNode(node.id, node.type, it).propString("text")) }
        return tv
    }

    private fun simpleMarkdown(src: String): CharSequence {
        val out = SpannableStringBuilder()
        src.lineSequence().forEachIndexed { index, raw ->
            if (index > 0) out.append('\n')
            var line = raw
            var heading = 0
            while (line.startsWith("#")) {
                heading++
                line = line.removePrefix("#")
            }
            line = line.trimStart()
            val start = out.length
            var i = 0
            while (i < line.length) {
                when {
                    line.startsWith("**", i) -> {
                        val end = line.indexOf("**", i + 2)
                        if (end > i) {
                            val s = out.length
                            out.append(line.substring(i + 2, end))
                            out.setSpan(StyleSpan(Typeface.BOLD), s, out.length, 0)
                            i = end + 2
                        } else {
                            out.append(line[i]); i++
                        }
                    }
                    line.startsWith("`", i) -> {
                        val end = line.indexOf('`', i + 1)
                        if (end > i) {
                            val s = out.length
                            out.append(line.substring(i + 1, end))
                            out.setSpan(TypefaceSpan("monospace"), s, out.length, 0)
                            i = end + 1
                        } else {
                            out.append(line[i]); i++
                        }
                    }
                    else -> {
                        out.append(line[i]); i++
                    }
                }
            }
            if (heading > 0) {
                out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, 0)
                if (heading == 1) {
                    out.setSpan(ForegroundColorSpan(UiTheme.ACCENT), start, out.length, 0)
                }
            }
        }
        return out
    }

    private fun image(node: UiNode): View {
        val iv = ImageView(activity).apply { adjustViewBounds = true }
        fun load(n: UiNode) {
            val w = n.propInt("width", 0)
            val h = n.propInt("height", 0)
            if (w > 0 || h > 0) {
                iv.layoutParams = ViewGroup.LayoutParams(
                    if (w > 0) UiTheme.dp(density, w.toFloat()) else ViewGroup.LayoutParams.WRAP_CONTENT,
                    if (h > 0) UiTheme.dp(density, h.toFloat()) else ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            val src = n.propString("src")
            iv.setImageBitmap(decodeImage(src))
        }
        load(node)
        register(node.id) { load(UiNode(node.id, node.type, it)) }
        return iv
    }

    private fun decodeImage(src: String) = try {
        when {
            src.startsWith("data:image") -> {
                val data = src.substringAfter(",", missingDelimiterValue = "")
                val bytes = Base64.decode(data, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            src.isNotEmpty() && File(src).isFile -> BitmapFactory.decodeFile(src)
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    private fun progress(node: UiNode): View {
        val label = TextView(activity).apply {
            setTextColor(UiTheme.TEXT_MUTED)
            textSize = 12f
        }
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progressTintList = UiTheme.controlTint()
            progressBackgroundTintList = ColorStateList.valueOf(UiTheme.TRACK)
        }
        fun apply(n: UiNode) {
            setLabel(label, n.propString("label"))
            label.visibility = if (label.text.isNullOrEmpty()) View.GONE else View.VISIBLE
            bar.isIndeterminate = n.propBool("indeterminate", false)
            val raw = n.propDouble("value", 0.0)
            val ratio = if (raw > 1.0) (raw / 100.0).coerceIn(0.0, 1.0) else raw.coerceIn(0.0, 1.0)
            bar.progress = (ratio * 1000).roundToInt()
        }
        apply(node)
        register(node.id) { apply(UiNode(node.id, node.type, it)) }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label)
            addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun badge(node: UiNode): View {
        val tv = TextView(activity).apply {
            setTextColor(UiTheme.chipText(true))
            textSize = 11f
            background = UiTheme.chipBackground(density, true)
            val p = UiTheme.dp(density, 6f)
            setPadding(p, UiTheme.dp(density, 2f), p, UiTheme.dp(density, 2f))
        }
        tv.text = node.propString("text")
        register(node.id) { tv.text = UiNode(node.id, node.type, it).propString("text") }
        return tv
    }

    private fun icon(node: UiNode): View {
        val tv = TextView(activity).apply {
            setTextColor(UiTheme.TEXT)
            textSize = 22f
            gravity = Gravity.CENTER
        }
        tv.text = node.propString("text")
        register(node.id) { tv.text = UiNode(node.id, node.type, it).propString("text") }
        return tv
    }

    private fun button(node: UiNode, icon: Boolean): View {
        val btn = Button(activity).apply {
            isAllCaps = false
            textSize = if (icon) 16f else 13f
            minimumHeight = UiTheme.dp(density, UiTheme.Metrics.BUTTON_MIN_H.toFloat())
            val padH = UiTheme.dp(density, 16f)
            setPadding(padH, paddingTop, padH, paddingBottom)
        }
        fun apply(n: UiNode) {
            btn.text = n.propString("label").ifEmpty { n.propString("text") }
            val enabled = n.propBool("enabled", true)
            val filled = n.propString("style", "filled") != "outline"
            btn.isEnabled = enabled
            btn.setTextColor(UiTheme.buttonText(filled))
            btn.background = UiTheme.buttonBackground(
                density,
                filled = filled,
                enabled = enabled,
            )
        }
        apply(node)
        btn.setOnClickListener { emit(node.id, "click", null) }
        register(node.id) { apply(UiNode(node.id, node.type, it)) }
        return btn
    }

    private fun toggle(node: UiNode, switch: Boolean): View {
        val label = TextView(activity).apply {
            setTextColor(UiTheme.TEXT)
            textSize = 13f
        }
        val control: View = if (switch) {
            Switch(activity).apply {
                thumbTintList = UiTheme.controlTint()
                trackTintList = UiTheme.controlTint()
            }
        } else {
            CheckBox(activity).apply { buttonTintList = UiTheme.controlTint() }
        }
        fun checked(): Boolean = when (control) {
            is Switch -> control.isChecked
            is CheckBox -> control.isChecked
            else -> false
        }
        fun setChecked(value: Boolean) {
            when (control) {
                is Switch -> control.isChecked = value
                is CheckBox -> control.isChecked = value
            }
        }
        fun apply(n: UiNode) {
            setLabel(label, n.propString("label"))
            val enabled = n.propBool("enabled", true)
            control.isEnabled = enabled
            label.isEnabled = enabled
            if (n.props.containsKey("value") || n.props.containsKey("label")) {
                setChecked(n.propBool("value", checked()))
            }
        }
        apply(node)
        val listener = View.OnClickListener { emit(node.id, "change", checked()) }
        control.setOnClickListener(listener)
        register(node.id) { apply(UiNode(node.id, node.type, it)) }
        return labeledRow(label, control)
    }

    private fun radio(node: UiNode): View {
        val rb = RadioButton(activity).apply {
            setTextColor(UiTheme.TEXT)
            buttonTintList = UiTheme.controlTint()
        }
        fun apply(n: UiNode) {
            rb.text = n.propString("label")
            rb.isEnabled = n.propBool("enabled", true)
            rb.isChecked = n.propBool("value", rb.isChecked)
        }
        apply(node)
        rb.setOnClickListener { emit(node.id, "change", rb.isChecked) }
        register(node.id) { apply(UiNode(node.id, node.type, it)) }
        return rb
    }

    private fun radioGroup(node: UiNode): View {
        val label = TextView(activity).apply {
            setTextColor(UiTheme.TEXT)
            textSize = 13f
        }
        val group = RadioGroup(activity)
        var options = node.propOptions().ifEmpty { node.propOptions("items") }
        fun rebuild(n: UiNode) {
            setLabel(label, n.propString("label"))
            label.visibility = if (label.text.isNullOrEmpty()) View.GONE else View.VISIBLE
            options = n.propOptions().ifEmpty { n.propOptions("items") }
            val selected = n.propString("value")
            group.removeAllViews()
            options.forEach { opt ->
                val rb = RadioButton(activity).apply {
                    id = View.generateViewId()
                    text = opt.label
                    tag = opt.id
                    setTextColor(UiTheme.TEXT)
                    buttonTintList = UiTheme.controlTint()
                    isChecked = opt.id == selected
                    isEnabled = n.propBool("enabled", true)
                }
                group.addView(rb)
            }
        }
        rebuild(node)
        group.setOnCheckedChangeListener { rg, checkedId ->
            if (applying) return@setOnCheckedChangeListener
            val rb = rg.findViewById<RadioButton>(checkedId) ?: return@setOnCheckedChangeListener
            emit(node.id, "change", rb.tag as? String)
        }
        register(node.id) { rebuild(UiNode(node.id, node.type, it)) }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label)
            addView(group)
        }
    }

    private fun slider(node: UiNode): View {
        val label = TextView(activity).apply { setTextColor(UiTheme.TEXT); textSize = 13f }
        val valueView = TextView(activity).apply { setTextColor(UiTheme.ACCENT); textSize = 12f }
        val seek = SeekBar(activity).apply {
            progressTintList = UiTheme.controlTint()
            thumbTintList = UiTheme.controlTint()
            progressBackgroundTintList = ColorStateList.valueOf(UiTheme.TRACK)
            val vPad = UiTheme.dp(density, 4f)
            setPadding(paddingStart, vPad, paddingEnd, vPad)
        }
        var min = node.propDouble("min", 0.0)
        var max = node.propDouble("max", 100.0)
        var step = node.propDouble("step", 1.0).let { if (it <= 0) 1.0 else it }
        fun toProgress(v: Double) = ((v - min) / step).roundToInt().coerceAtLeast(0)
        fun fromProgress(p: Int) = min + p * step
        fun apply(n: UiNode) {
            if (n.props.containsKey("min")) min = n.propDouble("min", min)
            if (n.props.containsKey("max")) max = n.propDouble("max", max)
            if (n.props.containsKey("step")) step = n.propDouble("step", step).let { if (it <= 0) 1.0 else it }
            setLabel(label, n.propString("label"))
            seek.isEnabled = n.propBool("enabled", true)
            seek.max = toProgress(max)
            val value = n.propDouble("value", fromProgress(seek.progress))
            seek.progress = toProgress(value)
            valueView.text = formatNumber(fromProgress(seek.progress))
        }
        apply(node)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val v = fromProgress(progress)
                valueView.text = formatNumber(v)
                if (fromUser) emit(node.id, "change", v)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        register(node.id) { apply(UiNode(node.id, node.type, it)) }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(valueView)
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(header)
            addView(seek, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun stepper(node: UiNode): View {
        val label = TextView(activity).apply { setTextColor(UiTheme.TEXT); textSize = 13f }
        val valueView = TextView(activity).apply {
            setTextColor(UiTheme.ACCENT)
            gravity = Gravity.CENTER
            minWidth = UiTheme.dp(density, 36f)
        }
        var min = node.propDouble("min", 0.0)
        var max = node.propDouble("max", 100.0)
        var step = node.propDouble("step", 1.0).let { if (it <= 0) 1.0 else it }
        var value = node.propDouble("value", min)
        fun show() { valueView.text = formatNumber(value) }
        fun apply(n: UiNode) {
            if (n.props.containsKey("min")) min = n.propDouble("min", min)
            if (n.props.containsKey("max")) max = n.propDouble("max", max)
            if (n.props.containsKey("step")) step = n.propDouble("step", step).let { if (it <= 0) 1.0 else it }
            if (n.props.containsKey("value")) value = n.propDouble("value", value)
            setLabel(label, n.propString("label"))
            show()
        }
        apply(node)
        val stepBtnSize = UiTheme.dp(density, 36f)
        val minus = Button(activity).apply {
            text = "−"
            isAllCaps = false
            minWidth = stepBtnSize
            minHeight = stepBtnSize
            minimumWidth = stepBtnSize
            minimumHeight = stepBtnSize
            setTextColor(UiTheme.buttonText(false))
            background = UiTheme.buttonBackground(density, filled = false)
            setOnClickListener {
                value = (value - step).coerceIn(min, max)
                show()
                emit(node.id, "change", value)
            }
        }
        val plus = Button(activity).apply {
            text = "+"
            isAllCaps = false
            minWidth = stepBtnSize
            minHeight = stepBtnSize
            minimumWidth = stepBtnSize
            minimumHeight = stepBtnSize
            setTextColor(UiTheme.buttonText(false))
            background = UiTheme.buttonBackground(density, filled = false)
            setOnClickListener {
                value = (value + step).coerceIn(min, max)
                show()
                emit(node.id, "change", value)
            }
        }
        register(node.id) { apply(UiNode(node.id, node.type, it)) }
        val controls = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(minus)
            addView(valueView)
            addView(plus)
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label)
            addView(controls)
        }
    }

    private fun textField(node: UiNode): View {
        val label = TextView(activity).apply { setTextColor(UiTheme.TEXT); textSize = 13f }
        val edit = EditText(activity).apply {
            styleInput()
        }
        fun apply(n: UiNode) {
            setLabel(label, n.propString("label"))
            edit.hint = n.propString("placeholder")
            edit.isEnabled = n.propBool("enabled", true)
            edit.inputType = when {
                n.propBool("password", false) -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                n.propBool("multiline", false) -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                else -> InputType.TYPE_CLASS_TEXT
            }
            if (n.props.containsKey("value") && edit.text.toString() != n.propString("value")) {
                edit.setText(n.propString("value"))
            }
        }
        apply(node)
        edit.addTextChangedListener(simpleWatcher { emit(node.id, "change", it) })
        edit.setOnEditorActionListener { _, _, _ ->
            emit(node.id, "submit", edit.text.toString())
            true
        }
        register(node.id) { apply(UiNode(node.id, node.type, it)) }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label)
            addView(edit, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun numberField(node: UiNode): View {
        val label = TextView(activity).apply { setTextColor(UiTheme.TEXT); textSize = 13f }
        val edit = EditText(activity).apply {
            styleInput()
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        fun apply(n: UiNode) {
            setLabel(label, n.propString("label"))
            edit.isEnabled = n.propBool("enabled", true)
            if (n.props.containsKey("value")) {
                val text = formatNumber(n.propDouble("value", 0.0))
                if (edit.text.toString() != text) edit.setText(text)
            }
        }
        apply(node)
        edit.addTextChangedListener(simpleWatcher { text ->
            emit(node.id, "change", text.toDoubleOrNull() ?: 0.0)
        })
        register(node.id) { apply(UiNode(node.id, node.type, it)) }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label)
            addView(edit, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun select(node: UiNode): View {
        val label = TextView(activity).apply { setTextColor(UiTheme.TEXT); textSize = 13f }
        val valueView = TextView(activity).apply {
            setTextColor(UiTheme.TEXT)
            textSize = UiTheme.Metrics.INPUT_TEXT
            includeFontPadding = false
            background = UiTheme.inputBackground(density)
            val p = UiTheme.dp(density, UiTheme.Metrics.INPUT_PAD.toFloat())
            setPadding(p, p, p, p)
            minimumHeight = UiTheme.dp(density, UiTheme.Metrics.INPUT_MIN_H.toFloat())
            gravity = Gravity.CENTER_VERTICAL
        }
        var options = node.propOptions().ifEmpty { node.propOptions("items") }
        var selected = node.propString("value")
        fun caption(): String = options.firstOrNull { it.id == selected }?.label ?: selected
        fun apply(n: UiNode) {
            setLabel(label, n.propString("label"))
            options = n.propOptions().ifEmpty { n.propOptions("items") }.ifEmpty { options }
            if (n.props.containsKey("value")) selected = n.propString("value")
            valueView.text = caption()
            valueView.isEnabled = n.propBool("enabled", true)
        }
        apply(node)
        valueView.setOnClickListener {
            val labels = options.map { it.label }.toTypedArray()
            AlertDialog.Builder(activity)
                .setTitle(label.text)
                .setItems(labels) { _, which ->
                    val opt = options.getOrNull(which) ?: return@setItems
                    selected = opt.id
                    valueView.text = opt.label
                    emit(node.id, "change", opt.id)
                }
                .show()
        }
        register(node.id) { apply(UiNode(node.id, node.type, it)) }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label)
            addView(valueView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun list(node: UiNode): View {
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        var options = node.propOptions().ifEmpty { node.propOptions("items") }
        var selected = node.propString("value")
        fun rebuild(n: UiNode) {
            options = n.propOptions().ifEmpty { n.propOptions("items") }.ifEmpty { options }
            if (n.props.containsKey("value")) selected = n.propString("value")
            root.removeAllViews()
            options.forEach { opt ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    isClickable = true
                    background = UiTheme.chipBackground(density, opt.id == selected)
                    val p = UiTheme.dp(density, UiTheme.Metrics.LIST_ITEM_PAD.toFloat())
                    setPadding(p, p, p, p)
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { bottomMargin = UiTheme.dp(density, 4f) }
                    layoutParams = lp
                }
                row.addView(TextView(activity).apply {
                    text = opt.label
                    setTextColor(UiTheme.chipText(opt.id == selected))
                    textSize = 13f
                })
                if (opt.subtitle.isNotEmpty()) {
                    row.addView(TextView(activity).apply {
                        text = opt.subtitle
                        setTextColor(UiTheme.TEXT_MUTED)
                        textSize = 11f
                    })
                }
                row.setOnClickListener {
                    selected = opt.id
                    emit(node.id, "change", opt.id)
                    emit(node.id, "click", opt.id)
                    rebuild(n)
                }
                root.addView(row)
            }
        }
        rebuild(node)
        register(node.id) { rebuild(UiNode(node.id, node.type, it)) }
        return root
    }

    private fun colorPicker(node: UiNode): View {
        val label = TextView(activity).apply { setTextColor(UiTheme.TEXT); textSize = 13f }
        val preview = View(activity).apply {
            layoutParams = LinearLayout.LayoutParams(UiTheme.dp(density, 28f), UiTheme.dp(density, 28f))
        }
        val edit = EditText(activity).apply {
            styleInput()
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val swatches = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        val presets = listOf(
            "#E53935", "#43A047", "#1E88E5", "#FDD835",
            "#FB8C00", "#8E24AA", "#FFFFFF", "#000000", "#00897B",
        )
        var current = node.propString("value", "#00897B")
        fun applyColor(hex: String, notify: Boolean) {
            current = if (hex.startsWith("#")) hex else "#$hex"
            edit.setText(current)
            try {
                preview.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(current))
                }
            } catch (_: Exception) {
            }
            if (notify) emit(node.id, "change", current)
        }
        fun apply(n: UiNode) {
            setLabel(label, n.propString("label"))
            edit.isEnabled = n.propBool("enabled", true)
            if (n.props.containsKey("value")) applyColor(n.propString("value", current), false)
        }
        apply(node)
        applyColor(current, false)
        presets.forEach { hex ->
            val swatch = View(activity).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(hex))
                }
                val size = UiTheme.dp(density, 22f)
                val lp = LinearLayout.LayoutParams(size, size).apply { marginEnd = UiTheme.dp(density, 6f) }
                layoutParams = lp
                setOnClickListener { applyColor(hex, true) }
            }
            swatches.addView(swatch)
        }
        edit.addTextChangedListener(simpleWatcher { applyColor(it, true) })
        register(node.id) { apply(UiNode(node.id, node.type, it)) }
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(preview)
            addView(edit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = UiTheme.dp(density, 8f)
            })
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label)
            addView(row)
            addView(swatches)
        }
    }

    private fun toggleGroup(node: UiNode): View {
        val label = TextView(activity).apply { setTextColor(UiTheme.TEXT); textSize = 13f }
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
        var options = node.propOptions().ifEmpty { node.propOptions("items") }
        var selected = node.propString("value")
        fun rebuild(n: UiNode) {
            setLabel(label, n.propString("label"))
            options = n.propOptions().ifEmpty { n.propOptions("items") }.ifEmpty { options }
            if (n.props.containsKey("value")) selected = n.propString("value")
            row.removeAllViews()
            options.forEach { opt ->
                val chip = TextView(activity).apply {
                    text = opt.label
                    textSize = 12f
                    val selectedChip = opt.id == selected
                    setTextColor(UiTheme.chipText(selectedChip))
                    background = UiTheme.chipBackground(density, selectedChip)
                    val p = UiTheme.dp(density, 8f)
                    setPadding(p, UiTheme.dp(density, 4f), p, UiTheme.dp(density, 4f))
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply { marginEnd = UiTheme.dp(density, 6f) }
                    layoutParams = lp
                    setOnClickListener {
                        selected = opt.id
                        emit(node.id, "change", opt.id)
                        rebuild(n)
                    }
                }
                row.addView(chip)
            }
        }
        rebuild(node)
        register(node.id) { rebuild(UiNode(node.id, node.type, it)) }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(label)
            addView(row)
        }
    }

    private fun labeledRow(label: TextView, control: View): View {
        return LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = UiTheme.dp(density, UiTheme.Metrics.ROW_MIN_H.toFloat())
            addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(control)
        }
    }

    private fun EditText.styleInput() {
        setTextColor(UiTheme.TEXT)
        setHintTextColor(UiTheme.TEXT_MUTED)
        textSize = UiTheme.Metrics.INPUT_TEXT
        includeFontPadding = false
        background = UiTheme.inputBackground(density)
        val h = UiTheme.dp(density, UiTheme.Metrics.INPUT_PAD.toFloat())
        val v = UiTheme.dp(density, 4f)
        setPadding(h, v, h, v)
        minimumHeight = UiTheme.dp(density, UiTheme.Metrics.INPUT_MIN_H.toFloat())
        setLineSpacing(0f, 1f)
    }

    private fun setLabel(tv: TextView, text: String) {
        tv.text = text
        tv.visibility = if (text.isBlank()) View.GONE else View.VISIBLE
    }

    private fun register(id: String, binder: (Map<String, Any?>) -> Unit) {
        if (id.isNotEmpty()) binders[id] = binder
    }

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private fun simpleWatcher(onChange: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (!applying) onChange(s?.toString().orEmpty())
        }
    }

    private class FlowLayout(
        context: android.content.Context,
        private val gap: Int,
    ) : ViewGroup(context) {
        override fun generateDefaultLayoutParams(): LayoutParams =
            MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

        override fun generateLayoutParams(p: LayoutParams): LayoutParams = MarginLayoutParams(p)

        override fun generateLayoutParams(attrs: android.util.AttributeSet): LayoutParams =
            MarginLayoutParams(context, attrs)

        override fun checkLayoutParams(p: LayoutParams): Boolean = p is MarginLayoutParams

        private fun margins(child: View): MarginLayoutParams =
            child.layoutParams as? MarginLayoutParams
                ?: MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            var x = paddingLeft
            var y = paddingTop
            var rowH = 0
            var maxW = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                measureChild(child, widthMeasureSpec, heightMeasureSpec)
                val m = margins(child)
                val cw = child.measuredWidth + m.leftMargin + m.rightMargin
                val ch = child.measuredHeight + m.topMargin + m.bottomMargin
                if (x + cw + paddingRight > width && x > paddingLeft) {
                    x = paddingLeft
                    y += rowH + gap
                    rowH = 0
                }
                x += cw + gap
                rowH = maxOf(rowH, ch)
                maxW = maxOf(maxW, x)
            }
            val h = y + rowH + paddingBottom
            setMeasuredDimension(
                resolveSize(maxW + paddingRight, widthMeasureSpec),
                resolveSize(h, heightMeasureSpec),
            )
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val width = r - l
            var x = paddingLeft
            var y = paddingTop
            var rowH = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                val m = margins(child)
                val cw = child.measuredWidth + m.leftMargin + m.rightMargin
                val ch = child.measuredHeight + m.topMargin + m.bottomMargin
                if (x + cw + paddingRight > width && x > paddingLeft) {
                    x = paddingLeft
                    y += rowH + gap
                    rowH = 0
                }
                val left = x + m.leftMargin
                val top = y + m.topMargin
                child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
                x += cw + gap
                rowH = maxOf(rowH, ch)
            }
        }
    }
}
