package io.github.soanssandha.sidearm.hook.effect

import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.core.sequencer.CameraArgs
import io.github.soanssandha.sidearm.core.sequencer.Event
import io.github.soanssandha.sidearm.core.sequencer.Timing
import io.github.soanssandha.sidearm.hook.effect.fixture.FakeEffectRunner
import io.github.soanssandha.sidearm.hook.effect.fixture.FakeEffectRunner.Call
import io.github.soanssandha.sidearm.hook.effect.fixture.RecordingLog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The reducer's own rules are covered in `:core`; these tests cover what the hook adds around it. */
class SequencerTest {

    private val target = TargetComponent("com.example.app", "com.example.app.Main")
    private val live = Config(enabled = true, target = target)
    private val dryRun = live.copy(dryRun = true)
    private val camera = CameraArgs(useWakelock = false, source = 1)

    private val runner = FakeEffectRunner()
    private val log = RecordingLog()
    private val sequencer = Sequencer(Timing(windowMs = 300, graceMs = 50), runner, log)

    private fun cameraRequested(pressTime: Long = 1_000, startedLocked: Boolean = true) =
        sequencer.dispatch(Event.CameraRequested(pressTime, camera, startedLocked))

    private fun press(eventTime: Long, taps: Int, interactive: Boolean = true) =
        sequencer.dispatch(Event.PowerDown(eventTime, taps, interactive))

    /** Presses 1 and 2 of a sequence whose press 2 lands at 1000, as stock reports them. */
    private fun doublePress(): Boolean {
        press(eventTime = 800, taps = 1)
        val held = cameraRequested(pressTime = 1_000)
        press(eventTime = 1_000, taps = 2)
        return held
    }

    @Test
    fun `leaves every press to stock while the feature is off`() {
        assertFalse(doublePress())
        assertFalse(press(eventTime = 1_200, taps = 3))

        assertEquals(emptyList(), runner.calls)
    }

    @Test
    fun `holds the camera on a double press, then runs it when the timer fires`() {
        sequencer.updateConfig(live)

        assertTrue(doublePress())
        runner.fireTimer()

        assertEquals(listOf(Call.ScheduleTimer(deadline = 1_350), Call.RunHeldCamera(camera)), runner.calls)
    }

    /** Presses 1 and 2 of a sequence on an unlocked phone, whose press 2 lands at 1000. */
    private fun unlockedDoublePress(): Boolean {
        press(eventTime = 800, taps = 1)
        val held = cameraRequested(pressTime = 1_000, startedLocked = false)
        press(eventTime = 1_000, taps = 2)
        return held
    }

    @Test
    fun `on an unlocked phone, lets the camera through and holds nothing`() {
        sequencer.updateConfig(live)

        assertFalse(unlockedDoublePress())

        assertEquals(emptyList(), runner.calls)
    }

    @Test
    fun `on an unlocked phone, press 3 opens the target over the camera`() {
        sequencer.updateConfig(live)
        unlockedDoublePress()

        assertTrue(press(eventTime = 1_200, taps = 3))

        assertEquals(listOf<Call>(Call.LaunchTarget(target, fallback = null)), runner.calls)
    }

    @Test
    fun `on an unlocked phone, a dry run only checks the target`() {
        sequencer.updateConfig(dryRun)
        unlockedDoublePress()

        assertFalse(press(eventTime = 1_200, taps = 3))

        assertEquals(listOf<Call>(Call.CheckTarget(target)), runner.calls)
    }

    @Test
    fun `an unlocked sequence that began as a dry run stays one`() {
        sequencer.updateConfig(dryRun)
        unlockedDoublePress()

        sequencer.updateConfig(live)

        assertFalse(press(eventTime = 1_200, taps = 3))
        assertEquals(listOf<Call>(Call.CheckTarget(target)), runner.calls)
    }

    @Test
    fun `consumes press 3 and launches the target instead of the camera`() {
        sequencer.updateConfig(live)
        doublePress()

        assertTrue(press(eventTime = 1_200, taps = 3))

        assertEquals(
            listOf(Call.ScheduleTimer(deadline = 1_350), Call.CancelTimer, Call.LaunchTarget(target, camera)),
            runner.calls,
        )
    }

    @Test
    fun `wakes the device when press 3 comes while it isn't interactive`() {
        sequencer.updateConfig(live)
        doublePress()

        press(eventTime = 1_200, taps = 3, interactive = false)

        assertEquals(
            listOf(
                Call.ScheduleTimer(deadline = 1_350),
                Call.CancelTimer,
                Call.WakeDevice,
                Call.LaunchTarget(target, camera),
            ),
            runner.calls,
        )
    }

    @Test
    fun `a dry run leaves every press to stock and only checks the target`() {
        sequencer.updateConfig(dryRun)

        assertFalse(doublePress())
        assertFalse(press(eventTime = 1_200, taps = 3, interactive = false))

        assertEquals(
            listOf(Call.ScheduleTimer(deadline = 1_350), Call.CancelTimer, Call.CheckTarget(target)),
            runner.calls,
        )
    }

