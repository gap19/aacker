package io.github.gap19.aacker.library

import android.content.Context
import android.net.Uri
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Lyrics stored in the MP4 "©lyr" tag (written from the FLAC LYRICS field). Only the
 * metadata boxes are read, a few kilobytes at the start of each file; the audio is never touched.
 */
object Lyrics {
    private const val MOOV = 0x6d6f6f76
    private const val UDTA = 0x75647461
    private const val META = 0x6d657461
    private const val ILST = 0x696c7374
    private const val HDLR = 0x68646c72
    private const val LYR = 0xa96c7972.toInt()
    private const val DATA = 0x64617461
    private const val MAX_TEXT = 256 * 1024

    fun read(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            FileInputStream(fd.fileDescriptor).channel.use { find(it) }
        }
    }.getOrNull()?.let(::clean)?.takeIf { it.isNotBlank() }

    private class Box(val type: Int, val body: Long, val end: Long)

    private fun child(ch: FileChannel, from: Long, to: Long, type: Int): Box? {
        val head = ByteBuffer.allocate(16)
        var pos = from
        while (pos + 8 <= to) {
            head.clear(); head.limit(8)
            if (ch.read(head, pos) < 8) return null
            head.flip()
            var size = head.int.toLong() and 0xffffffffL
            val t = head.int
            var header = 8L
            if (size == 1L) {
                head.clear(); head.limit(8)
                if (ch.read(head, pos + 8) < 8) return null
                head.flip(); size = head.long; header = 16
            } else if (size == 0L) size = to - pos
            if (size < header) return null
            if (t == type) return Box(t, pos + header, minOf(pos + size, to))
            pos += size
        }
        return null
    }

    internal fun find(ch: FileChannel): String? {
        val moov = child(ch, 0, ch.size(), MOOV) ?: return null
        val udta = child(ch, moov.body, moov.end, UDTA) ?: return null
        val meta = child(ch, udta.body, udta.end, META) ?: return null
        // iTunes writes "meta" as a full box (4 bytes of version and flags); QuickTime does not.
        val peek = ByteBuffer.allocate(8)
        ch.read(peek, meta.body); peek.flip()
        val offset = if (peek.remaining() == 8 && peek.getInt(4) == HDLR) 0 else 4
        val ilst = child(ch, meta.body + offset, meta.end, ILST) ?: return null
        val lyr = child(ch, ilst.body, ilst.end, LYR) ?: return null
        val data = child(ch, lyr.body, lyr.end, DATA) ?: return null
        // Type indicator and locale precede the UTF-8 text.
        val start = data.body + 8
        val length = (data.end - start).coerceIn(0, MAX_TEXT.toLong()).toInt()
        val text = ByteBuffer.allocate(length)
        while (text.hasRemaining() && ch.read(text, start + text.position()) > 0) Unit
        return String(text.array(), 0, text.position(), Charsets.UTF_8)
    }

    private val timestamp = Regex("""\[\d{1,3}:\d{2}(?:[.:]\d{1,3})?]""")
    private val header = Regex("""^\[[a-z]{2,6}:.*]$""", RegexOption.IGNORE_CASE)

    /** Plain text; timestamps of LRC-style lyrics are removed, never invented. */
    internal fun clean(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n')
        .lines()
        .filterNot { header.matches(it.trim()) }
        .joinToString("\n") { it.replace(timestamp, "").trimEnd() }
        .trim()
}
