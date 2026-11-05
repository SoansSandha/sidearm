package io.github.soanssandha.sidearm.core.sequencer

/**
 * The press windows in ms, fixed for the life of one hook generation.
 *
 * [lateStartMs] is how long after press 2 SystemUI's camera start still belongs to that press, in the clean launch
 * mode: one that comes later is someone else's. It must outlast the hold ([windowMs] + [graceMs]).
 */
data class Timing(
    val windowMs: Long = DEFAULT_WINDOW_MS,
    val graceMs: Long = DEFAULT_GRACE_MS,
    val lateStartMs: Long = DEFAULT_LATE_START_MS,
) {
    init {
        require(windowMs > 0) { "windowMs must be positive, was $windowMs" }
        require(graceMs >= 0) { "graceMs must not be negative, was $graceMs" }
        require(lateStartMs >= windowMs + graceMs) {
            "lateStartMs must be at least windowMs + graceMs (${windowMs + graceMs}), was $lateStartMs"
        }
    }

    companion object {
        /** Stock's `POWER_DOUBLE_TAP_MAX_TIME_MS` on Android 17. The hook reads the real value at boot. */
        const val DEFAULT_WINDOW_MS = 300L

        /** Covers the few ms between a press's event time and the moment the hook sees it. */
        const val DEFAULT_GRACE_MS = 50L

        /** SystemUI starts the camera a few main-thread hops after press 2; even a cold start is well inside this. */
        const val DEFAULT_LATE_START_MS = 2_000L
    }
}
