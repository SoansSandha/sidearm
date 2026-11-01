package io.github.soanssandha.sidearm.data

import android.os.Process
import io.github.libxposed.service.HookedTarget
import io.github.libxposed.service.XposedService
import io.github.soanssandha.sidearm.core.config.Config

/** [ModuleHost] backed by Vector's service binder. */
class VectorModuleHost(private val service: XposedService) : ModuleHost {
    private val store = RemoteConfigStore(service)

    override fun framework() = FrameworkInfo(service.frameworkName, service.frameworkVersion, service.apiVersion)

    override fun isSystemInScope(): Boolean = SYSTEM_SCOPE in service.scope

    override fun systemServerLoad(): ModuleLoad {
        // Vector lists system_server as process "system" under the system uid.
        val target = service.runningTargets.firstOrNull {
            it.uid == Process.SYSTEM_UID && it.processName == SYSTEM_PROCESS
        } ?: return ModuleLoad.NOT_RUNNING
        return when (target.state) {
            HookedTarget.State.UP_TO_DATE -> ModuleLoad.UP_TO_DATE
            HookedTarget.State.STALE -> ModuleLoad.STALE
            HookedTarget.State.RELOADING -> ModuleLoad.RELOADING
            HookedTarget.State.FAILED, null -> ModuleLoad.FAILED
        }
    }

    override fun readConfig(): Config = store.read()

    override fun writeConfig(config: Config): Boolean = store.write(config)

    private companion object {
        /** The `scope.list` entry for system_server. */
        const val SYSTEM_SCOPE = "system"
        const val SYSTEM_PROCESS = "system"
    }
}
