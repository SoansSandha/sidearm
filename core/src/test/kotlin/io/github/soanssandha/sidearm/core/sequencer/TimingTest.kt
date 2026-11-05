package io.github.soanssandha.sidearm.core.sequencer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TimingTest {

    @Test
    fun `defaults match stock's double-tap window plus the grace period, and wait 2 s for a late camera start`() {
        assertEquals(Timing(windowMs = 300, graceMs = 50, lateStartMs = 2_000), Timing())
    }

    @Test
    fun `rejects a window that is not positive or a negative grace`() {
        assertFailsWith<IllegalArgumentException> { Timing(windowMs = 0) }
        assertFailsWith<IllegalArgumentException> { Timing(windowMs = -1) }
        assertFailsWith<IllegalArgumentException> { Timing(graceMs = -1) }
    }

    @Test
    fun `rejects a late-start wait shorter than the window and its grace`() {
        // A start that's still expected must outlive the hold, or a held start could outlast its own watch.
        assertFailsWith<IllegalArgumentException> { Timing(windowMs = 300, graceMs = 50, lateStartMs = 349) }
        assertEquals(350L, Timing(windowMs = 300, graceMs = 50, lateStartMs = 350).lateStartMs)
    }

    @Test
    fun `accepts a zero grace`() {
        assertEquals(0L, Timing(graceMs = 0).graceMs)
    }
}
