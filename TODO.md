# Open tasks

Things that are known, agreed on and not done yet.

## 1. The MyAnimeList consent page still says "Aniyomi"

The app signs in with AniYomi's public MAL client id (`686b980ff4240fccce7f6a654cea07ce`), so
the consent page on myanimelist.net shows the name **"Aniyomi"** and the app's icon.

Fixing this needs a **self-registered MAL client**: create an app on
<https://myanimelist.net/apiconfig>, set the redirect URI to `aniyomi://myanimelist-auth`, then
swap the id in `MyAnimeListApi.kt`:

```kotlin
internal const val CLIENT_ID = "686b980ff4240fccce7f6a654cea07ce" // -> the new client id
```

That is the only line that has to change — the header and the OAuth flow use the constant.

Note: the redirect URI is declared in `app/src/main/AndroidManifest.xml` for
`TrackLoginActivity`. It has to stay in sync with the value registered at MAL.

## 2. Nothing is ticked in the app from MyAnimeList anymore

The auto-tick (the progress of the list applied to the local episodes) was removed on purpose.
It needed a title match between the list and every local anime, and the seasons of a show
look almost the same - a wrong match ticks the wrong episodes.

Only the other direction is automatic: watching an episode here updates the counter of the
list entry the user picked. If the user wants the episodes they watched elsewhere ticked as
well, they mark them in the app.

## 3. The entry of MyAnimeList is linked automatically, letter for letter only

"Add to MAL" links on its own only when the local title and an entry match letter for letter,
**seasons included**. "One Piece 3rd Season" only links to the season 3 entry; other seasons
stay unlinked. The linking happens against:

1. the entries of the user's own list (known offline, carries the status and progress)
2. MyAnimeList's own search - the entries that are not on the list
3. AniList's search, whose romaji title bridges the language gap

Every other case - a linked entry that doesn't match letter for letter, nothing matching -
opens the picker. The picker shows the linked entry (marked **Linked**), the user's list
entries (marked **On your list**) and the search results ranked by title and by the number of
episodes. The entry the user picks is the one that gets linked.

Strict matching means an anime is never linked to an entry that merely leans the same way -
"One Piece" (the extension title that drops the season) doesn't link to a random season of
the series; the user picks it once, and it stays linked.
