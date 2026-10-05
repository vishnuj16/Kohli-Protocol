package com.vishnu.kohliprotocol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vishnu.kohliprotocol.ui.theme.KohliColors
import java.io.File

/**
 * Square, cropped thumbnail of a photo in app-private storage. Coil decodes off the main thread,
 * downsampled to exactly the thumbnail's pixel size, and keeps it in its memory cache.
 */
@Composable
fun PhotoThumbnail(path: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val request = remember(path, px) {
        ImageRequest.Builder(context)
            .data(File(path))
            .size(px)
            .memoryCacheKey("thumb:$path:$px")
            .crossfade(true)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(KohliColors.SurfaceHigh),
    )
}
