package io.github.soanssandha.sidearm.hook.config.fixture

import android.content.SharedPreferences

/**
 * Read-only preferences like Vector's in system_server: listeners are held strongly, and [put] notifies them once
 * per key, after every value is in.
 */
internal class FakeRemotePreferences(initial: Map<String, Any> = emptyMap()) : SharedPreferences {

    private val values = initial.toMutableMap()
    val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    /** Stores [changes] the way the app's commit arrives in system_server. */
    fun put(vararg changes: Pair<String, Any>) {
        values.putAll(changes)
        listeners.toList().forEach { listener ->
            changes.forEach { (key, _) -> listener.onSharedPreferenceChanged(this, key) }
        }
    }

    override fun getAll(): Map<String, *> = values.toMap()

    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        values[key] as? Set<String> ?: defValues

    override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = key in values

    override fun edit(): SharedPreferences.Editor = throw UnsupportedOperationException("Read only")

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener,
    ) {
        listeners -= listener
    }
}
