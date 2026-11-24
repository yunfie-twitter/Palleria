package com.yunfie.illustia

import androidx.test.core.app.ApplicationProvider
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.setResourceReaderAndroidContext
import org.junit.rules.ExternalResource

/**
 * Compose Multiplatform のリソース解決に Context を与えるテストルール。
 *
 * stringResource は Android では DefaultAndroidResourceReader を通り、AssetManager から
 * composeResources 配下の cvr を読む。Robolectric では Context が未設定だと
 * "Android context is not initialized" で失敗するため、テスト開始時に Application Context を注入する。
 */
class ComposeResourcesRule : ExternalResource() {
    @OptIn(ExperimentalResourceApi::class)
    override fun before() {
        setResourceReaderAndroidContext(ApplicationProvider.getApplicationContext())
    }
}
