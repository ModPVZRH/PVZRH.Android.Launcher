#nullable enable

using System;
using System.Collections.Generic;
using System.IO;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using BepInEx;

namespace PVZRH.LauncherUi;

/// <summary>
/// TCP client for the PVZRH Android launcher Java UI bridge.
/// </summary>
/// <remarks>
/// Typical BepInEx plugin usage (this DLL is loaded by BepInEx from <c>plugins/</c>):
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
/// </remarks>
public sealed class LauncherUiClient : IDisposable
{
    private const int HelloAckTimeoutMs = 5000;

    private static readonly Encoding Utf8NoBom = new UTF8Encoding(encoderShouldEmitUTF8Identifier: false);

    private readonly TcpClient _tcp;
    private readonly NetworkStream _stream;
    private readonly object _writeLock = new object();
    private readonly Thread _receiveThread;
    private volatile bool _disposed;

    /// <summary>Session id assigned by the launcher in <c>hello_ack</c>.</summary>
    public string SessionId { get; }

    /// <summary>Raised when a widget fires <c>click</c>, <c>change</c>, or <c>submit</c>.</summary>
    public event Action<UiEvent>? OnEvent;

    /// <summary>Raised for session lifecycle states: <c>shown</c>, <c>hidden</c>, <c>destroyed</c>.</summary>
    public event Action<string>? OnLifecycle;

    /// <summary>Raised once when the TCP connection is lost and this instance was not disposed.</summary>
    public event Action? OnDisconnected;

    private LauncherUiClient(TcpClient tcp, NetworkStream stream, string sessionId)
    {
        _tcp = tcp;
        _stream = stream;
        SessionId = sessionId;
        _receiveThread = new Thread(ReceiveLoop)
        {
            IsBackground = true,
            Name = "PVZRH.LauncherUi.Receive"
        };
        _receiveThread.Start();
    }

    /// <summary>
    /// Loads discovery, connects to the launcher UI bridge, sends <c>hello</c>, and waits for <c>hello_ack</c>.
    /// </summary>
    /// <param name="discoveryFilePath">Path to <c>launcher-bridge.json</c>.</param>
    /// <param name="pluginId">Stable plugin id sent in the handshake.</param>
    /// <param name="pluginName">Human-readable plugin name.</param>
    /// <param name="pluginVersion">Plugin version string. Defaults to <c>1.0.0</c>.</param>
    /// <exception cref="ArgumentNullException">A required argument is null.</exception>
    /// <exception cref="IOException">
    /// The discovery file is missing or invalid, the TCP connection failed, or the launcher
    /// closed the connection without a valid <c>hello_ack</c> (for example a bad token).
    /// </exception>
    /// <exception cref="TimeoutException">No <c>hello_ack</c> arrived within 5 seconds.</exception>
    public static LauncherUiClient Connect(
        string discoveryFilePath,
        string pluginId,
        string pluginName,
        string pluginVersion = "1.0.0")
    {
        if (discoveryFilePath is null)
        {
            throw new ArgumentNullException(nameof(discoveryFilePath));
        }

        if (pluginId is null)
        {
            throw new ArgumentNullException(nameof(pluginId));
        }

        if (pluginName is null)
        {
            throw new ArgumentNullException(nameof(pluginName));
        }

        if (pluginVersion is null)
        {
            throw new ArgumentNullException(nameof(pluginVersion));
        }

        BridgeDiscoveryInfo info = BridgeDiscoveryInfo.Load(discoveryFilePath);
        if (info.Protocol != FrameCodec.Version)
        {
            throw new IOException(
                "Launcher UI bridge protocol " + info.Protocol +
                " is incompatible with this client (protocol " + FrameCodec.Version + ").");
        }

        var tcp = new TcpClient();
        NetworkStream? stream = null;
        bool own = true;
        try
        {
            try
            {
                tcp.NoDelay = true;
                tcp.Connect(info.Host, info.Port);
            }
            catch (SocketException ex)
            {
                throw new IOException(
                    "Failed to connect to launcher UI bridge at " + info.Host + ":" + info.Port + ": " + ex.Message,
                    ex);
            }
            catch (ArgumentOutOfRangeException ex)
            {
                throw new IOException(
                    "Failed to connect to launcher UI bridge: invalid port " + info.Port + ".",
                    ex);
            }

            stream = tcp.GetStream();
            byte[] hello = Utf8NoBom.GetBytes(BridgeJson.Hello(pluginId, pluginName, pluginVersion, info.Token));
            FrameCodec.WriteFrame(stream, hello);

            stream.ReadTimeout = HelloAckTimeoutMs;
            byte[] ackPayload;
            try
            {
                ackPayload = FrameCodec.ReadFrame(stream);
            }
            catch (EndOfStreamException ex)
            {
                throw new IOException(
                    "Launcher UI bridge closed the connection before hello_ack. The discovery token may be invalid.",
                    ex);
            }
            catch (IOException ex) when (IsTimeout(ex))
            {
                throw new TimeoutException(
                    "Timed out after 5 seconds waiting for hello_ack from the launcher UI bridge. The discovery token may be invalid or the launcher is not ready.",
                    ex);
            }
            catch (IOException ex)
            {
                throw new IOException(
                    "Failed to read hello_ack from the launcher UI bridge: " + ex.Message,
                    ex);
            }

            if (ackPayload.Length == 0)
            {
                throw new IOException("Launcher UI bridge sent an empty hello_ack frame.");
            }

            ServerMessage ack;
            try
            {
                ack = ServerMessage.Parse(Utf8NoBom.GetString(ackPayload));
            }
            catch (FormatException ex)
            {
                throw new IOException("Launcher UI bridge sent an invalid hello_ack payload.", ex);
            }

            if (!string.Equals(ack.Type, "hello_ack", StringComparison.Ordinal))
            {
                throw new IOException(
                    "Expected hello_ack from the launcher UI bridge, received '" + ack.Type +
                    "'. The discovery token may be invalid.");
            }

            if (string.IsNullOrEmpty(ack.SessionId))
            {
                throw new IOException("Launcher UI bridge hello_ack did not include a sessionId.");
            }

            if (ack.Protocol != 0 && ack.Protocol != FrameCodec.Version)
            {
                throw new IOException(
                    "Launcher UI bridge protocol " + ack.Protocol +
                    " does not match this client (protocol " + FrameCodec.Version + ").");
            }

            stream.ReadTimeout = Timeout.Infinite;
            var client = new LauncherUiClient(tcp, stream, ack.SessionId);
            own = false;
            return client;
        }
        finally
        {
            if (own)
            {
                try
                {
                    stream?.Dispose();
                }
                catch (Exception)
                {
                }

                try
                {
                    tcp.Dispose();
                }
                catch (Exception)
                {
                }
            }
        }
    }

