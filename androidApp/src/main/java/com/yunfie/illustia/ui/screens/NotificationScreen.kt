@file:JvmName("AndroidNotificationScreen")

package com.yunfie.illustia.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.yunfie.illustia.IllustiaUiState
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.ui.screens.NotificationScreen as SharedNotificationScreen

@Composable
fun NotificationScreen(
    state: IllustiaUiState,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
) {
    val actions =
        remember(viewModel) {
            object : NotificationActions {
                override fun refreshNotifications() = viewModel.refreshNotifications()

                override fun loadMoreNotifications() = viewModel.loadMoreNotifications()

                override fun expandNotification(id: Long) = viewModel.expandNotification(id)

                override fun openNotificationTarget(targetUrl: String?) = viewModel.openNotificationTarget(targetUrl)
            }
        }
    SharedNotificationScreen(
        state = state,
        actions = actions,
        onBack = onBack,
    )
}
