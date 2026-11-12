# =============================================================================
# Alexyomi — upstream (aniyomiorg/aniyomi) sync with library-feature exclusions.
#
#   .\scripts\sync-upstream.ps1            # create sync-upstream-YYYYMMDD and merge
#   .\scripts\sync-upstream.ps1 -DryRun    # preview only, no commit
#
# After the script finishes: build (`.\gradlew.bat :app:assembleDebug`),
# verify the checklist in UPSTREAM_SYNC.md, then merge to main.
# =============================================================================
param(
    [string]$UpstreamRef = "main",
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$repo = (git rev-parse --show-toplevel).Trim()
Push-Location $repo
try {
    git fetch upstream

    $target = "upstream/$UpstreamRef"
    $branch = "sync-upstream-$(Get-Date -Format 'yyyyMMdd')"
    Write-Output "Creating $branch and merging $target (library exclusions applied)…"
    git checkout -b $branch

    # merge=ours in .gitattributes already resolves the excluded paths in our favour
    git merge --no-commit --no-ff $target
    if ($LASTEXITCODE -ne 0) {
        Write-Output ""
        Write-Output "Merge reported conflicts. These are expected only in soft-excluded files."
        Write-Output "Resolve them by keeping our version (see UPSTREAM_SYNC.md)."
        Write-Output "Then run:  git add -A; git commit"
        exit 1
    }

    # Belt and braces: drop anything that slipped in under hard-excluded paths
    $hardExcluded = @(
        "app/src/main/java/eu/kanade/tachiyomi/ui/library",
        "app/src/main/java/eu/kanade/tachiyomi/ui/updates",
        "app/src/main/java/eu/kanade/tachiyomi/ui/category",
        "app/src/main/java/eu/kanade/tachiyomi/ui/stats",
        "app/src/main/java/eu/kanade/tachiyomi/data/library",
        "app/src/main/java/eu/kanade/tachiyomi/ui/browse/manga/migration",
        "app/src/main/java/eu/kanade/tachiyomi/ui/browse/anime/migration",
        "app/src/main/java/eu/kanade/presentation/library",
        "app/src/main/java/eu/kanade/presentation/updates",
        "app/src/main/java/eu/kanade/presentation/category",
        "app/src/main/java/eu/kanade/presentation/more/stats",
        "presentation-widget",
        "domain/src/main/java/tachiyomi/domain/updates",
        "data/src/main/java/tachiyomi/data/updates"
    )
    $removedSomething = $false
    foreach ($p in $hardExcluded) {
        if (Test-Path $p) {
            Write-Output "Removing resurrected path: $p"
            git rm -r --ignore-unmatch --quiet -- $p
            $removedSomething = $true
        }
    }

    Write-Output ""
    Write-Output "=== git status after exclusion pass ==="
    git status --short

    if ($DryRun) {
        Write-Output ""
        Write-Output "Dry run — nothing committed. Review the diff, then commit manually or re-run without -DryRun."
        return
    }

    $msg = "Merge $target (Alexyomi: library feature decommissioned)"
    if ($removedSomething) { $msg += "`n`nDrop upstream files under excluded library paths." }
    git add -A
    git commit -m $msg

    Write-Output ""
    Write-Output "Merge prepared on $branch. Now verify:"
    Write-Output "  1. .\gradlew.bat :app:assembleDebug"
    Write-Output "  2. Checklist in UPSTREAM_SYNC.md"
} finally {
    Pop-Location
}
