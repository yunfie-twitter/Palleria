package com.yunfie.illustia

import android.content.Context
import com.yunfie.illustia.data.IllustiaRepository
import com.yunfie.illustia.platform.AndroidAppServices
import com.yunfie.illustia.platform.AppServices

/**
 * UI が具体リポジトリを必要とする場合の取り出し口。
 *
 * ViewModel 本体は [com.yunfie.illustia.data.IllustiaRepositoryApi] しか知らないため、
 * 具体型が要る UI 側（ブックマーク・検索などのストア生成）でここを使う。
 */
fun Context.uiRepository(): IllustiaRepository = (applicationContext as IllustiaApplication).repository

/**
 * UI がストレージ容量読み取りなどのプラットフォーム操作を必要とする場合の取り出し口。
 */
fun Context.uiAppServices(): AppServices = AndroidAppServices(applicationContext)
