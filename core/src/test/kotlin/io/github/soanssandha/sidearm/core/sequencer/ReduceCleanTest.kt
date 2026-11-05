package io.github.soanssandha.sidearm.core.sequencer

import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.config.LaunchMode
import io.github.soanssandha.sidearm.core.config.TargetComponent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * One test per row of the clean launch mode's state table in docs/plan.md §3.2: on an unlocked phone, stock's camera
 * request goes through at once, and SystemUI's camera activity start is what gets held (ADR 0010).
 */
class ReduceCleanTest {

    private val target = TargetComponent("com.example.app", "com.example.app.Main")
    private val clean = Config(enabled = true, target = target, launchMode = LaunchMode.CLEAN)
    private val classic = clean.copy(launchMode = LaunchMode.CLASSIC)
    private val camera = CameraArgs(useWakelock = false, source = 1)
    private val timing = Timing(windowMs = 300, graceMs = 50, lateStartMs = 2_000)

    private val awaiting = State.AwaitingStart(generation = 1, pressTime = 1_000, deadline = 1_350)
    private val holding = State.HoldingStart(generation = 1, pressTime = 1_000, deadline = 1_350)
    private val dropping = State.DroppingStart(generation = 2, until = 3_000)

    /** After press 2 at 1000, whose camera start went (or will go) through. */
    private val idleAfterCamera = State.Idle(generation = 1, lastCameraPress = 1_000)

    private fun reduce(state: State, event: Event, config: Config = clean) = reduce(state, event, config, timing)

    private fun assertUnchanged(state: State, event: Event, config: Config = clean) {
        assertEquals(Transition(state, emptyList()), reduce(state, event, config))
    }

    private fun cameraRequested(pressTime: Long = 1_000, startedLocked: Boolean = false) =
        Event.CameraRequested(pressTime = pressTime, camera = camera, startedLocked = startedLocked)

    private fun press(eventTime: Long, taps: Int, interactive: Boolean = true) =
        Event.PowerDown(eventTime = eventTime, taps = taps, interactive = interactive)

    private fun startRequested(time: Long = 1_080) = Event.CameraStartRequested(time)

    // Idle and HoldingCamera

    @Test
    fun `an unlocked camera request runs at once and waits for SystemUI's camera start`() {
        assertEquals(
            Transition(
                State.AwaitingStart(generation = 8, pressTime = 1_000, deadline = 1_350),
                listOf(Effect.ScheduleTimer(deadline = 1_350, generation = 8), Effect.RunCameraNow),
            ),
            reduce(State.Idle(generation = 7), cameraRequested()),
        )
    }

    @Test
    fun `a locked camera request is held in GestureLauncherService, as in classic`() {
        assertEquals(
            reduce(State.INITIAL, cameraRequested(startedLocked = true), classic),
            reduce(State.INITIAL, cameraRequested(startedLocked = true), clean),
        )
    }

    @Test
    fun `while not armed, clean changes nothing`() {
        listOf(clean.copy(enabled = false), clean.copy(target = null)).forEach { config ->
            assertEquals(
                Transition(State.INITIAL, listOf(Effect.RunCameraNow)),
                reduce(State.INITIAL, cameraRequested(), config),
                "config $config",
            )
        }
    }

    @Test
    fun `a camera start nobody is waiting for runs at once`() {
        listOf(State.INITIAL, idleAfterCamera, State.HoldingCamera(1, 1_000, 1_350, camera)).forEach { state ->
            assertEquals(Transition(state, listOf(Effect.RunStartNow)), reduce(state, startRequested()), "in $state")
        }
    }

    // AwaitingStart

    @Test
    fun `SystemUI's camera start is held until the press window ends`() {
        assertEquals(Transition(holding, listOf(Effect.HoldStart)), reduce(awaiting, startRequested()))
    }

    @Test
    fun `press 2 itself, arriving after its camera request, changes nothing while waiting`() {
        assertUnchanged(awaiting, press(eventTime = 1_000, taps = 2))
    }

    @Test
    fun `press 3 before the start arrives opens the target and drops the start when it comes`() {
        // A new generation for the drop window, so a deadline timer that was already running when cancelled can't
        // end it early.
        assertEquals(
            Transition(
                State.DroppingStart(generation = 2, until = 3_000),
                listOf(
                    Effect.CancelTimer(generation = 1),
                    Effect.ScheduleTimer(deadline = 3_000, generation = 2),
                    Effect.ConsumePress,
                    Effect.LaunchTarget(target, fallback = Fallback.PendingStart),
                ),
            ),
            reduce(awaiting, press(eventTime = 1_200, taps = 3)),
        )
    }

