package io.github.soanssandha.sidearm.hook.intercept

import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.config.LaunchMode
import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.core.sequencer.CameraArgs
import io.github.soanssandha.sidearm.core.sequencer.Event
import io.github.soanssandha.sidearm.core.sequencer.Timing
import io.github.soanssandha.sidearm.hook.effect.Sequencer
import io.github.soanssandha.sidearm.hook.effect.fixture.FakeCallerIdentity
import io.github.soanssandha.sidearm.hook.effect.fixture.FakeEffectRunner
import io.github.soanssandha.sidearm.hook.effect.fixture.FakeEffectRunner.Call
import io.github.soanssandha.sidearm.hook.effect.fixture.RecordingLog
import io.github.soanssandha.sidearm.hook.intercept.fixture.FakeChain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ActivityStartHookerTest {

    private val target = TargetComponent("com.example.app", "com.example.app.Main")
    private val clean = Config(enabled = true, target = target, launchMode = LaunchMode.CLEAN)

    private val runner = FakeEffectRunner()
    private val log = RecordingLog()
    private val sequencer = Sequencer(Timing(windowMs = 300, graceMs = 50), runner, log).apply { cleanAvailable = true }
    private val identity = FakeCallerIdentity(token = 42)
    private val service = Any()
    private var now = 1_080L
    private var matches = true

    private val hooker = ActivityStartHooker(
        sequencer = sequencer,
        matcher = { matches },
        identity = identity,
        startSuccess = START_SUCCESS,
        uptimeMillis = { now },
        log = log,
    )

    /** A `startActivityAsUser` call whose original returns [STOCK_RESULT]. */
    private fun start() = FakeChain(service, listOf(null, "com.android.systemui", null), STOCK_RESULT)

    /** An unlocked double press whose press 2 lands at 1000, in the clean mode. */
    private fun cleanDoublePress(config: Config = clean) {
        sequencer.updateConfig(config)
        sequencer.dispatch(Event.PowerDown(eventTime = 800, taps = 1, interactive = true))
        sequencer.dispatch(Event.CameraRequested(1_000, CameraArgs(useWakelock = false, source = 1), false))
        sequencer.dispatch(Event.PowerDown(eventTime = 1_000, taps = 2, interactive = true))
    }

    @Test
    fun `while no clean sequence expects a start, every start runs untouched and isn't looked at`() {
        matches = false
        val chain = start()
        val hooker = ActivityStartHooker(sequencer, { error("looked at") }, identity, START_SUCCESS, { now }, log)

        assertEquals(STOCK_RESULT, hooker.intercept(chain))

        assertEquals(1, chain.proceedCalls)
        assertEquals(emptyList(), log.lines)
    }

    @Test
    fun `SystemUI's camera start after a clean double press is held and answered with success`() {
        cleanDoublePress()
        val chain = start()

        assertEquals(START_SUCCESS, hooker.intercept(chain))

        assertEquals(0, chain.proceedCalls)
        val held = runner.calls.filterIsInstance<Call.HoldStart>().single().call
        assertEquals(service, held.service)
        assertEquals(listOf(null, "com.android.systemui", null), held.args.toList())
        assertEquals(42L, held.callerIdentity)
    }

    @Test
    fun `other starts while watching run untouched`() {
        cleanDoublePress()
        matches = false
        val chain = start()

        assertEquals(STOCK_RESULT, hooker.intercept(chain))

        assertEquals(1, chain.proceedCalls)
        assertTrue(runner.calls.none { it is Call.HoldStart })
    }

    @Test
    fun `a start after the watch has run out runs untouched`() {
        cleanDoublePress()
        now = 3_001
        val chain = start()

        assertEquals(STOCK_RESULT, hooker.intercept(chain))

        assertEquals(1, chain.proceedCalls)
        assertTrue(runner.calls.none { it is Call.HoldStart })
    }

    @Test
    fun `in a dry run the start runs, and only the log says it would have been held`() {
        cleanDoublePress(clean.copy(dryRun = true))
        val chain = start()

        assertEquals(STOCK_RESULT, hooker.intercept(chain))

        assertEquals(1, chain.proceedCalls)
        assertTrue(log.lines.any { it.startsWith("Dry run: CameraStartRequested") }, "log: ${log.lines}")
    }

    @Test
    fun `a failure while matching lets the start run and logs it`() {
        cleanDoublePress()
        val chain = start()
        val failing = ActivityStartHooker(sequencer, { error("boom") }, identity, START_SUCCESS, { now }, log)

        assertEquals(STOCK_RESULT, failing.intercept(chain))

        assertEquals(1, chain.proceedCalls)
        assertTrue(log.lines.any { it.startsWith("W: Activity start not handled") }, "log: ${log.lines}")
    }

    private companion object {
        const val START_SUCCESS = 0
        const val STOCK_RESULT = 2
    }
}
