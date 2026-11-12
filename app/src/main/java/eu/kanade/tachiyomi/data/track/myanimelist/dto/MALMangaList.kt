package eu.kanade.tachiyomi.data.track.myanimelist.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MALMangaListResponse(
    val data: List<MALMangaListItem> = emptyList(),
    val paging: MALAnimeListPaging = MALAnimeListPaging(),
)

@Serializable
data class MALMangaListItem(
    val node: MALMangaListNode,
    @SerialName("list_status")
    val listStatus: MALListMangaItemStatus,
)

@Serializable
data class MALMangaListNode(
    val id: Long = 0,
    val title: String = "",
    val synopsis: String? = null,
    @SerialName("num_chapters")
    val numChapters: Long = 0,
    val mean: Double = -1.0,
    @SerialName("main_picture")
    val covers: MALAnimeCovers? = null,
    val status: String = "",
    @SerialName("media_type")
    val mediaType: String = "",
    @SerialName("start_date")
    val startDate: String? = null,
)
