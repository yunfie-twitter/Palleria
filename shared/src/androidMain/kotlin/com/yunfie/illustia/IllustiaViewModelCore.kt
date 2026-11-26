package com.yunfie.illustia

import com.yunfie.illustia.platform.ViewModelDepsApi

/**
 * Stable implementation entry point assembled from responsibility-focused modules.
 *
 * Keep this class intentionally small: new behavior belongs in the closest feature
 * module, while state shared across modules belongs in [IllustiaViewModelFoundation].
 */
open class IllustiaViewModelCore(
    deps: ViewModelDepsApi,
) : IllustiaLibraryNavigationModule(deps)
