package io.github.soanssandha.sidearm.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfigTest {

    private val target = TargetComponent("com.example.app", "com.example.app.Main")

    @Test
    fun `is armed only when enabled with a target`() {
        assertEquals(target, Config(enabled = true, target = target).armedTarget)
        assertNull(Config(enabled = true, target = null).armedTarget)
        assertNull(Config(enabled = false, target = target).armedTarget)
        assertNull(Config.DEFAULT.armedTarget)
    }

    @Test
    fun `dry run doesn't change whether the feature is armed`() {
        assertEquals(target, Config(enabled = true, target = target, dryRun = true).armedTarget)
        assertNull(Config(enabled = false, target = target, dryRun = true).armedTarget)
    }

    @Test
    fun `default is disabled with no target, and not a dry run`() {
        assertEquals(Config(enabled = false, target = null, dryRun = false), Config.DEFAULT)
    }
}
