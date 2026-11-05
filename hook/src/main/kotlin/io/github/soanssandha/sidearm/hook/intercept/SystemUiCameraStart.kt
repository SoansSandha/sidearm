package io.github.soanssandha.sidearm.hook.intercept

import android.content.Intent
import android.provider.MediaStore
import io.github.libxposed.api.XposedInterface
import io.github.soanssandha.sidearm.hook.effect.CallerIdentity
import io.github.soanssandha.sidearm.hook.target.ActivityTaskManagerTargets

/**
 * Matches the call SystemUI makes to start the camera after a power double-press on an unlocked phone: no caller
 * thread or result target, SystemUI as the calling package and the calling uid, and the insecure camera action. On a
 * locked phone SystemUI starts the secure camera instead, which doesn't match.
 *
 * The package and uid are both checked here, because a held start returns before the framework checks that they
 * agree. SystemUI's uid is looked up as system_server, not as whichever app is calling, and kept once found: the
 * lookup can run during any app's call, and that app might not be allowed to see SystemUI.
 */
internal class SystemUiCameraStart(
    private val targets: ActivityTaskManagerTargets,
    private val identity: CallerIdentity,
) : CameraStartMatcher {

    @Volatile
    private var systemUiUid = UNKNOWN_UID

    override fun matches(chain: XposedInterface.Chain): Boolean {
        if (chain.getArg(ARG_CALLER) != null || chain.getArg(ARG_RESULT_TO) != null) return false
        if (chain.getArg(ARG_CALLING_PACKAGE) != SYSTEM_UI_PACKAGE) return false
        if ((chain.getArg(ARG_INTENT) as? Intent)?.action != MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA) return false
        val uid = systemUiUid(checkNotNull(chain.thisObject))
        return uid != UNKNOWN_UID && identity.callingUid() == uid
    }

    private fun systemUiUid(service: Any): Int {
        if (systemUiUid != UNKNOWN_UID) return systemUiUid
        val found = identity.runAsSelf {
            targets.context(service).packageManager.getPackageUid(SYSTEM_UI_PACKAGE, 0)
        }
        if (found >= 0) systemUiUid = found
        return found.coerceAtLeast(UNKNOWN_UID)
    }

    private companion object {
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        const val UNKNOWN_UID = -1
        const val ARG_CALLER = 0
        const val ARG_CALLING_PACKAGE = 1
        const val ARG_INTENT = 3
        const val ARG_RESULT_TO = 5
    }
}
