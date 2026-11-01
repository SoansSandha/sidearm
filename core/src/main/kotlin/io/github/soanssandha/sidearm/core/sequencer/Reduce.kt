package io.github.soanssandha.sidearm.core.sequencer

import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.config.TargetComponent

private const val TRIPLE_PRESS_TAPS = 3

/**
 * The press sequencer. On a power double-press while the phone is locked, it holds stock's camera launch for one
 * window; a third press in that window opens [Config.armedTarget] instead, and otherwise the held camera runs. While
 * the phone is unlocked the camera opens at once, as in stock, and a third press opens the target over it (holding
 * would lock the phone; ADR 0007). The rules are the state table in docs/plan.md §3.2.
 *
 * Pure and cheap, so the hook can call it under one lock. It fails open: anything unexpected while the camera is
 * held runs the held camera.
 */
fun reduce(state: State, event: Event, config: Config, timing: Timing): Transition = when (state) {
    is State.Idle -> state.on(event, config, timing)
    is State.HoldingCamera -> state.on(event, config)
}

private fun State.Idle.on(event: Event, config: Config, timing: Timing): Transition = when (event) {
    is Event.CameraRequested -> when {
        config.armedTarget == null -> Transition(this, listOf(Effect.RunCameraNow))
        event.startedLocked -> hold(event, timing)
        else -> Transition(State.Idle(generation, lastCameraPress = event.pressTime), listOf(Effect.RunCameraNow))
    }

    is Event.PowerDown -> if (isThirdPressAfterCamera(
            event,
            timing,
        )
    ) {
        openOverCamera(event, config)
    } else {
        Transition(this)
    }

    is Event.TimerFired, Event.ConfigChanged, Event.Released -> Transition(this)
}

private fun State.HoldingCamera.on(event: Event, config: Config): Transition = when (event) {
    is Event.PowerDown -> when {
        // Stock asks for the camera during press 2, so press 2 itself arrives after the hold began.
        event.eventTime == pressTime -> Transition(this)

        event.taps == TRIPLE_PRESS_TAPS -> {
            val target = config.armedTarget
            if (target != null) launch(target, event) else flush(cancelTimer = true)
        }

        else -> flush(cancelTimer = true)
    }

    is Event.TimerFired -> if (event.generation == generation) flush(cancelTimer = false) else Transition(this)

    Event.ConfigChanged -> if (config.armedTarget != null) Transition(this) else flush(cancelTimer = true)

    Event.Released -> flush(cancelTimer = true)

    is Event.CameraRequested -> {
        val flushed = flush(cancelTimer = true)
        flushed.copy(effects = flushed.effects + Effect.RunCameraNow)
    }
}

private fun State.Idle.hold(event: Event.CameraRequested, timing: Timing): Transition {
    val next = generation + 1
    val deadline = event.pressTime + timing.windowMs + timing.graceMs
    return Transition(
        State.HoldingCamera(next, event.pressTime, deadline, event.camera),
        listOf(Effect.ScheduleTimer(deadline, next), Effect.HoldCamera),
    )
}

/** Stock counted a third press within one window of a press 2 whose camera has already run. */
private fun State.Idle.isThirdPressAfterCamera(event: Event.PowerDown, timing: Timing): Boolean {
    val cameraPress = lastCameraPress ?: return false
    return event.taps == TRIPLE_PRESS_TAPS && event.eventTime - cameraPress < timing.windowMs
}

/**
 * Opens the target over the camera that's already opening. The press is consumed either way, so PhoneWindowManager
 * doesn't toggle the screen while the camera opens; once the feature is off, that's all it does.
 */
private fun State.Idle.openOverCamera(event: Event.PowerDown, config: Config): Transition {
    val target = config.armedTarget ?: return Transition(this, listOf(Effect.ConsumePress))
    return Transition(
        State.Idle(generation),
        listOfNotNull(
            Effect.ConsumePress,
            Effect.WakeDevice.takeUnless { event.interactive },
            Effect.LaunchTarget(target, fallback = null),
        ),
    )
}

private fun State.HoldingCamera.launch(target: TargetComponent, event: Event.PowerDown) = Transition(
    State.Idle(generation),
    listOfNotNull(
        Effect.CancelTimer(generation),
        Effect.ConsumePress,
        Effect.WakeDevice.takeUnless { event.interactive },
        Effect.LaunchTarget(target, fallback = camera),
    ),
)

/** Ends the hold by running the held camera. */
private fun State.HoldingCamera.flush(cancelTimer: Boolean) = Transition(
    State.Idle(generation, lastCameraPress = pressTime),
    listOfNotNull(Effect.CancelTimer(generation).takeIf { cancelTimer }, Effect.RunHeldCamera(camera)),
)
