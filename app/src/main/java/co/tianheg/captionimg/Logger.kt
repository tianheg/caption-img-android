package co.tianheg.captionimg

import android.content.Context
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    private var logFile: File? = null

    fun init(context: Context) {
        // 由于 targetSdk 为 28，我们可以直接在内部存储根目录创建文件夹
        // 路径为：/sdcard/co.tianheg.captionimg/error_log.txt
        try {
            val root = Environment.getExternalStorageDirectory()
            val dir = File(root, "co.tianheg.captionimg")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            logFile = File(dir, "error_log.txt")
        } catch (e: Exception) {
            // 回退到应用私有外部存储
            val dir = context.getExternalFilesDir(null)
            if (dir != null) {
                logFile = File(dir, "error_log.txt")
            }
        }
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
