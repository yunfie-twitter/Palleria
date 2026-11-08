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
 */
internal fun resolveProgressivePlaceholderUrls(
    illust: Illust,
    quality: String,
    progressiveQuality: Boolean,
): List<String> =
    lowerQualityTier(quality)
        ?.takeIf { progressiveQuality }
        ?.let { resolveQualityImageUrls(illust, it) }
        .orEmpty()

private fun firstNonBlank(vararg candidates: String?): String? = candidates.firstOrNull { !it.isNullOrBlank() }
