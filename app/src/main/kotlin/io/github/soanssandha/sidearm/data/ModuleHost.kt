package io.github.soanssandha.sidearm.data

import io.github.soanssandha.sidearm.core.config.Config

/**
 * What the config screen needs from the Xposed framework hosting Sidearm. Every call can make a binder call, so keep
 * them off the main thread; any of them can throw if the framework dies.
 */
interface ModuleHost {
    /** Name, version and API level of the framework, such as Vector 2.2, API 102. */
    fun framework(): FrameworkInfo

    /** Whether System Framework is in Sidearm's scope, so the hook loads into system_server. */
    fun isSystemInScope(): Boolean

    /** Whether the hook is running in system_server, and whether that copy is the installed version. */
    fun systemServerLoad(): ModuleLoad

    fun readConfig(): Config

    /** Stores [config]; false if the framework didn't take it. */
    fun writeConfig(config: Config): Boolean
}
