package eu.kanade.tachiyomi.ui.download

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadProvider
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadCache
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadManager
import eu.kanade.tachiyomi.data.download.manga.MangaDownloadProvider
import eu.kanade.tachiyomi.data.download.manga.model.MangaDownload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import eu.kanade.tachiyomi.util.size
import tachiyomi.domain.entries.anime.model.asAnimeCover
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeCover
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.entries.manga.model.asMangaCover
import tachiyomi.domain.entries.manga.model.Manga
import tachiyomi.domain.entries.manga.model.MangaCover
import tachiyomi.domain.entries.manga.repository.MangaRepository
import tachiyomi.domain.items.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.source.manga.service.MangaSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

sealed class DownloadsEntry {
    abstract val id: Long
    abstract val sourceId: Long
    abstract val title: String
    abstract val sourceName: String
    abstract val count: Int
    abstract val sizeBytes: Long
    abstract val isAnime: Boolean

    data class AnimeEntry(
        override val id: Long,
        override val sourceId: Long,
        override val title: String,
        override val sourceName: String,
        override val count: Int,
        override val sizeBytes: Long,
        val anime: Anime,
        val cover: AnimeCover,
    ) : DownloadsEntry() {
        override val isAnime: Boolean = true
    }

    data class MangaEntry(
        override val id: Long,
        override val sourceId: Long,
        override val title: String,
        override val sourceName: String,
        override val count: Int,
        override val sizeBytes: Long,
        val manga: Manga,
        val cover: MangaCover,
    ) : DownloadsEntry() {
        override val isAnime: Boolean = false
    }
}

data class DownloadsListState(
    val animeItems: List<DownloadsEntry.AnimeEntry> = emptyList(),
    val mangaItems: List<DownloadsEntry.MangaEntry> = emptyList(),
    val isLoading: Boolean = true,
)

enum class QueueSort {
    NewestUpload,
    OldestUpload,
    NumberAscending,
    NumberDescending,
}

