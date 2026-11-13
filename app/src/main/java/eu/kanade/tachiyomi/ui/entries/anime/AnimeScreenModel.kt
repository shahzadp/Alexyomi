package eu.kanade.tachiyomi.ui.entries.anime

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Immutable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import aniyomi.domain.anime.SeasonAnime
import aniyomi.domain.anime.SeasonDisplayMode
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.addOrRemove
import eu.kanade.core.util.insertSeparators
import eu.kanade.domain.entries.anime.interactor.SetAnimeViewerFlags
import eu.kanade.domain.entries.anime.interactor.SyncRelatedAnimeWithSource
import eu.kanade.domain.entries.anime.interactor.UpdateAnime
import eu.kanade.domain.entries.anime.model.downloadedFilter
import eu.kanade.domain.entries.anime.model.seasonDownloadedFilter
import eu.kanade.domain.items.episode.interactor.SetSeenStatus
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.domain.track.anime.interactor.AddAnimeTracks
import eu.kanade.domain.track.anime.interactor.RefreshAnimeTracks
import eu.kanade.domain.track.anime.interactor.TrackEpisode
import eu.kanade.domain.track.model.AutoTrackState
import eu.kanade.domain.track.service.TrackPreferences
import eu.kanade.presentation.entries.DownloadAction
import eu.kanade.presentation.entries.anime.components.EpisodeDownloadAction
import eu.kanade.presentation.util.formattedMessage
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.UnmeteredSource
import eu.kanade.tachiyomi.animesource.model.FetchType
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadCache
import eu.kanade.tachiyomi.data.download.anime.AnimeDownloadManager
import eu.kanade.tachiyomi.data.download.anime.model.AnimeDownload
import eu.kanade.tachiyomi.data.track.EnhancedAnimeTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.ui.entries.anime.track.AnimeTrackItem
import eu.kanade.tachiyomi.ui.mal.MalEntryIds
import eu.kanade.tachiyomi.ui.mal.MalEntryLookup
import eu.kanade.tachiyomi.ui.mal.MalEntryResolver
import eu.kanade.tachiyomi.ui.mal.MalListScreenModel
import eu.kanade.tachiyomi.ui.mal.MalListStore
import eu.kanade.tachiyomi.ui.mal.MalProgressSync
import eu.kanade.tachiyomi.ui.player.settings.GesturePreferences
import eu.kanade.tachiyomi.ui.player.settings.PlayerPreferences
import eu.kanade.tachiyomi.util.AniChartApi
import eu.kanade.tachiyomi.util.episode.getNextUnseen
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import logcat.LogPriority
import mihon.domain.items.episode.interactor.FilterEpisodesForDownload
import mihon.domain.source.interactor.UpdateAnimeFromRemote
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.preference.TriState
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.core.common.util.lang.launchNonCancellable
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.download.service.DownloadPreferences
import tachiyomi.domain.entries.anime.interactor.GetAnimeWithEpisodesAndSeasons
import tachiyomi.domain.entries.anime.interactor.GetRelatedAnime
import tachiyomi.domain.entries.anime.interactor.SetAnimeEpisodeFlags
import tachiyomi.domain.entries.anime.interactor.SetAnimeSeasonFlags
import tachiyomi.domain.entries.anime.model.Anime
import tachiyomi.domain.entries.anime.model.AnimeRelationGroup
import tachiyomi.domain.entries.anime.model.NoSeasonsException
import tachiyomi.domain.entries.anime.repository.AnimeRepository
import tachiyomi.domain.entries.applyFilter
import tachiyomi.domain.items.episode.interactor.GetEpisodesByAnimeId
import tachiyomi.domain.items.episode.interactor.SetAnimeDefaultEpisodeFlags
import tachiyomi.domain.items.episode.interactor.UpdateEpisode
import tachiyomi.domain.items.episode.model.Episode
import tachiyomi.domain.items.episode.model.EpisodeUpdate
import tachiyomi.domain.items.episode.model.NoEpisodesException
import tachiyomi.domain.items.episode.service.calculateEpisodeGap
import tachiyomi.domain.items.episode.service.getEpisodeSort
import tachiyomi.domain.items.season.interactor.SetAnimeDefaultSeasonFlags
import tachiyomi.domain.items.season.service.getSeasonSortComparator
import tachiyomi.domain.items.season.service.seasonSortAlphabetically
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import tachiyomi.domain.track.anime.interactor.GetAnimeTracks
import tachiyomi.domain.track.anime.model.AnimeTrack
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.source.local.entries.anime.isLocal
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar
import kotlin.math.floor

