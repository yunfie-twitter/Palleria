package com.yunfie.illustia.ui.screens

/** 通知画面が ViewModel に要求する操作。 */
interface NotificationActions {
    fun refreshNotifications()

    fun loadMoreNotifications()

    fun expandNotification(id: Long)

    fun openNotificationTarget(targetUrl: String?)
}
