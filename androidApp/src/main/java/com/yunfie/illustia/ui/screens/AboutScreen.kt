package com.yunfie.illustia.ui.screens

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.updater.AppReleaseInfo
import org.json.JSONArray
import com.yunfie.illustia.ui.screens.AboutScreen as SharedAboutScreen

@Composable
fun AboutScreen(
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val updateState by viewModel.updateCheckState.collectAsStateWithLifecycle()
    val appVersion = remember(context) { context.readAppVersionName() }
    val licenses = remember(context) { context.readThirdPartyLicenses() }
    val actions =
        remember(viewModel) {
            object : AboutActions {
                override fun checkForUpdates(silent: Boolean) = viewModel.checkForUpdates(silent)

                override fun downloadUpdate(release: AppReleaseInfo) = viewModel.downloadUpdate(release)

                override fun cancelDownloadUpdate() = viewModel.cancelDownloadUpdate()

                override fun installUpdate(apkPath: String) = viewModel.installUpdate(apkPath)

                override fun appVersion(): String? = appVersion

                override fun readThirdPartyLicenses(): List<ThirdPartyLicenseItem> = licenses
            }
        }
    SharedAboutScreen(
        updateState = updateState,
        actions = actions,
        appIcon = painterResource(R.mipmap.ic_launcher_foreground),
        onBack = onBack,
    )
}

private fun Context.readAppVersionName(): String? = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()

private fun Context.readThirdPartyLicenses(): List<ThirdPartyLicenseItem> =
    runCatching {
        assets.open("licenses.json").bufferedReader().use { reader ->
            val jsonArray = JSONArray(reader.readText())
            List(jsonArray.length()) { index ->
                val obj = jsonArray.getJSONObject(index)
                ThirdPartyLicenseItem(
                    name = obj.optString("name"),
                    category = obj.optString("category"),
                    version = obj.optString("version"),
                    author = obj.optString("author"),
                    license = obj.optString("license"),
                    url = obj.optString("url"),
                )
            }
        }
    }.getOrElse { emptyList() }
