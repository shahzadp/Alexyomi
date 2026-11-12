package eu.kanade.tachiyomi.ui.mal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.mal.MalProfileScreen
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.data.track.myanimelist.MyAnimeListApi
import eu.kanade.tachiyomi.util.system.openInBrowser
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * In-app MAL profile opened from the MAL tab's top bar avatar.
 * Shows the user's identity and stats; the full account page stays
 * reachable via "Open on MyAnimeList".
 */
class MalProfileScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val context = LocalContext.current
        val tracker = remember { Injekt.get<TrackerManager>().myAnimeList }

        val screenModel = rememberScreenModel("mal_profile") { MalProfileScreenModel() }
        val state by screenModel.state.collectAsState()

        MalProfileScreen(
            state = state,
            navigateUp = { navigator.pop() },
            onOpenWeb = {
                val username = tracker.getUsername()
                val url = if (state.signedIn && username.isNotBlank()) {
                    "https://myanimelist.net/profile/$username"
                } else {
                    MyAnimeListApi.authUrl().toString()
                }
                context.openInBrowser(url, forceDefaultBrowser = !state.signedIn)
            },
            onOpenFriends = {
                val username = tracker.getUsername()
                val url = if (state.signedIn && username.isNotBlank()) {
                    "https://myanimelist.net/profile/$username/friends"
                } else {
                    MyAnimeListApi.authUrl().toString()
                }
                context.openInBrowser(url, forceDefaultBrowser = !state.signedIn)
            },
            onMediaTypeChange = screenModel::setMediaType,
            onRefresh = screenModel::refresh,
            onLogOut = {
                tracker.logout()
                screenModel.refresh()
            },
        )
    }
}
