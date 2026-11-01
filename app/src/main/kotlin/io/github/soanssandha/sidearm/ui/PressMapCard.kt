package io.github.soanssandha.sidearm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.soanssandha.sidearm.R

/** What each count of power presses does, with Sidearm's press highlighted. Four presses do nothing new, so skip it. */
@Composable
fun PressMapCard(state: MainUiState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.power_button),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PressKey(1, stringResource(R.string.press_screen), highlighted = false)
                PressKey(2, stringResource(R.string.press_camera), highlighted = false)
                PressKey(3, state.targetApp?.label ?: stringResource(R.string.press_your_app), highlighted = true)
                PressKey(5, stringResource(R.string.press_sos), highlighted = false)
            }
        }
    }
}

@Composable
private fun RowScope.PressKey(presses: Int, label: String, highlighted: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val description = pluralStringResource(R.plurals.press_description, presses, presses, label)
    Column(
        modifier = Modifier.weight(1f).clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(if (highlighted) scheme.primary else scheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                presses.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = if (highlighted) scheme.onPrimary else scheme.onSecondaryContainer,
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            color = if (highlighted) scheme.onSurface else scheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
