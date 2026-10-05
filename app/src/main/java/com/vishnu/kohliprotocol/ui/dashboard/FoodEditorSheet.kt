package com.vishnu.kohliprotocol.ui.dashboard

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import com.vishnu.kohliprotocol.ui.components.GhostButton
import com.vishnu.kohliprotocol.ui.components.InlineMessage
import com.vishnu.kohliprotocol.ui.components.MessageTone
import com.vishnu.kohliprotocol.ui.components.MutedText
import com.vishnu.kohliprotocol.ui.components.PhotoThumbnail
import com.vishnu.kohliprotocol.ui.components.PrimaryButton
import com.vishnu.kohliprotocol.ui.components.SecondaryButton
import com.vishnu.kohliprotocol.ui.components.StatusPill
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.vishnu.kohliprotocol.ui.theme.KohliType
import com.vishnu.kohliprotocol.ui.theme.KohliColors

/** Bottom sheet for logging into a slot, "+ Add Food", or editing an existing entry. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodEditorSheet(state: EditorState, viewModel: DashboardViewModel) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Survives the activity being recreated while the camera app is in front.
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        pendingCapture?.let { viewModel.onPhotoCaptured(it, ok) }
        pendingCapture = null
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::onGalleryPicked)
    }

    val target = state.target
    val title = when (target) {
        is EditorTarget.Slot -> target.slot.mealType.label
        EditorTarget.AddFood -> "+ Add Food"
        is EditorTarget.Existing -> "Edit — ${target.item.label}"
    }

    ModalBottomSheet(
        onDismissRequest = viewModel::dismissEditor,
        sheetState = sheetState,
        containerColor = KohliColors.SurfaceHigh,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(title.uppercase(), style = KohliType.Brand.copy(fontSize = 20.sp))

            if (target is EditorTarget.Existing) {
                val logged = Instant.ofEpochMilli(target.item.entry.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(target.item.label, KohliColors.Accent)
                    StatusPill("Logged $logged", KohliColors.Muted)
                }
            }

            if (target is EditorTarget.Slot && target.slot.status == SlotStatus.SKIPPED) {
                MutedText("This meal is marked skipped. Logging food will un-skip it.")
            }
            if (target is EditorTarget.Slot && target.slot.entries.isNotEmpty()) {
                MutedText("Already logged: " + target.slot.entries.joinToString(" · ") { it.description })
            }

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("What did you eat?") },
                placeholder = { Text("e.g. 2 chapati, dal, curd") },
                minLines = 3,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )

            state.photoPath?.let { path ->
                Box {
                    PhotoThumbnail(path, size = 120.dp)
                    FilledIconButton(
                        onClick = viewModel::removePhoto,
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = Color.White)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(
                    text = "Camera",
                    onClick = {
                        val file = viewModel.newCaptureFile()
                        pendingCapture = file.absolutePath
                        try {
                            cameraLauncher.launch(viewModel.captureUri(file))
                        } catch (e: ActivityNotFoundException) {
                            pendingCapture = null
                            viewModel.onPhotoCaptured(file.absolutePath, success = false)
                            Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    text = "Gallery",
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !state.busy,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.busy) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                    color = KohliColors.Accent,
                    trackColor = KohliColors.Outline,
                )
            }
            state.error?.let { InlineMessage(it, tone = MessageTone.ERROR) }

            PrimaryButton(
                text = if (target is EditorTarget.Existing) "SAVE CHANGES" else "LOG FOOD",
                onClick = viewModel::save,
                enabled = !state.busy,
            )

            if (target is EditorTarget.Slot && target.slot.status == SlotStatus.EMPTY) {
                SecondaryButton(
                    text = "MARK SKIPPED",
                    onClick = viewModel::markSkipped,
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (target is EditorTarget.Existing) {
                GhostButton(
                    text = "Delete entry",
                    onClick = viewModel::delete,
                    enabled = !state.busy,
                    color = KohliColors.Missing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
