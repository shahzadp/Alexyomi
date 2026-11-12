package tachiyomi.domain.entries.manga.repository

import kotlinx.coroutines.flow.Flow
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaUpdate

interface MangaRepository {

    suspend fun getMangaById(id: Long): Manga

    suspend fun getMangaByIdAsFlow(id: Long): Flow<Manga>

    suspend fun getMangaByUrlAndSourceId(url: String, sourceId: Long): Manga?

    fun getMangaByUrlAndSourceIdAsFlow(url: String, sourceId: Long): Flow<Manga?>

    suspend fun getAllMangas(): List<Manga>

    suspend fun resetMangaViewerFlags(): Boolean

    suspend fun setMangaCategories(mangaId: Long, categoryIds: List<Long>)

    suspend fun insertManga(manga: Manga): Long?

    suspend fun updateManga(update: MangaUpdate): Boolean

    suspend fun updateAllManga(mangaUpdates: List<MangaUpdate>): Boolean
}
