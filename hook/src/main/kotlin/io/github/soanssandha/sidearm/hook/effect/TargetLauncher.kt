package io.github.soanssandha.sidearm.hook.effect

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.hook.target.KeyguardDismissCallbackTargets
import io.github.soanssandha.sidearm.hook.target.KeyguardDismissCallbackTargets.Outcome
import io.github.soanssandha.sidearm.hook.target.PhoneWindowManagerTargets
import java.lang.reflect.InvocationTargetException

/**
 * Opens the chosen app through PhoneWindowManager's own launch path, the way its double-press-to-app setting does
 * (`launchTargetActivityOnMultiPressPower`). If the keyguard is showing, it first asks SystemUI to unlock, the way the
 * Wallet tile does, and opens the app once the unlock succeeds (docs/plan.md §3.3). Call it on [handler]'s looper,
 * never on the input thread.
 *
 * It checks the target against system_server's own user (user 0) and opens it for the current user, as
 * PhoneWindowManager does. Those are the same user on a phone with one user, which is all v1 supports.
 *
 * [latestPress] is the event time of the latest power press stock counted, or null if it isn't known yet.
 */
internal class TargetLauncher(
    private val windowManager: PhoneWindowManagerTargets,
    private val dismissCallback: KeyguardDismissCallbackTargets,
    private val handler: Handler,
    private val latestPress: () -> Long?,
    private val log: HookLog,
) {

    /** The latest press stock counted. Call it on the input thread right after press 3, for [launch]'s [press3]. */
    fun latestPressNow(): Long? = latestPress()

    /**
     * Opens [target], through the unlock prompt if the phone is locked. When it can't be opened, calls [fallback],
     * which runs the held camera; it's null when the camera has already run, and then the camera simply stays open.
     * If the user cancels the unlock prompt, nothing opens, as with the Wallet tile. [press3] is press 3's event time,
     * read on the input thread, so a later press can be told apart from it.
     */
    fun launch(target: TargetComponent, fallback: (() -> Unit)?, press3: Long?) {
        val request = Request(target, fallback, press3)
        request.attempt {
            when (val plan = plan(target)) {
                is Plan.Open -> open(plan.policy, plan.intent, request)
                is Plan.Unlock -> scheduleUnlock(request)
                is Plan.CantOpen -> cantOpen(plan, request)
            }
        }
    }

    /** For a dry run: logs what [launch] would do, without opening anything. */
    fun check(target: TargetComponent) {
        val outcome = when (val plan = plan(target)) {
            is Plan.Open -> "would open"
            is Plan.Unlock -> "would ask to unlock after $UNLOCK_DELAY_MS ms, then open"
            is Plan.CantOpen -> "wouldn't open, because ${plan.reason}"
        }
        log.info("Dry run: ${target.flatten()} $outcome")
    }

    /**
     * Waits until SystemUI can show the fingerprint overlay (see [UNLOCK_DELAY_MS]), then asks for the unlock. A power
     * press in the meantime, such as presses 4 and 5 of Emergency SOS, calls it off, so the prompt can't cover SOS.
     * Emergency SOS only counts presses less than 300 ms apart, so its press 4 always lands inside the delay.
     */
    private fun scheduleUnlock(request: Request): Boolean {
        handler.postGuardedDelayed(log, "Unlock request", UNLOCK_DELAY_MS) { requestUnlock(request) }
        log.info("Asking SystemUI to unlock in $UNLOCK_DELAY_MS ms, then opening ${request.name}")
        return true
    }

    private fun requestUnlock(request: Request) {
        val latest = latestPress()
        if (request.press3 == null || latest == null) {
            log.info("Not asking to unlock: can't tell whether another power press followed press 3")
            return
        }
        if (latest != request.press3) {
            log.info("Not asking to unlock: another power press followed press 3, perhaps for Emergency SOS")
            return
        }
        request.attempt {
            // Checked again: a face unlock may have finished while waiting.
            when (val plan = plan(request.target)) {
                is Plan.Open -> open(plan.policy, plan.intent, request)
                is Plan.Unlock -> dismissKeyguard(plan, request)
                is Plan.CantOpen -> cantOpen(plan, request)
            }
        }
    }

    private fun dismissKeyguard(plan: Plan.Unlock, request: Request): Boolean {
        val callback = DismissCallbackBinder(dismissCallback) { outcome ->
            // SystemUI calls back on a binder thread; finish on the looper the launch started on.
            handler.postGuarded(log, "Unlock result") { onUnlock(outcome, request) }
        }
        windowManager.dismissKeyguard(plan.policy, dismissCallback.asInterface(callback))
        log.info("Asked SystemUI to unlock before opening ${request.name}")
        return true
    }

    private fun onUnlock(outcome: Outcome, request: Request) {
        when (outcome) {
            Outcome.SUCCEEDED -> openOnceUnlocked(request, checksLeft = UNLOCKED_CHECKS)

            Outcome.CANCELLED -> log.info("Unlock cancelled; not opening ${request.name}")

            // PhoneWindowManager also reports an error when the keyguard went away on its own, such as a face
            // unlock just before the request, so look again before falling back.
            Outcome.ERROR -> request.attempt {
                when (val plan = plan(request.target)) {
                    is Plan.Open -> open(plan.policy, plan.intent, request)

                    is Plan.Unlock, is Plan.CantOpen -> {
                        log.info("Unlock failed; not opening ${request.name}, ${request.instead}")
                        false
                    }
                }
            }
        }
    }

    /**
     * Opens the app only once PhoneWindowManager also sees the keyguard gone, so nothing opens over the lock screen
     * even if a success report came early or wasn't genuine. Its keyguard state can trail SystemUI's report a little.
     */
    private fun openOnceUnlocked(request: Request, checksLeft: Int) {
        val plan = plan(request.target)
        when {
            plan is Plan.Open -> request.attempt { open(plan.policy, plan.intent, request) }

            plan is Plan.Unlock && checksLeft > 0 ->
                handler.postGuardedDelayed(log, "Unlocked check", UNLOCKED_CHECK_INTERVAL_MS) {
                    openOnceUnlocked(request, checksLeft - 1)
                }

            plan is Plan.Unlock -> log.warn("Unlock reported, but the keyguard is still showing; not opening")

            else -> log.info("Unlocked, but not opening ${request.name}, because ${(plan as Plan.CantOpen).reason}")
        }
    }

    private fun open(policy: Any, intent: Intent, request: Request): Boolean {
        windowManager.startActivity(policy, intent)
        // PhoneWindowManager still skips it, and logs why, while setup is incomplete.
        log.info("Asked PhoneWindowManager to open ${request.name}")
        return true
    }

    private fun cantOpen(plan: Plan.CantOpen, request: Request): Boolean {
        log.info("Not opening ${request.name}, because ${plan.reason}; ${request.instead}")
        return false
    }

    private fun plan(target: TargetComponent): Plan {
        val policy = windowManager.policy() ?: return Plan.CantOpen("PhoneWindowManager isn't running")
        val intent = launcherIntent(windowManager.context(policy), target)
            ?: return Plan.CantOpen("it isn't an installed launcher activity")
        return if (windowManager.isKeyguardShowing(policy)) Plan.Unlock(policy, intent) else Plan.Open(policy, intent)
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

    /** One press 3's launch, which may finish later on the looper once the unlock comes back. */
    private inner class Request(val target: TargetComponent, val fallback: (() -> Unit)?, val press3: Long?) {
        val name: String = target.flatten()
        val instead = if (fallback != null) "opening the camera instead" else "the camera stays open"

        /** Runs [step]; if it throws or returns false, logs why and runs the fallback. */
        fun attempt(step: () -> Boolean) {
            val handled = runCatching(step).getOrElse { error ->
                log.warn("Couldn't open $name; $instead", (error as? InvocationTargetException)?.cause ?: error)
                false
            }
            if (!handled) fallback?.invoke()
        }
    }

    private sealed interface Plan {
        class Open(val policy: Any, val intent: Intent) : Plan

        class Unlock(val policy: Any, val intent: Intent) : Plan

        class CantOpen(val reason: String) : Plan
    }

    private companion object {
        /**
         * How long after press 3 to ask for the unlock. SystemUI only shows the fingerprint overlay once its keyguard
         * has finished leaving the always-on display, up to 500 ms after the wake at press 1 or 2
         * (`FromDozingTransitionInteractor`, `FromAodTransitionInteractor`). Asked sooner, it shows the full bouncer,
         * where the fingerprint sensor is off. Press 3 lands 150–300 ms after press 2, so this is enough.
         */
        const val UNLOCK_DELAY_MS = 400L

        /** How often, and how many times, to look for the keyguard gone after a success report: up to 0.5 s. */
        const val UNLOCKED_CHECK_INTERVAL_MS = 100L
        const val UNLOCKED_CHECKS = 5
    }
}
