package com.volumekey.counter.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.volumekey.counter.data.Checkpoint
import com.volumekey.counter.data.Counter
import com.volumekey.counter.data.CountEvent
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 计数数据 CSV 导出（UTF-8 带 BOM，Excel 直接打开中文不乱码）。
 * Android 10+ 写入公共 Download 目录；更早系统退化为分享方式。
 */
object CsvExporter {

    private fun sourceName(source: String): String = when (source) {
        "volume_up" -> "音量上键"
        "volume_down" -> "音量下键"
        "button" -> "屏幕按钮"
        "pocket" -> "口袋模式"
        "rollback" -> "回退"
        "reset" -> "复位"
        else -> source
    }

    fun buildCsv(counter: Counter, checkpoints: List<Checkpoint>, events: List<CountEvent>): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.append("音量键计数器 导出\n\n")
        sb.append("[计数器]\n名称,当前计数,创建时间,更新时间\n")
        sb.append("${csv(counter.name)},${counter.value},${csv(fmt.format(Date(counter.createdAt)))},${csv(fmt.format(Date(counter.updatedAt)))}\n\n")
        sb.append("[检查点]\n序号,名称,计数值,保存时间\n")
        checkpoints.forEachIndexed { i, cp ->
            sb.append("${i + 1},${csv(cp.name)},${cp.value},${csv(fmt.format(Date(cp.createdAt)))}\n")
        }
        sb.append("\n[事件明细]\n序号,时间,变化,计数后,来源\n")
        events.forEachIndexed { i, e ->
            val delta = if (e.delta > 0) "+${e.delta}" else "${e.delta}"
            sb.append("${i + 1},${csv(fmt.format(Date(e.timestamp)))},${delta},${e.valueAfter},${csv(sourceName(e.source))}\n")
        }
        sb.append("\n导出时间,${csv(fmt.format(Date()))}\n")
        return sb.toString()
    }

    private fun csv(s: String): String =
        if (s.contains(',') || s.contains('"') || s.contains('\n')) "\"${s.replace("\"", "\"\"")}\"" else s

    fun fileName(counterName: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val safe = counterName.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_")
        return "计数器_${safe}_$stamp.csv"
    }

    /** @return 成功时返回提示路径文案；失败返回 null（调用方走分享兜底） */
    fun saveToDownloads(context: Context, fileName: String, content: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return null
            context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // BOM
                out.write(content.toByteArray(Charsets.UTF_8))
            } ?: return null
            "Download/$fileName"
        } catch (_: Exception) {
            null
        }
    }

    /** 兜底：写缓存目录后拉起分享（低版本系统或 Download 写入失败时） */
    fun shareAsFile(context: Context, fileName: String, content: String): Boolean {
        return try {
            val dir = File(context.cacheDir, "csv_share").apply { mkdirs() }
            val file = File(dir, fileName)
            file.outputStream().use { out ->
                out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "导出/分享 CSV"))
            true
        } catch (_: Exception) {
            false
        }
    }
}
