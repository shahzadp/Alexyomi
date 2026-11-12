package eu.kanade.tachiyomi.util

import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.ConfigurableAnimeSource
import eu.kanade.tachiyomi.animesource.sourcePreferences
import eu.kanade.tachiyomi.data.preference.SharedPreferencesDataStore
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.source.anime.service.AnimeSourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

private val DUB_TOKENS = listOf("dub", "dubbed")
private val SUB_TOKENS = listOf("sub", "subtitled")
private val JAPANESE_TOKENS = listOf("japanese", "jpn", "jp ")

/**
 * Tokens that identify a video quality selector.
 */
private val QUALITY_TOKENS = listOf("1080", "2160", "720", "480", "360", "4k")

/**
 * The resolution we want the sources to use instead of their own default (usually 1080p).
 */
private const val PREFERRED_QUALITY = "720"

/**
 * Version of the rules below.
 *
 * Bump this whenever a rule is added or changed: the applied source ids are only meaningful
 * for the version they were collected with, so a new version makes the app walk over every
 * installed source again.
 */
private const val SOURCE_RULES_VERSION = 2

/**
 * Anime extensions ship their audio type preference with "Sub" as the default and their quality
 * selector with 1080p. Since the host app owns the preference store of every source, it can
 * flip those selectors to the dub option and to 720p. This affects both playback and downloads,
 * as the extension marks the matching video as preferred.
 *
 * @return the number of sources that were changed
 */
suspend fun preferDubbedAnimeSources(context: Context, sources: List<AnimeSource>): Int {
    var changed = 0
    for (source in sources) {
        if (source !is ConfigurableAnimeSource) continue
        try {
            if (withContext(Dispatchers.IO) { applySourceRules(context, source, preferDub = true) }) {
                changed++
            }
        } catch (e: Throwable) {
            context.logcat(LogPriority.ERROR, e) { "Failed to apply source rules for ${source.name}" }
        }
    }
    return changed
}

/**
 * Applies the source rules (dub audio + 720p) to every source that wasn't processed yet.
 *
 * When the rules changed since the last run, every source is processed again so the new rule
 * reaches the sources that were already handled before.
 *
 * @return the number of sources that were changed
 */
suspend fun applyDubPreferenceToAnimeSources(context: Context, force: Boolean = false): Int {
    val audioPreferences = Injekt.get<AudioPreferences>()
    val preferDub = force || audioPreferences.preferDubAnimeSources().get()

    val sourceManager = Injekt.get<AnimeSourceManager>()
    val applied = audioPreferences.dubAppliedSourceIds().get()
    val rulesChanged = audioPreferences.sourceRulesVersion().get() != SOURCE_RULES_VERSION

    val sources = sourceManager.getAll()
    val pending = if (force || rulesChanged) {
        sources
    } else {
        sources.filter { it.id.toString() !in applied }
    }

    if (pending.isEmpty()) {
        if (rulesChanged) audioPreferences.sourceRulesVersion().set(SOURCE_RULES_VERSION)
        return 0
    }

    var changed = 0
    for (source in pending) {
        if (source !is ConfigurableAnimeSource) continue
        try {
            if (withContext(Dispatchers.IO) { applySourceRules(context, source, preferDub) }) {
                changed++
            }
        } catch (e: Throwable) {
            context.logcat(LogPriority.ERROR, e) { "Failed to apply source rules for ${source.name}" }
        }
    }

    audioPreferences.dubAppliedSourceIds().set(
        (applied + pending.map { it.id.toString() }).toSet(),
    )
    audioPreferences.sourceRulesVersion().set(SOURCE_RULES_VERSION)
    return changed
}

/**
 * Applies the dub and quality rules to a single source.
 */
private fun applySourceRules(
    context: Context,
    source: ConfigurableAnimeSource,
    preferDub: Boolean,
): Boolean {
    val themed = ContextThemeWrapper(context, R.attr.preferenceTheme)
    val preferenceManager = PreferenceManager(themed)
    preferenceManager.preferenceDataStore = SharedPreferencesDataStore(source.sourcePreferences())

    val screen = preferenceManager.createPreferenceScreen(themed)
    source.setupPreferenceScreen(screen)

    var changed = false
    // Extensions usually put their preferences inside a category (or even a nested screen),
    // so the whole tree has to be walked instead of only the top level.
    screen.flatten().forEach { preference ->
        when (preference) {
            is ListPreference -> {
                // Quality first, so a combined sub/dub + quality list keeps the dub option
                if (applyQualityPreference(context, preference, source)) changed = true
                if (preferDub) {
                    val dubValue = findDubValue(
                        preference.entryValues?.map { it.toString() }.orEmpty(),
                        preference.entries?.map { it.toString() }.orEmpty(),
                    )
                    if (dubValue != null && preference.value != dubValue) {
                        if (writePreferenceValue(context, preference, dubValue, source)) changed = true
                    }
                }
            }
            is SwitchPreference -> {
                if (!preferDub) return@forEach
                val key = preference.key.orEmpty()
                val title = preference.title?.toString().orEmpty()
                if ((key.contains("dub", ignoreCase = true) || title.contains("dub", ignoreCase = true)) &&
                    !preference.isChecked
                ) {
                    preference.isChecked = true
                    changed = true
                }
            }
        }
    }

    // Some extensions use custom preference types, so also scan the raw preference store
    if (preferDub && setDubInRawPreferences(source)) {
        changed = true
    }
    if (setQualityInRawPreferences(source)) {
        changed = true
    }

    return changed
}

/**
 * Walks a preference and all of its children.
 */
private fun Preference.flatten(): Sequence<Preference> = sequence {
    yield(this@flatten)
    if (this@flatten is PreferenceGroup) {
        for (index in 0 until this@flatten.preferenceCount) {
            yieldAll(this@flatten.getPreference(index).flatten())
        }
    }
}

