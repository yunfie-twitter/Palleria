package com.yunfie.illustia.platform

/**
 * ViewModel 層に入ってくる外部リンク要求の、Android 非依存な表現。
 *
 * 従来は `android.content.Intent` をそのまま `handleIncomingIntent` に渡していた。
 * `NativeIntentRouter.parse(Intent)` が実際に読んでいた値だけを、この型に写し取る。
 *
 * 画像共有（`ACTION_SEND` + `EXTRA_STREAM`）だけは `Uri` をそのまま運ぶ必要があり、
 * ここでは `imageUri` の文字列表現として保持する。
 */
data class IncomingLink(
    /** 履歴から再起動された Intent か（その場合は再処理しない）。 */
    val launchedFromHistory: Boolean = false,
    /** `Intent.action`。null なら判定不能。 */
    val action: Action? = null,
    /** `intent.dataString`。Pixiv OAuth コールバックや `ACTION_VIEW` の本文に使う。 */
    val dataString: String? = null,
    /** ハンドオフ用の追加 URI。 */
    val handoffUri: String? = null,
    /** `ACTION_SEND` の本文テキスト。 */
    val sharedText: String? = null,
    /** `ACTION_PROCESS_TEXT` の本文。 */
    val processText: CharSequence? = null,
    /** `ACTION_SEND` で共有された画像の URI（文字列表現）。 */
    val sharedImageUri: String? = null,
    /** `ACTION_VIEW` の `iid` 追加値。0 以下は無効。 */
    val illustId: Long = 0L,
) {
    enum class Action {
        VIEW,
        SEND,
        PROCESS_TEXT,
    }
}
