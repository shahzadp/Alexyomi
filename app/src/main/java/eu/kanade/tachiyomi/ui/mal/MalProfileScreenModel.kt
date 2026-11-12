package eu.kanade.tachiyomi.ui.mal

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeList
import eu.kanade.tachiyomi.data.track.myanimelist.dto.MALUserProfile
import eu.kanade.tachiyomi.util.system.isConnectedToWifi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MalProfileScreenModel(
    private val tracker: MyAnimeList = Injekt.get<TrackerManager>().myAnimeList,
) : StateScreenModel<MalProfileScreenModel.State>(State()) {

    // Signature of the profile currently shown / cached, so the cheap background
    // check can skip pushing an identical network response into the UI.
    private var lastProfileSignature: String? = null

    // Minimum time the pull-to-refresh indicator stays up, so the gesture
    // never feels broken even when there is nothing to sync.
    private companion object {
        const val MIN_REFRESH_INDICATOR_MS = 750L
    }

    init {
        screenModelScope.launchIO {
            tracker.isLoggedInFlow.collect { signedIn ->
                if (signedIn) {
                    restoreFromCacheOrLoad()
                    loadStats()
                } else {
                    MalProfileStore.clear()
                    lastProfileSignature = null
                    mutableState.update {
                        it.copy(
                            loading = false,
                            reloading = false,
                            signedIn = false,
                            userName = "",
                            avatarUrl = null,
                            joinedAt = null,
                            birthday = null,
                            gender = null,
                            location = null,
                            animeStats = null,
                            mangaStats = null,
                        )
                    }
                }
            }
        }
        screenModelScope.launchIO {
            // Re-read the list cache whenever My List writes it (full sync or edit),
            // so the numbers stay in sync without any manual refresh.
            MalRevision.statsDirty.collectLatest {
                if (tracker.isLoggedIn) {
                    loadStats()
                }
            }
        }
    }

    private fun restoreFromCacheOrLoad() {
        screenModelScope.launchIO {
            val cached = MalProfileStore.read()
            if (cached != null) {
                // Show the cached profile instantly; a cheap background pass
                // refreshes it only when something actually changed.
                applyProfile(cached, loading = false)
                maybeRefreshInBackground()
            } else {
                loadIdentity()
            }
        }
    }

    /**
     * Pull-to-refresh. Always re-fetches and, after applying whatever came back,
     * holds the indicator for a minimum spin time — even when nothing changed —
     * because that's the behaviour people expect from pull-to-refresh.
     */
    fun refresh() {
        screenModelScope.launchIO {
            if (!tracker.isLoggedIn) return@launchIO
            val startedAt = System.currentTimeMillis()
            mutableState.update { it.copy(reloading = true) }

            val fresh = tracker.getMyProfile()
            if (fresh != null) {
                applyProfile(fresh, loading = false)
                MalProfileStore.write(fresh)
            }
            loadStats()

            val elapsed = System.currentTimeMillis() - startedAt
            kotlinx.coroutines.delay(MIN_REFRESH_INDICATOR_MS - elapsed.coerceAtMost(MIN_REFRESH_INDICATOR_MS))
            mutableState.update { it.copy(reloading = false) }
        }
    }

    private suspend fun loadIdentity() {
        mutableState.update { it.copy(loading = true, signedIn = true) }
        val profile = tracker.getMyProfile()
        if (profile != null) {
            applyProfile(profile, loading = false)
            MalProfileStore.write(profile)
        } else {
            // Keep whatever we can: username from prefs
            mutableState.update {
                it.copy(
                    loading = false,
                    signedIn = true,
                    userName = tracker.getUsername(),
                )
            }
        }
    }

    /**
     * Cheap background pass: re-downloads the profile and only applies it (and
     * rewrites the cache) when something is actually different. Wi-Fi only,
     * same policy as the list's background refresh.
     */
    private suspend fun maybeRefreshInBackground() {
        if (!Injekt.get<android.app.Application>().isConnectedToWifi()) return
        val fresh = tracker.getMyProfile() ?: return
        if (fresh.signature() == lastProfileSignature) return
        applyProfile(fresh, loading = false)
        MalProfileStore.write(fresh)
    }

    private fun applyProfile(profile: MALUserProfile, loading: Boolean) {
        lastProfileSignature = profile.signature()
        mutableState.update {
            it.copy(
                loading = loading,
                reloading = false,
                signedIn = true,
                userName = profile.name.takeIf { name -> name.isNotBlank() } ?: tracker.getUsername(),
                avatarUrl = profile.picture.takeIf { picture -> picture.isNotBlank() },
                joinedAt = profile.joinedAt?.takeIf { joinedAt -> joinedAt.isNotBlank() },
                birthday = profile.birthday?.takeIf { birthday -> birthday.isNotBlank() },
                gender = profile.gender?.takeIf { gender -> gender.isNotBlank() },
                location = profile.location?.takeIf { location -> location.isNotBlank() },
            )
        }
    }

    private fun MALUserProfile.signature(): String = listOf(
        name,
        picture,
        joinedAt.orEmpty(),
        birthday.orEmpty(),
        gender.orEmpty(),
        location.orEmpty(),
        isSupporter.toString(),
    ).joinToString("|")

    private suspend fun loadStats() {
        if (!tracker.isLoggedIn) return
        val animeEntries = MalListStore.read(MalListScreenModel.MediaType.ANIME).orEmpty()
        val mangaEntries = MalListStore.read(MalListScreenModel.MediaType.MANGA).orEmpty()
        mutableState.update {
            it.copy(
                loading = false,
                animeStats = computeStats(animeEntries, isAnime = true),
                mangaStats = computeStats(mangaEntries, isAnime = false),
            )
        }
    }

    fun setMediaType(mediaType: MalListScreenModel.MediaType) {
        mutableState.update { it.copy(mediaType = mediaType) }
    }

    private fun computeStats(
        entries: List<MalListScreenModel.MalListEntry>,
        isAnime: Boolean,
    ): MediaStats {
        fun isStatus(entry: MalListScreenModel.MalListEntry, vararg statuses: String): Boolean =
            entry.status.lowercase() in statuses

        val watching = entries.count {
            if (isAnime) {
                isStatus(it, "watching", "rewatching")
            } else {
                isStatus(it, "reading", "rereading")
            }
        }
        val planned = entries.count {
            if (isAnime) {
                isStatus(it, "plan_to_watch")
            } else {
                isStatus(it, "plan_to_read")
            }
        }
        val scored = entries.mapNotNull { it.score.takeIf { score -> score > 0 } }
        return MediaStats(
            totalEntries = entries.size,
            watching = watching,
            planned = planned,
            completed = entries.count { isStatus(it, "completed") },
            onHold = entries.count { isStatus(it, "on_hold") },
            dropped = entries.count { isStatus(it, "dropped") },
            progressSum = entries.sumOf { it.progress.coerceAtLeast(0) },
            avgScore = if (scored.isNotEmpty()) scored.average() else null,
        )
    }

    data class State(
        val loading: Boolean = true,
        val reloading: Boolean = false,
        val signedIn: Boolean = false,
        val userName: String = "",
        val avatarUrl: String? = null,
        val joinedAt: String? = null,
        val birthday: String? = null,
        val gender: String? = null,
        val location: String? = null,
        val mediaType: MalListScreenModel.MediaType = MalListScreenModel.MediaType.ANIME,
        val animeStats: MediaStats? = null,
        val mangaStats: MediaStats? = null,
    )

    data class MediaStats(
        val totalEntries: Int,
        val watching: Int,
        val planned: Int,
        val completed: Int,
        val onHold: Int,
        val dropped: Int,
        val progressSum: Int,
        val avgScore: Double?,
    )
}
