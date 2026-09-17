#nullable enable

using System;
using System.Collections.Generic;

namespace PVZRH.LauncherUi
{
    /// <summary>
    /// Fluent factories for PVZRH launcher widgets. Type strings match the Android inflater catalog.
    /// </summary>
    /// <example>
    /// <code>
    /// Ui.Column("root",
    ///     Ui.Text("t", "Cheat Menu", "title"),
    ///     Ui.Switch("god", "God mode", false),
    ///     Ui.Slider("sun", "Sun", 150, 0, 9990, 50)
    /// ).With("gap", 8);
    /// </code>
    /// </example>
    public static class Ui
    {
        /// <summary>Vertical stack of children. Set spacing with <c>.With("gap", n)</c>.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="children">Child widgets, top to bottom.</param>
        public static UiNode Column(string id, params UiNode[] children)
            => Layout(id, "column", children);

        /// <summary>Horizontal stack of children. Set spacing with <c>.With("gap", n)</c>.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="children">Child widgets, start to end.</param>
        public static UiNode Row(string id, params UiNode[] children)
            => Layout(id, "row", children);

        /// <summary>Scrollable container for overflowing content.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="children">Child widgets inside the scroll viewport.</param>
        public static UiNode Scroll(string id, params UiNode[] children)
            => Layout(id, "scroll", children);

        /// <summary>Flow layout that wraps children onto additional lines.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="children">Child widgets to wrap.</param>
        public static UiNode Wrap(string id, params UiNode[] children)
            => Layout(id, "wrap", children);

        /// <summary>Labeled group of related widgets.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="children">Grouped child widgets.</param>
        public static UiNode Group(string id, params UiNode[] children)
            => Layout(id, "group", children);

        /// <summary>Overlapping stack; later children draw above earlier ones.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="children">Stacked child widgets.</param>
        public static UiNode Stack(string id, params UiNode[] children)
            => Layout(id, "stack", children);

        /// <summary>Grid of children. Set column count with <c>.With("columns", n)</c>.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="children">Grid cells in row-major order.</param>
        public static UiNode Grid(string id, params UiNode[] children)
            => Layout(id, "grid", children);

        /// <summary>Tab host. Each child is a tab page; set titles with child <c>label</c> props.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="children">Tab pages.</param>
        public static UiNode Tabs(string id, params UiNode[] children)
            => Layout(id, "tabs", children);

        /// <summary>Expandable section. Set header text with <c>.With("label", text)</c>.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="children">Content shown when expanded.</param>
        public static UiNode Collapsible(string id, params UiNode[] children)
            => Layout(id, "collapsible", children);

        /// <summary>Fixed-size empty space.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="size">Extent in density-independent pixels. Defaults to 8.</param>
        public static UiNode Spacer(string id, int size = 8)
            => new UiNode(id, "spacer", Props(("size", size)));

        /// <summary>Horizontal rule used to separate sections.</summary>
        /// <param name="id">Widget id.</param>
        public static UiNode Divider(string id)
            => new UiNode(id, "divider");

        /// <summary>Static text label.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="text">Text to display.</param>
        /// <param name="style">One of <c>title</c>, <c>subtitle</c>, <c>body</c>, <c>caption</c>, <c>error</c>.</param>
        public static UiNode Text(string id, string text, string style = "body")
            => new UiNode(id, "text", Props(("text", text), ("style", style)));

        /// <summary>Markdown-formatted text block.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="text">Markdown source.</param>
        public static UiNode Markdown(string id, string text)
            => new UiNode(id, "markdown", Props(("text", text)));

        /// <summary>Image from a URI, asset path, or data URL. Zero width or height means intrinsic size.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="src">Image source.</param>
        /// <param name="width">Width in density-independent pixels, or 0 for intrinsic width.</param>
        /// <param name="height">Height in density-independent pixels, or 0 for intrinsic height.</param>
        public static UiNode Image(string id, string src, int width = 0, int height = 0)
            => new UiNode(id, "image", Props(("src", src), ("width", width), ("height", height)));

