package io.github.gap19.aacker.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.DefaultAudioTrackBufferSizeProvider
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.DefaultMediaCodecAdapterFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import io.github.gap19.aacker.MainActivity
import io.github.gap19.aacker.R
import io.github.gap19.aacker.data.Durable
import io.github.gap19.aacker.data.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors

/**
 * Audio lives here, so playback continues with the screen off or the app closed.
 *
 * Built for the least work per second of music: only an AAC audio renderer and the MP4
 * extractor exist, the decoder output may be offloaded to the audio DSP, the loader reads
 * minutes ahead and then sleeps, and the playback loop wakes only when it has work.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class, ExperimentalApi::class])
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private lateinit var handler: Handler
    private lateinit var store: QueueStore
    /** Consecutive unplayable items skipped, so a queue of missing files cannot loop forever. */
    private var failures = 0
    private val saveSoon = Runnable { session?.player?.let(store::save) }
    private val settingsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Settings.SAVER) (session?.player as? ExoPlayer)?.let(::applyOffload)
    }

    /**
     * Compressed AAC goes to the audio DSP when the route allows it. By default only with
     * gapless joins (true album playback); the battery saver accepts the route's plain offload.
     */
    private fun applyOffload(player: ExoPlayer) {
        val saver = Settings.saver(this)
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setAudioOffloadPreferences(
                AudioOffloadPreferences.Builder()
                    .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                    .setIsGaplessSupportRequired(!saver)
                    .build(),
            )
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        handler = Handler(mainLooper)
        store = QueueStore(this)
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).putExtra(MainActivity.OPEN_NOW, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // One UI Watch already lists every media session on the watch face; no ongoing activity of our own.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) },
        )

        val player = ExoPlayer.Builder(this, audioOnly(this), localMp4(this))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    // Read up to five minutes ahead, then let the storage and CPU sleep until 30 s remain.
                    .setBufferDurationsMs(30_000, 300_000, 500, 1_000)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build(),
            )
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            // Never play through the watch speaker: wait for earbuds instead.
            .setSuppressPlaybackOnUnsuitableOutput(true)
            // The playback loop runs only when a renderer needs it, not every 10 ms.
            .experimentalSetDynamicSchedulingEnabled(true)
            .build()
        applyOffload(player)
        Settings.prefs(this).registerOnSharedPreferenceChangeListener(settingsListener)
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e(TAG, "Playback error", error)
                // A missing or undecodable file: move on instead of silencing the queue.
                if (failures < player.mediaItemCount && player.hasNextMediaItem()) {
                    failures++
                    val resume = player.playWhenReady
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.playWhenReady = resume
                }
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) = save()
            override fun onShuffleModeEnabledChanged(enabled: Boolean) = save()
            override fun onRepeatModeChanged(mode: Int) = save()

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) { failures = 0; logOffload() } else save(now = true)
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) save()
            }
        })
        player.addAudioOffloadListener(object : ExoPlayer.AudioOffloadListener {
            override fun onOffloadedPlayback(offloaded: Boolean) {
                android.util.Log.i(TAG, "Offloaded playback: $offloaded")
            }
        })
        store.restore(player)

        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(object : MediaSession.Callback {
                // Controllers send only the media id (the MediaStore row); the URI is derived here.
                override fun onAddMediaItems(
                    mediaSession: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>,
                ) = Futures.immediateFuture(mediaItems.map(::playable).toMutableList())

                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    val result = MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                    if (controller.packageName != packageName) {
                        // System media controls and earbuds may control playback, not load files.
                        result.setAvailablePlayerCommands(
                            MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                                .removeAll(Player.COMMAND_SET_MEDIA_ITEM, Player.COMMAND_CHANGE_MEDIA_ITEMS).build(),
                        )
                    }
                    return result.build()
                }
            })
            .build()
    }

    /** Coalesce bursts (a new queue fires a transition and a timeline change) into one write. */
    private fun save(now: Boolean = false) {
        handler.removeCallbacks(saveSoon)
        if (now) saveSoon.run() else handler.postDelayed(saveSoon, 500)
    }

    /** What the platform offers for AAC on the current route, for the log. */
    private fun logOffload() {
        val format = android.media.AudioFormat.Builder().setEncoding(android.media.AudioFormat.ENCODING_AAC_LC)
            .setSampleRate(44_100).setChannelMask(android.media.AudioFormat.CHANNEL_OUT_STEREO).build()
        val attrs = android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val support = when (android.media.AudioManager.getPlaybackOffloadSupport(format, attrs)) {
            android.media.AudioManager.PLAYBACK_OFFLOAD_GAPLESS_SUPPORTED -> "gapless"
            android.media.AudioManager.PLAYBACK_OFFLOAD_SUPPORTED -> "without gapless"
            else -> "none"
        }
        android.util.Log.i(TAG, "AAC offload on this route: $support")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            player?.let { store.save(it, sync = true) }
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        Settings.prefs(this).unregisterOnSharedPreferenceChangeListener(settingsListener)
        session?.run {
            store.save(player, sync = true)
            player.release()
            release()
        }
        session = null
        store.close()
        super.onDestroy()
    }

    companion object {
        const val TAG = "AACker"
        /** Extras key of the album an item belongs to. */
        const val ALBUM = "album"

        fun uri(mediaId: String): Uri? = mediaId.toLongOrNull()?.let {
            android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it)
        }

        fun playable(item: MediaItem): MediaItem =
            if (item.localConfiguration != null) item else item.buildUpon().setUri(uri(item.mediaId)).build()

        /** Only the AAC decoder: no video, text, metadata or image renderers are created. */
        private fun audioOnly(context: Context) = RenderersFactory { handler, _, audio, _, _ ->
            arrayOf(
                MediaCodecAudioRenderer(
                    context,
                    // Synchronous codec calls on the playback thread: no extra callback thread
                    // waking for every 23 ms AAC frame.
                    DefaultMediaCodecAdapterFactory(context).forceDisableAsynchronous(),
                    MediaCodecSelector.DEFAULT, false, handler, audio,
                    DefaultAudioSink.Builder(context)
                        // Two seconds of PCM in the AudioTrack (default: under one): decoding happens
                        // in bursts and the CPU sleeps between them.
                        .setAudioTrackBufferSizeProvider(
                            DefaultAudioTrackBufferSizeProvider.Builder()
                                .setMinPcmBufferDurationUs(1_000_000)
                                .setTargetPcmBufferDurationUs(2_000_000)
                                .setMaxPcmBufferDurationUs(2_000_000)
                                .setPcmBufferMultiplicationFactor(8)
                                .build(),
                        )
                        .build(),
                ),
            )
        }

        /** MediaStore content only, parsed by the MP4 extractor alone, without its cover art. */
        private fun localMp4(context: Context): ProgressiveMediaSource.Factory {
            val data = DataSource.Factory { ContentDataSource(context) }
            val extractors = ExtractorsFactory {
                arrayOf(Mp4Extractor(SubtitleParser.Factory.UNSUPPORTED, Mp4Extractor.FLAG_DISABLE_ARTWORK_METADATA))
            }
            return ProgressiveMediaSource.Factory(data, extractors)
        }
    }
}

