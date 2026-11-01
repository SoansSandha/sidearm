package io.github.soanssandha.sidearm.hook.target

import android.annotation.SuppressLint
import android.os.IBinder
import java.lang.reflect.Method

/**
 * Reflective handles into `com.android.internal.policy.IKeyguardDismissCallback`, the callback SystemUI calls when an
 * unlock it was asked for succeeds, is cancelled or fails. They let the hook implement it as a plain `Binder`, with no
 * hidden-API stubs at compile time: the descriptor and transaction codes come from the framework's own `Stub`.
 *
 * Resolved once at boot, all-or-nothing, together with the other targets.
 */
internal class KeyguardDismissCallbackTargets private constructor(
    val callbackInterface: Class<*>,
    val descriptor: String,
    private val succeededCode: Int,
    private val cancelledCode: Int,
    private val errorCode: Int,
    private val asInterfaceMethod: Method,
) {

    enum class Outcome { SUCCEEDED, CANCELLED, ERROR }

    /** What a transaction [code] reports, or null if it isn't one of the callback's methods. */
    fun outcomeOf(code: Int): Outcome? = when (code) {
        succeededCode -> Outcome.SUCCEEDED
        cancelledCode -> Outcome.CANCELLED
        errorCode -> Outcome.ERROR
        else -> null
    }

    /** Wraps [binder] as an `IKeyguardDismissCallback`, the type `PhoneWindowManager.dismissKeyguardLw` takes. */
    fun asInterface(binder: IBinder): Any = checkNotNull(asInterfaceMethod.invoke(null, binder))

    companion object {
        private const val CALLBACK_CLASS = "com.android.internal.policy.IKeyguardDismissCallback"
        private const val STUB_CLASS = "$CALLBACK_CLASS\$Stub"

        @SuppressLint("PrivateApi") // A framework-internal interface that system_server itself uses.
        fun resolve(classLoader: ClassLoader): Result<KeyguardDismissCallbackTargets> = runCatching {
            resolve(
                callbackInterface = Class.forName(CALLBACK_CLASS, false, classLoader),
                stub = Class.forName(STUB_CLASS, false, classLoader),
            ).getOrThrow()
        }

        /** Resolves the targets from classes that are already loaded. Tests pass stand-ins with the same members. */
        internal fun resolve(callbackInterface: Class<*>, stub: Class<*>): Result<KeyguardDismissCallbackTargets> =
            runCatching {
                val int = Int::class.java
                KeyguardDismissCallbackTargets(
                    callbackInterface = callbackInterface,
                    descriptor = stub.declaredField("DESCRIPTOR", String::class.java).get(null) as String,
                    succeededCode = stub.declaredField("TRANSACTION_onDismissSucceeded", int).getInt(null),
                    cancelledCode = stub.declaredField("TRANSACTION_onDismissCancelled", int).getInt(null),
                    errorCode = stub.declaredField("TRANSACTION_onDismissError", int).getInt(null),
                    asInterfaceMethod = stub.declaredMethod("asInterface", callbackInterface, IBinder::class.java),
                )
            }
    }
}
