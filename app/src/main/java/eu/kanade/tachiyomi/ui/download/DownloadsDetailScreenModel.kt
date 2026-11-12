package eu.kanade.tachiyomi.ui.download

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadProvider
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.util.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.domain.entries.EntryCover
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.items.chapter.model.Chapter
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

sealed class DownloadsDetailItem {
    abstract val id: Long
    abstract val name: String
    abstract val number: Double
    abstract val sizeBytes: Long
    abstract val watched: Boolean

    data class Episode(
        override val id: Long,
        override val name: String,
        override val number: Double,
        override val sizeBytes: Long,
        override val watched: Boolean,
        val episode: tachiyomi.domain.items.episode.model.Episode,
    ) : DownloadsDetailItem()

    data class Chapter(
        override val id: Long,
        override val name: String,
        override val number: Double,
        override val sizeBytes: Long,
        override val watched: Boolean,
        val chapter: tachiyomi.domain.items.chapter.model.Chapter,
    ) : DownloadsDetailItem()
}

data class DownloadsDetailState(
    val isAnime: Boolean = false,
    val title: String = "",
    val cover: EntryCover? = null,
    val items: List<DownloadsDetailItem> = emptyList(),
    val isLoading: Boolean = true,
)

class DownloadsDetailScreenModel(
    private val animeId: Long?,
    private val mangaId: Long?,
    private val animeRepository: AnimeRepository = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val animeDownloadCache: AnimeDownloadCache = Injekt.get(),
    private val mangaDownloadCache: MangaDownloadCache = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
    private val mangaDownloadManager: MangaDownloadManager = Injekt.get(),
    private val animeDownloadProvider: AnimeDownloadProvider = Injekt.get(),
    private val mangaDownloadProvider: MangaDownloadProvider = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val mangaSourceManager: MangaSourceManager = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
) : ScreenModel {

    private val _state = MutableStateFlow(DownloadsDetailState(isAnime = animeId != null))
    val state: StateFlow<DownloadsDetailState> = _state.asStateFlow()

    init {
        refresh()
        screenModelScope.launch {
            combine(
                animeDownloadCache.changes,
                mangaDownloadCache.changes,
            ) { _, _ -> Unit }
                .debounce(600L)
                .collect { refresh() }
        }
    }

    fun refresh() {
        screenModelScope.launch {
            withContext(Dispatchers.IO) {
                if (animeId != null) {
                    val anime = animeRepository.getAnimeById(animeId)
                    val source = animeSourceManager.getOrStub(anime.source)
                    val items = getEpisodesByAnimeId.await(animeId)
                        .sortedBy { it.episodeNumber }
                        .mapNotNull { episode ->
                            if (!animeDownloadCache.isEpisodeDownloaded(
                                    episode.name,
                                    episode.scanlator,
                                    anime.title,
                                    anime.source,
                                    skipCache = true,
                                )
                            ) {
                                return@mapNotNull null
                            }
                            val dir = animeDownloadProvider.findEpisodeDir(
                                episode.name,
                                episode.scanlator,
                                anime.title,
                                source,
                            )
                            DownloadsDetailItem.Episode(
                                id = episode.id,
                                name = episode.name,
                                number = episode.episodeNumber,
                                sizeBytes = dir?.size() ?: 0L,
                                watched = episode.seen,
                                episode = episode,
                            )
                        }

                    _state.update {
                        it.copy(
                            title = anime.title,
                            cover = anime.asAnimeCover(),
                            items = items,
                            isLoading = false,
                        )
                    }
                } else if (mangaId != null) {
                    val manga = mangaRepository.getMangaById(mangaId)
                    val source = mangaSourceManager.getOrStub(manga.source)
                    val items = getChaptersByMangaId.await(mangaId)
                        .sortedBy { it.chapterNumber }
                        .mapNotNull { chapter ->
                            if (!mangaDownloadCache.isChapterDownloaded(
                                    chapter.name,
                                    chapter.scanlator,
                                    manga.title,
                                    manga.source,
                                    skipCache = true,
                                )
                            ) {
                                return@mapNotNull null
                            }
                            val dir = mangaDownloadProvider.findChapterDir(
                                chapter.name,
                                chapter.scanlator,
                                manga.title,
                                source,
                            )
                            DownloadsDetailItem.Chapter(
                                id = chapter.id,
                                name = chapter.name,
                                number = chapter.chapterNumber,
                                sizeBytes = dir?.size() ?: 0L,
                                watched = chapter.read,
                                chapter = chapter,
                            )
                        }

                    _state.update {
                        it.copy(
                            title = manga.title,
                            cover = manga.asMangaCover(),
                            items = items,
                            isLoading = false,
                        )
                    }
                }
            }
        }
    }

    /**
     * Deletes the given downloaded episodes/chapters. Only the selected items are removed,
     * regardless of the read/bookmarked protection rules used for automatic cleanups.
     */
    fun deleteSelected(items: List<DownloadsDetailItem>) {
        if (items.isEmpty()) return
        screenModelScope.launch {
            withContext(Dispatchers.IO) {
                val episodes = items.filterIsInstance<DownloadsDetailItem.Episode>().map { it.episode }
                val chapters = items.filterIsInstance<DownloadsDetailItem.Chapter>().map { it.chapter }

                if (episodes.isNotEmpty() && animeId != null) {
                    val anime = animeRepository.getAnimeById(animeId)
                    val source = animeSourceManager.getOrStub(anime.source)
                    animeDownloadManager.removeFromQueue(episodes)
                    val (_, episodeDirs) = animeDownloadProvider.findEpisodeDirs(episodes, anime, source)
                    episodeDirs.forEach { it.delete() }
                    animeDownloadCache.removeEpisodes(episodes, anime)
                }

                if (chapters.isNotEmpty() && mangaId != null) {
                    val manga = mangaRepository.getMangaById(mangaId)
                    val source = mangaSourceManager.getOrStub(manga.source)
                    mangaDownloadManager.removeFromQueue(chapters)
                    val (_, chapterDirs) = mangaDownloadProvider.findChapterDirs(chapters, manga, source)
                    chapterDirs.forEach { it.delete() }
                    mangaDownloadCache.removeChapters(chapters, manga)
                }
            }
        }
    }
}
