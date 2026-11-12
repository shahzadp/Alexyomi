package eu.kanade.presentation.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import eu.kanade.tachiyomi.ui.player.EXTERNAL_PLAYERS
import eu.kanade.tachiyomi.ui.player.MPV_KT
import eu.kanade.tachiyomi.ui.player.MPV_KT_PREVIEW
import eu.kanade.tachiyomi.ui.player.MPV_PLAYER
import eu.kanade.tachiyomi.ui.player.MPV_REMOTE
import eu.kanade.tachiyomi.ui.player.MX_PLAYER
import eu.kanade.tachiyomi.ui.player.MX_PLAYER_FREE
import eu.kanade.tachiyomi.ui.player.MX_PLAYER_PRO
import eu.kanade.tachiyomi.ui.player.JUST_PLAYER
import eu.kanade.tachiyomi.ui.player.NEXT_PLAYER
import eu.kanade.tachiyomi.ui.player.VLC_PLAYER
import eu.kanade.tachiyomi.ui.player.WEB_VIDEO_CASTER
import eu.kanade.tachiyomi.ui.player.X_PLAYER
import tachiyomi.i18n.MR
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

/**
 * Dialog that lets the user pick the player used for videos, without having to dig through
 * the settings. The built-in player is always offered as the first entry.
 *
 * @param selectedPackage the package name of the currently selected external player, null when
 * the built-in player is used
 * @param onSelect called with the package name of the picked external player, null for the built-in one
 */
@Composable
fun ExternalPlayerPickerDialog(
    selectedPackage: String?,
    onSelect: (String?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val packageManager = remember(context) { context.packageManager }

    val installedPlayers = remember(context) {
        packageManager.getInstalledPackages(0)
            .filter { it.packageName in EXTERNAL_PLAYERS }
            .map { info ->
                val label = info.applicationInfo?.let { packageManager.getApplicationLabel(it).toString() }
                    ?: info.packageName
                info.packageName to label
            }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(AYMR.strings.pref_external_player_preference)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                PlayerOption(
                    title = stringResource(AYMR.strings.pref_player_internal),
                    selected = selectedPackage == null,
                    onClick = { onSelect(null) },
                )
                installedPlayers.forEach { (packageName, label) ->
                    PlayerOption(
                        title = label,
                        selected = selectedPackage == packageName,
                        onClick = { onSelect(packageName) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(MR.strings.action_ok))
            }
        },
    )
}

@Composable
private fun PlayerOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