class DownloadsListScreenModel(
    private val animeRepository: AnimeRepository = Injekt.get(),
    private val mangaRepository: MangaRepository = Injekt.get(),
    private val animeDownloadCache: AnimeDownloadCache = Injekt.get(),
    private val mangaDownloadCache: MangaDownloadCache = Injekt.get(),
    private val animeDownloadManager: AnimeDownloadManager = Injekt.get(),
    private val animeDownloadProvider: AnimeDownloadProvider = Injekt.get(),
    private val mangaDownloadManager: MangaDownloadManager = Injekt.get(),
    private val mangaDownloadProvider: MangaDownloadProvider = Injekt.get(),
    private val animeSourceManager: AnimeSourceManager = Injekt.get(),
    private val mangaSourceManager: MangaSourceManager = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val getChaptersByMangaId: GetChaptersByMangaId = Injekt.get(),
) : ScreenModel {

    private val _state = MutableStateFlow(DownloadsListState())
    val state: StateFlow<DownloadsListState> = _state.asStateFlow()

    val animeQueue: StateFlow<List<AnimeDownload>> = animeDownloadManager.queueState
    val mangaQueue: StateFlow<List<MangaDownload>> = mangaDownloadManager.queueState

    val isAnimeDownloaderRunning: StateFlow<Boolean> = animeDownloadManager.isDownloaderRunning
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), false)
    val isMangaDownloaderRunning: StateFlow<Boolean> = mangaDownloadManager.isDownloaderRunning
        .stateIn(screenModelScope, SharingStarted.WhileSubscribed(5000), false)

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
                // The download cache can be stale (e.g. after the storage folder was moved), so the
                // entries are verified against the filesystem instead of trusting the cache.
                val animeItems = animeRepository.getAllAnimes()
                    .mapNotNull { anime ->
                        // Fast path: the cache knows which entries have downloads
                        if (animeDownloadCache.getDownloadCount(anime) <= 0) return@mapNotNull null
                        val source = animeSourceManager.getOrStub(anime.source)
                        val episodes = getEpisodesByAnimeId.await(anime.id)
                        val downloaded = episodes.filter { episode ->
                            animeDownloadCache.isEpisodeDownloaded(
                                episode.name,
                                episode.scanlator,
                                anime.title,
                                anime.source,
                                skipCache = true,
                            )
                        }
                        // The cache was stale (e.g. the storage folder moved), skip the entry
                        if (downloaded.isEmpty()) return@mapNotNull null
                        val size = downloaded.sumOf { episode ->
                            animeDownloadProvider.findEpisodeDir(
                                episode.name,
                                episode.scanlator,
                                anime.title,
                                source,
                            )?.size() ?: 0L
                        }
                        DownloadsEntry.AnimeEntry(
                            id = anime.id,
                            sourceId = anime.source,
                            title = anime.title,
                            sourceName = source.name,
                            count = downloaded.size,
                            sizeBytes = size,
                            anime = anime,
                            cover = anime.asAnimeCover(),
                        )
                    }
                    .sortedBy { it.title.lowercase() }

                val mangaItems = mangaRepository.getAllMangas()
                    .mapNotNull { manga ->
                        // Fast path: the cache knows which entries have downloads
                        if (mangaDownloadCache.getDownloadCount(manga) <= 0) return@mapNotNull null
                        val source = mangaSourceManager.getOrStub(manga.source)
                        val chapters = getChaptersByMangaId.await(manga.id)
                        val downloaded = chapters.filter { chapter ->
                            mangaDownloadCache.isChapterDownloaded(
                                chapter.name,
                                chapter.scanlator,
                                manga.title,
                                manga.source,
                                skipCache = true,
                            )
                        }
                        // The cache was stale (e.g. the storage folder moved), skip the entry
                        if (downloaded.isEmpty()) return@mapNotNull null
                        val size = downloaded.sumOf { chapter ->
                            mangaDownloadProvider.findChapterDir(
                                chapter.name,
                                chapter.scanlator,
                                manga.title,
                                source,
                            )?.size() ?: 0L
                        }
                        DownloadsEntry.MangaEntry(
                            id = manga.id,
                            sourceId = manga.source,
                            title = manga.title,
                            sourceName = source.name,
                            count = downloaded.size,
                            sizeBytes = size,
                            manga = manga,
                            cover = manga.asMangaCover(),
                        )
                    }
                    .sortedBy { it.title.lowercase() }

                _state.update {
                    DownloadsListState(
                        animeItems = animeItems,
                        mangaItems = mangaItems,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun deleteDownloads(entry: DownloadsEntry) {
        screenModelScope.launch {
            when (entry) {
                is DownloadsEntry.AnimeEntry -> {
                    val source = animeSourceManager.getOrStub(entry.sourceId)
                    animeDownloadManager.deleteAnime(entry.anime, source)
                }
                is DownloadsEntry.MangaEntry -> {
                    val source = mangaSourceManager.getOrStub(entry.sourceId)
                    mangaDownloadManager.deleteManga(entry.manga, source)
                }
            }
        }
    }

    fun startAnimeDownloads() = animeDownloadManager.startDownloads()
    fun pauseAnimeDownloads() = animeDownloadManager.pauseDownloads()
    fun clearAnimeQueue() = animeDownloadManager.clearQueue()

    fun startMangaDownloads() = mangaDownloadManager.startDownloads()
    fun pauseMangaDownloads() = mangaDownloadManager.pauseDownloads()
    fun clearMangaQueue() = mangaDownloadManager.clearQueue()

    fun cancelAnimeDownloads(downloads: List<AnimeDownload>) = animeDownloadManager.cancelQueuedDownloads(downloads)
    fun cancelMangaDownloads(downloads: List<MangaDownload>) = mangaDownloadManager.cancelQueuedDownloads(downloads)

    fun sortAnimeQueue(mode: QueueSort) {
        val queue = animeQueue.value
        val sorted = when (mode) {
            QueueSort.NewestUpload -> queue.sortedByDescending { it.episode.dateUpload }
            QueueSort.OldestUpload -> queue.sortedBy { it.episode.dateUpload }
            QueueSort.NumberAscending -> queue.sortedBy { it.episode.episodeNumber }
            QueueSort.NumberDescending -> queue.sortedByDescending { it.episode.episodeNumber }
        }
        animeDownloadManager.reorderQueue(sorted)
    }

    fun sortMangaQueue(mode: QueueSort) {
        val queue = mangaQueue.value
        val sorted = when (mode) {
            QueueSort.NewestUpload -> queue.sortedByDescending { it.chapter.dateUpload }
            QueueSort.OldestUpload -> queue.sortedBy { it.chapter.dateUpload }
            QueueSort.NumberAscending -> queue.sortedBy { it.chapter.chapterNumber }
            QueueSort.NumberDescending -> queue.sortedByDescending { it.chapter.chapterNumber }
        }
        mangaDownloadManager.reorderQueue(sorted)
    }
}