/**
 * Selects the 720p option of a quality selector, if the preference is one.
 *
 * @return whether the preference was changed
 */
private fun applyQualityPreference(
    context: Context,
    preference: ListPreference,
    source: ConfigurableAnimeSource,
): Boolean {
    val values = preference.entryValues?.map { it.toString() }.orEmpty()
    val entries = preference.entries?.map { it.toString() }.orEmpty()

    val all = values + entries
    val looksLikeQuality = all.any { text ->
        QUALITY_TOKENS.any { text.contains(it, ignoreCase = true) }
    }
    if (!looksLikeQuality) return false

    // Only ever override a higher default (1080p/4K). A list that tops out at 720p already is
    // what we want, so it is left alone.
    val hasHigherQuality = all.any { text ->
        text.contains("1080", ignoreCase = true) ||
            text.contains("2160", ignoreCase = true) ||
            text.contains("4k", ignoreCase = true)
    }
    if (!hasHigherQuality) return false

    val valueIndex = values.indexOfFirst { it.contains(PREFERRED_QUALITY, ignoreCase = true) }
    val entryIndex = entries.indexOfFirst { it.contains(PREFERRED_QUALITY, ignoreCase = true) }
    if (valueIndex < 0 && entryIndex < 0) return false

    val targetValue = when {
        valueIndex >= 0 -> values[valueIndex]
        entryIndex >= 0 -> {
            // The preference only carries display names, mirror them as values so the
            // extension actually shows the new selection.
            if (values.isEmpty()) {
                runCatching { preference.entryValues = entries.toTypedArray() }
            }
            entries[entryIndex]
        }
        else -> return false
    }

    if (preference.value == targetValue) return false
    return writePreferenceValue(context, preference, targetValue, source)
}

/**
 * Writes a new value to a list preference.
 *
 * The value is written through the preference itself and additionally into the store of the
 * source, because some extensions build their preferences without a data store and would
 * otherwise never see the new value.
 *
 * @return whether the value was written
 */
private fun writePreferenceValue(
    context: Context,
    preference: ListPreference,
    value: String,
    source: ConfigurableAnimeSource,
): Boolean {
    var written = false
    runCatching { preference.value = value }
        .onSuccess { written = true }
        .onFailure { e ->
            context.logcat(LogPriority.ERROR, e) { "Failed to set ${preference.key} of ${source.name}" }
        }

    val key = preference.key
    if (!key.isNullOrEmpty()) {
        runCatching { source.sourcePreferences().edit().putString(key, value).apply() }
            .onSuccess { written = true }
    }

    return written
}

/**
 * Returns the dub entry of a sub/dub selector, or null when the preference isn't one.
 */
private fun findDubValue(values: List<String>, entries: List<String>): String? {
    val all = values + entries

    val hasSub = all.any { text -> SUB_TOKENS.any { text.contains(it, ignoreCase = true) } } ||
        all.any { text -> JAPANESE_TOKENS.any { text.contains(it, ignoreCase = true) } }
    if (!hasSub) return null

    val dubEntry = values.indexOfFirst { value -> DUB_TOKENS.any { value.contains(it, ignoreCase = true) } }
    if (dubEntry >= 0) return values[dubEntry]

    // Extensions that label the audio type with the language instead of sub/dub
    val englishEntry = values.indexOfFirst { value -> value.contains("english", ignoreCase = true) }
    if (englishEntry >= 0) return values[englishEntry]

    val dubText = entries.indexOfFirst { text -> DUB_TOKENS.any { text.contains(it, ignoreCase = true) } }
    if (dubText >= 0) return entries[dubText]

    val englishText = entries.indexOfFirst { text -> text.contains("english", ignoreCase = true) }
    if (englishText >= 0) return entries[englishText]

    return null
}

/**
 * Fallback for extensions with custom preference implementations: flip any stored value whose
 * key mentions dub.
 */
private fun setDubInRawPreferences(source: ConfigurableAnimeSource): Boolean {
    val prefs = source.sourcePreferences()
    var changed = false
    prefs.all.forEach { (key, value) ->
        if (!key.contains("dub", ignoreCase = true)) return@forEach
        when (value) {
            is Boolean -> if (!value) {
                prefs.edit().putBoolean(key, true).apply()
                changed = true
            }
            is String -> if (!value.contains("dub", ignoreCase = true)) {
                prefs.edit().putString(key, "dub").apply()
                changed = true
            }
        }
    }
    return changed
}

/**
 * Fallback for extensions that read their quality straight from the store instead of using a
 * list preference. Only plain quality tokens ("1080", "1080p", ...) are rewritten so a value
 * that means something else is never touched.
 */
private fun setQualityInRawPreferences(source: ConfigurableAnimeSource): Boolean {
    val prefs = source.sourcePreferences()
    var changed = false
    prefs.all.forEach { (key, value) ->
        if (value !is String) return@forEach
        val match = PURE_QUALITY_REGEX.matchEntire(value.trim()) ?: return@forEach
        val resolution = match.groupValues[1]
        if (resolution == PREFERRED_QUALITY) return@forEach
        if (resolution != "1080" && resolution != "2160") return@forEach

        val newValue = if (match.groupValues[2].isEmpty()) PREFERRED_QUALITY else "$PREFERRED_QUALITY${match.groupValues[2]}"
        prefs.edit().putString(key, newValue).apply()
        changed = true
    }
    return changed
}

/**
 * Matches a plain resolution value like "1080" or "1080p".
 */
private val PURE_QUALITY_REGEX = Regex("^(\\d{3,4})(p)?$", RegexOption.IGNORE_CASE)
