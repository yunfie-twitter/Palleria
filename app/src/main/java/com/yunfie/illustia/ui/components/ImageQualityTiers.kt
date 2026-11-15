package com.yunfie.illustia.ui.components

import com.yunfie.illustia.models.Illust

internal const val QUALITY_LOW = "low"
internal const val QUALITY_MEDIUM = "medium"
internal const val QUALITY_HIGH = "high"

/**
 * 画質 tier に対応する画像 URL の一覧を解決する。
 *
 * low は medium、medium は large、high は original を使い、
 * 各 tier が空のときは一段下の tier にフォールバックする。
 */
internal fun resolveQualityImageUrls(
    illust: Illust,
    quality: String,
): List<String> =
    when (quality) {
        QUALITY_LOW -> {
            illust.mediumImagePages.ifEmpty {
                listOfNotNull(firstNonBlank(illust.mediumImageUrl, illust.squareImageUrl, illust.imageUrl))
            }
        }

        QUALITY_MEDIUM -> {
            illust.imagePages.ifEmpty {
                listOfNotNull(firstNonBlank(illust.imageUrl, illust.mediumImageUrl, illust.squareImageUrl))
            }
        }

        else -> {
            illust.originalImagePages.ifEmpty {
                illust.imagePages.ifEmpty {
                    listOfNotNull(
                        firstNonBlank(
                            illust.imageUrl,
                            illust.mediumImageUrl,
                            illust.squareImageUrl,
                            illust.originalImageUrl,
                        ),
                    )
                }
            }
        }
    }

/**
 * 選択画質の一段下の tier を返す。low 自身には下地が無いため null。
 */
internal fun lowerQualityTier(quality: String): String? =
    when (quality) {
        QUALITY_HIGH -> QUALITY_MEDIUM
        QUALITY_MEDIUM -> QUALITY_LOW
        else -> null
    }

/**
 * 本画像を表示する前に下地として読む URL 一覧を解決する。
 * [progressiveQuality] が false のときは空 (= 従来動作)。
 *
 * 下地には tier の一段下ではなくサムネイル (square) を使う。Pixiv の medium は
 * `imagePages` と `mediumImagePages` の両方が同じ URL を指すことが多く、一段下では
 * 本画像と同じ URL になって下地が消えてしまうため。
 */
internal fun resolveProgressivePlaceholderUrls(
    illust: Illust,
    quality: String,
    progressiveQuality: Boolean,
): List<String> {
    // low より下は無い。ここで空を返すのは従来動作の維持のため。
    val tierHasUnderlay = lowerQualityTier(quality) != null
    val thumbnail = illust.thumbnailUrl
    return listOf(thumbnail)
        .takeIf { progressiveQuality && tierHasUnderlay }
        .orEmpty()
        .filter { it.isNotBlank() && it != illust.previewUrl }
}

private fun firstNonBlank(vararg candidates: String?): String? = candidates.firstOrNull { !it.isNullOrBlank() }
