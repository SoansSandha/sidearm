package io.github.soanssandha.sidearm.hook.effect.fixture

import io.github.soanssandha.sidearm.hook.effect.HookLog

/** Keeps every line; warnings start with `W: `. */
internal class RecordingLog : HookLog {
    val lines = mutableListOf<String>()

    override fun info(message: String) {
        lines += message
    }

    override fun warn(message: String, error: Throwable?) {
        lines += "W: $message"
    }
}
