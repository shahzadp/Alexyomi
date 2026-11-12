package eu.kanade.presentation.more.settings.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import eu.kanade.presentation.more.settings.Preference
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MARK_DUPLICATE_CHAPTER_READ_EXISTING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MARK_DUPLICATE_CHAPTER_READ_NEW
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MARK_DUPLICATE_EPISODE_SEEN_EXISTING
import tachiyomi.domain.library.service.LibraryPreferences.Companion.MARK_DUPLICATE_EPISODE_SEEN_NEW
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

object SettingsLibraryScreen : SearchableSettings {

    @Composable
    @ReadOnlyComposable
    override fun getTitleRes() = AYMR.strings.pref_behavior_screen

    @Composable
    override fun getPreferences(): List<Preference> {
        val libraryPreferences = Injekt.get<LibraryPreferences>()

        return listOf(
            getBehaviorGroup(libraryPreferences),
            getAnimeBehaviorGroup(libraryPreferences),
        )
    }

    @Composable
    private fun getBehaviorGroup(
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_behavior),            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.swipeChapterStartAction(),
                    entries = persistentMapOf(
                        LibraryPreferences.ChapterSwipeAction.Disabled to
                            stringResource(MR.strings.disabled),
                        LibraryPreferences.ChapterSwipeAction.ToggleBookmark to
                            stringResource(MR.strings.action_bookmark),
                        LibraryPreferences.ChapterSwipeAction.ToggleRead to
                            stringResource(MR.strings.action_mark_as_read),
                        LibraryPreferences.ChapterSwipeAction.Download to
                            stringResource(MR.strings.action_download),
                    ),
                    title = stringResource(MR.strings.pref_chapter_swipe_start),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.swipeChapterEndAction(),
                    entries = persistentMapOf(
                        LibraryPreferences.ChapterSwipeAction.Disabled to
                            stringResource(MR.strings.disabled),
                        LibraryPreferences.ChapterSwipeAction.ToggleBookmark to
                            stringResource(MR.strings.action_bookmark),
                        LibraryPreferences.ChapterSwipeAction.ToggleRead to
                            stringResource(MR.strings.action_mark_as_read),
                        LibraryPreferences.ChapterSwipeAction.Download to
                            stringResource(MR.strings.action_download),
                    ),
                    title = stringResource(MR.strings.pref_chapter_swipe_end),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = libraryPreferences.markDuplicateReadChapterAsRead(),
                    entries = persistentMapOf(
                        MARK_DUPLICATE_CHAPTER_READ_EXISTING to
                            stringResource(MR.strings.pref_mark_duplicate_read_chapter_read_existing),
                        MARK_DUPLICATE_CHAPTER_READ_NEW to
                            stringResource(MR.strings.pref_mark_duplicate_read_chapter_read_new),
                    ),
                    title = stringResource(MR.strings.pref_mark_duplicate_read_chapter_read),
                ),
            ),
        )
    }

    @Composable
    private fun getAnimeBehaviorGroup(
        libraryPreferences: LibraryPreferences,
    ): Preference.PreferenceGroup {
        return Preference.PreferenceGroup(
            title = stringResource(AYMR.strings.pref_behavior_episode),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.swipeEpisodeStartAction(),
                    entries = persistentMapOf(
                        LibraryPreferences.EpisodeSwipeAction.Disabled to
                            stringResource(MR.strings.disabled),
                        LibraryPreferences.EpisodeSwipeAction.ToggleBookmark to
                            stringResource(AYMR.strings.action_bookmark_episode),
                        LibraryPreferences.EpisodeSwipeAction.ToggleFillermark to
                            stringResource(AYMR.strings.action_fillermark_episode),
                        LibraryPreferences.EpisodeSwipeAction.ToggleSeen to
                            stringResource(AYMR.strings.action_mark_as_seen),
                        LibraryPreferences.EpisodeSwipeAction.Download to
                            stringResource(MR.strings.action_download),
                    ),
                    title = stringResource(AYMR.strings.pref_episode_swipe_start),
                ),
                Preference.PreferenceItem.ListPreference(
                    preference = libraryPreferences.swipeEpisodeEndAction(),
                    entries = persistentMapOf(
                        LibraryPreferences.EpisodeSwipeAction.Disabled to
                            stringResource(MR.strings.disabled),
                        LibraryPreferences.EpisodeSwipeAction.ToggleBookmark to
                            stringResource(AYMR.strings.action_bookmark_episode),
                        LibraryPreferences.EpisodeSwipeAction.ToggleFillermark to
                            stringResource(AYMR.strings.action_fillermark_episode),
                        LibraryPreferences.EpisodeSwipeAction.ToggleSeen to
                            stringResource(AYMR.strings.action_mark_as_seen),
                        LibraryPreferences.EpisodeSwipeAction.Download to
                            stringResource(MR.strings.action_download),
                    ),
                    title = stringResource(AYMR.strings.pref_episode_swipe_end),
                ),
                Preference.PreferenceItem.MultiSelectListPreference(
                    preference = libraryPreferences.markDuplicateSeenEpisodeAsSeen(),
                    entries = persistentMapOf(
                        MARK_DUPLICATE_EPISODE_SEEN_EXISTING to
                            stringResource(AYMR.strings.pref_mark_duplicate_seen_episode_seen_existing),
                        MARK_DUPLICATE_EPISODE_SEEN_NEW to
                            stringResource(AYMR.strings.pref_mark_duplicate_seen_episode_seen_new),
                    ),
                    title = stringResource(AYMR.strings.pref_mark_duplicate_seen_episode_seen),
                ),
            ),
        )
    }
}
