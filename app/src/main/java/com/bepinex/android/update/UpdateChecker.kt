package com.bepinex.android.update

import android.content.Context
import android.net.Uri
import android.os.LocaleList
import com.bepinex.android.BepInExLog
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

object UpdateChecker {

    private const val INFO_URL_GITHUB =
        "https://raw.githubusercontent.com/Modded-PVZRH/PVZRH.Launcher-release/refs/heads/main/info.json"
    private const val INFO_URL_GH_PROXY =
        "https://v6.gh-proxy.org/https://github.com/Modded-PVZRH/PVZRH.Launcher-release/raw/refs/heads/main/info.json"

    data class UpdateInfo(
        val version: String,
        val allowStart: Boolean,
        val announcementDate: String,
        val announcementZh: String,
        val announcementEn: String,
        val urlApk: String,
        val urlLib: String
    )

    /** Parse [text](url) markdown links into pairs */
    fun parseLinks(text: String): List<Pair<String, String>> {
        val regex = Regex("""\[([^\]]+)\]\(([^)]+)\)""")
        return regex.findAll(text).map { it.groupValues[1] to it.groupValues[2] }.toList()
    }

    private fun isChinese(context: Context): Boolean {
        val lang = context.resources.configuration.locales[0]?.language
        return lang == "zh"
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
            val urlsLib = json.optJSONObject("urlLib") ?: JSONObject()

            val apkPrefix = if (isZh) "gh-proxy" else "github"
            val libPrefix = if (isZh) "gh-proxy" else "github"

            val urlApk = urlsApk.optString(apkPrefix, urlsApk.optString("github", ""))
            val urlLib = urlsLib.optString(libPrefix, urlsLib.optString("github", ""))

            BepInExLog.i("info.json fetched: version=$version, allowStart=$allowStart")

            UpdateInfo(
                version = version,
                allowStart = allowStart,
                announcementDate = announcementDate,
                announcementZh = announcementZh,
                announcementEn = announcementEn,
                urlApk = urlApk,
                urlLib = urlLib
            )
        } catch (e: Exception) {
            BepInExLog.e("Failed to fetch info.json", e)
            null
        }
    }

    /**
     * Compare current version with remote version.
     * Strips -ci.XXX suffix before comparing.
     * Returns true if update is available.
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
