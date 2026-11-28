package com.yunfie.illustia

import androidx.lifecycle.ViewModel

/** Android では AndroidX の [ViewModel] にそのまま委譲する。 */
actual abstract class PlatformViewModel actual constructor() : ViewModel() {
    protected actual open override fun onCleared() = super.onCleared()
}
