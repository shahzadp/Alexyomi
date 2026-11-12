package eu.kanade.tachiyomi.data.track.anilist.dto

import kotlinx.serialization.Serializable

@Serializable
data class ALMediaDetails(
    val id: Int = 0,
    val idMal: Int? = null,
    val title: ALTitle = ALTitle(),
    val coverImage: ALCoverImage = ALCoverImage(),
    val bannerImage: String? = null,
    val description: String? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val status: String? = null,
    val format: String? = null,
    val averageScore: Int? = null,
    val popularity: Int? = null,
    val genres: List<String> = emptyList(),
    val season: String? = null,
    val seasonYear: Int? = null,
    val studios: ALStudioConnection = ALStudioConnection(),
    val nextAiringEpisode: ALNextAiringEpisode? = null,
    val siteUrl: String? = null,
    val chapters: Int? = null,
    val volumes: Int? = null,
    val startDate: ALDate? = null,
)

@Serializable
data class ALDate(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null,
)

@Serializable
data class ALStudioConnection(
    val nodes: List<ALStudio> = emptyList(),
)

@Serializable
data class ALStudio(
    val name: String = "",
    val isAnimationStudio: Boolean = true,
)

@Serializable
data class ALNextAiringEpisode(
    val airingAt: Long = 0,
    val episode: Int = 0,
)
