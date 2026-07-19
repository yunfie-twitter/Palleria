package com.yunfie.illustia.wallpaper

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LiveWallpaperSupportTest : FunSpec({
    test("detects HyperOS on Xiaomi family devices") {
        isHyperOsDevice(
            manufacturer = "Xiaomi",
            brand = "Redmi",
            osVersionName = "OS1.0",
            incremental = "OS1.0.8.0",
            display = "UKQ1",
        ) shouldBe true
    }

    test("does not disable non-HyperOS or non-Xiaomi devices") {
        isHyperOsDevice(
            manufacturer = "Xiaomi",
            brand = "Redmi",
            osVersionName = "",
            incremental = "V14.0.1.0",
            display = "TKQ1",
        ) shouldBe false

        isHyperOsDevice(
            manufacturer = "Google",
            brand = "Pixel",
            osVersionName = "",
            incremental = "OS1.0",
            display = "HyperOS",
        ) shouldBe false
    }
})
