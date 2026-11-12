package eu.kanade.tachiyomi.ui.mal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.mal.MalMangaInfoScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.ui.browse.manga.source.globalsearch.GlobalMangaSearchScreen
import eu.kanade.tachiyomi.util.system.openInBrowser

/**
 * Dedicated manga information page opened from the MAL tab (My List rows).
 * Shows data from AniList's API; the Read button hands off to AniYomi's
 * global manga source search for the installed extensions — mirroring the
 * anime flow and the Watch button.
 *
 * The floating edit button reflects the user's MAL manga list status
 * (reading = green, completed = blue, plan to read = grey, on hold = gold,
 * dropped = red) and opens the list editor. Neutral when not on the list.
 */
class MalMangaInfoScreen(
    private val anilistId: Int?,
    private val malId: Int?,
    private val fallbackTitle: String,
    private val openEditorOnStart: Boolean = false,
    /**
     * The local manga this entry belongs to, when it was opened from an entry page.
     *
     * Used to link the MyAnimeList entry with the local manga, so the read chapters of both
     * are kept in sync.
     */
    private val localMangaId: Long? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current

        val screenModel = rememberScreenModel("mal_manga_info_${anilistId}_$malId") {
            MalMangaInfoScreenModel(
                anilistId,
                malId,
                localMangaId = localMangaId,
                localMangaTitle = fallbackTitle,
            )
        }
        val state by screenModel.state.collectAsState()

        MalMangaInfoScreen(
            state = state,
            events = screenModel.events,
            fallbackTitle = fallbackTitle,
            navigateUp = { navigator.pop() },
            onRead = { title ->
                navigator.push(GlobalMangaSearchScreen(title))
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
