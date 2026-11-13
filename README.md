# Alexyomi

An Android app for anime and manga that keeps **MyAnimeList** at the centre.

Alexyomi is a focused rework of [AniYomi](https://github.com/aniyomiorg/aniyomi) (itself built
on [Mihon](https://github.com/mihonapp/mihon)): there is no local library, no favourites and
no backup/restore. Your list *is* your MyAnimeList, and everything the app does — the list
you browse, the progress you set, the episodes that get ticked — is read from and written to
MyAnimeList.

## What it does

- **MyAnimeList tab** — your list for anime and manga, with filtering, sorting, search and
  a search history. Status, score and progress are edited right there, or with the *Add to
  MAL* button on any entry page.
- **Browse** — Seasonal, Top and search, backed by AniList's free API.
- **Progress goes to MyAnimeList** — add an anime with the *Add to MAL* button and the
  episodes you watch move the counter of the list. The app never guesses which entry belongs
  to an anime: when it can't tell them apart, it shows the entries and you pick the right
  one. Nothing gets ticked in the app from MyAnimeList, only what you watch here is sent.
- **Downloads tab** — the download queue and everything downloaded in one place, with live
  progress, per-item play/delete and a media player picker.
- **Built-in player** (mpv) with 720p and dubbed audio applied across your installed
  sources, plus an external player (VLC) for downloaded files.
- **Smaller APK** — roughly half the size of upstream, English-only strings, no bundled
  subtitle font.

## Downloads

Releases are on the [Releases page](https://github.com/shahzadp/Alexyomi/releases).

The app is split per CPU architecture — grab the **arm64-v8a** build for a modern phone:

```
app-arm64-v8a-release.apk
```

There is also a universal APK (`app-universal-release.apk`) that runs everywhere but is
considerably larger.

| | |
|---|---|
| minimum Android | 6.0 (API 23) |
| architectures | arm64-v8a, armeabi-v7a, x86, x86_64 |
| size (arm64) | ~53 MB |
| license | Apache License 2.0 |

## Building it

You need JDK 21 and the Android SDK.

```bash
git clone https://github.com/shahzadp/Alexyomi.git
cd Alexyomi

./gradlew :app:assembleRelease
```

The APKs land in `app/build/outputs/apk/release/`.

### Signing

The release build is signed with a key that lives in the project folder but is **never
committed** — the keystore and the credentials file are gitignored. Without them the release
variant simply has no signing config, which is fine for local builds.

See [BUILD.md](BUILD.md) for the details.

## Credits

Alexyomi is built on the shoulders of:

- [AniYomi](https://github.com/aniyomiorg/aniyomi) — the fork this app is based on
- [Mihon](https://github.com/mihonapp/mihon) — the app this descends from
- [Tachiyomi](https://github.com/tachiyomiorg/tachiyomi) — the original
- [libmpv](https://github.com/mpv-player/mpv) — the player
- [AniList](https://anilist.co) and [MyAnimeList](https://myanimelist.net) — the APIs

Thanks to everyone who contributed to those projects.

## License

Apache License 2.0 — see [LICENSE](LICENSE).
