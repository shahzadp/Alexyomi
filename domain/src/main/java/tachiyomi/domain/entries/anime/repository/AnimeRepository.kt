package tachiyomi.domain.entries.anime.repository

import aniyomi.domain.anime.SeasonAnime
import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeUpdate

interface AnimeRepository {

    suspend fun getAnimeById(id: Long): Anime

    suspend fun getAnimeByIdAsFlow(id: Long): Flow<Anime>

    suspend fun getAnimeByUrlAndSourceId(url: String, sourceId: Long): Anime?

    fun getAnimeByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Anime?>

    suspend fun getAllAnimes(): List<Anime>

    suspend fun resetAnimeViewerFlags(): Boolean

    suspend fun setAnimeCategories(animeId: Long, categoryIds: List<Long>)

    suspend fun insertAnime(anime: Anime): Long?

    suspend fun updateAnime(update: AnimeUpdate): Boolean

    suspend fun updateAllAnime(animeUpdates: List<AnimeUpdate>): Boolean

    suspend fun getAnimeSeasonsById(parentId: Long): List<SeasonAnime>

    fun getAnimeSeasonsByIdAsFlow(parentId: Long): Flow<List<SeasonAnime>>

    suspend fun removeParentIdByIds(animeIds: List<Long>)

    suspend fun getChildrenByParentId(parentId: Long): List<Anime>
}
