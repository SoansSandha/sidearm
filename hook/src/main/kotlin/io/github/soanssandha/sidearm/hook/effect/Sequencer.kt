package io.github.soanssandha.sidearm.hook.effect

import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.sequencer.Effect
import io.github.soanssandha.sidearm.core.sequencer.Event
import io.github.soanssandha.sidearm.core.sequencer.State
import io.github.soanssandha.sidearm.core.sequencer.Timing
import io.github.soanssandha.sidearm.core.sequencer.reduce

/**
 * The press sequencer as it runs in system_server. It owns the sequencer state and the config, runs [reduce] under
 * one lock, and then runs the effects outside that lock, in order (docs/plan.md §3.2). Key events arrive on the
 * input thread, and timers and config changes on the main looper.
 *
 * A dry run is latched for each sequence, when its camera request arrives: a sequence that started as a dry run
 * stays one until it ends, whatever the config does in between. Otherwise switching modes mid-hold would open
 * stock's camera a second time, or never open a held one.
 *
 * It logs every decision while the feature is armed, and otherwise only decisions that change what stock does, so
 * the log stays quiet while the feature is off.
 */
internal class Sequencer(private val timing: Timing, private val runner: EffectRunner, private val log: HookLog) {

    private val lock = Any()
    private var state: State = State.INITIAL
    private var config = Config.DEFAULT
    private var dryRun = false
    private var retired = false

    /**
     * Handles an event from a hooked call. Returns true when that call should return true instead of stock's
     * result: the camera request is held (`handleCameraGesture` skips the original) or the press is consumed
     * (`interceptPowerKeyDown`). Always false during a dry run.
     */
    fun dispatch(event: Event): Boolean = carryOut(decide(event))

    fun updateConfig(next: Config) {
        carryOut(decide(Event.ConfigChanged) { config = next })
    }

    /** Runs any held camera, then leaves every later call to stock. For a hot reload, before this copy retires. */
    fun retire() {
        carryOut(decide(Event.Released) { retired = true })
    }

    private fun decide(event: Event, update: () -> Unit = {}): Decision? = synchronized(lock) {
        if (retired) return null
        update()
        // A camera request while idle starts a sequence, held or not; one while holding only ends the old hold.
        if (event is Event.CameraRequested && state is State.Idle) dryRun = config.dryRun
        val transition = reduce(state, event, config, timing)
        state = transition.state
        Decision(event, transition.effects, dryRun, armed = config.armedTarget != null)
    }

    private fun carryOut(decision: Decision?): Boolean {
        if (decision == null) return false
        if (decision.isWorthLogging()) runCatching { log.info(decision.describe()) }
        decision.effects.forEach { effect ->
            runCatching { execute(effect, decision.dryRun) }.onFailure { log.warn("$effect failed", it) }
        }
        return !decision.dryRun && (Effect.HoldCamera in decision.effects || Effect.ConsumePress in decision.effects)
    }

    private fun execute(effect: Effect, dryRun: Boolean) {
        when (effect) {
            is Effect.ScheduleTimer -> runner.scheduleTimer(effect.deadline) {
                dispatch(Event.TimerFired(effect.generation))
            }

            is Effect.CancelTimer -> runner.cancelTimer()

            // These decide what the hooked call returns; see dispatch.
            Effect.HoldCamera, Effect.RunCameraNow, Effect.ConsumePress -> Unit

            is Effect.RunHeldCamera -> if (!dryRun) runner.runHeldCamera(effect.camera)

            Effect.WakeDevice -> if (!dryRun) runner.wakeDevice()

            is Effect.LaunchTarget ->
                if (dryRun) runner.checkTarget(effect.target) else runner.launchTarget(effect.target, effect.fallback)
        }
    }

    private class Decision(val event: Event, val effects: List<Effect>, val dryRun: Boolean, val armed: Boolean) {
        fun isWorthLogging(): Boolean =
            if (armed) effects.isNotEmpty() || event is Event.PowerDown else effects.any { it != Effect.RunCameraNow }

        fun describe(): String = when {
            effects.isEmpty() -> "$event -> stock"
            dryRun -> "Dry run: $event -> ${effects.joinToString()}"
            else -> "$event -> ${effects.joinToString()}"
        }
    }
}
