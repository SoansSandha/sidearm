package io.github.soanssandha.sidearm.data

/** What pressing the power button twice does. Triple press only works on top of [CAMERA]. */
enum class DoublePress {
    CAMERA,
    WALLET,
    OFF,

    /** The setting couldn't be read, or holds a value this build doesn't know. */
    UNKNOWN,
    ;

    companion object {
        // GestureLauncherService's modes (the framework's config_doubleTapPowerGestureMode) and actions.
        const val MODE_DISABLED = 0
        const val MODE_CAMERA = 1
        const val MODE_MULTI_TARGET = 2
        const val ACTION_CAMERA = 0
        const val ACTION_WALLET = 1

        /**
         * Applies Android 17's `GestureLauncherService.isCameraDoubleTapPowerSettingEnabled` and
         * `isWalletDoubleTapPowerSettingEnabled` to the stored values. A null argument is one that isn't stored (or,
         * for [mode] and [defaultAction], couldn't be read); stock falls back to the same defaults.
         *
         * @param mode the device's double-press mode, from framework resources
         * @param multiTargetEnabled `double_tap_power_button_gesture_enabled`
         * @param action `double_tap_power_button_gesture`
         * @param defaultAction the device's default for [action], from framework resources
         * @param cameraDisabled `camera_double_tap_power_gesture_disabled`
         */
        fun resolve(mode: Int?, multiTargetEnabled: Int?, action: Int?, defaultAction: Int?, cameraDisabled: Int?) =
            when (mode) {
                MODE_DISABLED -> OFF

                MODE_CAMERA -> if ((cameraDisabled ?: 0) == 0) CAMERA else OFF

                MODE_MULTI_TARGET -> when {
                    (multiTargetEnabled ?: 1) != 1 -> OFF

                    else -> when (action ?: defaultAction) {
                        ACTION_CAMERA -> CAMERA
                        ACTION_WALLET -> WALLET
                        else -> UNKNOWN
                    }
                }

                else -> UNKNOWN
            }
    }
}
