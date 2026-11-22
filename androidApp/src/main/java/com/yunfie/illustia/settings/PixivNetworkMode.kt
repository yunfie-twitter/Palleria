package com.yunfie.illustia.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.yunfie.illustia.R
import com.yunfie.illustia.models.NetworkMode

fun pixivNetworkModeOptions(): List<String> =
    listOf(
        NetworkMode.Ech.code,
        NetworkMode.Compat.code,
        NetworkMode.Standard.code,
    )

@Composable
fun pixivNetworkModeLabel(value: String): String =
    when (NetworkMode.fromCode(value)) {
        NetworkMode.Ech -> {
            stringResource(R.string.pixiv_network_mode_ech) + " - " + stringResource(R.string.setup_network_mode_ech_label)
        }

        NetworkMode.Compat -> {
            stringResource(R.string.pixiv_network_mode_compat) + " - " +
                stringResource(R.string.setup_network_mode_compat_label)
        }

        NetworkMode.Standard -> {
            stringResource(R.string.pixiv_network_mode_standard) + " - " +
                stringResource(R.string.setup_network_mode_standard_label)
        }
    }
