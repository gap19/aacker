package io.github.gap19.aacker.library

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.core.graphics.scale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Album covers from the picture embedded in each album's first track. The embedded JPEG is
 * read once and kept in the cache folder; list thumbnails are scaled once and kept beside it,
 * so scrolling never touches the audio files again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Artwork(private val context: Context) {
    enum class Size { THUMB, FULL }

    private val dir = File(context.cacheDir, "art").apply { mkdirs() }
    private val memory = object : LruCache<String, Bitmap>(MEMORY_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val accents = ConcurrentHashMap<String, Int>()
    private val missing = ConcurrentHashMap.newKeySet<String>()
    private val inflight = ConcurrentHashMap<String, CompletableDeferred<Bitmap?>>()
    // Two decoders keep a fast fling smooth without competing with playback for the CPU.
    private val io = Dispatchers.IO.limitedParallelism(2)

    fun cached(album: Album, size: Size): Bitmap? = memory.get(memoryKey(album, size))

    /** Accent derived from the cover, once its thumbnail has been loaded. */
    fun accent(album: Album): Int? = accents[album.key]

    suspend fun load(album: Album, size: Size): Bitmap? {
        val key = memoryKey(album, size)
        memory.get(key)?.let { return it }
        if (album.key in missing) return null
        val mine = CompletableDeferred<Bitmap?>()
        val running = inflight.putIfAbsent(key, mine)
        if (running != null) return running.await()
        return try {
            val bitmap = withContext(io) { runCatching { decode(album, size) }.getOrNull() }
            if (bitmap != null) memory.put(key, bitmap)
            mine.complete(bitmap)
            bitmap
        } finally {
            inflight.remove(key)
        }
    }

    /** The full cover as a file, for the media session (system media controls). */
    suspend fun file(album: Album): File? = withContext(io) { runCatching { original(album) }.getOrNull() }

    private fun hash(album: Album): String {
        val d = MessageDigest.getInstance("SHA-1").digest(album.key.toByteArray())
        return d.take(10).joinToString("") { "%02x".format(it) }
    }

    private fun memoryKey(album: Album, size: Size) = "${size.name}\u001f${album.key}"

    /** Embedded JPEG/PNG bytes written once; null when the album has no embedded picture. */
    private fun original(album: Album): File? {
        val h = hash(album)
        val file = File(dir, "$h.img")
        if (file.isFile && file.length() > 0) return file
        val none = File(dir, "$h.none")
        if (none.exists()) { missing += album.key; return null }
        val bytes = MediaMetadataRetriever().use { r ->
            r.setDataSource(context, album.cover.uri)
            r.embeddedPicture
        }
        if (bytes == null || bytes.isEmpty()) {
            none.createNewFile(); missing += album.key; return null
        }
        val tmp = File(dir, "$h.tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(file)
        return file
    }

    private fun decode(album: Album, size: Size): Bitmap? {
        val h = hash(album)
        if (size == Size.THUMB) {
            val thumb = File(dir, "$h-$THUMB_PX.jpg")
            val bitmap = if (thumb.isFile) BitmapFactory.decodeFile(thumb.path) else {
                val src = original(album) ?: return null
                val scaled = scaled(src, THUMB_PX) ?: return null
                val tmp = File(dir, "$h-$THUMB_PX.tmp")
                tmp.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                tmp.renameTo(thumb)
                scaled
            }
            if (bitmap != null && !accents.containsKey(album.key)) accents[album.key] = accentOf(bitmap)
            return bitmap
        }
        val src = original(album) ?: return null
        return scaled(src, FULL_PX)
    }

    /** Decoded at the nearest power of two above [px], then scaled down smoothly to [px]. */
    private fun scaled(file: File, px: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val side = minOf(bounds.outWidth, bounds.outHeight)
        if (side <= 0) return null
        var sample = 1
        while (side / (sample * 2) >= px) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val w = decoded.width; val hgt = decoded.height
        if (maxOf(w, hgt) <= px) return decoded
        val f = px.toFloat() / maxOf(w, hgt)
        return decoded.scale((w * f).toInt().coerceAtLeast(1), (hgt * f).toInt().coerceAtLeast(1))
            .also { if (it !== decoded) decoded.recycle() }
    }

    companion object {
        /** Thumbnails: 40 dp rows at 340 dpi are about 85 px; 128 px stays sharp at larger sizes. */
        const val THUMB_PX = 128
        /** The 480 px round screen. */
        const val FULL_PX = 480
        private const val MEMORY_BYTES = 12 * 1024 * 1024
        const val DEFAULT_ACCENT = 0xFF8FD3C9.toInt()

        /**
         * The cover's dominant colourful hue, lifted to a soft, readable tone for black backgrounds.
         * Grey covers keep the app accent.
         */
        fun accentOf(bitmap: Bitmap): Int {
            val w = bitmap.width; val h = bitmap.height
            val px = IntArray(w * h)
            bitmap.getPixels(px, 0, w, 0, 0, w, h)
            val weight = FloatArray(36)
            val hsv = FloatArray(3)
            var total = 0f
            for (i in px.indices step 2) {
                Color.colorToHSV(px[i], hsv)
                if (hsv[2] < 0.18f) continue
                val score = hsv[1] * hsv[1] * hsv[2]
                weight[(hsv[0] / 10f).toInt().coerceIn(0, 35)] += score
                total += score
            }
            if (total < px.size * 0.004f) return DEFAULT_ACCENT
            val best = weight.indices.maxBy { weight[it] + 0.5f * (weight[(it + 35) % 36] + weight[(it + 1) % 36]) }
            var sx = 0f; var sy = 0f; var sat = 0f; var n = 0f
            for (i in px.indices step 2) {
                Color.colorToHSV(px[i], hsv)
                if (hsv[2] < 0.18f) continue
                val bin = (hsv[0] / 10f).toInt().coerceIn(0, 35)
                if (bin != best && bin != (best + 1) % 36 && bin != (best + 35) % 36) continue
                val s = hsv[1] * hsv[1] * hsv[2]
                val a = Math.toRadians(hsv[0].toDouble())
                sx += (Math.cos(a) * s).toFloat(); sy += (Math.sin(a) * s).toFloat(); sat += hsv[1] * s; n += s
            }
            val hue = ((Math.toDegrees(Math.atan2(sy.toDouble(), sx.toDouble())) + 360) % 360).toFloat()
            val s = (sat / n).coerceIn(0.28f, 0.55f)
            return Color.HSVToColor(floatArrayOf(hue, s, 0.92f))
        }
    }
}
