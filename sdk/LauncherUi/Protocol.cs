using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Text;

namespace PVZRH.LauncherUi;

/// <summary>
/// Length-prefixed UTF-8 JSON frames used by the launcher UI bridge.
/// </summary>
/// <remarks>
/// Each frame is a 4-byte big-endian payload length followed by UTF-8 JSON.
/// The payload must not exceed <see cref="MaxFrameBytes"/>. Protocol version
/// <see cref="Version"/> matches the Android launcher.
/// </remarks>
public static class FrameCodec
{
    /// <summary>JSON protocol version written as the <c>v</c> field.</summary>
    public const int Version = 1;

    /// <summary>Maximum payload size in bytes (256 KiB).</summary>
    public const int MaxFrameBytes = 256 * 1024;

    /// <summary>
    /// Reads one frame from <paramref name="stream"/>.
    /// </summary>
    /// <param name="stream">Readable stream positioned at a frame header.</param>
    /// <returns>The payload bytes (possibly empty).</returns>
    /// <exception cref="ArgumentNullException"><paramref name="stream"/> is null.</exception>
    /// <exception cref="EndOfStreamException">The stream ended before a full frame arrived.</exception>
    /// <exception cref="IOException">The declared length is negative or larger than <see cref="MaxFrameBytes"/>.</exception>
    public static byte[] ReadFrame(Stream stream)
    {
        if (stream is null)
        {
            throw new ArgumentNullException(nameof(stream));
        }

        byte[] header = new byte[4];
        int headerRead = ReadAtMost(stream, header);
        if (headerRead == 0)
        {
            throw new EndOfStreamException("End of stream before UI bridge frame.");
        }

        if (headerRead < header.Length)
        {
            throw new EndOfStreamException("Unexpected end of stream while reading frame length.");
        }

        int length = (header[0] << 24) | (header[1] << 16) | (header[2] << 8) | header[3];
        if (length < 0 || length > MaxFrameBytes)
        {
            throw new IOException("Invalid frame length: " + length);
        }

        if (length == 0)
        {
            return Array.Empty<byte>();
        }

        byte[] payload = new byte[length];
        int bodyRead = ReadAtMost(stream, payload);
        if (bodyRead < length)
        {
            throw new EndOfStreamException("Unexpected end of stream while reading frame body.");
        }

        return payload;
    }

    /// <summary>
    /// Writes one frame to <paramref name="stream"/> and flushes.
    /// Callers that share an output stream must serialize writes themselves.
    /// </summary>
    /// <param name="stream">Writable stream.</param>
    /// <param name="payload">UTF-8 JSON payload.</param>
    /// <exception cref="ArgumentNullException"><paramref name="stream"/> or <paramref name="payload"/> is null.</exception>
    /// <exception cref="IOException"><paramref name="payload"/> is larger than <see cref="MaxFrameBytes"/>.</exception>
    public static void WriteFrame(Stream stream, byte[] payload)
    {
        if (stream is null)
        {
            throw new ArgumentNullException(nameof(stream));
        }

        if (payload is null)
        {
            throw new ArgumentNullException(nameof(payload));
        }

        if (payload.Length > MaxFrameBytes)
        {
            throw new IOException("Frame exceeds MAX_FRAME_BYTES: " + payload.Length);
        }

        byte[] header = new byte[4];
        header[0] = (byte)(payload.Length >> 24);
        header[1] = (byte)(payload.Length >> 16);
        header[2] = (byte)(payload.Length >> 8);
        header[3] = (byte)payload.Length;
        stream.Write(header, 0, 4);
        if (payload.Length > 0)
        {
            stream.Write(payload, 0, payload.Length);
        }

        stream.Flush();
    }

    private static int ReadAtMost(Stream stream, byte[] buffer)
    {
        int offset = 0;
        while (offset < buffer.Length)
        {
            int n = stream.Read(buffer, offset, buffer.Length - offset);
            if (n <= 0)
            {
                return offset;
            }

            offset += n;
        }

        return offset;
    }
}

/// <summary>
/// Builds plugin-to-launcher JSON messages (no third-party JSON library).
/// </summary>
public static class BridgeJson
{
    /// <summary>
    /// Handshake identifying this plugin.
    /// </summary>
    /// <returns><c>{"v":1,"type":"hello",...}</c></returns>
    public static string Hello(string pluginId, string name, string version, string token)
    {
        if (pluginId is null)
        {
            throw new ArgumentNullException(nameof(pluginId));
        }

        if (name is null)
        {
            throw new ArgumentNullException(nameof(name));
        }

        if (version is null)
        {
            throw new ArgumentNullException(nameof(version));
        }

        if (token is null)
        {
            throw new ArgumentNullException(nameof(token));
        }

        return new JsonObject()
            .Add("v", FrameCodec.Version)
            .Add("type", "hello")
            .Add("pluginId", pluginId)
            .Add("name", name)
            .Add("version", version)
            .Add("token", token)
            .ToJson();
    }

