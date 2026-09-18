package com.bepinex.android.market

import android.content.Context
import com.bepinex.android.BepInExLog
import com.bepinex.android.update.UpdateChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object MarketApi {

    private const val CACHE_DIR = "market"
    private const val CACHE_FILE = "mods.json"
    private const val CATEGORY_CACHE_FILE = "categories.json"
    private const val TAG_CACHE_FILE = "tags.json"

    @Volatile
    private var memoryMods: List<MarketMod>? = null
    @Volatile
    private var memoryCategories: List<MarketCategory>? = null
    @Volatile
    private var memoryTags: List<MarketTag>? = null

    private val _statsRevision = MutableStateFlow(0)
    val statsRevision: StateFlow<Int> = _statsRevision.asStateFlow()

    fun cachedMods(): List<MarketMod>? = memoryMods

    fun cachedCategories(): List<MarketCategory>? = memoryCategories

    fun cachedTags(): List<MarketTag>? = memoryTags

    fun loadTags(context: Context, forceRefresh: Boolean = false): List<MarketTag> {
        val appContext = context.applicationContext
        if (!forceRefresh) {
            memoryTags?.let { return it }
            readTagCache(appContext)?.let { cached ->
                memoryTags = cached
                return cached
            }
        }

        val base = apiBase(appContext)
        val body = base?.let { requestBody("$it/public/tag") }
        val parsed = body?.let(::parseTagPayload)
        if (parsed != null) {
            memoryTags = parsed
            writeNamedCache(appContext, TAG_CACHE_FILE, body)
            return parsed
        }

        memoryTags?.let { return it }
        return readTagCache(appContext)?.also { memoryTags = it }
            ?: tagsFromMods(memoryMods.orEmpty())
    }

    fun loadCategories(context: Context, forceRefresh: Boolean = false): List<MarketCategory> {
        val appContext = context.applicationContext
        if (!forceRefresh) {
            memoryCategories?.let { return it }
            readCategoryCache(appContext)?.let { cached ->
                memoryCategories = cached
                return cached
            }
        }

        val base = apiBase(appContext)
        val body = base?.let { requestBody("$it/public/category") }
        val parsed = body?.let(::parseCategoryPayload)
        if (parsed != null) {
            memoryCategories = parsed
            writeNamedCache(appContext, CATEGORY_CACHE_FILE, body)
            return parsed
        }

        memoryCategories?.let { return it }
        return readCategoryCache(appContext)?.also { memoryCategories = it }
            ?: categoriesFromMods(memoryMods.orEmpty())
    }

    fun findCachedMod(id: String): MarketMod? =
        memoryMods?.firstOrNull { it.id == id }

    fun loadMods(context: Context, forceRefresh: Boolean = false): List<MarketMod>? {
        val appContext = context.applicationContext
        if (!forceRefresh) {
            memoryMods?.let { return it }
            readCache(appContext)?.let { cached ->
                memoryMods = cached
                return cached
            }
        }

        val base = apiBase(appContext) ?: return memoryMods ?: readCache(appContext)?.also { memoryMods = it }
        val body = requestBody("$base/public/mod")
        if (body != null) {
            val parsed = parseModPayload(body)?.filter { it.isBepInExFramework }
            if (parsed != null) {
                memoryMods = parsed
                writeNamedCache(appContext, CACHE_FILE, body)
                return parsed
            }
        }

        memoryMods?.let { return it }
        return readCache(appContext)?.also { memoryMods = it }
    }

    fun loadModDetail(context: Context, modId: String, forceRefresh: Boolean = false): MarketMod? {
        if (!forceRefresh) {
            findCachedMod(modId)?.let { return it }
        }

        val base = apiBase(context.applicationContext) ?: return findCachedMod(modId)
        val remote = requestJson("$base/public/mod/$modId") { json ->
            when (val data = json.opt("data")) {
                is JSONObject -> parseMod(data)
                else -> null
            }
        }?.takeIf { it.isBepInExFramework }

        if (remote != null) {
            val current = memoryMods.orEmpty()
            memoryMods = current.filterNot { it.id == remote.id } + remote
            return remote
        }

        return findCachedMod(modId)
    }

    fun recordView(context: Context, modId: String): Boolean {
        val base = apiBase(context.applicationContext) ?: return false
        val ok = postStat("$base/public/mod/$modId/view")
        if (ok) bumpCachedCount(modId, view = true)
        return ok
    }

    fun recordDownload(context: Context, modId: String): Boolean {
        val base = apiBase(context.applicationContext) ?: return false
        val ok = postStat("$base/public/mod/$modId/download")
        if (ok) bumpCachedCount(modId, download = true)
        return ok
    }

    private fun apiBase(context: Context): String? {
        val host = UpdateChecker.resolveMarketBackend(context).trimEnd('/')
        if (host.isEmpty()) {
            BepInExLog.w("Market backend URL is missing from info.json urlMarket.backed")
            return null
        }
        return "$host/api"
    }

    private fun bumpCachedCount(modId: String, view: Boolean = false, download: Boolean = false) {
        val current = memoryMods ?: return
        memoryMods = current.map { mod ->
            if (mod.id != modId) mod
            else mod.copy(
                viewCount = if (view) incrementCount(mod.viewCount) else mod.viewCount,
                downloadCount = if (download) incrementCount(mod.downloadCount) else mod.downloadCount
            )
        }
        _statsRevision.value++
    }

    private fun incrementCount(raw: String): String =
        ((raw.toLongOrNull() ?: 0L) + 1L).toString()

    private fun postStat(url: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = openConnection(url, method = "POST")
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setFixedLengthStreamingMode(0)
            conn.outputStream.use { }
            val code = conn.responseCode
            if (code !in 200..299) {
                BepInExLog.w("Market stat failed: HTTP $code ($url)")
                false
            } else {
                true
            }
        } catch (error: Exception) {
            BepInExLog.w("Market stat request failed ($url): ${error.message}")
            false
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseModPayload(body: String): List<MarketMod>? {
        return try {
            val json = JSONObject(body)
            if (json.optInt("code", -1) != 0) {
                BepInExLog.w("Market API error: ${json.optString("msg")}")
                return null
            }
            when (val data = json.opt("data")) {
                is JSONArray -> parseModList(data)
                is JSONObject -> {
                    val records = data.optJSONArray("records") ?: return emptyList()
                    parseModList(records)
                }
                else -> emptyList()
            }
        } catch (error: Exception) {
            BepInExLog.e("Failed to parse market payload", error)
            null
        }
    }

    private fun parseModList(array: JSONArray): List<MarketMod> =
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let(::parseMod)
        }

    private fun parseMod(obj: JSONObject): MarketMod = MarketMod(
        id = obj.stringOrEmpty("id"),
        modName = obj.stringOrEmpty("modName"),
        englishName = obj.stringOrEmpty("englishName"),
        authorName = obj.stringOrEmpty("authorName"),
        otherAuthors = obj.stringOrEmpty("otherAuthors"),
        modDescription = obj.stringOrEmpty("modDescription"),
        iconUrl = obj.stringOrEmpty("iconUrl"),
        videoUrl = obj.stringOrEmpty("videoUrl"),
        gameName = obj.stringOrEmpty("gameName"),
        supportedVersions = obj.stringOrEmpty("supportedVersions")
            .ifBlank { obj.stringOrEmpty("gameVersion") },
        isPreposition = obj.optBoolean("isPreposition", false),
        isModpack = obj.optBoolean("isModpack", false),
        frameworkName = obj.stringOrEmpty("frameworkName"),
        showDirectUrl = obj.optBoolean("showDirectUrl", false),
        downloadDirectUrl = obj.stringOrEmpty("downloadDirectUrl"),
        downloadCloudUrl = obj.stringOrEmpty("downloadCloudUrl"),
        version = obj.stringOrEmpty("version"),
        fileSize = if (obj.isNull("fileSize")) 0L else obj.optLong("fileSize", 0L),
        downloadCount = obj.stringOrEmpty("downloadCount").ifBlank { "0" },
        viewCount = obj.stringOrEmpty("viewCount").ifBlank { "0" },
        isFeatured = obj.optBoolean("isFeatured", false),
        createdAt = obj.stringOrEmpty("createdAt"),
        updatedAt = obj.stringOrEmpty("updatedAt"),
        categoryId = obj.stringOrEmpty("categoryId"),
        categoryName = obj.stringOrEmpty("categoryName"),
        tags = parseTags(obj)
    )

    private fun parseTags(obj: JSONObject): List<MarketTag> {
        val array = obj.optJSONArray("tags") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val name = item.stringOrEmpty("name")
            if (name.isEmpty()) return@mapNotNull null
            MarketTag(
                id = item.stringOrEmpty("id"),
                name = name,
                color = item.stringOrEmpty("color")
            )
        }
    }

    private fun parseCategoryPayload(body: String): List<MarketCategory>? {
        return try {
            val json = JSONObject(body)
            if (json.optInt("code", -1) != 0) {
                BepInExLog.w("Market category API error: ${json.optString("msg")}")
                return null
            }
            val data = json.optJSONArray("data") ?: return emptyList()
            (0 until data.length()).mapNotNull { index ->
                val item = data.optJSONObject(index) ?: return@mapNotNull null
                val id = item.stringOrEmpty("id")
                val name = item.stringOrEmpty("name")
                if (id.isEmpty() || name.isEmpty()) return@mapNotNull null
                MarketCategory(
                    id = id,
                    name = name,
                    description = item.stringOrEmpty("description"),
                    sortOrder = item.optInt("sortOrder", 0)
                )
            }
        } catch (error: Exception) {
            BepInExLog.e("Failed to parse market categories", error)
            null
        }
    }

    private fun parseTagPayload(body: String): List<MarketTag>? {
        return try {
            val json = JSONObject(body)
            if (json.optInt("code", -1) != 0) {
                BepInExLog.w("Market tag API error: ${json.optString("msg")}")
                return null
            }
            val data = json.optJSONArray("data") ?: return emptyList()
            (0 until data.length()).mapNotNull { index ->
                val item = data.optJSONObject(index) ?: return@mapNotNull null
                val name = item.stringOrEmpty("name")
                if (name.isEmpty()) return@mapNotNull null
                MarketTag(
                    id = item.stringOrEmpty("id"),
                    name = name,
                    color = item.stringOrEmpty("color")
                )
            }
        } catch (error: Exception) {
            BepInExLog.e("Failed to parse market tags", error)
            null
        }
    }

    private fun tagsFromMods(mods: List<MarketMod>): List<MarketTag> =
        mods.flatMap { it.tags }
            .filter { it.name.isNotBlank() }
            .distinctBy { it.id.ifEmpty { it.name } }

    private fun readTagCache(context: Context): List<MarketTag>? {
        val file = File(File(context.filesDir, CACHE_DIR), TAG_CACHE_FILE)
        if (!file.isFile) return null
        val body = runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return parseTagPayload(body)
    }

    private fun categoriesFromMods(mods: List<MarketMod>): List<MarketCategory> =
        mods.mapNotNull { mod ->
            val id = mod.categoryId.trim()
            val name = mod.categoryName.trim()
            if (id.isEmpty() || name.isEmpty()) null else id to name
        }
            .distinctBy { it.first }
            .map { (id, name) -> MarketCategory(id = id, name = name) }

    private fun readCategoryCache(context: Context): List<MarketCategory>? {
        val file = File(File(context.filesDir, CACHE_DIR), CATEGORY_CACHE_FILE)
        if (!file.isFile) return null
        val body = runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return parseCategoryPayload(body)
    }

    private fun writeNamedCache(context: Context, fileName: String, body: String) {
        runCatching {
            val file = File(File(context.filesDir, CACHE_DIR), fileName)
            file.parentFile?.mkdirs()
            file.writeText(body)
        }.onFailure { error ->
            BepInExLog.w("Failed to write market cache $fileName: ${error.message}")
        }
    }

    private fun cacheFile(context: Context): File =
        File(File(context.filesDir, CACHE_DIR), CACHE_FILE)

    private fun readCache(context: Context): List<MarketMod>? {
        val file = cacheFile(context)
        if (!file.isFile) return null
        val body = runCatching { file.readText() }.getOrNull()?.takeIf { it.isNotBlank() } ?: return null
        return parseModPayload(body)?.filter { it.isBepInExFramework }
    }

    private fun requestBody(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = openConnection(url)
            if (conn.responseCode != 200) {
                BepInExLog.w("Market request failed: HTTP ${conn.responseCode} ($url)")
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optInt("code", -1) != 0) {
                BepInExLog.w("Market API error: ${json.optString("msg")} ($url)")
                return null
            }
            body
        } catch (error: Exception) {
            BepInExLog.e("Failed to fetch market data from $url", error)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun <T> requestJson(url: String, parse: (JSONObject) -> T): T? {
        var conn: HttpURLConnection? = null
        return try {
            conn = openConnection(url)
            if (conn.responseCode != 200) {
                BepInExLog.w("Market request failed: HTTP ${conn.responseCode} ($url)")
                return null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optInt("code", -1) != 0) {
                BepInExLog.w("Market API error: ${json.optString("msg")} ($url)")
                return null
            }
            parse(json)
        } catch (error: Exception) {
            BepInExLog.e("Failed to fetch market data from $url", error)
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun openConnection(url: String, method: String = "GET"): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("User-Agent", "PVZRH-Launcher/1.0")
            setRequestProperty("Accept", "application/json")
        }

    private fun JSONObject.stringOrEmpty(key: String): String {
        if (!has(key) || isNull(key)) return ""
        val value = optString(key, "")
        return if (value.equals("null", ignoreCase = true)) "" else value.trim()
    }
}
