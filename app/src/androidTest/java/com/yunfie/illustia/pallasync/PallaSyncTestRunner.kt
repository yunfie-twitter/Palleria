package com.yunfie.illustia.pallasync

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.Retention
import kotlin.annotation.Target

/** Isolate protocol tests from account startup, workers and telemetry on the disposable emulator. */
class PallaSyncTestRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle) {
        val runHarnessOnly =
            arguments.getString("relay_origin")?.startsWith("https://127.0.0.1:") == true ||
                arguments.getString("pallasyncLargeHistoryStress") == "true"
        if (!runHarnessOnly) {
            arguments.putString("notAnnotation", PallaSyncHarnessOnly::class.java.name)
        }
        super.onCreate(arguments)
    }

    override fun newApplication(
        cl: ClassLoader,
        className: String,
        context: Context,
    ): Application = super.newApplication(cl, Application::class.java.name, context)
}

/** Tests that require a disposable relay or an explicitly disposable large database. */
@Retention(RUNTIME)
@Target(FUNCTION)
annotation class PallaSyncHarnessOnly
