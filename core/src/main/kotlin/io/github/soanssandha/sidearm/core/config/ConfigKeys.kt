package io.github.soanssandha.sidearm.core.config

/**
 * Names stored in Vector's remote preferences. These are the only copies of them: the app writes and the hook reads
 * through [ConfigCodec]. Renaming one resets that setting on every existing install.
 */
object ConfigKeys {
    /** The remote preferences group. */
    const val GROUP = "settings"

    const val ENABLED = "enabled"

    /** A [TargetComponent.flatten] string. */
    const val TARGET_COMPONENT = "target_component"

    /** See [Config.dryRun]. */
    const val DRY_RUN = "dry_run"
}