        /// <summary>Determinate or indeterminate progress indicator.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="value">Progress in the range 0..1 when determinate.</param>
        /// <param name="indeterminate">When true, ignores <paramref name="value"/> and animates continuously.</param>
        /// <param name="label">Optional caption shown with the bar.</param>
        public static UiNode Progress(string id, double value, bool indeterminate = false, string? label = null)
            => new UiNode(id, "progress", Props(
                ("value", value),
                ("indeterminate", indeterminate),
                ("label", label)));

        /// <summary>Compact status chip.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="text">Badge text.</param>
        public static UiNode Badge(string id, string text)
            => new UiNode(id, "badge", Props(("text", text)));

        /// <summary>Symbolic icon.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="symbol">Icon name understood by the inflater.</param>
        public static UiNode Icon(string id, string symbol)
            => new UiNode(id, "icon", Props(("symbol", symbol)));

        /// <summary>Push button. Default style is <c>filled</c>.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="label">Button text.</param>
        /// <param name="enabled">When false, the button ignores presses.</param>
        /// <param name="style">Inflater style such as <c>filled</c>, <c>outlined</c>, or <c>text</c>.</param>
        public static UiNode Button(string id, string label, bool enabled = true, string style = "filled")
            => new UiNode(id, "button", Props(("label", label), ("enabled", enabled), ("style", style)));

        /// <summary>Icon-only button.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="symbol">Icon name understood by the inflater.</param>
        /// <param name="enabled">When false, the button ignores presses.</param>
        public static UiNode IconButton(string id, string symbol, bool enabled = true)
            => new UiNode(id, "icon_button", Props(("symbol", symbol), ("enabled", enabled)));

        /// <summary>Boolean toggle with a text label.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="label">Switch text.</param>
        /// <param name="value">Current on/off state.</param>
        /// <param name="enabled">When false, the switch cannot be changed.</param>
        public static UiNode Switch(string id, string label, bool value, bool enabled = true)
            => new UiNode(id, "switch", Props(("label", label), ("value", value), ("enabled", enabled)));

        /// <summary>Boolean checkbox with a text label.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="label">Checkbox text.</param>
        /// <param name="value">Current checked state.</param>
        /// <param name="enabled">When false, the checkbox cannot be changed.</param>
        public static UiNode Checkbox(string id, string label, bool value, bool enabled = true)
            => new UiNode(id, "checkbox", Props(("label", label), ("value", value), ("enabled", enabled)));

        /// <summary>Single radio button. Group exclusive selection with <see cref="RadioGroup"/>.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="label">Radio text.</param>
        /// <param name="value">Current selected state.</param>
        /// <param name="enabled">When false, the radio cannot be changed.</param>
        public static UiNode Radio(string id, string label, bool value, bool enabled = true)
            => new UiNode(id, "radio", Props(("label", label), ("value", value), ("enabled", enabled)));

        /// <summary>Exclusive choice among <see cref="UiOption"/> values.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="label">Group caption.</param>
        /// <param name="options">Available options.</param>
        /// <param name="value">Id of the selected option.</param>
        public static UiNode RadioGroup(string id, string label, IEnumerable<UiOption> options, string value)
            => new UiNode(id, "radio_group", Props(
                ("label", label),
                ("options", CopyOptions(options)),
                ("value", value)));

        /// <summary>Continuous numeric slider.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="label">Slider caption.</param>
        /// <param name="value">Current value.</param>
        /// <param name="min">Inclusive minimum.</param>
        /// <param name="max">Inclusive maximum.</param>
        /// <param name="step">Snap increment. Defaults to 1.</param>
        public static UiNode Slider(string id, string label, double value, double min, double max, double step = 1)
            => new UiNode(id, "slider", Props(
                ("label", label),
                ("value", value),
                ("min", min),
                ("max", max),
                ("step", step)));

        /// <summary>Numeric stepper with increment and decrement controls.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="label">Stepper caption.</param>
        /// <param name="value">Current value.</param>
        /// <param name="min">Inclusive minimum.</param>
        /// <param name="max">Inclusive maximum.</param>
        /// <param name="step">Amount added or subtracted per tap. Defaults to 1.</param>
        public static UiNode Stepper(string id, string label, double value, double min, double max, double step = 1)
            => new UiNode(id, "stepper", Props(
                ("label", label),
                ("value", value),
                ("min", min),
                ("max", max),
                ("step", step)));

