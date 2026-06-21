package com.yunfie.illustia

import android.content.Context
import android.content.res.Resources
import android.os.Looper
import android.os.Process
import android.view.Gravity
import android.view.InflateException
import android.widget.Toast
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.concurrent.thread
import kotlin.system.exitProcess

class CrashHandler : Thread.UncaughtExceptionHandler {
    private var defaultHandler: Thread.UncaughtExceptionHandler? = null
    private var context: Context? = null

    fun init(ctx: Context) {
        context = ctx.applicationContext
        defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    override fun uncaughtException(thread: Thread, ex: Throwable) {
        if (!handleException(ex)) {
            defaultHandler?.uncaughtException(thread, ex)
            return
        }
        defaultHandler?.uncaughtException(thread, ex) ?: run {
            Process.killProcess(Process.myPid())
            exitProcess(10)
        }
    }

    private fun handleException(ex: Throwable?): Boolean {
        if (ex == null) return false
        thread {
            Looper.prepare()
            val message = crashMessage(ex)
            Toast.makeText(context, message, Toast.LENGTH_LONG).also {
                it.setGravity(Gravity.CENTER, 0, 0)
                it.show()
            }
            Looper.loop()
        }
        return true
    }

    private fun crashMessage(ex: Throwable): String {
        val message = ex.message.orEmpty()
        return when {
            ex is Resources.NotFoundException || ex is InflateException || message.contains("XML") ->
                "アプリのリソース読み込みに失敗しました。\n$message"
            message.contains("document", ignoreCase = true) ->
                "保存先フォルダの権限を確認してください。設定から保存先を選び直すと復旧できる場合があります。\n$message"
            else -> {
                val sw = StringWriter()
                ex.printStackTrace(PrintWriter(sw))
                sw.buffer.toString().trim()
            }
        }
    }

    companion object {
        val instance: CrashHandler by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { CrashHandler() }
    }
}
