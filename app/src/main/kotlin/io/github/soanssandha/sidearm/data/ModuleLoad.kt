package io.github.soanssandha.sidearm.data

/** The state of the hook in system_server, as the framework reports it. */
enum class ModuleLoad {
    /** Not loaded: Sidearm was enabled or scoped after the last boot. */
    NOT_RUNNING,
    UP_TO_DATE,

    /** Running an older install; the framework couldn't hot-reload it. */
    STALE,
    RELOADING,
    FAILED,
}
