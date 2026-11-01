package io.github.soanssandha.sidearm.ui.fixture

import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.data.FrameworkInfo
import io.github.soanssandha.sidearm.data.ModuleHost
import io.github.soanssandha.sidearm.data.ModuleLoad

/** A framework that keeps the config in memory. Set [dead] to make every call throw, as a dead binder does. */
class FakeModuleHost(var config: Config = Config.DEFAULT) : ModuleHost {
    val info = FrameworkInfo("Vector", "2.2", 102)
    var inScope = true
    var load = ModuleLoad.UP_TO_DATE
    var loadReportable = true
    var acceptWrites = true
    var dead = false
    val writes = mutableListOf<Config>()

    override fun framework(): FrameworkInfo = alive { info }

    override fun isSystemInScope(): Boolean = alive { inScope }

    override fun systemServerLoad(): ModuleLoad = alive {
        if (!loadReportable) throw UnsupportedOperationException("Not on this API")
        load
    }

    override fun readConfig(): Config = alive { config }

    override fun writeConfig(config: Config): Boolean = alive {
        writes += config
        if (acceptWrites) this.config = config
        acceptWrites
    }

    private fun <T> alive(block: () -> T): T {
        check(!dead) { "Binder died" }
        return block()
    }
}
