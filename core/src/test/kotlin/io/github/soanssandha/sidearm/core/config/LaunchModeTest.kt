package io.github.soanssandha.sidearm.core.config

import kotlin.test.Test
import kotlin.test.assertEquals

class LaunchModeTest {

    @Test
    fun `stored names never change, because old installs already hold them`() {
        assertEquals("classic", LaunchMode.CLASSIC.stored)
        assertEquals("clean", LaunchMode.CLEAN.stored)
    }

    @Test
    fun `reads each stored name back`() {
        LaunchMode.entries.forEach { assertEquals(it, LaunchMode.fromStored(it.stored)) }
    }

    @Test
    fun `anything else reads as classic, the mode that works everywhere`() {
        listOf(null, "", "Clean", " clean", "fast").forEach {
            assertEquals(LaunchMode.CLASSIC, LaunchMode.fromStored(it), "stored '$it'")
        }
    }
}
