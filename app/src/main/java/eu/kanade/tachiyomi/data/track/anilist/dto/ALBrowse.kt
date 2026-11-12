package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.Serializable

@Serializable
data class ALBrowseResponse(
    val data: ALBrowseData? = null,
    val errors: List<ALGraphQlError> = emptyList(),
)

@Serializable
data class ALGraphQlError(
    val message: String = "",
)

@Serializable
data class ALMediaDetailsResponse(
    val data: ALMediaDetailsData? = null,
    val errors: List<ALGraphQlError> = emptyList(),
)

@Serializable
data class ALMediaDetailsData(
    val Media: ALMediaDetails? = null,
)

@Serializable
data class ALBrowseData(
    val Page: ALBrowsePage? = null,
)

@Serializable
data class ALBrowsePage(
    val pageInfo: ALPageInfo? = null,
    val media: List<ALMedia> = emptyList(),
)

@Serializable
data class ALPageInfo(
    val hasNextPage: Boolean = false,
)

@Serializable
data class ALMedia(
    val id: Int = 0,
    val idMal: Int? = null,
    val title: ALTitle = ALTitle(),
    val coverImage: ALCoverImage = ALCoverImage(),
    val averageScore: Int? = null,
    val episodes: Int? = null,
    val chapters: Int? = null,
    val format: String? = null,
    val status: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
)

@Serializable
data class ALTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
)

@Serializable
data class ALCoverImage(
    val large: String? = null,
    val medium: String? = null,
)
