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
 * The app looks the entry up on its own, but it can't always tell the entries apart - the
 * seasons of a show look almost the same and some extensions use other titles. Instead of
 * opening an entry that could be a different anime, the user picks the right one here.
 */
class MalEntryPickerScreen(
    private val title: String,
    /**
     * The local anime the entry belongs to, when it was opened from an entry page.
     *
     * Used to link the MyAnimeList entry with the local anime, so the watched episodes of
     * both are kept in sync.
     */
    private val localAnimeId: Long? = null,
    private val localMangaId: Long? = null,
) : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val isManga = localMangaId != null

        val screenModel = rememberScreenModel { MalEntryPickerScreenModel(title, isManga) }
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
