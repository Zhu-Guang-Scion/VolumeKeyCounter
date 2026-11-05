package com.volumekey.counter.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File

/**
 * 水印照片落盘：从系统相机写满的临时 URI 解码（采样防 OOM + EXIF 转正）→
 * 绘制水印 → 存入系统相册 Pictures/盘点取证/，命名 盘点对象名-序号.jpg，
 * 并回写 EXIF（拍摄时间 + GPS 坐标）供审计溯源。
 */
object PhotoStore {

    private const val MAX_EDGE = 2560
    const val ALBUM_DIR = "盘点取证"

    data class SaveResult(val fileName: String, val uri: String)

    private fun thumbDir(context: Context): File =
        File(context.filesDir, "thumbs").apply { mkdirs() }

    private fun thumbFile(context: Context, fileName: String): File =
        File(thumbDir(context), fileName)

    /** 保存时同时写一份小缩略图到 App 私有目录（绕开 MediaStore 兼容性问题） */
    private fun savePrivateThumb(context: Context, bitmap: Bitmap, fileName: String) {
        runCatching {
            val scale = 320f / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
            val tw = (bitmap.width * scale).toInt().coerceAtLeast(1)
            val th = (bitmap.height * scale).toInt().coerceAtLeast(1)
            val thumb = Bitmap.createScaledBitmap(bitmap, tw, th, true)
            thumbFile(context, fileName).outputStream().use { thumb.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            if (thumb !== bitmap) thumb.recycle()
        }
    }

    /** 从 App 私有目录加载缩略图（100% 可读，不走 MediaStore） */
    fun loadPrivateThumb(context: Context, fileName: String): Bitmap? = runCatching {
        val f = thumbFile(context, fileName)
        if (f.exists() && f.length() > 0) BitmapFactory.decodeFile(f.absolutePath) else null
    }.getOrNull()

    fun deletePrivateThumb(context: Context, fileName: String) {
        runCatching { thumbFile(context, fileName).delete() }
    }

    fun save(
        context: Context,
        tmpUri: Uri,
        itemSeq: Int,
        watermarkTitle: String,
        watermarkInfoLines: List<String>,
        lat: Double?,
        lng: Double?,
        takenAt: Long,
        itemName: String
    ): SaveResult {
        // 1) 读字节（相机已把照片写入我们 FileProvider 的临时文件）
        val bytes = context.contentResolver.openInputStream(tmpUri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法读取拍摄结果")
        if (bytes.size < 1024) throw IllegalStateException("拍摄结果异常（文件过小，可能被相机取消）")

        // 2) 采样解码（防 OOM）+ EXIF 转正 + 水印
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / sample > MAX_EDGE) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            ?: throw IllegalStateException("照片解码失败")
        val orientation = WatermarkPainter.exifOrientation(bytes)
        val marked = WatermarkPainter.apply(src, orientation, watermarkTitle, watermarkInfoLines)
        if (marked !== src) src.recycle()

        // 3) 相册目录 + 文件名
        val safeName = itemName.replace(Regex("[\\\\/:*?\"<>|\\s]"), "_").ifBlank { "盘点对象" }
        val fileName = "${safeName}-$itemSeq.jpg"

        // 4) 写入 MediaStore
        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM_DIR)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    ALBUM_DIR
                )
                dir.mkdirs()
                put(MediaStore.Images.Media.DATA, File(dir, fileName).absolutePath)
            }
        }
        val outUri = resolver.insert(collection, values)
            ?: throw IllegalStateException("无法创建相册条目")
        try {
            // 先压到缓存文件，便于经 ExifInterface(String) 回写 EXIF（时间/GPS）
            val tmpOut = File(context.cacheDir, "wm_out_${System.currentTimeMillis()}.jpg")
            try {
                tmpOut.outputStream().use { os -> marked.compress(Bitmap.CompressFormat.JPEG, 92, os) }
                try {
                    val exif = ExifInterface(tmpOut.absolutePath)
                    val fmt = java.text.SimpleDateFormat("yyyy:MM:dd HH:mm:ss", java.util.Locale.US)
                    exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, fmt.format(java.util.Date(takenAt)))
                    exif.setAttribute(ExifInterface.TAG_DATETIME, fmt.format(java.util.Date(takenAt)))
                    if (lat != null && lng != null) exif.setLatLong(lat, lng)
                    exif.saveAttributes()
                } catch (_: Exception) {
                    // EXIF 失败不影响成片
                }
                resolver.openOutputStream(outUri)?.use { os -> os.write(tmpOut.readBytes()) }
                    ?: throw IllegalStateException("无法写入照片")
            } finally {
                runCatching { tmpOut.delete() }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(outUri, values, null, null)
            }
        } catch (e: Exception) {
            runCatching { resolver.delete(outUri, null, null) }
            throw e
        } finally {
            // 在 recycle 前保存私有缩略图
            savePrivateThumb(context, marked, fileName)
            marked.recycle()
        }
        // 回读系统实际登记的文件名——若同名陈旧行导致系统自动改名为 "xxx (1).jpg"，
        // 必须以实际名为准，否则后续按名查询/缩略图全部失配
        val actualName = runCatching {
            resolver.query(outUri, arrayOf(MediaStore.Images.Media.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        return SaveResult(actualName ?: fileName, outUri.toString())
    }

    // ---- 相册查询 / 缩略图 / 删除 ----

    /** 在 盘点取证 相册中按文件名找 MediaStore URI；MediaStore 查不到时退化为直拼文件路径 URI */
    fun findUri(context: Context, displayName: String): Uri? = try {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val byQuery = context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.DISPLAY_NAME} = ?",
            arrayOf(displayName),
            null
        )?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                Uri.withAppendedPath(collection, id.toString())
            } else null
        }
        // MediaStore 查不到（IS_PENDING 期间对其他查询不可见等）→ 直接拼文件路径
        byQuery ?: Uri.fromFile(
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "$ALBUM_DIR/$displayName"
            )
        ).takeIf { it.path != null && File(it.path!!).canRead() }
    } catch (_: Exception) {
        null
    }

    fun deleteByUri(context: Context, uriString: String, displayName: String): Boolean = try {
        val uri: Uri = when {
            uriString.isNotBlank() -> Uri.parse(uriString)
            else -> findUri(context, displayName) ?: return false
        }
        context.contentResolver.delete(uri, null, null) > 0
    } catch (_: Exception) {
        false
    }

    /**
     * 存在性以「能实际打开流」为准：优先用存储的 URI，退化到按名查询。
     */
    fun existsReadable(context: Context, uriString: String, displayName: String): Boolean = try {
        val uri: Uri = when {
            uriString.isNotBlank() -> Uri.parse(uriString)
            else -> findUri(context, displayName) ?: return false
        }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.read() } != null
        }.getOrDefault(false)
    } catch (_: Exception) {
        false
    }

    private val thumbCache = android.util.LruCache<String, Bitmap>(48)

    /** 加载缩略图：优先 App 私有目录（100% 可读），退化到存储的 content URI → 按名查询 */
    fun loadThumbnail(context: Context, uriString: String, displayName: String): Bitmap? = runCatching {
        val key = "th_$displayName"
        thumbCache.get(key)?.let { return it }
        // 1) App 私有目录（最可靠）
        loadPrivateThumb(context, displayName)?.let {
            thumbCache.put(key, it)
            return it
        }
        // 2) 存储的 content URI
        val uri: Uri = when {
            uriString.isNotBlank() -> Uri.parse(uriString)
            else -> findUri(context, displayName) ?: return null
        }
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 320) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (bmp != null) thumbCache.put(key, bmp)
        bmp
    }.getOrNull()

    /** 使缩略图缓存失效（删除照片后调用） */
    fun invalidateThumb(displayName: String) {
        thumbCache.remove("th_$displayName")
    }
}
