package io.github.soanssandha.sidearm.hook.target

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PowerManagerTargetsTest {

    /** `android.os.PowerManager`'s hidden members, as they are on Android 17. */
    @Suppress("unused")
    class FakePowerManager {
        val wakes = mutableListOf<Triple<Long, Int, String?>>()

        fun wakeUp(time: Long, reason: Int, details: String?) {
            wakes += Triple(time, reason, details)
        }

        companion object {
            const val WAKE_REASON_POWER_BUTTON = 1
        }
    }

    /** `wakeUp` without the reason, as on older releases. */
    @Suppress("unused", "UNUSED_PARAMETER")
    class OldPowerManager {
        fun wakeUp(time: Long) = Unit

        companion object {
            const val WAKE_REASON_POWER_BUTTON = 1
        }
    }

    @Test
    fun `wakes the device as a power button press`() {
        val powerManager = FakePowerManager()

        PowerManagerTargets.resolve(FakePowerManager::class.java).getOrThrow()
            .wakeUpFromPowerButton(powerManager, time = 5_000, details = "test")

        assertEquals(listOf<Triple<Long, Int, String?>>(Triple(5_000L, 1, "test")), powerManager.wakes)
    }

    @Test
    fun `refuses a build whose wakeUp changed`() {
        val error = PowerManagerTargets.resolve(OldPowerManager::class.java).exceptionOrNull()

        assertTrue(error != null && "wakeUp" in error.toString(), "got $error")
    }
}
