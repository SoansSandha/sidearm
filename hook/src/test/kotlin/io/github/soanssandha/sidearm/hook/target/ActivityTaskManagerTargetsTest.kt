package io.github.soanssandha.sidearm.hook.target

import io.github.soanssandha.sidearm.hook.target.fixture.ActivityTaskManagerFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ActivityTaskManagerTargetsTest {

    private fun resolve(
        service: Class<*>,
        activityManager: Class<*> = ActivityTaskManagerFixtures.ActivityManager::class.java,
    ) = ActivityTaskManagerTargets.resolve(
        service = service,
        applicationThread = ActivityTaskManagerFixtures.ApplicationThread::class.java,
        profilerInfo = ActivityTaskManagerFixtures.ProfilerInfo::class.java,
        activityManager = activityManager,
    )

    private fun assertRefuses(result: Result<ActivityTaskManagerTargets>, member: String) {
        val error = assertNotNull(result.exceptionOrNull(), "should refuse because of $member")
        assertTrue(member in error.toString(), "refused for the wrong reason: $error")
    }

    @Test
    fun `resolves every target from Android 17's members`() {
        val targets = resolve(ActivityTaskManagerFixtures.Android17::class.java).getOrThrow()

        assertEquals("startActivityAsUser", targets.startActivityAsUser.name)
        assertEquals(12, targets.startActivityAsUser.parameterCount)
        assertEquals(0, targets.startSuccess)
    }

    @Test
    fun `refuses when startActivityAsUser is gone or reshaped`() {
        assertRefuses(
            resolve(ActivityTaskManagerFixtures.WithoutStartActivityAsUser::class.java),
            "startActivityAsUser",
        )
    }

    @Test
    fun `refuses when startActivityAsUser no longer returns the start result`() {
        assertRefuses(resolve(ActivityTaskManagerFixtures.StartReturnsVoid::class.java), "startActivityAsUser returns")
    }

    @Test
    fun `refuses when the service's context field is gone`() {
        assertRefuses(resolve(ActivityTaskManagerFixtures.WithoutContext::class.java), "mContext")
    }

    @Test
    fun `refuses when START_SUCCESS is gone`() {
        assertRefuses(
            resolve(
                ActivityTaskManagerFixtures.Android17::class.java,
                ActivityTaskManagerFixtures.ActivityManagerWithoutStartSuccess::class.java,
            ),
            "START_SUCCESS",
        )
    }
}
