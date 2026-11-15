package rw.itunda.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/**
 * Debug builds only: remembers the last uncaught exception and shows it on the next launch so
 * a tester can copy the stack trace without adb. Release builds never install this.
 */
internal object DebugCrashReport {
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val file = File(context.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                file.writeText("Thread: ${thread.name}\n" + error.stackTraceToString().take(12_000))
            } catch (_: Exception) {
                // Best effort only; never mask the original crash.
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun showIfPresent(activity: Activity) {
        val file = File(activity.filesDir, FILE)
        if (!file.isFile) return
        val text = try { file.readText() } catch (_: Exception) { return }
        file.delete()
        val body = TextView(activity).apply {
            this.text = text
            setTextIsSelectable(true)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(40, 24, 40, 24)
        }
        AlertDialog.Builder(activity)
            .setTitle("itunda crashed last time")
            .setView(ScrollView(activity).apply { addView(body) })
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("itunda crash", text))
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
