package com.yunfie.illustia.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle-aware `collectAsState` with no lifecycle to be aware of.
 *
 * `androidx.lifecycle.compose.collectAsStateWithLifecycle` is Android-only, so commonMain screens
 * calling it could never compile for iOS. The lifecycle-aware variant only avoids re-subscribing
 * while the host is stopped; a plain `collectAsState` is otherwise equivalent, and keeping the name
 * lets the shared screens stay unchanged instead of forking six call sites by target.
 */
@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(): State<T> = collectAsState()

/**
 * Same as [collectAsStateWithLifecycle], for a cold flow whose first frame needs a value.
 */
@Composable
fun <T> Flow<T>.collectAsStateWithLifecycle(initialValue: T): State<T> = collectAsState(initial = initialValue)
