# Upstream sync guide (Alexyomi ← Aniyomi)

> **Build/artifact note:** the maintained and installed artifact is the **preview** build
> (`:app:assemblePreview`, R8-minified, ~72 MB). See `BUILD.md`.

Alexyomi is a fork of Aniyomi with the **Aniyomi anime/manga library** (the local
tracking library and everything around it) decommissioned in favor of MyAnimeList
integration. This guide exists so that pulling new upstream work from `aniyomiorg`
never silently re-introduces that code.

## Remotes

```
upstream → https://github.com/aniyomiorg/aniyomi   (same URL as origin)
origin  → https://github.com/aniyomiorg/aniyomi
```

The `upstream` remote was added for semantic clarity — syncs are always done
against it. `main` tracks the upstream default branch.

```powershell
git fetch upstream                 # see what is new
git log --oneline main..upstream/main
```

## The two layers of defense

### 1. `.gitattributes` (`merge=ours`)

For every path listed below, git resolves the merge in our favor, automatically:

```
<path> merge=ours
```

Meaning: if upstream modified the file, our version wins and upstream's change
is silently discarded — this is intentional. If **we deleted the file** and
upstream modified it, ours (deleted) wins. This is what keeps the library
feature from coming back on its own.

The `.gitattributes` merge driver only kicks in on *conflicts/changes to the
same files*. If upstream adds a **brand-new file** inside one of the excluded
paths (e.g. `.../ui/library/NewFeature.kt`), git may add it anyway — which is
why the sync script below performs a second pass.

### 2. `scripts/sync-upstream.ps1` (hard exclusion pass + verification)

```powershell
.\scripts\sync-upstream.ps1              # create sync-upstream-YYYYMMDD branch and merge
.\scripts\sync-upstream.ps1 -DryRun      # show what would change without committing
```

What it does:

1. Fetches `upstream/main`.
2. Creates a branch `sync-upstream-YYYYMMDD` from the current branch.
3. Merges `upstream/main` (`--no-commit --no-ff`).
4. Deletes any file or directory inside the hard-excluded paths that slipped
   through (new upstream files), then shows `git status` and asks for review.
5. (No `-DryRun`) commits the merge.

It never force-pushes, never rewrites history, and never runs the build.
**You must build and test before merging to `main`**:

```powershell
.\build.ps1                # or: .\gradlew.bat :app:assembleDebug
```

## Hard-excluded paths (deleted or rewritten — keep excluded)

```
app/src/main/java/eu/kanade/tachiyomi/ui/library/           library UI
app/src/main/java/eu/kanade/tachiyomi/ui/updates/           library updates feed
app/src/main/java/eu/kanade/tachiyomi/ui/category/          library categories
app/src/main/java/eu/kanade/tachiyomi/ui/stats/              library stats
app/src/main/java/eu/kanade/tachiyomi/data/library/         library update WorkManager jobs
app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/migration/
app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/migration/
app/src/main/java/eu/kanade/presentation/library/
app/src/main/java/eu/kanade/presentation/updates/
app/src/main/java/eu/kanade/presentation/category/
app/src/main/java/eu/kanade/presentation/more/stats/
presentation-widget/                        library "Updates" home-screen widgets
domain/src/main/java/tachiyomi/domain/updates/
data/src/main/java/tachiyomi/data/updates/
```

## Soft-excluded files (rewritten by us — ours wins wholesale, review the diff)

```
app/src/main/java/eu/kanade/tachiyomi/ui/home/HomeScreen.kt   MAL is first tab
app/src/main/java/eu/kanade/tachiyomi/ui/main/MainActivity.kt shortcuts trimmed
app/src/main/java/eu/kanade/tachiyomi/ui/more/MoreTab.kt
app/src/main/java/eu/kanade.tachiyomi/ui/browse/BrowseTab.kt  no Migrate tabs
app/src/main/java/eu/kanade/presentation/more/MoreScreen.kt  no Updates/Categories/Stats/alt-nav entries
app/src/main/java/eu/kanade/presentation/util/Navigator.kt    NavStyle removed
app/src/main/java/eu/kanade/domain/ui/                        NavStyle/StartScreen/UiPreferences
app/src/main/java/eu/kanade/tachiyomi/ui/entries/             entry screens (favorite/add-to-library removed)
app/src/main/java/eu/kanade/presentation/entries/             entry components (FAB, interval, categories)
app/src/main/java/eu/kanade/presentation/library/             deleted
app/src/main/java/eu/kanade/tachiyomi/ui/history/             history (add-to-library removed)
app/src/main/java/eu/kanade/presentation/history/             history UI (add-to-library removed)
... browse screens (InLibraryBadge removed), SettingsDownloadScreen,
    SettingsAdvancedScreen, SettingsLibraryScreen (Behavior-only),
    SettingsBrowseScreen, DomainModule, NotificationReceiver,
    PreferenceRestorer, App.kt, build.gradle.kts, settings.gradle.kts
```

`ours wins` on these files means **you do not get any upstream work in them for
free**. That is a deliberate trade-off. If you ever want an upstream fix in one
of them, hand-merge it deliberately and remove it from the exclusion list in
the same commit.

## Checklist after a sync merge

- [ ] `git status` — nothing left under hard-excluded paths
- [ ] `:app:assembleDebug` — build green
- [ ] LIB tab is gone from the bottom bar; MAL is the first/default tab
- [ ] MAL still shows My List / Season / Top / Search; profile icon works
- [ ] Long-press on a browse item does nothing (no in-library toggle)
- [ ] Settings → Advanced — no "library" groups; More page has no Updates/Categories/Stats
- [ ] No new upstream file slipped in (`git diff --stat HEAD~1`)

## If upstream rewrites one of our files massively

`merge=ours` discards upstream's version entirely. When that happens:

1. `git diff HEAD..upstream/main -- <file>` to see what upstream did.
2. Port the relevant upstream change by hand, keeping our removals.
3. Re-list the file under "hand-merged" above and remove it from
   `.gitattributes` if we now track upstream's structure.

Add new exclusions to **both** `.gitattributes` and this file.
