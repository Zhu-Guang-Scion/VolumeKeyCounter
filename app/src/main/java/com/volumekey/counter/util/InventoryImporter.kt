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
 * 清单盘点 CSV 导入。
 * 表头五列（顺序任意）：盘点对象名称 / 自定义列 / 盘点对象数量 / 实际盘点数量 / 备注
 * 兼容 UTF-8(含BOM) 与 GBK 编码、带引号字段；无表头时按 [名称,自定义列,数量,实盘,备注] 顺序解析。
 */
object InventoryImporter {

    private val NAME_ALIASES = listOf("盘点对象名称", "名称", "物料名称", "对象名称", "品名", "name")
    private val CUSTOM_ALIASES = listOf("自定义列", "自定义", "规格", "型号")
    private val EXPECTED_ALIASES = listOf("盘点对象数量", "账面数量", "应盘数量", "账面数", "数量")
    private val COUNTED_ALIASES = listOf("实际盘点数量", "实盘数量", "实盘数", "实盘", "实际数量")
    private val REMARK_ALIASES = listOf("备注", "说明")

    data class Result(val listId: Long, val rows: Int, val skipped: Int)

    suspend fun import(context: Context, uri: Uri): Result = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法读取所选文件")
        val text = decode(bytes)
        val displayName = queryDisplayName(context, uri) ?: "导入清单"
        val listName = displayName.substringBeforeLast('.')

        val table = text.lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .map(::parseCsvLine)
            .toList()
        if (table.isEmpty()) throw IllegalStateException("文件内容为空")

        // 表头列映射
        val header = table.first()
        val idxName = header.indexOfFirst { it.trim() in NAME_ALIASES }
        val useHeader = idxName >= 0
        val idxCustom = header.indexOfFirst { it.trim() in CUSTOM_ALIASES }
        val idxExpected = header.indexOfFirst { it.trim() in EXPECTED_ALIASES }
        val idxCounted = header.indexOfFirst { it.trim() in COUNTED_ALIASES }
        val idxRemark = header.indexOfFirst { it.trim() in REMARK_ALIASES }

        val dataRows = if (useHeader) table.drop(1) else table
        if (dataRows.isEmpty()) throw IllegalStateException("没有数据行（只有表头？）")

        var skipped = 0
        val items = dataRows.mapIndexedNotNull { i, row ->
            val cell: (Int?, Int) -> String = { idx, def -> if (idx != null && idx < row.size) row[idx].trim() else row.getOrNull(def)?.trim() ?: "" }
            val name = if (useHeader) row.getOrNull(idxName)?.trim() ?: "" else cell(null, 0)
            if (name.isEmpty()) {
                skipped++
                return@mapIndexedNotNull null
            }
            InventoryItem(
                listId = 0L,
                sortOrder = i,
                name = name,
                customField = if (useHeader) row.getOrNull(idxCustom)?.trim() ?: "" else cell(null, 1),
                expectedQty = parseQty(if (useHeader) row.getOrNull(idxExpected) else cell(null, 2)),
                countedQty = parseQty(if (useHeader) row.getOrNull(idxCounted) else cell(null, 3)),
                touched = false,
                remark = if (useHeader) row.getOrNull(idxRemark)?.trim() ?: "" else cell(null, 4)
            )
        }
        if (items.isEmpty()) throw IllegalStateException("没有有效条目（名称列均为空）")

        val db = AppDatabase.getInstance(context)
        val repo = CounterRepository(db, db.counterDao())
        val listId = repo.insertInventoryList(listName, items)
        Result(listId, items.size, skipped)
    }

    private fun decode(bytes: ByteArray): String {
        var text = String(bytes, Charsets.UTF_8)
        if (text.isNotEmpty() && text[0] == '﻿') text = text.substring(1) // 去 BOM
        if (text.contains('\uFFFD')) text = String(bytes, charset("GBK")) // Excel 导出的 GBK
        return text
    }

    /** 单行 CSV 解析，支持引号包裹与转义引号 */
    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < line.length && line[i + 1] == '"') { sb.append('"'); i++ }
                        else inQuotes = false
                    } else sb.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> { out.add(sb.toString()); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    private fun parseQty(s: String?): Long {
        val t = s?.trim().orEmpty()
        if (t.isEmpty()) return 0L
        return t.toDoubleOrNull()?.let { Math.round(it) } ?: t.toLongOrNull() ?: 0L
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    } catch (_: Exception) {
        null
    }

    /** 生成模板 CSV 到 Download 目录（UTF-8 带 BOM，Excel 可直接编辑另存） */
    fun writeTemplate(context: Context): String? {
        val content = "盘点对象名称,自定义列,盘点对象数量,实际盘点数量,备注\n" +
            "示例-A区货架,规格型号X1,100,0,\n" +
            "示例-B区托盘,规格型号X2,24,0,放行待核对\n"
        return CsvExporter.saveToDownloads(context, "盘点清单模板.csv", content)
    }
}
