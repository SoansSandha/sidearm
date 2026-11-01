package io.github.soanssandha.sidearm.hook.config

import android.content.SharedPreferences
import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.config.ConfigCodec

/**
 * Follows the settings the app stores in Vector's remote preferences and hands each new [Config] to [onChange].
 * Vector pushes changes into system_server, so they apply without a reboot (docs/plan.md §3.4).
 *
 * Vector holds listeners strongly, calls them on a binder thread while holding its own lock, and calls them once
 * per changed key. So the listener is always unregistered on [stop], that unregistering happens outside this
 * class's lock, and only actual changes reach [onChange]. [onChange] runs under this class's lock (and on a
 * binder thread, under Vector's), so it should only hand the config on.
 */
internal class RemoteConfigReader(
    private val openPreferences: () -> SharedPreferences,
    private val onChange: (Config) -> Unit,
) {

    private val lock = Any()
    private var preferences: SharedPreferences? = null
    private var config = Config.DEFAULT
    private var stopped = false
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { changed, _ -> publish(changed) }

    /**
     * Opens the preferences and starts following them. Opening is a blocking binder call to Vector, so keep it off
     * the input thread and off any looper the system_server watchdog monitors. Does nothing once stopped.
     */
    fun start() {
        if (synchronized(lock) { stopped }) return
        val opened = openPreferences()
        opened.registerOnSharedPreferenceChangeListener(listener)
        val keep = synchronized(lock) {
            if (!stopped) preferences = opened
            !stopped
        }
        if (keep) publish(opened) else opened.unregisterOnSharedPreferenceChangeListener(listener)
    }

    /** Stops following the preferences for good. A retiring hook generation must call this. */
    fun stop() {
        val opened = synchronized(lock) {
            stopped = true
            preferences.also { preferences = null }
        }
        opened?.unregisterOnSharedPreferenceChangeListener(listener)
    }

    // Reading inside the lock keeps an older snapshot from overwriting a newer one. It can't deadlock with a
    // callback: Vector's getAll() copies a ConcurrentHashMap without taking the lock it notifies under.
    private fun publish(source: SharedPreferences) {
        synchronized(lock) {
            if (stopped) return
            val next = ConfigCodec.decode(source.all)
            if (next == config) return
            config = next
            onChange(next)
        }
    }
}
