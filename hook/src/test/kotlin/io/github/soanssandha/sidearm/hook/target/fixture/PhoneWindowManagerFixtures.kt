package io.github.soanssandha.sidearm.hook.target.fixture

import android.content.Context
import android.content.Intent

/**
 * Stand-ins for the framework classes `PhoneWindowManagerTargets` reads, copying the Android 17 members that
 * docs/hook-targets.md lists.
 */
@Suppress("unused", "UNUSED_PARAMETER")
object PhoneWindowManagerFixtures {

    /** `com.android.server.LocalServices`. Tests register a policy and must clear it afterwards. */
    class LocalServices {
        companion object {
            @JvmStatic
            var registered: Any? = null

            @JvmStatic
            fun getService(type: Class<*>): Any? = registered?.takeIf(type::isInstance)
        }
    }

    /** `com.android.server.policy.WindowManagerPolicy`. */
    interface WindowManagerPolicy

    /** `android.os.UserHandle`. */
    class UserHandle private constructor() {
        companion object {
            @JvmField
            val CURRENT_OR_SELF = UserHandle()
        }
    }

    /** `com.android.server.policy.keyguard.KeyguardServiceDelegate`. */
    class KeyguardServiceDelegate(private val showing: Boolean) {
        fun isShowing(): Boolean = showing
    }

    /** `com.android.server.policy.PhoneWindowManager` as it is on Android 17. */
    class PhoneWindowManager(private val mKeyguardDelegate: KeyguardServiceDelegate?) : WindowManagerPolicy {
        private val mContext: Context? = null

        private fun startActivityAsUser(intent: Intent, handle: UserHandle) = Unit
    }

    /** `startActivityAsUser(Intent, UserHandle)` is gone. */
    class WithoutStartActivity(private val mKeyguardDelegate: KeyguardServiceDelegate?) : WindowManagerPolicy {
        private val mContext: Context? = null
    }

    /** `mKeyguardDelegate` changed type. */
    class WithOtherKeyguardDelegate(private val mKeyguardDelegate: Any?) : WindowManagerPolicy {
        private val mContext: Context? = null

        private fun startActivityAsUser(intent: Intent, handle: UserHandle) = Unit
    }

    /** Some other policy, as on a non-phone form factor. */
    class OtherPolicy : WindowManagerPolicy
}
