package com.yunfie.illustia.ui.screens

/** Tracks positions in sanitized speech text, rather than offsets in the original novel. */
internal class NovelTtsProgress {
    private var offset = 0
    private var utteranceOffset = 0
    private var utteranceLength = 0
    private var sequence = 0L
    private var activeId: String? = null

    fun begin(text: String): Pair<String, String> {
        utteranceOffset = offset.coerceIn(0, text.length)
        val remaining = text.substring(utteranceOffset)
        utteranceLength = remaining.length
        val id = "novel_${++sequence}"
        activeId = id
        return id to remaining
    }

    fun isActive(id: String?): Boolean = id != null && id == activeId

    fun onRangeStart(
        id: String?,
        start: Int,
        end: Int,
    ) {
        if (!isActive(id)) return
        if (start >= 0 && start < end && end <= utteranceLength) {
            // Repeat the currently spoken range instead of skipping its unfinished words.
            offset = maxOf(offset, utteranceOffset + start)
        }
    }

    fun pause() {
        activeId = null
    }

    fun reset() {
        pause()
        offset = 0
    }
}
