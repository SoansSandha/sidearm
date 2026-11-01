package io.github.soanssandha.sidearm.hook.effect

/** Where the hook writes its lines. In system_server that's the Xposed log, which Vector forwards to logcat. */
internal interface HookLog {
    fun info(message: String)

    fun warn(message: String, error: Throwable? = null)
}
