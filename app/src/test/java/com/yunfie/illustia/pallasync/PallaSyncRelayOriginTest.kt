package com.yunfie.illustia.pallasync

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PallaSyncRelayOriginTest :
    StringSpec({
        "the shipped default is the live relay, not the unreachable legacy host" {
            PallaSyncRelayOrigin.DEFAULT_ORIGIN shouldBe "https://sync.yunfi.f5.si"
            (PallaSyncRelayOrigin.DEFAULT_ORIGIN in PallaSyncRelayOrigin.LEGACY_DEFAULTS) shouldBe false
        }

        "an empty or missing setting resolves to the default" {
            PallaSyncRelayOrigin.resolve(null) shouldBe PallaSyncRelayOrigin.DEFAULT_ORIGIN
            PallaSyncRelayOrigin.resolve("") shouldBe PallaSyncRelayOrigin.DEFAULT_ORIGIN
            PallaSyncRelayOrigin.resolve("   ") shouldBe PallaSyncRelayOrigin.DEFAULT_ORIGIN
        }

        /**
         * The previous release persisted the old host. Those users never chose it, so keeping it
         * would leave them pointed at an origin that answers HTTP 530.
         */
        "a stored legacy default migrates to the current default" {
            PallaSyncRelayOrigin.resolve("https://api.yunfi.f5.si") shouldBe PallaSyncRelayOrigin.DEFAULT_ORIGIN
            PallaSyncRelayOrigin.resolve("https://api.yunfi.f5.si/") shouldBe PallaSyncRelayOrigin.DEFAULT_ORIGIN
            PallaSyncRelayOrigin.resolve("HTTPS://API.YUNFI.F5.SI") shouldBe PallaSyncRelayOrigin.DEFAULT_ORIGIN
        }

        /** An operator running their own relay must never be redirected without asking. */
        "an explicitly chosen origin is preserved" {
            val custom = "https://relay.example.com"
            PallaSyncRelayOrigin.resolve(custom) shouldBe custom
            PallaSyncRelayOrigin.resolve("$custom/") shouldBe custom
            // A non-default port is part of the origin and must survive normalization.
            PallaSyncRelayOrigin.resolve("https://relay.example.com:8443") shouldBe "https://relay.example.com:8443"
        }

        "candidates try the resolved origin first and never repeat it" {
            val candidates = PallaSyncRelayOrigin.candidates("https://relay.example.com")
            candidates.first() shouldBe "https://relay.example.com"
            candidates.toSet().size shouldBe candidates.size

            // The legacy host is not offered as a fallback: it is known to be unreachable.
            candidates.forEach { (it in PallaSyncRelayOrigin.LEGACY_DEFAULTS) shouldBe false }
        }

        "the default origin is a usable candidate on its own" {
            PallaSyncRelayOrigin.candidates(null) shouldBe listOf(PallaSyncRelayOrigin.DEFAULT_ORIGIN)
        }
    })
