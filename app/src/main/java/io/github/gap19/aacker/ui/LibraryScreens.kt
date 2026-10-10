package io.github.gap19.aacker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListSubHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import io.github.gap19.aacker.NowState
import io.github.gap19.aacker.R
import io.github.gap19.aacker.data.Settings
import io.github.gap19.aacker.library.Album
import io.github.gap19.aacker.library.Artist
import io.github.gap19.aacker.library.Artwork
import io.github.gap19.aacker.library.Library
import io.github.gap19.aacker.library.clock

private val Thumb = 40.dp
private val ThumbShape = RoundedCornerShape(10.dp)

@Composable
fun HomeScreen(
    library: Library,
    now: NowState,
    nowAlbum: Album?,
    onNowPlaying: () -> Unit,
    onArtists: () -> Unit,
    onAlbums: () -> Unit,
    onRandom: () -> Unit,
    onSettings: () -> Unit,
) {
    ListScreen(
        edgeButton = {
            EdgeButton(onClick = onRandom, buttonSize = androidx.wear.compose.material3.EdgeButtonSize.Medium) {
                Icon(painterResource(R.drawable.ic_random), contentDescription = stringResource(R.string.random_album))
            }
        },
    ) { spec ->
        item {
            ListHeader(Modifier.fillMaxWidth().transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                Text("AACker", fontFamily = Gothic, fontSize = 30.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (now.mediaId != null && nowAlbum != null) {
            item {
                Button(
                    onClick = onNowPlaying,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                    transformation = SurfaceTransformation(spec),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = { Cover(nowAlbum, Artwork.Size.THUMB, Modifier.size(Thumb), ThumbShape) },
                    label = { Text(now.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    secondaryLabel = {
                        Text(stringResource(R.string.now_playing), maxLines = 1, color = MaterialTheme.colorScheme.primary)
                    },
                )
            }
        }
        item {
            Button(
                onClick = onArtists,
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
                colors = ButtonDefaults.filledTonalButtonColors(),
                icon = { Icon(painterResource(R.drawable.ic_artist), contentDescription = null) },
                label = { Text(stringResource(R.string.artists)) },
                secondaryLabel = { Text(library.artists.size.toString()) },
            )
        }
        item {
            Button(
                onClick = onAlbums,
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
                colors = ButtonDefaults.filledTonalButtonColors(),
                icon = { Icon(painterResource(R.drawable.ic_album), contentDescription = null) },
                label = { Text(stringResource(R.string.albums)) },
                secondaryLabel = { Text(rememberPlural(R.plurals.tracks_count, library.trackCount)) },
            )
        }
        item {
            Button(
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
                colors = ButtonDefaults.childButtonColors(),
                label = { Text(stringResource(R.string.settings)) },
            )
        }
    }
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    var saver by remember { mutableStateOf(Settings.saver(context)) }
    ListScreen { spec ->
        item {
            ListHeader(Modifier.fillMaxWidth().transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                Text(stringResource(R.string.settings))
            }
        }
        item {
            SwitchButton(
                checked = saver,
                onCheckedChange = { saver = it; Settings.setSaver(context, it) },
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
                label = { Text(stringResource(R.string.battery_saver)) },
            )
        }
        item {
            Text(
                stringResource(R.string.battery_saver_text), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).padding(horizontal = 10.dp),
            )
        }
    }
}

@Composable
fun ArtistsScreen(artists: List<Artist>, onArtist: (Artist) -> Unit) {
    ListScreen { spec ->
        item {
            ListHeader(Modifier.fillMaxWidth().transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                Text(stringResource(R.string.artists))
            }
        }
        items(artists, key = { it.name }) { artist ->
            Button(
                onClick = { onArtist(artist) },
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
                colors = ButtonDefaults.filledTonalButtonColors(),
                icon = { Cover(artist.albums.first(), Artwork.Size.THUMB, Modifier.size(Thumb), CircleShape) },
                label = { Text(artist.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = { Text(rememberPlural(R.plurals.albums_count, artist.albums.size), maxLines = 1) },
            )
        }
    }
}

/** Albums of one artist (with the artist as header) or the whole collection. */
@Composable
fun AlbumsScreen(title: String, albums: List<Album>, showArtist: Boolean, onAlbum: (Album) -> Unit) {
    ListScreen { spec ->
        item {
            ListHeader(Modifier.fillMaxWidth().transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            }
        }
        items(albums, key = { it.key }) { album ->
            Button(
                onClick = { onAlbum(album) },
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
                colors = ButtonDefaults.filledTonalButtonColors(),
                icon = { Cover(album, Artwork.Size.THUMB, Modifier.size(Thumb), ThumbShape) },
                label = { Text(album.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = {
                    val line = if (showArtist) listOf(album.artist, album.year) else listOf(album.year)
                    Text(line.filter { it.isNotEmpty() }.joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        }
    }
}

@Composable
fun AlbumScreen(album: Album, now: NowState, onPlay: (start: Int, shuffle: Boolean) -> Unit) {
    AackerTheme(accent = rememberAccent(album)) {
        val multiDisc = album.discs > 1
        ListScreen { spec ->
            item {
                Column(
                    Modifier.fillMaxWidth().transformedHeight(this, spec).padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Cover(album, Artwork.Size.FULL, Modifier.size(104.dp), RoundedCornerShape(16.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        album.title, fontFamily = Gothic, fontSize = 24.sp, lineHeight = 26.sp,
                        textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Text(
                        album.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOf(album.year, rememberPlural(R.plurals.tracks_count, album.tracks.size), clock(album.durationMs))
                            .filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = { onPlay(0, true) }) {
                            Icon(painterResource(R.drawable.ic_shuffle), contentDescription = stringResource(R.string.shuffle))
                        }
                        FilledIconButton(
                            onClick = { onPlay(0, false) },
                            modifier = Modifier.size(IconButtonDefaults.LargeButtonSize),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_play), contentDescription = stringResource(R.string.play),
                                modifier = Modifier.size(IconButtonDefaults.LargeIconSize),
                            )
                        }
                        Spacer(Modifier.width(IconButtonDefaults.DefaultButtonSize))
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
            var disc = 0
            album.tracks.forEachIndexed { i, t ->
                if (multiDisc && t.disc != disc) {
                    disc = t.disc
                    val d = disc
                    item(key = "disc$d") {
                        ListSubHeader(Modifier.fillMaxWidth().transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                            Text(stringResource(R.string.disc, d))
                        }
                    }
                }
                item(key = t.id) {
                    val playing = now.mediaId == t.id.toString()
                    Button(
                        onClick = { onPlay(i, false) },
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                        colors = if (playing) ButtonDefaults.filledVariantButtonColors() else ButtonDefaults.filledTonalButtonColors(),
                        icon = {
                            if (playing) Icon(painterResource(R.drawable.ic_equalizer), contentDescription = stringResource(R.string.now_playing))
                            else Text(
                                t.number.takeIf { it > 0 }?.toString().orEmpty(), style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center, modifier = Modifier.width(22.dp),
                            )
                        },
                        label = { Text(t.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        secondaryLabel = { Text(clock(t.durationMs), maxLines = 1) },
                    )
                }
            }
        }
    }
}
