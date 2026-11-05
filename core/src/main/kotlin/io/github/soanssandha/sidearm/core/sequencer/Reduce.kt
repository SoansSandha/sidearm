package io.github.soanssandha.sidearm.core.sequencer

import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.config.LaunchMode
import io.github.soanssandha.sidearm.core.config.TargetComponent

private const val TRIPLE_PRESS_TAPS = 3

/**
 * The press sequencer. On a power double-press while the phone is locked, it holds stock's camera launch for one
 * window; a third press in that window opens [Config.armedTarget] instead, and otherwise the held camera runs. While
 * the phone is unlocked the camera request goes to SystemUI at once, as in stock (holding it would lock the phone;
 * ADR 0007). Then, in the classic launch mode, a third press opens the target over the camera. In the clean mode,
 * SystemUI's camera activity start is held instead, so a third press opens the target with no camera first
 * (ADR 0010). The rules are the state tables in docs/plan.md §3.2.
 *
 * Pure and cheap, so the hook can call it under one lock. It fails open: anything unexpected while a camera is held
 * runs the held camera.
 */
fun reduce(state: State, event: Event, config: Config, timing: Timing): Transition = when (state) {
    is State.Idle -> state.on(event, config, timing)
    is State.HoldingCamera -> state.on(event, config)
    is State.AwaitingStart -> state.on(event, config, timing)
    is State.HoldingStart -> state.on(event, config)
    is State.DroppingStart -> state.on(event, config, timing)
}

/** Armed, in the clean launch mode. */
private val Config.armedClean: Boolean
    get() = armedTarget != null && launchMode == LaunchMode.CLEAN

private fun State.Idle.on(event: Event, config: Config, timing: Timing): Transition = when (event) {
    is Event.CameraRequested -> when {
        config.armedTarget == null -> Transition(this, listOf(Effect.RunCameraNow))
        event.startedLocked -> hold(event, timing)
        config.launchMode == LaunchMode.CLEAN -> awaitStart(event, timing)
        else -> Transition(State.Idle(generation, lastCameraPress = event.pressTime), listOf(Effect.RunCameraNow))
    }

    is Event.CameraStartRequested -> Transition(this, listOf(Effect.RunStartNow))

    is Event.PowerDown -> if (isThirdPressAfterCamera(
            event,
            timing,
        )
    ) {
        openOverCamera(event, config)
    } else {
        Transition(this)
    }

    is Event.TimerFired, Event.ConfigChanged, Event.Released, Event.TargetUnavailable -> Transition(this)
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

    // On a locked phone SystemUI starts the secure camera, which the hook doesn't match; this one isn't ours.
    is Event.CameraStartRequested -> Transition(this, listOf(Effect.RunStartNow))

    Event.TargetUnavailable -> Transition(this)
}

private fun State.AwaitingStart.on(event: Event, config: Config, timing: Timing): Transition = when (event) {
    is Event.CameraStartRequested -> Transition(
        State.HoldingStart(generation, pressTime, deadline),
        listOf(Effect.HoldStart),
    )

    is Event.PowerDown -> {
        val target = config.armedTarget
        when {
            event.eventTime == pressTime -> Transition(this)

            event.taps == TRIPLE_PRESS_TAPS && target != null -> launchBeforeStart(target, event, timing)

            // Not ours to hold any more: the start goes through when it comes, and the press is handled as if the
            // camera had already run.
            else -> stopWaiting().then(event, config, timing)
        }
    }

    is Event.TimerFired -> if (event.generation == generation) {
        Transition(State.Idle(generation, lastCameraPress = pressTime))
    } else {
        Transition(this)
    }

    Event.ConfigChanged -> if (config.armedClean) Transition(this) else stopWaiting()

    Event.Released -> stopWaiting()

    is Event.CameraRequested -> stopWaiting().then(event, config, timing)

    Event.TargetUnavailable -> Transition(this)
}

private fun State.HoldingStart.on(event: Event, config: Config): Transition = when (event) {
    is Event.PowerDown -> when {
        event.eventTime == pressTime -> Transition(this)

        event.taps == TRIPLE_PRESS_TAPS -> {
            val target = config.armedTarget
            if (target != null) launchOverHeldStart(target, event) else flushStart(cancelTimer = true)
        }

        else -> flushStart(cancelTimer = true)
    }

    is Event.TimerFired -> if (event.generation == generation) flushStart(cancelTimer = false) else Transition(this)

    Event.ConfigChanged -> if (config.armedClean) Transition(this) else flushStart(cancelTimer = true)

    Event.Released -> flushStart(cancelTimer = true)

    is Event.CameraStartRequested -> {
        val flushed = flushStart(cancelTimer = true)
        flushed.copy(effects = flushed.effects + Effect.RunStartNow)
    }

    is Event.CameraRequested -> {
        val flushed = flushStart(cancelTimer = true)
        flushed.copy(effects = flushed.effects + Effect.RunCameraNow)
    }

    // Its fallback is the held start itself, which the hook runs directly.
    Event.TargetUnavailable -> Transition(this)
}

