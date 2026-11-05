package io.github.soanssandha.sidearm.hook.target

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Reflective handles into Android 17's `ActivityTaskManagerService`, for the clean launch mode: its binder entry
 * `startActivityAsUser` is where SystemUI's camera start arrives, and where it's held (ADR 0010,
 * docs/hook-targets.md).
 *
 * Resolved once at boot, all-or-nothing, but apart from the other targets: if these are missing, only the clean mode
 * is unavailable, and everything else installs as usual.
 */
internal class ActivityTaskManagerTargets private constructor(
    /** `int startActivityAsUser(IApplicationThread, String, String, Intent, String, IBinder, String, int, int, ProfilerInfo, Bundle, int)`. */
    val startActivityAsUser: Method,
    /** `ActivityManager.START_SUCCESS`, what a held or dropped start returns. */
    val startSuccess: Int,
    private val contextField: Field,
) {

    /** The service's context: system_server's own. */
    fun context(service: Any): Context = contextField.get(service) as Context

    companion object {
        private const val SERVICE_CLASS = "com.android.server.wm.ActivityTaskManagerService"
        private const val APPLICATION_THREAD_CLASS = "android.app.IApplicationThread"
        private const val PROFILER_INFO_CLASS = "android.app.ProfilerInfo"

        @SuppressLint("PrivateApi") // system_server's own classes, which this module exists to hook.
        fun resolve(classLoader: ClassLoader): Result<ActivityTaskManagerTargets> = runCatching {
            resolve(
                service = Class.forName(SERVICE_CLASS, false, classLoader),
                applicationThread = Class.forName(APPLICATION_THREAD_CLASS, false, classLoader),
                profilerInfo = Class.forName(PROFILER_INFO_CLASS, false, classLoader),
                activityManager = ActivityManager::class.java,
            ).getOrThrow()
        }

        /** Resolves the targets from classes that are already loaded. Tests pass stand-ins with the same members. */
        internal fun resolve(
            service: Class<*>,
            applicationThread: Class<*>,
            profilerInfo: Class<*>,
            activityManager: Class<*>,
        ): Result<ActivityTaskManagerTargets> = runCatching {
            val string = String::class.java
            val int = Int::class.java
            ActivityTaskManagerTargets(
                startActivityAsUser = service.declaredMethod(
                    "startActivityAsUser",
                    int,
                    applicationThread,
                    string,
                    string,
                    Intent::class.java,
                    string,
                    IBinder::class.java,
                    string,
                    int,
                    int,
                    profilerInfo,
                    Bundle::class.java,
                    int,
                ),
                startSuccess = activityManager.declaredField("START_SUCCESS", int).getInt(null),
                contextField = service.declaredField("mContext", Context::class.java),
            )
        }
    }
}
