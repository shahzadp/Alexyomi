package eu.kanade.tachiyomi.ui.mal

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.anilist.AnilistAnimeDetails
import eu.kanade.tachiyomi.data.track.anilist.AnilistBrowseApi
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALListMangaItemStatus
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MalMangaInfoScreenModel(
    private val anilistId: Int?,
    private val malId: Int?,
    /**
     * The local manga this entry belongs to, when it was opened from an entry page.
     *
     * When set, the entry is linked with the local manga so the read chapters of both are
     * kept in sync.
     */
    private val localMangaId: Long? = null,
    private val localMangaTitle: String? = null,
    private val anilistApi: AnilistBrowseApi = AnilistBrowseApi(Injekt.get<NetworkHelper>().client),
    private val tracker: MyAnimeList = Injekt.get<TrackerManager>().myAnimeList,
) : StateScreenModel<MalMangaInfoScreenModel.State>(State(signedIn = tracker.isLoggedIn)) {

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events: Flow<Event> = _events.receiveAsFlow()

    private var lastRevision = MalRevision.revision.value

    init {
        load()
        screenModelScope.launchIO {
            MalRevision.revision.collectLatest { revision ->
                if (revision != lastRevision) {
                    lastRevision = revision
                    refreshListStatus(mutableState.value.details?.malId)
                }
            }
        }
    }

    fun load() {
        screenModelScope.launchIO {
            mutableState.update { it.copy(loading = true, error = false) }
            try {
                val details = anilistApi.mangaDetails(anilistId, malId)
                mutableState.update { it.copy(loading = false, details = details) }
                refreshListStatus(details.malId)
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, error = true) }
            }
        }
    }

    private suspend fun refreshListStatus(statusMalId: Int?) {
        val id = statusMalId ?: malId ?: return
        if (!tracker.isLoggedIn) {
            mutableState.update { it.copy(signedIn = false, listStatus = null) }
            return
        }
        val status = tracker.findMangaListStatus(id)
        mutableState.update { it.copy(signedIn = true, listStatus = status) }
    }

    fun saveListEntry(
        status: String,
        score: Int,
        chaptersRead: Int,
    ) {
        val details = mutableState.value.details
        val id = details?.malId ?: malId
        if (id == null) {
            screenModelScope.launchIO { _events.send(Event.UpdateFailed) }
            return
        }
        screenModelScope.launchIO {
            try {
                tracker.updateMangaListStatus(
                    mangaId = id,
                    status = status.ifBlank { "plan_to_read" },
                    score = score,
                    numChaptersRead = chaptersRead,
                )
                MalRevision.bump()
                refreshListStatus(id)
                linkLocalManga(id, chaptersRead)
                _events.send(Event.Updated)
            } catch (e: Exception) {
                _events.send(Event.UpdateFailed)
            }
        }
    }

    /**
     * Links the entry with the local manga it was opened from, so the read progress of the
     * list and the local chapters stay in sync.
     */
    private suspend fun linkLocalManga(malId: Int, chaptersRead: Int) {
        val mangaId = localMangaId ?: return
        val title = localMangaTitle
            ?: mutableState.value.details?.displayTitle
            ?: return
        MalProgressSync.linkManga(
            mangaId = mangaId,
            mangaTitle = title,
            malId = malId,
            chaptersRead = chaptersRead,
        )
    }

    fun removeFromList() {
        val details = mutableState.value.details
        val id = details?.malId ?: malId
        if (id == null) {
            screenModelScope.launchIO { _events.send(Event.UpdateFailed) }
            return
        }
        screenModelScope.launchIO {
            try {
                tracker.deleteMangaListEntry(id)
                mutableState.update { it.copy(listStatus = null) }
                MalRevision.bump()
                _events.send(Event.Removed)
            } catch (e: Exception) {
                _events.send(Event.UpdateFailed)
            }
        }
    }

    sealed interface Event {
        data object Updated : Event
        data object UpdateFailed : Event
        data object Removed : Event
    }

    data class State(
        val loading: Boolean = true,
        val error: Boolean = false,
        val details: AnilistAnimeDetails? = null,
        val signedIn: Boolean = false,
        val listStatus: MALListMangaItemStatus? = null,
    )
}
