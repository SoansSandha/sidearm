package io.github.soanssandha.sidearm.ui

import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.data.BuildCheck
import io.github.soanssandha.sidearm.data.DoublePress
import io.github.soanssandha.sidearm.data.LauncherApp
import io.github.soanssandha.sidearm.data.ModuleLoad

/**
 * Everything the config screen shows. The fields read from Vector ([systemInScope], [load], [config], [targetApp]) are
 * null until it connects, and again after it disconnects.
 */
data class MainUiState(
    val build: BuildCheck,
    val vector: VectorState = VectorState.Connecting,
    val systemInScope: Boolean? = null,
    /** Null if Vector couldn't say. */
    val load: ModuleLoad? = null,
    val config: Config? = null,
    /** The app [config]'s target names, or null if there's no target or it's no longer a launcher activity. */
    val targetApp: LauncherApp? = null,
    val doublePress: DoublePress = DoublePress.UNKNOWN,
    /** Every launcher app, for the picker; null until it has been loaded. */
    val apps: List<LauncherApp>? = null,
    val message: UserMessage? = null,
) {
    val hero: Hero get() = Hero.of(this)
}
