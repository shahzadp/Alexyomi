package eu.kanade.domain.entries.anime.interactor

import tachiyomi.domain.entries.anime.interactor.AnimeFetchInterval
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import java.time.Instant
import java.time.ZonedDateTime

class UpdateAnime(
    private val animeRepository: AnimeRepository,
    private val animeFetchInterval: AnimeFetchInterval,
) {

    suspend fun await(animeUpdate: AnimeUpdate): Boolean {
        return animeRepository.updateAnime(animeUpdate)
    }

    suspend fun awaitAll(animeUpdates: List<AnimeUpdate>): Boolean {
        return animeRepository.updateAllAnime(animeUpdates)
    }

    suspend fun awaitUpdateFetchInterval(
        anime: Anime,
        dateTime: ZonedDateTime = ZonedDateTime.now(),
        window: Pair<Long, Long> = animeFetchInterval.getWindow(dateTime),
    ): Boolean {
        return animeRepository.updateAnime(
            animeFetchInterval.toAnimeUpdate(anime, dateTime, window),
        )
    }

    suspend fun awaitUpdateLastUpdate(animeId: Long): Boolean {
        return animeRepository.updateAnime(AnimeUpdate(id = animeId, lastUpdate = Instant.now().toEpochMilli()))
    }

    suspend fun awaitUpdateCoverLastModified(animeId: Long): Boolean {
        return animeRepository.updateAnime(
            AnimeUpdate(id = animeId, coverLastModified = Instant.now().toEpochMilli()),
        )
    }

    suspend fun awaitUpdateBackgroundLastModified(animeId: Long): Boolean {
        return animeRepository.updateAnime(
            AnimeUpdate(id = animeId, backgroundLastModified = Instant.now().toEpochMilli()),
        )
    }
}
