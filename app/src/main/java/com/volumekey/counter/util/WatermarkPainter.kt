package com.volumekey.counter.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.exifinterface.media.ExifInterface

/**
 * 水印绘制引擎：把照片转正（按 EXIF 方向）后在左下角绘制半透明底块 + 多行白字。
 * 参考市面水印相机的通用排版：标题行 + 时间 + 坐标 + 地址。
 */
object WatermarkPainter {

    /** 读取 EXIF 方向 */
    fun exifOrientation(bytes: ByteArray): Int = try {
        val exif = ExifInterface(bytes.inputStream())
        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    fun apply(src: Bitmap, orientation: Int, lines: List<String>): Bitmap {
        val upright = rotateByExif(src, orientation)
        val out = if (upright.config == Bitmap.Config.ARGB_8888 && upright.isMutable) upright
        else upright.copy(Bitmap.Config.ARGB_8888, true)

        val w = out.width
        val h = out.height
        // 字号随图片短边缩放，并夹在合理区间（适配 1080p~4K 照片）
        val textPx = (minOf(w, h) / 24f).coerceIn(20f, 72f)
        val pad = textPx * 0.55f
        val lineGap = textPx * 0.42f

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = textPx
            isFakeBoldText = true
        }
        val bgPaint = Paint().apply {
            color = Color.argb(150, 0, 0, 0)
        }

        val maxW = lines.maxOf { textPaint.measureText(it) }
        val blockW = maxW + pad * 2
        val blockH = lines.size * textPx + (lines.size - 1) * lineGap + pad * 2

        val canvas = Canvas(out)
        val margin = textPx * 0.7f
        val left = margin
        val top = h - blockH - margin

        canvas.drawRoundRect(left, top, left + blockW, top + blockH, textPx * 0.35f, textPx * 0.35f, bgPaint)
        var baseline = top + pad + textPx * 0.82f
        lines.forEach { line ->
            canvas.drawText(line, left + pad, baseline, textPaint)
            baseline += textPx + lineGap
        }
        return out
    }

    private fun rotateByExif(src: Bitmap, orientation: Int): Bitmap {
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees == 0f) return src
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }
}
