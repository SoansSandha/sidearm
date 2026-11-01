package io.github.soanssandha.sidearm.hook.target

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Reflective handles into Android 17's `PhoneWindowManager`, for opening the chosen app the way its own
 * double-press-to-app setting does, and for asking SystemUI to unlock first (docs/plan.md §3.3). Resolved once at
 * boot, all-or-nothing, together with [GestureLauncherTargets].
 *
 * The instance itself only exists once WindowManagerService has started, so it's looked up at launch time.
 */
internal class PhoneWindowManagerTargets private constructor(
    private val getLocalService: Method,
    private val policyType: Class<*>,
    private val phoneWindowManager: Class<*>,
    private val contextField: Field,
    private val keyguardDelegateField: Field,
    private val isShowing: Method,
    private val startActivityAsUser: Method,
    private val currentOrSelf: Any,
    private val dismissKeyguardLw: Method,
) {

    /** The running PhoneWindowManager, or null before WindowManagerService has registered it. */
    fun policy(): Any? = getLocalService.invoke(null, policyType)?.takeIf(phoneWindowManager::isInstance)

    fun context(policy: Any): Context = contextField.get(policy) as Context

    /** Whether the keyguard is showing. Also true when that can't be told, so nothing opens over a locked phone. */
    fun isKeyguardShowing(policy: Any): Boolean {
        val delegate = keyguardDelegateField.get(policy) ?: return true
        return isShowing.invoke(delegate) as Boolean
    }

    /**
     * Starts [intent] for the current user through PhoneWindowManager. It skips the launch while setup is
     * incomplete, and logs rather than throws when the activity is missing.
     */
    fun startActivity(policy: Any, intent: Intent) {
        startActivityAsUser.invoke(policy, intent, currentOrSelf)
    }

    /**
     * Asks SystemUI to unlock, showing the fingerprint overlay or bouncer the way the Wallet tile does, and then calls
     * [callback], an `IKeyguardDismissCallback`. If the keyguard isn't showing, PhoneWindowManager calls its
     * `onDismissError` straight away.
     */
    fun dismissKeyguard(policy: Any, callback: Any) {
        dismissKeyguardLw.invoke(policy, callback, null)
    }

    companion object {
        private const val LOCAL_SERVICES_CLASS = "com.android.server.LocalServices"
        private const val POLICY_CLASS = "com.android.server.policy.WindowManagerPolicy"
        private const val PHONE_WINDOW_MANAGER_CLASS = "com.android.server.policy.PhoneWindowManager"
        private const val KEYGUARD_DELEGATE_CLASS = "com.android.server.policy.keyguard.KeyguardServiceDelegate"
        private const val DISMISS_CALLBACK_CLASS = "com.android.internal.policy.IKeyguardDismissCallback"

        @SuppressLint("PrivateApi") // system_server's own classes, which this module exists to hook.
        fun resolve(classLoader: ClassLoader): Result<PhoneWindowManagerTargets> = runCatching {
            resolve(
                localServices = Class.forName(LOCAL_SERVICES_CLASS, false, classLoader),
                policy = Class.forName(POLICY_CLASS, false, classLoader),
                phoneWindowManager = Class.forName(PHONE_WINDOW_MANAGER_CLASS, false, classLoader),
                keyguardDelegate = Class.forName(KEYGUARD_DELEGATE_CLASS, false, classLoader),
                userHandle = UserHandle::class.java,
                dismissCallback = Class.forName(DISMISS_CALLBACK_CLASS, false, classLoader),
            ).getOrThrow()
        }

        /** Resolves the targets from classes that are already loaded. Tests pass stand-ins with the same members. */
        internal fun resolve(
            localServices: Class<*>,
            policy: Class<*>,
            phoneWindowManager: Class<*>,
            keyguardDelegate: Class<*>,
            userHandle: Class<*>,
            dismissCallback: Class<*>,
        ): Result<PhoneWindowManagerTargets> = runCatching {
            check(policy.isAssignableFrom(phoneWindowManager)) { "$phoneWindowManager doesn't implement $policy" }
            PhoneWindowManagerTargets(
                getLocalService = localServices.declaredMethod("getService", Any::class.java, Class::class.java),
                policyType = policy,
                phoneWindowManager = phoneWindowManager,
                contextField = phoneWindowManager.declaredField("mContext", Context::class.java),
                keyguardDelegateField = phoneWindowManager.declaredField("mKeyguardDelegate", keyguardDelegate),
                isShowing = keyguardDelegate.declaredMethod("isShowing", Boolean::class.java),
                startActivityAsUser = phoneWindowManager.declaredMethod(
                    "startActivityAsUser",
                    Void.TYPE,
                    Intent::class.java,
                    userHandle,
                ),
                currentOrSelf = checkNotNull(userHandle.declaredField("CURRENT_OR_SELF", userHandle).get(null)),
                dismissKeyguardLw = phoneWindowManager.declaredMethod(
                    "dismissKeyguardLw",
                    Void.TYPE,
                    dismissCallback,
                    CharSequence::class.java,
                ),
            )
        }
    }
}
