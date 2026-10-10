package io.github.gap19.aacker.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import io.github.gap19.aacker.LibraryState
import io.github.gap19.aacker.R
import io.github.gap19.aacker.app

private object Route {
    const val HOME = "home"
    const val ARTISTS = "artists"
    const val ALBUMS = "albums"
    const val ARTIST = "artist?name={name}"
    const val ALBUM = "album?key={key}"
    const val PLAYER = "player"
    const val LYRICS = "lyrics"
    const val SETTINGS = "settings"
    fun artist(name: String) = "artist?name=${Uri.encode(name)}"
    fun album(key: String) = "album?key=${Uri.encode(key)}"
}

@Composable
fun Root(openPlayer: Int) {
    val app = LocalContext.current.app
    val libraryState by app.library.state.collectAsStateWithLifecycle()
    AackerTheme {
        AppScaffold(timeText = { TimeText() }) {
            when (val s = libraryState) {
                LibraryState.NeedsPermission -> PermissionScreen { app.library.start() }
                LibraryState.Loading -> Message(stringResource(R.string.loading), null)
                is LibraryState.Ready ->
                    if (s.library.albums.isEmpty()) Message(stringResource(R.string.empty_title), stringResource(R.string.empty_text))
                    else Navigation(s.library, openPlayer)
            }
        }
    }
}

@Composable
private fun Navigation(library: io.github.gap19.aacker.library.Library, openPlayer: Int) {
    val app = LocalContext.current.app
    val now by app.player.state.collectAsStateWithLifecycle()
    val nav = rememberSwipeDismissableNavController()
    val nowAlbum = remember(now.albumKey, library) { library.album(now.albumKey) }
    // Opened from the watch face or the media notification: go straight to the player.
    LaunchedEffect(openPlayer) {
        if (openPlayer > 0 && nav.currentDestination?.route != Route.PLAYER) nav.navigate(Route.PLAYER) { launchSingleTop = true }
    }
    SwipeDismissableNavHost(navController = nav, startDestination = Route.HOME) {
        composable(Route.HOME) {
            HomeScreen(
                library, now, nowAlbum,
                onNowPlaying = { nav.navigate(Route.PLAYER) { launchSingleTop = true } },
                onArtists = { nav.navigate(Route.ARTISTS) },
                onAlbums = { nav.navigate(Route.ALBUMS) },
                onRandom = {
                    app.player.playAlbum(library.albums.random())
                    nav.navigate(Route.PLAYER) { launchSingleTop = true }
                },
                onSettings = { nav.navigate(Route.SETTINGS) },
            )
        }
        composable(Route.SETTINGS) { SettingsScreen() }
        composable(Route.ARTISTS) {
            ArtistsScreen(library.artists) { artist ->
                // An artist with one album opens it directly.
                if (artist.albums.size == 1) nav.navigate(Route.album(artist.albums.first().key))
                else nav.navigate(Route.artist(artist.name))
            }
        }
        composable(Route.ARTIST) { entry ->
            val artist = library.artist(entry.arguments?.getString("name").orEmpty())
            if (artist != null) AlbumsScreen(artist.name, artist.albums, showArtist = false) { nav.navigate(Route.album(it.key)) }
        }
        composable(Route.ALBUMS) {
            AlbumsScreen(stringResource(R.string.albums), library.albums, showArtist = true) { nav.navigate(Route.album(it.key)) }
        }
        composable(Route.ALBUM) { entry ->
            val album = library.album(entry.arguments?.getString("key").orEmpty())
            if (album != null) AlbumScreen(album, now) { start, shuffle ->
                app.player.playAlbum(album, start, shuffle)
                nav.navigate(Route.PLAYER) { launchSingleTop = true }
            }
        }
        composable(Route.PLAYER) {
            NowPlayingScreen(
                now, nowAlbum, app.player,
                onLyrics = { nav.navigate(Route.LYRICS) { launchSingleTop = true } },
                onAlbum = { nowAlbum?.let { nav.navigate(Route.album(it.key)) } },
            )
        }
        composable(Route.LYRICS) { LyricsScreen(now, nowAlbum) }
    }
}

@Composable
private fun PermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) onGranted() else denied = true
    }
    Message(stringResource(R.string.permission_title), stringResource(R.string.permission_text)) {
        Button(
            onClick = {
                if (!denied) launcher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                else context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                )
            },
            label = { Text(stringResource(if (denied) R.string.permission_settings else R.string.permission_button)) },
        )
    }
}

@Composable
private fun Message(title: String, text: String?, action: (@Composable () -> Unit)? = null) {
    ScreenScaffold {
        Box(Modifier.fillMaxSize().padding(horizontal = 26.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, fontFamily = Gothic, fontSize = 26.sp, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
                if (text != null) Text(
                    text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                action?.invoke()
            }
        }
    }
}
