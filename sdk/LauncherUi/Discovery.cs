#nullable enable

using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Text;

namespace PVZRH.LauncherUi;

/// <summary>
/// Connection details advertised by the Android launcher in <c>launcher-bridge.json</c>.
/// </summary>
public sealed class BridgeDiscoveryInfo
{
    /// <summary>JSON protocol version advertised by the launcher.</summary>
    public int Protocol { get; }

    /// <summary>TCP host to connect to. Typically <c>127.0.0.1</c>.</summary>
    public string Host { get; }

    /// <summary>TCP port the in-game bridge is listening on.</summary>
    public int Port { get; }

    /// <summary>Shared secret sent in the <c>hello</c> handshake.</summary>
    public string Token { get; }

    private BridgeDiscoveryInfo(int protocol, string host, int port, string token)
    {
        Protocol = protocol;
        Host = host;
        Port = port;
        Token = token;
    }

    /// <summary>
    /// Default discovery file under a BepInEx root directory.
    /// </summary>
    /// <param name="bepInExRoot">BepInEx root path (for example <c>Paths.BepInExRootPath</c>).</param>
    /// <returns><c>{bepInExRoot}/launcher-bridge.json</c></returns>
    public static string DefaultPath(string bepInExRoot)
    {
        if (bepInExRoot is null)
        {
            throw new ArgumentNullException(nameof(bepInExRoot));
        }

        return Path.Combine(bepInExRoot, "launcher-bridge.json");
    }

    /// <summary>
    /// Reads <c>protocol</c>, <c>host</c>, <c>port</c>, and <c>token</c> from a discovery file.
    /// Missing <c>host</c> defaults to <c>127.0.0.1</c>.
    /// </summary>
    /// <param name="discoveryFilePath">Path to <c>launcher-bridge.json</c>.</param>
    /// <exception cref="ArgumentNullException"><paramref name="discoveryFilePath"/> is null.</exception>
    /// <exception cref="IOException">The file is missing, unreadable, or not a valid discovery object.</exception>
    public static BridgeDiscoveryInfo Load(string discoveryFilePath)
    {
        if (discoveryFilePath is null)
        {
            throw new ArgumentNullException(nameof(discoveryFilePath));
        }

        if (!File.Exists(discoveryFilePath))
        {
            throw new IOException(
                "Launcher bridge discovery file not found: " + discoveryFilePath +
                ". Start the game from the PVZRH Android launcher so it can write launcher-bridge.json.");
        }

        string json;
        try
        {
            json = File.ReadAllText(discoveryFilePath, new UTF8Encoding(false));
        }
        catch (Exception ex) when (ex is IOException || ex is UnauthorizedAccessException)
        {
            throw new IOException(
                "Failed to read launcher bridge discovery file '" + discoveryFilePath + "': " + ex.Message,
                ex);
        }

        Dictionary<string, object?> obj;
        try
        {
            object? parsed = Json.Parse(json);
            if (parsed is not Dictionary<string, object?> root)
            {
                throw new FormatException("Expected a JSON object.");
            }

            obj = root;
        }
        catch (FormatException ex)
        {
            throw new IOException(
                "Launcher bridge discovery file '" + discoveryFilePath + "' is not valid JSON: " + ex.Message,
                ex);
        }

        int protocol = ReadInt(obj, "protocol", discoveryFilePath);
        int port = ReadInt(obj, "port", discoveryFilePath);
        if (port < 1 || port > 65535)
        {
            throw new IOException(
                "Launcher bridge discovery file '" + discoveryFilePath + "' has an invalid 'port': " + port + ".");
        }

        string host = "127.0.0.1";
        if (obj.TryGetValue("host", out object? hostValue) && hostValue is string hostText && hostText.Length > 0)
        {
            host = hostText;
        }

        if (!obj.TryGetValue("token", out object? tokenValue) || tokenValue is not string token || token.Length == 0)
        {
            throw new IOException(
                "Launcher bridge discovery file '" + discoveryFilePath + "' is missing a non-empty 'token'.");
        }

        return new BridgeDiscoveryInfo(protocol, host, port, token);
    }

    private static int ReadInt(Dictionary<string, object?> obj, string key, string path)
    {
        if (!obj.TryGetValue(key, out object? value) || value is null)
        {
            throw new IOException("Launcher bridge discovery file '" + path + "' is missing '" + key + "'.");
        }

        if (value is double d)
        {
            if (double.IsNaN(d) || double.IsInfinity(d) || d < int.MinValue || d > int.MaxValue)
            {
                throw new IOException("Launcher bridge discovery file '" + path + "' has an invalid '" + key + "'.");
            }

            return (int)d;
        }

        if (value is string text &&
            int.TryParse(text, NumberStyles.Integer, CultureInfo.InvariantCulture, out int parsed))
        {
            return parsed;
        }

        throw new IOException("Launcher bridge discovery file '" + path + "' has an invalid '" + key + "'.");
    }
}
