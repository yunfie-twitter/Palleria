package com.yunfie.illustia

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppShortcutDestination(
    val action: String,
) {
    Search("com.yunfie.illustia.action.SHORTCUT_SEARCH"),
    Ranking("com.yunfie.illustia.action.SHORTCUT_RANKING"),
    Bookmarks("com.yunfie.illustia.action.SHORTCUT_BOOKMARKS"),
    ViewHistory("com.yunfie.illustia.action.SHORTCUT_VIEW_HISTORY"),
}

object AppShortcutRouter {
    private val _pending = MutableStateFlow<AppShortcutDestination?>(null)
    val pending: StateFlow<AppShortcutDestination?> = _pending.asStateFlow()

    fun accept(intent: Intent?): Boolean {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) {
            return false
        }
        return acceptAction(intent.action)
    }

    fun acceptAction(action: String?): Boolean {
        val destination =
            AppShortcutDestination.entries
                .firstOrNull { it.action == action }
                ?: return false
        _pending.value = destination
        return true
    }

    fun trigger(destination: AppShortcutDestination) {
        _pending.value = destination
    }

    fun consume(destination: AppShortcutDestination) {
        _pending.compareAndSet(destination, null)
    }
}
