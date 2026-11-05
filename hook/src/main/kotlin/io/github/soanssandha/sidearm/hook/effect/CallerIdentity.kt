package io.github.soanssandha.sidearm.hook.effect

/**
 * The binder caller identity of the current thread: `Binder.getCallingUid()` and the token that
 * `Binder.clearCallingIdentity()` returns. A held start replays later on another thread, as the app that made it.
 */
internal interface CallerIdentity {
    /** The calling uid of the binder call this thread is handling. */
    fun callingUid(): Int

    /** A token for the current caller, to replay as it later. Leaves this thread's identity as it was. */
    fun capture(): Long

    /** Runs [block] as the caller [token] captured, then restores this thread's own identity. */
    fun <T> runAs(token: Long, block: () -> T): T

    /** Runs [block] as system_server itself rather than the binder caller, then restores the caller. */
    fun <T> runAsSelf(block: () -> T): T
}
