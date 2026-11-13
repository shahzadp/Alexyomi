package eu.kanade.tachiyomi.ui.mal

/**
 * Ids needed to open a MyAnimeList entry: the AniList id and/or the MAL id.
 */
data class MalEntryIds(
    val anilistId: Int?,
    val malId: Int?,
)

/**
 * Whether the app may let the user add the anime to (or update it on) MyAnimeList.
 *
 * The app never guesses an entry. It opens one on its own only when the local anime and the
 * entry are the same anime - the same title, letter for letter, the seasons included. Every
 * other case shows the picker.
 */
sealed interface MalEntryLookup {

    /** The entry is the same anime - it may be opened and linked on its own. */
    data class Found(val ids: MalEntryIds) : MalEntryLookup

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