/**
 * Queue, index, position and modes survive restarts; restoring never starts sound.
 * Snapshots are taken on the main thread (stored strings only) and written on one background thread.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class QueueStore(context: Context) {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(context.mainLooper)
    private val file = File(context.filesDir, "queue.json")

    private class Snapshot(val items: List<JSONObject>, val index: Int, val position: Long, val shuffle: Boolean, val repeat: Int)

    private fun snapshot(player: Player): Snapshot {
        val items = List(player.mediaItemCount) { i ->
            val item = player.getMediaItemAt(i)
            val m = item.mediaMetadata
            JSONObject().put("id", item.mediaId)
                .put("title", m.title?.toString().orEmpty())
                .put("artist", m.artist?.toString().orEmpty())
                .put("album", m.albumTitle?.toString().orEmpty())
                .put("albumArtist", m.albumArtist?.toString().orEmpty())
                .put("number", m.trackNumber ?: 0)
                .put("disc", m.discNumber ?: 1)
                .put("duration", m.durationMs ?: 0)
                .put("art", m.artworkUri?.toString().orEmpty())
                .put("key", m.extras?.getString(PlaybackService.ALBUM).orEmpty())
        }
        return Snapshot(items, player.currentMediaItemIndex, player.currentPosition.coerceAtLeast(0), player.shuffleModeEnabled, player.repeatMode)
    }

    private fun write(s: Snapshot) {
        val o = JSONObject().put("version", 1).put("items", JSONArray(s.items)).put("index", s.index)
            .put("position", s.position).put("shuffle", s.shuffle).put("repeat", s.repeat)
        runCatching { Durable.write(file, o.toString()) }
            .onFailure { android.util.Log.e(PlaybackService.TAG, "Queue not saved", it) }
    }

    fun save(player: Player, sync: Boolean = false) {
        val s = runCatching { snapshot(player) }.getOrNull() ?: return
        if (sync) write(s) else io.execute { write(s) }
    }

    fun restore(player: ExoPlayer) {
        io.execute {
            val o = runCatching { JSONObject(Durable.read(file) ?: return@execute) }.getOrNull() ?: return@execute
            val a = o.optJSONArray("items") ?: return@execute
            val items = List(a.length()) { item(a.getJSONObject(it)) }
            main.post {
                // The app may already have started something new while the file was read.
                if (items.isEmpty() || player.mediaItemCount > 0) return@post
                player.setMediaItems(items, o.optInt("index").coerceIn(0, items.size - 1), o.optLong("position"))
                player.shuffleModeEnabled = o.optBoolean("shuffle")
                player.repeatMode = o.optInt("repeat")
                player.playWhenReady = false
                player.prepare()
            }
        }
    }

    fun close() = io.shutdown()

    companion object {
        fun item(e: JSONObject): MediaItem {
            val meta = MediaMetadata.Builder()
                .setTitle(e.optString("title"))
                .setArtist(e.optString("artist"))
                .setAlbumTitle(e.optString("album"))
                .setAlbumArtist(e.optString("albumArtist"))
                .setTrackNumber(e.optInt("number"))
                .setDiscNumber(e.optInt("disc", 1))
                .setDurationMs(e.optLong("duration"))
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setExtras(Bundle().apply { putString(PlaybackService.ALBUM, e.optString("key")) })
            e.optString("art").takeIf { it.isNotEmpty() }?.let { meta.setArtworkUri(it.toUri()) }
            val id = e.optString("id")
            return MediaItem.Builder().setMediaId(id).setUri(PlaybackService.uri(id)).setMediaMetadata(meta.build()).build()
        }
    }
}
