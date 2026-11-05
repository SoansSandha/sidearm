package io.github.soanssandha.sidearm.hook.effect

import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.config.LaunchMode
import io.github.soanssandha.sidearm.core.sequencer.Effect
import io.github.soanssandha.sidearm.core.sequencer.Event
import io.github.soanssandha.sidearm.core.sequencer.State
import io.github.soanssandha.sidearm.core.sequencer.Timing
import io.github.soanssandha.sidearm.core.sequencer.reduce

/**
 * The press sequencer as it runs in system_server. It owns the sequencer state and the config, runs [reduce] under
 * one lock, and then runs the effects outside that lock, in order (docs/plan.md §3.2). Key events arrive on the
 * input thread, SystemUI's camera starts on a binder thread, and timers and config changes on the main looper.
 *
 * A dry run is latched for each sequence, when its camera request arrives: a sequence that started as a dry run
 * stays one until it ends, whatever the config does in between. Otherwise switching modes mid-hold would open
 * stock's camera a second time, or never open a held one.
 *
 * Until [cleanAvailable] is set, the clean launch mode reads as classic: without the activity-start hook nothing
 * would report SystemUI's camera start, and the classic mode is the one that works without it.
 *
 * It logs every decision while the feature is armed, and otherwise only decisions that change what stock does, so
 * the log stays quiet while the feature is off.
 */
internal class Sequencer(private val timing: Timing, private val runner: EffectRunner, private val log: HookLog) {

    private val lock = Any()
    private var state: State = State.INITIAL
    private var config = Config.DEFAULT
    private var requested = Config.DEFAULT
    private var dryRun = false
    private var retired = false

    /** Whether the activity-start hook is installed. Set once at install, before the config is first read. */
    @Volatile
    var cleanAvailable = false

    /**
     * Until when the activity-start hook should report SystemUI's camera starts, in uptime ms, or [NOT_WATCHING].
     * Read without the lock on every activity start on the phone, so it's one volatile field.
     */
    @Volatile
    var startWatchUntil = NOT_WATCHING
        private set

    /**
     * Handles an event from a hooked call. Returns true when that call should return true instead of stock's
     * result: the camera request is held (`handleCameraGesture` skips the original) or the press is consumed
     * (`interceptPowerKeyDown`). Always false during a dry run.
     */
    fun dispatch(event: Event): Boolean = carryOut(decide(event))

    /**
     * Handles SystemUI's camera start at uptime [time]. Returns true when `startActivityAsUser` should return success
     * without running: the start is held (in [call], stored before the lock is released) or dropped. Always false
     * during a dry run.
     */
    fun dispatchStart(time: Long, call: HeldStart.Call): Boolean = carryOut(
        decide(Event.CameraStartRequested(time), onDecided = { effects ->
            if (Effect.HoldStart in effects) runner.holdStart(call)
        }),
    )

    fun updateConfig(next: Config) {
        if (next.launchMode == LaunchMode.CLEAN && !cleanAvailable) {
            runCatching { log.warn("Clean launch isn't available on this build; using classic") }
        }
        carryOut(decide(Event.ConfigChanged) { requested = next })
    }

    /** Runs any held camera, then leaves every later call to stock. For a hot reload, before this copy retires. */
    fun retire() {
        carryOut(decide(Event.Released) { retired = true })
    }

    /**
     * Runs [update], then reduces [event]. [onDecided] runs inside the lock with the effects, unless this is a dry
     * run, for anything that must happen before another event can see the new state.
     */
    private fun decide(event: Event, onDecided: (List<Effect>) -> Unit = {}, update: () -> Unit = {}): Decision? =
        synchronized(lock) {
            if (retired) return null
            update()
            config = effective(requested)
            // A camera request starts a new sequence unless it arrives during a hold, which it only ends.
            if (event is Event.CameraRequested && state !is State.HoldingCamera && state !is State.HoldingStart) {
                dryRun = config.dryRun
            }
            val transition = reduce(state, event, config, timing)
            state = transition.state
            // update() may have just retired this copy (retire()); a retired copy never watches.
            startWatchUntil = if (retired) NOT_WATCHING else state.startWatchUntil(timing) ?: NOT_WATCHING
            if (!dryRun) onDecided(transition.effects)
            Decision(event, transition.effects, dryRun, armed = config.armedTarget != null)
        }

    private fun effective(next: Config): Config {
        val usable = cleanAvailable || next.launchMode == LaunchMode.CLASSIC
        return if (usable) next else next.copy(launchMode = LaunchMode.CLASSIC)
    }

    private fun carryOut(decision: Decision?): Boolean {
        if (decision == null) return false
        if (decision.isWorthLogging()) runCatching { log.info(decision.describe()) }
        decision.effects.forEach { effect ->
            runCatching { execute(effect, decision.dryRun) }.onFailure { log.warn("$effect failed", it) }
        }
        return !decision.dryRun && decision.effects.any { it in ANSWERS }
    }

    private fun execute(effect: Effect, dryRun: Boolean) {
        when (effect) {
            is Effect.ScheduleTimer -> runner.scheduleTimer(effect.deadline) {
                dispatch(Event.TimerFired(effect.generation))
            }

            is Effect.CancelTimer -> runner.cancelTimer()

            // These decide what the hooked call returns; see dispatch and dispatchStart. A held start is stored
            // inside the lock.
            Effect.HoldCamera, Effect.RunCameraNow, Effect.ConsumePress,
            Effect.HoldStart, Effect.RunStartNow, Effect.DropStart,
            -> Unit

            is Effect.RunHeldCamera -> if (!dryRun) runner.runHeldCamera(effect.camera)

            Effect.RunHeldStart -> if (!dryRun) runner.runHeldStart()

            Effect.WakeDevice -> if (!dryRun) runner.wakeDevice()

            is Effect.LaunchTarget ->
                if (dryRun) {
                    runner.checkTarget(effect.target)
                } else {
                    runner.launchTarget(effect.target, effect.fallback) { dispatch(Event.TargetUnavailable) }
                }
        }
    }

    private class Decision(val event: Event, val effects: List<Effect>, val dryRun: Boolean, val armed: Boolean) {
        fun isWorthLogging(): Boolean = if (armed) {
            effects.isNotEmpty() || event is Event.PowerDown
        } else {
            effects.any { it != Effect.RunCameraNow && it != Effect.RunStartNow }
        }

        fun describe(): String = when {
            effects.isEmpty() -> "$event -> stock"
            dryRun -> "Dry run: $event -> ${effects.joinToString()}"
            else -> "$event -> ${effects.joinToString()}"
        }
    }

    companion object {
        /** [startWatchUntil] when no clean sequence expects SystemUI's camera start. */
        const val NOT_WATCHING = Long.MIN_VALUE

        /** Effects that make the hooked call return without running stock. */
        private val ANSWERS = setOf(Effect.HoldCamera, Effect.ConsumePress, Effect.HoldStart, Effect.DropStart)
    }
}