private fun State.DroppingStart.on(event: Event, config: Config, timing: Timing): Transition = when (event) {
    is Event.CameraStartRequested -> Transition(
        State.Idle(generation),
        listOf(Effect.CancelTimer(generation), if (event.time <= until) Effect.DropStart else Effect.RunStartNow),
    )

    // The window ran out with no start, so there's nothing left to drop.
    is Event.TimerFired -> if (event.generation == generation) Transition(State.Idle(generation)) else Transition(this)

    // Press 3's target couldn't be opened: the start runs when it comes, so press 3 still opens something.
    Event.TargetUnavailable, Event.Released -> stopDropping()

    // Presses 4 and 5 of Emergency SOS go to stock, and a late start still mustn't open over the countdown.
    is Event.PowerDown, Event.ConfigChanged -> Transition(this)

    is Event.CameraRequested -> stopDropping().then(event, config, timing)
}

private fun State.DroppingStart.stopDropping() = Transition(
    State.Idle(generation),
    listOf(Effect.CancelTimer(generation)),
)

/** Runs [event] through the state this transition leads to, keeping this transition's effects first. */
private fun Transition.then(event: Event, config: Config, timing: Timing): Transition {
    val next = reduce(state, event, config, timing)
    return Transition(next.state, effects + next.effects)
}

private fun State.Idle.hold(event: Event.CameraRequested, timing: Timing): Transition {
    val next = generation + 1
    val deadline = event.pressTime + timing.windowMs + timing.graceMs
    return Transition(
        State.HoldingCamera(next, event.pressTime, deadline, event.camera),
        listOf(Effect.ScheduleTimer(deadline, next), Effect.HoldCamera),
    )
}

private fun State.Idle.awaitStart(event: Event.CameraRequested, timing: Timing): Transition {
    val next = generation + 1
    val deadline = event.pressTime + timing.windowMs + timing.graceMs
    return Transition(
        State.AwaitingStart(next, event.pressTime, deadline),
        listOf(Effect.ScheduleTimer(deadline, next), Effect.RunCameraNow),
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
        Effect.LaunchTarget(target, fallback = Fallback.Camera(camera)),
    ),
)

/** Ends the hold by running the held camera. */
private fun State.HoldingCamera.flush(cancelTimer: Boolean) = Transition(
    State.Idle(generation, lastCameraPress = pressTime),
    listOfNotNull(Effect.CancelTimer(generation).takeIf { cancelTimer }, Effect.RunHeldCamera(camera)),
)

/**
 * Press 3 came before SystemUI's camera start: open the target, and drop that start if it comes by the end of the
 * drop window. The window takes a new generation, so a deadline timer that was already running when cancelled
 * can't end it early.
 */
private fun State.AwaitingStart.launchBeforeStart(
    target: TargetComponent,
    event: Event.PowerDown,
    timing: Timing,
): Transition {
    val next = generation + 1
    val until = pressTime + timing.lateStartMs
    return Transition(
        State.DroppingStart(next, until),
        listOfNotNull(
            Effect.CancelTimer(generation),
            Effect.ScheduleTimer(until, next),
            Effect.ConsumePress,
            Effect.WakeDevice.takeUnless { event.interactive },
            Effect.LaunchTarget(target, fallback = Fallback.PendingStart),
        ),
    )
}

/** Stops waiting for SystemUI's camera start, which then goes through when it comes. */
private fun State.AwaitingStart.stopWaiting() = Transition(
    State.Idle(generation, lastCameraPress = pressTime),
    listOf(Effect.CancelTimer(generation)),
)

private fun State.HoldingStart.launchOverHeldStart(target: TargetComponent, event: Event.PowerDown) = Transition(
    State.Idle(generation),
    listOfNotNull(
        Effect.CancelTimer(generation),
        Effect.ConsumePress,
        Effect.WakeDevice.takeUnless { event.interactive },
        Effect.LaunchTarget(target, fallback = Fallback.HeldStart),
    ),
)

/** Ends the hold by running the held camera start. */
private fun State.HoldingStart.flushStart(cancelTimer: Boolean) = Transition(
    State.Idle(generation, lastCameraPress = pressTime),
    listOfNotNull(Effect.CancelTimer(generation).takeIf { cancelTimer }, Effect.RunHeldStart),
)
