package com.yunfie.illustia.pallasync

/** §11.6: an initial source is accepted only after an explicit foreground choice. */
internal object PallaSyncInitialSourceChoice {
    private val supportedSources =
        setOf(
            "new_chain_local_state",
            "relay_full_replay",
            "authenticated_export",
            "trusted_snapshot",
        )

    fun requireExplicit(
        source: String,
        explicitlyConfirmed: Boolean,
    ) {
        check(explicitlyConfirmed && source in supportedSources) { "C_BOOTSTRAP_TRUST_REQUIRED" }
    }
}
