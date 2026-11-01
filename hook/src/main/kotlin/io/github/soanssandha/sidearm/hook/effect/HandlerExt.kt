package io.github.soanssandha.sidearm.hook.effect

import android.os.Handler

/**
 * Posts [block] and catches anything it throws, because an uncaught exception on any system_server thread restarts
 * system_server. Logs if the looper refused the message.
 */
internal fun Handler.postGuarded(log: HookLog, what: String, block: () -> Unit) {
    postGuardedDelayed(log, what, delayMs = 0, block)
}

/** [postGuarded], [delayMs] from now. */
internal fun Handler.postGuardedDelayed(log: HookLog, what: String, delayMs: Long, block: () -> Unit) {
    if (!postDelayed({ runCatching(block).onFailure { log.warn("$what failed", it) } }, delayMs)) {
        log.warn("$what not posted: the looper is quitting")
    }
}
