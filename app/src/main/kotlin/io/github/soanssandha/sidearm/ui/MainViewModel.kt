package io.github.soanssandha.sidearm.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.data.AndroidDeviceChecks
import io.github.soanssandha.sidearm.data.AppCatalog
import io.github.soanssandha.sidearm.data.DeviceChecks
import io.github.soanssandha.sidearm.data.DoublePress
import io.github.soanssandha.sidearm.data.LauncherApp
import io.github.soanssandha.sidearm.data.ModuleHost
import io.github.soanssandha.sidearm.data.ModuleLoad
import io.github.soanssandha.sidearm.data.PackageManagerAppCatalog
import io.github.soanssandha.sidearm.data.VectorConnection
import io.github.soanssandha.sidearm.data.VectorModuleHost
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Holds the config screen's state. Every framework call runs on one background lane, in order, so a refresh can't
 * overwrite a write that's still in flight, and two quick taps are stored in the order they came. The app list loads
 * separately, so it never waits behind the framework.
 *
 * Writes aren't optimistic: the switch moves once Vector has taken the change, a few milliseconds later. Vector then
 * pushes it to the hook in system_server, where it applies without a reboot.
 *
 * @param hosts the framework connection: null until Vector sends its binder, and again if it dies
 * @param connectTimeoutMillis how long to wait for Vector before saying it isn't connected
 */
class MainViewModel(
    hosts: Flow<ModuleHost?>,
    private val device: DeviceChecks,
    private val catalog: AppCatalog,
    private val io: CoroutineDispatcher,
    connectTimeoutMillis: Long = CONNECT_TIMEOUT_MILLIS,
) : ViewModel() {
    private val lane = io.limitedParallelism(1)
    private val mutableState = MutableStateFlow(MainUiState(build = device.build))
    val state: StateFlow<MainUiState> = mutableState.asStateFlow()

    /** The current connection; read on [lane], set from the collector below. */
    @Volatile
    private var host: ModuleHost? = null

    init {
        viewModelScope.launch {
            hosts.collect { next ->
                host = next
                refresh()
            }
        }
        viewModelScope.launch {
            delay(connectTimeoutMillis)
            mutableState.update { state ->
                if (state.vector == VectorState.Connecting) state.copy(vector = VectorState.NotConnected) else state
            }
        }
    }

    /** Reads everything again. Call it when the screen comes back, since the user may have changed settings. */
    fun refresh() {
        viewModelScope.launch(lane) { reload() }
    }

    fun setEnabled(enabled: Boolean) = store { it.copy(enabled = enabled) }

    fun pick(app: LauncherApp) = store { it.copy(target = app.component) }

    /** Loads the launcher apps for the picker. */
    fun loadApps() {
        viewModelScope.launch(io) {
            val apps = runCatching { catalog.launcherApps() }
                .onFailure { Log.w(TAG, "Couldn't list launcher apps", it) }
                .getOrDefault(emptyList())
            mutableState.update { it.copy(apps = apps) }
        }
    }

    fun messageShown() = mutableState.update { it.copy(message = null) }

    /** Runs on [lane]. */
    private fun reload() {
        val doublePress = device.doublePress()
        val current = host
        val snapshot = current?.let { host ->
            runCatching { readHost(host) }.onFailure { Log.w(TAG, "Couldn't read from Vector", it) }.getOrNull()
        }
        mutableState.update { state ->
            when {
                snapshot != null -> snapshot.applyTo(state).copy(doublePress = doublePress)

                // The binder died, or every call on it fails: treat it as gone.
                current != null || state.vector is VectorState.Connected -> state.disconnected(doublePress)

                else -> state.copy(doublePress = doublePress)
            }
        }
    }

    /**
     * Applies [change] to the stored config, read again first so a change made elsewhere (such as a dry run set
     * over adb) isn't overwritten with what the screen last saw.
     */
    private fun store(change: (Config) -> Config) {
        viewModelScope.launch(lane) {
            val current = host ?: return@launch
            if (mutableState.value.config == null) return@launch
            runCatching {
                val stored = current.readConfig()
                val next = change(stored)
                // Null if Vector didn't take the write.
                if (next == stored) stored else next.takeIf { current.writeConfig(it) }
            }.onSuccess { stored ->
                if (stored != null) {
                    mutableState.update { it.copy(config = stored, targetApp = findApp(stored.target)) }
                } else {
                    mutableState.update { it.copy(message = UserMessage.SAVE_FAILED) }
                }
            }.onFailure { error ->
                Log.w(TAG, "Couldn't write to Vector", error)
                mutableState.update { it.copy(message = UserMessage.SAVE_FAILED) }
                // The binder may have died; show it now rather than when Vector's death notice arrives.
                reload()
            }
        }
    }

    /** Binder calls only: a failure here means Vector is unreachable. */
    private fun readHost(host: ModuleHost): HostSnapshot {
        val config = host.readConfig()
        return HostSnapshot(
            vector = VectorState.Connected(host.framework()),
            systemInScope = host.isSystemInScope(),
            // Optional: the screen still works if this framework can't report it.
            load = runCatching { host.systemServerLoad() }.getOrNull(),
            config = config,
            targetApp = findApp(config.target),
        )
    }

    /** The target's launcher app, or null if there's none, it's gone, or the package manager failed. */
    private fun findApp(target: TargetComponent?): LauncherApp? = target?.let {
        runCatching { catalog.find(it) }.onFailure { error -> Log.w(TAG, "Couldn't look up $it", error) }.getOrNull()
    }

    private class HostSnapshot(
        val vector: VectorState.Connected,
        val systemInScope: Boolean,
        val load: ModuleLoad?,
        val config: Config,
        val targetApp: LauncherApp?,
    ) {
        fun applyTo(state: MainUiState) = state.copy(
            vector = vector,
            systemInScope = systemInScope,
            load = load,
            config = config,
            targetApp = targetApp,
        )
    }

    private fun MainUiState.disconnected(doublePress: DoublePress) = copy(
        vector = VectorState.NotConnected,
        systemInScope = null,
        load = null,
        config = null,
        targetApp = null,
        doublePress = doublePress,
    )

    companion object {
        private const val TAG = "Sidearm"
        private const val CONNECT_TIMEOUT_MILLIS = 3_000L

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return viewModelFactory {
                initializer {
                    MainViewModel(
                        hosts = VectorConnection.service.map { service -> service?.let(::VectorModuleHost) },
                        device = AndroidDeviceChecks(appContext.contentResolver),
                        catalog = PackageManagerAppCatalog(appContext.packageManager, appContext.packageName),
                        io = Dispatchers.IO,
                    )
                }
            }
        }
    }
}
