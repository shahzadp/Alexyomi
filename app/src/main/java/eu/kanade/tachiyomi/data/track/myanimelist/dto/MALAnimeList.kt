package eu.kanade.tachiyomi.data.track.myanimelist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MALAnimeListResponse(
    val data: List<MALAnimeListItem> = emptyList(),
    val paging: MALAnimeListPaging = MALAnimeListPaging(),
)

@Serializable
data class MALAnimeListPaging(
    val next: String? = null,
)

@Serializable
data class MALAnimeListItem(
    val node: MALAnimeListNode,
    @SerialName("list_status")
    val listStatus: MALListAnimeItemStatus,
)

@Serializable
data class MALAnimeListNode(
    val id: Long = 0,
    val title: String = "",
    val synopsis: String? = null,
    @SerialName("num_episodes")
    val numEpisodes: Long = 0,
    val mean: Double = -1.0,
    @SerialName("main_picture")
    val covers: MALAnimeCovers? = null,
    val status: String = "",
    @SerialName("media_type")
    val mediaType: String = "",
    @SerialName("start_date")
    val startDate: String? = null,
)