        /// <summary>Single-line or multiline text input.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="label">Field caption.</param>
        /// <param name="value">Current text.</param>
        /// <param name="placeholder">Hint shown when the value is empty.</param>
        /// <param name="password">When true, the value is masked.</param>
        /// <param name="multiline">When true, the field accepts multiple lines.</param>
        public static UiNode TextField(
            string id,
            string label,
            string value,
            string? placeholder = null,
            bool password = false,
            bool multiline = false)
            => new UiNode(id, "text_field", Props(
                ("label", label),
                ("value", value),
                ("placeholder", placeholder),
                ("password", password),
                ("multiline", multiline)));

        /// <summary>Numeric text input. NaN <paramref name="min"/> or <paramref name="max"/> is omitted.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="label">Field caption.</param>
        /// <param name="value">Current number.</param>
        /// <param name="min">Inclusive minimum, or <see cref="double.NaN"/> for unbounded.</param>
        /// <param name="max">Inclusive maximum, or <see cref="double.NaN"/> for unbounded.</param>
        /// <param name="step">Suggested increment. Defaults to 1.</param>
        public static UiNode NumberField(
            string id,
            string label,
            double value,
            double min = double.NaN,
            double max = double.NaN,
            double step = 1)
            => new UiNode(id, "number_field", Props(
                ("label", label),
                ("value", value),
                ("min", min),
                ("max", max),
                ("step", step)));

        /// <summary>Drop-down selection among <see cref="UiOption"/> values.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="label">Field caption.</param>
        /// <param name="options">Available options.</param>
        /// <param name="value">Id of the selected option.</param>
        public static UiNode Select(string id, string label, IEnumerable<UiOption> options, string value)
            => new UiNode(id, "select", Props(
                ("label", label),
                ("options", CopyOptions(options)),
                ("value", value)));

        /// <summary>Selectable list of <see cref="UiOption"/> items.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="items">Rows to display.</param>
        /// <param name="value">Id of the selected item, if any.</param>
        public static UiNode List(string id, IEnumerable<UiOption> items, string? value = null)
            => new UiNode(id, "list", Props(("items", CopyOptions(items)), ("value", value)));

        /// <summary>Color input stored as <c>#RRGGBB</c>.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="label">Field caption.</param>
        /// <param name="value">Current color in <c>#RRGGBB</c> form.</param>
        public static UiNode ColorPicker(string id, string label, string value)
            => new UiNode(id, "color_picker", Props(("label", label), ("value", value)));

        /// <summary>Segmented control that selects one <see cref="UiOption"/>.</summary>
        /// <param name="id">Widget id.</param>
        /// <param name="label">Group caption.</param>
        /// <param name="options">Available segments.</param>
        /// <param name="value">Id of the selected option.</param>
        public static UiNode ToggleGroup(string id, string label, IEnumerable<UiOption> options, string value)
            => new UiNode(id, "toggle_group", Props(
                ("label", label),
                ("options", CopyOptions(options)),
                ("value", value)));

        private static UiNode Layout(string id, string type, UiNode[] children)
            => new UiNode(id, type, children: children);

        private static List<UiOption> CopyOptions(IEnumerable<UiOption> options)
        {
            if (options is null)
                throw new ArgumentNullException(nameof(options));
            return options as List<UiOption> ?? new List<UiOption>(options);
        }

        private static Dictionary<string, object?> Props(params (string Key, object? Value)[] entries)
        {
            var dict = new Dictionary<string, object?>(entries.Length);
            foreach ((string key, object? value) in entries)
            {
                if (value is null)
                    continue;
                if (value is double d && (double.IsNaN(d) || double.IsInfinity(d)))
                    continue;
                if (value is float f && (float.IsNaN(f) || float.IsInfinity(f)))
                    continue;
                dict[key] = value;
            }

            return dict;
        }
    }
}