    @Test
    fun `press 3 before the start wakes the device if it isn't interactive`() {
        assertEquals(
            listOf(
                Effect.CancelTimer(generation = 1),
                Effect.ScheduleTimer(deadline = 3_000, generation = 2),
                Effect.ConsumePress,
                Effect.WakeDevice,
                Effect.LaunchTarget(target, fallback = Fallback.PendingStart),
            ),
            reduce(awaiting, press(eventTime = 1_200, taps = 3, interactive = false)).effects,
        )
    }

    @Test
    fun `press 3 after the feature was turned off only stops the screen toggle while the camera opens`() {
        assertEquals(
            Transition(idleAfterCamera, listOf(Effect.CancelTimer(generation = 1), Effect.ConsumePress)),
            reduce(awaiting, press(eventTime = 1_200, taps = 3), clean.copy(enabled = false)),
        )
    }

    @Test
    fun `any other press stops waiting, and the start goes through when it comes`() {
        assertEquals(
            Transition(idleAfterCamera, listOf(Effect.CancelTimer(generation = 1))),
            reduce(awaiting, press(eventTime = 1_900, taps = 1)),
        )
    }

    @Test
    fun `the window ending before the start arrives stops waiting`() {
        assertEquals(Transition(idleAfterCamera), reduce(awaiting, Event.TimerFired(generation = 1)))
        assertUnchanged(awaiting, Event.TimerFired(generation = 0))
    }

    @Test
    fun `switching to classic or turning the feature off stops waiting`() {
        assertUnchanged(awaiting, Event.ConfigChanged)
        listOf(classic, clean.copy(enabled = false)).forEach { config ->
            assertEquals(
                Transition(idleAfterCamera, listOf(Effect.CancelTimer(generation = 1))),
                reduce(awaiting, Event.ConfigChanged, config),
                "config $config",
            )
        }
    }

    @Test
    fun `releasing the hook stops waiting`() {
        assertEquals(
            Transition(idleAfterCamera, listOf(Effect.CancelTimer(generation = 1))),
            reduce(awaiting, Event.Released),
        )
    }

    @Test
    fun `a new camera request while waiting starts a new sequence`() {
        assertEquals(
            Transition(
                State.AwaitingStart(generation = 2, pressTime = 2_000, deadline = 2_350),
                listOf(
                    Effect.CancelTimer(generation = 1),
                    Effect.ScheduleTimer(deadline = 2_350, generation = 2),
                    Effect.RunCameraNow,
                ),
            ),
            reduce(awaiting, cameraRequested(pressTime = 2_000)),
        )
    }

    // HoldingStart

    @Test
    fun `press 2 itself changes nothing while the start is held`() {
        assertUnchanged(holding, press(eventTime = 1_000, taps = 2))
    }

    @Test
    fun `press 3 opens the target, with the held start as its fallback`() {
        assertEquals(
            Transition(
                State.Idle(generation = 1),
                listOf(
                    Effect.CancelTimer(generation = 1),
                    Effect.ConsumePress,
                    Effect.LaunchTarget(target, fallback = Fallback.HeldStart),
                ),
            ),
            reduce(holding, press(eventTime = 1_200, taps = 3)),
        )
    }

    @Test
    fun `press 3 with the start held wakes the device if it isn't interactive`() {
        assertEquals(
            listOf(
                Effect.CancelTimer(generation = 1),
                Effect.ConsumePress,
                Effect.WakeDevice,
                Effect.LaunchTarget(target, fallback = Fallback.HeldStart),
            ),
            reduce(holding, press(eventTime = 1_200, taps = 3, interactive = false)).effects,
        )
    }

    @Test
    fun `press 3 after the feature was turned off runs the held start`() {
        assertEquals(
            Transition(idleAfterCamera, listOf(Effect.CancelTimer(generation = 1), Effect.RunHeldStart)),
            reduce(holding, press(eventTime = 1_200, taps = 3), clean.copy(target = null)),
        )
    }

    @Test
    fun `any other press runs the held start`() {
        assertEquals(
            Transition(idleAfterCamera, listOf(Effect.CancelTimer(generation = 1), Effect.RunHeldStart)),
            reduce(holding, press(eventTime = 1_900, taps = 1)),
        )
    }

    @Test
    fun `the window ending runs the held start`() {
        assertEquals(
            Transition(idleAfterCamera, listOf(Effect.RunHeldStart)),
            reduce(holding, Event.TimerFired(generation = 1)),
        )
        assertUnchanged(holding, Event.TimerFired(generation = 0))
    }

