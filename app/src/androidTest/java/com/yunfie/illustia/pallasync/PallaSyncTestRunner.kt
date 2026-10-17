package com.yunfie.illustia.pallasync

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Isolate protocol tests from account startup, workers and telemetry on the disposable emulator. */
class PallaSyncTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader,
        className: String,
        context: Context,
    ): Application = super.newApplication(cl, Application::class.java.name, context)
}
