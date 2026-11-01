package io.github.soanssandha.sidearm.core.sequencer

/** The press windows in ms, fixed for the life of one hook generation. */
data class Timing(val windowMs: Long = DEFAULT_WINDOW_MS, val graceMs: Long = DEFAULT_GRACE_MS) {
    init {
        require(windowMs > 0) { "windowMs must be positive, was $windowMs" }
        require(graceMs >= 0) { "graceMs must not be negative, was $graceMs" }
    }

    companion object {
        /** Stock's `POWER_DOUBLE_TAP_MAX_TIME_MS` on Android 17. The hook reads the real value at boot. */
        const val DEFAULT_WINDOW_MS = 300L

        /** Covers the few ms between a press's event time and the moment the hook sees it. */
        const val DEFAULT_GRACE_MS = 50L
    }
}