    /// <summary>
    /// Full widget tree replacement. <paramref name="treeJsonObject"/> is inserted as a raw JSON object.
    /// </summary>
    /// <returns><c>{"v":1,"type":"set_tree","tree":{...}}</c></returns>
    public static string SetTree(string treeJsonObject)
    {
        if (treeJsonObject is null)
        {
            throw new ArgumentNullException(nameof(treeJsonObject));
        }

        return new JsonObject()
            .Add("v", FrameCodec.Version)
            .Add("type", "set_tree")
            .AddRaw("tree", treeJsonObject)
            .ToJson();
    }

    /// <summary>
    /// Partial property update for one widget. <paramref name="propsJsonObject"/> is inserted as a raw JSON object.
    /// </summary>
    /// <returns><c>{"v":1,"type":"update","widgetId":"...","props":{...}}</c></returns>
    public static string Update(string widgetId, string propsJsonObject)
    {
        if (widgetId is null)
        {
            throw new ArgumentNullException(nameof(widgetId));
        }

        if (propsJsonObject is null)
        {
            throw new ArgumentNullException(nameof(propsJsonObject));
        }

        return new JsonObject()
            .Add("v", FrameCodec.Version)
            .Add("type", "update")
            .Add("widgetId", widgetId)
            .AddRaw("props", propsJsonObject)
            .ToJson();
    }

    /// <summary>
    /// Toast request shown by the launcher.
    /// </summary>
    /// <returns><c>{"v":1,"type":"toast","text":"..."}</c></returns>
    public static string Toast(string text)
    {
        if (text is null)
        {
            throw new ArgumentNullException(nameof(text));
        }

        return new JsonObject()
            .Add("v", FrameCodec.Version)
            .Add("type", "toast")
            .Add("text", text)
            .ToJson();
    }

    /// <summary>
    /// Session close. A null <paramref name="reason"/> is encoded as JSON <c>null</c>.
    /// </summary>
    /// <returns><c>{"v":1,"type":"close","reason":"..."}</c></returns>
    public static string Close(string? reason)
    {
        return new JsonObject()
            .Add("v", FrameCodec.Version)
            .Add("type", "close")
            .Add("reason", reason)
            .ToJson();
    }
}

/// <summary>
/// Inbound message from the Android launcher (<c>hello_ack</c>, <c>event</c>, or <c>lifecycle</c>).
/// </summary>
public sealed class ServerMessage
{
    private ServerMessage(
        string type,
        string? sessionId,
        int protocol,
        string? widgetId,
        string? name,
        object? value,
        string? state)
    {
        Type = type;
        SessionId = sessionId;
        Protocol = protocol;
        WidgetId = widgetId;
        Name = name;
        Value = value;
        State = state;
    }

    /// <summary>Message discriminator (<c>hello_ack</c>, <c>event</c>, <c>lifecycle</c>, ...).</summary>
    public string Type { get; }

    /// <summary>Session id from <c>hello_ack</c>.</summary>
    public string? SessionId { get; }

    /// <summary>Negotiated protocol version from <c>hello_ack</c>; 0 if absent.</summary>
    public int Protocol { get; }

    /// <summary>Widget id from <c>event</c>.</summary>
    public string? WidgetId { get; }

    /// <summary>Event name (<c>click</c>, <c>change</c>, <c>submit</c>).</summary>
    public string? Name { get; }

    /// <summary>Event value: <see cref="bool"/>, <see cref="double"/>, <see cref="string"/>, or null.</summary>
    public object? Value { get; }

    /// <summary>Lifecycle state (<c>shown</c>, <c>hidden</c>, <c>destroyed</c>).</summary>
    public string? State { get; }

    /// <summary>
    /// Parses a launcher JSON object.
    /// </summary>
    /// <param name="json">UTF-16 JSON text (decode the frame payload as UTF-8 first).</param>
    /// <exception cref="ArgumentNullException"><paramref name="json"/> is null.</exception>
    /// <exception cref="FormatException">The text is not a JSON object.</exception>
    public static ServerMessage Parse(string json)
    {
        if (json is null)
        {
            throw new ArgumentNullException(nameof(json));
        }

        object? parsed = Json.Parse(json);
        if (parsed is not Dictionary<string, object?> root)
        {
            throw new FormatException("Expected a JSON object.");
        }

        string type = GetString(root, "type") ?? string.Empty;
        object? rawValue = null;
        bool hasValue = root.TryGetValue("value", out object? valueField);
        if (hasValue)
        {
            rawValue = NormalizeValue(valueField);
        }

        return new ServerMessage(
            type,
            GetString(root, "sessionId"),
            GetInt(root, "protocol"),
            GetString(root, "widgetId"),
            GetString(root, "name"),
            rawValue,
            GetString(root, "state"));
    }

