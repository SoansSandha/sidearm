package io.github.soanssandha.sidearm.hook.target

import android.annotation.SuppressLint
import android.view.KeyEvent
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Reflective handles into Android 17's `GestureLauncherService`, resolved once at boot (docs/hook-targets.md).
 *
 * Resolution is all-or-nothing: if any method or field is missing or changed type (an OTA renamed it), the module
 * installs no hooks and the stock power-button behaviour is untouched.
 */
internal class GestureLauncherTargets private constructor(
    val interceptPowerKeyDown: Method,
    val handleCameraGesture: Method,
    val doubleTapWindowMs: Long,
    val powerDoubleTapSource: Int,
    private val consecutiveTapsField: Field,
    private val lastPowerDownField: Field,
) {

    /** Stock's consecutive tap count, including the press it counted last. */
    fun consecutiveTaps(service: Any): Int = consecutiveTapsField.getInt(service)

    /** The event time of the press stock counted last. */
    fun lastPowerDown(service: Any): Long = lastPowerDownField.getLong(service)

    /** Whether stock counted the press at [eventTime]. It doesn't count long presses or presses in the SOS cooldown. */
    fun wasCounted(service: Any, eventTime: Long): Boolean = lastPowerDown(service) == eventTime

    companion object {
        private const val SERVICE_CLASS = "com.android.server.GestureLauncherService"
        private const val MUTABLE_BOOLEAN_CLASS = "android.util.MutableBoolean"
        private const val STATUS_BAR_MANAGER_CLASS = "android.app.StatusBarManager"

        @SuppressLint("PrivateApi") // system_server's own classes, which this module exists to hook.
        fun resolve(classLoader: ClassLoader): Result<GestureLauncherTargets> = runCatching {
            resolve(
                service = Class.forName(SERVICE_CLASS, false, classLoader),
                mutableBoolean = Class.forName(MUTABLE_BOOLEAN_CLASS, false, classLoader),
                statusBarManager = Class.forName(STATUS_BAR_MANAGER_CLASS, false, classLoader),
            ).getOrThrow()
        }

        /** Resolves the targets from classes that are already loaded. Tests pass stand-ins with the same members. */
        internal fun resolve(
            service: Class<*>,
            mutableBoolean: Class<*>,
            statusBarManager: Class<*>,
        ): Result<GestureLauncherTargets> = runCatching {
            val boolean = Boolean::class.java
            GestureLauncherTargets(
                interceptPowerKeyDown = service.declaredMethod(
                    "interceptPowerKeyDown",
                    boolean,
                    KeyEvent::class.java,
                    boolean,
                    mutableBoolean,
                ),
                handleCameraGesture = service.declaredMethod("handleCameraGesture", boolean, boolean, Int::class.java),
                doubleTapWindowMs = service.declaredField("POWER_DOUBLE_TAP_MAX_TIME_MS", Long::class.java)
                    .getLong(null),
                powerDoubleTapSource = statusBarManager
                    .declaredField("CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP", Int::class.java)
                    .getInt(null),
                consecutiveTapsField = service.declaredField("mPowerButtonConsecutiveTaps", Int::class.java),
                lastPowerDownField = service.declaredField("mLastPowerDown", Long::class.java),
            )
        }
    }
}
