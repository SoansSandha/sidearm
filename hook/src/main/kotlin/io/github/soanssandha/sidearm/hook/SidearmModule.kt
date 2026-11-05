package io.github.soanssandha.sidearm.hook

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedInterface.Invoker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import io.github.soanssandha.sidearm.core.config.ConfigKeys
import io.github.soanssandha.sidearm.core.sequencer.Timing
import io.github.soanssandha.sidearm.hook.config.RemoteConfigReader
import io.github.soanssandha.sidearm.hook.effect.BinderCallerIdentity
import io.github.soanssandha.sidearm.hook.effect.DeviceWaker
import io.github.soanssandha.sidearm.hook.effect.HeldCamera
import io.github.soanssandha.sidearm.hook.effect.HeldStart
import io.github.soanssandha.sidearm.hook.effect.HookLog
import io.github.soanssandha.sidearm.hook.effect.Sequencer
import io.github.soanssandha.sidearm.hook.effect.SystemEffectRunner
import io.github.soanssandha.sidearm.hook.effect.TargetLauncher
import io.github.soanssandha.sidearm.hook.effect.postGuarded
import io.github.soanssandha.sidearm.hook.intercept.ActivityStartHooker
import io.github.soanssandha.sidearm.hook.intercept.CameraGestureHooker
import io.github.soanssandha.sidearm.hook.intercept.PowerKeyDownHooker
import io.github.soanssandha.sidearm.hook.intercept.SequenceStart
import io.github.soanssandha.sidearm.hook.intercept.SystemUiCameraStart
import io.github.soanssandha.sidearm.hook.target.ActivityTaskManagerTargets
import io.github.soanssandha.sidearm.hook.target.GestureLauncherTargets
import io.github.soanssandha.sidearm.hook.target.KeyguardDismissCallbackTargets
import io.github.soanssandha.sidearm.hook.target.PhoneWindowManagerTargets
import io.github.soanssandha.sidearm.hook.target.PowerManagerTargets
import java.lang.reflect.Method
import kotlin.concurrent.thread

/**
 * Sidearm's entry point in system_server. It resolves every framework target, hooks `interceptPowerKeyDown` and
 * `handleCameraGesture`, and follows the config the app stores. If any of those targets is missing it installs
 * nothing, and the power button keeps its stock behaviour (docs/plan.md §3.5).
 *
 * The clean launch mode's hook on `startActivityAsUser` is installed apart from them: if it can't be, the clean mode
 * reads as classic and everything else works as before (ADR 0010).
 */
class SidearmModule : XposedModule() {

    // Handed to the next generation on hot reload, so it can resolve targets even if this one ended up with no
    // hooks.
    @Volatile
    private var systemServerClassLoader: ClassLoader? = null

    @Volatile
    private var runtime: Runtime? = null

