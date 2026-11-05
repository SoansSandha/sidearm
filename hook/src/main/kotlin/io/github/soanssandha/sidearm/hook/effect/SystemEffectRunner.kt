package io.github.soanssandha.sidearm.hook.effect

import android.os.Handler
import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.core.sequencer.CameraArgs
import io.github.soanssandha.sidearm.core.sequencer.Fallback

/**
 * Runs effects in system_server. Timers, launches and replayed camera starts go to [handler], which is on
 * system_server's main looper: the hook adds no looper of its own, and never starts an activity from the input
 * thread (docs/plan.md §3.3). Held cameras run on whichever thread released them, as stock runs the camera on the
 * input thread.
 *
 * [heldStart] is null when the activity-start hook isn't installed; the sequencer then never holds a start.
 */
internal class SystemEffectRunner(
    private val handler: Handler,
    private val heldCamera: HeldCamera,
    private val heldStart: HeldStart?,
    private val launcher: TargetLauncher,
    private val waker: DeviceWaker,
    private val log: HookLog,
) : EffectRunner {

    override fun scheduleTimer(deadline: Long, onFire: () -> Unit) {
        val guarded = Runnable { runCatching(onFire).onFailure { log.warn("Timer failed", it) } }
        // The deadline is on the key events' uptime clock, which postAtTime also uses.
        if (!handler.postAtTime(guarded, TIMER, deadline)) {
            log.warn("Timer not scheduled; the next power press runs the held camera")
        }
    }

    override fun cancelTimer() {
        handler.removeCallbacksAndMessages(TIMER)
    }

    override fun runHeldCamera(camera: CameraArgs) {
        heldCamera.run(camera)
    }

    override fun holdStart(call: HeldStart.Call) {
        checkNotNull(heldStart) { "No activity-start hook, so nothing can be held" }.hold(call)
    }

    override fun runHeldStart() {
        val held = heldStart ?: return log.warn("No activity-start hook, so no held camera start to run")
        // Taken now, so a start held by a later sequence before this runs isn't replayed in its place.
        val call = held.take() ?: return log.warn("No held camera start to run")
        handler.postGuarded(log, "Held camera start") { held.run(call) }
    }

    override fun wakeDevice() {
        waker.wake()
    }

    override fun launchTarget(target: TargetComponent, fallback: Fallback?, releasePendingStart: () -> Unit) {
        val runFallback: (() -> Unit)? = when (fallback) {
            is Fallback.Camera -> {
                { heldCamera.run(fallback.camera) }
            }

            Fallback.HeldStart -> heldStartFallback()

            Fallback.PendingStart -> releasePendingStart

            null -> null
        }
        // This runs on the input thread right after press 3, so the latest press is press 3 itself.
        val press3 = launcher.latestPressNow()
        handler.postGuarded(log, "Launch") { launcher.launch(target, runFallback, press3) }
    }

    /** Takes the held start out now: if the target opens, it's dropped by never running. */
    private fun heldStartFallback(): (() -> Unit)? {
        val held = heldStart ?: return null
        val call = held.take() ?: return null
        return { held.run(call) }
    }

    override fun checkTarget(target: TargetComponent) {
        handler.postGuarded(log, "Dry-run check") { launcher.check(target) }
    }

    /**
     * Drops any pending timer, for a hot reload. Launches already queued still run, so a press 3 that was consumed
     * still opens something.
     */
    fun release() {
        cancelTimer()
    }

    private companion object {
        val TIMER = Any()
    }
}
