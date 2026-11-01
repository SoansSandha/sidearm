package io.github.soanssandha.sidearm.ui

import io.github.soanssandha.sidearm.data.DoublePress
import io.github.soanssandha.sidearm.data.ModuleLoad

/** What the status card at the top says: the one thing that matters most right now. */
sealed interface Hero {
    data object Connecting : Hero

    data object NotConnected : Hero

    /** The user turned the feature off. Setup problems still show in the checks. */
    data object Off : Hero

    data object NotInScope : Hero

    /** Not loaded in system_server yet; a reboot loads it. */
    data object NotLoaded : Hero

    /** An older install is loaded in system_server; a reboot loads this one. */
    data object Outdated : Hero

    data object LoadFailed : Hero

    data object NoTarget : Hero

    /** The chosen app was uninstalled, disabled, or lost its launcher activity. */
    data class TargetGone(val packageName: String) : Hero

    /** Double-press opens Wallet or is off, so the hook stands aside. */
    data class DoublePressNotCamera(val doublePress: DoublePress) : Hero

    /** Armed, but the config's dry run only logs presses (set from adb in debug builds). */
    data class DryRun(val appLabel: String) : Hero

    data class Active(val appLabel: String) : Hero

    companion object {
        /** The highest-priority state of [state], checked in the order a user would fix things. */
        fun of(state: MainUiState): Hero {
            when (state.vector) {
                VectorState.Connecting -> return Connecting
                VectorState.NotConnected -> return NotConnected
                is VectorState.Connected -> Unit
            }
            val config = state.config ?: return Connecting
            if (!config.enabled) return Off
            if (state.systemInScope == false) return NotInScope
            when (state.load) {
                ModuleLoad.NOT_RUNNING -> return NotLoaded
                ModuleLoad.STALE -> return Outdated
                ModuleLoad.FAILED -> return LoadFailed
                ModuleLoad.UP_TO_DATE, ModuleLoad.RELOADING, null -> Unit
            }
            val target = config.target ?: return NoTarget
            val app = state.targetApp ?: return TargetGone(target.packageName)
            return when (state.doublePress) {
                DoublePress.WALLET, DoublePress.OFF -> DoublePressNotCamera(state.doublePress)
                DoublePress.CAMERA, DoublePress.UNKNOWN -> if (config.dryRun) DryRun(app.label) else Active(app.label)
            }
        }
    }
}