    @Test
    fun `a dry run never replays the camera, because stock already opened it`() {
        sequencer.updateConfig(dryRun)
        doublePress()

        runner.fireTimer()

        assertEquals(listOf<Call>(Call.ScheduleTimer(deadline = 1_350)), runner.calls)
    }

    @Test
    fun `a hold that began as a dry run stays one when dry run is switched off`() {
        sequencer.updateConfig(dryRun)
        doublePress()

        sequencer.updateConfig(live)
        runner.fireTimer()

        assertEquals(
            listOf<Call>(Call.ScheduleTimer(deadline = 1_350)),
            runner.calls,
            "stock's camera must not open twice",
        )
    }

    @Test
    fun `a live hold still opens the camera when dry run is switched on`() {
        sequencer.updateConfig(live)
        doublePress()

        sequencer.updateConfig(dryRun)
        runner.fireTimer()

        assertEquals(listOf(Call.ScheduleTimer(deadline = 1_350), Call.RunHeldCamera(camera)), runner.calls)
    }

    @Test
    fun `a third press that lost the race to the timer is still consumed`() {
        sequencer.updateConfig(live)
        doublePress()
        runner.fireTimer()

        assertTrue(press(eventTime = 1_290, taps = 3))
    }

    @Test
    fun `a third press that lost the race to the timer in a dry run is left to stock`() {
        sequencer.updateConfig(dryRun)
        doublePress()
        runner.fireTimer()

        assertFalse(press(eventTime = 1_290, taps = 3))
    }

    @Test
    fun `turning the feature off mid-hold runs the held camera`() {
        sequencer.updateConfig(live)
        doublePress()

        sequencer.updateConfig(Config.DEFAULT)

        assertEquals(
            listOf(Call.ScheduleTimer(deadline = 1_350), Call.CancelTimer, Call.RunHeldCamera(camera)),
            runner.calls,
        )
    }

    @Test
    fun `retiring runs the held camera and then leaves everything to stock`() {
        sequencer.updateConfig(live)
        doublePress()

        sequencer.retire()
        runner.fireTimer()
        sequencer.updateConfig(live)

        assertFalse(cameraRequested(pressTime = 5_000))
        assertFalse(press(eventTime = 5_200, taps = 3))
        assertEquals(
            listOf(Call.ScheduleTimer(deadline = 1_350), Call.CancelTimer, Call.RunHeldCamera(camera)),
            runner.calls,
        )
    }

    @Test
    fun `a failing effect doesn't stop the ones after it`() {
        sequencer.updateConfig(live)
        doublePress()
        runner.failCancelTimer = true

        assertTrue(press(eventTime = 1_200, taps = 3))

        assertEquals(Call.LaunchTarget(target, camera), runner.calls.last())
        assertTrue(log.lines.any { it.startsWith("W: ") && "CancelTimer" in it }, "log: ${log.lines}")
    }

    @Test
    fun `while armed, logs every press and every decision, marking dry runs`() {
        sequencer.updateConfig(dryRun)

        press(eventTime = 800, taps = 1)
        cameraRequested(pressTime = 1_000)

        assertEquals(2, log.lines.size, "log: ${log.lines}")
        assertTrue(log.lines[0].startsWith("PowerDown(") && log.lines[0].endsWith("-> stock"), "log: ${log.lines}")
        assertTrue(log.lines[1].startsWith("Dry run: ") && "HoldCamera" in log.lines[1], "log: ${log.lines}")
    }

    @Test
    fun `while off, logs nothing for presses that stock handles alone`() {
        doublePress()
        press(eventTime = 1_200, taps = 3)

        assertEquals(emptyList(), log.lines)
    }

    @Test
    fun `still logs a held camera being released when the feature is turned off`() {
        sequencer.updateConfig(live)
        doublePress()
        log.lines.clear()

        sequencer.updateConfig(Config.DEFAULT)

        assertEquals(1, log.lines.size, "log: ${log.lines}")
        assertTrue("RunHeldCamera" in log.lines.single(), "log: ${log.lines}")
    }

    @Test
    fun `a failing log doesn't stop the effects`() {
        val failingLog = object : HookLog {
            override fun info(message: String) = error("log failed")

            override fun warn(message: String, error: Throwable?) = Unit
        }
        val sequencer = Sequencer(Timing(windowMs = 300, graceMs = 50), runner, failingLog)
        sequencer.updateConfig(live)

        assertTrue(
            sequencer.dispatch(Event.CameraRequested(pressTime = 1_000, camera = camera, startedLocked = true)),
        )
        assertEquals(listOf<Call>(Call.ScheduleTimer(deadline = 1_350)), runner.calls)
    }
}
