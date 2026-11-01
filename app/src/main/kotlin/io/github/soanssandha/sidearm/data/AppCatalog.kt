package io.github.soanssandha.sidearm.data

import io.github.soanssandha.sidearm.core.config.TargetComponent

/** The apps a triple press can open. Both calls query the package manager, so keep them off the main thread. */
interface AppCatalog {
    /** Every launcher activity except Sidearm's own, sorted by name. */
    fun launcherApps(): List<LauncherApp>

    /** [component] if it's still an enabled launcher activity, or null if it's gone. */
    fun find(component: TargetComponent): LauncherApp?
}
