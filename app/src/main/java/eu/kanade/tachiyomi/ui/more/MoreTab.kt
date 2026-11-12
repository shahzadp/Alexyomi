package eu.kanade.tachiyomi.ui.more

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.navigator.currentOrThrow
import cafe.adriel.voyager.navigator.tab.TabOptions
import eu.kanade.presentation.more.MoreScreen
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.setting.PlayerSettingsScreen
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.i18n.stringResource

data object MoreTab : Tab {

    override val options: TabOptions
        @Composable
        get() {
            return TabOptions(
                index = 3u,
                title = stringResource(MR.strings.label_settings),
                icon = painterResource(R.drawable.ic_settings_24dp),
            )
        }

    override suspend fun onReselect(navigator: Navigator) {
        navigator.push(SettingsScreen())
    }

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        MoreScreen(
            onClickHistory = { navigator.push(HistoriesTab) },
            onClickDataAndStorage = { navigator.push(SettingsScreen(SettingsScreen.Destination.DataAndStorage)) },
            onClickPlayerSettings = { navigator.push(PlayerSettingsScreen(mainSettings = false)) },
            onClickSettings = { navigator.push(SettingsScreen()) },
            onClickAbout = { navigator.push(SettingsScreen(SettingsScreen.Destination.About)) },
        )
    }
}