    /// <summary>
    /// Connect using <c>launcher-bridge.json</c> under the given BepInEx root directory.
    /// </summary>
    /// <param name="bepInExRoot">BepInEx root path (for example <c>Paths.BepInExRootPath</c> from a BepInEx plugin).</param>
    /// <param name="pluginId">Stable plugin id sent in the handshake.</param>
    /// <param name="pluginName">Human-readable plugin name.</param>
    /// <param name="pluginVersion">Plugin version string. Defaults to <c>1.0.0</c>.</param>
    public static LauncherUiClient ConnectFromBepInEx(
        string bepInExRoot,
        string pluginId,
        string pluginName,
        string pluginVersion = "1.0.0")
        => Connect(BridgeDiscoveryInfo.DefaultPath(bepInExRoot), pluginId, pluginName, pluginVersion);

    /// <summary>
    /// Connect using <c>launcher-bridge.json</c> under <see cref="Paths.BepInExRootPath"/>.
    /// Requires this assembly to be loaded as a BepInEx plugin.
    /// </summary>
    /// <param name="pluginId">Stable plugin id sent in the handshake.</param>
    /// <param name="pluginName">Human-readable plugin name.</param>
    /// <param name="pluginVersion">Plugin version string. Defaults to <c>1.0.0</c>.</param>
    public static LauncherUiClient Connect(
        string pluginId,
        string pluginName,
        string pluginVersion = "1.0.0")
        => ConnectFromBepInEx(Paths.BepInExRootPath, pluginId, pluginName, pluginVersion);

    /// <summary>
    /// Replaces the entire widget tree shown for this plugin.
    /// </summary>
    /// <param name="root">Root node of the new tree.</param>
    public void SetTree(UiNode root)
    {
        if (root is null)
        {
            throw new ArgumentNullException(nameof(root));
        }

        Send(BridgeJson.SetTree(root.ToJson()));
    }

