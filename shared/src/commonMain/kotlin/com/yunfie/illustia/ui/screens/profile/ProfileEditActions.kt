package com.yunfie.illustia.ui.screens.profile

import com.yunfie.illustia.models.pixiv.AccountEditResult
import com.yunfie.illustia.models.pixiv.UserProfileEdit

/**
 * プロフィール編集画面が ViewModel に要求する操作。
 */
interface ProfileEditActions {
    suspend fun updateUserProfile(profile: UserProfileEdit): AccountEditResult
}
