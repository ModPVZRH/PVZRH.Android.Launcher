#nullable enable

using System;
using System.Collections.Generic;
using System.Globalization;
using System.Text;

namespace PVZRH.LauncherUi
{
    /// <summary>
    /// A node in the launcher UI tree. Serialized to JSON for the Android inflater.
    /// </summary>
    public sealed class UiNode
    {
        /// <summary>Stable widget id used for events and updates.</summary>
        public string Id { get; }

        /// <summary>Inflater type string, such as <c>column</c> or <c>text_field</c>.</summary>
        public string Type { get; }

        /// <summary>Type-specific properties. Null values are omitted from JSON.</summary>
        public Dictionary<string, object?> Props { get; }

        /// <summary>Child widgets, in display order.</summary>
        public List<UiNode> Children { get; }

        /// <summary>
        /// Creates a UI node.
        /// </summary>
        /// <param name="id">Widget id. Must not be null.</param>
        /// <param name="type">Inflater type string. Must not be null.</param>
        /// <param name="props">Optional properties. Copied when provided.</param>
        /// <param name="children">Optional child widgets. Copied when provided; null entries are ignored.</param>
        public UiNode(
            string id,
            string type,
            Dictionary<string, object?>? props = null,
            IEnumerable<UiNode>? children = null)
        {
            Id = id ?? throw new ArgumentNullException(nameof(id));
            Type = type ?? throw new ArgumentNullException(nameof(type));
            Props = props != null
                ? new Dictionary<string, object?>(props)
                : new Dictionary<string, object?>();
            Children = new List<UiNode>();
            if (children == null)
                return;
            foreach (UiNode child in children)
            {
                if (child != null)
                    Children.Add(child);
            }
        }

        /// <summary>
        /// Sets a property and returns this node for chaining.
        /// </summary>
        /// <param name="key">Property name.</param>
        /// <param name="value">Property value. Null is stored and later omitted from JSON.</param>
        /// <returns>This node.</returns>
        public UiNode With(string key, object? value)
        {
            if (key is null)
                throw new ArgumentNullException(nameof(key));
            Props[key] = value;
            return this;
        }

        /// <summary>
        /// Sets <c>width</c> and <c>height</c> in density-independent pixels. Zero means unconstrained.
        /// </summary>
        /// <param name="width">Width in dp, or 0 for unconstrained.</param>
        /// <param name="height">Height in dp, or 0 for unconstrained.</param>
        /// <returns>This node.</returns>
        public UiNode Size(int width = 0, int height = 0)
        {
            With("width", width);
            With("height", height);
            return this;
        }

        /// <summary>
        /// Sets <c>minWidth</c> and <c>minHeight</c> in density-independent pixels. Zero means no minimum.
        /// </summary>
        /// <param name="minWidth">Minimum width in dp, or 0 for none.</param>
        /// <param name="minHeight">Minimum height in dp, or 0 for none.</param>
        /// <returns>This node.</returns>
        public UiNode MinSize(int minWidth = 0, int minHeight = 0)
        {
            With("minWidth", minWidth);
            With("minHeight", minHeight);
            return this;
        }

        /// <summary>
        /// Sets uniform <c>padding</c> in density-independent pixels.
        /// </summary>
        /// <param name="all">Padding applied to all sides.</param>
        /// <returns>This node.</returns>
        public UiNode Padding(int all)
            => With("padding", all);

        /// <summary>
        /// Sets horizontal and vertical padding in density-independent pixels.
        /// </summary>
        public UiNode Padding(int horizontal, int vertical)
        {
            With("paddingH", horizontal);
            With("paddingV", vertical);
            return this;
        }

        /// <summary>
        /// Sets per-side padding in density-independent pixels.
        /// </summary>
        public UiNode Padding(int left, int top, int right, int bottom)
        {
            With("paddingLeft", left);
            With("paddingTop", top);
            With("paddingRight", right);
            With("paddingBottom", bottom);
            return this;
        }

        /// <summary>
        /// Sets uniform <c>margin</c> in density-independent pixels.
        /// </summary>
        public UiNode Margin(int all)
            => With("margin", all);

        /// <summary>
        /// Sets horizontal and vertical margin in density-independent pixels.
        /// </summary>
        public UiNode Margin(int horizontal, int vertical)
        {
            With("marginH", horizontal);
            With("marginV", vertical);
            return this;
        }

