package com.yunfie.illustia.compose

import androidx.compose.runtime.Composable
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.feature_flags_summary
import com.yunfie.illustia.resources.feature_flags_title
import org.jetbrains.compose.resources.stringResource

internal const val COMPOSE_MODULE_NAME = "illustia-compose"

/**
 * Compose Resources の疎通確認用。
 *
 * `values/` をフォールバックに、ロケール別リソースがあればそちらが使われる。
 */
@Composable
fun featureFlagsStrings(): Pair<String, String> =
    stringResource(Res.string.feature_flags_title) to
        stringResource(Res.string.feature_flags_summary)
