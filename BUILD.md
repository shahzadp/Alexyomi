# Building Alexyomi

## The artifact we ship and test

| | command | output | size (arm64) | package id |
|---|---|---|---|---|
| **Release (maintained)** | `.\gradlew.bat :app:assembleRelease` | `app/build/outputs\apk\release\app-arm64-v8a-release.apk` | ~53 MB | `xyz.jmir.tachiyomi.mi` |
| Debug (only for quick iteration) | `.\gradlew.bat :app:assembleDebug` | `app/build/outputs\apk\debug\app-arm64-v8a-debug.apk` | ~102 MB | `xyz.jmir.tachiyomi.mi.dev` |

The **release** variant is the one we install on devices, publish and treat as the reference
build:

- it has **R8 shrinking and resource shrinking** applied (the debug build ships unshrunk code
  and is ~30 MB bigger)
- it is signed with the project keystore, so updates install over each other
- the ABI splits produce one APK per architecture plus a universal one

Use the debug build only when you need fast iteration and don't care about the size; always
verify a change in the release build before calling it done.

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
.\gradlew.bat :app:assembleRelease --console=plain --no-daemon

# Send the fresh APK to the phone over KDE Connect
.\scripts\send-apk-to-phone.ps1
```

## Size notes

The release build is dominated by native libraries, not by app code:

```
lib/             ~42 MB   ffmpeg/mpv ~25 · image decoder 5 · misc 12
DEX (R8)         ~22 MB   (the unshrunk debug dex is ~95 MB)
res/ + arsc      ~2.6 MB  (English only, see localeFilters)
assets           ~0.4 MB  (the bundled subtitle font is excluded)
```

Two build-level size measures live in `app/build.gradle.kts`:

- `androidResources.localeFilters += listOf("en")` — only English strings are packaged. The 64
  translations are still in `i18n/` and `i18n-aniyomi/`; extend the list to ship them again.
- the `stripSubtitleFont*` tasks drop the 6 MB `subfont.ttf` that the mpv library bundles. mpv
  renders subtitles with the system fonts directory (`PlayerActivity.copyFontsDirectory`), so the
  bundled font isn't needed.

## Signing

The release build is signed with a key that lives in the project folder but is **never
committed** — both the keystore and the credentials file are gitignored:

```
keystore/alexyomi-release.jks     the key itself
keystore.properties               storeFile / storePassword / keyAlias / keyPassword
```

`app/build.gradle.kts` reads `keystore.properties` and, when it exists, signs the `release`
variant with it. Without that file the release variant simply has no signing config.

Check what a variant is signed with:

```powershell
.\gradlew.bat :app:signingReport
```

The **preview** variant stays on the Android debug key on purpose: it is the build that goes
onto the phone, and switching it to the release key changes the signature, which means the
installed app has to be uninstalled once before it can be updated.

**Keep the password out of the repository.** It is not written in this file, in `TODO.md` or
anywhere else in the tree — only in `keystore.properties`, which is gitignored. If you ever
lose it, the key can no longer be used to sign updates for an already published app, so keep
a copy of the password somewhere safe.

## ABI splits

`splits.abi` produces per-ABI APKs plus a universal one. Install the arm64 one for modern phones:

```
app/build/outputs/apk/preview/app-arm64-v8a-preview.apk
```

## Playback defaults

The app ships with the following defaults, all of them changeable in the settings:

| default | where | implementation |
|---|---|---|
| episodes play in the **built-in player** | Settings → Player → External player | `PlayerPreferences.alwaysUseExternalPlayer()` defaults to `false` |
| the external player (VLC) is one tap away | entry screen → bottom action menu → *Play externally* | the menu shows *Play externally* while the built-in player is the default |
| **720p** instead of 1080p for every source | Settings → Player → Audio → *Apply source defaults now* | `AnimeSourceDubPreference.kt` walks the preference tree of every installed source |
| **Dub** audio for every source | same place | same mechanism, the sub/dub selector is flipped to the dub entry |
| **English** audio and subtitles in the built-in player | Settings → Player → Audio/Subtitles | `TrackSelect` falls back to English instead of the device locale, mpv gets `alang`/`slang` = `en` |

### Why the built-in player is the default

The built-in player streams through the app's own http client, so it sends the headers a source
needs (`Referer`, `User-Agent`, …) and it controls the audio and subtitle tracks of the file.

External players only get the plain stream url — they ignore the `headers` intent extra — so
many sources refuse the stream there, and the app cannot influence which audio or subtitle
track the player picks. VLC decides that on its own, using the preferences of the user.

The scopes stay separated:

- **downloaded** episodes are handed to the player as a local `content://` file (FileProvider),
  so they play offline in the built-in player and in VLC alike
- **online** episodes are streamed by the built-in player; the app's http server is only started
  for sources that proxy through it (`http://localhost:1x` urls)

> The audio/subtitle preferences inside VLC are VLC's own. The app can only control the
> built-in player and the preferences of the extensions it hosts.

### What the app can and can't do inside VLC

VLC for Android only understands a small set of intent extras. The app uses the ones that exist:

| extra | effect in VLC |
|---|---|
| `intent.data` | the media to play (a `content://` file for downloads, the stream url for online) |
| `position` | resume position, in **milliseconds** |
| `from_start` | start from the beginning instead of the resume position |
| `title` | the title shown in VLC |

VLC has **no intent extra for the audio track and none for the subtitle track**, so the app
deliberately does not send any — VLC picks them with its own preferences. Likewise the
`headers` / `http-header-fields` extras are ignored by VLC, so a source that demands a
`Referer` header may refuse the stream in VLC while it plays fine in the built-in player.

### The source rules version

`AudioPreferences.sourceRulesVersion()` holds the version of the rules above. Bump
`SOURCE_RULES_VERSION` in `AnimeSourceDubPreference.kt` whenever a rule is added or changed —
the applied source ids are only meaningful for the version they were collected with, so a new
version makes the app walk over every installed source again.

## Notes on the upstream merge workflow

See `UPSTREAM_SYNC.md` for how `aniyomiorg/aniyomi` is merged without resurrecting the removed
library/backup features.

