package eu.kanade.presentation.more.settings.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import eu.kanade.presentation.more.settings.Preference
import eu.kanade.presentation.more.settings.screen.SearchableSettings
import eu.kanade.tachiyomi.ui.player.settings.AudioChannels
import eu.kanade.tachiyomi.ui.player.settings.AudioPreferences
import eu.kanade.tachiyomi.util.applyDubPreferenceToAnimeSources
import eu.kanade.tachiyomi.util.system.toast
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.core.common.util.lang.withUIContext
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.collectAsState
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Locale
import java.util.MissingResourceException

object PlayerSettingsAudioScreen : SearchableSettings {

    @ReadOnlyComposable
    @Composable
    override fun getTitleRes() = AYMR.strings.pref_player_audio

    @Composable
    override fun getPreferences(): List<Preference> {
        val audioPreferences = remember { Injekt.get<AudioPreferences>() }
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val prefLangs = audioPreferences.preferredAudioLanguages()
        val pitchCorrection = audioPreferences.enablePitchCorrection()
        val audioChannels = audioPreferences.audioChannels()
        val boostCapPref = audioPreferences.volumeBoostCap()
        val boostCap by boostCapPref.collectAsState()
        val preferDub = audioPreferences.preferDubAnimeSources()

        return listOf(
            Preference.PreferenceItem.EditTextInfoPreference(
                preference = prefLangs,
                dialogSubtitle = stringResource(AYMR.strings.pref_player_audio_lang_info),
                title = stringResource(AYMR.strings.pref_player_audio_lang),
                validate = { pref ->
                    val langs = pref.split(",").filter(String::isNotEmpty).map(String::trim)
                    langs.forEach {
                        try {
                            val locale = Locale(it)
                            if (locale.isO3Language == locale.language &&
                                locale.language == locale.getDisplayName(Locale.ENGLISH)
                            ) {
                                throw MissingResourceException("", "", "")
                            }
                        } catch (_: MissingResourceException) {
                            return@EditTextInfoPreference false
                        }
                    }

                    true
                },
                errorMessage = { pref ->
                    val langs = pref.split(",").filter(String::isNotEmpty).map(String::trim)
                    langs.forEach {
                        try {
                            val locale = Locale(it)
                            if (locale.isO3Language == locale.language &&
                                locale.language == locale.getDisplayName(Locale.ENGLISH)
                            ) {
                                throw MissingResourceException("", "", "")
                            }
                        } catch (_: MissingResourceException) {
                            return@EditTextInfoPreference stringResource(
                                AYMR.strings.pref_player_subtitle_invalid_lang,
                                it,
                            )
                        }
                    }
                    ""
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = pitchCorrection,
                title = stringResource(AYMR.strings.pref_player_audio_pitch_correction),
                subtitle = stringResource(AYMR.strings.pref_player_audio_pitch_correction_summary),
            ),
            Preference.PreferenceItem.ListPreference(
                preference = audioChannels,
                entries = AudioChannels.entries.associateWith {
                    stringResource(it.titleRes)
                }.toImmutableMap(),
                title = stringResource(AYMR.strings.pref_player_audio_channels),
            ),
            Preference.PreferenceItem.SliderPreference(
                value = boostCap,
                valueRange = 0..200,
                title = stringResource(AYMR.strings.pref_player_audio_boost_cap),
                subtitle = boostCap.toString(),
                onValueChanged = {
                    boostCapPref.set(it)
                    true
                },
            ),
            Preference.PreferenceItem.SwitchPreference(
                preference = preferDub,
                title = stringResource(AYMR.strings.pref_player_audio_prefer_dub),
                subtitle = stringResource(AYMR.strings.pref_player_audio_prefer_dub_summary),
            ),
            Preference.PreferenceItem.TextPreference(
                title = stringResource(AYMR.strings.pref_player_audio_apply_dub),
                subtitle = stringResource(AYMR.strings.pref_player_audio_apply_dub_summary),
                onClick = {
                    scope.launch {
                        val changed = applyDubPreferenceToAnimeSources(context, force = true)
                        withUIContext {
                            context.toast(
                                context.stringResource(
                                    AYMR.strings.pref_player_audio_apply_dub_result,
                                    changed,
                                ),
                            )
                        }
                    }
                },
            ),
        )
    }
}
