package com.yunfie.illustia.updater

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class AppUpdaterRepositoryTest :
    StringSpec({
        "compares semantic version strings correctly" {
            AppUpdaterRepository.compareVersions("5.5.2", "5.5.1") shouldBe 1
            AppUpdaterRepository.compareVersions("5.5.1", "5.5.2") shouldBe -1
            AppUpdaterRepository.compareVersions("5.5.2", "5.5.2") shouldBe 0
            AppUpdaterRepository.compareVersions("5.6.0", "5.5.9") shouldBe 1
            AppUpdaterRepository.compareVersions("6.0.0", "5.9.9") shouldBe 1
            AppUpdaterRepository.compareVersions("5.5.2.1", "5.5.2") shouldBe 1
            AppUpdaterRepository.compareVersions("5.5.2", "5.5.2.0") shouldBe 0
        }

        "parses UpdateInstallMethod values correctly" {
            UpdateInstallMethod.fromValue("standard_apk") shouldBe UpdateInstallMethod.STANDARD_APK
            UpdateInstallMethod.fromValue("shizuku") shouldBe UpdateInstallMethod.SHIZUKU
            UpdateInstallMethod.fromValue("unknown") shouldBe UpdateInstallMethod.STANDARD_APK
            UpdateInstallMethod.fromValue(null) shouldBe UpdateInstallMethod.STANDARD_APK
            UpdateInstallMethod.fromValue("") shouldBe UpdateInstallMethod.STANDARD_APK
            UpdateInstallMethod.SHIZUKU.value shouldBe "shizuku"
            UpdateInstallMethod.STANDARD_APK.value shouldBe "standard_apk"
        }
    })
