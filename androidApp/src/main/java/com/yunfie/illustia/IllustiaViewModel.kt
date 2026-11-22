package com.yunfie.illustia

import android.app.Application
import androidx.annotation.Keep

/**
 * Stable ViewModel entry point used by the UI and dependency injection.
 *
 * Feature implementation lives under the `viewmodel` source folder so this
 * public type can remain source-compatible while its responsibilities evolve.
 */
@Keep
class IllustiaViewModel(
    app: Application,
) : IllustiaViewModelCore(app)
