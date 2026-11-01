package io.github.soanssandha.sidearm.hook.intercept

import io.github.soanssandha.sidearm.hook.target.PhoneWindowManagerTargets

/**
 * Whether the phone was locked when the current press sequence began. It's read at press 1, a key down that comes
 * before stock starts turning the screen off. By press 2 the screen-off has already made the keyguard report itself
 * showing, even on a phone that was unlocked.
 *
 * Both hookers run on the input thread; the field is volatile only to be safe.
 */
internal class SequenceStart(private val windowManager: PhoneWindowManagerTargets) {

    /** True until a first press says otherwise, and whenever the keyguard can't be read: holding is the usual case. */
    @Volatile
    var startedLocked: Boolean = true
        private set

    /** Records the keyguard state at a press stock counted as the first of a sequence. Cheap: it's a cached flag. */
    fun onFirstPress() {
        startedLocked = windowManager.policy()?.let(windowManager::isKeyguardShowing) ?: true
    }
}
