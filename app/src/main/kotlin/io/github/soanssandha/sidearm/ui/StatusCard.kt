package io.github.soanssandha.sidearm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.soanssandha.sidearm.R
import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.data.DoublePress

/**
 * The card at the top: the on/off switch, then the one state that matters most ([Hero]), coloured by how it's going,
 * with the fix and a button for it when there's one.
 */
@Composable
fun StatusCard(
    state: MainUiState,
    onEnabledChange: (Boolean) -> Unit,
    onChooseApp: () -> Unit,
    onOpenGestureSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hero = state.hero
    val content = heroContent(hero, state)
    val colors = toneColors(content.tone)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.content),
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EnableRow(state.config, onEnabledChange)
            Row(
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeroBadge(hero, colors)
                Column {
                    Text(content.headline, style = MaterialTheme.typography.headlineSmall)
                    Text(content.detail, style = MaterialTheme.typography.bodyLarge)
                }
            }
            content.fix?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            val buttonColors = ButtonDefaults.buttonColors(containerColor = colors.badge, contentColor = colors.onBadge)
            when (content.action) {
                HeroAction.CHOOSE_APP -> Button(onClick = onChooseApp, colors = buttonColors) {
                    Text(stringResource(R.string.choose_app))
                }

                HeroAction.GESTURE_SETTINGS -> Button(onClick = onOpenGestureSettings, colors = buttonColors) {
                    Text(stringResource(R.string.open_gesture_settings))
                }

                null -> Unit
            }
            frameworkLine(state)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** The whole row toggles, so the switch carries the "Triple press" label for TalkBack. */
@Composable
private fun EnableRow(config: Config?, onEnabledChange: (Boolean) -> Unit) {
    val checked = config?.enabled == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = checked, enabled = config != null, role = Role.Switch, onValueChange = onEnabledChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.triple_press),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = config != null,
            thumbContent = if (checked) {
                {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            } else {
                null
            },
        )
    }
}

@Composable
private fun HeroBadge(hero: Hero, colors: ToneColors) {
    Box(
        modifier = Modifier.size(56.dp).background(colors.badge, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (hero == Hero.Connecting) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = colors.onBadge, strokeWidth = 3.dp)
        } else {
            Icon(badgeIcon(hero), contentDescription = null, tint = colors.onBadge, modifier = Modifier.size(28.dp))
        }
    }
}

private fun badgeIcon(hero: Hero): ImageVector = when (hero) {
    is Hero.Active -> Icons.Rounded.Check

    is Hero.DryRun -> Icons.Rounded.Info

    Hero.Off -> PowerIcon

    Hero.NoTarget -> Icons.Rounded.Add

    Hero.Connecting, Hero.NotConnected, Hero.NotInScope, Hero.NotLoaded, Hero.Outdated, Hero.LoadFailed,
    is Hero.TargetGone, is Hero.DoublePressNotCamera,
    -> Icons.Rounded.Warning
}

