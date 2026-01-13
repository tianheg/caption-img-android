package co.tianheg.captionimg

import android.content.Context
import android.net.Uri
import android.os.Build
import android.app.RecoverableSecurityException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * XMP-only description reader/writer.
 *
 * Notes:
 * - Only reads/writes JPEG APP1 XMP packets via [JpegXmp].
 * - No fallback to other metadata formats (by design).
 */
class XmpHandler(private val context: Context) {

    suspend fun readDescription(uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                val xmpPacket = context.contentResolver.openInputStream(uri)?.use { stream ->
                    JpegXmp.readXmp(stream)
                }

                if (xmpPacket.isNullOrBlank()) return@withContext null

                val extracted = try {
                    XmpDescription.extractDescription(xmpPacket)
                } catch (e: Exception) {
                    Logger.log("解析 JPEG XMP 失败", e)
                    null
                }

                extracted
                    ?.trim()
                    ?.takeIf { it.isNotBlank() && it != "??" }
            } catch (e: Exception) {
                Logger.log("读取 XMP 描述失败", e)
                null
            }
        }
    }

    /**
     * Updates image description by writing a JPEG APP1 XMP segment.
     *
     * This method overwrites the original URI only (no "save as" fallback).
     *
     * Note: some providers may re-encode the image and drop metadata; in that case
     * verification will fail and this returns null.
     */
    suspend fun updateDescription(uri: Uri, description: String): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val xmpPacket = XmpDescription.buildPacket(description)

                val tempWithXmp = File.createTempFile("xmp_temp", ".jpg", context.cacheDir)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempWithXmp.outputStream().use { output ->
                        val ok = JpegXmp.writeXmp(input, output, xmpPacket)
                        if (!ok) throw IllegalStateException("不是有效的 JPG/JPEG 文件，无法写入 XMP")
                    }
                } ?: return@withContext null

                // 1) Try overwrite with rwt (best-effort for SAF/document providers).
                val wroteBackRwt = try {
                    context.contentResolver.openOutputStream(uri, "rwt")?.use { outputStream ->
                        tempWithXmp.inputStream().use { input ->
                            input.copyTo(outputStream)
                        }
                    } != null
                } catch (e: Exception) {
                    Logger.log("写回原始图片失败: $uri", e)
                    false
                }

                // 2) Fallback: try plain write mode.
                val wroteBack = wroteBackRwt || try {
                    context.contentResolver.openOutputStream(uri, "w")?.use { outputStream ->
                        tempWithXmp.inputStream().use { input ->
                            input.copyTo(outputStream)
                        }
                    } != null
                } catch (e: Exception) {
                    Logger.log("写回原始图片失败: $uri", e)
                    false
                }

                if (!wroteBack) {
                    tempWithXmp.delete()
                    return@withContext null
                }

                // Verify read-back: if provider strips metadata, report failure.
                val verify = try {
                    val xmpNow = context.contentResolver.openInputStream(uri)?.use { stream ->
                        JpegXmp.readXmp(stream)
                    }
                    val extracted = if (!xmpNow.isNullOrBlank()) XmpDescription.extractDescription(xmpNow) else null
                    extracted == description
                } catch (e: Exception) {
                    Logger.log("验证写回失败: $uri", e)
                    false
                }

                tempWithXmp.delete()

                if (verify) uri else null
            } catch (e: Exception) {
                // Let callers handle permission recovery flows.
                if (e is SecurityException) throw e
                if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) throw e
                Logger.log("更新 XMP 描述失败: $uri", e)
                null
            }
        }
    }
}
