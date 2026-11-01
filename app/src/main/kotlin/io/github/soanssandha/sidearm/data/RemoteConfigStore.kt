package io.github.soanssandha.sidearm.data

import io.github.libxposed.service.XposedService
import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.config.ConfigCodec
import io.github.soanssandha.sidearm.core.config.ConfigKeys

/**
 * Reads and writes the settings the hook follows, in Vector's remote preferences, through the `:core` config
 * contract. Vector pushes every write to the hook in system_server, where it applies without a reboot.
 */
class RemoteConfigStore(private val service: XposedService) {

    /** The stored config. This can make a binder call, so keep it off the UI thread. */
    fun read(): Config = ConfigCodec.decode(service.getRemotePreferences(ConfigKeys.GROUP).all)

    /** Stores [config]. Returns false if Vector didn't take the write. This makes a binder call too. */
    fun write(config: Config): Boolean {
        val editor = service.getRemotePreferences(ConfigKeys.GROUP).edit()
        ConfigCodec.encode(config).forEach { (key, value) ->
            when (value) {
                null -> editor.remove(key)
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                else -> error("No preference type for $key: ${value::class.java.name}")
            }
        }
        return editor.commit()
    }
}
