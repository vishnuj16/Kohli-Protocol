package com.vishnu.kohliprotocol.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vishnu.kohliprotocol.ui.theme.KohliColors
import java.io.File

/** Square, cropped thumbnail of a photo in app-private storage. */
@Composable
fun PhotoThumbnail(path: String, size: Dp, modifier: Modifier = Modifier) {
    AsyncImage(
        model = File(path),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(KohliColors.SurfaceHigh),
    )
}
