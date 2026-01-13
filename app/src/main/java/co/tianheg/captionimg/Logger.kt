package co.tianheg.captionimg

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    private var logFile: File? = null

    fun init(context: Context) {
        // Scoped Storage (targetSdk 29+) 下不应写入 /sdcard 根目录。
        // 统一写到应用私有目录：优先外部私有目录，其次内部目录，无需任何存储权限。
        val dir = context.getExternalFilesDir("logs") ?: context.filesDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        logFile = File(dir, "error_log.txt")
    }

    fun log(message: String, e: Throwable? = null) {
        val file = logFile ?: return
        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val logMessage = "$timestamp: $message\n${e?.stackTraceToString() ?: ""}\n"
            
            FileOutputStream(file, true).use {
                it.write(logMessage.toByteArray())
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }
}
