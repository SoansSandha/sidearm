package io.github.soanssandha.sidearm.hook.config

import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.config.ConfigKeys
import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.hook.config.fixture.FakeRemotePreferences
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemoteConfigReaderTest {

    private val target = TargetComponent("com.example.app", "com.example.app.Main")
    private val armed = Config(enabled = true, target = target)

    private val preferences = FakeRemotePreferences()
    private val published = mutableListOf<Config>()
    private var opened = 0
    private val reader = RemoteConfigReader(openPreferences = ::open, onChange = published::add)

    private fun open(): FakeRemotePreferences {
        opened++
        return preferences
    }

    @Test
    fun `publishes the stored config on start`() {
        preferences.put(ConfigKeys.ENABLED to true, ConfigKeys.TARGET_COMPONENT to target.flatten())
        reader.start()

        assertEquals(listOf(armed), published)
    }

    @Test
    fun `publishes nothing on start when nothing is stored, because that's the default`() {
        reader.start()

        assertEquals(emptyList(), published)
    }

    @Test
    fun `publishes each change once, however many keys it touched`() {
        reader.start()

        preferences.put(ConfigKeys.ENABLED to true, ConfigKeys.TARGET_COMPONENT to target.flatten())
        preferences.put(ConfigKeys.DRY_RUN to true)

        assertEquals(listOf(armed, armed.copy(dryRun = true)), published)
    }

    @Test
    fun `stop unregisters, and later changes are ignored`() {
        reader.start()

        reader.stop()
        preferences.put(ConfigKeys.ENABLED to true, ConfigKeys.TARGET_COMPONENT to target.flatten())

        assertTrue(preferences.listeners.isEmpty())
        assertEquals(emptyList(), published)
    }

    @Test
    fun `start after stop does nothing`() {
        reader.stop()

        reader.start()

        assertEquals(0, opened)
        assertTrue(preferences.listeners.isEmpty())
    }

    @Test
    fun `a change that leaves the config the same publishes nothing`() {
        preferences.put(ConfigKeys.ENABLED to true, ConfigKeys.TARGET_COMPONENT to target.flatten())
        reader.start()

        preferences.put(ConfigKeys.ENABLED to true)
        preferences.put("unknown_key" to 1)

        assertEquals(listOf(armed), published)
    }

    @Test
    fun `a stop that lands while start is opening the preferences leaves no listener behind`() {
        lateinit var racing: RemoteConfigReader
        racing = RemoteConfigReader(
            openPreferences = {
                racing.stop()
                preferences
            },
            onChange = published::add,
        )
        preferences.put(ConfigKeys.ENABLED to true, ConfigKeys.TARGET_COMPONENT to target.flatten())

        racing.start()

        assertTrue(preferences.listeners.isEmpty())
        assertEquals(emptyList(), published)
    }
}
