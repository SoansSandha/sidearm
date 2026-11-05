package io.github.soanssandha.sidearm.hook.target.fixture

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder

/**
 * Stand-ins for the framework classes `ActivityTaskManagerTargets` reads. [Android17] copies the members that
 * docs/hook-targets.md lists for Android 17; the others each break one of them, the way an OTA might.
 */
@Suppress("unused", "UNUSED_PARAMETER")
object ActivityTaskManagerFixtures {

    /** `android.app.IApplicationThread`. */
    interface ApplicationThread

    /** `android.app.ProfilerInfo`. */
    class ProfilerInfo

    /** `android.app.ActivityManager`, for its hidden `START_SUCCESS`. */
    class ActivityManager {
        companion object {
            const val START_SUCCESS = 0
        }
    }

    /** `android.app.ActivityManager` without `START_SUCCESS`. */
    class ActivityManagerWithoutStartSuccess

    /** `com.android.server.wm.ActivityTaskManagerService` as it is on Android 17. */
    class Android17 {
        private val mContext: Context? = null

        fun startActivityAsUser(
            caller: ApplicationThread?,
            callingPackage: String?,
            callingFeatureId: String?,
            intent: Intent?,
            resolvedType: String?,
            resultTo: IBinder?,
            resultWho: String?,
            requestCode: Int,
            startFlags: Int,
            profilerInfo: ProfilerInfo?,
            bOptions: Bundle?,
            userId: Int,
        ): Int = 0
    }

    /** The service with the binder entry gone, or reshaped as an OTA might (here, a new trailing parameter). */
    class WithoutStartActivityAsUser {
        private val mContext: Context? = null

        fun startActivityAsUser(
            caller: ApplicationThread?,
            callingPackage: String?,
            callingFeatureId: String?,
            intent: Intent?,
            resolvedType: String?,
            resultTo: IBinder?,
            resultWho: String?,
            requestCode: Int,
            startFlags: Int,
            profilerInfo: ProfilerInfo?,
            bOptions: Bundle?,
            userId: Int,
            validateIncomingUser: Boolean,
        ): Int = 0
    }

    /** The service with `startActivityAsUser` returning nothing. */
    class StartReturnsVoid {
        private val mContext: Context? = null

        fun startActivityAsUser(
            caller: ApplicationThread?,
            callingPackage: String?,
            callingFeatureId: String?,
            intent: Intent?,
            resolvedType: String?,
            resultTo: IBinder?,
            resultWho: String?,
            requestCode: Int,
            startFlags: Int,
            profilerInfo: ProfilerInfo?,
            bOptions: Bundle?,
            userId: Int,
        ) = Unit
    }

    /** The service without its `mContext` field. */
    class WithoutContext {
        fun startActivityAsUser(
            caller: ApplicationThread?,
            callingPackage: String?,
            callingFeatureId: String?,
            intent: Intent?,
            resolvedType: String?,
            resultTo: IBinder?,
            resultWho: String?,
            requestCode: Int,
            startFlags: Int,
            profilerInfo: ProfilerInfo?,
            bOptions: Bundle?,
            userId: Int,
        ): Int = 0
    }
}
