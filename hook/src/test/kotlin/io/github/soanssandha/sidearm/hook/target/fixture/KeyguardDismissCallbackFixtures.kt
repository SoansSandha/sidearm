package io.github.soanssandha.sidearm.hook.target.fixture

import android.os.IBinder

/**
 * Stand-ins for `com.android.internal.policy.IKeyguardDismissCallback` and its AIDL `Stub`, copying the members that
 * docs/hook-targets.md lists. The transaction codes are deliberately not AIDL's usual 1, 2, 3, so a test can't pass
 * by assuming them.
 */
// The field names copy the framework's AIDL-generated ones, which reflection looks up by name.
@Suppress("unused", "ktlint:standard:property-naming")
object KeyguardDismissCallbackFixtures {

    interface IKeyguardDismissCallback {
        fun onDismissError()

        fun onDismissSucceeded()

        fun onDismissCancelled()
    }

    /** What `Stub.asInterface` returns for a remote binder: a proxy holding it. */
    class Proxy(val binder: IBinder) : IKeyguardDismissCallback {
        override fun onDismissError() = Unit

        override fun onDismissSucceeded() = Unit

        override fun onDismissCancelled() = Unit
    }

    /** `IKeyguardDismissCallback.Stub` as it is on Android 17. */
    abstract class Stub {
        companion object {
            const val DESCRIPTOR = "com.android.internal.policy.IKeyguardDismissCallback"
            private const val TRANSACTION_onDismissError = 11
            private const val TRANSACTION_onDismissSucceeded = 12
            private const val TRANSACTION_onDismissCancelled = 13

            @JvmStatic
            fun asInterface(binder: IBinder): IKeyguardDismissCallback = Proxy(binder)
        }
    }

    /** A `Stub` that lost one of its transaction codes. */
    abstract class StubWithoutCancelled {
        companion object {
            const val DESCRIPTOR = "com.android.internal.policy.IKeyguardDismissCallback"
            private const val TRANSACTION_onDismissError = 11
            private const val TRANSACTION_onDismissSucceeded = 12

            @JvmStatic
            fun asInterface(binder: IBinder): IKeyguardDismissCallback = Proxy(binder)
        }
    }
}
