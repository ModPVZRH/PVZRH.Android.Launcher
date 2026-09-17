package com.bepinex.android.bridge

import com.bepinex.android.BepInExPaths
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom

/** Advertised TCP endpoint for the in-game declarative UI bridge. */
data class BridgeEndpoint(
    val protocol: Int,
    val host: String,
    val port: Int,
    val token: String
)

/** Publishes [BridgeEndpoint] as launcher-bridge.json for C# plugins. */
object BridgeDiscovery {

    const val FILE_NAME = "launcher-bridge.json"
    const val HOST_LOOPBACK = "127.0.0.1"

    private val random = SecureRandom()

    /** Discovery file under the game's BepInEx directory. */
    fun file(packageName: String): File =
        BepInExPaths.getBridgeDiscoveryFile(packageName)

    /** Writes [endpoint] as JSON, creating parent directories as needed. */
    fun write(packageName: String, endpoint: BridgeEndpoint) {
        val dest = file(packageName)
        dest.parentFile?.mkdirs()
        val json = JSONObject()
            .put("protocol", endpoint.protocol)
            .put("host", endpoint.host)
            .put("port", endpoint.port)
            .put("token", endpoint.token)
            .toString()
        val temp = File(dest.parentFile, "${dest.name}.tmp")
        temp.writeText(json)
        if (dest.exists()) dest.delete()
        if (!temp.renameTo(dest)) {
            dest.writeText(json)
            temp.delete()
        }
    }

    /** Deletes the discovery file if present. Errors are ignored. */
    fun clear(packageName: String) {
        try {
            file(packageName).delete()
        } catch (_: Exception) {
        }
    }

    /** 32-character lowercase hex token from [SecureRandom]. */
    fun generateToken(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}
