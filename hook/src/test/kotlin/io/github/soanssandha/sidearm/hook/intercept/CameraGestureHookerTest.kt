package io.github.soanssandha.sidearm.hook.intercept

import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.core.sequencer.Timing
import io.github.soanssandha.sidearm.hook.effect.HeldCamera
import io.github.soanssandha.sidearm.hook.effect.Sequencer
import io.github.soanssandha.sidearm.hook.effect.fixture.FakeEffectRunner
import io.github.soanssandha.sidearm.hook.effect.fixture.RecordingLog
import io.github.soanssandha.sidearm.hook.intercept.fixture.FakeChain
import io.github.soanssandha.sidearm.hook.intercept.fixture.FakeInvoker
import io.github.soanssandha.sidearm.hook.target.GestureLauncherTargets
import io.github.soanssandha.sidearm.hook.target.PhoneWindowManagerTargets
import io.github.soanssandha.sidearm.hook.target.fixture.GestureLauncherFixtures
import io.github.soanssandha.sidearm.hook.target.fixture.PhoneWindowManagerFixtures
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CameraGestureHookerTest {

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
    private val original = FakeInvoker()
    private val heldCamera = HeldCamera(original)
    private val sequenceStart = SequenceStart(windowManager)
    private val hooker = CameraGestureHooker(targets, sequenceStart, sequencer, heldCamera, log)
    private val service = GestureLauncherFixtures.Android17().apply { counted(eventTime = 1_000, taps = 2) }

    private fun armed() =
        sequencer.updateConfig(Config(enabled = true, target = TargetComponent("com.a.b", "com.a.b.C")))

    /** Press 1 of the sequence, as the power hooker records it. */
    private fun firstPressWith(keyguardShowing: Boolean?) {
        PhoneWindowManagerFixtures.LocalServices.registered = keyguardShowing?.let {
            PhoneWindowManagerFixtures.PhoneWindowManager(PhoneWindowManagerFixtures.KeyguardServiceDelegate(it))
        }
        sequenceStart.onFirstPress()
    }

    private fun cameraChain(source: Int, on: Any = service) =
        FakeChain(thisObject = on, args = listOf(false, source), stockResult = true)

    @BeforeTest
    fun startLocked() = firstPressWith(keyguardShowing = true)

    @AfterTest
    fun clearLocalServices() {
        PhoneWindowManagerFixtures.LocalServices.registered = null
    }

    @Test
    fun `holds a power double-press request on a locked phone without running stock, and returns true`() {
        armed()
        val chain = cameraChain(source = POWER_DOUBLE_TAP)

        assertEquals(true, hooker.intercept(chain))
        assertEquals(0, chain.proceedCalls)
        assertEquals(listOf<FakeEffectRunner.Call>(FakeEffectRunner.Call.ScheduleTimer(deadline = 1_350)), runner.calls)
    }

    @Test
    fun `replays the held request on the original method with its arguments`() {
        armed()
        hooker.intercept(cameraChain(source = POWER_DOUBLE_TAP))

        runner.fireTimer()
        heldCamera.run((runner.calls.last() as FakeEffectRunner.Call.RunHeldCamera).camera)

        assertEquals(listOf(listOf<Any?>(service, false, POWER_DOUBLE_TAP)), original.calls)
    }

    @Test
    fun `lets a power double-press request through when the sequence started unlocked`() {
        armed()
        firstPressWith(keyguardShowing = false)
        // By press 2 the screen-off makes the keyguard report itself showing; only press 1's reading counts.
        PhoneWindowManagerFixtures.LocalServices.registered = PhoneWindowManagerFixtures.PhoneWindowManager(
            PhoneWindowManagerFixtures.KeyguardServiceDelegate(showing = true),
        )
        val chain = cameraChain(source = POWER_DOUBLE_TAP)

        assertEquals(true, hooker.intercept(chain))
        assertEquals(1, chain.proceedCalls)
        assertEquals(emptyList(), runner.calls)
    }

    @Test
    fun `assumes the phone was locked when PhoneWindowManager isn't available at press 1`() {
        armed()
        firstPressWith(keyguardShowing = null)
        val chain = cameraChain(source = POWER_DOUBLE_TAP)

        hooker.intercept(chain)

        assertEquals(0, chain.proceedCalls)
    }

    @Test
    fun `passes requests from other sources straight to stock`() {
        armed()
        val chain = cameraChain(source = LIFT_GESTURE)

        assertEquals(true, hooker.intercept(chain))
        assertEquals(1, chain.proceedCalls)
        assertEquals(emptyList(), runner.calls)
    }

    @Test
    fun `runs the request now while the feature is off`() {
        val chain = cameraChain(source = POWER_DOUBLE_TAP)

        hooker.intercept(chain)

        assertEquals(1, chain.proceedCalls)
    }

    @Test
    fun `a failure runs the request now`() {
        armed()
        val chain = cameraChain(source = POWER_DOUBLE_TAP, on = Any())

        assertEquals(true, hooker.intercept(chain))
        assertEquals(1, chain.proceedCalls)
        assertTrue(log.lines.any { it.startsWith("W: Camera request not handled") }, "log: ${log.lines}")
    }

    private companion object {
        const val POWER_DOUBLE_TAP = 1

        // Any other StatusBarManager.CAMERA_LAUNCH_SOURCE_* value.
        const val LIFT_GESTURE = 2
    }
}
