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
            // Prerelease comparisons
            AppUpdaterRepository.compareVersions("6.1.0-beta.10", "6.1.0-beta.9") shouldBe 1
            AppUpdaterRepository.compareVersions("6.1.0-beta.9", "6.1.0-beta.10") shouldBe -1
            AppUpdaterRepository.compareVersions("6.1.0", "6.1.0-beta.10") shouldBe 1
            AppUpdaterRepository.compareVersions("6.1.0-beta.10", "6.1.0") shouldBe -1
            AppUpdaterRepository.compareVersions("6.2.0-beta.1", "6.1.0") shouldBe 1
            AppUpdaterRepository.compareVersions("6.1.0-rc.1", "6.1.0-beta.2") shouldBe 1
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

        "extracts PackageInstaller session ID correctly from various outputs" {
            AppUpdaterRepository.extractSessionId("Success: created install session [12345678]") shouldBe 12345678
            AppUpdaterRepository.extractSessionId("Success: created install session 987654") shouldBe 987654
            AppUpdaterRepository.extractSessionId("created install session [42]\n") shouldBe 42
            AppUpdaterRepository.extractSessionId("Failure [INSTALL_FAILED_INVALID_APK]") shouldBe null
            AppUpdaterRepository.extractSessionId("Error: unknown command") shouldBe null
            AppUpdaterRepository.extractSessionId("") shouldBe null
        }

        "extracts SHA-256 checksum from asset digest and release notes correctly" {
            val validDigest = "a80847fcfa0743e0c156b6db368c48db2f80592ff68d0aca0ab907c5e4b90bd3"
            val assetWithDigest =
                kotlinx.serialization.json.buildJsonObject {
                    put("name", kotlinx.serialization.json.JsonPrimitive("Palleria-arm64-v8a.apk"))
                    put("digest", kotlinx.serialization.json.JsonPrimitive("sha256:$validDigest"))
                }
            AppUpdaterRepository.extractSha256(assetWithDigest) shouldBe validDigest

            val assetWithoutDigest =
                kotlinx.serialization.json.buildJsonObject {
                    put("name", kotlinx.serialization.json.JsonPrimitive("Palleria-arm64-v8a.apk"))
                }
            val body = "Checksums:\n$validDigest  Palleria-arm64-v8a.apk"
            AppUpdaterRepository.extractSha256(assetWithoutDigest, releaseBody = body, apkName = "Palleria-arm64-v8a.apk") shouldBe
                validDigest

            val bodyLabeled = "Release info\nSHA256: $validDigest\nEnjoy!"
            AppUpdaterRepository.extractSha256(assetWithoutDigest, releaseBody = bodyLabeled) shouldBe validDigest

            AppUpdaterRepository.extractSha256(assetWithoutDigest, releaseBody = "No checksums here") shouldBe null
        }

        "selects best matching APK asset based on device ABI" {
            val arm64Asset =
                kotlinx.serialization.json.buildJsonObject {
                    put("name", kotlinx.serialization.json.JsonPrimitive("Palleria-v6.0.0-release-arm64-v8a.apk"))
                }
            val armV7Asset =
                kotlinx.serialization.json.buildJsonObject {
                    put("name", kotlinx.serialization.json.JsonPrimitive("Palleria-v6.0.0-release-armeabi-v7a.apk"))
                }
            val universalAsset =
                kotlinx.serialization.json.buildJsonObject {
                    put("name", kotlinx.serialization.json.JsonPrimitive("Palleria-v6.0.0-release-universal.apk"))
                }

            val list = listOf(armV7Asset, universalAsset, arm64Asset)

            AppUpdaterRepository.selectBestApkAsset(list, arrayOf("arm64-v8a")) shouldBe arm64Asset
            AppUpdaterRepository.selectBestApkAsset(list, arrayOf("x86_64", "universal")) shouldBe universalAsset
            AppUpdaterRepository.selectBestApkAsset(list, arrayOf("mips")) shouldBe universalAsset
        }
    })
