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
        assertEquals("dry_run", ConfigKeys.DRY_RUN)
        assertEquals("launch_mode", ConfigKeys.LAUNCH_MODE)
    }

    @Test
    fun `decodes stored values`() {
        val stored = mapOf(
            ConfigKeys.ENABLED to true,
            ConfigKeys.TARGET_COMPONENT to "com.example.app/.Main",
            ConfigKeys.DRY_RUN to true,
            ConfigKeys.LAUNCH_MODE to "clean",
        )

        assertEquals(
            Config(enabled = true, target = target, dryRun = true, launchMode = LaunchMode.CLEAN),
            ConfigCodec.decode(stored),
        )
    }

    @Test
    fun `an unknown launch mode falls back to classic and leaves the feature armed`() {
        listOf<Any?>("CLEAN ", "turbo", "", 1, true, null).forEach { stored ->
            val config = ConfigCodec.decode(
                mapOf(
                    ConfigKeys.ENABLED to true,
                    ConfigKeys.TARGET_COMPONENT to "com.example.app/.Main",
                    ConfigKeys.LAUNCH_MODE to stored,
                ),
            )

            assertEquals(LaunchMode.CLASSIC, config.launchMode, "stored $stored")
            assertEquals(target, config.armedTarget, "stored $stored")
        }
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
            ConfigKeys.DRY_RUN to 1,
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
            mapOf(
                ConfigKeys.ENABLED to true,
                ConfigKeys.TARGET_COMPONENT to "com.example.app/com.example.app.Main",
                ConfigKeys.DRY_RUN to true,
                ConfigKeys.LAUNCH_MODE to "clean",
            ),
            ConfigCodec.encode(Config(enabled = true, target = target, dryRun = true, launchMode = LaunchMode.CLEAN)),
        )
        assertEquals(
            mapOf(
                ConfigKeys.ENABLED to false,
                ConfigKeys.TARGET_COMPONENT to null,
                ConfigKeys.DRY_RUN to false,
                ConfigKeys.LAUNCH_MODE to "classic",
            ),
            ConfigCodec.encode(Config.DEFAULT),
        )
    }

    @Test
    fun `encode then decode gives back the same config`() {
        listOf(
            Config.DEFAULT,
            Config(true, target),
            Config(false, target),
            Config(true, null),
            Config(true, target, dryRun = true),
            Config(true, target, launchMode = LaunchMode.CLEAN),
        ).forEach {
            assertEquals(it, ConfigCodec.decode(ConfigCodec.encode(it)))
        }
    }
}
