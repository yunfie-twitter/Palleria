package com.yunfie.illustia.pallasync

/** §5.5: one cleanup failure cannot leave later secret owners unclosed. */
@Suppress("TooGenericExceptionCaught")
internal fun closeRecoveryResources(resources: List<AutoCloseable>) {
    var failure: Throwable? = null
    for (resource in resources) {
        try {
            resource.close()
        } catch (cleanup: Throwable) {
            if (failure == null) {
                failure = cleanup
            } else if (failure !== cleanup) {
                failure.addSuppressed(cleanup)
            }
        }
    }
    failure?.let { throw it }
}
