package io.github.soanssandha.sidearm.data

import kotlin.test.Test
import kotlin.test.assertEquals

class FrameworkInfoTest {

    @Test
    fun `drops the build details Vector adds to its version`() {
        assertEquals("2.2", FrameworkInfo("Vector", "2.2 (3080) 88f8e1fa-JingMatrix-Vector", 102).shortVersion)
    }

    @Test
    fun `keeps a plain version as it is`() {
        assertEquals("1.9.2", FrameworkInfo("LSPosed", " 1.9.2 ", 100).shortVersion)
    }
}