        /// <summary>
        /// Sets per-side margin in density-independent pixels.
        /// </summary>
        public UiNode Margin(int left, int top, int right, int bottom)
        {
            With("marginLeft", left);
            With("marginTop", top);
            With("marginRight", right);
            With("marginBottom", bottom);
            return this;
        }

        /// <summary>
        /// Sets <c>textSize</c> in scaled pixels (sp).
        /// </summary>
        /// <param name="sp">Font size in sp.</param>
        /// <returns>This node.</returns>
        public UiNode TextSize(double sp)
            => With("textSize", sp);

        /// <summary>
        /// Serializes this node to a compact JSON object with <c>id</c>, <c>type</c>, <c>props</c>, and <c>children</c>.
        /// </summary>
        public string ToJson()
        {
            var sb = new StringBuilder();
            WriteTo(sb);
            return sb.ToString();
        }

        internal void WriteTo(StringBuilder sb)
        {
            sb.Append("{\"id\":");
            UiJson.WriteString(sb, Id);
            sb.Append(",\"type\":");
            UiJson.WriteString(sb, Type);
            sb.Append(",\"props\":");
            UiJson.WriteObject(sb, Props);
            sb.Append(",\"children\":[");
            for (int i = 0; i < Children.Count; i++)
            {
                if (i > 0)
                    sb.Append(',');
                Children[i].WriteTo(sb);
            }

            sb.Append("]}");
        }
    }

    /// <summary>
    /// A selectable item used by list, select, radio, and toggle widgets.
    /// </summary>
    public sealed class UiOption
    {
        /// <summary>Value written back when this option is selected.</summary>
        public string Id { get; }

        /// <summary>Primary text shown to the user.</summary>
        public string Label { get; }

        /// <summary>Optional secondary text. Omitted from JSON when null.</summary>
        public string? Subtitle { get; }

        /// <summary>
        /// Creates a selectable option.
        /// </summary>
        /// <param name="id">Option value. Must not be null.</param>
        /// <param name="label">Display label. Must not be null.</param>
        /// <param name="subtitle">Optional secondary text.</param>
        public UiOption(string id, string label, string? subtitle = null)
        {
            Id = id ?? throw new ArgumentNullException(nameof(id));
            Label = label ?? throw new ArgumentNullException(nameof(label));
            Subtitle = subtitle;
        }

        /// <summary>Serializes this option to a compact JSON object.</summary>
        public string ToJson()
        {
            var sb = new StringBuilder();
            WriteTo(sb);
            return sb.ToString();
        }

        internal void WriteTo(StringBuilder sb)
        {
            sb.Append("{\"id\":");
            UiJson.WriteString(sb, Id);
            sb.Append(",\"label\":");
            UiJson.WriteString(sb, Label);
            if (Subtitle != null)
            {
                sb.Append(",\"subtitle\":");
                UiJson.WriteString(sb, Subtitle);
            }

            sb.Append('}');
        }
    }

    /// <summary>
    /// Dependency-free JSON writer for UI trees. Null, NaN, and infinity property values are skipped.
    /// </summary>
    internal static class UiJson
    {
        internal static void WriteObject(StringBuilder sb, IDictionary<string, object?> props)
        {
            sb.Append('{');
            bool first = true;
            foreach (KeyValuePair<string, object?> pair in props)
            {
                if (ShouldSkip(pair.Value))
                    continue;
                if (!first)
                    sb.Append(',');
                first = false;
                WriteString(sb, pair.Key);
                sb.Append(':');
                WriteValue(sb, pair.Value);
            }

            sb.Append('}');
        }

        internal static void WriteValue(StringBuilder sb, object? value)
        {
            if (value is null)
            {
                sb.Append("null");
                return;
            }

            switch (value)
            {
                case string s:
                    WriteString(sb, s);
                    return;
                case bool b:
                    sb.Append(b ? "true" : "false");
                    return;
                case char c:
                    WriteString(sb, c.ToString());
                    return;
                case UiOption option:
                    option.WriteTo(sb);
                    return;
                case UiNode node:
                    node.WriteTo(sb);
                    return;
                case Dictionary<string, object?> dict:
                    WriteObject(sb, dict);
                    return;
                case IDictionary<string, object?> genericMap:
                    WriteObject(sb, genericMap);
                    return;
                case System.Collections.IDictionary map:
                    WriteDictionary(sb, map);
                    return;
                case IEnumerable<UiOption> options:
                    WriteArray(sb, options, static (buffer, item) => item.WriteTo(buffer));
                    return;
                case IEnumerable<UiNode> nodes:
                    WriteArray(sb, nodes, static (buffer, item) => item.WriteTo(buffer));
                    return;
                case IEnumerable<Dictionary<string, object?>> objects:
                    WriteArray(sb, objects, static (buffer, item) => WriteObject(buffer, item));
                    return;
                case IEnumerable<object?> items:
                    WriteArray(sb, items, static (buffer, item) => WriteValue(buffer, item));
                    return;
                case System.Collections.IEnumerable enumerable:
                    WriteEnumerable(sb, enumerable);
                    return;
                default:
                    if (TryWriteNumber(sb, value))
                        return;
                    WriteString(sb, Convert.ToString(value, CultureInfo.InvariantCulture) ?? string.Empty);
                    return;
            }
        }

