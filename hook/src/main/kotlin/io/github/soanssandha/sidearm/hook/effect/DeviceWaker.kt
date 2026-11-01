package io.github.soanssandha.sidearm.hook.effect

import android.os.PowerManager
import android.os.SystemClock
import io.github.soanssandha.sidearm.hook.target.PhoneWindowManagerTargets
import io.github.soanssandha.sidearm.hook.target.PowerManagerTargets

/**
 * Wakes the device for a press 3 that came while it wasn't interactive. It runs on the input thread, where
 * PhoneWindowManager wakes the device for an ordinary power press too.
 */
internal class DeviceWaker(
    private val windowManager: PhoneWindowManagerTargets,
    private val power: PowerManagerTargets,
    private val log: HookLog,
) {
    fun wake() {
        val policy = windowManager.policy() ?: return log.warn("Not waking: PhoneWindowManager isn't running")
        val powerManager = checkNotNull(windowManager.context(policy).getSystemService(PowerManager::class.java))
        power.wakeUpFromPowerButton(powerManager, SystemClock.uptimeMillis(), WAKE_DETAILS)
        log.info("Woke the device for press 3")
    }

    private companion object {
        /** Shown in the power manager's wake log. */
        const val WAKE_DETAILS = "sidearm:TRIPLE_PRESS"
    }
}
