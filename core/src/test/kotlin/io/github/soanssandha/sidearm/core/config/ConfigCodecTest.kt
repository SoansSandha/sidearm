package io.github.soanssandha.sidearm.core.config

import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigCodecTest {

    private val target = TargetComponent("com.example.app", "com.example.app.Main")

    @Test
    fun `stored names never change, because old installs already hold them`() {
        assertEquals("settings", ConfigKeys.GROUP)
        assertEquals("enabled", ConfigKeys.ENABLED)
        assertEquals("target_component", ConfigKeys.TARGET_COMPONENT)
    }

    @Test
    fun `decodes stored values`() {
        val stored = mapOf(
            ConfigKeys.ENABLED to true,
            ConfigKeys.TARGET_COMPONENT to "com.example.app/.Main",
        )

        assertEquals(Config(enabled = true, target = target), ConfigCodec.decode(stored))
    }

    @Test
    fun `missing values decode to the default`() {
        assertEquals(Config.DEFAULT, ConfigCodec.decode(emptyMap<String, Any?>()))
    }

    @Test
    fun `values of the wrong type are ignored`() {
        val stored = mapOf(
            ConfigKeys.ENABLED to "true",
            ConfigKeys.TARGET_COMPONENT to 42,
        )

        assertEquals(Config.DEFAULT, ConfigCodec.decode(stored))
    }

    @Test
    fun `an invalid target leaves the feature unarmed`() {
        val stored = mapOf(
            ConfigKeys.ENABLED to true,
            ConfigKeys.TARGET_COMPONENT to "not a component",
        )

        val config = ConfigCodec.decode(stored)

        assertEquals(Config(enabled = true, target = null), config)
        assertEquals(null, config.armedTarget)
    }

    @Test
    fun `encodes every key, with null meaning remove`() {
        assertEquals(
            mapOf(ConfigKeys.ENABLED to true, ConfigKeys.TARGET_COMPONENT to "com.example.app/com.example.app.Main"),
            ConfigCodec.encode(Config(enabled = true, target = target)),
        )
        assertEquals(
            mapOf(ConfigKeys.ENABLED to false, ConfigKeys.TARGET_COMPONENT to null),
            ConfigCodec.encode(Config.DEFAULT),
        )
    }

    @Test
    fun `encode then decode gives back the same config`() {
        listOf(Config.DEFAULT, Config(true, target), Config(false, target), Config(true, null)).forEach {
            assertEquals(it, ConfigCodec.decode(ConfigCodec.encode(it)))
        }
    }
}
