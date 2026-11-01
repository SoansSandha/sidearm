package io.github.soanssandha.sidearm.hook.target

import android.os.PowerManager
import java.lang.reflect.Method

/**
 * Reflective handles into `PowerManager`'s hidden `wakeUp(long, int, String)`, for waking the device when press 3
 * comes while it isn't interactive. It wakes with `WAKE_REASON_POWER_BUTTON`, as a power press would, and never with
 * `WAKE_REASON_GESTURE`, which would stop the next power press from turning the screen off (docs/plan.md §3.1).
 *
 * Resolved once at boot, all-or-nothing, together with the other targets.
 */
internal class PowerManagerTargets private constructor(private val wakeUp: Method, private val powerButtonReason: Int) {

    /** Wakes the device at uptime [time] as a power button press, with [details] for the power log. */
    fun wakeUpFromPowerButton(powerManager: Any, time: Long, details: String) {
        wakeUp.invoke(powerManager, time, powerButtonReason, details)
    }

    companion object {
        fun resolve(): Result<PowerManagerTargets> = resolve(PowerManager::class.java)

        /** Resolves the targets from a class that's already loaded. Tests pass a stand-in with the same members. */
        internal fun resolve(powerManager: Class<*>): Result<PowerManagerTargets> = runCatching {
            PowerManagerTargets(
                wakeUp = powerManager.declaredMethod(
                    "wakeUp",
                    Void.TYPE,
                    Long::class.java,
                    Int::class.java,
                    String::class.java,
                ),
                powerButtonReason = powerManager.declaredField(
                    "WAKE_REASON_POWER_BUTTON",
                    Int::class.java,
                ).getInt(null),
            )
        }
    }
}
