package com.volumekey.counter.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.volumekey.counter.data.AppDatabase
import com.volumekey.counter.data.CounterRepository
import com.volumekey.counter.data.InventoryItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 清单盘点 CSV 导入（经评审迭代版）。
 * 表头五列（顺序任意）：盘点对象名称 / 自定义列 / 盘点对象数量 / 实际盘点数量 / 备注
 *
 * 兼容性：
 * - 编码 BOM 嗅探：UTF-8（含BOM）/ UTF-16LE / UTF-16BE；无 BOM 时 UTF-8 优先、
 *   出现替换字符则回退 GBK
 * - 分隔符嗅探：Tab（Excel「Unicode 文本」）/ 半角逗号 / 全角逗号（仅无半角逗号时）
 * - 引号包裹字段（含转义引号与单元格内换行）、千分位数量、"12.0" 小数
 * - 表头不可识别时的启发式：首行无任何数字且次行有数字 → 跳过首行
 */
object InventoryImporter {

    private val NAME_ALIASES = listOf("盘点对象名称", "名称", "物料名称", "对象名称", "品名", "name")
    private val CUSTOM_ALIASES = listOf("自定义列", "自定义", "规格", "型号")
    private val EXPECTED_ALIASES = listOf("盘点对象数量", "账面数量", "应盘数量", "账面数", "数量")
    private val COUNTED_ALIASES = listOf("实际盘点数量", "实盘数量", "实盘数", "实盘", "实际数量")
    private val REMARK_ALIASES = listOf("备注", "说明")

    data class Result(
        val listId: Long,
        val rows: Int,
        val skipped: Int,
        val qtyInvalid: Int,
        val headerMatched: Boolean
    )

    const val TEMPLATE_CSV =
        "盘点对象名称,自定义列,盘点对象数量,实际盘点数量,备注\n" +
            "示例-A区货架,规格型号X1,100,0,\n" +
            "示例-B区托盘,规格型号X2,24,0,放行待核对\n"

    suspend fun import(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法读取所选文件")
        val text = decode(bytes)
        if (text.isBlank()) throw IllegalStateException("文件内容为空")

        val delimiter = sniffDelimiter(text)
        val table = parseCsv(text, delimiter)
            .map { row -> row.map { it.trim() } }
            .filter { row -> row.any { it.isNotEmpty() } }
        if (table.isEmpty()) throw IllegalStateException("没有有效数据行")

        if (table.first().size < 2) {
            throw IllegalStateException("格式不识别：整行只解析出一列。请确认是逗号分隔的 CSV（Excel 另存为「CSV(逗号分隔)」或「Unicode 文本」）")
        }

        // 表头列映射
        val header = table.first()
        val idxName = header.indexOfFirst { it in NAME_ALIASES }
        val useHeader = idxName >= 0
        val idxCustom = header.indexOfFirst { it in CUSTOM_ALIASES }
        val idxExpected = header.indexOfFirst { it in EXPECTED_ALIASES }
        val idxCounted = header.indexOfFirst { it in COUNTED_ALIASES }
        val idxRemark = header.indexOfFirst { it in REMARK_ALIASES }

        var dataRows = if (useHeader) table.drop(1) else table
        if (!useHeader && dataRows.size >= 2) {
            // 启发式：首行无数字、次行有数字 → 首行是不可识别的表头，跳过
            val firstHasNum = dataRows.first().any { parseQtyRaw(it) != null }
            val secondHasNum = dataRows[1].any { parseQtyRaw(it) != null }
            if (!firstHasNum && secondHasNum) dataRows = dataRows.drop(1)
        }
        if (dataRows.isEmpty()) throw IllegalStateException("没有数据行（只有表头？）")

        val displayName = queryDisplayName(context, uri) ?: "导入清单"
        val listName = displayName.substringBeforeLast('.')

        var skipped = 0
        var qtyInvalid = 0
        val items = dataRows.mapIndexedNotNull { i, row ->
            fun cell(idx: Int?, def: Int): String =
                if (idx != null && idx < row.size) row[idx] else row.getOrNull(def) ?: ""

            val name = if (useHeader) row.getOrNull(idxName) ?: "" else cell(null, 0)
            if (name.isEmpty()) {
                skipped++
                return@mapIndexedNotNull null
            }
            val expectedRaw = if (useHeader) row.getOrNull(idxExpected) ?: "" else cell(null, 2)
            val countedRaw = if (useHeader) row.getOrNull(idxCounted) ?: "" else cell(null, 3)
            val expected = parseQtySmart(expectedRaw) { qtyInvalid++ }
            val counted = parseQtySmart(countedRaw) { qtyInvalid++ }
            InventoryItem(
                listId = 0L,
                sortOrder = i,
                name = name,
                customField = if (useHeader) row.getOrNull(idxCustom) ?: "" else cell(null, 1),
                expectedQty = expected,
                countedQty = counted,
                touched = false,
                remark = if (useHeader) row.getOrNull(idxRemark) ?: "" else cell(null, 4)
            )
        }
        if (items.isEmpty()) throw IllegalStateException("没有有效条目（名称列均为空）")

        val db = AppDatabase.getInstance(context)
        val repo = CounterRepository(db, db.counterDao())
        val listId = repo.insertInventoryList(listName, items)
        Result(listId, items.size, skipped, qtyInvalid, useHeader)
    }

