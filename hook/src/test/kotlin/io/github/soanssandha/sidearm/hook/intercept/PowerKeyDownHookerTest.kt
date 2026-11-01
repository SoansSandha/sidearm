package io.github.soanssandha.sidearm.hook.intercept

import android.view.KeyEvent
import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.core.sequencer.CameraArgs
import io.github.soanssandha.sidearm.core.sequencer.Event
import io.github.soanssandha.sidearm.core.sequencer.Timing
import io.github.soanssandha.sidearm.hook.effect.Sequencer
import io.github.soanssandha.sidearm.hook.effect.fixture.FakeEffectRunner
import io.github.soanssandha.sidearm.hook.effect.fixture.RecordingLog
import io.github.soanssandha.sidearm.hook.intercept.fixture.FakeChain
import io.github.soanssandha.sidearm.hook.target.GestureLauncherTargets
import io.github.soanssandha.sidearm.hook.target.PhoneWindowManagerTargets
import io.github.soanssandha.sidearm.hook.target.fixture.GestureLauncherFixtures
import io.github.soanssandha.sidearm.hook.target.fixture.PhoneWindowManagerFixtures
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The unit-test android.jar returns default values, so every [KeyEvent] here has event time 0. Holds therefore
 * start at a negative press time, so the press under test is a later one.
 */
class PowerKeyDownHookerTest {

    private val target = TargetComponent("com.example.app", "com.example.app.Main")
    private val camera = CameraArgs(useWakelock = false, source = 1)
    private val targets = GestureLauncherTargets.resolve(
        service = GestureLauncherFixtures.Android17::class.java,
        mutableBoolean = GestureLauncherFixtures.MutableBoolean::class.java,
        statusBarManager = GestureLauncherFixtures.StatusBarManager::class.java,
    ).getOrThrow()

    private val windowManager = PhoneWindowManagerTargets.resolve(
        localServices = PhoneWindowManagerFixtures.LocalServices::class.java,
        policy = PhoneWindowManagerFixtures.WindowManagerPolicy::class.java,
        phoneWindowManager = PhoneWindowManagerFixtures.PhoneWindowManager::class.java,
        keyguardDelegate = PhoneWindowManagerFixtures.KeyguardServiceDelegate::class.java,
        userHandle = PhoneWindowManagerFixtures.UserHandle::class.java,
    ).getOrThrow()

    private val runner = FakeEffectRunner()
    private val log = RecordingLog()
    private val sequencer = Sequencer(Timing(windowMs = 300, graceMs = 50), runner, log)
    private val sequenceStart = SequenceStart(windowManager)
    private val hooker = PowerKeyDownHooker(targets, sequenceStart, sequencer, log)
    private val service = GestureLauncherFixtures.Android17()

    private fun pressChain(stockResult: Boolean, on: Any = service) = FakeChain(
        thisObject = on,
        args = listOf(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_POWER), true, null),
        stockResult = stockResult,
    )

    private fun keyguard(showing: Boolean) {
        PhoneWindowManagerFixtures.LocalServices.registered =
            PhoneWindowManagerFixtures.PhoneWindowManager(PhoneWindowManagerFixtures.KeyguardServiceDelegate(showing))
    }

    @AfterTest
    fun clearLocalServices() {
        PhoneWindowManagerFixtures.LocalServices.registered = null
    }

    @Test
    fun `records whether the phone was locked at the first press of a sequence, and only then`() {
        keyguard(showing = false)
        service.counted(eventTime = 0, taps = 1)
        hooker.intercept(pressChain(stockResult = false))
        assertFalse(sequenceStart.startedLocked)

        keyguard(showing = true)
        service.counted(eventTime = 0, taps = 2)
        hooker.intercept(pressChain(stockResult = true))
        assertFalse(sequenceStart.startedLocked, "press 2 must not overwrite press 1's reading")
    }

    @Test
    fun `runs stock once and returns its result when the press isn't consumed`() {
        service.counted(eventTime = 0, taps = 1)
        val chain = pressChain(stockResult = false)

        assertEquals(false, hooker.intercept(chain))
        assertEquals(1, chain.proceedCalls)
    }

    @Test
    fun `returns true for a press 3 the sequencer consumes, even when stock said false`() {
        sequencer.updateConfig(Config(enabled = true, target = target))
        sequencer.dispatch(Event.CameraRequested(pressTime = -100, camera = camera, startedLocked = true))
        service.counted(eventTime = 0, taps = 3)
        val chain = pressChain(stockResult = false)

        assertEquals(true, hooker.intercept(chain))
        assertEquals(1, chain.proceedCalls)
        assertTrue(runner.calls.any { it is FakeEffectRunner.Call.LaunchTarget }, "calls: ${runner.calls}")
    }

    @Test
    fun `leaves a press stock didn't count to stock, such as a long press`() {
        sequencer.updateConfig(Config(enabled = true, target = target))
        sequencer.dispatch(Event.CameraRequested(pressTime = -100, camera = camera, startedLocked = true))
        service.counted(eventTime = -100, taps = 2)
        val chain = pressChain(stockResult = false)

        assertEquals(false, hooker.intercept(chain))
        assertEquals(listOf<FakeEffectRunner.Call>(FakeEffectRunner.Call.ScheduleTimer(deadline = 250)), runner.calls)
    }

    @Test
    fun `a failure after stock ran returns stock's result without running stock again`() {
        val chain = pressChain(stockResult = true, on = Any())

        assertEquals(true, hooker.intercept(chain))
        assertEquals(1, chain.proceedCalls)
        assertTrue(log.lines.any { it.startsWith("W: Power press not handled") }, "log: ${log.lines}")
    }
}
