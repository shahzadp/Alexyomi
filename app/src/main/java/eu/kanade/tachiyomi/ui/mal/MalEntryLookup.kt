package eu.kanade.tachiyomi.ui.mal

/**
 * Whether the app may let the user add the anime to (or update it on) MyAnimeList.
 *
 * The app never picks the entry on its own. The title of an anime in an extension is not
 * proof of anything - extensions carry other titles, decorations, and sometimes a completely
 * different show than the one the user looked at. So the app shows the entries and the user
 * picks the right one, every time.
 */
sealed interface MalEntryLookup {

    /** The user picks the entry in the picker.
     *
     * [linkedMalId] is the MyAnimeList entry the app has linked with the anime, when it has
     * one. It is shown marked in the picker, so a link that points at the wrong entry is
     * visible and can be changed.
     */
    data class Pick(val linkedMalId: Int?) : MalEntryLookup

    /** The user isn't signed in to MyAnimeList. */
    data object NotSignedIn : MalEntryLookup
}
