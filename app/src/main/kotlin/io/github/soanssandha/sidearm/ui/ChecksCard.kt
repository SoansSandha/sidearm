package io.github.soanssandha.sidearm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.soanssandha.sidearm.R
import io.github.soanssandha.sidearm.data.DoublePress
import io.github.soanssandha.sidearm.data.ModuleLoad

/** Every condition triple press depends on, each with its own state, so a problem shows even while the feature is off. */
@Composable
fun ChecksCard(state: MainUiState, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.checks),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            when (val vector = state.vector) {
                VectorState.Connecting -> CheckRow(Level.PENDING, stringResource(R.string.check_connecting))

                VectorState.NotConnected -> CheckRow(Level.WARN, stringResource(R.string.check_not_connected))

                is VectorState.Connected -> {
                    val framework = vector.framework
                    CheckRow(
                        Level.OK,
                        stringResource(
                            R.string.check_connected,
                            framework.name,
                            framework.shortVersion,
                            framework.apiVersion,
                        ),
                    )
                    when (state.systemInScope) {
                        true -> CheckRow(Level.OK, stringResource(R.string.check_in_scope))
                        false -> CheckRow(Level.WARN, stringResource(R.string.check_not_in_scope))
                        null -> Unit
                    }
                    LoadRow(state.load)
                }
            }
            DoublePressRow(state.doublePress)
            if (state.build.tested) {
                CheckRow(Level.OK, stringResource(R.string.check_build_tested, state.build.id))
            } else {
                CheckRow(Level.WARN, stringResource(R.string.check_build_untested, state.build.id))
            }
        }
    }
}

@Composable
private fun LoadRow(load: ModuleLoad?) {
    when (load) {
        ModuleLoad.UP_TO_DATE -> CheckRow(Level.OK, stringResource(R.string.check_loaded))
        ModuleLoad.RELOADING -> CheckRow(Level.PENDING, stringResource(R.string.check_reloading))
        ModuleLoad.NOT_RUNNING -> CheckRow(Level.WARN, stringResource(R.string.check_not_loaded))
        ModuleLoad.STALE -> CheckRow(Level.WARN, stringResource(R.string.check_stale))
        ModuleLoad.FAILED -> CheckRow(Level.WARN, stringResource(R.string.check_load_failed))
        null -> Unit
    }
}

@Composable
private fun DoublePressRow(doublePress: DoublePress) {
    when (doublePress) {
        DoublePress.CAMERA -> CheckRow(Level.OK, stringResource(R.string.check_double_press_camera))
        DoublePress.WALLET -> CheckRow(Level.WARN, stringResource(R.string.check_double_press_wallet))
        DoublePress.OFF -> CheckRow(Level.WARN, stringResource(R.string.check_double_press_off))
        DoublePress.UNKNOWN -> CheckRow(Level.PENDING, stringResource(R.string.check_double_press_unknown))
    }
}

@Composable
private fun CheckRow(level: Level, text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val (icon, tint) = when (level) {
            Level.OK -> Icons.Rounded.CheckCircle to scheme.primary
            Level.WARN -> Icons.Rounded.Warning to scheme.error
            Level.PENDING -> Icons.Rounded.Info to scheme.onSurfaceVariant
        }
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (level == Level.WARN) scheme.error else scheme.onSurface,
        )
    }
}

private enum class Level { OK, WARN, PENDING }
