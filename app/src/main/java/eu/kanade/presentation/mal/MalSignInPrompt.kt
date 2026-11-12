package eu.kanade.presentation.mal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.OpenInNew
import eu.kanade.tachiyomi.R
import tachiyomi.i18n.aniyomi.AYMR
import tachiyomi.presentation.core.i18n.stringResource

private val MalBrand = Color(0xFF2E51A2)
private val MalBrandLight = Color(0xFF3F63B4)

@Composable
fun MalSignInPrompt(
    onSignIn: () -> Unit,
    errorMessage: String? = null,
    modifier: Modifier = Modifier,
) {
    // The content can be taller than the screen (small devices / large font scale). Without a
    // scrollable container the Column squeezes its children, which collapsed the sign in button.
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Brand mark
            Surface(
                color = MalBrand,
                shape = CircleShape,
                modifier = Modifier.size(120.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_mal_24dp),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .padding(24.dp)
                        .clip(CircleShape),
                )
            }

            Text(
                text = stringResource(AYMR.strings.mal_sign_in_headline),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                text = stringResource(AYMR.strings.mal_sign_in_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )

            // Benefit card
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    BenefitRow(
                        icon = { Icon(Icons.Outlined.DoneAll, contentDescription = null, tint = MalBrandLight) },
                        text = stringResource(AYMR.strings.mal_sign_in_benefit_list),
                    )
                    BenefitRow(
                        icon = { Icon(Icons.Outlined.Add, contentDescription = null, tint = MalBrandLight) },
                        text = stringResource(AYMR.strings.mal_sign_in_benefit_increment),
                    )
                    BenefitRow(
                        icon = { Icon(Icons.Outlined.Edit, contentDescription = null, tint = MalBrandLight) },
                        text = stringResource(AYMR.strings.mal_sign_in_benefit_edit),
                    )
                }
            }

            Button(
                onClick = onSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 22.dp)
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MalBrand, contentColor = Color.White),
            ) {
                Icon(
                    imageVector = Icons.Outlined.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Text(
                    text = stringResource(AYMR.strings.mal_sign_in_action),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = stringResource(AYMR.strings.mal_sign_in_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )

            // The reason of the last failed attempt, so a broken sign in isn't silent
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                ) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BenefitRow(
    icon: @Composable () -> Unit,
    text: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 14.dp),
        )
    }
}
