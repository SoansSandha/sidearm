package io.github.soanssandha.sidearm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.soanssandha.sidearm.R
import io.github.soanssandha.sidearm.data.AppIcons

/** The app three presses open, with a button to change it. */
@Composable
fun TargetCard(state: MainUiState, icons: AppIcons, onChange: () -> Unit, modifier: Modifier = Modifier) {
    val target = state.config?.target
    val app = state.targetApp
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppIcon(app?.component, icons, Modifier.size(56.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.opens),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = app?.label ?: target?.packageName ?: stringResource(R.string.no_app_chosen),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (target != null && app == null && state.config != null) {
                    Text(
                        stringResource(R.string.not_installed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            FilledTonalButton(onClick = onChange, enabled = state.config != null) {
                Text(stringResource(if (target == null) R.string.choose else R.string.change))
            }
        }
    }
}
