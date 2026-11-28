package com.yunfie.illustia

/** iOS ではライフサイクル連動が無いため、素の基底クラスで十分。 */
actual abstract class PlatformViewModel actual constructor() {
    protected actual open fun onCleared() = Unit
}
