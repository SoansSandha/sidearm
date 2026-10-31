package io.github.soanssandha.sidearm.hook

import android.view.KeyEvent
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Reflective handles into Android 17's `GestureLauncherService`, resolved once at boot.
 *
 * Resolution is all-or-nothing: if any method or field is missing (an OTA renamed it), the module
 * installs no hooks and the stock power-button behaviour is untouched.
 */
internal class GestureLauncherTargets private constructor(
    val interceptPowerKeyDown: Method,
    val handleCameraGesture: Method,
    val doubleTapWindowMs: Long,
    val powerDoubleTapSource: Int,
    private val consecutiveTaps: Field,
    private val lastPowerDown: Field,
    private val cameraEnabled: Field,
    private val walletEnabled: Field,
    private val emergencyEnabled: Field,
    private val mutableBooleanValue: Field,
) {

    /** Reads the service's state right after stock `interceptPowerKeyDown` has run. */
    fun snapshot(service: Any, event: KeyEvent, outLaunched: Any): PowerKeySnapshot =
        PowerKeySnapshot(
            counted = lastPowerDown.getLong(service) == event.eventTime,
            consecutiveTaps = consecutiveTaps.getInt(service),
            outLaunched = mutableBooleanValue.getBoolean(outLaunched),
            cameraEnabled = cameraEnabled.getBoolean(service),
            walletEnabled = walletEnabled.getBoolean(service),
            emergencyEnabled = emergencyEnabled.getBoolean(service),
        )

    companion object {
        private const val SERVICE_CLASS = "com.android.server.GestureLauncherService"
        private const val MUTABLE_BOOLEAN_CLASS = "android.util.MutableBoolean"
        private const val STATUS_BAR_MANAGER_CLASS = "android.app.StatusBarManager"

        fun resolve(classLoader: ClassLoader): Result<GestureLauncherTargets> = runCatching {
            val service = Class.forName(SERVICE_CLASS, false, classLoader)
            val mutableBoolean = Class.forName(MUTABLE_BOOLEAN_CLASS, false, classLoader)
            val statusBarManager = Class.forName(STATUS_BAR_MANAGER_CLASS, false, classLoader)

            GestureLauncherTargets(
                interceptPowerKeyDown = service.method(
                    "interceptPowerKeyDown",
                    KeyEvent::class.java,
                    java.lang.Boolean.TYPE,
                    mutableBoolean,
                ),
                handleCameraGesture = service.method(
                    "handleCameraGesture",
                    java.lang.Boolean.TYPE,
                    Integer.TYPE,
                ),
                doubleTapWindowMs = service.field("POWER_DOUBLE_TAP_MAX_TIME_MS", java.lang.Long.TYPE)
                    .getLong(null),
                powerDoubleTapSource = statusBarManager
                    .field("CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP", Integer.TYPE)
                    .getInt(null),
                consecutiveTaps = service.field("mPowerButtonConsecutiveTaps", Integer.TYPE),
                lastPowerDown = service.field("mLastPowerDown", java.lang.Long.TYPE),
                cameraEnabled = service.field("mCameraDoubleTapPowerEnabled", java.lang.Boolean.TYPE),
                walletEnabled = service.field("mWalletDoubleTapPowerEnabled", java.lang.Boolean.TYPE),
                emergencyEnabled = service.field("mEmergencyGestureEnabled", java.lang.Boolean.TYPE),
                mutableBooleanValue = mutableBoolean.field("value", java.lang.Boolean.TYPE),
            )
        }

        private fun Class<*>.method(name: String, vararg parameterTypes: Class<*>): Method =
            getDeclaredMethod(name, *parameterTypes).apply {
                check(returnType == java.lang.Boolean.TYPE) { "$name returns $returnType" }
                isAccessible = true
            }

        private fun Class<*>.field(name: String, type: Class<*>): Field =
            getDeclaredField(name).apply {
                check(this.type == type) { "$name is ${this.type}, expected $type" }
                isAccessible = true
            }
    }
}

internal data class PowerKeySnapshot(
    val counted: Boolean,
    val consecutiveTaps: Int,
    val outLaunched: Boolean,
    val cameraEnabled: Boolean,
    val walletEnabled: Boolean,
    val emergencyEnabled: Boolean,
)
