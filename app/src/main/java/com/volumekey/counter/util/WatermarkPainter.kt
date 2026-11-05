package com.volumekey.counter.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.exifinterface.media.ExifInterface

/**
 * 水印绘制引擎（美化版）：照片转正后在左下角绘制渐变底块 + 标题/信息两级排版。
 * 排版参考主流水印相机：大号标题 + 彩色竖条点缀 + 细分隔线 + 信息行。
 */
object WatermarkPainter {

    private const val ACCENT_COLOR = 0xFF6FC3FF.toInt()

    fun exifOrientation(bytes: ByteArray): Int = try {
        val exif = ExifInterface(bytes.inputStream())
        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (_: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    fun apply(src: Bitmap, orientation: Int, title: String, infoLines: List<String>): Bitmap {
        val upright = rotateByExif(src, orientation)
        val out = if (upright.config == Bitmap.Config.ARGB_8888 && upright.isMutable) upright
        else upright.copy(Bitmap.Config.ARGB_8888, true)

        val w = out.width
        val h = out.height
        val shortEdge = minOf(w, h)
        val infoPx = (shortEdge / 26f).coerceIn(18f, 60f)
        val titlePx = infoPx * 1.35f
        val pad = infoPx * 0.7f
        val lineGap = infoPx * 0.5f
        val dividerGap = infoPx * 0.6f
        val accentW = infoPx * 0.3f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = titlePx
            isFakeBoldText = true
        }
        val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(245, 255, 255, 255)
            textSize = infoPx
        }
        val dividerPaint = Paint().apply { color = Color.argb(80, 255, 255, 255) }

        val titleW = titlePaint.measureText(title)
        val infoW = if (infoLines.isEmpty()) 0f else infoLines.maxOf { infoPaint.measureText(it) }
        val contentW = maxOf(titleW, infoW)
        val textLeft = pad
        val blockW = textLeft + contentW + pad
        val blockH = pad * 2 +
            titlePx * 0.9f + dividerGap * 2 + infoPx * 0.35f +
            infoLines.size * (infoPx + lineGap)

        val canvas = Canvas(out)
        val margin = infoPx * 0.7f
        val left = margin
        val top = h - blockH - margin

        // 渐变底块（左深右浅）
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                left, top, left + blockW, top,
                Color.argb(185, 0, 0, 0),
                Color.argb(115, 0, 0, 0),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(left, top, left + blockW, top + blockH, infoPx * 0.4f, infoPx * 0.4f, bgPaint)
        // 标题
        var baseline = top + pad + titlePx * 0.78f
        canvas.drawText(title, textLeft, baseline, titlePaint)
        // 分隔线
        val divY = baseline + dividerGap
        canvas.drawRect(textLeft, divY, textLeft + contentW, divY + infoPx * 0.07f, dividerPaint)
        // 信息行
        var y = divY + dividerGap + infoPx * 0.35f + infoPx * 0.78f
        infoLines.forEach { line ->
            canvas.drawText(line, textLeft, y, infoPaint)
            y += infoPx + lineGap
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
