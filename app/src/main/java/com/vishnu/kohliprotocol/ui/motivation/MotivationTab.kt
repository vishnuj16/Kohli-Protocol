package com.vishnu.kohliprotocol.ui.motivation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vishnu.kohliprotocol.data.local.entity.MotivationPhotoEntity
import com.vishnu.kohliprotocol.ui.components.InlineMessage
import com.vishnu.kohliprotocol.ui.components.MessageTone
import com.vishnu.kohliprotocol.ui.components.PrimaryButton
import com.vishnu.kohliprotocol.ui.components.SecondaryButton
import com.vishnu.kohliprotocol.ui.components.bounceClick
import com.vishnu.kohliprotocol.ui.theme.KohliColors
import com.vishnu.kohliprotocol.ui.theme.KohliType
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val cardShape = RoundedCornerShape(16.dp)
private val addedFormat = DateTimeFormatter.ofPattern("d MMM yyyy")

/** The Motivation tab: a Pinterest-style masonry feed of private photos. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MotivationTab(viewModel: MotivationViewModel, contentPadding: PaddingValues) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var viewing by remember { mutableStateOf<MotivationPhotoEntity?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris ->
        viewModel.addPhotos(uris)
    }

    Box(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Column(Modifier.fillMaxSize()) {
            if (busy) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth(),
                    color = KohliColors.Accent,
                    trackColor = KohliColors.Outline,
                )
            }
            error?.let {
                InlineMessage(
                    it,
                    tone = MessageTone.ERROR,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clickable(onClick = viewModel::clearError),
                )
            }
            val list = photos
            when {
                list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = KohliColors.Accent)
                }
                list.isEmpty() -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("NOTHING HERE YET", style = KohliType.Brand)
                    Text(
                        "Add the people, places and goals that keep you honest.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KohliColors.Muted,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Fixed(2),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + 96.dp,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { photo ->
                        PhotoCard(photo, onClick = { viewing = photo })
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            containerColor = KohliColors.Accent,
            contentColor = Color.Black,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = contentPadding.calculateBottomPadding() + 20.dp)
                .bounceClick(),
        ) { Icon(Icons.Filled.Add, contentDescription = "Add photos") }
    }

    viewing?.let { photo ->
        PhotoViewer(
            photo = photo,
            onClose = { viewing = null },
            onDelete = {
                viewModel.delete(photo)
                viewing = null
            },
        )
    }
}

/** A masonry card: the photo at its natural aspect ratio. */
@Composable
private fun PhotoCard(photo: MotivationPhotoEntity, onClick: () -> Unit) {
    AsyncImage(
        model = File(photo.photoPath),
        contentDescription = photo.title,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 90.dp)
            .bounceClick()
            .shadow(elevation = 4.dp, shape = cardShape)
            .clip(cardShape)
            .border(1.dp, KohliColors.Outline, cardShape)
            .background(KohliColors.Surface)
            .clickable(onClick = onClick),
    )
}

/** Full-bleed viewer with gradient overlays; scales and fades in. */
@Composable
private fun PhotoViewer(photo: MotivationPhotoEntity, onClose: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    val added = Instant.ofEpochMilli(photo.dateAdded).atZone(ZoneId.systemDefault()).format(addedFormat)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        AnimatedVisibility(
            visibleState = appear,
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.92f, animationSpec = tween(260)),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(
                    model = File(photo.photoPath),
                    contentDescription = photo.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clickable(onClick = onClose),
                )

                // Top overlay: title and upload date.
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                ) {
                    photo.title?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.titleSmall, color = Color.White)
                    }
                    Text("Added $added", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
                }

                // Bottom overlay: actions.
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PrimaryButton(
                        text = "Delete Photo",
                        onClick = { confirmDelete = true },
                        fillWidth = false,
                        containerColor = KohliColors.Missing,
                        contentColor = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    SecondaryButton(text = "Close", onClick = onClose, contentColor = Color.White)
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = KohliColors.SurfaceHigh,
            title = { Text("Delete photo?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("It will be removed from the gallery and deleted from the device.", color = KohliColors.Muted) },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = KohliColors.Missing) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = KohliColors.Muted) } },
        )
    }
}
