package eu.kanade.tachiyomi.ui.mal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.mal.MalAnimeInfoScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.anime.source.globalsearch.GlobalAnimeSearchScreen
import eu.kanade.tachiyomi.util.system.openInBrowser

/**
 * Dedicated anime information page opened from the MAL tab (browse grids and
 * My List rows). Shows data from AniList's API; the Watch button hands off to
 * AniYomi's global anime source search for the installed extensions.
 *
 * The floating edit button reflects the user's MAL list status for this anime
 * (watching = green, completed = blue, plan to watch = grey, on hold = gold,
 * dropped = red) and opens the list editor. Neutral when not on the list.
 */
class MalAnimeInfoScreen(
    private val anilistId: Int?,
    private val malId: Int?,
    private val fallbackTitle: String,
    private val openEditorOnStart: Boolean = false,
    /**
     * The local anime this entry belongs to, when it was opened from an entry page.
     *
     * Used to link the MyAnimeList entry with the local anime, so the watched episodes of
     * both are kept in sync.
     */
    private val localAnimeId: Long? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current

        val screenModel = rememberScreenModel("mal_info_${anilistId}_$malId") {
            MalAnimeInfoScreenModel(anilistId, malId, localAnimeId = localAnimeId, localAnimeTitle = fallbackTitle)
        }
        val state by screenModel.state.collectAsState()

        MalAnimeInfoScreen(
            state = state,
            events = screenModel.events,
            fallbackTitle = fallbackTitle,
            navigateUp = { navigator.pop() },
            onWatch = { title ->
                navigator.push(GlobalAnimeSearchScreen(title))
            },
            onOpenWeb = { url ->
                context.openInBrowser(url)
            },
            onRetry = screenModel::load,
            onSaveEntry = screenModel::saveListEntry,
            onRemoveEntry = screenModel::removeFromList,
            openEditorOnStart = openEditorOnStart,
        )
    }
}