        internal static void WriteString(StringBuilder sb, string value)
        {
            sb.Append('"');
            foreach (char c in value)
            {
                switch (c)
                {
                    case '"':
                        sb.Append("\\\"");
                        break;
                    case '\\':
                        sb.Append("\\\\");
                        break;
                    case '\b':
                        sb.Append("\\b");
                        break;
                    case '\f':
                        sb.Append("\\f");
                        break;
                    case '\n':
                        sb.Append("\\n");
                        break;
                    case '\r':
                        sb.Append("\\r");
                        break;
                    case '\t':
                        sb.Append("\\t");
                        break;
                    default:
                        if (c < ' ')
                        {
                            sb.Append("\\u");
                            sb.Append(((int)c).ToString("x4", CultureInfo.InvariantCulture));
                        }
                        else
                        {
                            sb.Append(c);
                        }

                        break;
                }
            }

            sb.Append('"');
        }

        private static void WriteDictionary(StringBuilder sb, System.Collections.IDictionary map)
        {
            sb.Append('{');
            bool first = true;
            foreach (System.Collections.DictionaryEntry entry in map)
            {
                if (ShouldSkip(entry.Value))
                    continue;
                if (!first)
                    sb.Append(',');
                first = false;
                WriteString(sb, Convert.ToString(entry.Key, CultureInfo.InvariantCulture) ?? string.Empty);
                sb.Append(':');
                WriteValue(sb, entry.Value);
            }

            sb.Append('}');
        }

        private static void WriteEnumerable(StringBuilder sb, System.Collections.IEnumerable enumerable)
        {
            sb.Append('[');
            bool first = true;
            foreach (object? item in enumerable)
            {
                if (!first)
                    sb.Append(',');
                first = false;
                WriteValue(sb, item);
            }

            sb.Append(']');
        }

        private static void WriteArray<T>(StringBuilder sb, IEnumerable<T> items, Action<StringBuilder, T> writeItem)
        {
            sb.Append('[');
            bool first = true;
            foreach (T item in items)
            {
                if (!first)
                    sb.Append(',');
                first = false;
                writeItem(sb, item);
            }

            sb.Append(']');
        }

        private static bool TryWriteNumber(StringBuilder sb, object value)
        {
            switch (Convert.GetTypeCode(value))
            {
                case TypeCode.Byte:
                case TypeCode.SByte:
                case TypeCode.Int16:
                case TypeCode.UInt16:
                case TypeCode.Int32:
                case TypeCode.UInt32:
                case TypeCode.Int64:
                case TypeCode.UInt64:
                    sb.Append(Convert.ToString(value, CultureInfo.InvariantCulture));
                    return true;
                case TypeCode.Single:
                {
                    float number = (float)value;
                    if (float.IsNaN(number) || float.IsInfinity(number))
                        sb.Append("null");
                    else
                        sb.Append(number.ToString("G9", CultureInfo.InvariantCulture));
                    return true;
                }
                case TypeCode.Double:
                {
                    double number = (double)value;
                    if (double.IsNaN(number) || double.IsInfinity(number))
                        sb.Append("null");
                    else
                        sb.Append(number.ToString("G17", CultureInfo.InvariantCulture));
                    return true;
                }
                case TypeCode.Decimal:
                    sb.Append(((decimal)value).ToString(CultureInfo.InvariantCulture));
                    return true;
                default:
                    return false;
            }
        }

        private static bool ShouldSkip(object? value)
        {
            if (value is null)
                return true;
            if (value is double d && (double.IsNaN(d) || double.IsInfinity(d)))
                return true;
            if (value is float f && (float.IsNaN(f) || float.IsInfinity(f)))
                return true;
            return false;
        }
    }
}
