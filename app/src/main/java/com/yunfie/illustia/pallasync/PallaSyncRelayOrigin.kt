package com.yunfie.illustia.pallasync

/**
 * Resolves which relay origin the app should actually use.
 *
 * The configured URL is a *default*, not a fixed constant: the owning relay can move, and a
 * deployment that answers the health endpoint today may be an unreachable origin tomorrow (the
 * previous default, https://api.yunfi.f5.si, currently answers HTTP 530). Keeping the decision in
 * one place means the settings screen, the coordinator and the trust gate cannot disagree.
 */
internal object PallaSyncRelayOrigin {
    /** §10.1: the origin the app ships pointing at. */
    const val DEFAULT_ORIGIN = "https://sync.yunfi.f5.si"

    /**
     * Origins still known to represent this deployment.
     *
     * Ordered after [DEFAULT_ORIGIN], used only when the primary fails its health check. The old
     * `api.` host is intentionally absent: it has answered HTTP 530 since the migration, so
     * falling back to it would only delay the failure the user already sees.
     */
    val KNOWN_ORIGINS: List<String> = listOf(DEFAULT_ORIGIN)

    /**
     * Previous shipped defaults. A stored setting equal to one of these was never an explicit
     * user choice, so it is migrated to [DEFAULT_ORIGIN]. Any other stored value is left alone.
     */
    val LEGACY_DEFAULTS: Set<String> = setOf("https://api.yunfi.f5.si")

    /**
     * Returns the origin to use for a stored setting.
     *
     * A stored value that is still a legacy default is replaced; a value the user chose is kept
     * verbatim so an operator running their own relay is never silently redirected.
     */
    fun resolve(stored: String?): String {
        val trimmed = stored?.trim()?.trimEnd('/').orEmpty()
        if (trimmed.isEmpty()) return DEFAULT_ORIGIN
        if (trimmed.lowercase() in LEGACY_DEFAULTS) return DEFAULT_ORIGIN
        return trimmed
    }

    /**
     * Candidate origins to try in order: the resolved setting first, then any remaining known
     * origins as fallbacks.
     */
    fun candidates(stored: String?): List<String> {
        val primary = resolve(stored)
        return listOf(primary) + KNOWN_ORIGINS.filter { it != primary }
    }
}
