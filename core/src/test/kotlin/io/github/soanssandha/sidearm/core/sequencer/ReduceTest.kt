package io.github.soanssandha.sidearm.core.sequencer

import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.config.TargetComponent
import kotlin.test.Test
import kotlin.test.assertEquals

/** One test per row of the state table in docs/plan.md §3.2. Timing scenarios are in [ReduceTimingTest]. */
class ReduceTest {

    private val target = TargetComponent("com.example.app", "com.example.app.Main")
    private val otherTarget = TargetComponent("com.example.other", "com.example.other.Main")
    private val armed = Config(enabled = true, target = target)
    private val camera = CameraArgs(useWakelock = false, source = 1)
    private val timing = Timing(windowMs = 300, graceMs = 50)

    private val holding = State.HoldingCamera(generation = 1, pressTime = 1_000, deadline = 1_350, camera = camera)

    /** After press 2 at 1000, whose camera has already run: the timer released it, or the phone was unlocked. */
    private val idleAfterCamera = State.Idle(generation = 1, lastCameraPress = 1_000)

    private fun reduce(state: State, event: Event, config: Config = armed) = reduce(state, event, config, timing)

    private fun assertUnchanged(state: State, event: Event, config: Config = armed) {
        assertEquals(Transition(state, emptyList()), reduce(state, event, config))
    }

    private fun cameraRequested(pressTime: Long = 1_000, startedLocked: Boolean = true) =
        Event.CameraRequested(pressTime = pressTime, camera = camera, startedLocked = startedLocked)

    // Idle

    @Test
    fun `camera request while armed and locked holds the camera until one window plus grace after the press`() {
        val transition = reduce(State.INITIAL, cameraRequested(pressTime = 1_000))

        assertEquals(
            Transition(
                State.HoldingCamera(generation = 1, pressTime = 1_000, deadline = 1_350, camera = camera),
                listOf(Effect.ScheduleTimer(deadline = 1_350, generation = 1), Effect.HoldCamera),
            ),
            transition,
        )
    }

    @Test
    fun `each hold takes the next generation`() {
        val transition = reduce(State.Idle(generation = 7), cameraRequested())

        assertEquals(8L, transition.state.generation)
    }

    @Test
    fun `camera request while armed and unlocked runs the camera now and remembers the press`() {
        // Holding would lock the phone (ADR 0007), so the camera opens as in stock and press 3 opens the target over it.
        assertEquals(
            Transition(State.Idle(generation = 7, lastCameraPress = 1_000), listOf(Effect.RunCameraNow)),
            reduce(State.Idle(generation = 7), cameraRequested(pressTime = 1_000, startedLocked = false)),
        )
    }

    @Test
    fun `camera request while not armed runs the camera now and remembers nothing`() {
        val unarmed =
            listOf(Config.DEFAULT, Config(enabled = true, target = null), Config(enabled = false, target = target))

        unarmed.forEach { config ->
            listOf(true, false).forEach { startedLocked ->
                assertEquals(
                    Transition(State.INITIAL, listOf(Effect.RunCameraNow)),
                    reduce(State.INITIAL, cameraRequested(startedLocked = startedLocked), config),
                    "config $config, keyguard showing $startedLocked",
                )
            }
        }
    }

    @Test
    fun `ordinary presses are left to stock`() {
        listOf(1, 2, 3, 4, 5).forEach { taps ->
            assertUnchanged(State.INITIAL, Event.PowerDown(eventTime = 1_000, taps = taps, interactive = true))
        }
    }

    @Test
    fun `third press after the camera already ran opens the target over it`() {
        // Stock counted it within the window of press 2, whose camera already ran: the phone was unlocked, or the
        // timer won the race. There's no camera to fall back to, because it's already open.
        assertEquals(
            Transition(
                State.Idle(generation = 1),
                listOf(Effect.ConsumePress, Effect.LaunchTarget(target = target, fallback = null)),
            ),
            reduce(idleAfterCamera, Event.PowerDown(eventTime = 1_299, taps = 3, interactive = true)),
        )
    }

    @Test
    fun `third press after the camera already ran also wakes the device if it isn't interactive`() {
        assertEquals(
            listOf(Effect.ConsumePress, Effect.WakeDevice, Effect.LaunchTarget(target = target, fallback = null)),
            reduce(idleAfterCamera, Event.PowerDown(eventTime = 1_299, taps = 3, interactive = false)).effects,
        )
    }

    @Test
    fun `third press after the camera ran is only consumed once the feature is off, as the camera is opening`() {
        // e.g. the feature was turned off mid-hold, which also flushes the camera.
        assertEquals(
            Transition(idleAfterCamera, listOf(Effect.ConsumePress)),
            reduce(idleAfterCamera, Event.PowerDown(eventTime = 1_200, taps = 3, interactive = true), Config.DEFAULT),
        )
    }

