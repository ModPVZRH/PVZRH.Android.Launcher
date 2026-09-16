#nullable enable

using BepInEx;
using BepInEx.Unity.IL2CPP;

namespace PVZRH.LauncherUi;

/// <summary>
/// BepInEx plugin that ships the launcher UI client.
/// Other plugins should depend on this assembly and declare
/// <c>[BepInDependency(LauncherUiPlugin.PluginGuid)]</c>.
/// </summary>
/// <example>
/// <code>
/// [BepInPlugin("com.example.mymod", "My Mod", "1.0.0")]
/// [BepInDependency(LauncherUiPlugin.PluginGuid)]
/// public class MyMod : BasePlugin
/// {
///     public override void Load()
///     {
///         var ui = LauncherUiClient.Connect("com.example.mymod", "My Mod");
///         ui.OnEvent += e => { if (e.WidgetId == "god") God = e.AsBool(); };
///         ui.SetTree(Ui.Column("root", Ui.Switch("god", "God mode", false)));
///     }
/// }
/// </code>
/// </example>
[BepInPlugin(PluginGuid, PluginName, PluginVersion)]
public class LauncherUiPlugin : BasePlugin
{
    /// <summary>BepInEx GUID other plugins pass to <c>[BepInDependency]</c>.</summary>
    public const string PluginGuid = "com.pvzrh.launcher.ui";

    /// <summary>Human-readable plugin name.</summary>
    public const string PluginName = "PVZRH Launcher UI";

    /// <summary>Plugin version, also used as the default client version.</summary>
    public const string PluginVersion = "1.0.0";

    /// <summary>The loaded plugin instance, or null before <see cref="Load"/>.</summary>
    public static LauncherUiPlugin? Instance { get; private set; }

    /// <inheritdoc />
    public override void Load()
    {
        Instance = this;
        Log.LogInfo(
            "Launcher UI client loaded. Other plugins: [BepInDependency(\"" +
            PluginGuid +
            "\")] then LauncherUiClient.Connect(pluginId, pluginName).");
    }
}
