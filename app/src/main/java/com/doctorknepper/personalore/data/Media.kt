package com.doctorknepper.personalore.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import com.doctorknepper.personalore.util.formatDuration
import java.io.ByteArrayOutputStream
import java.time.format.DateTimeFormatter

/** Photo import: re-encoding to a fresh JPEG drops all EXIF metadata, including GPS location. */
object PhotoImport {
    private const val MAX_SIDE = 2560

    fun process(context: Context, uri: Uri): ByteArray {
        val raw = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val orientation = runCatching {
            ExifInterface(raw.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("Unsupported image")

        val scale = MAX_SIDE.toFloat() / maxOf(bmp.width, bmp.height)
        val m = Matrix()
        if (scale < 1f) m.postScale(scale, scale)
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        }
        if (!m.isIdentity) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        return ByteArrayOutputStream().use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 88, out); out.toByteArray() }
    }
}

/** Decrypted, downsized bitmaps kept in memory while the journal is unlocked. */
object MediaCache {
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun bitmap(name: String, maxSidePx: Int): Bitmap? {
        val key = "$name@$maxSidePx"
        cache.get(key)?.let { return it }
        val bytes = Store.readMedia(name) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSidePx) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        cache.put(key, bmp)
        return bmp
    }

    fun remove(name: String) = cache.snapshot().keys.filter { it.startsWith("$name@") }.forEach { cache.remove(it) }

    fun clear() = cache.evictAll()
}

object Markdown {
    private val dayFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")

    fun slug(e: Entry): String =
        e.title.ifBlank { e.promptText ?: e.body.take(40) }.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40).ifBlank { "entry" }

    fun entry(e: Entry, mediaPrefix: String): String = buildString {
        append("## ").append(e.title.ifBlank { e.date.format(dayFmt) }).append("\n\n")
        append("*").append(e.date.format(dayFmt)).append("*")
        moodOf(e.mood)?.let { append(" · Mood: ").append(it.emoji).append(' ').append(it.label) }
        append("\n\n")
        if (e.tags.isNotEmpty()) append("Tags: ").append(e.tags.joinToString(" ") { "#$it" }).append("\n\n")
        e.promptText?.let { append("> **Prompt:** ").append(it).append("\n\n") }
        if (e.body.isNotBlank()) append(e.body.trim()).append("\n\n")
        e.photos.forEachIndexed { i, p -> append("![Photo ${i + 1}](").append(mediaPrefix).append(p).append(")\n\n") }
        e.audio.forEachIndexed { i, a ->
            append("🎙 [Voice note ${i + 1}").append(if (a.note.isNotBlank()) ": ${a.note}" else "")
                .append(" (").append(formatDuration(a.durationMs)).append(")](").append(mediaPrefix).append(a.file).append(")\n\n")
        }
    }
}
