package com.bepinex.android.market

import android.content.Context
import com.bepinex.android.BepInExLog
import com.bepinex.android.settings.AppSettings
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object MarketApi {

    private fun baseUrl(context: Context): String {
        val url = AppSettings.getMarketUrl(context)
        if (url.isEmpty()) return ""
        val trimmed = url.trimEnd('/')
        return if (trimmed.endsWith("/api")) trimmed else "$trimmed/api"
    }

    fun fetchMods(context: Context): List<MarketMod> {
        val base = baseUrl(context)
        if (base.isEmpty()) return emptyList()
        return try {
            val conn = URL("$base/public/mod").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "PVZRH-Launcher/1.0")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            if (conn.responseCode != 200) {
                BepInExLog.w("Market fetch failed: HTTP ${conn.responseCode}")
                return emptyList()
            }

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            if (json.optInt("code", -1) != 0) {
                BepInExLog.w("Market error: ${json.optString("msg")}")
                return emptyList()
            }

            val data = json.opt("data")
            when (data) {
                is JSONArray -> (0 until data.length()).mapNotNull { i ->
                    data.optJSONObject(i)?.let { parseMod(it) }
                }
                is JSONObject -> {
                    val records = data.optJSONArray("records") ?: return emptyList()
                    (0 until records.length()).mapNotNull { i ->
                        records.optJSONObject(i)?.let { parseMod(it) }
                    }
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            BepInExLog.e("Failed to fetch market", e)
            emptyList()
        }
    }

    fun fetchModDetail(context: Context, modId: String): MarketMod? {
        val base = baseUrl(context)
        if (base.isEmpty()) return null
        return try {
            val conn = URL("$base/public/mod/$modId").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "PVZRH-Launcher/1.0")
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            if (conn.responseCode != 200) {
                BepInExLog.w("Market detail fetch failed: HTTP ${conn.responseCode}")
                return null
            }

            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            if (json.optInt("code", -1) != 0) {
                BepInExLog.w("Market detail error: ${json.optString("msg")}")
                return null
            }

            val data = json.opt("data")
            when (data) {
                is JSONObject -> parseMod(data)
                else -> null
            }
        } catch (e: Exception) {
            BepInExLog.e("Failed to fetch market detail", e)
            null
        }
    }

    private fun parseMod(obj: JSONObject): MarketMod = MarketMod(
        id = obj.optString("id", ""),
        modName = obj.optString("modName", ""),
        englishName = obj.optString("englishName", ""),
        modDescription = obj.optString("modDescription", ""),
        gameName = obj.optString("gameName", ""),
        frameworkName = obj.optString("frameworkName", ""),
        downloadDirectUrl = obj.optString("downloadDirectUrl", ""),
        downloadCloudUrl = obj.optString("downloadCloudUrl", ""),
        version = obj.optString("version", ""),
        fileSize = obj.optLong("fileSize", 0),
        downloadCount = obj.optString("downloadCount", "0"),
        viewCount = obj.optString("viewCount", "0"),
        isFeatured = obj.optBoolean("isFeatured", false),
        isPreposition = obj.optBoolean("isPreposition", false),
        authorId = obj.optString("authorId", ""),
        videoUrl = obj.optString("videoUrl", ""),
        supportedVersions = obj.optString("supportedVersions", ""),
        showDirectUrl = obj.optBoolean("showDirectUrl", false)
    )
}
