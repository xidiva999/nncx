package dev.nncx.ui

import android.content.Context
import java.io.File

/** MagicOS encrypts logcat for third-party reads, so traces go to a file. */
object Trace {
    fun w(context: Context, msg: String) {
        runCatching {
            File(context.filesDir, "trace.txt").appendText(msg + "\n")
        }
    }
}
