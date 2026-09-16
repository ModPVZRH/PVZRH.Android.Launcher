#nullable enable

namespace PVZRH.LauncherUi;

/// <summary>
/// Visual chrome for a plugin's launcher panel (size, colors, corner radius, elevation).
/// </summary>
public sealed class PanelStyle
{
    /// <summary>Panel width in density-independent pixels. Defaults to 320.</summary>
    public int Width { get; set; } = 320;

    /// <summary>Panel height in density-independent pixels. Defaults to 420.</summary>
    public int Height { get; set; } = 420;

    /// <summary>Corner radius in density-independent pixels. Defaults to 8.</summary>
    public double CornerRadius { get; set; } = 8;

    /// <summary>Panel background color as <c>#AARRGGBB</c> or <c>#RRGGBB</c>. Null keeps the launcher default.</summary>
    public string? Background { get; set; }

    /// <summary>Header bar color as <c>#AARRGGBB</c> or <c>#RRGGBB</c>. Null keeps the launcher default.</summary>
    public string? HeaderColor { get; set; }

    /// <summary>Title text color as <c>#AARRGGBB</c> or <c>#RRGGBB</c>. Null keeps the launcher default.</summary>
    public string? TitleColor { get; set; }

    /// <summary>Shadow elevation in density-independent pixels. Defaults to 16.</summary>
    public double Elevation { get; set; } = 16;
}
