package io.github.soanssandha.sidearm.core.sequencer

import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.core.sequencer.fixture.SequenceDriver
import io.github.soanssandha.sidearm.core.sequencer.fixture.SequenceDriver.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Whole press sequences with stock's 300 ms window and our 50 ms grace. Press 1 is at [PRESS_1] and press 2 at
 * [PRESS_2], so the held camera's deadline is [PRESS_2] + 350.
 */
class ReduceTimingTest {

    private val target = TargetComponent("com.example.app", "com.example.app.Main")
    private val armed = Config(enabled = true, target = target)

    private fun tripleWithGap(gapMs: Long, seenAfterMs: Long = 0, interactive: Boolean = true): List<Outcome> {
        val driver = SequenceDriver(armed)
        driver.press(PRESS_1)
        driver.press(PRESS_2)
        driver.press(PRESS_2 + gapMs, interactive, seenAfterMs)
        driver.advanceTo(END)
        return driver.outcomes
    }

    @Test
    fun `third press 295 ms after the second opens the target and never the camera`() {
        assertEquals(
            listOf(Outcome.PressConsumed(10_445), Outcome.TargetLaunched(target, 10_445)),
            tripleWithGap(gapMs = 295),
        )
    }

    @Test
    fun `third press 299 ms after the second opens the target`() {
        assertEquals(
            listOf(Outcome.PressConsumed(10_449), Outcome.TargetLaunched(target, 10_449)),
            tripleWithGap(gapMs = 299),
        )
    }

    @Test
    fun `third press 300 ms after the second is a new press, so the camera opens then and stock handles the press`() {
        assertEquals(listOf(Outcome.CameraOpened(10_450)), tripleWithGap(gapMs = 300))
    }

    @Test
    fun `third press 349 ms after the second opens the camera at that press`() {
        assertEquals(listOf(Outcome.CameraOpened(10_499)), tripleWithGap(gapMs = 349))
    }

    @Test
    fun `third press 351 ms after the second comes after the camera already opened`() {
        assertEquals(listOf(Outcome.CameraOpened(10_500)), tripleWithGap(gapMs = 351))
    }

    @Test
    fun `third press that reaches the hook within the grace period still opens the target`() {
        assertEquals(
            listOf(Outcome.PressConsumed(10_449), Outcome.TargetLaunched(target, 10_498)),
            tripleWithGap(gapMs = 299, seenAfterMs = 49),
        )
    }

    @Test
    fun `third press that reaches the hook after the deadline opens the camera, then the target over it`() {
        assertEquals(
            listOf(Outcome.CameraOpened(10_500), Outcome.PressConsumed(10_445), Outcome.TargetLaunched(target, 10_505)),
            tripleWithGap(gapMs = 295, seenAfterMs = 60),
        )
    }

    @Test
    fun `triple from screen off wakes the device`() {
        assertEquals(
            listOf(Outcome.PressConsumed(10_445), Outcome.DeviceWoken, Outcome.TargetLaunched(target, 10_445)),
            tripleWithGap(gapMs = 295, interactive = false),
        )
    }

    @Test
    fun `double press opens the camera 350 ms after the second press`() {
        val driver = SequenceDriver(armed)

        driver.press(PRESS_1)
        driver.press(PRESS_2)
        driver.advanceTo(END)

        assertEquals(listOf(Outcome.CameraOpened(10_500)), driver.outcomes)
    }

    @Test
    fun `single press is left entirely to stock`() {
        val driver = SequenceDriver(armed)

        driver.press(PRESS_1)
        driver.advanceTo(END)

        assertEquals(emptyList(), driver.outcomes)
    }

    @Test
    fun `presses 4 and 5 after a triple are left to stock, so Emergency SOS still sees them`() {
        val driver = SequenceDriver(armed)

        listOf(10_000L, 10_150, 10_300, 10_450, 10_600).forEach(driver::press)
        driver.advanceTo(END)

        assertEquals(listOf(Outcome.PressConsumed(10_300), Outcome.TargetLaunched(target, 10_300)), driver.outcomes)
    }

    @Test
    fun `a later double press after a triple still opens the camera`() {
        val driver = SequenceDriver(armed)

        listOf(10_000L, 10_150, 10_300, 12_000, 12_150).forEach(driver::press)
        driver.advanceTo(END)

        assertEquals(
            listOf(
                Outcome.PressConsumed(10_300),
                Outcome.TargetLaunched(target, 10_300),
                Outcome.CameraOpened(12_500),
            ),
            driver.outcomes,
        )
    }

