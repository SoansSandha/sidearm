package io.github.soanssandha.sidearm.ui

import io.github.soanssandha.sidearm.data.FrameworkInfo

/** Whether the app has Vector's service. */
sealed interface VectorState {
    /** Waiting for Vector to send its binder, which it does soon after the app starts. */
    data object Connecting : VectorState

    /** No binder after a few seconds, or the one we had died: Sidearm is probably not enabled in Vector. */
    data object NotConnected : VectorState

    data class Connected(val framework: FrameworkInfo) : VectorState
}
