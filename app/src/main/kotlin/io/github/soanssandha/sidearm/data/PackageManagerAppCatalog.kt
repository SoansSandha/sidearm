package io.github.soanssandha.sidearm.data

import android.content.Intent
import android.content.pm.PackageManager
import io.github.soanssandha.sidearm.core.config.TargetComponent
import java.text.Collator

/**
 * [AppCatalog] from the package manager. The manifest's `<queries>` entry for MAIN/LAUNCHER makes every launcher
 * activity visible, so the app needs no QUERY_ALL_PACKAGES. These are the same activities the hook accepts as targets.
 */
class PackageManagerAppCatalog(private val packageManager: PackageManager, private val ownPackage: String) :
    AppCatalog {

    override fun launcherApps(): List<LauncherApp> {
        val collator = Collator.getInstance()
        return query(launcherIntent())
            .filter { it.component.packageName != ownPackage }
            .sortedWith(compareBy(collator) { it.label })
    }

    override fun find(component: TargetComponent): LauncherApp? =
        query(launcherIntent().setPackage(component.packageName)).firstOrNull { it.component == component }

    private fun query(intent: Intent): List<LauncherApp> =
        packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0)).mapNotNull { info ->
            val activity = info.activityInfo
            // TargetComponent rejects names the config contract couldn't store.
            runCatching { TargetComponent(activity.packageName, activity.name) }.getOrNull()?.let { component ->
                LauncherApp(component, info.loadLabel(packageManager).toString())
            }
        }

    private fun launcherIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
}
