package dev.nncx

import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Twenty lines that buy the freedom to break things recklessly.
 *
 * A launcher that crashes leaves the phone with no home screen, which turns a
 * Saturday toy into a Saturday problem. On an uncaught throw we hand control to
 * FallbackActivity — a different process, no Compose, no theme, no index — and
 * write the trace where x-ray can read it later.
 */
object CrashGuard {

    private const val TAG = "NNCx/CrashGuard"
    private const val DIR = "crashes"
    private const val KEEP = 20

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val app = context.applicationContext

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(app, thread, error) }
            runCatching {
                app.startActivity(
                    Intent(app, FallbackActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .putExtra(FallbackActivity.EXTRA_REASON, error.javaClass.simpleName + ": " + error.message)
                )
            }
            Log.e(TAG, "home died on ${thread.name}", error)

            // Don't let the platform's crash dialog race our fallback.
            previous?.let { runCatching { } }
            Process.killProcess(Process.myPid())
            kotlin.system.exitProcess(10)
        }
    }

    fun record(context: Context, thread: Thread, error: Throwable) {
        val dir = File(context.filesDir, DIR).apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()

        File(dir, "$stamp.txt").writeText(
            buildString {
                appendLine("when:   $stamp")
                appendLine("thread: ${thread.name}")
                appendLine("type:   ${error.javaClass.name}")
                appendLine("msg:    ${error.message}")
                appendLine()
                append(trace)
            }
        )

        dir.listFiles()?.sortedBy { it.name }?.dropLast(KEEP)?.forEach { it.delete() }
    }

    fun crashes(context: Context): List<File> =
        File(context.filesDir, DIR).listFiles()?.sortedByDescending { it.name } ?: emptyList()
}
