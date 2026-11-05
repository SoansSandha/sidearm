package io.github.soanssandha.sidearm.hook.effect.fixture

import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.core.sequencer.CameraArgs
import io.github.soanssandha.sidearm.core.sequencer.Fallback
import io.github.soanssandha.sidearm.hook.effect.EffectRunner
import io.github.soanssandha.sidearm.hook.effect.HeldStart

/** Records every call in order. Timers fire only when a test calls [fireTimer]. */
internal class FakeEffectRunner : EffectRunner {

    sealed interface Call {
        data class ScheduleTimer(val deadline: Long) : Call
        data object CancelTimer : Call
        data class RunHeldCamera(val camera: CameraArgs) : Call
        data class HoldStart(val call: HeldStart.Call) : Call
        data object RunHeldStart : Call
        data object WakeDevice : Call
        data class LaunchTarget(val target: TargetComponent, val fallback: Fallback?) : Call
        data class CheckTarget(val target: TargetComponent) : Call
    }

    val calls = mutableListOf<Call>()

    /** Makes [cancelTimer] throw after recording the call. */
    var failCancelTimer = false

    private var lastTimer: (() -> Unit)? = null

    /**
     * Fires the most recently scheduled timer. Cancelling doesn't stop it, which is what happens to a timer that
     * is already running when it's cancelled.
     */
    fun fireTimer() = checkNotNull(lastTimer) { "No timer was scheduled" }()

    override fun scheduleTimer(deadline: Long, onFire: () -> Unit) {
        calls += Call.ScheduleTimer(deadline)
        lastTimer = onFire
    }

    override fun cancelTimer() {
        calls += Call.CancelTimer
        check(!failCancelTimer) { "cancelTimer failed" }
    }

    override fun runHeldCamera(camera: CameraArgs) {
        calls += Call.RunHeldCamera(camera)
    }

    override fun holdStart(call: HeldStart.Call) {
        calls += Call.HoldStart(call)
    }

    override fun runHeldStart() {
        calls += Call.RunHeldStart
    }

    override fun wakeDevice() {
        calls += Call.WakeDevice
    }

    /** What the last [launchTarget] would call if its target couldn't be opened and its start was pending. */
    var releasePendingStart: (() -> Unit)? = null
        private set

    override fun launchTarget(target: TargetComponent, fallback: Fallback?, releasePendingStart: () -> Unit) {
        calls += Call.LaunchTarget(target, fallback)
        this.releasePendingStart = releasePendingStart
    }

    override fun checkTarget(target: TargetComponent) {
        calls += Call.CheckTarget(target)
    }
}
