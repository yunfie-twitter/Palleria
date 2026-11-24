package com.yunfie.illustia.settings

import androidx.compose.runtime.Composable
import com.yunfie.illustia.R
import com.yunfie.illustia.models.NetworkMode
import com.yunfie.illustia.resources.Res
import com.yunfie.illustia.resources.pixiv_network_mode_compat
import com.yunfie.illustia.resources.pixiv_network_mode_ech
import com.yunfie.illustia.resources.pixiv_network_mode_standard
import com.yunfie.illustia.resources.setup_network_mode_compat_label
import com.yunfie.illustia.resources.setup_network_mode_ech_label
import com.yunfie.illustia.resources.setup_network_mode_standard_label
import org.jetbrains.compose.resources.stringResource

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
            stringResource(Res.string.pixiv_network_mode_ech) + " - " + stringResource(Res.string.setup_network_mode_ech_label)
        }

        NetworkMode.Compat -> {
            stringResource(Res.string.pixiv_network_mode_compat) + " - " +
                stringResource(Res.string.setup_network_mode_compat_label)
        }

        NetworkMode.Standard -> {
            stringResource(Res.string.pixiv_network_mode_standard) + " - " +
                stringResource(Res.string.setup_network_mode_standard_label)
        }
    }
