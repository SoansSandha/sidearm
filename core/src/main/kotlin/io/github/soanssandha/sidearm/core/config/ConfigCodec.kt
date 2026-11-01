package io.github.soanssandha.sidearm.core.config

/**
 * Converts between stored preference values and [Config]. Stored values are untrusted, so anything missing, of the
 * wrong type or malformed decodes to "off" or "no target".
 */
object ConfigCodec {
    /** Builds a [Config] from stored values, such as `SharedPreferences.getAll()`. */
    fun decode(stored: Map<String, *>): Config = Config(
        enabled = stored[ConfigKeys.ENABLED] as? Boolean ?: false,
        target = (stored[ConfigKeys.TARGET_COMPONENT] as? String)?.let(TargetComponent::parse),
        dryRun = stored[ConfigKeys.DRY_RUN] as? Boolean ?: false,
    )

    /** The values to store for [config]. A null value means the key should be removed. */
    fun encode(config: Config): Map<String, Any?> = mapOf(
        ConfigKeys.ENABLED to config.enabled,
        ConfigKeys.TARGET_COMPONENT to config.target?.flatten(),
        ConfigKeys.DRY_RUN to config.dryRun,
    )
}
