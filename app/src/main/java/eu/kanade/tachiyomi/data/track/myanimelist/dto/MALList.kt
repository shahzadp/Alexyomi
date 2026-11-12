package eu.kanade.tachiyomi.data.track.myanimelist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MALListAnimeItem(
    @SerialName("num_episodes")
    val numEpisodes: Long,
    @SerialName("my_list_status")
    val myListStatus: MALListAnimeItemStatus?,
)

/** Minimal response for reading my_list_status of a single anime. */
@Serializable
data class MALAnimeListStatusResponse(
    @SerialName("my_list_status")
    val myListStatus: MALListAnimeItemStatus? = null,
)

/** Minimal response for reading my_list_status of a single manga. */
@Serializable
data class MALMangaListStatusResponse(
    @SerialName("my_list_status")
    val myListStatus: MALListMangaItemStatus? = null,
)

/**
 * The list status of an anime.
 *
 * Every field has a default, because MyAnimeList only returns the fields that were asked
 * for - a request for `my_list_status{status,score,...}` leaves out the dates, and a strict
 * parser would reject the whole response.
 */
@Serializable
data class MALListAnimeItemStatus(
    @SerialName("is_rewatching")
    val isRewatching: Boolean = false,
    val status: String = "",
    @SerialName("num_episodes_watched")
    val numEpisodesWatched: Double = 0.0,
    val score: Int = 0,
    @SerialName("start_date")
    val startDate: String? = null,
    @SerialName("finish_date")
    val finishDate: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
)

@Serializable
data class MALListMangaItem(
    @SerialName("num_chapters")
    val numChapters: Long,
    @SerialName("my_list_status")
    val myListStatus: MALListMangaItemStatus?,
)

/**
 * The list status of a manga. Same as the anime one - only the requested fields are returned.
 */
@Serializable
data class MALListMangaItemStatus(
    @SerialName("is_rereading")
    val isRereading: Boolean = false,
    val status: String = "",
    @SerialName("num_chapters_read")
    val numChaptersRead: Double = 0.0,
    val score: Int = 0,
    @SerialName("start_date")
    val startDate: String? = null,
    @SerialName("finish_date")
    val finishDate: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
)