    private static string? GetString(Dictionary<string, object?> obj, string key)
    {
        if (!obj.TryGetValue(key, out object? value) || value is null)
        {
            return null;
        }

        return value as string;
    }

    private static int GetInt(Dictionary<string, object?> obj, string key)
    {
        if (!obj.TryGetValue(key, out object? value) || value is not double d)
        {
            return 0;
        }

        if (d < int.MinValue || d > int.MaxValue)
        {
            return 0;
        }

        return (int)d;
    }

    private static object? NormalizeValue(object? value)
    {
        return value switch
        {
            bool or double or string => value,
            _ => null
        };
    }
}

/// <summary>
/// Tiny JSON object builder. Values are escaped; <see cref="AddRaw"/> inserts already-encoded JSON.
/// </summary>
internal sealed class JsonObject
{
    private readonly StringBuilder _sb = new StringBuilder();
    private bool _hasField;
    private bool _open = true;

    public JsonObject()
    {
        _sb.Append('{');
    }

    public JsonObject Add(string key, int value)
    {
        BeginField(key);
        _sb.Append(value.ToString(CultureInfo.InvariantCulture));
        return this;
    }

    public JsonObject Add(string key, string? value)
    {
        BeginField(key);
        if (value is null)
        {
            _sb.Append("null");
        }
        else
        {
            Json.AppendString(_sb, value);
        }

        return this;
    }

    public JsonObject AddRaw(string key, string json)
    {
        if (json is null)
        {
            throw new ArgumentNullException(nameof(json));
        }

        BeginField(key);
        _sb.Append(json);
        return this;
    }

    public string ToJson()
    {
        if (_open)
        {
            _sb.Append('}');
            _open = false;
        }

        return _sb.ToString();
    }

    private void BeginField(string key)
    {
        if (!_open)
        {
            throw new InvalidOperationException("JSON object is already closed.");
        }

        if (_hasField)
        {
            _sb.Append(',');
        }

        _hasField = true;
        Json.AppendString(_sb, key);
        _sb.Append(':');
    }
}

/// <summary>
/// Dependency-free JSON reader/writer helpers for objects, arrays, strings, numbers, true/false/null.
/// </summary>
internal static class Json
{
    private const int MaxDepth = 64;

    public static object? Parse(string text)
    {
        var parser = new Parser(text);
        return parser.ParseDocument();
    }