    @Test
    fun `switching to classic or turning the feature off runs the held start`() {
        assertUnchanged(holding, Event.ConfigChanged)
        listOf(classic, clean.copy(enabled = false)).forEach { config ->
            assertEquals(
                Transition(idleAfterCamera, listOf(Effect.CancelTimer(generation = 1), Effect.RunHeldStart)),
                reduce(holding, Event.ConfigChanged, config),
                "config $config",
            )
        }
    }

    @Test
    fun `releasing the hook runs the held start`() {
        assertEquals(
            Transition(idleAfterCamera, listOf(Effect.CancelTimer(generation = 1), Effect.RunHeldStart)),
            reduce(holding, Event.Released),
        )
    }

    @Test
    fun `another camera start while one is held runs both`() {
        assertEquals(
            Transition(
                idleAfterCamera,
                listOf(Effect.CancelTimer(generation = 1), Effect.RunHeldStart, Effect.RunStartNow),
            ),
            reduce(holding, startRequested(time = 1_100)),
        )
    }

    @Test
    fun `a new camera request while a start is held runs the held start and the new request`() {
        assertEquals(
            Transition(
                idleAfterCamera,
                listOf(Effect.CancelTimer(generation = 1), Effect.RunHeldStart, Effect.RunCameraNow),
            ),
            reduce(holding, cameraRequested(pressTime = 2_000)),
        )
    }

    // DroppingStart

    @Test
    fun `a camera start that comes after press 3 opened the target is dropped`() {
        assertEquals(
            Transition(State.Idle(generation = 2), listOf(Effect.CancelTimer(generation = 2), Effect.DropStart)),
            reduce(dropping, startRequested(time = 3_000)),
        )
    }

    @Test
    fun `a camera start after the drop window is someone else's and runs`() {
        assertEquals(
            Transition(State.Idle(generation = 2), listOf(Effect.CancelTimer(generation = 2), Effect.RunStartNow)),
            reduce(dropping, startRequested(time = 3_001)),
        )
    }

    @Test
    fun `the drop window ending with no start stops dropping`() {
        assertEquals(Transition(State.Idle(generation = 2)), reduce(dropping, Event.TimerFired(generation = 2)))
        assertUnchanged(dropping, Event.TimerFired(generation = 1))
    }

    @Test
    fun `a target that can't be opened lets the late start through, so press 3 still does something`() {
        assertEquals(
            Transition(State.Idle(generation = 2), listOf(Effect.CancelTimer(generation = 2))),
            reduce(dropping, Event.TargetUnavailable),
        )
    }

    @Test
    fun `presses after press 3 go to stock, and a late start is still dropped`() {
        // Presses 4 and 5 of an Emergency SOS sequence: nothing must open over the countdown.
        assertUnchanged(dropping, press(eventTime = 1_400, taps = 4))
        assertUnchanged(dropping, press(eventTime = 1_600, taps = 5))
    }

    @Test
    fun `config changes leave a late start to be dropped`() {
        assertUnchanged(dropping, Event.ConfigChanged, classic)
    }

    @Test
    fun `releasing the hook stops dropping`() {
        assertEquals(
            Transition(State.Idle(generation = 2), listOf(Effect.CancelTimer(generation = 2))),
            reduce(dropping, Event.Released),
        )
    }

    @Test
    fun `a new camera request starts a new sequence`() {
        val fromIdle = reduce(State.Idle(generation = 2), cameraRequested(pressTime = 9_000))

        assertEquals(
            fromIdle.copy(effects = listOf(Effect.CancelTimer(generation = 2)) + fromIdle.effects),
            reduce(dropping, cameraRequested(pressTime = 9_000)),
        )
    }

    @Test
    fun `an unavailable target changes nothing outside a drop window`() {
        listOf(
            State.INITIAL,
            awaiting,
            holding,
            idleAfterCamera,
            State.HoldingCamera(1, 1_000, 1_350, camera),
        ).forEach { state ->
            assertUnchanged(state, Event.TargetUnavailable)
        }
    }

    // The hook's gate

    @Test
    fun `the hook watches camera starts only while a clean sequence expects one`() {
        assertEquals(3_000L, awaiting.startWatchUntil(timing))
        assertEquals(3_000L, holding.startWatchUntil(timing))
        assertEquals(3_000L, dropping.startWatchUntil(timing))
        assertNull(State.INITIAL.startWatchUntil(timing))
        assertNull(idleAfterCamera.startWatchUntil(timing))
        assertNull(State.HoldingCamera(1, 1_000, 1_350, camera).startWatchUntil(timing))
    }
}
