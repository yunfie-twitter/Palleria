package com.yunfie.illustia

import android.app.Application
import com.yunfie.illustia.data.ManagedDataRepository

/**
 * Stable implementation entry point assembled from responsibility-focused modules.
 *
 * Keep this class intentionally small: new behavior belongs in the closest feature
 * module, while state shared across modules belongs in [IllustiaViewModelFoundation].
 */
open class IllustiaViewModelCore(
    app: Application,
    managedDataRepository: ManagedDataRepository = ManagedDataRepository(app.contentResolver),
) : IllustiaLibraryNavigationModule(app, managedDataRepository)