    @Test
    fun `third press of a later sequence is left to stock`() {
        assertUnchanged(idleAfterCamera, Event.PowerDown(eventTime = 1_300, taps = 3, interactive = true))
    }

    @Test
    fun `other presses after the camera ran are left to stock`() {
        assertUnchanged(idleAfterCamera, Event.PowerDown(eventTime = 1_100, taps = 1, interactive = true))
        assertUnchanged(idleAfterCamera, Event.PowerDown(eventTime = 1_200, taps = 4, interactive = true))
    }

    @Test
    fun `timers, config changes and release do nothing while idle`() {
        listOf(Event.TimerFired(generation = 1), Event.ConfigChanged, Event.Released).forEach { event ->
            assertUnchanged(idleAfterCamera, event)
            assertUnchanged(idleAfterCamera, event, Config.DEFAULT)
        }
    }

    // HoldingCamera

    @Test
    fun `the press that started the hold changes nothing`() {
        // Stock calls handleCameraGesture inside press 2's interceptPowerKeyDown, so press 2 arrives after the hold.
        assertUnchanged(holding, Event.PowerDown(eventTime = 1_000, taps = 2, interactive = true))
    }

    @Test
    fun `third press launches the target instead of the camera`() {
        assertEquals(
            Transition(
                State.Idle(generation = 1),
                listOf(
                    Effect.CancelTimer(generation = 1),
                    Effect.ConsumePress,
                    Effect.LaunchTarget(target = target, fallback = camera),
                ),
            ),
            reduce(holding, Event.PowerDown(eventTime = 1_200, taps = 3, interactive = true)),
        )
    }

    @Test
    fun `third press while not interactive also wakes the device`() {
        assertEquals(
            listOf(
                Effect.CancelTimer(generation = 1),
                Effect.ConsumePress,
                Effect.WakeDevice,
                Effect.LaunchTarget(target = target, fallback = camera),
            ),
            reduce(holding, Event.PowerDown(eventTime = 1_200, taps = 3, interactive = false)).effects,
        )
    }

    @Test
    fun `third press launches the target configured at that moment`() {
        val changed = Config(enabled = true, target = otherTarget)

        val effects = reduce(holding, Event.PowerDown(eventTime = 1_200, taps = 3, interactive = true), changed).effects

        assertEquals(Effect.LaunchTarget(target = otherTarget, fallback = camera), effects.last())
    }

    @Test
    fun `third press after the feature was disarmed runs the held camera and is left to stock`() {
        assertEquals(
            Transition(idleAfterCamera, listOf(Effect.CancelTimer(generation = 1), Effect.RunHeldCamera(camera))),
            reduce(holding, Event.PowerDown(eventTime = 1_200, taps = 3, interactive = true), Config.DEFAULT),
        )
    }

    @Test
    fun `any other press runs the held camera and is left to stock`() {
        val others = listOf(
            Event.PowerDown(eventTime = 1_320, taps = 1, interactive = true),
            Event.PowerDown(eventTime = 1_100, taps = 2, interactive = true),
            Event.PowerDown(eventTime = 1_100, taps = 4, interactive = false),
        )

        others.forEach { event ->
            assertEquals(
                Transition(idleAfterCamera, listOf(Effect.CancelTimer(generation = 1), Effect.RunHeldCamera(camera))),
                reduce(holding, event),
                "event $event",
            )
        }
    }

    @Test
    fun `timer for the current hold runs the held camera`() {
        assertEquals(
            Transition(idleAfterCamera, listOf(Effect.RunHeldCamera(camera))),
            reduce(holding, Event.TimerFired(generation = 1)),
        )
    }

    @Test
    fun `timer from an earlier hold is ignored`() {
        assertUnchanged(holding, Event.TimerFired(generation = 0))
    }

    @Test
    fun `config change that disarms the feature runs the held camera now`() {
        assertEquals(
            Transition(idleAfterCamera, listOf(Effect.CancelTimer(generation = 1), Effect.RunHeldCamera(camera))),
            reduce(holding, Event.ConfigChanged, Config.DEFAULT),
        )
    }

    @Test
    fun `config change that keeps the feature armed keeps holding`() {
        assertUnchanged(holding, Event.ConfigChanged, Config(enabled = true, target = otherTarget))
    }

    @Test
    fun `release runs the held camera now`() {
        assertEquals(
            Transition(idleAfterCamera, listOf(Effect.CancelTimer(generation = 1), Effect.RunHeldCamera(camera))),
            reduce(holding, Event.Released),
        )
    }

    @Test
    fun `a second camera request runs both the held camera and the new one`() {
        assertEquals(
            Transition(
                idleAfterCamera,
                listOf(Effect.CancelTimer(generation = 1), Effect.RunHeldCamera(camera), Effect.RunCameraNow),
            ),
            reduce(holding, cameraRequested(pressTime = 1_400)),
        )
    }
}
