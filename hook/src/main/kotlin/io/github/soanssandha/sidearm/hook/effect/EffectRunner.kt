package io.github.soanssandha.sidearm.hook.effect

import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.core.sequencer.CameraArgs
import io.github.soanssandha.sidearm.core.sequencer.Fallback

/**
 * Carries out the reducer's effects in system_server. [Sequencer] calls it after releasing its lock, and decides
 * which effects reach it during a dry run. The exception is [holdStart], which it calls inside the lock.
 */
internal interface EffectRunner {
    /** Calls [onFire] at uptime [deadline] on a system_server looper. */
    fun scheduleTimer(deadline: Long, onFire: () -> Unit)

    /** Drops the pending timer, if there is one. */
    fun cancelTimer()

    /** Runs a held `handleCameraGesture` call on the original method. */
    fun runHeldCamera(camera: CameraArgs)

    /** Keeps SystemUI's camera start [call] until it's run or dropped. Called inside the sequencer's lock. */
    fun holdStart(call: HeldStart.Call)

    /** Runs the held camera start, as SystemUI, without blocking the caller. */
    fun runHeldStart()

    /** Wakes the device for a press 3 that came while it wasn't interactive. */
    fun wakeDevice()

    /**
     * Opens [target] without blocking the caller. If it can't be opened, runs the [fallback], or nothing if the
     * camera has already run. A held start that isn't needed is dropped. For [Fallback.PendingStart], whose start
     * hasn't come yet, the fallback is calling [releasePendingStart].
     */
    fun launchTarget(target: TargetComponent, fallback: Fallback?, releasePendingStart: () -> Unit)

    /** For a dry run: logs whether [target] would open, without opening it. */
    fun checkTarget(target: TargetComponent)
}
