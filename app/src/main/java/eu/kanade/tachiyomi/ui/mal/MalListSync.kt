package eu.kanade.tachiyomi.ui.mal

import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistBrowseApi
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALAnimeListItem
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALMangaListItem
import eu.kanade.tachiyomi.network.NetworkHelper
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Fetches the list of the user from MyAnimeList and fills the gaps from AniList.
 *
 * Shared by the list screen and the background sync, so both work with the same data.
 */
object MalListSync {

    private const val MAX_ENTRIES = 10000
    private const val PAGE_SIZE = 250

    private val tracker = Injekt.get<TrackerManager>().myAnimeList
    private val anilistApi = AnilistBrowseApi(Injekt.get<NetworkHelper>().client)

    /**
     * The whole list of the user. Filtering happens client-side, this keeps the disk cache
     * complete for offline viewing.
     */
    suspend fun fetch(
        mediaType: MalListScreenModel.MediaType,
    ): List<MalListScreenModel.MalListEntry> {
        val entries = mutableListOf<MalListScreenModel.MalListEntry>()
        var offset = 0
        while (entries.size < MAX_ENTRIES) {
            val entriesThisPage = if (mediaType == MalListScreenModel.MediaType.ANIME) {
                val page = tracker.animeListPage(status = null, offset = offset)
                Pair(page.data.map { it.toEntry() }, page.paging.next.isNullOrBlank())
            } else {
                val page = tracker.mangaListPage(status = null, offset = offset)
                Pair(page.data.map { it.toEntry() }, page.paging.next.isNullOrBlank())
            }
            entries += entriesThisPage.first
            if (entriesThisPage.second || entriesThisPage.first.isEmpty()) break
            offset += PAGE_SIZE
        }
        return entries
    }

    /**
     * Fills the missing totals/airing status from AniList, only for the entries that lack them.
     */
    suspend fun enrich(
        working: MutableList<MalListScreenModel.MalListEntry>,
        mediaType: MalListScreenModel.MediaType,
    ) {
        val missing = working.filter { it.total == null || it.airingStatus.isBlank() }
        if (missing.isEmpty()) return
        val info = try {
            if (mediaType == MalListScreenModel.MediaType.ANIME) {
                anilistApi.listStatusByMalId(missing.map { it.id.toInt() })
            } else {
                anilistApi.mangaChaptersByMalId(missing.map { it.id.toInt() })
            }
        } catch (e: Exception) {
            emptyMap()
        }
        if (info.isEmpty()) return

        for (i in working.indices) {
            val entry = working[i]
            val enrich = info[entry.id.toInt()] ?: continue
            working[i] = entry.copy(
                total = entry.total ?: enrich.episodes,
                airingStatus = entry.airingStatus.takeIf { it.isNotBlank() } ?: enrich.airingStatus,
                mediaType = entry.mediaType?.takeIf { it.isNotBlank() } ?: enrich.format,
                seasonLabel = entry.seasonLabel
                    ?: enrich.season?.let { s -> enrich.seasonYear?.let { y -> "$s $y" } ?: s },
            )
        }
    }

    private fun seasonOf(date: String?): String? {
        if (date.isNullOrBlank()) return null
        val year = date.take(4)
        if (year.toIntOrNull() == null) return null
        val month = date.substringAfter('-', missingDelimiterValue = "")
            .substringBefore('-')
            .toIntOrNull()
        return if (month != null) {
            val season = when (month) {
                in 1..3 -> "Winter"
                in 4..6 -> "Spring"
                in 7..9 -> "Summer"
                else -> "Fall"
            }
            "$season $year"
        } else {
            year
        }
    }

    private fun MALMangaListItem.toEntry() = MalListScreenModel.MalListEntry(
        id = node.id,
        title = node.title,
        coverUrl = node.covers?.medium?.takeIf { it.isNotBlank() } ?: node.covers?.large.orEmpty(),
        progress = listStatus.numChaptersRead.toInt(),
        total = node.numChapters.toInt().takeIf { it > 0 },
        status = listStatus.status,
        airingStatus = node.status,
        isReplaying = listStatus.isRereading,
        score = listStatus.score,
        mediaType = node.mediaType.takeIf { it.isNotBlank() },
        seasonLabel = seasonOf(node.startDate),
        updatedAt = listStatus.updatedAt,
    )

    private fun MALAnimeListItem.toEntry() = MalListScreenModel.MalListEntry(
        id = node.id,
        title = node.title,
        coverUrl = node.covers?.medium?.takeIf { it.isNotBlank() } ?: node.covers?.large.orEmpty(),
        progress = listStatus.numEpisodesWatched.toInt(),
        total = node.numEpisodes.toInt().takeIf { it > 0 },
        status = listStatus.status,
        airingStatus = node.status,
        isReplaying = listStatus.isRewatching,
        score = listStatus.score,
        mediaType = node.mediaType.takeIf { it.isNotBlank() },
        seasonLabel = seasonOf(node.startDate),
        updatedAt = listStatus.updatedAt,
    )
}
