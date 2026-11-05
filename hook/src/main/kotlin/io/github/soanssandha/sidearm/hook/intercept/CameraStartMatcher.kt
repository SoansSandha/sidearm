package io.github.soanssandha.sidearm.hook.intercept

import io.github.libxposed.api.XposedInterface

/** Recognises SystemUI's camera start among the `startActivityAsUser` calls. */
internal fun interface CameraStartMatcher {
    fun matches(chain: XposedInterface.Chain): Boolean
}
