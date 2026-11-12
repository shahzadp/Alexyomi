package eu.kanade.domain.ui.model

import dev.icerock.moko.resources.StringResource
import eu.kanade.presentation.util.Tab
import eu.kanade.tachiyomi.ui.browse.BrowseTab
import eu.kanade.tachiyomi.ui.history.HistoriesTab
import eu.kanade.tachiyomi.ui.mal.MalTab
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR

enum class StartScreen(val titleRes: StringResource, val tab: Tab) {
    MAL(AYMR.strings.label_mal, MalTab),
    HISTORY(MR.strings.label_recent_manga, HistoriesTab),
    BROWSE(MR.strings.browse, BrowseTab),
}
