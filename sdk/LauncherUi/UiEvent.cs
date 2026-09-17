#nullable enable

using System;
using System.Globalization;

namespace PVZRH.LauncherUi;

/// <summary>
/// Widget event raised by the launcher (<c>click</c>, <c>change</c>, <c>submit</c>).
/// </summary>
public sealed class UiEvent
{
    /// <summary>Id of the widget that produced the event.</summary>
    public string WidgetId { get; }

    /// <summary>Event name: <c>click</c>, <c>change</c>, or <c>submit</c>.</summary>
    public string Name { get; }

    /// <summary>
    /// Event payload: <see cref="bool"/>, <see cref="double"/>, <see cref="string"/>, or null.
    /// </summary>
    public object? Value { get; }

    /// <summary>
    /// Creates a widget event.
    /// </summary>
    /// <param name="widgetId">Widget id. Must not be null.</param>
    /// <param name="name">Event name. Must not be null.</param>
    /// <param name="value">Optional payload.</param>
    public UiEvent(string widgetId, string name, object? value)
    {
        WidgetId = widgetId ?? throw new ArgumentNullException(nameof(widgetId));
        Name = name ?? throw new ArgumentNullException(nameof(name));
        Value = value;
    }

    /// <summary>
    /// Reads <see cref="Value"/> as a boolean.
    /// </summary>
    /// <param name="defaultValue">Returned when the value is missing or not boolean-like.</param>
    public bool AsBool(bool defaultValue = false)
    {
        switch (Value)
        {
            case bool b:
                return b;
            case double d:
                return d != 0 && !double.IsNaN(d);
            case string s:
                if (bool.TryParse(s, out bool parsed))
                {
                    return parsed;
                }

                if (s == "1")
                {
                    return true;
                }

                if (s == "0")
                {
                    return false;
                }

                return defaultValue;
            default:
                return defaultValue;
        }
    }

    /// <summary>
    /// Reads <see cref="Value"/> as a double.
    /// </summary>
    /// <param name="defaultValue">Returned when the value is missing or not numeric.</param>
    public double AsDouble(double defaultValue = 0)
    {
        switch (Value)
        {
            case double d:
                return double.IsNaN(d) || double.IsInfinity(d) ? defaultValue : d;
            case bool b:
                return b ? 1 : 0;
            case string s:
                if (double.TryParse(s, NumberStyles.Float, CultureInfo.InvariantCulture, out double parsed) &&
                    !double.IsNaN(parsed) &&
                    !double.IsInfinity(parsed))
                {
                    return parsed;
                }

                return defaultValue;
            default:
                return defaultValue;
        }
    }

    /// <summary>
    /// Reads <see cref="Value"/> as a 32-bit integer.
    /// </summary>
    /// <param name="defaultValue">Returned when the value is missing or not convertible.</param>
    public int AsInt(int defaultValue = 0)
    {
        switch (Value)
        {
            case double d:
                if (double.IsNaN(d) || double.IsInfinity(d) || d < int.MinValue || d > int.MaxValue)
                {
                    return defaultValue;
                }

                return Convert.ToInt32(d);
            case bool b:
                return b ? 1 : 0;
            case string s:
                if (int.TryParse(s, NumberStyles.Integer, CultureInfo.InvariantCulture, out int n))
                {
                    return n;
                }

                if (double.TryParse(s, NumberStyles.Float, CultureInfo.InvariantCulture, out double parsed) &&
                    !double.IsNaN(parsed) &&
                    !double.IsInfinity(parsed) &&
                    parsed >= int.MinValue &&
                    parsed <= int.MaxValue)
                {
                    return Convert.ToInt32(parsed);
                }

                return defaultValue;
            default:
                return defaultValue;
        }
    }

    /// <summary>
    /// Reads <see cref="Value"/> as a string.
    /// </summary>
    /// <param name="defaultValue">Returned when the value is null.</param>
    public string AsString(string defaultValue = "")
    {
        switch (Value)
        {
            case null:
                return defaultValue ?? string.Empty;
            case string s:
                return s;
            case bool b:
                return b ? "true" : "false";
            case IFormattable f:
                return f.ToString(null, CultureInfo.InvariantCulture) ?? defaultValue ?? string.Empty;
            default:
                return Value.ToString() ?? defaultValue ?? string.Empty;
        }
    }
}
