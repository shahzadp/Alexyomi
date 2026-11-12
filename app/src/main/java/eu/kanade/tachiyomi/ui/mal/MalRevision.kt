package eu.kanade.tachiyomi.ui.mal

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide revision counter for anything derived from the user's MAL
 * account. Any screen that writes to MyAnimeList bumps it, and any screen
 * that reads from it collects it — so e.g. editing an entry on the anime
 * info page makes the My List tab reflect it without leaving/re-entering.
 */
object MalRevision {
    val revision: MutableStateFlow<Int> = MutableStateFlow(0)

    /**
     * Fires whenever the on-disk My List cache is written, i.e. after every
     * fetch or edit. Screens derived from the cache (e.g. the profile stats)
     * collect it to re-read without polling.
     */
    val statsDirty: MutableStateFlow<Int> = MutableStateFlow(0)

    fun bump() {
        revision.update { it + 1 }
    }

    fun markStatsDirty() {
        statsDirty.update { it + 1 }
    }
}
