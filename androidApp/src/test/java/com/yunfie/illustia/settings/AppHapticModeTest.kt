package com.yunfie.illustia.settings

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AppHapticModeTest :
    StringSpec({
        "off mode is preserved on supported devices" {
            effectiveAppHapticMode("off", supported = true) shouldBe AppHapticMode.Off
        }

        "unsupported devices always use off mode" {
            effectiveAppHapticMode("rich", supported = false) shouldBe AppHapticMode.Off
            effectiveAppHapticMode("clear", supported = false) shouldBe AppHapticMode.Off
        }

        "unknown persisted values keep the rich compatibility default" {
            effectiveAppHapticMode("unknown", supported = true) shouldBe AppHapticMode.Rich
        }
    })
