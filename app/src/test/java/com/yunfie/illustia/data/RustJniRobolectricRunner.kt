package com.yunfie.illustia.data

import org.junit.runners.model.FrameworkMethod
import org.robolectric.RobolectricTestRunner
import org.robolectric.internal.bytecode.InstrumentationConfiguration

/** A JVM can load one JNI image into only one classloader. Android/Room still use the SDK sandbox. */
class RustJniRobolectricRunner(
    testClass: Class<*>,
) : RobolectricTestRunner(testClass) {
    override fun createClassLoaderConfig(method: FrameworkMethod): InstrumentationConfiguration {
        val builder = InstrumentationConfiguration.Builder(super.createClassLoaderConfig(method))
        builder.doNotAcquirePackage("kotlinx.serialization")
        builder.doNotAcquirePackage("com.sun.jna")
        builder.doNotAcquirePackage("com.yunfie.illustia.rust")
        return builder.build()
    }
}
