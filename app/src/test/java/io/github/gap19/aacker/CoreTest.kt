package io.github.gap19.aacker

import io.github.gap19.aacker.library.Lyrics
import io.github.gap19.aacker.library.Track
import io.github.gap19.aacker.library.clock
import io.github.gap19.aacker.library.leading
import io.github.gap19.aacker.library.total
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile

class CoreTest {
    private fun box(type: String, body: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val size = 8 + body.size
        out.write(byteArrayOf((size ushr 24).toByte(), (size ushr 16).toByte(), (size ushr 8).toByte(), size.toByte()))
        out.write(type.toByteArray(Charsets.ISO_8859_1))
        out.write(body)
        return out.toByteArray()
    }

    private fun mp4(lyrics: String?, fullMeta: Boolean = true): File {
        val items = if (lyrics == null) box("©nam", box("data", ByteArray(8) + "Title".toByteArray()))
        else box("©lyr", box("data", ByteArray(8) + lyrics.toByteArray()))
        val hdlr = box("hdlr", ByteArray(25))
        val meta = box("meta", (if (fullMeta) ByteArray(4) else ByteArray(0)) + hdlr + box("ilst", items))
        val moov = box("moov", box("mvhd", ByteArray(100)) + box("trak", ByteArray(40)) + box("udta", meta))
        val f = File.createTempFile("lyrics", ".m4a")
        f.writeBytes(box("ftyp", "M4A ".toByteArray() + ByteArray(8)) + moov + box("mdat", ByteArray(64)))
        f.deleteOnExit()
        return f
    }

    private fun read(f: File) = RandomAccessFile(f, "r").channel.use { Lyrics.find(it) }

    @Test fun lyricsFromIlst() = assertEquals("Line one\nLine two", read(mp4("Line one\nLine two")))
    @Test fun lyricsQuickTimeMeta() = assertEquals("Hey", read(mp4("Hey", fullMeta = false)))
    @Test fun noLyrics() = assertNull(read(mp4(null)))

    @Test fun lrcTimestampsRemoved() =
        assertEquals("First\nSecond", Lyrics.clean("[ar:Someone]\n[00:12.30]First\r\n[00:15.00]Second"))

    @Test fun numbers() {
        assertEquals(3, leading("3/11")); assertEquals(11, total("3/11"))
        assertNull(total("3")); assertNull(leading(null)); assertEquals(2, leading(" 2 "))
    }

    @Test fun clocks() {
        assertEquals("4:05", clock(245_000)); assertEquals("1:02:03", clock(3_723_000)); assertEquals("0:00", clock(-5))
    }

    private fun track(folder: String, album: String = "A") =
        Track(1, "t", "x", album, "x", 1, 1, 1, 0, "", folder, "01.m4a")

    @Test fun discFoldersJoinTheirAlbum() {
        assertEquals("Music/X/2000 - A", track("Music/X/2000 - A/CD1").albumFolder)
        assertEquals("Music/X/2000 - A", track("Music/X/2000 - A/Disc 2").albumFolder)
        assertEquals("Music/X/2000 - A", track("Music/X/2000 - A").albumFolder)
        assertEquals(track("Music/X/2000 - A/CD1").albumKey, track("Music/X/2000 - A/CD2").albumKey)
    }
}

/** Optional check against a real converted file: AACKER_SAMPLE=/path/to/song.m4a. */
class SampleTest {
    @Test fun realFile() {
        val path = System.getenv("AACKER_SAMPLE") ?: return
        val text = RandomAccessFile(path, "r").channel.use { Lyrics.find(it) }
        println("AACKER_SAMPLE lyrics: ${text?.length ?: 0} chars; starts: ${text?.take(60)?.replace('\n', '/')}")
        org.junit.Assert.assertNotNull(text)
    }
}
