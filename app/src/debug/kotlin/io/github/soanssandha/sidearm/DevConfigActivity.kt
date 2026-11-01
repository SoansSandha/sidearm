package io.github.soanssandha.sidearm

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import io.github.libxposed.service.XposedService
import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.config.ConfigKeys
import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.data.RemoteConfigStore
import io.github.soanssandha.sidearm.data.VectorConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Debug builds only. Sets the hook's config from adb, for scripts and for settings the config app doesn't show,
 * such as dry run:
 *
 *     adb shell am start -n io.github.soanssandha.sidearm/.DevConfigActivity \
 *         --ez enabled true --es target_component com.example.app/.MainActivity --ez dry_run true
 *
 * Extras are named after the config keys, and any that are left out keep their stored value; `--esn
 * target_component` clears the target. With no extras it reports the stored config.
 *
 * The activity is invisible and finishes as soon as it's done, so every `am start` gets a fresh one with its own
 * extras. (A second start into a task that's still open would only bring it to the front, without the extras.)
 * The full result is logged under `SidearmDev`; a toast shows a short version.
 */
class DevConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extras = intent.extras
        lifecycleScope.launch {
            // An invisible activity still takes touches, so never let it linger.
            val service = withTimeoutOrNull(TIMEOUT_MS) { VectorConnection.service.filterNotNull().first() }
            val result = if (service == null) {
                NOT_CONNECTED
            } else {
                // Reading and writing the preferences are binder calls; keep them off the UI thread.
                withContext(Dispatchers.IO) { update(service, extras) }
            }
            finishWith(result)
        }
    }

    private fun update(service: XposedService, extras: Bundle?): String = runCatching {
        val store = RemoteConfigStore(service)
        val stored = store.read()
        val requested = requested(stored, extras)
        if (requested != stored) check(store.write(requested)) { "Vector didn't take the write" }
        val framework = "${service.frameworkName} ${service.frameworkVersion} (API ${service.apiVersion})"
        Log.i(TAG, "$framework, scope ${service.scope}")
        Log.i(TAG, "${if (requested == stored) "Unchanged" else "Stored"}: $requested")
        "Sidearm: ${if (requested == stored) "unchanged" else "stored"}. Enabled ${requested.enabled}, dry run " +
            "${requested.dryRun}, target ${requested.target?.packageName ?: "none"}"
    }.getOrElse { error ->
        Log.e(TAG, "Couldn't update the config", error)
        "Sidearm: failed. $error"
    }

    private fun finishWith(result: String) {
        if (isFinishing) return
        Toast.makeText(applicationContext, result, Toast.LENGTH_LONG).show()
        finish()
    }

    /** [stored], with every config extra that [extras] holds applied over it. A mistyped extra is an error. */
    private fun requested(stored: Config, extras: Bundle?): Config {
        if (extras == null) return stored
        val target = if (extras.containsKey(ConfigKeys.TARGET_COMPONENT)) {
            extra<String>(extras, ConfigKeys.TARGET_COMPONENT)?.takeIf(String::isNotEmpty)?.let { flat ->
                requireNotNull(TargetComponent.parse(flat)) { "Not a component: '$flat'" }
            }
        } else {
            stored.target
        }
        return Config(
            enabled = extra<Boolean>(extras, ConfigKeys.ENABLED) ?: stored.enabled,
            target = target,
            dryRun = extra<Boolean>(extras, ConfigKeys.DRY_RUN) ?: stored.dryRun,
        )
    }

    /** The extra at [key], or null if it's absent or null. Throws if it has another type, such as `--es` for a flag. */
    private inline fun <reified T : Any> extra(extras: Bundle, key: String): T? {
        @Suppress("DEPRECATION") // The typed getters can't tell a missing extra from one of the wrong type.
        val value = extras.get(key) ?: return null
        require(value is T) { "'$key' must be a ${T::class.java.simpleName}, was ${value::class.java.simpleName}" }
        return value
    }

    private companion object {
        const val TAG = "SidearmDev"
        const val TIMEOUT_MS = 10_000L
        const val NOT_CONNECTED = "Sidearm: Vector's service didn't connect. Is Sidearm enabled in the Vector manager?"
    }
}
