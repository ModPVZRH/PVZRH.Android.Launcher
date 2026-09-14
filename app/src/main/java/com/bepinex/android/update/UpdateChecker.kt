package com.bepinex.android.update

import android.content.Context
import android.net.Uri
import android.os.LocaleList
import com.bepinex.android.BepInExLog
import com.bepinex.android.settings.AppSettings
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object UpdateChecker {

    private const val INFO_URL_GITHUB =
        "https://raw.githubusercontent.com/ModPVZRH/PVZRH.Launcher-release/refs/heads/main/info.json"
    private const val INFO_URL_GH_PROXY =
        "https://v6.gh-proxy.org/https://github.com/ModPVZRH/PVZRH.Launcher-release/raw/refs/heads/main/info.json"

    private const val PREFS_NAME = "update_checker"
    private const val KEY_URL_LIB = "urlLib"
    private const val KEY_URL_LIB_SYM = "urlLibSym"
    private const val KEY_URL_MARKET = "urlMarket"

    data class LibSource(
        val offlineMode: Boolean,
        val github: String = "",
        val ghProxy: String = "",
        val cos: String = ""
    ) {
        fun downloadUrls(preferProxy: Boolean): List<String> {
            val ordered = if (preferProxy) {
                listOf(ghProxy, github, cos)
            } else {
                listOf(github, ghProxy, cos)
            }
            return ordered.map { cleanUrl(it) }.filter { it.isNotEmpty() }.distinct()
        }

        fun toJson(): JSONObject = JSONObject().apply {
            put("offline-mode", offlineMode)
            put("github", github)
            put("gh-proxy", ghProxy)
            put("cos", cos)
        }

        companion object {
            val OFFLINE = LibSource(offlineMode = true)

            fun fromJson(obj: JSONObject): LibSource = LibSource(
                offlineMode = boolValue(obj, "offline-mode", default = true),
                github = cleanUrl(obj.optString("github")),
                ghProxy = cleanUrl(obj.optString("gh-proxy")),
                cos = cleanUrl(obj.optString("cos"))
            )

            fun cleanUrl(value: String): String {
                val trimmed = value.trim()
                return if (trimmed.isEmpty() || trimmed.equals("null", ignoreCase = true)) "" else trimmed
            }

            private fun boolValue(obj: JSONObject, key: String, default: Boolean): Boolean {
                if (!obj.has(key) || obj.isNull(key)) return default
                return when (val value = obj.opt(key)) {
                    is Boolean -> value
                    is Number -> value.toInt() != 0
                    is String -> value.equals("true", ignoreCase = true)
                    else -> default
                }
            }
        }
    }

    data class UpdateInfo(
        val version: String,
        val allowStart: Boolean,
        val announcementDate: String,
        val announcementZh: String,
        val announcementEn: String,
        val urlApk: String,
        val urlLib: LibSource,
        val urlLibSym: LibSource,
        val urlMarket: String
    )

    /** Parse [text](url) markdown links into pairs */
    fun parseLinks(text: String): List<Pair<String, String>> {
        val regex = Regex("""\[([^\]]+)\]\(([^)]+)\)""")
        return regex.findAll(text).map { it.groupValues[1] to it.groupValues[2] }.toList()
    }

    private fun isChinese(context: Context): Boolean {
        return when (AppSettings.getLanguage(context)) {
            AppSettings.Language.CHINESE, AppSettings.Language.CHINESE_TW -> true
            AppSettings.Language.SYSTEM ->
                context.resources.configuration.locales[0]?.language?.equals("zh", ignoreCase = true) == true
            else -> false
        }
    }

    private fun infoJsonUrl(context: Context): String =
        if (isChinese(context)) INFO_URL_GH_PROXY else INFO_URL_GITHUB

    fun fetchInfo(context: Context): UpdateInfo? {
        return try {
            val url = URL(infoJsonUrl(context))
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "PVZRH-Launcher/1.0")

            if (conn.responseCode != 200) {
                BepInExLog.w("info.json fetch failed: HTTP ${conn.responseCode}")
                return null
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)

            val version = json.optString("version", "")
            val allowStart = json.optString("allowStart", "true") == "true"

            val announcement = json.optJSONObject("announcement") ?: JSONObject()
            val announcementDate = announcement.optString("Date", "")
            val announcementZh = announcement.optString("SChinese", "")
            val announcementEn = announcement.optString("English", "")

            val isZh = isChinese(context)
            val urlsApk = json.optJSONObject("urlApk") ?: JSONObject()
            val apkPrefix = if (isZh) "gh-proxy" else "github"
            val urlApk = urlsApk.optString(apkPrefix, urlsApk.optString("github", ""))
            val urlLib = parseLibSource(json, "urlLib")
            val urlLibSym = parseLibSource(json, "urlLibSym")
            val urlMarket = parseMarketBackend(json)

            BepInExLog.i(
                "info.json fetched: version=$version, allowStart=$allowStart, " +
                    "urlLib.offline=${urlLib.offlineMode}, urlLibSym.offline=${urlLibSym.offlineMode}, " +
                    "urlMarket=$urlMarket"
            )
            cacheLibSources(context, urlLib, urlLibSym)
            cacheMarketBackend(context, urlMarket)

            UpdateInfo(
                version = version,
                allowStart = allowStart,
                announcementDate = announcementDate,
                announcementZh = announcementZh,
                announcementEn = announcementEn,
                urlApk = urlApk,
                urlLib = urlLib,
                urlLibSym = urlLibSym,
                urlMarket = urlMarket
            )
        } catch (e: Exception) {
            BepInExLog.e("Failed to fetch info.json", e)
            null
        }
    }

    fun preferProxyMirrors(context: Context): Boolean = isChinese(context)

    /**
     * Resolve the market API host from info.json `urlMarket.backed`.
     * Uses the last cached value when the network fetch fails.
     */
    fun resolveMarketBackend(context: Context): String {
        loadCachedMarketBackend(context)?.let { if (it.isNotEmpty()) return it }
        fetchInfo(context)
        return loadCachedMarketBackend(context).orEmpty()
    }

    /**
     * Resolve libunity sources from info.json.
     * Network failure uses the last cached result, then defaults to offline extract.
     */
    fun resolveLibSources(context: Context): Pair<LibSource, LibSource> {
        val info = fetchInfo(context)
        if (info != null) return info.urlLib to info.urlLibSym
        loadCachedLibSources(context)?.let {
            BepInExLog.w("Using cached libunity sources after info.json fetch failure")
            return it
        }
        BepInExLog.w("libunity source unavailable; defaulting to offline extract")
        return LibSource.OFFLINE to LibSource.OFFLINE
    }

    private fun parseMarketBackend(json: JSONObject): String {
        val value = json.opt("urlMarket") ?: return ""
        if (value is JSONObject) return LibSource.cleanUrl(value.optString("backed"))
        return LibSource.cleanUrl(value.toString())
    }

    private fun cacheMarketBackend(context: Context, url: String) {
        if (url.isEmpty()) return
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL_MARKET, url)
            .commit()
    }

    private fun loadCachedMarketBackend(context: Context): String? {
        val url = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_URL_MARKET, null)
            ?.let(LibSource::cleanUrl)
        return url?.takeIf { it.isNotEmpty() }
    }

    private fun parseLibSource(json: JSONObject, key: String): LibSource {
        val value = json.opt(key) ?: return LibSource.OFFLINE
        if (value is JSONObject) return LibSource.fromJson(value)
        val url = LibSource.cleanUrl(value.toString())
        return if (url.isEmpty()) LibSource.OFFLINE
        else LibSource(offlineMode = false, github = url)
    }

    private fun cacheLibSources(context: Context, lib: LibSource, sym: LibSource) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_URL_LIB, lib.toJson().toString())
            .putString(KEY_URL_LIB_SYM, sym.toJson().toString())
            .commit()
    }

    private fun loadCachedLibSources(context: Context): Pair<LibSource, LibSource>? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val libRaw = prefs.getString(KEY_URL_LIB, null) ?: return null
        val symRaw = prefs.getString(KEY_URL_LIB_SYM, null) ?: return null
        return try {
            LibSource.fromJson(JSONObject(libRaw)) to LibSource.fromJson(JSONObject(symRaw))
        } catch (e: Exception) {
            BepInExLog.w("Failed to read cached libunity sources: ${e.message}")
            null
        }
    }

    /**
     * Compare current version with remote version, ignoring -ci.XXX suffix.
     */
    fun hasUpdate(currentVersion: String, remoteVersion: String): Boolean {
        val currentBase = currentVersion.replace(Regex("-ci\\.\\d+$"), "")
        return currentBase != remoteVersion
    }

    fun shouldBlockStart(remoteVersion: String, currentVersion: String): Boolean {
        val currentBase = currentVersion.replace(Regex("-ci\\.\\d+$"), "")
        return currentBase > remoteVersion
    }
}
