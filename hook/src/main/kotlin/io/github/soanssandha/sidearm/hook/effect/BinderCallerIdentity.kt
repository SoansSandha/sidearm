package io.github.soanssandha.sidearm.hook.effect

import android.os.Binder

/** [CallerIdentity] through `android.os.Binder`. */
internal object BinderCallerIdentity : CallerIdentity {
    override fun callingUid(): Int = Binder.getCallingUid()

    override fun capture(): Long {
        // clearCallingIdentity returns the caller's identity as a token; restore it at once to change nothing.
        val token = Binder.clearCallingIdentity()
        Binder.restoreCallingIdentity(token)
        return token
    }

    override fun <T> runAs(token: Long, block: () -> T): T {
        val own = Binder.clearCallingIdentity()
        Binder.restoreCallingIdentity(token)
        return try {
            block()
        } finally {
            Binder.restoreCallingIdentity(own)
        }
    }

    override fun <T> runAsSelf(block: () -> T): T {
        val caller = Binder.clearCallingIdentity()
        return try {
            block()
        } finally {
            Binder.restoreCallingIdentity(caller)
        }
    }
}