class AnimeScreenModel(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val animeId: Long,
    private val isFromSource: Boolean,
    private val downloadPreferences: DownloadPreferences = Injekt.get(),
    private val libraryPreferences: LibraryPreferences = Injekt.get(),
    private val trackPreferences: TrackPreferences = Injekt.get(),
    internal val playerPreferences: PlayerPreferences = Injekt.get(),
    internal val gesturePreferences: GesturePreferences = Injekt.get(),
    private val sourcePreferences: SourcePreferences = Injekt.get(),
    private val trackerManager: TrackerManager = Injekt.get(),
    private val trackEpisode: TrackEpisode = Injekt.get(),
    private val sourceManager: AnimeSourceManager = Injekt.get(),
    private val downloadManager: AnimeDownloadManager = Injekt.get(),
    private val downloadCache: AnimeDownloadCache = Injekt.get(),
    private val getAnimeAndEpisodesAndSeasons: GetAnimeWithEpisodesAndSeasons = Injekt.get(),
    private val setAnimeEpisodeFlags: SetAnimeEpisodeFlags = Injekt.get(),
    private val setAnimeDefaultEpisodeFlags: SetAnimeDefaultEpisodeFlags = Injekt.get(),
    private val setAnimeSeasonFlags: SetAnimeSeasonFlags = Injekt.get(),
    private val setAnimeDefaultSeasonFlags: SetAnimeDefaultSeasonFlags = Injekt.get(),
    private val getRelatedAnime: GetRelatedAnime = Injekt.get(),
    private val syncRelatedAnimeWithSource: SyncRelatedAnimeWithSource = Injekt.get(),
    private val setSeenStatus: SetSeenStatus = Injekt.get(),
    private val updateEpisode: UpdateEpisode = Injekt.get(),
    private val updateAnime: UpdateAnime = Injekt.get(),
    private val getTracks: GetAnimeTracks = Injekt.get(),
    private val addTracks: AddAnimeTracks = Injekt.get(),
    private val animeRepository: AnimeRepository = Injekt.get(),
    private val getEpisodesByAnimeId: GetEpisodesByAnimeId = Injekt.get(),
    private val filterEpisodesForDownload: FilterEpisodesForDownload = Injekt.get(),
    private val updateAnimeFromRemote: UpdateAnimeFromRemote = Injekt.get(),
    internal val setAnimeViewerFlags: SetAnimeViewerFlags = Injekt.get(),
    val snackbarHostState: SnackbarHostState = SnackbarHostState(),
) : StateScreenModel<AnimeScreenModel.State>(State.Loading) {

    private var cachedMalEntryIds: MalEntryIds? = null

    /** The link with MyAnimeList is only looked up once per screen. */
    private var lastMalLinkAttempt = 0L

    private val successState: State.Success?
        get() = state.value as? State.Success

    val anime: Anime?
        get() = successState?.anime

    val source: AnimeSource?
        get() = successState?.source

    private val processedEpisodes: List<EpisodeList.Item>?
        get() = successState?.processedEpisodes

    val episodeSwipeStartAction = libraryPreferences.swipeEpisodeEndAction().get()
    val episodeSwipeEndAction = libraryPreferences.swipeEpisodeStartAction().get()
    var autoTrackState = trackPreferences.autoUpdateTrackOnMarkRead().get()

    val showNextEpisodeAirTime = trackPreferences.showNextEpisodeAiringTime().get()
    val alwaysUseExternalPlayer = playerPreferences.alwaysUseExternalPlayer().get()
    val useExternalDownloader = downloadPreferences.useExternalDownloader().get()

    val relatedAnimeDisplayMode = sourcePreferences.sourceDisplayMode().get()

    private val selectedPositions: Array<Int> = arrayOf(-1, -1) // first and last selected index in list
    private val selectedEpisodeIds: HashSet<Long> = HashSet()

    /**
     * Helper function to update the UI state only if it's currently in success state
     */
    private inline fun updateSuccessState(func: (State.Success) -> State.Success) {
        mutableState.update {
            when (it) {
                State.Loading -> it
                is State.Success -> func(it)
            }
        }
    }

    init {
        screenModelScope.launchIO {
            combine(
                getAnimeAndEpisodesAndSeasons.subscribe(animeId).distinctUntilChanged(),
                downloadCache.changes,
                downloadManager.queueState,
            ) { animeAndEpisodesAndSeasons, _, _ -> animeAndEpisodesAndSeasons }
                .flowWithLifecycle(lifecycle)
                .collectLatest { (anime, episodes, seasons) ->
                    updateSuccessState {
                        it.copy(
                            anime = anime,
                            episodes = episodes.toEpisodeListItems(anime),
                            seasons = seasons.toAnimeSeasonItems(),
                        )
                    }
                }
        }

        observeDownloads()

        // Keep the read episodes in sync with MyAnimeList. This has to run when the
        // episodes are there, otherwise there is nothing to tick.
        screenModelScope.launchIO {
            getAnimeAndEpisodesAndSeasons.subscribe(animeId)
                .distinctUntilChanged()
                .flowWithLifecycle(lifecycle)
                .collect { (anime, episodes, _) ->
                    // The lookup takes a moment, so it runs on its own - a new emission must
                    // not cancel the one that is running.
                    screenModelScope.launchIO {
                        syncMalProgress(anime)
                    }
                }
        }

        screenModelScope.launchIO {
            val anime = getAnimeAndEpisodesAndSeasons.awaitAnime(animeId)
            val source = sourceManager.getOrStub(anime.source)

            val episodes = if (anime.fetchType == FetchType.Seasons) {
                emptyList()
            } else {
                getAnimeAndEpisodesAndSeasons.awaitEpisodes(animeId)
                    .toEpisodeListItems(anime)
            }

            val seasons = if (anime.fetchType == FetchType.Episodes) {
                emptyList()
            } else {
                getAnimeAndEpisodesAndSeasons.awaitSeasons(animeId)
                    .toAnimeSeasonItems()
            }

            if (!anime.initialized) {
                setAnimeDefaultEpisodeFlags.await(anime)
                setAnimeDefaultSeasonFlags.await(anime)
            }

            val needRefreshInfo = !anime.initialized
            val needRefreshEpisode = episodes.isEmpty() && anime.fetchType == FetchType.Episodes
            val needRefreshSeason = seasons.isEmpty() && anime.fetchType == FetchType.Seasons

            // Show what we have earlier
            mutableState.update {
                State.Success(
                    anime = anime,
                    source = source,
                    isFromSource = isFromSource,
                    episodes = episodes,
                    seasons = seasons,
                    isRefreshingData = needRefreshInfo || needRefreshEpisode || needRefreshSeason,
                    dialog = null,
                )
            }
            // Start observe tracking since it only needs animeId
            observeTrackers()

            observeRelatedAnime()
            syncRelatedAnime()

            // Fetch info-episodes when needed
            if ((needRefreshInfo || needRefreshEpisode || needRefreshSeason) && screenModelScope.isActive) {
                fetchAllFromSource(
                    manualFetch = false,
                    fetchDetails = needRefreshInfo,
                    fetchEpisodes = needRefreshEpisode,
                    fetchSeasons = needRefreshSeason,
                )
            }

            // Initial loading finished
            updateSuccessState { it.copy(isRefreshingData = false) }
        }
    }

    fun fetchAllFromSource(manualFetch: Boolean = true) {
        screenModelScope.launch {
            updateSuccessState { it.copy(isRefreshingData = true) }
            fetchAllFromSource(
                manualFetch = manualFetch,
                fetchDetails = true,
                fetchEpisodes = true,
                fetchSeasons = true,
            )
            updateSuccessState { it.copy(isRefreshingData = false) }
            successState?.let { updateAiringTime(it.anime, it.trackItems, manualFetch) }
        }
    }

    private suspend fun fetchAllFromSource(
        manualFetch: Boolean,
        fetchDetails: Boolean,
        fetchEpisodes: Boolean,
        fetchSeasons: Boolean,
    ) {
        val state = successState ?: return
        if (fetchDetails) {
            syncRelatedAnime(forceRefresh = true)
        }

        try {
            withUIContext {
                when (state.anime.fetchType) {
                    FetchType.Episodes -> {
                        val update = updateAnimeFromRemote.awaitEpisodesUpdate(
                            source = state.source,
                            anime = state.anime,
                            fetchDetails = fetchDetails,
                            fetchEpisodes = fetchEpisodes,
                            manualFetch = manualFetch,
                        )
                            .getOrThrow()

                        if (manualFetch) {
                            downloadNewEpisodes(update.newEpisodes)
                        }
                    }
                    FetchType.Seasons -> {
                        val update = updateAnimeFromRemote.awaitSeasonsUpdate(
                            source = state.source,
                            anime = state.anime,
                            fetchDetails = fetchDetails,
                            fetchSeasons = fetchSeasons,
                            manualFetch = manualFetch,
                        )
                            .getOrThrow()

                        if (libraryPreferences.updateSeasonOnRefresh().get()) {
                            fetchEpisodesFromSeasons(update.newSeasons, manualFetch)
                        }
                    }
                }
            }
        } catch (_: CancellationException) {
            // ignore
        } catch (e: Exception) {
            val message = when (e) {
                is NoEpisodesException -> {
                    context.stringResource(AYMR.strings.no_episodes_error)
                }
                is NoSeasonsException -> {
                    context.stringResource(AYMR.strings.no_seasons_error)
                }
                else -> {
                    logcat(LogPriority.ERROR, e)
                    with(context) { e.formattedMessage }
                }
            }

            screenModelScope.launch {
                snackbarHostState.showSnackbar(message = message)
            }
        }
    }

    // Anime info - start


    /**
     * Returns true if the anime has any downloads.
     */
    private fun hasDownloads(): Boolean {
        val anime = successState?.anime ?: return false
        return downloadManager.getDownloadCount(anime) > 0
    }

    /**
     * Deletes all the downloads for the anime.
     */
    private fun deleteDownloads() {
        val state = successState ?: return
        downloadManager.deleteAnime(state.anime, state.source)
    }


    // Anime info - end

    // Episodes list - start

    private fun observeDownloads() {
        screenModelScope.launchIO {
            downloadManager.statusFlow()
                .filter { it.anime.id == successState?.anime?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .flowWithLifecycle(lifecycle)
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }

        screenModelScope.launchIO {
            downloadManager.progressFlow()
                .filter { it.anime.id == successState?.anime?.id }
                .catch { error -> logcat(LogPriority.ERROR, error) }
                .flowWithLifecycle(lifecycle)
                .collect {
                    withUIContext {
                        updateDownloadState(it)
                    }
                }
        }
    }

    private fun updateDownloadState(download: AnimeDownload) {
        updateSuccessState { successState ->
            val modifiedIndex = successState.episodes.indexOfFirst { it.id == download.episode.id }
            if (modifiedIndex < 0) return@updateSuccessState successState

            val newEpisodes = successState.episodes.toMutableList().apply {
                val item = removeAt(modifiedIndex)
                    .copy(downloadState = download.status, downloadProgress = download.progress)
                add(modifiedIndex, item)
            }
            successState.copy(episodes = newEpisodes)
        }
    }

    private fun List<Episode>.toEpisodeListItems(anime: Anime): List<EpisodeList.Item> {
        val isLocal = anime.isLocal()
        return map { episode ->
            val activeDownload = if (isLocal) {
                null
            } else {
                downloadManager.getQueuedDownloadOrNull(episode.id)
            }
            val downloaded = if (isLocal) {
                true
            } else {
                downloadManager.isEpisodeDownloaded(
                    episode.name,
                    episode.scanlator,
                    anime.title,
                    anime.source,
                )
            }
            val downloadState = when {
                activeDownload != null -> activeDownload.status
                downloaded -> AnimeDownload.State.DOWNLOADED
                else -> AnimeDownload.State.NOT_DOWNLOADED
            }

            EpisodeList.Item(
                episode = episode,
                downloadState = downloadState,
                downloadProgress = activeDownload?.progress ?: 0,
                selected = episode.id in selectedEpisodeIds,
            )
        }
    }

    private fun List<SeasonAnime>.toAnimeSeasonItems(): List<AnimeSeasonItem> {
        return map { seasonAnime ->
            AnimeSeasonItem(
                seasonAnime = seasonAnime,
                downloadCount = downloadManager.getDownloadCount(seasonAnime.anime).toLong(),
                unseenCount = seasonAnime.unseenCount,
                isLocal = seasonAnime.anime.isLocal(),
                sourceLanguage = sourceManager.getOrStub(seasonAnime.anime.source).lang,
                showContinueOverlay = false,
            )
        }
    }


    /**
     * Fetch episodes from all seasons of an anime.
     */
    private suspend fun CoroutineScope.fetchEpisodesFromSeasons(seasons: List<Anime>, manualFetch: Boolean) {
        val state = successState ?: return

        val fetch: suspend (Anime) -> Unit = { s ->
            // Only fetch seasons with `Episodes` fetch type and only for non completed, unless they
            // haven't been fetched at all.
            if (s.fetchType === FetchType.Episodes && (s.lastUpdate == 0L || s.status.toInt() != SAnime.COMPLETED)) {
                try {
                    updateAnimeFromRemote.awaitEpisodesUpdate(
                        source = state.source,
                        anime = s,
                        fetchEpisodes = true,
                        manualFetch = manualFetch,
                    )
                } catch (e: Throwable) {
                    logcat(LogPriority.ERROR, e)
                }
            }
        }

        if (state.source is UnmeteredSource) {
            seasons.map { s ->
                async(Dispatchers.IO) {
                    fetch(s)
                }
            }.awaitAll()
        } else {
            seasons.forEach { s ->
                ensureActive()
                fetch(s)
            }
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.EpisodeSwipeAction.Disabled]
     */
    fun episodeSwipe(episodeItem: EpisodeList.Item, swipeAction: LibraryPreferences.EpisodeSwipeAction) {
        screenModelScope.launch {
            executeEpisodeSwipeAction(episodeItem, swipeAction)
        }
    }

    /**
     * @throws IllegalStateException if the swipe action is [LibraryPreferences.EpisodeSwipeAction.Disabled]
     */
    private fun executeEpisodeSwipeAction(
        episodeItem: EpisodeList.Item,
        swipeAction: LibraryPreferences.EpisodeSwipeAction,
    ) {
        val episode = episodeItem.episode
        when (swipeAction) {
            LibraryPreferences.EpisodeSwipeAction.ToggleSeen -> {
                markEpisodesSeen(listOf(episode), !episode.seen)
            }
            LibraryPreferences.EpisodeSwipeAction.ToggleBookmark -> {
                bookmarkEpisodes(listOf(episode), !episode.bookmark)
            }
            LibraryPreferences.EpisodeSwipeAction.ToggleFillermark -> {
                fillermarkEpisodes(listOf(episode), !episode.fillermark)
            }
            LibraryPreferences.EpisodeSwipeAction.Download -> {
                val downloadAction: EpisodeDownloadAction = when (episodeItem.downloadState) {
                    AnimeDownload.State.ERROR,
                    AnimeDownload.State.NOT_DOWNLOADED,
                    -> EpisodeDownloadAction.START_NOW
                    AnimeDownload.State.QUEUE,
                    AnimeDownload.State.DOWNLOADING,
                    -> EpisodeDownloadAction.CANCEL
                    AnimeDownload.State.DOWNLOADED -> EpisodeDownloadAction.DELETE
                }
                runEpisodeDownloadActions(
                    items = listOf(episodeItem),
                    action = downloadAction,
                )
            }
            LibraryPreferences.EpisodeSwipeAction.Disabled -> throw IllegalStateException()
        }
    }

    suspend fun getNextUnseenEpisode(anime: Anime): Episode? {
        return getEpisodesByAnimeId.await(anime.id).getNextUnseen(anime, downloadManager)
    }

    /**
     * Returns the next unseen episode or null if everything is seen.
     */
    fun getNextUnseenEpisode(): Episode? {
        val successState = successState ?: return null
        return successState.episodes.getNextUnseen(successState.anime)
    }

    private fun getUnseenEpisodes(): List<Episode> {
        return successState?.processedEpisodes
            ?.filter { (episode, dlStatus) -> !episode.seen && dlStatus == AnimeDownload.State.NOT_DOWNLOADED }
            ?.map { it.episode }
            ?.toList()
            ?: emptyList()
    }

    private fun getUnseenEpisodesSorted(): List<Episode> {
        val anime = successState?.anime ?: return emptyList()
        val episodes = getUnseenEpisodes().sortedWith(getEpisodeSort(anime))
        return if (anime.sortDescending()) episodes.reversed() else episodes
    }

    private fun startDownload(
        episodes: List<Episode>,
        startNow: Boolean,
        video: Video? = null,
    ) {
        val successState = successState ?: return

        screenModelScope.launchNonCancellable {
            if (startNow) {
                val episodeId = episodes.singleOrNull()?.id ?: return@launchNonCancellable
                downloadManager.startDownloadNow(episodeId)
            } else {
                downloadEpisodes(episodes, false, video)
            }
        }
    }

    fun runEpisodeDownloadActions(
        items: List<EpisodeList.Item>,
        action: EpisodeDownloadAction,
    ) {
        when (action) {
            EpisodeDownloadAction.START -> {
                startDownload(items.map { it.episode }, false)
                if (items.any { it.downloadState == AnimeDownload.State.ERROR }) {
                    downloadManager.startDownloads()
                }
            }
            EpisodeDownloadAction.START_NOW -> {
                val episode = items.singleOrNull()?.episode ?: return
                startDownload(listOf(episode), true)
            }
            EpisodeDownloadAction.CANCEL -> {
                val episodeId = items.singleOrNull()?.id ?: return
                cancelDownload(episodeId)
            }
            EpisodeDownloadAction.DELETE -> {
                deleteEpisodes(items.map { it.episode })
            }
            EpisodeDownloadAction.SHOW_QUALITIES -> {
                val episode = items.singleOrNull()?.episode ?: return
                showQualitiesDialog(episode)
            }
        }
    }

    fun runDownloadAction(action: DownloadAction) {
        val episodesToDownload = when (action) {
            DownloadAction.NEXT_1_ITEM -> getUnseenEpisodesSorted().take(1)
            DownloadAction.NEXT_5_ITEMS -> getUnseenEpisodesSorted().take(5)
            DownloadAction.NEXT_10_ITEMS -> getUnseenEpisodesSorted().take(10)
            DownloadAction.NEXT_25_ITEMS -> getUnseenEpisodesSorted().take(25)

            DownloadAction.UNVIEWED_ITEMS -> getUnseenEpisodes()
        }
        if (episodesToDownload.isNotEmpty()) {
            startDownload(episodesToDownload, false)
        }
    }

    private fun cancelDownload(episodeId: Long) {
        val activeDownload = downloadManager.getQueuedDownloadOrNull(episodeId) ?: return
        downloadManager.cancelQueuedDownloads(listOf(activeDownload))
        updateDownloadState(activeDownload.apply { status = AnimeDownload.State.NOT_DOWNLOADED })
    }

    fun markPreviousEpisodeSeen(pointer: Episode) {
        val anime = successState?.anime ?: return
        val episodes = processedEpisodes.orEmpty().map { it.episode }.toList()
        val prevEpisodes = if (anime.sortDescending()) episodes.asReversed() else episodes
        val pointerPos = prevEpisodes.indexOf(pointer)
        if (pointerPos != -1) markEpisodesSeen(prevEpisodes.take(pointerPos), true)
    }

    /**
     * Mark the selected episode list as seen/unseen.
     * @param episodes the list of selected episodes.
     * @param seen whether to mark episodes as seen or unseen.
     */
    fun markEpisodesSeen(episodes: List<Episode>, seen: Boolean) {
        toggleAllSelection(false)
        if (episodes.isEmpty()) return
        screenModelScope.launchIO {
            setSeenStatus.await(
                seen = seen,
                episodes = episodes.toTypedArray(),
            )

            if (!seen || successState?.hasLoggedInTrackers == false || autoTrackState == AutoTrackState.NEVER) {
                return@launchIO
            }

            refreshTrackers()

            val tracks = getTracks.await(animeId)
            val maxEpisodeNumber = episodes.maxOf { it.episodeNumber }
            val shouldPromptTrackingUpdate = tracks.any { track -> maxEpisodeNumber > track.lastEpisodeSeen }

            if (!shouldPromptTrackingUpdate) return@launchIO

            if (autoTrackState == AutoTrackState.ALWAYS) {
                trackEpisode.await(context, animeId, maxEpisodeNumber)
                withUIContext {
                    context.toast(
                        context.stringResource(AYMR.strings.trackers_updated_summary_anime, maxEpisodeNumber.toInt()),
                    )
                }
                return@launchIO
            }

            val result = snackbarHostState.showSnackbar(
                message = context.stringResource(AYMR.strings.confirm_tracker_update_anime, maxEpisodeNumber.toInt()),
                actionLabel = context.stringResource(MR.strings.action_ok),
                duration = SnackbarDuration.Short,
                withDismissAction = true,
            )

            if (result == SnackbarResult.ActionPerformed) {
                trackEpisode.await(context, animeId, maxEpisodeNumber)
            }
        }
    }

    private suspend fun refreshTrackers(
        refreshTracks: RefreshAnimeTracks = Injekt.get(),
    ) {
        refreshTracks.await(animeId)
            .filter { it.first != null }
            .forEach { (track, e) ->
                logcat(LogPriority.ERROR, e) {
                    "Failed to refresh track data animeId=$animeId for service ${track!!.id}"
                }
                withUIContext {
                    context.toast(
                        context.stringResource(
                            MR.strings.track_error,
                            track!!.name,
                            e.message ?: "",
                        ),
                    )
                }
            }
    }

    /**
     * Downloads the given list of episodes with the manager.
     * @param episodes the list of episodes to download.
     */
    private fun downloadEpisodes(
        episodes: List<Episode>,
        alt: Boolean = false,
        video: Video? = null,
    ) {
        val anime = successState?.anime ?: return
        downloadManager.downloadEpisodes(anime, episodes, true, alt, video)
        toggleAllSelection(false)
    }

    /**
     * Bookmarks the given list of episodes.
     * @param episodes the list of episodes to bookmark.
     */
    fun bookmarkEpisodes(episodes: List<Episode>, bookmarked: Boolean) {
        screenModelScope.launchIO {
            episodes
                .filterNot { it.bookmark == bookmarked }
                .map { EpisodeUpdate(id = it.id, bookmark = bookmarked) }
                .let { updateEpisode.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    /**
     * Fillermarks the given list of episodes.
     * @param episodes the list of episodes to fillermark.
     */
    fun fillermarkEpisodes(episodes: List<Episode>, fillermarked: Boolean) {
        screenModelScope.launchIO {
            episodes
                .filterNot { it.fillermark == fillermarked }
                .map { EpisodeUpdate(id = it.id, fillermark = fillermarked) }
                .let { updateEpisode.awaitAll(it) }
        }
        toggleAllSelection(false)
    }

    /**
     * Deletes the given list of episode.
     *
     * @param episodes the list of episodes to delete.
     */
    fun deleteEpisodes(episodes: List<Episode>) {
        screenModelScope.launchNonCancellable {
            try {
                successState?.let { state ->
                    downloadManager.deleteEpisodes(
                        episodes,
                        state.anime,
                        state.source,
                    )
                }
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e)
            }
        }
    }

    private fun downloadNewEpisodes(episodes: List<Episode>) {
        screenModelScope.launchNonCancellable {
            val anime = successState?.anime ?: return@launchNonCancellable
            val episodesToDownload = filterEpisodesForDownload.await(anime, episodes)

            if (episodesToDownload.isNotEmpty()) {
                downloadEpisodes(episodesToDownload)
            }
        }
    }

    /**
     * Sets the seen filter and requests an UI update.
     * @param state whether to display only unseen episodes or all episodes.
     */
    fun setUnseenFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_UNSEEN
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_SEEN
        }
        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetUnseenFilter(anime, flag)
        }
    }

    /**
     * Sets the download filter and requests an UI update.
     * @param state whether to display only downloaded episodes or all episodes.
     */
    fun setDownloadedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_DOWNLOADED
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_NOT_DOWNLOADED
        }

        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetDownloadedFilter(anime, flag)
        }
    }

    /**
     * Sets the bookmark filter and requests an UI update.
     * @param state whether to display only bookmarked episodes or all episodes.
     */
    fun setBookmarkedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_NOT_BOOKMARKED
        }

        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetBookmarkFilter(anime, flag)
        }
    }

    /**
     * Sets the fillermark filter and requests an UI update.
     * @param state whether to display only fillermarked episodes or all episodes.
     */
    fun setFillermarkedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.EPISODE_SHOW_FILLERMARKED
            TriState.ENABLED_NOT -> Anime.EPISODE_SHOW_NOT_FILLERMARKED
        }

        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetFillermarkFilter(anime, flag)
        }
    }

    /**
     * Sets the active display mode.
     * @param mode the mode to set.
     */
    fun setDisplayMode(mode: Long) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetDisplayMode(anime, mode)
        }
    }

    /**
     * Sets the sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSorting(sort: Long) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitSetSortingModeOrFlipOrder(anime, sort)
        }
    }

    /**
     * Sets whether previews are to be shown or not.
     * @param flag to show previews.
     */
    fun showEpisodePreviews(flag: Long) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitShowEpisodePreviews(anime, flag)
        }
    }

    /**
     * Sets whether summaries are to be shown or not.
     * @param flag to show summaries.
     */
    fun showEpisodeSummaries(flag: Long) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeEpisodeFlags.awaitShowEpisodeSummaries(anime, flag)
        }
    }

    fun setCurrentSettingsAsDefault(applyToExisting: Boolean) {
        val anime = successState?.anime ?: return
        screenModelScope.launchNonCancellable {
            libraryPreferences.setEpisodeSettingsDefault(anime)
            if (applyToExisting) {
                setAnimeDefaultEpisodeFlags.awaitAll()
            }
            snackbarHostState.showSnackbar(
                message = context.stringResource(AYMR.strings.episode_settings_updated),
            )
        }
    }

    /**
     * Sets the season download filter and requests an UI update.
     * @param state whether to display only downloaded seasons or all seasons.
     */
    fun setSeasonDownloadedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.SEASON_SHOW_DOWNLOADED
            TriState.ENABLED_NOT -> Anime.SEASON_SHOW_NOT_DOWNLOADED
        }

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetDownloadedFilter(anime, flag)
        }
    }

    /**
     * Sets the season seen filter and requests an UI update.
     * @param state whether to display only unseen seasons or all seasons.
     */
    fun setSeasonUnseenFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.SEASON_SHOW_UNSEEN
            TriState.ENABLED_NOT -> Anime.SEASON_SHOW_SEEN
        }

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetUnseenFilter(anime, flag)
        }
    }

    /**
     * Sets the season started filter and requests an UI update.
     * @param state whether to display only started seasons or all seasons.
     */
    fun setSeasonStartedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.SEASON_SHOW_STARTED
            TriState.ENABLED_NOT -> Anime.SEASON_SHOW_NOT_STARTED
        }

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetStartedFilter(anime, flag)
        }
    }

    /**
     * Sets the season bookmarked filter and requests an UI update.
     * @param state whether to display only bookmarked seasons or all seasons.
     */
    fun setSeasonBookmarkedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.SEASON_SHOW_BOOKMARKED
            TriState.ENABLED_NOT -> Anime.SEASON_SHOW_NOT_BOOKMARKED
        }

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetBookmarkedFilter(anime, flag)
        }
    }

    /**
     * Sets the season fillermarked filter and requests an UI update.
     * @param state whether to display only fillermarked seasons or all seasons.
     */
    fun setSeasonFillermarkedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.SEASON_SHOW_FILLERMARKED
            TriState.ENABLED_NOT -> Anime.SEASON_SHOW_NOT_FILLERMARKED
        }

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetFillermarkedFilter(anime, flag)
        }
    }

    /**
     * Sets the season completed filter and requests an UI update.
     * @param state whether to display only completed seasons or all seasons.
     */
    fun setSeasonCompletedFilter(state: TriState) {
        val anime = successState?.anime ?: return

        val flag = when (state) {
            TriState.DISABLED -> Anime.SHOW_ALL
            TriState.ENABLED_IS -> Anime.SEASON_SHOW_COMPLETED
            TriState.ENABLED_NOT -> Anime.SEASON_SHOW_NOT_COMPLETED
        }

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetCompletedFilter(anime, flag)
        }
    }

    /**
     * Sets the season sorting method and requests an UI update.
     * @param sort the sorting mode.
     */
    fun setSeasonSorting(sort: Long) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetSortingModeOrFlipOrder(anime, sort)
        }
    }

    /**
     * Sets the season grid display method and requests an UI update.
     * @param mode the display mode.
     */
    fun setSeasonDisplayGridMode(mode: SeasonDisplayMode) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetGridMode(anime, mode)
        }
    }

    /**
     * Sets the season grid size and requests an UI update.
     * @param size the size.
     */
    fun setSeasonDisplayGridSize(size: Int) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetGridSize(anime, size)
        }
    }

    /**
     * Sets the season download overlay and requests an UI update.
     * @param visible the visibility.
     */
    fun setSeasonDownloadOverlay(visible: Boolean) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetDownloadedOverlay(anime, visible)
        }
    }

    /**
     * Sets the season unseen overlay and requests an UI update.
     * @param visible the visibility.
     */
    fun setSeasonUnseenOverlay(visible: Boolean) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetUnseenOverlay(anime, visible)
        }
    }

    /**
     * Sets the season local overlay and requests an UI update.
     * @param visible the visibility.
     */
    fun setSeasonLocalOverlay(visible: Boolean) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetLocalOverlay(anime, visible)
        }
    }

    /**
     * Sets the season lang overlay and requests an UI update.
     * @param visible the visibility.
     */
    fun setSeasonLangOverlay(visible: Boolean) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetLangOverlay(anime, visible)
        }
    }

    /**
     * Sets the season continue overlay and requests an UI update.
     * @param visible the visibility.
     */
    fun setSeasonContinueOverlay(visible: Boolean) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetContinueOverlay(anime, visible)
        }
    }

    /**
     * Sets the active season display mode.
     * @param mode the mode to set.
     */
    fun setSeasonDisplayMode(mode: Long) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            setAnimeSeasonFlags.awaitSetDisplayMode(anime, mode)
        }
    }

    fun setSeasonCurrentSettingsAsDefault(applyToExisting: Boolean) {
        val anime = successState?.anime ?: return

        screenModelScope.launchNonCancellable {
            libraryPreferences.setSeasonSettingsDefault(anime)
            if (applyToExisting) {
                setAnimeDefaultSeasonFlags.awaitAll()
            }
            snackbarHostState.showSnackbar(
                message = context.stringResource(AYMR.strings.season_settings_updated),
            )
        }
    }

    fun toggleSelection(
        item: EpisodeList.Item,
        selected: Boolean,
        userSelected: Boolean = false,
        fromLongPress: Boolean = false,
    ) {
        updateSuccessState { successState ->
            val newEpisodes = successState.processedEpisodes.toMutableList().apply {
                val selectedIndex = successState.processedEpisodes.indexOfFirst { it.id == item.episode.id }
                if (selectedIndex < 0) return@apply

                val selectedItem = get(selectedIndex)
                if ((selectedItem.selected && selected) || (!selectedItem.selected && !selected)) return@apply

                val firstSelection = none { it.selected }
                set(selectedIndex, selectedItem.copy(selected = selected))
                selectedEpisodeIds.addOrRemove(item.id, selected)

                if (selected && userSelected && fromLongPress) {
                    if (firstSelection) {
                        selectedPositions[0] = selectedIndex
                        selectedPositions[1] = selectedIndex
                    } else {
                        // Try to select the items in-between when possible
                        val range: IntRange
                        if (selectedIndex < selectedPositions[0]) {
                            range = selectedIndex + 1..<selectedPositions[0]
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            range = (selectedPositions[1] + 1)..<selectedIndex
                            selectedPositions[1] = selectedIndex
                        } else {
                            // Just select itself
                            range = IntRange.EMPTY
                        }

                        range.forEach {
                            val inbetweenItem = get(it)
                            if (!inbetweenItem.selected) {
                                selectedEpisodeIds.add(inbetweenItem.id)
                                set(it, inbetweenItem.copy(selected = true))
                            }
                        }
                    }
                } else if (userSelected && !fromLongPress) {
                    if (!selected) {
                        if (selectedIndex == selectedPositions[0]) {
                            selectedPositions[0] = indexOfFirst { it.selected }
                        } else if (selectedIndex == selectedPositions[1]) {
                            selectedPositions[1] = indexOfLast { it.selected }
                        }
                    } else {
                        if (selectedIndex < selectedPositions[0]) {
                            selectedPositions[0] = selectedIndex
                        } else if (selectedIndex > selectedPositions[1]) {
                            selectedPositions[1] = selectedIndex
                        }
                    }
                }
            }
            successState.copy(episodes = newEpisodes)
        }
    }

    fun toggleAllSelection(selected: Boolean) {
        updateSuccessState { successState ->
            val newEpisodes = successState.episodes.map {
                selectedEpisodeIds.addOrRemove(it.id, selected)
                it.copy(selected = selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(episodes = newEpisodes)
        }
    }

    fun invertSelection() {
        updateSuccessState { successState ->
            val newEpisodes = successState.episodes.map {
                selectedEpisodeIds.addOrRemove(it.id, !it.selected)
                it.copy(selected = !it.selected)
            }
            selectedPositions[0] = -1
            selectedPositions[1] = -1
            successState.copy(episodes = newEpisodes)
        }
    }

    // Episodes list - end

    // Related anime - start

    private fun observeRelatedAnime() {
        screenModelScope.launchIO {
            getRelatedAnime.subscribe(animeId)
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .collectLatest { relations ->
                    updateSuccessState { it.copy(relatedAnime = relations) }
                }
        }
    }

    fun syncRelatedAnime(forceRefresh: Boolean = false) {
        val state = successState ?: return
        if (!state.source.supportsRelatedAnime) return

        screenModelScope.launchIO {
            updateSuccessState { it.copy(isLoadingRelatedAnime = true) }
            try {
                syncRelatedAnimeWithSource.await(state.anime, forceRefresh)
            } catch (e: Throwable) {
                logcat(LogPriority.ERROR, e) { "Failed to fetch related anime" }
            } finally {
                updateSuccessState { it.copy(isLoadingRelatedAnime = false) }
            }
        }
    }

    // Related anime - end

    // Track sheet - start

    private fun observeTrackers() {
        val anime = successState?.anime ?: return

        screenModelScope.launchIO {
            combine(
                getTracks.subscribe(anime.id).catch { logcat(LogPriority.ERROR, it) },
                trackerManager.loggedInTrackersFlow(),
            ) { animeTracks, loggedInTrackers ->
                // Show only if the service supports this manga's source
                val supportedTrackers = loggedInTrackers.filter {
                    (it as? EnhancedAnimeTracker)?.accept(source!!) ?: true
                }
                val supportedTrackerIds = supportedTrackers.map { it.id }.toHashSet()
                val supportedTrackerTracks = animeTracks.filter { it.trackerId in supportedTrackerIds }
                supportedTrackerTracks.size to supportedTrackers.isNotEmpty()
            }
                .flowWithLifecycle(lifecycle)
                .distinctUntilChanged()
                .collectLatest { (trackingCount, hasLoggedInTrackers) ->
                    updateSuccessState {
                        it.copy(
                            trackingCount = trackingCount,
                            hasLoggedInTrackers = hasLoggedInTrackers,
                        )
                    }
                }
        }

        screenModelScope.launchIO {
            combine(
                getTracks.subscribe(anime.id).catch { logcat(LogPriority.ERROR, it) },
                trackerManager.loggedInTrackersFlow(),
            ) { animeTracks, loggedInTrackers ->
                loggedInTrackers
                    .map { service -> AnimeTrackItem(animeTracks.find { it.trackerId == service.id }, service) }
            }
                .distinctUntilChanged()
                .collectLatest { trackItems ->
                    updateAiringTime(anime, trackItems, manualFetch = false)
                }
        }
    }

    private suspend fun updateAiringTime(
        anime: Anime,
        trackItems: List<AnimeTrackItem>,
        manualFetch: Boolean,
    ) {
        val airingEpisodeData = AniChartApi().loadAiringTime(anime, trackItems, manualFetch)
        setAnimeViewerFlags.awaitSetNextEpisodeAiring(anime.id, airingEpisodeData)
        updateSuccessState { it.copy(nextAiringEpisode = airingEpisodeData) }
    }

    // Track sheet - end

    sealed interface Dialog {
        data class DeleteEpisodes(val episodes: List<Episode>) : Dialog
        data class ShowQualities(val episode: Episode, val anime: Anime, val source: AnimeSource) : Dialog
        data object ChangeAnimeSkipIntro : Dialog
        data object EpisodeSettingsSheet : Dialog
        data object SeasonSettingsSheet : Dialog
        data object TrackSheet : Dialog
        data object FullImages : Dialog
    }

    fun dismissDialog() {
        updateSuccessState { it.copy(dialog = null) }
    }

    fun showDeleteEpisodeDialog(episodes: List<Episode>) {
        updateSuccessState { it.copy(dialog = Dialog.DeleteEpisodes(episodes)) }
    }

    fun showSettingsDialog() {
        updateSuccessState {
            when (it.anime.fetchType) {
                FetchType.Seasons -> it.copy(dialog = Dialog.SeasonSettingsSheet)
                FetchType.Episodes -> it.copy(dialog = Dialog.EpisodeSettingsSheet)
            }
        }
    }

    fun showTrackDialog() {
        updateSuccessState { it.copy(dialog = Dialog.TrackSheet) }
    }

    /**
     * Resolves the MyAnimeList ids of this anime so it can be added to (or updated on) the MAL list.
     */
    suspend fun resolveMalEntryIds(): MalEntryIds? {
        cachedMalEntryIds?.let { return it }
        val anime = anime ?: return null
        val tracks = try {
            getTracks.await(anime.id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to load trackers of ${anime.title}" }
            emptyList()
        }
        val ids = MalEntryResolver.forAnime(anime.id, anime.title, tracks)
        if (ids != null) {
            cachedMalEntryIds = ids
        }
        return ids
    }

    /**
     * Looks the MyAnimeList entry of this anime up.
     *
     * When the app can't tell the entries apart, the user picks the entry - opening a wrong
     * anime is worse than asking.
     */
    suspend fun lookupMalEntry(): MalEntryLookup {
        cachedMalEntryIds?.let { return MalEntryLookup.Found(it) }
        val anime = anime ?: return MalEntryLookup.Uncertain
        val tracks = try {
            getTracks.await(anime.id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to load trackers of ${anime.title}" }
            emptyList()
        }
        val lookup = MalEntryResolver.lookupAnime(anime.id, anime.title, tracks)
        if (lookup is MalEntryLookup.Found) {
            cachedMalEntryIds = lookup.ids
        }
        return lookup
    }

    /**
     * Keeps the watched episodes of the anime in sync with its MyAnimeList entry.
     *
     * The anime is linked with the entry when it is on the user's list, then the progress of
     * the list is applied to the local episodes, so an episode that was watched elsewhere is
     * ticked without the user having to mark it again.
     */
    private suspend fun syncMalProgress(anime: Anime) {
        val tracker = Injekt.get<TrackerManager>().myAnimeList
        if (!tracker.isLoggedIn) return

        val tracks = try {
            getTracks.await(anime.id)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to load trackers of ${anime.title}" }
            emptyList()
        }

        // The entry is looked up again every now and then, so a link that points at the wrong
        // entry is corrected instead of being used forever.
        val now = System.currentTimeMillis()
        if (now - lastMalLinkAttempt >= MAL_LINK_RETRY_INTERVAL) {
            lastMalLinkAttempt = now
            linkWithMalEntry(anime, tracks)
            return
        }

        // Apply the progress of the entry that is linked already
        val track = tracks.firstOrNull { it.trackerId == MalProgressSync.MAL_TRACKER_ID } ?: return
        val markAll = track.status == MyAnimeList.COMPLETED
        val progress = MalProgressSync.applyProgressForAnime(anime.id, track.remoteId, markAll)
        if (progress != null) {
            MalProgressSync.logEvent("Applied MAL progress $progress to anime ${anime.id}")
        }
    }

    /**
     * Looks the anime up on MyAnimeList and links it, when it is on the user's list.
     */
    private suspend fun linkWithMalEntry(anime: Anime, tracks: List<AnimeTrack>) {
        val tracker = Injekt.get<TrackerManager>().myAnimeList
        val ids = MalEntryResolver.forAnime(anime.id, anime.title, tracks)
        val malId = ids?.malId
        if (malId == null) {
            MalProgressSync.logEvent("No MyAnimeList entry for '${anime.title}' (id ${anime.id})")
            return
        }

        val status = try {
            tracker.findListStatus(malId)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, e) { "Failed to load the MyAnimeList status of ${anime.title}" }
            null
        }
        if (status == null) {
            MalProgressSync.logEvent("MyAnimeList entry $malId is not on the user's list")
            return
        }

        MalProgressSync.linkAnime(
            animeId = anime.id,
            animeTitle = anime.title,
            malId = malId,
            episodesWatched = status.numEpisodesWatched.toInt(),
        )
    }

    fun showImagesDialog() {
        updateSuccessState { it.copy(dialog = Dialog.FullImages) }
    }

    fun showAnimeSkipIntroDialog() {
        updateSuccessState { it.copy(dialog = Dialog.ChangeAnimeSkipIntro) }
    }

    private fun showQualitiesDialog(episode: Episode) {
        updateSuccessState { it.copy(dialog = Dialog.ShowQualities(episode, it.anime, it.source)) }
    }

    companion object {
        /**
         * How long to wait before the MyAnimeList entry is looked up again.
         */
        private const val MAL_LINK_RETRY_INTERVAL = 5_000L
    }

    sealed interface State {
        @Immutable
        data object Loading : State

        @Immutable
        data class Success(
            val anime: Anime,
            val source: AnimeSource,
            val isFromSource: Boolean,
            val episodes: List<EpisodeList.Item>,
            val seasons: List<AnimeSeasonItem>,
            val trackingCount: Int = 0,
            val hasLoggedInTrackers: Boolean = false,
            val relatedAnime: List<AnimeRelationGroup> = emptyList(),
            val isLoadingRelatedAnime: Boolean = false,
            val isRefreshingData: Boolean = false,
            val dialog: Dialog? = null,
            val trackItems: List<AnimeTrackItem> = emptyList(),
            val nextAiringEpisode: Pair<Int, Long> = Pair(
                anime.nextEpisodeToAir,
                anime.nextEpisodeAiringAt,
            ),
        ) : State {

            val processedSeasons by lazy {
                seasons.applySeasonFilters(anime).toList()
            }

            val processedEpisodes by lazy {
                episodes.applyFilters(anime).toList()
            }

            val episodeListItems by lazy {
                processedEpisodes.insertSeparators { before, after ->
                    val (lowerEpisode, higherEpisode) = if (anime.sortDescending()) {
                        after to before
                    } else {
                        before to after
                    }
                    if (higherEpisode == null) return@insertSeparators null

                    if (lowerEpisode == null) {
                        floor(higherEpisode.episode.episodeNumber)
                            .toInt()
                            .minus(1)
                            .coerceAtLeast(0)
                    } else {
                        calculateEpisodeGap(higherEpisode.episode, lowerEpisode.episode)
                    }
                        .takeIf { it > 0 }
                        ?.let { missingCount ->
                            EpisodeList.MissingCount(
                                id = "${lowerEpisode?.id}-${higherEpisode.id}",
                                count = missingCount,
                            )
                        }
                }
            }

            val trackingAvailable: Boolean
                get() = trackItems.isNotEmpty()

            val airingEpisodeNumber: Double
                get() = nextAiringEpisode.first.toDouble()

            val airingTime: Long
                get() = nextAiringEpisode.second.times(1000L).minus(
                    Calendar.getInstance().timeInMillis,
                )
            val showPreviews: Boolean
                get() = anime.showPreviews()

            val showSummaries: Boolean
                get() = anime.showSummaries()

            /**
             * Applies the view filters to the list of episodes obtained from the database.
             * @return an observable of the list of episodes filtered and sorted.
             */
            private fun List<EpisodeList.Item>.applyFilters(anime: Anime): Sequence<EpisodeList.Item> {
                val isLocalAnime = anime.isLocal()
                val unseenFilter = anime.unseenFilter
                val downloadedFilter = anime.downloadedFilter
                val bookmarkedFilter = anime.bookmarkedFilter
                val fillermarkedFilter = anime.fillermarkedFilter
                return asSequence()
                    .filter { (episode) -> applyFilter(unseenFilter) { !episode.seen } }
                    .filter { (episode) -> applyFilter(bookmarkedFilter) { episode.bookmark } }
                    .filter { (episode) -> applyFilter(fillermarkedFilter) { episode.fillermark } }
                    .filter { applyFilter(downloadedFilter) { it.isDownloaded || isLocalAnime } }
                    .sortedWith { (episode1), (episode2) ->
                        getEpisodeSort(anime).invoke(
                            episode1,
                            episode2,
                        )
                    }
            }

            private fun List<AnimeSeasonItem>.applySeasonFilters(anime: Anime): Sequence<AnimeSeasonItem> {
                val unseenFilter = anime.seasonUnseenFilter
                val downloadedFilter = anime.seasonDownloadedFilter
                val startedFilter = anime.seasonStartedFilter
                val completedFilter = anime.seasonCompletedFilter
                val bookmarkedFilter = anime.seasonBookmarkedFilter
                val fillermarkedFilter = anime.seasonFillermarkedFilter

                val comparator = getSeasonSortComparator(anime)
                    .let { if (anime.seasonSortDescending()) it.reversed() else it }
                    .thenComparator(seasonSortAlphabetically)

                return asSequence()
                    .filter { (season) -> applyFilter(unseenFilter) { !season.seen } }
                    .filter { (season) -> applyFilter(startedFilter) { season.hasStarted } }
                    .filter { (season) ->
                        applyFilter(completedFilter) { season.anime.status.toInt() == SAnime.COMPLETED }
                    }
                    .filter { (season) -> applyFilter(bookmarkedFilter) { season.hasBookmarks } }
                    .filter { (season) -> applyFilter(fillermarkedFilter) { season.hasFillermarks } }
                    .filter { applyFilter(downloadedFilter) { it.downloadCount > 0 || it.seasonAnime.anime.isLocal() } }
                    .sortedWith(compareBy(comparator) { it.seasonAnime })
                    .map {
                        val itemAnime = it.seasonAnime.anime
                        AnimeSeasonItem(
                            seasonAnime = it.seasonAnime,
                            downloadCount = if (anime.seasonDownloadedOverlay) it.downloadCount else -1L,
                            unseenCount = if (anime.seasonUnseenOverlay) it.unseenCount else -1L,
                            isLocal = anime.seasonLocalOverlay && it.isLocal,
                            sourceLanguage = if (anime.seasonLangOverlay) it.sourceLanguage else "",
                            showContinueOverlay =
                            anime.seasonContinueOverlay &&
                                it.unseenCount > 0 &&
                                itemAnime.fetchType == FetchType.Episodes,
                        )
                    }
            }
        }
    }
}

@Immutable
sealed class EpisodeList {
    @Immutable
    data class MissingCount(
        val id: String,
        val count: Int,
    ) : EpisodeList()

    @Immutable
    data class Item(
        val episode: Episode,
        val downloadState: AnimeDownload.State,
        val downloadProgress: Int,
        val selected: Boolean = false,
    ) : EpisodeList() {
        val id = episode.id
        val isDownloaded = downloadState == AnimeDownload.State.DOWNLOADED
    }
}
