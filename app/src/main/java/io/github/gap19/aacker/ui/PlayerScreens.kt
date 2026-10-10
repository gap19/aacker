package io.github.gap19.aacker.ui

import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.transformedHeight
import io.github.gap19.aacker.NowState
import io.github.gap19.aacker.PlayerModel
import io.github.gap19.aacker.R
import io.github.gap19.aacker.library.Album
import io.github.gap19.aacker.library.Artwork
import io.github.gap19.aacker.library.Lyrics
import io.github.gap19.aacker.library.clock
import io.github.gap19.aacker.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * The player: cover behind, progress around the round edge, controls in the middle and the
 * rotating bezel for volume. While playing the screen redraws once per second and only the ring
 * changes; nothing animates continuously and nothing updates with the screen off.
 */
@Composable
fun NowPlayingScreen(
    now: NowState,
    album: Album?,
    player: PlayerModel,
    onLyrics: () -> Unit,
    onAlbum: () -> Unit,
) {
    val context = LocalContext.current
    val accent = rememberAccent(album)
    AackerTheme(accent = accent) {
        val position = remember { mutableLongStateOf(player.position()) }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(now.mediaId, now.isPlaying) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                do {
                    val p = player.position()
                    position.longValue = p
                    // Wake on the next whole second of the song, so the clock text ticks evenly.
                    delay(1000 - p % 1000 + 5)
                } while (now.isPlaying)
            }
        }
        val volume = rememberVolume(context)
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        LaunchedEffect(now.needsHeadphones) { if (now.needsHeadphones) player.chooseOutput(context) }

        ScreenScaffold {
            Box(
                Modifier
                    .fillMaxSize()
                    .onRotaryScrollEvent { volume.scroll(it.verticalScrollPixels); true }
                    .focusRequester(focus)
                    .focusable(),
                contentAlignment = Alignment.Center,
            ) {
                if (album != null) {
                    Cover(album, Artwork.Size.FULL, Modifier.fillMaxSize(), RectangleShape)
                }
                Scrim()
                ProgressRing(
                    fraction = { if (now.durationMs > 0) position.longValue.toFloat() / now.durationMs else 0f },
                    color = MaterialTheme.colorScheme.primary,
                )
                VolumeArc(volume)
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 30.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        now.title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        now.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = player::previous) {
                            Icon(painterResource(R.drawable.ic_previous), stringResource(R.string.previous))
                        }
                        FilledIconButton(onClick = player::toggle, modifier = Modifier.size(IconButtonDefaults.LargeButtonSize)) {
                            Icon(
                                painterResource(if (now.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                                stringResource(if (now.isPlaying) R.string.pause else R.string.play),
                                modifier = Modifier.size(IconButtonDefaults.LargeIconSize),
                            )
                        }
                        IconButton(onClick = player::next) {
                            Icon(painterResource(R.drawable.ic_next), stringResource(R.string.next))
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${clock(position.longValue)} / ${clock(now.durationMs)}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (now.needsHeadphones) {
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { player.chooseOutput(context) },
                            colors = ButtonDefaults.filledTonalButtonColors(),
                            icon = { Icon(painterResource(R.drawable.ic_headphones), null) },
                            label = { Text(stringResource(R.string.headphones), maxLines = 1) },
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = onLyrics, modifier = Modifier.size(IconButtonDefaults.SmallButtonSize)) {
                                Icon(
                                    painterResource(R.drawable.ic_lyrics), stringResource(R.string.lyrics),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(IconButtonDefaults.SmallIconSize),
                                )
                            }
                            IconButton(onClick = onAlbum, enabled = album != null, modifier = Modifier.size(IconButtonDefaults.SmallButtonSize)) {
                                Icon(
                                    painterResource(R.drawable.ic_album), stringResource(R.string.open_album),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(IconButtonDefaults.SmallIconSize),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Darkens the cover towards the edge so text and the ring stay readable on any art. */
@Composable
private fun Scrim() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                0f to Color.Black.copy(alpha = 0.55f),
                0.7f to Color.Black.copy(alpha = 0.72f),
                1f to Color.Black.copy(alpha = 0.92f),
                center = center, radius = size.minDimension / 2,
            ),
        )
    }
}

/** Song progress along the round edge; read in the draw phase, so ticks only redraw. */
@Composable
private fun ProgressRing(fraction: () -> Float, color: Color) {
    Box(
        Modifier.fillMaxSize().drawWithCache {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2 + 2.dp.toPx()
            val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
            val topLeft = Offset(inset, inset)
            val track = color.copy(alpha = 0.18f)
            onDrawBehind {
                drawArc(track, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                val f = fraction().coerceIn(0f, 1f)
                if (f > 0f) drawArc(color, -90f, 360f * f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
        },
    )
}

/** Bezel volume: system music volume in steps, shown briefly on the left edge. */
class Volume(private val audio: AudioManager) {
    var level by mutableFloatStateOf(0f)
    var visible by mutableStateOf(false)
    var touched by mutableIntStateOf(0)
    private var pending = 0f

    fun scroll(pixels: Float) {
        pending += pixels
        val step = 36f
        while (abs(pending) >= step) {
            val up = pending > 0
            pending -= if (up) step else -step
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0,
            )
        }
        refresh()
        visible = true
        touched++
    }

    fun refresh() {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        level = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }
}

@Composable
private fun rememberVolume(context: Context): Volume =
    remember { Volume(context.getSystemService(AudioManager::class.java)).apply { refresh() } }

@Composable
private fun VolumeArc(volume: Volume) {
    LaunchedEffect(volume.touched) {
        if (volume.visible) { delay(1_500); volume.visible = false }
    }
    val alpha by animateFloatAsState(if (volume.visible) 1f else 0f, tween(250), label = "volume")
    if (alpha == 0f) return
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxSize()) {
        val stroke = 6.dp.toPx()
        val inset = 12.dp.toPx()
        val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
        val topLeft = Offset(inset, inset)
        // A 60° arc on the left edge, filled from the bottom.
        drawArc(color.copy(alpha = 0.22f * alpha), 150f, 60f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        drawArc(color.copy(alpha = alpha), 210f, -60f * volume.level, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun LyricsScreen(now: NowState, album: Album?) {
    val context = LocalContext.current
    val text by produceState<String?>(null, now.mediaId) {
        val uri = now.mediaId?.let(PlaybackService::uri)
        value = if (uri == null) "" else withContext(Dispatchers.IO) { Lyrics.read(context, uri) }.orEmpty()
    }
    AackerTheme(accent = rememberAccent(album)) {
        val paragraphs = remember(text) { text.orEmpty().split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() } }
        ListScreen { spec ->
            item {
                ListHeader(Modifier.fillMaxWidth().transformedHeight(this, spec), transformation = SurfaceTransformation(spec)) {
                    Text(now.title, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (text != null && paragraphs.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.no_lyrics), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).padding(horizontal = 12.dp),
                    )
                }
            }
            items(paragraphs) { p ->
                Text(
                    p, style = MaterialTheme.typography.bodyMedium, fontSize = 15.sp, lineHeight = 20.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, spec).padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
    }
}
