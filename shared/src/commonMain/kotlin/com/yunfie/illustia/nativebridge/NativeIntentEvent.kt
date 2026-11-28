package com.yunfie.illustia.nativebridge

/**
 * 外部から渡された共有・リンクの意図。
 *
 * `Intent` の詳細は `androidApp` 側に残し、ViewModel 層はこの型だけを受け取る。
 */
sealed interface NativeIntentEvent {
    data class Artwork(
        val id: Long,
    ) : NativeIntentEvent

    data class User(
        val id: Long,
    ) : NativeIntentEvent

    data class Tag(
        val tag: String,
    ) : NativeIntentEvent

    data class Text(
        val value: String,
    ) : NativeIntentEvent

    data class Image(
        val uri: String,
    ) : NativeIntentEvent
}
