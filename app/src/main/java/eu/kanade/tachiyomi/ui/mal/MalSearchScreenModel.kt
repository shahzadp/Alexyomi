package eu.kanade.tachiyomi.ui.mal

import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.tachiyomi.data.track.anilist.AnilistBrowseApi
import eu.kanade.tachiyomi.data.track.anilist.AnilistBrowseItem
import eu.kanade.tachiyomi.network.NetworkHelper
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class MalSearchScreenModel(
    private val anilistApi: AnilistBrowseApi = AnilistBrowseApi(Injekt.get<NetworkHelper>().client),
) : StateScreenModel<MalSearchScreenModel.State>(State()) {

    private var lastQuery = ""

    init {
        screenModelScope.launchIO {
            val history = MalSearchHistoryStore.read()
            mutableState.update { it.copy(history = history.toImmutableList()) }
        }
    }

    fun setQuery(query: String) {
        mutableState.update { it.copy(query = query) }
    }

    /**
     * Clears the query and the results, back to the empty state.
     */
    fun clear() {
        lastQuery = ""
        mutableState.update {
            it.copy(
                query = "",
                items = persistentListOf(),
                searched = false,
                error = false,
                loading = false,
            )
        }
    }

    /**
     * Runs the search for the given query (used by the history shortcuts).
     */
    fun search(query: String = mutableState.value.query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        if (trimmed != mutableState.value.query) {
            mutableState.update { it.copy(query = trimmed) }
        }
        if (trimmed == lastQuery && mutableState.value.searched) return
        lastQuery = trimmed
        screenModelScope.launchIO {
            mutableState.update { it.copy(loading = true, error = false) }
            try {
                val result = anilistApi.searchAnime(trimmed)
                MalSearchHistoryStore.add(trimmed)
                val history = MalSearchHistoryStore.read()
                mutableState.update {
                    it.copy(
                        loading = false,
                        items = result.items.toImmutableList(),
                        searched = true,
                        history = history.toImmutableList(),
                    )
                }
            } catch (e: Exception) {
                mutableState.update { it.copy(loading = false, error = true) }
            }
        }
    }

    /**
     * Removes a single entry from the search history.
     */
    fun removeHistory(query: String) {
        screenModelScope.launchIO {
            MalSearchHistoryStore.remove(query)
            val history = MalSearchHistoryStore.read()
            mutableState.update { it.copy(history = history.toImmutableList()) }
        }
    }

    /**
     * Removes every entry from the search history.
     */
    fun clearHistory() {
        screenModelScope.launchIO {
            MalSearchHistoryStore.clear()
            mutableState.update { it.copy(history = persistentListOf()) }
        }
    }

    data class State(
        val query: String = "",
        val loading: Boolean = false,
        val error: Boolean = false,
        val searched: Boolean = false,
        val items: ImmutableList<AnilistBrowseItem> = persistentListOf(),
        val history: ImmutableList<String> = persistentListOf(),
    )
}
