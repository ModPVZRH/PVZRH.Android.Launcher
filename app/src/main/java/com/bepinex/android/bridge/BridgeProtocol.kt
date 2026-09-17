package com.bepinex.android.bridge

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Length-prefixed JSON protocol for the TCP declarative UI bridge.
 *
 * Frame: 4-byte big-endian unsigned length, then UTF-8 JSON body.
 */
object BridgeProtocol {
    const val VERSION = 1
    const val MAX_FRAME_BYTES = 256 * 1024

    /**
     * Reads one frame. Returns null on clean EOF before any length byte.
     */
    fun readFrame(input: InputStream): ByteArray? {
        val header = ByteArray(4)
        val headerRead = readExact(input, header)
        if (headerRead == 0) return null
        if (headerRead < header.size) {
            throw IOException("Unexpected EOF while reading frame length")
        }

        val length = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).int
        if (length < 0 || length > MAX_FRAME_BYTES) {
            throw IOException("Invalid frame length: $length")
        }

        val payload = ByteArray(length)
        if (length > 0) {
            val bodyRead = readExact(input, payload)
            if (bodyRead < length) {
                throw IOException("Unexpected EOF while reading frame body")
            }
        }
        return payload
    }

    /**
     * Writes one frame and flushes. Callers that share an output stream must lock.
     */
    fun writeFrame(output: OutputStream, payload: ByteArray) {
        if (payload.size > MAX_FRAME_BYTES) {
            throw IOException("Frame exceeds MAX_FRAME_BYTES: ${payload.size}")
        }
        val header = ByteBuffer.allocate(4)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(payload.size)
            .array()
        output.write(header)
        output.write(payload)
        output.flush()
    }

    /**
     * Parses an inbound JSON object. Missing or unknown [type] becomes [BridgeInbound.Unknown].
     */
    fun parseInbound(json: String): BridgeInbound {
        val raw = JSONObject(json)
        val type = if (raw.has("type") && !raw.isNull("type")) raw.optString("type") else ""
        return when (type) {
            "hello" -> BridgeInbound.Hello(
                pluginId = raw.optString("pluginId"),
                name = raw.optString("name"),
                version = raw.optString("version"),
                token = raw.optString("token")
            )
            "set_tree" -> BridgeInbound.SetTree(
                tree = raw.optJSONObject("tree") ?: JSONObject()
            )
            "update" -> BridgeInbound.Update(
                widgetId = raw.optString("widgetId"),
                props = raw.optJSONObject("props") ?: JSONObject()
            )
            "toast" -> BridgeInbound.Toast(
                text = raw.optString("text")
            )
            "close" -> BridgeInbound.Close(
                reason = if (raw.has("reason") && !raw.isNull("reason")) {
                    raw.optString("reason")
                } else {
                    null
                }
            )
            "set_icon" -> BridgeInbound.SetIcon(
                mime = raw.optString("mime", "image/png"),
                data = raw.optString("data")
            )
            "set_panel" -> BridgeInbound.SetPanel(raw = raw)
            "set_fab" -> BridgeInbound.SetFab(raw = raw)
            else -> BridgeInbound.Unknown(type, raw)
        }
    }

    /**
     * Encodes an outbound message as a JSON object string.
     */
    fun encodeOutbound(message: BridgeOutbound): String {
        val json = JSONObject()
        json.put("v", VERSION)
        when (message) {
            is BridgeOutbound.HelloAck -> {
                json.put("type", "hello_ack")
                json.put("sessionId", message.sessionId)
                json.put("protocol", message.protocol)
            }
            is BridgeOutbound.Event -> {
                json.put("type", "event")
                json.put("widgetId", message.widgetId)
                json.put("name", message.name)
                putEventValue(json, message.value)
            }
            is BridgeOutbound.Lifecycle -> {
                json.put("type", "lifecycle")
                json.put("state", message.state)
            }
        }
        return json.toString()
    }

    private fun putEventValue(json: JSONObject, value: Any?) {
        when (value) {
            null -> return
            is Boolean, is Number, is String, is JSONObject, is JSONArray -> json.put("value", value)
            else -> json.put("value", if (value === JSONObject.NULL) JSONObject.NULL else value.toString())
        }
    }

    private fun readExact(input: InputStream, buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val n = input.read(buffer, offset, buffer.size - offset)
            if (n < 0) return offset
            offset += n
        }
        return offset
    }
}

/** Plugin-to-launcher message. */
sealed class BridgeInbound {
    /** Handshake identifying the plugin. */
    data class Hello(
        val pluginId: String,
        val name: String,
        val version: String,
        val token: String
    ) : BridgeInbound()

    /** Full widget tree replacement. */
    data class SetTree(val tree: JSONObject) : BridgeInbound()

    /** Partial property update for one widget. */
    data class Update(
        val widgetId: String,
        val props: JSONObject
    ) : BridgeInbound()

    /** Toast request. */
    data class Toast(val text: String) : BridgeInbound()

    /** Session close. */
    data class Close(val reason: String?) : BridgeInbound()

    /** Launcher icon; [data] is base64 and may include a data-URI prefix. */
    data class SetIcon(val mime: String, val data: String) : BridgeInbound()

    /** Full panel style object for later parsing. */
    data class SetPanel(val raw: JSONObject) : BridgeInbound()

    /** Floating action button label and size. */
    data class SetFab(val raw: JSONObject) : BridgeInbound()

    /** Unrecognized or untyped payload. */
    data class Unknown(
        val type: String,
        val raw: JSONObject
    ) : BridgeInbound()
}

/** Launcher-to-plugin message. */
sealed class BridgeOutbound {
    /** Handshake acknowledgement. */
    data class HelloAck(
        val sessionId: String,
        val protocol: Int = BridgeProtocol.VERSION
    ) : BridgeOutbound()

    /** Widget event. */
    data class Event(
        val widgetId: String,
        val name: String,
        val value: Any?
    ) : BridgeOutbound()

    /** Session lifecycle notification. */
    data class Lifecycle(val state: String) : BridgeOutbound()
}
