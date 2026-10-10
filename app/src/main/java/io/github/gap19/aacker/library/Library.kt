package io.github.gap19.aacker.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.provider.MediaStore.Audio.Media
import java.text.Collator
import java.util.Locale

/**
 * One song as the system media index knows it. The watch already reads every file's tags
 * when it is copied, so AACker never opens the audio to build its library.
 */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val disc: Int,
    val discTotal: Int,
    val number: Int,
    val durationMs: Long,
    val year: String,
    /** Folder relative to the storage root, without the trailing slash ("Music/Artist/2000 - Album"). */
    val folder: String,
    val fileName: String,
) {
    val uri: Uri get() = ContentUris.withAppendedId(Media.EXTERNAL_CONTENT_URI, id)

    /** Disc subfolders (CD1, Disc 2) belong to the album folder above them. */
    val albumFolder: String
        get() {
            val name = folder.substringAfterLast('/').lowercase(Locale.ROOT)
            val disc = listOf("cd", "disc", "disk", "disco").any { p ->
                name.startsWith(p) && name.removePrefix(p).trim(' ', '-', '_', '.').toIntOrNull() != null
            }
            return if (disc) folder.substringBeforeLast('/', "") else folder
        }

    /** Folder and album title identify an album; equal titles in different folders never merge. */
    val albumKey: String get() = "$albumFolder\u001f$album"
}

data class Album(
    val key: String,
    val title: String,
    val artist: String,
    val year: String,
    val tracks: List<Track>,
) {
    val durationMs: Long = tracks.sumOf { it.durationMs }
    val discs: Int = tracks.maxOf { maxOf(it.disc, it.discTotal) }
    /** The first track carries the album's embedded front cover. */
    val cover: Track get() = tracks.first()
}

data class Artist(val name: String, val albums: List<Album>)

class Library(val albums: List<Album>) {
    val trackCount: Int = albums.sumOf { it.tracks.size }
    val artists: List<Artist> = albums.groupBy { it.artist }
        .map { (name, list) -> Artist(name, list) }
        .sortedWith(compareBy(collator) { it.name })
    private val byKey = albums.associateBy { it.key }
    private val byArtist = artists.associateBy { it.name }

    fun album(key: String): Album? = byKey[key]
    fun artist(name: String): Artist? = byArtist[name]

    companion object {
        val EMPTY = Library(emptyList())
    }
}

/** Case- and accent-insensitive order in the user's language ("múm" next to "MONO"). */
private val collator: Collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

/** "3/11" → 3; MediaStore leaves the text as written in the tag. */
internal fun leading(value: String?): Int? = value?.trim()?.takeWhile(Char::isDigit)?.toIntOrNull()

/** "3/11" → 11. */
internal fun total(value: String?): Int? = value?.substringAfter('/', "")?.trim()?.toIntOrNull()

private fun clean(value: String?): String =
    value?.takeUnless { it == MediaStore.UNKNOWN_STRING }?.trim().orEmpty()

object LibraryReader {
    private val columns = arrayOf(
        Media._ID, Media.TITLE, Media.ARTIST, Media.ALBUM, Media.ALBUM_ARTIST,
        Media.CD_TRACK_NUMBER, Media.DISC_NUMBER, Media.TRACK, Media.DURATION, Media.YEAR,
        Media.RELATIVE_PATH, Media.DISPLAY_NAME,
    )

    /** One query for the whole collection; about 1 400 rows take a few tens of milliseconds. */
    fun read(context: Context, unknownArtist: String): Library {
        val tracks = ArrayList<Track>()
        context.contentResolver.query(
            Media.EXTERNAL_CONTENT_URI, columns, "${Media.IS_MUSIC} != 0", null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val packed = c.getInt(7)
                val name = c.getString(11).orEmpty()
                val artist = clean(c.getString(2))
                val discText = c.getString(6)
                tracks += Track(
                    id = c.getLong(0),
                    title = clean(c.getString(1)).ifEmpty { name.substringBeforeLast('.') },
                    artist = artist,
                    album = clean(c.getString(3)),
                    albumArtist = clean(c.getString(4)).ifEmpty { artist }.ifEmpty { unknownArtist },
                    disc = leading(discText) ?: (packed / 1000).takeIf { it > 0 } ?: 1,
                    discTotal = total(discText) ?: 1,
                    number = leading(c.getString(5)) ?: (packed % 1000),
                    durationMs = c.getLong(8),
                    year = c.getInt(9).takeIf { it > 0 }?.toString().orEmpty(),
                    folder = c.getString(10).orEmpty().trimEnd('/'),
                    fileName = name,
                )
            }
        }
        val albums = tracks.groupBy { it.albumKey }.map { (key, group) ->
            val ordered = group.sortedWith(compareBy<Track>({ it.disc }, { it.number }, { it.fileName }))
            val head = ordered.first()
            Album(key, head.album.ifEmpty { head.albumFolder.substringAfterLast('/') }, head.albumArtist, head.year, ordered)
        }.sortedWith(
            compareBy<Album, String>(collator) { it.artist }.thenBy { it.year }.thenBy(collator) { it.title }.thenBy { it.key },
        )
        return Library(albums)
    }
}

/** Durations as m:ss, or h:mm:ss from one hour. */
fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
    else String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60)
}
