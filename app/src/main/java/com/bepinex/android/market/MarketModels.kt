package com.bepinex.android.market

data class MarketCategory(
    val id: String,
    val name: String,
    val description: String = "",
    val sortOrder: Int = 0
)

data class MarketTag(
    val id: String,
    val name: String,
    val color: String = ""
)

data class MarketMod(
    val id: String,
    val modName: String,
    val englishName: String,
    val authorName: String,
    val otherAuthors: String,
    val modDescription: String,
    val iconUrl: String,
    val videoUrl: String,
    val gameName: String,
    val supportedVersions: String,
    val isPreposition: Boolean,
    val isModpack: Boolean,
    val frameworkName: String,
    val showDirectUrl: Boolean,
    val downloadDirectUrl: String,
    val downloadCloudUrl: String,
    val version: String,
    val fileSize: Long,
    val downloadCount: String,
    val viewCount: String,
    val isFeatured: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val categoryId: String = "",
    val categoryName: String = "",
    val tags: List<MarketTag> = emptyList()
) {
    val displayName: String
        get() = localizedName(preferChinese = true)

    fun localizedName(preferChinese: Boolean): String {
        return if (preferChinese) {
            modName.ifBlank { englishName }
        } else {
            englishName.ifBlank { modName }
        }
    }

    val displayAuthor: String
        get() = authorName.ifBlank { otherAuthors }

    val timestamp: String
        get() = updatedAt.ifBlank { createdAt }

    val isBepInExFramework: Boolean
        get() {
            val value = frameworkName.trim()
            if (value == "1") return true
            return value.contains("bepinex", ignoreCase = true)
        }
}
