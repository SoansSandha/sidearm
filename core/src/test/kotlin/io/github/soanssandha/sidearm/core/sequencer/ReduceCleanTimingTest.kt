package io.github.soanssandha.sidearm.core.sequencer

import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.config.LaunchMode
import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.core.sequencer.fixture.SequenceDriver
import io.github.soanssandha.sidearm.core.sequencer.fixture.SequenceDriver.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Whole unlocked press sequences in both launch modes, with SystemUI starting the camera some ms after press 2: [WARM]
 * as usual, or [COLD] as just after a reboot. Press 1 is at [PRESS_1] and press 2 at [PRESS_2], so the press window
 * plus grace ends at [PRESS_2] + 350.
 */
class ReduceCleanTimingTest {

    private val target = TargetComponent("com.example.app", "com.example.app.Main")
    private val clean = Config(enabled = true, target = target, launchMode = LaunchMode.CLEAN)
    private val classic = clean.copy(launchMode = LaunchMode.CLASSIC)

    private fun play(
        config: Config,
        startDelayMs: Long,
        presses: List<Long>,
        startedLocked: Boolean = false,
    ): List<Outcome> {
        val driver = SequenceDriver(config, startedLocked = startedLocked, systemUiStartDelayMs = startDelayMs)
        presses.forEach(driver::press)
        driver.advanceTo(END)
        return driver.outcomes
    }

    private val triple = listOf(PRESS_1, PRESS_2, PRESS_2 + 200)
    private val double = listOf(PRESS_1, PRESS_2)

    @Test
    fun `clean triple opens the target with no camera`() {
        assertEquals(
            listOf(Outcome.PressConsumed(10_350), Outcome.TargetLaunched(target, 10_350)),
            play(clean, WARM, triple),
        )
    }

    @Test
    fun `clean double opens the camera when the window ends`() {
        assertEquals(listOf(Outcome.CameraOpened(10_500)), play(clean, WARM, double))
    }

    @Test
    fun `clean triple with a slow SystemUI still opens no camera`() {
        assertEquals(
            listOf(
                Outcome.PressConsumed(10_350),
                Outcome.TargetLaunched(target, 10_350),
                Outcome.StartDropped(10_550),
            ),
            play(clean, COLD, triple),
        )
    }

    @Test
    fun `clean double with a slow SystemUI opens the camera when SystemUI gets to it`() {
        assertEquals(listOf(Outcome.CameraOpened(10_550)), play(clean, COLD, double))
    }

    @Test
    fun `classic triple opens the target over the camera`() {
        assertEquals(
            listOf(Outcome.CameraOpened(10_210), Outcome.PressConsumed(10_350), Outcome.TargetLaunched(target, 10_350)),
            play(classic, WARM, triple),
        )
    }

    @Test
    fun `classic triple with a slow SystemUI ends with the camera over the target (the known classic race)`() {
        assertEquals(
            listOf(Outcome.PressConsumed(10_350), Outcome.TargetLaunched(target, 10_350), Outcome.CameraOpened(10_550)),
            play(classic, COLD, triple),
        )
    }

    @Test
    fun `clean Emergency SOS opens the target at press 3, as classic does, and never the camera`() {
        val sos = listOf(PRESS_1, PRESS_2, 10_300L, 10_450L, 10_600L)

        assertEquals(
            listOf(
                Outcome.PressConsumed(10_300),
                Outcome.TargetLaunched(target, 10_300),
                Outcome.StartDropped(10_550),
            ),
            play(clean, COLD, sos),
        )
    }

    @Test
    fun `a locked phone behaves the same in both modes`() {
        listOf(triple, double).forEach { presses ->
            assertEquals(
                play(classic, WARM, presses, startedLocked = true),
                play(clean, WARM, presses, startedLocked = true),
                "presses $presses",
            )
        }
    }

    private companion object {
        const val PRESS_1 = 10_000L
        const val PRESS_2 = 10_150L
        const val END = 20_000L

        /** ms from press 2 to SystemUI's camera start. */
        const val WARM = 60L
        const val COLD = 400L
    }
}
