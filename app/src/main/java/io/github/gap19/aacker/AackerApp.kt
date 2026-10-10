package io.github.gap19.aacker

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import io.github.gap19.aacker.library.Album
import io.github.gap19.aacker.library.Artwork
import io.github.gap19.aacker.library.Library
import io.github.gap19.aacker.library.LibraryReader
import io.github.gap19.aacker.playback.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AackerApp : Application() {
    val artwork by lazy { Artwork(this) }
    val library by lazy { LibraryModel(this) }
    val player by lazy { PlayerModel(this, artwork) }
}

val Context.app: AackerApp get() = applicationContext as AackerApp

sealed interface LibraryState {
    data object NeedsPermission : LibraryState
    data object Loading : LibraryState
    data class Ready(val library: Library) : LibraryState
}

/** The collection from MediaStore, re-read when files are added or removed (debounced). */
class LibraryModel(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state
    private var job: Job? = null
    private var observing = false
    private val handler = Handler(Looper.getMainLooper())
    private val reload = Runnable { load() }
    private val observer = object : ContentObserver(handler) {
        // A copy of a whole album fires many changes: read once, a few seconds after the last.
        override fun onChange(selfChange: Boolean) {
            handler.removeCallbacks(reload)
            handler.postDelayed(reload, 3_000)
        }
    }

    fun granted() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    /** Called when the app becomes visible: read once, then follow changes. */
    fun start() {
        if (!granted()) { _state.value = LibraryState.NeedsPermission; return }
        if (!observing) {
            context.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
            observing = true
        }
        if (_state.value !is LibraryState.Ready) load()
    }

    private fun load() {
        job?.cancel()
        job = scope.launch {
            val unknown = context.getString(R.string.unknown_artist)
            val library = withContext(Dispatchers.IO) { LibraryReader.read(context, unknown) }
            _state.value = LibraryState.Ready(library)
        }
    }
}

/** What the screens show about playback. Position is read on demand, never pushed. */
data class NowState(
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val albumKey: String = "",
    val isPlaying: Boolean = false,
    val durationMs: Long = 0,
    val index: Int = 0,
    val count: Int = 0,
    val shuffle: Boolean = false,
    /** Playback waits because no earbuds or headphones are connected. */
    val needsHeadphones: Boolean = false,
)

/**
 * The app's side of the media session. The controller is connected only while the app is
 * visible; with the app closed the service runs alone, as with any media app.
 */
class PlayerModel(private val context: Context, private val artwork: Artwork) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(NowState())
    val state: StateFlow<NowState> = _state
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private val pending = ArrayList<(MediaController) -> Unit>()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = update(player)
    }

    fun connect() {
        if (future != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val f = MediaController.Builder(context, token).buildAsync()
        future = f
        f.addListener({
            val c = runCatching { f.get() }.getOrNull() ?: run { future = null; return@addListener }
            controller = c
            c.addListener(listener)
            update(c)
            pending.forEach { it(c) }
            pending.clear()
        }, ContextCompat.getMainExecutor(context))
    }

    fun disconnect() {
        controller?.removeListener(listener)
        future?.let(MediaController::releaseFuture)
        future = null
        controller = null
    }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let(action) ?: run { pending += action; connect() }
    }

    private fun update(p: Player) {
        val item = p.currentMediaItem
        val m = item?.mediaMetadata
        _state.value = NowState(
            mediaId = item?.mediaId,
            title = m?.title?.toString().orEmpty(),
            artist = m?.artist?.toString().orEmpty(),
            albumKey = m?.extras?.getString(PlaybackService.ALBUM).orEmpty(),
            isPlaying = p.isPlaying,
            durationMs = p.duration.takeIf { it > 0 } ?: m?.durationMs ?: 0,
            index = p.currentMediaItemIndex,
            count = p.mediaItemCount,
            shuffle = p.shuffleModeEnabled,
            needsHeadphones = p.playWhenReady &&
                p.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT,
        )
    }

    fun position(): Long = controller?.currentPosition ?: 0

    fun playAlbum(album: Album, start: Int = 0, shuffle: Boolean = false) {
        scope.launch {
            // The cover file lets the system media controls show the album art.
            val art = artwork.file(album)?.let { android.net.Uri.fromFile(it) }
            val items = album.tracks.map { t ->
                val meta = MediaMetadata.Builder()
                    .setTitle(t.title).setArtist(t.artist).setAlbumTitle(album.title).setAlbumArtist(album.artist)
                    .setTrackNumber(t.number).setDiscNumber(t.disc).setDurationMs(t.durationMs)
                    .setIsPlayable(true).setIsBrowsable(false)
                    .setExtras(Bundle().apply { putString(PlaybackService.ALBUM, album.key) })
                if (art != null) meta.setArtworkUri(art)
                MediaItem.Builder().setMediaId(t.id.toString()).setMediaMetadata(meta.build()).build()
            }
            withController { c ->
                val first = if (shuffle) items.indices.random() else start.coerceIn(0, items.lastIndex)
                c.setMediaItems(items, first, 0)
                c.shuffleModeEnabled = shuffle
                c.prepare()
                c.play()
            }
        }
    }

    fun toggle() = withController { c ->
        if (c.isPlaying) c.pause() else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition(0)
            c.play()
        }
    }

    fun next() = withController { it.seekToNext() }
    fun previous() = withController { it.seekToPrevious() }
    fun seekTo(ms: Long) = withController { it.seekTo(ms) }
    fun setShuffle(on: Boolean) = withController { it.shuffleModeEnabled = on }

    /**
     * The Wear OS output switcher (Samsung implements the documented settings panel action),
     * else Google's media routing action, else Bluetooth settings.
     */
    fun chooseOutput(activity: Context) {
        val panel = Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
            .putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
            .putExtra("EXTRA_CONNECTION_ONLY", true)
            .putExtra("EXTRA_CLOSE_ON_CONNECT", true)
            .putExtra("android.bluetooth.devicepicker.extra.FILTER_TYPE", 1)
        val routing = Intent("com.google.android.wearable.media.routing.action.SHOW_MEDIA_OUTPUT_SWITCHER")
            .putExtra(Intent.EXTRA_PACKAGE_NAME, context.packageName)
        runCatching { activity.startActivity(panel) }
            .recoverCatching { activity.startActivity(routing) }
            .recoverCatching { activity.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
    }
}
