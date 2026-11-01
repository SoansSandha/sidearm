package io.github.soanssandha.sidearm.hook.target

import io.github.soanssandha.sidearm.hook.target.fixture.GestureLauncherFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GestureLauncherTargetsTest {

    private fun resolve(
        service: Class<*>,
        statusBarManager: Class<*> = GestureLauncherFixtures.StatusBarManager::class.java,
    ) = GestureLauncherTargets.resolve(
        service = service,
        mutableBoolean = GestureLauncherFixtures.MutableBoolean::class.java,
        statusBarManager = statusBarManager,
    )

    private fun assertRefuses(result: Result<GestureLauncherTargets>, member: String) {
        val error = assertNotNull(result.exceptionOrNull(), "should refuse because of $member")
        assertTrue(member in error.toString(), "refused for the wrong reason: $error")
    }

    @Test
    fun `resolves every target from Android 17's members`() {
        val targets = resolve(GestureLauncherFixtures.Android17::class.java).getOrThrow()

        assertEquals("interceptPowerKeyDown", targets.interceptPowerKeyDown.name)
        assertEquals("handleCameraGesture", targets.handleCameraGesture.name)
        assertEquals(300L, targets.doubleTapWindowMs)
        assertEquals(1, targets.powerDoubleTapSource)
    }

    @Test
    fun `reads whether stock counted a press, and the tap count`() {
        val targets = resolve(GestureLauncherFixtures.Android17::class.java).getOrThrow()
        val service = GestureLauncherFixtures.Android17().apply { counted(eventTime = 1_000, taps = 2) }

        assertTrue(targets.wasCounted(service, eventTime = 1_000))
        assertFalse(targets.wasCounted(service, eventTime = 1_250), "a long press leaves mLastPowerDown alone")
        assertEquals(1_000, targets.lastPowerDown(service))
        assertEquals(2, targets.consecutiveTaps(service))
    }

    @Test
    fun `refuses a build whose service changed any target`() {
        assertRefuses(resolve(GestureLauncherFixtures.WithoutCameraGesture::class.java), "handleCameraGesture")
        assertRefuses(resolve(GestureLauncherFixtures.WithVoidCameraGesture::class.java), "handleCameraGesture")
        assertRefuses(
            resolve(GestureLauncherFixtures.WithTwoArgumentPowerKeyDown::class.java),
            "interceptPowerKeyDown",
        )
        assertRefuses(resolve(GestureLauncherFixtures.WithLongTapCount::class.java), "mPowerButtonConsecutiveTaps")
    }

    @Test
    fun `refuses a build without the power double-press camera source`() {
        val result = resolve(
            GestureLauncherFixtures.Android17::class.java,
            statusBarManager = GestureLauncherFixtures.StatusBarManagerWithoutPowerSource::class.java,
        )

        assertRefuses(result, "CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP")
    }

    @Test
    fun `refuses, without throwing, when the framework classes aren't there`() {
        assertRefuses(
            GestureLauncherTargets.resolve(javaClass.classLoader),
            "com.android.server.GestureLauncherService",
        )
    }
}
