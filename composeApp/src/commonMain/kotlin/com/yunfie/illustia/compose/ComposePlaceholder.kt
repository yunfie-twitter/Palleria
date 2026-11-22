package com.yunfie.illustia.compose

import androidx.compose.runtime.Composable
import illustia.composeapp.generated.resources.Res
import illustia.composeapp.generated.resources.feature_flags_summary
import illustia.composeapp.generated.resources.feature_flags_title
import org.jetbrains.compose.resources.stringResource

internal const val COMPOSE_MODULE_NAME = "illustia-compose"

/**
 * Compose Resources の疎通確認用。
 *
 * `values/` をフォールバックに、ロケール別リソースがあればそちらが使われる。
 */
@Composable
internal fun featureFlagsStrings(): Pair<String, String> =
    stringResource(Res.string.feature_flags_title) to
        stringResource(Res.string.feature_flags_summary)
