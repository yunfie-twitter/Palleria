package com.yunfie.illustia.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.yunfie.illustia.IllustiaViewModel
import com.yunfie.illustia.R
import com.yunfie.illustia.models.UserProfile
import com.yunfie.illustia.models.pixiv.UserProfileEdit
import com.yunfie.illustia.ui.components.DividerLine
import com.yunfie.illustia.ui.components.ElevatedPanel
import com.yunfie.illustia.ui.components.HeaderIcon
import com.yunfie.illustia.ui.components.LoadingIndicator
import com.yunfie.illustia.ui.components.PredictiveBackGestureHandler
import com.yunfie.illustia.ui.components.Section
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UserProfileEditScreen(
    user: UserProfile,
    viewModel: IllustiaViewModel,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit = onBack,
    modifier: Modifier = Modifier,
) {
    PredictiveBackGestureHandler(onBack = onBack)
    val scrollBehavior = MiuixScrollBehavior()
    val scope = rememberCoroutineScope()

    var userName by remember(user.id) { mutableStateOf(user.name) }
    var comment by remember(user.id) { mutableStateOf(user.comment) }
    var webpage by remember(user.id) { mutableStateOf("") }
    var twitter by remember(user.id) { mutableStateOf("") }
    var gender by remember(user.id) { mutableStateOf("unknown") }
    var birthday by remember(user.id) { mutableStateOf("") }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    @Suppress("TooGenericExceptionCaught")
    fun save() {
        if (isSaving) return
        val trimmedName = userName.trim()
        if (trimmedName.isEmpty()) return

        isSaving = true
        errorMessage = null
        successMessage = null
        scope.launch {
            try {
                val profileEdit =
                    UserProfileEdit(
                        userName = trimmedName,
                        comment = comment.trim(),
                        webpage = webpage.trim(),
                        twitter = twitter.trim(),
                        gender = gender,
                        birthday = birthday.trim(),
                        address = 0,
                        job = 0,
                    )
                val result = viewModel.updateUserProfile(profileEdit)
                isSaving = false
                if (result.isSucceeded) {
                    successMessage = result.message.ifBlank { null }
                    onSaveSuccess()
                } else {
                    errorMessage = result.message.ifBlank { result.validationErrors.values.firstOrNull() }
                }
            } catch (e: Exception) {
                if (e is kotlin.coroutines.cancellation.CancellationException) throw e
                isSaving = false
                errorMessage = e.message
            }
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MiuixTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = stringResource(R.string.user_profile_edit_title),
                largeTitle = stringResource(R.string.user_profile_edit_title),
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    HeaderIcon(MiuixIcons.Back, onClick = onBack)
                },
                actions = {
                    if (isSaving) {
                        LoadingIndicator(modifier = Modifier.padding(end = 12.dp).size(20.dp))
                    } else {
                        Button(
                            onClick = ::save,
                            enabled = userName.isNotBlank() && !isSaving,
                            colors =
                                ButtonDefaults.buttonColors(
                                    color = MiuixTheme.colorScheme.primary,
                                    contentColor = MiuixTheme.colorScheme.onPrimary,
                                ),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text(stringResource(R.string.user_profile_edit_save))
                        }
                    }
                },
            )
        },
    ) { scaffoldPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .background(MiuixTheme.colorScheme.surface),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = scaffoldPadding.calculateTopPadding() + 12.dp,
                    bottom = 96.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (errorMessage != null) {
                item {
                    ElevatedPanel(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Text(
                            text = errorMessage ?: stringResource(R.string.user_profile_edit_failed),
                            color = MiuixTheme.colorScheme.error,
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                }
            }

            if (successMessage != null) {
                item {
                    ElevatedPanel(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(12.dp),
                    ) {
                        Text(
                            text = successMessage ?: stringResource(R.string.user_profile_edit_saved),
                            color = MiuixTheme.colorScheme.primary,
                            style = MiuixTheme.textStyles.body2,
                        )
                    }
                }
            }

            item {
                Section(stringResource(R.string.user_profile_edit_title)) {
                    ElevatedPanel {
                        TextField(
                            value = userName,
                            onValueChange = { userName = it },
                            label = stringResource(R.string.user_profile_edit_user_name),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            singleLine = true,
                        )
                        DividerLine()
                        TextField(
                            value = comment,
                            onValueChange = { comment = it },
                            label = stringResource(R.string.user_profile_edit_comment),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            singleLine = false,
                            maxLines = 6,
                        )
                    }
                }
            }

            item {
                Section(stringResource(R.string.settings_general)) {
                    ElevatedPanel {
                        TextField(
                            value = webpage,
                            onValueChange = { webpage = it },
                            label = stringResource(R.string.user_profile_edit_webpage),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            singleLine = true,
                        )
                        DividerLine()
                        TextField(
                            value = twitter,
                            onValueChange = { twitter = it },
                            label = stringResource(R.string.user_profile_edit_twitter),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            singleLine = true,
                        )
                        DividerLine()
                        TextField(
                            value = birthday,
                            onValueChange = { birthday = it },
                            label = stringResource(R.string.user_profile_edit_birthday),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                            singleLine = true,
                        )
                    }
                }
            }
        }
    }
}
