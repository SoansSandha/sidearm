package io.github.soanssandha.sidearm.hook.effect

import android.os.Handler
import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.core.sequencer.CameraArgs

/**
 * Runs effects in system_server. Timers and launches go to [handler], which is on system_server's main looper:
 * the hook adds no looper of its own, and never opens an app from the input thread (docs/plan.md §3.3). Held
 * cameras run on whichever thread released them, as stock runs the camera on the input thread.
 */
internal class SystemEffectRunner(
    private val handler: Handler,
    private val heldCamera: HeldCamera,
    private val launcher: TargetLauncher,
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

    override fun wakeDevice() {
        log.info("Not waking the device: waking on press 3 arrives in Phase 3")
    }

    override fun launchTarget(target: TargetComponent, fallback: CameraArgs?) {
        val runFallback = fallback?.let { camera -> { heldCamera.run(camera) } }
        handler.postGuarded(log, "Launch") { launcher.launch(target, runFallback) }
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
