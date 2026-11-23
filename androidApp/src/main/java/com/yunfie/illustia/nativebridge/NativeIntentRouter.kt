package com.yunfie.illustia.nativebridge

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import com.yunfie.illustia.platform.IncomingLink

/**
 * `android.content.Intent` を [NativeIntentEvent] / [IncomingLink] へ変換する Android 側の糊。
 *
 * ルーティング本体は `shared` の [NativeIntentRouter] にある。
 * `NativeIntentRouter` の関数数上限を超えないよう、object の外に置いている。
 */
@Suppress("ReturnCount")
fun NativeIntentRouter.parse(intent: Intent?): NativeIntentEvent? {
    if (intent == null) return null
    if (intent.action == Intent.ACTION_PROCESS_TEXT) {
        return normalizeProcessText(intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT))
            ?.let(NativeIntentEvent::Text)
    }
    if (intent.action == Intent.ACTION_SEND) {
        val imageUri = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        if (imageUri != null) return NativeIntentEvent.Image(imageUri.toString())
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isNotBlank()) {
            parseText(text)?.let { return it }
            return NativeIntentEvent.Text(text)
        }
    }
    if (intent.action == Intent.ACTION_VIEW) {
        parseText(intent.dataString)?.let { return it }
        intent
            .getStringExtra(NativeIntentRouter.EXTRA_HANDOFF_URI)
            ?.takeIf(String::isNotBlank)
            ?.let(::parseText)
            ?.let { return it }
        intent.getLongExtra("iid", 0L).takeIf { it > 0L }?.let {
            return NativeIntentEvent.Artwork(it)
        }
    }
    return null
}

/**
 * `Intent` を、ViewModel 層が受け取る Android 非依存の [IncomingLink] に変換する。
 */
fun Intent.toIncomingLink(): IncomingLink {
    val action =
        when (this.action) {
            Intent.ACTION_VIEW -> IncomingLink.Action.VIEW
            Intent.ACTION_SEND -> IncomingLink.Action.SEND
            Intent.ACTION_PROCESS_TEXT -> IncomingLink.Action.PROCESS_TEXT
            else -> null
        }
    val imageUri =
        if (action == IncomingLink.Action.SEND) {
            IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)?.toString()
        } else {
            null
        }
    return IncomingLink(
        launchedFromHistory = flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0,
        action = action,
        dataString = dataString,
        handoffUri = getStringExtra(NativeIntentRouter.EXTRA_HANDOFF_URI),
        sharedText = getStringExtra(Intent.EXTRA_TEXT),
        processText = getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT),
        sharedImageUri = imageUri,
        illustId = getLongExtra("iid", 0L),
    )
}