@Composable
private fun heroContent(hero: Hero, state: MainUiState): HeroContent {
    val untested = if (state.build.tested) null else stringResource(R.string.hero_untested_build, state.build.id)
    return when (hero) {
        Hero.Connecting -> HeroContent(
            Tone.NEUTRAL,
            stringResource(R.string.hero_connecting),
            stringResource(R.string.hero_connecting_detail),
        )

        Hero.NotConnected -> HeroContent(
            Tone.PROBLEM,
            stringResource(R.string.hero_not_connected),
            stringResource(R.string.hero_not_connected_detail),
            fix = stringResource(R.string.hero_not_connected_fix),
        )

        Hero.Off -> HeroContent(
            Tone.NEUTRAL,
            stringResource(R.string.hero_off),
            stringResource(R.string.hero_off_detail),
        )

        Hero.NotInScope -> HeroContent(
            Tone.PROBLEM,
            stringResource(R.string.hero_not_in_scope),
            stringResource(R.string.hero_not_in_scope_detail),
            fix = stringResource(R.string.hero_not_in_scope_fix),
        )

        Hero.NotLoaded -> HeroContent(
            Tone.PROBLEM,
            stringResource(R.string.hero_reboot),
            stringResource(R.string.hero_not_loaded_detail),
            fix = stringResource(R.string.hero_not_loaded_fix),
        )

        Hero.Outdated -> HeroContent(
            Tone.PROBLEM,
            stringResource(R.string.hero_reboot),
            stringResource(R.string.hero_outdated_detail),
            fix = stringResource(R.string.hero_outdated_fix),
        )

        Hero.LoadFailed -> HeroContent(
            Tone.PROBLEM,
            stringResource(R.string.hero_load_failed),
            stringResource(R.string.hero_load_failed_detail),
            fix = stringResource(R.string.hero_load_failed_fix),
        )

        Hero.NoTarget -> HeroContent(
            Tone.ATTENTION,
            stringResource(R.string.hero_no_target),
            stringResource(R.string.hero_no_target_detail),
            action = HeroAction.CHOOSE_APP,
        )

        is Hero.TargetGone -> HeroContent(
            Tone.PROBLEM,
            stringResource(R.string.hero_target_gone),
            stringResource(R.string.hero_target_gone_detail, hero.packageName),
            action = HeroAction.CHOOSE_APP,
        )

        is Hero.DoublePressNotCamera -> HeroContent(
            Tone.PROBLEM,
            stringResource(R.string.hero_paused),
            stringResource(
                if (hero.doublePress == DoublePress.WALLET) {
                    R.string.hero_double_press_wallet
                } else {
                    R.string.hero_double_press_off
                },
            ),
            fix = stringResource(R.string.hero_double_press_fix),
            action = HeroAction.GESTURE_SETTINGS,
        )

        is Hero.DryRun -> HeroContent(
            Tone.ATTENTION,
            stringResource(R.string.hero_dry_run),
            stringResource(R.string.hero_dry_run_detail, hero.appLabel),
            fix = untested,
        )

        is Hero.Active -> HeroContent(
            Tone.POSITIVE,
            stringResource(R.string.hero_active),
            stringResource(R.string.hero_active_detail, hero.appLabel),
            fix = untested,
        )
    }
}

/** The framework, its API and, once it's in scope, System Framework: "Vector 2.2 · API 102 · System Framework". */
@Composable
private fun frameworkLine(state: MainUiState): String? {
    val framework = (state.vector as? VectorState.Connected)?.framework ?: return null
    val format = if (state.systemInScope == true) R.string.hero_framework_in_scope else R.string.hero_framework
    return stringResource(format, framework.name, framework.shortVersion, framework.apiVersion)
}

@Composable
private fun toneColors(tone: Tone): ToneColors {
    val scheme = MaterialTheme.colorScheme
    return when (tone) {
        Tone.POSITIVE -> ToneColors(
            scheme.primaryContainer,
            scheme.onPrimaryContainer,
            scheme.primary,
            scheme.onPrimary,
        )

        Tone.NEUTRAL -> ToneColors(scheme.surfaceContainerHigh, scheme.onSurface, scheme.secondary, scheme.onSecondary)

        Tone.ATTENTION -> ToneColors(
            scheme.tertiaryContainer,
            scheme.onTertiaryContainer,
            scheme.tertiary,
            scheme.onTertiary,
        )

        Tone.PROBLEM -> ToneColors(scheme.errorContainer, scheme.onErrorContainer, scheme.error, scheme.onError)
    }
}

private enum class Tone { POSITIVE, NEUTRAL, ATTENTION, PROBLEM }

private enum class HeroAction { CHOOSE_APP, GESTURE_SETTINGS }

private class HeroContent(
    val tone: Tone,
    val headline: String,
    val detail: String,
    val fix: String? = null,
    val action: HeroAction? = null,
)

private class ToneColors(val container: Color, val content: Color, val badge: Color, val onBadge: Color)
