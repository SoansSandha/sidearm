package io.github.soanssandha.sidearm.ui.fixture

import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.data.AppCatalog
import io.github.soanssandha.sidearm.data.LauncherApp

/** Set [broken] to make every call throw, as a failing package manager would. */
class FakeAppCatalog(var apps: List<LauncherApp>) : AppCatalog {
    var broken = false

    override fun launcherApps(): List<LauncherApp> = working { apps }

    override fun find(component: TargetComponent): LauncherApp? = working {
        apps.firstOrNull { it.component == component }
    }

    private fun <T> working(block: () -> T): T {
        check(!broken) { "Package manager failed" }
        return block()
    }
}