    @Test
    fun `a cancelled timer that fires anyway changes nothing`() {
        val driver = SequenceDriver(armed, cancelledTimersStillFire = true)

        // A triple cancels hold 1's timer; then a double whose third press comes 300 ms late flushes hold 2.
        listOf(10_000L, 10_150, 10_300, 12_000, 12_150, 12_450).forEach(driver::press)
        driver.advanceTo(END)

        assertEquals(
            listOf(
                Outcome.PressConsumed(10_300),
                Outcome.TargetLaunched(target, 10_300),
                Outcome.CameraOpened(12_450),
            ),
            driver.outcomes,
        )
    }

    @Test
    fun `with the feature off the camera opens on the second press, as in stock`() {
        val driver = SequenceDriver(Config.DEFAULT)

        listOf(PRESS_1, PRESS_2, PRESS_2 + 150).forEach(driver::press)
        driver.advanceTo(END)

        assertEquals(listOf(Outcome.CameraOpened(PRESS_2)), driver.outcomes)
    }

    @Test
    fun `with stock double-press not set to camera nothing happens`() {
        val driver = SequenceDriver(armed, cameraDoublePress = false)

        listOf(PRESS_1, PRESS_2, PRESS_2 + 150).forEach(driver::press)
        driver.advanceTo(END)

        assertEquals(emptyList(), driver.outcomes)
    }

    @Test
    fun `turning the feature off mid-hold opens the camera right away`() {
        val driver = SequenceDriver(armed)

        driver.press(PRESS_1)
        driver.press(PRESS_2)
        driver.changeConfig(at = 10_200, newConfig = Config.DEFAULT)
        driver.advanceTo(END)

        assertEquals(listOf(Outcome.CameraOpened(10_200)), driver.outcomes)
    }

    @Test
    fun `a hot reload mid-hold opens the camera right away`() {
        val driver = SequenceDriver(armed)

        driver.press(PRESS_1)
        driver.press(PRESS_2)
        driver.release(at = 10_200)
        driver.advanceTo(END)

        assertEquals(listOf(Outcome.CameraOpened(10_200)), driver.outcomes)
    }

    // An unlocked phone: the camera is never held (ADR 0007).

    private fun unlocked(vararg presses: Long): List<Outcome> {
        val driver = SequenceDriver(armed, startedLocked = false)
        presses.forEach(driver::press)
        driver.advanceTo(END)
        return driver.outcomes
    }

    @Test
    fun `unlocked, a double press opens the camera at once, as in stock`() {
        assertEquals(listOf(Outcome.CameraOpened(PRESS_2)), unlocked(PRESS_1, PRESS_2))
    }

    @Test
    fun `unlocked, a triple opens the camera at press 2 and the target over it at press 3`() {
        assertEquals(
            listOf(
                Outcome.CameraOpened(PRESS_2),
                Outcome.PressConsumed(10_445),
                Outcome.TargetLaunched(target, 10_445),
            ),
            unlocked(PRESS_1, PRESS_2, PRESS_2 + 295),
        )
    }

    @Test
    fun `unlocked, a third press 300 ms after the second is a new press, left to stock`() {
        assertEquals(listOf(Outcome.CameraOpened(PRESS_2)), unlocked(PRESS_1, PRESS_2, PRESS_2 + 300))
    }

    @Test
    fun `unlocked, presses 4 and 5 after a triple are left to stock, so Emergency SOS still sees them`() {
        assertEquals(
            listOf(Outcome.CameraOpened(10_150), Outcome.PressConsumed(10_300), Outcome.TargetLaunched(target, 10_300)),
            unlocked(10_000L, 10_150, 10_300, 10_450, 10_600),
        )
    }

    @Test
    fun `unlocked, with the feature off a triple is left entirely to stock`() {
        val driver = SequenceDriver(Config.DEFAULT, startedLocked = false)

        listOf(PRESS_1, PRESS_2, PRESS_2 + 150).forEach(driver::press)
        driver.advanceTo(END)

        assertEquals(listOf(Outcome.CameraOpened(PRESS_2)), driver.outcomes)
    }

    private companion object {
        const val PRESS_1 = 10_000L
        const val PRESS_2 = 10_150L
        const val END = 20_000L
    }
}
