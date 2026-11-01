package io.github.soanssandha.sidearm.data

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.res.Resources
import android.os.Build
import android.provider.Settings

/** [DeviceChecks] read from the phone's settings and build properties. */
class AndroidDeviceChecks(private val resolver: ContentResolver) : DeviceChecks {
    override fun doublePress(): DoublePress = runCatching {
        DoublePress.resolve(
            mode = frameworkInteger("config_doubleTapPowerGestureMode"),
            multiTargetEnabled = secureInt(MULTI_TARGET_ENABLED),
            action = secureInt(MULTI_TARGET_ACTION),
            defaultAction = frameworkInteger("config_doubleTapPowerGestureMultiTargetDefaultAction"),
            cameraDisabled = secureInt(CAMERA_DISABLED),
        )
    }.getOrDefault(DoublePress.UNKNOWN)

    override val build = BuildCheck(id = Build.ID, tested = Build.FINGERPRINT in TestedBuilds.FINGERPRINTS)

    /** The stored value, or null if the key isn't set. These keys are hidden but marked readable for apps. */
    private fun secureInt(key: String): Int? = Settings.Secure.getString(resolver, key)?.toIntOrNull()

    /** A framework-internal integer resource, or null if this build doesn't have it. */
    @SuppressLint("DiscouragedApi") // Internal resources have no public R id; looking them up by name is the only way.
    private fun frameworkInteger(name: String): Int? {
        val resources = Resources.getSystem()
        val id = resources.getIdentifier(name, "integer", "android")
        return if (id == 0) null else resources.getInteger(id)
    }

    private companion object {
        // Settings.Secure keys GestureLauncherService reads (hidden constants, so spelled out here).
        const val MULTI_TARGET_ENABLED = "double_tap_power_button_gesture_enabled"
        const val MULTI_TARGET_ACTION = "double_tap_power_button_gesture"
        const val CAMERA_DISABLED = "camera_double_tap_power_gesture_disabled"
    }
}