    /// <summary>
    /// Updates properties on a single widget. Null property values are omitted, matching <see cref="UiNode.ToJson"/>.
    /// </summary>
    /// <param name="widgetId">Target widget id.</param>
    /// <param name="props">Properties to merge onto the widget.</param>
    public void Update(string widgetId, Dictionary<string, object?> props)
    {
        if (widgetId is null)
        {
            throw new ArgumentNullException(nameof(widgetId));
        }

        if (props is null)
        {
            throw new ArgumentNullException(nameof(props));
        }

        var sb = new StringBuilder();
        UiJson.WriteObject(sb, props);
        Send(BridgeJson.Update(widgetId, sb.ToString()));
    }

    /// <summary>
    /// Shows a short toast in the launcher UI.
    /// </summary>
    /// <param name="text">Toast message.</param>
    public void Toast(string text)
    {
        if (text is null)
        {
            throw new ArgumentNullException(nameof(text));
        }

        Send(BridgeJson.Toast(text));
    }

    /// <summary>
    /// Asks the launcher to close this UI session.
    /// </summary>
    /// <param name="reason">Optional reason string; encoded as JSON null when omitted.</param>
    public void Close(string? reason = null)
    {
        if (_disposed)
        {
            return;
        }

        try
        {
            Send(BridgeJson.Close(reason));
        }
        catch (ObjectDisposedException)
        {
        }
        catch (IOException)
        {
        }
    }

    /// <summary>
    /// Sends <c>close</c> if possible and closes the TCP socket.
    /// </summary>
    public void Dispose()
    {
        if (_disposed)
        {
            return;
        }

        try
        {
            Close();
        }
        catch (Exception)
        {
        }

        lock (_writeLock)
        {
            if (_disposed)
            {
                return;
            }

            _disposed = true;
            try
            {
                _stream.Dispose();
            }
            catch (Exception)
            {
            }

            try
            {
                _tcp.Dispose();
            }
            catch (Exception)
            {
            }
        }
    }

    private void Send(string json)
    {
        byte[] payload = Utf8NoBom.GetBytes(json);
        lock (_writeLock)
        {
            if (_disposed)
            {
                throw new ObjectDisposedException(nameof(LauncherUiClient));
            }

            FrameCodec.WriteFrame(_stream, payload);
        }
    }

    private void ReceiveLoop()
    {
        try
        {
            while (!_disposed)
            {
                byte[] frame;
                try
                {
                    frame = FrameCodec.ReadFrame(_stream);
                }
                catch (ObjectDisposedException)
                {
                    break;
                }
                catch (IOException)
                {
                    break;
                }

                if (frame.Length == 0)
                {
                    continue;
                }

                ServerMessage message;
                try
                {
                    message = ServerMessage.Parse(Utf8NoBom.GetString(frame));
                }
                catch (FormatException)
                {
                    continue;
                }

                if (string.Equals(message.Type, "event", StringComparison.Ordinal))
                {
                    Action<UiEvent>? handler = OnEvent;
                    if (handler is null)
                    {
                        continue;
                    }

                    var uiEvent = new UiEvent(
                        message.WidgetId ?? string.Empty,
                        message.Name ?? string.Empty,
                        message.Value);
                    try
                    {
                        handler(uiEvent);
                    }
                    catch (Exception)
                    {
                    }
                }
                else if (string.Equals(message.Type, "lifecycle", StringComparison.Ordinal))
                {
                    Action<string>? handler = OnLifecycle;
                    if (handler is null)
                    {
                        continue;
                    }

                    try
                    {
                        handler(message.State ?? string.Empty);
                    }
                    catch (Exception)
                    {
                    }
                }
            }
        }
        catch (Exception)
        {
        }
        finally
        {
            if (!_disposed)
            {
                Action? handler = OnDisconnected;
                if (handler != null)
                {
                    try
                    {
                        handler();
                    }
                    catch (Exception)
                    {
                    }
                }
            }
        }
    }

    private static bool IsTimeout(Exception ex)
    {
        for (Exception? e = ex; e != null; e = e.InnerException)
        {
            if (e is TimeoutException)
            {
                return true;
            }

            if (e is SocketException se &&
                (se.SocketErrorCode == SocketError.TimedOut || se.SocketErrorCode == SocketError.WouldBlock))
            {
                return true;
            }
        }

        return false;
    }
}
