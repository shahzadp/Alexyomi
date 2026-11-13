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

## 2. The AniList ↔ MyAnimeList bridge misses niche anime

The bridge used to look the MAL id up through AniList only, which is why obscure and adult
entries were never found: AniList knows fewer anime than MAL and its search skips adult
content.

It now walks several sources (`MalEntryResolver`):

1. the tracker that is already linked to the anime
2. the user's own MAL list — matched by a normalised title comparison (`MalTitleMatcher`)
3. MyAnimeList's own search
4. AniList's search, as the last resort

The entry page also falls back to MyAnimeList for the details when AniList doesn't know the
entry (`MalAnimeInfoScreenModel.loadDetails`).

**Done:** the guesswork is gone. The comparison compares the season numbers of the titles, so
"X 3rd Season" never matches "X Season 4". When nothing fits the title or several entries fit
it equally well, the app opens the entry picker (`MalEntryPickerScreen`) and the user picks
the entry — the app never opens an entry that could be a different anime.
