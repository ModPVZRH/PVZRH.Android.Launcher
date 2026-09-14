package com.bepinex.android.market

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
    val updatedAt: String
) {
    val displayName: String
        get() = modName.ifBlank { englishName }

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
