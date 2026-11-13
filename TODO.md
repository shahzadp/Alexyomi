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

## 3. The AniList ↔ MyAnimeList bridge misses niche anime

The bridge used to look the MAL id up through AniList only, which is why obscure and adult
entries were never found: AniList knows fewer anime than MAL and its search skips adult
content.

It now walks several sources (`MalEntryResolver`):

1. the tracker that is already linked to the anime
2. the user's own MAL list — matched by an exact title comparison (`MalTitleMatcher`)
3. AniList's search, whose romaji title bridges the language gap
4. MyAnimeList's own search, which knows the entries AniList doesn't have

The entry page also falls back to MyAnimeList for the details when AniList doesn't know the
entry (`MalAnimeInfoScreenModel.loadDetails`).

**Done:** the guesswork is gone. Only an entry whose title is exactly the title of the anime
is opened on its own - including the romaji title of AniList, which is what the extensions
usually show. Everything else opens the entry picker (`MalEntryPickerScreen`), where the
entries of the list of the user, of MyAnimeList and of AniList are ranked - the number of
episodes tells the seasons apart, and the entries that are already on the list are marked.
