package eu.kanade.tachiyomi.source.anime

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimeRelation
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SAnimeEpisodeUpdate
import eu.kanade.tachiyomi.animesource.model.SAnimeSeasonUpdate
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers

/**
 * Source for user-selected movie streams.
 *
 * The user browses and finds a direct video stream in the built-in browser; a tap on
 * "Play in Alexyomi" creates a stub movie entry pointing at that stream and launches the
 * normal player with all the checks in place. The player then resolves the stream through
 * this source.
 */
class MoviesSource : AnimeHttpSource() {

    override val id: Long get() = ID

    override val name = "Movies"

    override val lang = "en"

    override val versionId = 1

    override val supportsLatest = false

    override val baseUrl = ""

    override suspend fun getPopularAnime(page: Int): AnimesPage = AnimesPage(emptyList(), hasNextPage = false)

    override suspend fun getLatestUpdates(page: Int): AnimesPage = AnimesPage(emptyList(), hasNextPage = false)

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage =
        AnimesPage(emptyList(), hasNextPage = false)

    override suspend fun getAnimeEpisodeUpdate(
        anime: SAnime,
        episodes: List<SEpisode>,
        fetchDetails: Boolean,
        fetchEpisodes: Boolean,
    ): SAnimeEpisodeUpdate = SAnimeEpisodeUpdate(anime, episodes)

    override suspend fun getAnimeSeasonUpdate(
        anime: SAnime,
        seasons: List<SAnime>,
        fetchDetails: Boolean,
        fetchSeasons: Boolean,
    ): SAnimeSeasonUpdate = SAnimeSeasonUpdate(anime, seasons)

    override suspend fun getRelatedAnimeList(anime: SAnime): List<AnimeRelation> = emptyList()

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        val memo = episode.memo
        val userAgent = memo["userAgent"]?.jsonPrimitive?.contentOrNull
        val referer = memo["referer"]?.jsonPrimitive?.contentOrNull
        val cookie = memo["cookie"]?.jsonPrimitive?.contentOrNull

        val headers = Headers.Builder()
            .apply {
                if (!userAgent.isNullOrBlank()) add("User-Agent", userAgent)
                if (!referer.isNullOrBlank()) add("Referer", referer)
                if (!cookie.isNullOrBlank()) add("Cookie", cookie)
            }
            .build()

        return listOf(
            Video(
                videoUrl = episode.url,
                videoTitle = episode.name,
                resolution = null,
                headers = headers,
                preferred = true,
            ),
        )
    }

    companion object {
        const val ID = 424242L
    }
}
