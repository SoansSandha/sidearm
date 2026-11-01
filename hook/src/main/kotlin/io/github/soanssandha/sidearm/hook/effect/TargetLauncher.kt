package io.github.soanssandha.sidearm.hook.effect

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.hook.target.PhoneWindowManagerTargets
import java.lang.reflect.InvocationTargetException

/**
 * Opens the chosen app through PhoneWindowManager's own launch path, the way its double-press-to-app setting does
 * (`launchTargetActivityOnMultiPressPower`). For now it opens the app only while the keyguard isn't showing; the
 * unlock prompt arrives in Phase 3. Call it on a system_server looper, never on the input thread.
 *
 * It checks the target against system_server's own user (user 0) and opens it for the current user, as
 * PhoneWindowManager does. Those are the same user on a phone with one user, which is all v1 supports.
 */
internal class TargetLauncher(private val windowManager: PhoneWindowManagerTargets, private val log: HookLog) {

    /**
     * Opens [target]. When it can't, calls [fallback], which runs the held camera; it's null when the camera has
     * already run, and then the camera simply stays open.
     */
    fun launch(target: TargetComponent, fallback: (() -> Unit)?) {
        val instead = if (fallback != null) "opening the camera instead" else "the camera stays open"
        val opened = runCatching { open(target, instead) }.getOrElse { error ->
            val cause = (error as? InvocationTargetException)?.cause ?: error
            log.warn("Couldn't open ${target.flatten()}; $instead", cause)
            false
        }
        if (!opened) fallback?.invoke()
    }

    /** For a dry run: logs what [launch] would do, without opening anything. */
    fun check(target: TargetComponent) {
        val outcome = when (val plan = plan(target)) {
            is Plan.Open -> "would open"
            is Plan.CantOpen -> "wouldn't open, because ${plan.reason}"
        }
        log.info("Dry run: ${target.flatten()} $outcome")
    }

    private fun open(target: TargetComponent, instead: String): Boolean = when (val plan = plan(target)) {
        is Plan.Open -> {
            windowManager.startActivity(plan.policy, plan.intent)
            // PhoneWindowManager still skips it, and logs why, while setup is incomplete.
            log.info("Asked PhoneWindowManager to open ${target.flatten()}")
            true
        }

        is Plan.CantOpen -> {
            log.info("Not opening ${target.flatten()}, because ${plan.reason}; $instead")
            false
        }
    }

    private fun plan(target: TargetComponent): Plan {
        val policy = windowManager.policy() ?: return Plan.CantOpen("PhoneWindowManager isn't running")
        if (windowManager.isKeyguardShowing(policy)) {
            return Plan.CantOpen("the keyguard is showing (unlock-then-open arrives in Phase 3)")
        }
        val intent = launcherIntent(windowManager.context(policy), target)
            ?: return Plan.CantOpen("it isn't an installed launcher activity")
        return Plan.Open(policy, intent)
    }

    /**
     * The intent a home screen uses to open [target], or null unless [target] is one of its package's enabled,
     * exported launcher activities. system_server can start any activity at all, even unexported ones, so this
     * keeps it to the ones a home screen could open. The query leaves out disabled components.
     */
    @SuppressLint("QueryPermissionsNeeded") // Package visibility doesn't filter system_server's queries.
    private fun launcherIntent(context: Context, target: TargetComponent): Intent? {
        val launcherActivities = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(target.packageName)
        val isLauncherActivity = context.packageManager
            .queryIntentActivities(launcherActivities, PackageManager.ResolveInfoFlags.of(0))
            .map { it.activityInfo }
            .any { it.exported && it.packageName == target.packageName && it.name == target.className }
        if (!isLauncherActivity) return null
        // The same flags PhoneWindowManager uses, so an app that's already open comes back where it was.
        return Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(target.packageName, target.className))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }

    private sealed interface Plan {
        class Open(val policy: Any, val intent: Intent) : Plan

        class CantOpen(val reason: String) : Plan
    }
}
