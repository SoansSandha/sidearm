package io.github.soanssandha.sidearm.core.sequencer.fixture

import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.core.sequencer.CameraArgs
import io.github.soanssandha.sidearm.core.sequencer.Effect
import io.github.soanssandha.sidearm.core.sequencer.Event
import io.github.soanssandha.sidearm.core.sequencer.State
import io.github.soanssandha.sidearm.core.sequencer.Timing
import io.github.soanssandha.sidearm.core.sequencer.reduce

/**
 * Plays power presses through [reduce] in the order the hook sees them: stock counts each press first (asking for
 * the camera on the second), then the hook reports the press. Timers run on a fake uptime clock, and every visible
 * result is recorded in [outcomes].
 *
 * With [cancelledTimersStillFire], a cancelled timer is delivered anyway, as when the handler had already picked it
 * up before the cancel landed.
 */
class SequenceDriver(
    private var config: Config,
    private val timing: Timing = Timing(),
    private val cameraDoublePress: Boolean = true,
    private val cancelledTimersStillFire: Boolean = false,
) {
    private val stock = StockTapCounter(timing.windowMs)
    private val pendingTimers = mutableMapOf<Long, Long>()
    private val recorded = mutableListOf<Outcome>()
    private var state: State = State.INITIAL
    private var now = 0L

    val outcomes: List<Outcome> get() = recorded.toList()

    /** A press at uptime [at] that reaches the hook [seenAfterMs] later. Timers due by then fire first. */
    fun press(at: Long, interactive: Boolean = true, seenAfterMs: Long = 0) {
        advanceTo(at + seenAfterMs)
        val taps = stock.count(at)
        if (cameraDoublePress && taps == CAMERA_TAPS) dispatch(Event.CameraRequested(pressTime = at, camera = CAMERA))
        dispatch(Event.PowerDown(eventTime = at, taps = taps, interactive = interactive))
    }

    fun changeConfig(at: Long, newConfig: Config) {
        advanceTo(at)
        config = newConfig
        dispatch(Event.ConfigChanged)
    }

    fun release(at: Long) {
        advanceTo(at)
        dispatch(Event.Released)
    }

    fun advanceTo(time: Long) {
        require(time >= now) { "Time went backwards: $time < $now" }
        while (true) {
            val (generation, deadline) = pendingTimers.entries.minByOrNull { it.value } ?: break
            if (deadline > time) break
            pendingTimers.remove(generation)
            now = deadline
            dispatch(Event.TimerFired(generation))
        }
        now = time
    }

    private fun dispatch(event: Event) {
        val transition = reduce(state, event, config, timing)
        state = transition.state
        transition.effects.forEach { apply(it, event) }
    }

    private fun apply(effect: Effect, event: Event) {
        when (effect) {
            is Effect.ScheduleTimer -> pendingTimers[effect.generation] = effect.deadline

            is Effect.CancelTimer -> if (!cancelledTimersStillFire) pendingTimers.remove(effect.generation)

            Effect.HoldCamera -> check(event is Event.CameraRequested) { "HoldCamera for $event" }

            Effect.RunCameraNow, is Effect.RunHeldCamera -> recorded += Outcome.CameraOpened(now)

            Effect.ConsumePress -> {
                check(event is Event.PowerDown) { "ConsumePress for $event" }
                recorded += Outcome.PressConsumed(event.eventTime)
            }

            Effect.WakeDevice -> recorded += Outcome.DeviceWoken

            is Effect.LaunchTarget -> recorded += Outcome.TargetLaunched(effect.target, now)
        }
    }

    sealed interface Outcome {
        data class CameraOpened(val at: Long) : Outcome
        data class TargetLaunched(val target: TargetComponent, val at: Long) : Outcome
        data class PressConsumed(val pressAt: Long) : Outcome
        data object DeviceWoken : Outcome
    }

    private companion object {
        const val CAMERA_TAPS = 2
        val CAMERA = CameraArgs(useWakelock = false, source = 1)
    }
}
