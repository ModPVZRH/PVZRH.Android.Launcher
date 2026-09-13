package com.bepinex.android.market

data class MarketMod(
    val id: String,
    val modName: String,
    val englishName: String,
    val modDescription: String,
    val gameName: String,
    val frameworkName: String,
    val downloadDirectUrl: String,
    val downloadCloudUrl: String,
    val version: String,
    val fileSize: Long,
    val downloadCount: String,
    val viewCount: String,
    val isFeatured: Boolean,
    val isPreposition: Boolean,
    val authorId: String,
    val videoUrl: String,
    val supportedVersions: String,
    val showDirectUrl: Boolean
)

data class MarketPageData(
    val records: List<MarketMod>,
    val total: Long,
    val current: Int,
    val size: Int
)

data class MarketPageRequest(
    val current: Int = 1,
    val size: Int = 20,
    val englishName: String = "",
    val gameName: String = "",
    val frameworkName: String = ""
)
