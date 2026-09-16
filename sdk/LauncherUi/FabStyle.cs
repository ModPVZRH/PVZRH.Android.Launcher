#nullable enable

namespace PVZRH.LauncherUi;

/// <summary>Shape of the floating action button. Unspecified messages default to <see cref="Circle"/>.</summary>
public enum FabShape
{
    /// <summary>Circular button (default).</summary>
    Circle,
    /// <summary>Rounded rectangle; uses <see cref="FabStyle.CornerRadius"/>.</summary>
    Rounded,
    /// <summary>Square with no rounding.</summary>
    Square,
}

/// <summary>
/// Chrome for a plugin's floating button. Null <see cref="Background"/> keeps the launcher default
/// (primary circle with no icon, transparent when an icon is set).
/// </summary>
public sealed class FabStyle
{
    /// <summary>One or two character fallback label when no icon is set.</summary>
    public string Label { get; set; } = "";

    /// <summary>Button size in density-independent pixels. Defaults to 48.</summary>
    public int Size { get; set; } = 48;

    /// <summary>
    /// Fill color as <c>#AARRGGBB</c> or <c>#RRGGBB</c>.
    /// Use <c>#00000000</c> for a fully transparent background.
    /// Null keeps the launcher default.
    /// </summary>
    public string? Background { get; set; }

    /// <summary>Button outline. Defaults to <see cref="FabShape.Circle"/>.</summary>
    public FabShape Shape { get; set; } = FabShape.Circle;

    /// <summary>Corner radius in dp when <see cref="Shape"/> is <see cref="FabShape.Rounded"/>. Defaults to 12.</summary>
    public double CornerRadius { get; set; } = 12;
}