    public static void AppendString(StringBuilder sb, string value)
    {
        sb.Append('"');
        for (int i = 0; i < value.Length; i++)
        {
            char c = value[i];
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

    private sealed class Parser
    {
        private readonly string _json;
        private int _i;
        private int _depth;

        public Parser(string json)
        {
            _json = json;
        }

        public object? ParseDocument()
        {
            SkipBom();
            object? value = ParseValue();
            SkipWs();
            if (_i < _json.Length)
            {
                throw Error("Trailing characters after JSON value");
            }

            return value;
        }

        private object? ParseValue()
        {
            SkipWs();
            if (_i >= _json.Length)
            {
                throw Error("Unexpected end of JSON");
            }

            char c = _json[_i];
            switch (c)
            {
                case '{':
                    return ParseObject();
                case '[':
                    return ParseArray();
                case '"':
                    return ParseString();
                case 't':
                    ExpectLiteral("true");
                    return true;
                case 'f':
                    ExpectLiteral("false");
                    return false;
                case 'n':
                    ExpectLiteral("null");
                    return null;
                default:
                    if (c == '-' || (c >= '0' && c <= '9'))
                    {
                        return ParseNumber();
                    }

                    throw Error("Unexpected token");
            }
        }

        private Dictionary<string, object?> ParseObject()
        {
            Enter();
            Expect('{');
            SkipWs();
            var obj = new Dictionary<string, object?>(StringComparer.Ordinal);
            if (Peek() == '}')
            {
                _i++;
                Leave();
                return obj;
            }

            while (true)
            {
                SkipWs();
                if (Peek() != '"')
                {
                    throw Error("Expected string key");
                }

                string key = ParseString();
                SkipWs();
                Expect(':');
                obj[key] = ParseValue();
                SkipWs();
                char c = Peek();
                if (c == ',')
                {
                    _i++;
                    continue;
                }

                if (c == '}')
                {
                    _i++;
                    Leave();
                    return obj;
                }

                throw Error("Expected ',' or '}'");
            }
        }

        private List<object?> ParseArray()
        {
            Enter();
            Expect('[');
            SkipWs();
            var list = new List<object?>();
            if (Peek() == ']')
            {
                _i++;
                Leave();
                return list;
            }

            while (true)
            {
                list.Add(ParseValue());
                SkipWs();
                char c = Peek();
                if (c == ',')
                {
                    _i++;
                    continue;
                }

                if (c == ']')
                {
                    _i++;
                    Leave();
                    return list;
                }

                throw Error("Expected ',' or ']'");
            }
        }

        private string ParseString()
        {
            Expect('"');
            var sb = new StringBuilder();
            while (_i < _json.Length)
            {
                char c = _json[_i++];
                if (c == '"')
                {
                    return sb.ToString();
                }

                if (c == '\\')
                {
                    if (_i >= _json.Length)
                    {
                        throw Error("Unterminated string escape");
                    }

                    char e = _json[_i++];
                    switch (e)
                    {
                        case '"':
                        case '\\':
                        case '/':
                            sb.Append(e);
                            break;
                        case 'b':
                            sb.Append('\b');
                            break;
                        case 'f':
                            sb.Append('\f');
                            break;
                        case 'n':
                            sb.Append('\n');
                            break;
                        case 'r':
                            sb.Append('\r');
                            break;
                        case 't':
                            sb.Append('\t');
                            break;
                        case 'u':
                            sb.Append(ParseHex4());
                            break;
                        default:
                            throw Error("Invalid escape sequence");
                    }
                }
                else if (c < ' ')
                {
                    throw Error("Unescaped control character in string");
                }
                else
                {
                    sb.Append(c);
                }
            }

            throw Error("Unterminated string");
        }

        private char ParseHex4()
        {
            int value = 0;
            for (int n = 0; n < 4; n++)
            {
                if (_i >= _json.Length)
                {
                    throw Error("Unterminated unicode escape");
                }

                value = (value << 4) | Hex(_json[_i++]);
            }

            return (char)value;
        }

        private double ParseNumber()
        {
            int start = _i;
            if (Peek() == '-')
            {
                _i++;
            }

            if (Peek() == '0')
            {
                _i++;
            }
            else if (Peek() >= '1' && Peek() <= '9')
            {
                while (Peek() >= '0' && Peek() <= '9')
                {
                    _i++;
                }
            }
            else
            {
                throw Error("Invalid number");
            }

            if (Peek() == '.')
            {
                _i++;
                if (Peek() < '0' || Peek() > '9')
                {
                    throw Error("Invalid number");
                }

                while (Peek() >= '0' && Peek() <= '9')
                {
                    _i++;
                }
            }

            char exp = Peek();
            if (exp == 'e' || exp == 'E')
            {
                _i++;
                char sign = Peek();
                if (sign == '+' || sign == '-')
                {
                    _i++;
                }

                if (Peek() < '0' || Peek() > '9')
                {
                    throw Error("Invalid number");
                }

                while (Peek() >= '0' && Peek() <= '9')
                {
                    _i++;
                }
            }

            string slice = _json.Substring(start, _i - start);
            if (!double.TryParse(slice, NumberStyles.Float, CultureInfo.InvariantCulture, out double value))
            {
                throw Error("Invalid number");
            }

            return value;
        }

        private void ExpectLiteral(string literal)
        {
            for (int n = 0; n < literal.Length; n++)
            {
                if (_i >= _json.Length || _json[_i] != literal[n])
                {
                    throw Error("Unexpected token");
                }

                _i++;
            }
        }

        private void Expect(char c)
        {
            if (Peek() != c)
            {
                throw Error("Expected '" + c + "'");
            }

            _i++;
        }

        private char Peek()
        {
            return _i < _json.Length ? _json[_i] : '\0';
        }

        private void SkipWs()
        {
            while (_i < _json.Length)
            {
                char c = _json[_i];
                if (c != ' ' && c != '\t' && c != '\n' && c != '\r')
                {
                    return;
                }

                _i++;
            }
        }

        private void SkipBom()
        {
            if (_i < _json.Length && _json[_i] == '\uFEFF')
            {
                _i++;
            }
        }

        private void Enter()
        {
            _depth++;
            if (_depth > MaxDepth)
            {
                throw Error("JSON nesting too deep");
            }
        }

        private void Leave()
        {
            _depth--;
        }

        private FormatException Error(string message)
        {
            return new FormatException("Invalid JSON at index " + _i + ": " + message);
        }

        private int Hex(char c)
        {
            if (c >= '0' && c <= '9')
            {
                return c - '0';
            }

            if (c >= 'a' && c <= 'f')
            {
                return c - 'a' + 10;
            }

            if (c >= 'A' && c <= 'F')
            {
                return c - 'A' + 10;
            }

            throw Error("Invalid hex digit");
        }
    }
}
