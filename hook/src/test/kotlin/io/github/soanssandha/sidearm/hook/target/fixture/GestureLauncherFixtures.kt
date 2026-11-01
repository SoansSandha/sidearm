package io.github.soanssandha.sidearm.hook.target.fixture

import android.view.KeyEvent

/**
 * Stand-ins for the framework classes `GestureLauncherTargets` reads. [Android17] copies the members that
 * docs/hook-targets.md lists for Android 17; the others each break one of them, the way an OTA might.
 */
@Suppress("unused", "UNUSED_PARAMETER")
object GestureLauncherFixtures {

    /** `android.util.MutableBoolean`. */
    class MutableBoolean(@JvmField var value: Boolean)

    /** `android.app.StatusBarManager`. */
    class StatusBarManager {
        companion object {
            const val CAMERA_LAUNCH_SOURCE_POWER_DOUBLE_TAP = 1
        }
    }

    /** `android.app.StatusBarManager` without the camera source constant. */
    class StatusBarManagerWithoutPowerSource

    /** `com.android.server.GestureLauncherService` as it is on Android 17. */
    class Android17 {
        private var mPowerButtonConsecutiveTaps = 0
        private var mLastPowerDown = 0L

        /** Sets the fields stock updates when it counts a press. */
        fun counted(eventTime: Long, taps: Int) {
            mLastPowerDown = eventTime
            mPowerButtonConsecutiveTaps = taps
        }

        fun interceptPowerKeyDown(event: KeyEvent, interactive: Boolean, outLaunched: MutableBoolean): Boolean = false

        fun handleCameraGesture(useWakelock: Boolean, source: Int): Boolean = true

        private companion object {
            const val POWER_DOUBLE_TAP_MAX_TIME_MS = 300L
        }
    }

    /** `handleCameraGesture` is gone, for example renamed or inlined away. */
    class WithoutCameraGesture {
        private var mPowerButtonConsecutiveTaps = 0
        private var mLastPowerDown = 0L

        fun interceptPowerKeyDown(event: KeyEvent, interactive: Boolean, outLaunched: MutableBoolean): Boolean = false

        private companion object {
            const val POWER_DOUBLE_TAP_MAX_TIME_MS = 300L
        }
    }

    /** `handleCameraGesture` no longer returns whether it launched. */
    class WithVoidCameraGesture {
        private var mPowerButtonConsecutiveTaps = 0
        private var mLastPowerDown = 0L

        fun interceptPowerKeyDown(event: KeyEvent, interactive: Boolean, outLaunched: MutableBoolean): Boolean = false

        fun handleCameraGesture(useWakelock: Boolean, source: Int) = Unit

        private companion object {
            const val POWER_DOUBLE_TAP_MAX_TIME_MS = 300L
        }
    }

    /** `interceptPowerKeyDown` lost its `outLaunched` parameter. */
    class WithTwoArgumentPowerKeyDown {
        private var mPowerButtonConsecutiveTaps = 0
        private var mLastPowerDown = 0L

        fun interceptPowerKeyDown(event: KeyEvent, interactive: Boolean): Boolean = false

        fun handleCameraGesture(useWakelock: Boolean, source: Int): Boolean = true

        private companion object {
            const val POWER_DOUBLE_TAP_MAX_TIME_MS = 300L
        }
    }

    /** The tap count changed type. */
    class WithLongTapCount {
        private var mPowerButtonConsecutiveTaps = 0L
        private var mLastPowerDown = 0L

        fun interceptPowerKeyDown(event: KeyEvent, interactive: Boolean, outLaunched: MutableBoolean): Boolean = false

        fun handleCameraGesture(useWakelock: Boolean, source: Int): Boolean = true

        private companion object {
            const val POWER_DOUBLE_TAP_MAX_TIME_MS = 300L
        }
    }
}
