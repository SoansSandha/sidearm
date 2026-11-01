package io.github.soanssandha.sidearm.data

import io.github.soanssandha.sidearm.core.config.TargetComponent

/** An app's launcher activity, which a triple press can open. */
data class LauncherApp(val component: TargetComponent, val label: String) {
    /** Whether a search for [query] should show this app: a match on its name or package, ignoring case. */
    fun matches(query: String): Boolean {
        val needle = query.trim()
        return label.contains(needle, ignoreCase = true) || component.packageName.contains(needle, ignoreCase = true)
    }
}
