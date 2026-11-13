package eu.kanade.tachiyomi.ui.mal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.mal.MalEntryPickerScreen
import eu.kanade.presentation.util.Screen

/**
 * Lets the user pick the MyAnimeList entry of an anime.
 *
 * The app never picks an entry on its own - the title of an anime in an extension is not
 * proof of anything. The user always picks the entry here, and the one the app has linked
 * before is shown marked, so a wrong link is visible and can be changed.
 */
class MalEntryPickerScreen(
    private val title: String,
    /**
     * The local anime the entry belongs to, when it was opened from an entry page.
     *
     * Used to link the MyAnimeList entry with the local anime, so the progress of the app
     * and the progress of the list are the same.
     */
    private val localAnimeId: Long? = null,
    private val localMangaId: Long? = null,
    /**
     * The MyAnimeList entry the app has linked with the local anime, when it has one.
     *
     * Shown marked in the picker, so a link that points at the wrong entry is visible and can
     * be changed.
     */
    private val linkedMalId: Int? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val isManga = localMangaId != null

        val screenModel = rememberScreenModel { MalEntryPickerScreenModel(title, isManga, localAnimeId, linkedMalId) }
        val state by screenModel.state.collectAsState()

        MalEntryPickerScreen(
            state = state,
            onQueryChange = screenModel::setQuery,
            onSearch = { screenModel.search() },
            onItemClick = { choice ->
                if (isManga) {
                    navigator.push(
                        MalMangaInfoScreen(
                            anilistId = choice.anilistId,
                            malId = choice.malId,
                            fallbackTitle = choice.title,
                            openEditorOnStart = true,
                            localMangaId = localMangaId,
                        ),
                    )
                } else {
                    navigator.push(
                        MalAnimeInfoScreen(
                            anilistId = choice.anilistId,
                            malId = choice.malId,
                            fallbackTitle = choice.title,
                            openEditorOnStart = true,
                            localAnimeId = localAnimeId,
                        ),
                    )
                }
            },
            onNavigateUp = { navigator.pop() },
        )
    }
}
