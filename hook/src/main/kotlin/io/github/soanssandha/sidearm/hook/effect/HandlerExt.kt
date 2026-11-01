package io.github.soanssandha.sidearm.hook.effect

import android.os.Handler

/**
 * Posts [block] and catches anything it throws, because an uncaught exception on any system_server thread restarts
 * system_server. Logs if the looper refused the message.
 */
internal fun Handler.postGuarded(log: HookLog, what: String, block: () -> Unit) {
    if (!post { runCatching(block).onFailure { log.warn("$what failed", it) } }) {
        log.warn("$what not posted: the looper is quitting")
    }
}
