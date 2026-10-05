package com.vishnu.kohliprotocol.ui.discipline

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.vishnu.kohliprotocol.data.apps.InstalledApp
import com.vishnu.kohliprotocol.ui.components.ChoiceChips
import com.vishnu.kohliprotocol.ui.components.MutedText
import com.vishnu.kohliprotocol.ui.components.bounceClick
import com.vishnu.kohliprotocol.ui.theme.KohliColors
import com.vishnu.kohliprotocol.ui.theme.KohliType

/**
 * Full-screen picker of installed apps. With [gamesFirst] it starts on apps that declare
 * themselves as games ("All apps" covers games that don't). Apps already in the list are shown
 * but can't be picked.
 */
@Composable
fun GamePickerDialog(
    apps: List<InstalledApp>?,
    controlled: Set<String>,
    onPick: (InstalledApp) -> Unit,
    onDismiss: () -> Unit,
    title: String = "CHOOSE A GAME",
    gamesFirst: Boolean = true,
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Start on "Games" only for game picking, and only when Android actually reports some games.
    var showAll by rememberSaveable(apps != null) {
        mutableStateOf(!gamesFirst || (apps != null && apps.none { it.isGame }))
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(KohliColors.Background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = KohliType.Brand, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search apps") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            ChoiceChips(
                options = listOf(false, true),
                selected = showAll,
                label = { all -> if (all) "All apps" else "Games" },
                onSelect = { showAll = it },
            )
            if (!showAll) {
                MutedText("Showing apps that tell Android they're games. Not listed? Switch to All apps.")
            }

            if (apps == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                val needle = query.trim().lowercase()
                val visible = apps.filter { app ->
                    (showAll || app.isGame) &&
                        (needle.isEmpty() || needle in app.label.lowercase() || needle in app.packageName.lowercase())
                }
                if (visible.isEmpty()) {
                    Text("No matching apps.", color = KohliColors.Muted, modifier = Modifier.padding(top = 24.dp))
                }
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(visible, key = { it.packageName }) { app ->
                        AppRow(app, isControlled = app.packageName in controlled, onClick = { onPick(app) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, isControlled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .then(if (isControlled) Modifier else Modifier.bounceClick())
            .clickable(enabled = !isControlled, onClick = onClick)
            .alpha(if (isControlled) 0.5f else 1f)
            .padding(vertical = 8.dp, horizontal = 4.dp),
    ) {
        if (app.icon != null) {
            Image(app.icon.asImageBitmap(), contentDescription = null, modifier = Modifier.size(40.dp))
        } else {
            Spacer(Modifier.size(40.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(app.packageName, color = KohliColors.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when {
            isControlled -> Badge("CONTROLLED")
            app.isGame -> Badge("GAME")
        }
    }
}

@Composable
private fun Badge(text: String) {
    Text(
        text,
        color = KohliColors.Accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(KohliColors.Accent.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
