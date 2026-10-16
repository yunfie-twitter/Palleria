package com.yunfie.illustia.pallasync

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.Test

class PallaSyncInitialSourceChoiceTest {
    @Test
    fun `all protocol source kinds require and accept explicit selection`() {
        listOf("new_chain_local_state", "relay_full_replay", "authenticated_export", "trusted_snapshot").forEach { source ->
            PallaSyncInitialSourceChoice.requireExplicit(source, explicitlyConfirmed = true)
        }
    }

    @Test
    fun `dismissed and unknown source choices fail closed`() {
        listOf(
            "new_chain_local_state" to false,
            "relay_full_replay" to false,
            "authenticated_export" to false,
            "trusted_snapshot" to false,
            "" to true,
            "relay_latest_head" to true,
        ).forEach { (source, confirmed) ->
            shouldThrow<IllegalStateException> {
                PallaSyncInitialSourceChoice.requireExplicit(source, confirmed)
            }.message shouldBe "C_BOOTSTRAP_TRUST_REQUIRED"
        }
    }
}
