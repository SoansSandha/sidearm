package io.github.soanssandha.sidearm.hook.effect

import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.core.sequencer.CameraArgs

/**
 * Carries out the reducer's effects in system_server. [Sequencer] calls it after releasing its lock, and decides
 * which effects reach it during a dry run.
 */
internal interface EffectRunner {
    /** Calls [onFire] at uptime [deadline] on a system_server looper. */
    fun scheduleTimer(deadline: Long, onFire: () -> Unit)

    /** Drops the pending timer, if there is one. */
    fun cancelTimer()

    /** Runs a held `handleCameraGesture` call on the original method. */
    fun runHeldCamera(camera: CameraArgs)

    /** Wakes the device for a press 3 that came while it wasn't interactive. */
    fun wakeDevice()

    /**
     * Opens [target] without blocking the caller. If it can't be opened, runs the [fallback] camera, or nothing if
     * the camera has already run.
     */
    fun launchTarget(target: TargetComponent, fallback: CameraArgs?)

    /** For a dry run: logs whether [target] would open, without opening it. */
    fun checkTarget(target: TargetComponent)
}
