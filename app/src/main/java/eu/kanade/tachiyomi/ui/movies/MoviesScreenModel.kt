package eu.kanade.tachiyomi.ui.movies

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.AnimeUpdateStrategy
import eu.kanade.tachiyomi.source.anime.MoviesSource
import eu.kanade.tachiyomi.ui.main.MainActivity
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.repository.EpisodeRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import android.content.Context
import logcat.LogPriority

/**
 * Handles playback of arbitrary movie stream URLs through the in-app player.
 *
 * Movies are stored as episodes of one synthetic movie entry, so the player, the trackers and
 * the download logic don't have to special-case them.
 */
class MoviesScreenModel : ScreenModel {

    private val animeRepository: AnimeRepository = Injekt.get()
    private val episodeRepository: EpisodeRepository = Injekt.get()

    /**
     * Inserts (or reuses) the movie anime + episode and launches the in-app player.
     */
    fun playMovie(context: Context, url: String, title: String) {
        if (url.isBlank()) return
        screenModelScope.launchIO {
            try {
                val anime = getOrCreateAnime(title)
                val episode = getOrCreateEpisode(anime.id, url, title)
                MainActivity.startPlayerActivity(
                    context,
                    anime.id,
                    episode.id,
                    false,
                    sourceId = MoviesSource.ID,
                    video = null,
                    hosterIndex = -1,
                    videoIndex = -1,
                    hosterList = null,
                )
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, e) { "Failed to play movie $url" }
            }
        }
    }

    private suspend fun getOrCreateAnime(title: String): Anime {
        val existing = animeRepository.getAnimeByUrlAndSourceId(
            url = "movie",
            sourceId = MoviesSource.ID,
        )
        if (existing != null) return existing

        val anime = Anime.create().copy(
            url = "movie",
            source = MoviesSource.ID,
            title = title,
            fetchType = FetchType.Episodes,
            status = 1,
            updateStrategy = AnimeUpdateStrategy.ALWAYS_UPDATE,
            initialized = true,
        )
        animeRepository.insertAnime(anime)
        return animeRepository.getAnimeByUrlAndSourceId(
            url = "movie",
            sourceId = MoviesSource.ID,
        )!!
    }

    private suspend fun getOrCreateEpisode(animeId: Long, url: String, title: String): Episode {
        val existing = episodeRepository.getEpisodeByUrlAndAnimeId(url = url, animeId = animeId)
        if (existing != null) return existing

        val episode = Episode.create().copy(
            animeId = animeId,
            url = url,
            name = title,
            episodeNumber = 1.0,
            sourceOrder = 0,
        )
        return episodeRepository.addAllEpisodes(listOf(episode)).first()
    }
}
