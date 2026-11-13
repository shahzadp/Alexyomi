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

## 3. The app never picks an entry on its own

The "Add to MAL" button never opens an entry on its own anymore. The title of an anime in an
extension is not proof of anything - extensions carry other titles, decorations, and sometimes
a completely different show than the one the user looked at. So every tap shows the picker
(`MalEntryPickerScreen`), where the user picks the entry:

- the entry the app has linked before is shown first and marked **Linked** - a wrong link from
  before this change is visible and can be changed to any entry on MyAnimeList
- the entries of the list of the user come next and are marked **On your list**
- everything else comes from MyAnimeList and AniList, ranked by the title and by the number of
  episodes - the seasons of a show have nearly the same title but not the same episode count

The entry the user picks is the one that gets linked, so the progress of the app and the
progress of the list are the same. Nothing else is opened, ever.