    private val hookLog = object : HookLog {
        override fun info(message: String) = log(Log.INFO, TAG, message)

        override fun warn(message: String, error: Throwable?) = log(Log.WARN, TAG, message, error)
    }

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        install(param.classLoader, oldHandles = emptyList())
    }

    // Retiring runs any held camera, drops the pending timer and stops following the config, so nothing in
    // system_server calls into this generation once the new one takes over.
    override fun onHotReloading(param: HotReloadingParam): Boolean {
        runtime?.retire()
        runtime = null
        param.setSavedInstanceState(systemServerClassLoader)
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        val oldHandles = param.oldHookHandles
        val classLoader = param.savedInstanceState as? ClassLoader
            ?: oldHandles.firstOrNull()?.executable?.declaringClass?.classLoader
        if (classLoader == null) {
            unhookAll(oldHandles)
            hookLog.warn("Hot reload: no system_server class loader; staying inactive")
            return
        }
        install(classLoader, oldHandles)
    }

    /**
     * Installs every hook or none. Hooks from an old generation with a matching id are swapped in place and the
     * rest are unhooked. After any failure everything is unhooked, so stock behaviour is left untouched.
     */
    private fun install(classLoader: ClassLoader, oldHandles: List<HookHandle>) {
        systemServerClassLoader = classLoader
        val oldById = oldHandles.associateBy { it.id }
        val installed = mutableListOf<HookHandle>()
        var built: Runtime? = null
        runCatching {
            val targets = Targets(
                gestureLauncher = GestureLauncherTargets.resolve(classLoader).getOrThrow(),
                windowManager = PhoneWindowManagerTargets.resolve(classLoader).getOrThrow(),
                dismissCallback = KeyguardDismissCallbackTargets.resolve(classLoader).getOrThrow(),
                power = PowerManagerTargets.resolve().getOrThrow(),
                // Optional: without it only the clean launch mode is missing (ADR 0010).
                activityTaskManager = ActivityTaskManagerTargets.resolve(classLoader)
                    .onFailure { hookLog.warn("Clean launch unavailable: activity-start target not found", it) }
                    .getOrNull(),
            )
            val gestureLauncher = targets.gestureLauncher
            val next = build(targets)
            built = next
            val specs = hookSpecs(targets, next)
            specs.forEach { spec -> installed += hookOrSwap(spec, oldById) }
            val cleanSpec = activityStartSpec(targets, next)
            val clean = cleanSpec != null && runCatching { installed += hookOrSwap(cleanSpec, oldById) }
                .onFailure { hookLog.warn("Clean launch unavailable: couldn't hook startActivityAsUser", it) }
                .isSuccess
            next.sequencer.cleanAvailable = clean
            val keptIds = specs.map(HookSpec::id).toSet() + listOfNotNull(cleanSpec?.id?.takeIf { clean })
            unhookAll(oldHandles.filter { it.id !in keptIds })
            // ART may have inlined handleCameraGesture into its caller; force the call through the hook.
            if (!deoptimize(gestureLauncher.interceptPowerKeyDown)) {
                hookLog.warn("interceptPowerKeyDown not deoptimized; an inlined camera call would skip the hook")
            }
            runtime = next
            next.start()
            "window=${gestureLauncher.doubleTapWindowMs}ms powerSource=${gestureLauncher.powerDoubleTapSource} " +
                "clean=${if (clean) "available" else "unavailable"}"
        }.onSuccess { details ->
            hookLog.info(
                "Active on $frameworkName $frameworkVersion (API $apiVersion): ${installed.size} hooks " +
                    "(${oldById.size} swapped from old generation), $details",
            )
        }.onFailure { error ->
            unhookAll((installed + oldHandles).distinct())
            built?.retire()
            runtime = null
            hookLog.warn("Not installed; stock behaviour unchanged", error)
        }
    }

    private fun build(targets: Targets): Runtime {
        // Timers, launches and config changes run on system_server's main looper. Vector calls
        // onSystemServerStarting after SystemServer has prepared it, and its messages only run once boot is done.
        val handler = Handler(checkNotNull(Looper.getMainLooper()) { "No main looper yet" })
        val gestureLauncher = targets.gestureLauncher
        val heldCamera = HeldCamera(getInvoker(gestureLauncher.handleCameraGesture).setType(Invoker.Type.ORIGIN))
        val heldStart = targets.activityTaskManager?.let { activityTasks ->
            HeldStart(
                original = getInvoker(activityTasks.startActivityAsUser).setType(Invoker.Type.ORIGIN),
                identity = BinderCallerIdentity,
                startSuccess = activityTasks.startSuccess,
                log = hookLog,
            )
        }
        val effects = SystemEffectRunner(
            handler = handler,
            heldCamera = heldCamera,
            heldStart = heldStart,
            launcher = TargetLauncher(
                windowManager = targets.windowManager,
                dismissCallback = targets.dismissCallback,
                handler = handler,
                latestPress = { heldCamera.service?.let(gestureLauncher::lastPowerDown) },
                log = hookLog,
            ),
            waker = DeviceWaker(targets.windowManager, targets.power, hookLog),
            log = hookLog,
        )
        val window = gestureLauncher.doubleTapWindowMs
        // The late-start wait must outlast the hold; stretching it keeps an unusually long window from failing
        // the whole install over the clean mode alone.
        val timing = Timing(
            windowMs = window,
            lateStartMs = maxOf(Timing.DEFAULT_LATE_START_MS, window + Timing.DEFAULT_GRACE_MS),
        )
        val sequencer = Sequencer(timing, effects, hookLog)
        // Vector calls the listener on a binder thread under its own lock; apply the change on the main looper.
        val config = RemoteConfigReader(openPreferences = { getRemotePreferences(ConfigKeys.GROUP) }) { config ->
            handler.postGuarded(hookLog, "Config change") {
                hookLog.info("Config: $config")
                sequencer.updateConfig(config)
            }
        }
        return Runtime(handler, sequencer, effects, heldCamera, config, hookLog)
    }

    private fun hookSpecs(targets: Targets, runtime: Runtime): List<HookSpec> {
        val gestureLauncher = targets.gestureLauncher
        val sequenceStart = SequenceStart(targets.windowManager)
        return listOf(
            HookSpec(
                ID_POWER_KEY_DOWN,
                gestureLauncher.interceptPowerKeyDown,
                PowerKeyDownHooker(gestureLauncher, sequenceStart, runtime.sequencer, hookLog),
            ),
            HookSpec(
                ID_CAMERA_GESTURE,
                gestureLauncher.handleCameraGesture,
                CameraGestureHooker(gestureLauncher, sequenceStart, runtime.sequencer, runtime.heldCamera, hookLog),
            ),
        )
    }

    /** The clean launch mode's hook on every activity start, or null when its targets weren't found. */
    private fun activityStartSpec(targets: Targets, runtime: Runtime): HookSpec? {
        val activityTasks = targets.activityTaskManager ?: return null
        return HookSpec(
            ID_ACTIVITY_START,
            activityTasks.startActivityAsUser,
            ActivityStartHooker(
                sequencer = runtime.sequencer,
                matcher = SystemUiCameraStart(activityTasks, BinderCallerIdentity),
                identity = BinderCallerIdentity,
                startSuccess = activityTasks.startSuccess,
                uptimeMillis = SystemClock::uptimeMillis,
                log = hookLog,
            ),
        )
    }

    /** Swaps [spec]'s hooker into the old generation's hook with the same id, or hooks its method afresh. */
    private fun hookOrSwap(spec: HookSpec, oldById: Map<String?, HookHandle>): HookHandle =
        oldById[spec.id]?.replaceHook(spec.hooker)
            ?: hook(spec.method).setId(spec.id).setExceptionMode(ExceptionMode.PROTECTIVE).intercept(spec.hooker)

    private fun unhookAll(handles: List<HookHandle>) {
        handles.forEach { handle ->
            runCatching { handle.unhook() }.onFailure { hookLog.warn("Couldn't unhook ${handle.id}", it) }
        }
    }

    private class HookSpec(val id: String, val method: Method, val hooker: Hooker)

    /** Every framework handle the hook needs, resolved together so that one missing member installs nothing. */
    private class Targets(
        val gestureLauncher: GestureLauncherTargets,
        val windowManager: PhoneWindowManagerTargets,
        val dismissCallback: KeyguardDismissCallbackTargets,
        val power: PowerManagerTargets,
        val activityTaskManager: ActivityTaskManagerTargets?,
    )

    /** One generation's live objects, started after its hooks are in and retired as a whole. */
    private class Runtime(
        private val handler: Handler,
        val sequencer: Sequencer,
        private val effects: SystemEffectRunner,
        val heldCamera: HeldCamera,
        private val config: RemoteConfigReader,
        private val log: HookLog,
    ) {
        /**
         * Reads the config once boot is done; until then the feature is off. Opening Vector's preferences is a
         * blocking binder call, so it runs on a short-lived thread rather than on a looper the system_server
         * watchdog monitors. The thread retries a few times if Vector isn't ready, then ends.
         */
        fun start() {
            handler.postGuarded(log, "Config read") {
                thread(name = "Sidearm config", isDaemon = true) {
                    // An exception escaping any system_server thread restarts system_server.
                    runCatching(::readConfig).onFailure { log.warn("Config read failed; the feature stays off", it) }
                }
            }
        }

        fun retire() {
            config.stop()
            sequencer.retire()
            effects.release()
        }

        private fun readConfig() {
            for (attempt in 1..CONFIG_ATTEMPTS) {
                val error = runCatching(config::start).exceptionOrNull() ?: return
                log.warn("Couldn't read the config (attempt $attempt of $CONFIG_ATTEMPTS)", error)
                if (attempt < CONFIG_ATTEMPTS) Thread.sleep(CONFIG_RETRY_MS)
            }
            log.warn("Config unavailable; the feature stays off until the next reboot or install")
        }
    }

    private companion object {
        const val TAG = "Sidearm"
        const val ID_POWER_KEY_DOWN = "interceptPowerKeyDown"
        const val ID_CAMERA_GESTURE = "handleCameraGesture"
        const val ID_ACTIVITY_START = "startActivityAsUser"
        const val CONFIG_ATTEMPTS = 5
        const val CONFIG_RETRY_MS = 5_000L
    }
}
