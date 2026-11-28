package com.yunfie.illustia.ui.screens.novel.reader

/** リーダー画面が TTS 再生制御に必要とする操作だけを表す境界。 */
interface NovelReaderTts {
    val isPlaying: Boolean
    val novelId: Long?
    val totalParagraphs: Int
    val currentParagraphIndex: Int
    val availableVoices: List<String>

    fun pause()

    fun resume()

    fun startReading(
        rawText: String,
        startIndex: Int,
        novelTitle: String,
        authorName: String,
        novelId: Long,
        rate: Float,
        pitchLevel: Float,
        voice: String,
        skipSymbols: Boolean,
        customDictionary: Map<String, String>,
    )

    /** 読み上げ速度を変更する。 */
    fun setRate(rate: Float)

    /** ピッチを変更する。 */
    fun setPitchLevel(pitchValue: Float)

    /** 音声を選択する。 */
    fun setVoiceSelection(name: String)
}
