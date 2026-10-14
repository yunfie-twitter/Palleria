package com.yunfie.illustia.pallasync

import org.junit.runners.model.FrameworkMethod
import org.robolectric.RobolectricTestRunner
import org.robolectric.internal.bytecode.InstrumentationConfiguration

/** A JVM can load one JNI image into only one classloader. Android/Room still use the SDK sandbox. */
class PallaSyncJniRobolectricRunner(
    testClass: Class<*>,
) : RobolectricTestRunner(testClass) {
    override fun createClassLoaderConfig(method: FrameworkMethod): InstrumentationConfiguration {
        val builder = InstrumentationConfiguration.Builder(super.createClassLoaderConfig(method))
        listOf(
            "PallaSyncCore",
            "PallaSyncSnapshotCore",
            "PallaSyncKeySnapshot",
            "PallaSyncPreparedKeys",
            "PallaSyncSecretLifecycle",
            "PallaSyncRecoveryCandidate",
            "PallaSyncRecoveryCompletion",
            "PallaSyncRootSession",
            "PallaSyncRootSession\$Companion",
            "PallaSyncRootSession\$Operation",
            "PallaSyncRootSession\$Identity",
            "PallaSyncRootSession\$Identity\$Companion",
            "PallaSyncRootSession\$Identity\$\$serializer",
            "PallaSyncRootSessionKt",
        ).forEach {
            builder.doNotAcquireClass("com.yunfie.illustia.pallasync.$it")
        }
        return builder.build()
    }
}
