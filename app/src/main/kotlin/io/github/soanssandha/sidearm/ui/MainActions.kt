package io.github.soanssandha.sidearm.ui

import io.github.soanssandha.sidearm.data.LauncherApp

/** What the config screen asks the activity to do. */
class MainActions(
    val onEnabledChange: (Boolean) -> Unit,
    /** The picker is opening: load the apps it lists. */
    val onOpenPicker: () -> Unit,
    val onPick: (LauncherApp) -> Unit,
    val onOpenGestureSettings: () -> Unit,
    val onOpenSource: () -> Unit,
    val onMessageShown: () -> Unit,
)
