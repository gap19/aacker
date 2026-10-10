package io.github.gap19.aacker.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import io.github.gap19.aacker.app
import io.github.gap19.aacker.library.Album
import io.github.gap19.aacker.library.Artwork

/** The album cover at [size], from memory when possible; a quiet disc mark while it loads. */
@Composable
fun Cover(album: Album, size: Artwork.Size, modifier: Modifier, shape: Shape) {
    val artwork = LocalContext.current.app.artwork
    val image by produceState<ImageBitmap?>(artwork.cached(album, size)?.asImageBitmap(), album.key, size) {
        if (value == null) value = artwork.load(album, size)?.asImageBitmap()
    }
    val current = image
    if (current != null) {
        Image(current, contentDescription = null, modifier = modifier.clip(shape), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh), Alignment.Center) {
            Icon(
                painterResource(io.github.gap19.aacker.R.drawable.ic_album), contentDescription = null,
                tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(Dp(18f)),
            )
        }
    }
}

/** Accent of an album's cover, available once its thumbnail is loaded (the lists load it first). */
@Composable
fun rememberAccent(album: Album?): Color {
    val artwork = LocalContext.current.app.artwork
    val accent by produceState(album?.let(artwork::accent)?.let(::Color) ?: Teal, album?.key) {
        if (album == null) return@produceState
        artwork.load(album, Artwork.Size.THUMB)
        artwork.accent(album)?.let { value = Color(it) }
    }
    return accent
}

/** A scrolling list screen: rotary scrolling, curved edges and the scroll indicator come from Wear Compose. */
@Composable
fun ListScreen(
    edgeButton: (@Composable androidx.compose.foundation.layout.BoxScope.() -> Unit)? = null,
    content: TransformingLazyColumnScope.(TransformationSpec) -> Unit,
) {
    val state = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    if (edgeButton != null) {
        ScreenScaffold(scrollState = state, edgeButton = edgeButton) { padding ->
            TransformingLazyColumn(state = state, contentPadding = padding, modifier = Modifier.fillMaxWidth()) { content(spec) }
        }
    } else {
        ScreenScaffold(scrollState = state) { padding ->
            TransformingLazyColumn(state = state, contentPadding = padding, modifier = Modifier.fillMaxWidth()) { content(spec) }
        }
    }
}

@Composable
fun rememberPlural(id: Int, n: Int): String = androidx.compose.ui.res.pluralStringResource(id, n, n)
