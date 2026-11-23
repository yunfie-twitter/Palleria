package com.yunfie.illustia

import android.app.Application
import androidx.annotation.Keep
import com.yunfie.illustia.platform.ViewModelDeps

/**
 * Stable ViewModel entry point used by the UI and dependency injection.
 *
 * Feature implementation lives under the `viewmodel` source folder so this
 * public type can remain source-compatible while its responsibilities evolve.
 *
 * The `Application` constructor is required by `AndroidViewModelFactory`; it is
 * converted to [ViewModelDeps] immediately so the rest of the chain stays free of
 * the raw `Application` type.
 */
@Keep
class IllustiaViewModel(
    app: Application,
) : IllustiaViewModelCore(ViewModelDeps(app))
