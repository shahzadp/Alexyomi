package eu.kanade.tachiyomi.ui.movies

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.movies.MoviesTabScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

data object MoviesTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            return TabOptions(
                index = 3u,
                title = stringResource(AYMR.strings.label_movies),
                icon = painterResource(R.drawable.ic_movies_24dp),
            )
        }

    @Composable
    override fun Content() {
        MoviesTabScreen()
    }
}
