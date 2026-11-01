package io.github.soanssandha.sidearm.core.sequencer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TimingTest {

    @Test
    fun `defaults match stock's double-tap window plus the grace period`() {
        assertEquals(Timing(windowMs = 300, graceMs = 50), Timing())
    }

    @Test
    fun `rejects a window that is not positive or a negative grace`() {
        assertFailsWith<IllegalArgumentException> { Timing(windowMs = 0) }
        assertFailsWith<IllegalArgumentException> { Timing(windowMs = -1) }
        assertFailsWith<IllegalArgumentException> { Timing(graceMs = -1) }
    }

    @Test
    fun `accepts a zero grace`() {
        assertEquals(0L, Timing(graceMs = 0).graceMs)
    }
}
