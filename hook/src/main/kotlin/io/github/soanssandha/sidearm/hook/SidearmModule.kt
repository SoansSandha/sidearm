package io.github.soanssandha.sidearm.hook

import android.util.Log
import android.view.KeyEvent
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.HookHandle
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.lang.reflect.Method

/**
 * Phase 0 probe: logs what `GestureLauncherService` decides on every power press and never changes
 * the outcome. The real interceptors replace these probes in Phase 2.
 */
class SidearmModule : XposedModule() {

    // Handed to the next generation on hot reload, so it can resolve targets even if this one
    // ended up with no hooks.
    @Volatile
    private var systemServerClassLoader: ClassLoader? = null

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        installProbes(param.classLoader, oldHandles = emptyList())
    }

    // The probes own no threads, timers or native hooks, so the old generation can always retire.
    override fun onHotReloading(param: HotReloadingParam): Boolean {
        param.setSavedInstanceState(systemServerClassLoader)
        return true
    }

    override fun onHotReloaded(param: HotReloadedParam) {
        val oldHandles = param.oldHookHandles
        val classLoader = param.savedInstanceState as? ClassLoader
            ?: oldHandles.firstOrNull()?.executable?.declaringClass?.classLoader
        if (classLoader == null) {
            oldHandles.forEach { runCatching { it.unhook() } }
            log(Log.WARN, TAG, "Hot reload: no system_server class loader; staying inactive")
            return
        }
        installProbes(classLoader, oldHandles)
    }

    /**
     * Installs every probe or none. Hooks from an old generation with a matching id are swapped in
     * place and the rest are unhooked. After any failure everything is unhooked, so stock behaviour
     * is left untouched.
     */
    private fun installProbes(classLoader: ClassLoader, oldHandles: List<HookHandle>) {
        systemServerClassLoader = classLoader
        val oldById = oldHandles.associateBy { it.id }
        val installed = mutableListOf<HookHandle>()
        runCatching {
            val targets = GestureLauncherTargets.resolve(classLoader).getOrThrow()
            val probes = probes(targets)
            probes.forEach { probe ->
                installed += oldById[probe.id]?.replaceHook(probe.hooker)
                    ?: hook(probe.method)
                        .setId(probe.id)
                        .setExceptionMode(ExceptionMode.PROTECTIVE)
                        .intercept(probe.hooker)
            }
            val probeIds = probes.map(Probe::id).toSet()
            oldHandles.filter { it.id !in probeIds }.forEach { runCatching { it.unhook() } }
            // ART may have inlined handleCameraGesture into its caller; force the call through the hook.
            val deoptimized = deoptimize(targets.interceptPowerKeyDown)
            "window=${targets.doubleTapWindowMs}ms powerSource=${targets.powerDoubleTapSource} " +
                "deoptimized=$deoptimized"
        }.onSuccess { details ->
            log(
                Log.INFO,
                TAG,
                "Probe active on $frameworkName $frameworkVersion (API $apiVersion): " +
                    "${installed.size} hooks (${oldById.size} swapped from old generation), $details",
            )
        }.onFailure { error ->
            (installed + oldHandles).forEach { runCatching { it.unhook() } }
            log(Log.WARN, TAG, "Probe not installed; stock behaviour unchanged", error)
        }
    }

    private fun probes(targets: GestureLauncherTargets): List<Probe> = listOf(
        Probe(ID_POWER_KEY_DOWN, targets.interceptPowerKeyDown, powerKeyDownProbe(targets)),
        Probe(ID_CAMERA_GESTURE, targets.handleCameraGesture, cameraGestureProbe()),
    )

    private fun powerKeyDownProbe(targets: GestureLauncherTargets) = Hooker { chain ->
        // Stock runs exactly once. Running it again would count a phantom tap (and could reach SOS).
        val result = chain.proceed()
        runCatching {
            val event = chain.getArg(0) as KeyEvent
            val snapshot = targets.snapshot(chain.thisObject, event, chain.getArg(2))
            log(
                Log.INFO,
                TAG,
                "powerDown t=${event.eventTime} device=${event.deviceId} longPress=${event.isLongPress} " +
                    "interactive=${chain.getArg(1)} result=$result $snapshot",
            )
        }.onFailure { log(Log.WARN, TAG, "powerDown probe failed", it) }
        result
    }

    private fun cameraGestureProbe() = Hooker { chain ->
        val result = chain.proceed()
        runCatching {
            log(
                Log.INFO,
                TAG,
                "handleCameraGesture(useWakelock=${chain.getArg(0)}, source=${chain.getArg(1)}) -> $result",
            )
        }
        result
    }

    private class Probe(val id: String, val method: Method, val hooker: Hooker)

    private companion object {
        const val TAG = "Sidearm"
        const val ID_POWER_KEY_DOWN = "interceptPowerKeyDown"
        const val ID_CAMERA_GESTURE = "handleCameraGesture"
    }
}
