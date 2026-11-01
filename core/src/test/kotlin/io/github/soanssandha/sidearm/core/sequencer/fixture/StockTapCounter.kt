package io.github.soanssandha.sidearm.core.sequencer.fixture

/**
 * Stock `GestureLauncherService` fast-tap counting: a press less than one window after the previous press adds a
 * tap, and any slower press starts again at 1 (stock resets when the interval is `>=` the window).
 *
 * Simplified: every press is counted, whereas stock skips long-press repeats (the hook doesn't report those) and
 * restarts at 1 after an Emergency SOS sequence that was too fast. The reducer takes stock's count as given, so this
 * only limits which sequences the fixture can produce. The Phase 2 dry run and the Phase 3 device matrix check the
 * real behaviour.
 */
class StockTapCounter(private val windowMs: Long) {
    private var lastPress: Long? = null
    private var taps = 0

    fun count(eventTime: Long): Int {
        val last = lastPress
        taps = if (last != null && eventTime - last < windowMs) taps + 1 else 1
        lastPress = eventTime
        return taps
    }
}
