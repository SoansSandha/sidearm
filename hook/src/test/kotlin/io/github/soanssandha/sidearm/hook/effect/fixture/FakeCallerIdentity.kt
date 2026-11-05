package io.github.soanssandha.sidearm.hook.effect.fixture

import io.github.soanssandha.sidearm.hook.effect.CallerIdentity

/** A binder caller identity: [uid] calls, [token] is captured, and [current] is the token a [runAs] block runs as. */
internal class FakeCallerIdentity(var uid: Int = 10_100, var token: Long = 42) : CallerIdentity {

    /** The token in effect, or null outside [runAs]. */
    var current: Long? = null
        private set

    override fun callingUid(): Int = uid

    override fun capture(): Long = token

    override fun <T> runAs(token: Long, block: () -> T): T {
        current = token
        return try {
            block()
        } finally {
            current = null
        }
    }

    override fun <T> runAsSelf(block: () -> T): T {
        current = SELF
        return try {
            block()
        } finally {
            current = null
        }
    }

    companion object {
        /** [current] inside [runAsSelf]. */
        const val SELF = -1L
    }
}