    // ---- 编码 ----

    internal fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            }
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
        }
        var text = String(bytes, Charsets.UTF_8)
        if (text.isNotEmpty() && text[0] == '﻿') text = text.substring(1) // UTF-8 BOM
        if (text.contains('\uFFFD')) text = String(bytes, charset("GBK")) // Excel 导出的 GBK
        return text
    }

    // ---- 分隔符与解析 ----

    internal fun sniffDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull { it.isNotBlank() } ?: return ','
        val tabs = firstLine.count { it == '\t' }
        val commas = firstLine.count { it == ',' }
        val fullWidth = firstLine.count { it == '，' }
        return when {
            tabs > commas && tabs >= 1 -> '\t'
            commas == 0 && fullWidth >= 4 -> '，'
            else -> ','
        }
    }

    /** 全文单遍解析：支持引号包裹、转义引号、单元格内换行 */
    internal fun parseCsv(text: String, delimiter: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> when {
                    c == '"' -> if (i + 1 < text.length && text[i + 1] == '"') {
                        sb.append('"'); i++
                    } else inQuotes = false
                    else -> sb.append(c)
                }
                c == '"' -> inQuotes = true
                c == delimiter -> {
                    row.add(sb.toString()); sb.clear()
                }
                c == '\r' -> { /* \r\n 与孤立 \r 都按换行处理，\r 本身丢弃 */ }
                c == '\n' -> {
                    row.add(sb.toString()); rows.add(row)
                    row = mutableListOf(); sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        if (sb.isNotEmpty() || row.isNotEmpty()) {
            row.add(sb.toString()); rows.add(row)
        }
        return rows
    }

    // ---- 数量解析 ----

    private class QtySignal

    /** 严格解析（表头启发用）：返回 null 表示不是数字 */
    internal fun parseQtyRaw(s: String): Long? {
        val t = s.trim().replace(",", "").replace("，", "")
        if (t.isEmpty()) return null
        return t.toLongOrNull() ?: t.toDoubleOrNull()?.let { Math.round(it) }
    }

    /** 宽松解析（数据用）：空=0，解析失败计无效并按 0 */
    private inline fun parseQtySmart(s: String, onInvalid: () -> Unit): Long {
        val t = s.trim()
        if (t.isEmpty()) return 0L
        val cleaned = t.replace(",", "").replace("，", "")
        val v = cleaned.toLongOrNull() ?: cleaned.toDoubleOrNull()?.let { Math.round(it) }
        if (v == null) {
            onInvalid()
            return 0L
        }
        return v
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (_: Exception) {
        null
    }

    /** 生成模板 CSV 到 Download（Android 10+），低版本由调用方走分享兜底 */
    fun writeTemplate(context: Context): String? =
        CsvExporter.saveToDownloads(context, "盘点清单模板.csv", TEMPLATE_CSV)
}
