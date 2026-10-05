package com.vishnu.kohliprotocol.ui.discipline

import android.content.pm.PackageManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishnu.kohliprotocol.data.apps.InstalledApp
import com.vishnu.kohliprotocol.data.guardian.ProtectedAction
import com.vishnu.kohliprotocol.data.repository.EnforcementRepository
import com.vishnu.kohliprotocol.data.repository.GameAccessState
import com.vishnu.kohliprotocol.data.restrictions.RestrictionCategory
import com.vishnu.kohliprotocol.data.restrictions.RestrictionLists
import com.vishnu.kohliprotocol.ui.components.AccentBanner
import com.vishnu.kohliprotocol.ui.components.Eyebrow
import com.vishnu.kohliprotocol.ui.components.GhostButton
import com.vishnu.kohliprotocol.ui.components.InlineMessage
import com.vishnu.kohliprotocol.ui.components.KohliCard
import com.vishnu.kohliprotocol.ui.components.MutedText
import com.vishnu.kohliprotocol.ui.components.PageColumn
import com.vishnu.kohliprotocol.ui.components.PrimaryButton
import com.vishnu.kohliprotocol.ui.components.SecondaryButton
import com.vishnu.kohliprotocol.ui.components.SectionCard
import com.vishnu.kohliprotocol.ui.components.StatusPill
import com.vishnu.kohliprotocol.ui.components.bounceClick
import com.vishnu.kohliprotocol.ui.theme.KohliColors
import com.vishnu.kohliprotocol.ui.theme.KohliType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clockTime = DateTimeFormatter.ofPattern("HH:mm")
private const val STEP = 0.1f

/** The Discipline tab: Biryani Parameter, games, restricted apps, weekly reports, testing. */
@Composable
fun DisciplineTab(
    viewModel: DisciplineViewModel,
    contentPadding: PaddingValues,
    onGate: (ProtectedAction) -> Unit,
    onOpenSettings: () -> Unit,
    onViewPdf: (String) -> Unit,
    onSharePdf: (String) -> Unit,
) {
    val reportEmailInfo by viewModel.reportEmailInfo.collectAsStateWithLifecycle()
    val emailedIds by viewModel.emailedIds.collectAsStateWithLifecycle()
    val reportBusy by viewModel.reportBusy.collectAsStateWithLifecycle()
    val pdfToOpen by viewModel.pdfToOpen.collectAsStateWithLifecycle()
    LaunchedEffect(pdfToOpen) {
        pdfToOpen?.let {
            onViewPdf(it)
            viewModel.pdfOpened()
        }
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    /** Category whose app picker is open, if any. */
    var pickerFor by rememberSaveable { mutableStateOf<RestrictionCategory?>(null) }
    var pendingAdd by remember { mutableStateOf<InstalledApp?>(null) }

    val current = state ?: return
    PageColumn(contentPadding) {
        message?.let { InlineMessage(it) }
        BiryaniSection(
            current = current.biryaniParameter,
            onSave = { value ->
                val target = DisciplineViewModel.roundParameter(value)
                if (target < current.biryaniParameter) {
                    onGate(ProtectedAction.LowerBiryaniParameter(current.biryaniParameter, target))
                } else {
                    viewModel.saveParameter(target)
                }
            },
        )
        GamesSection(current.games)
        RestrictedAppsSection(
            lists = current.lists,
            onAdd = { category ->
                viewModel.loadApps()
                pickerFor = category
            },
            onRemove = { category, pkg, label -> onGate(ProtectedAction.RemoveRestrictedApp(category, pkg, label)) },
        )
        WeeklyReportsSection(
            reports = current.reports,
            protocolStart = current.protocolStart,
            emailedIds = emailedIds,
            busy = reportBusy,
            onEvaluateNow = viewModel::evaluateNow,
            onView = onViewPdf,
            onShare = onSharePdf,
            onRebuild = viewModel::rebuildReport,
            onPreview = viewModel::previewReport,
            onEmailPreview = viewModel::emailPreview,
        )
        ReportEmailSection(reportEmailInfo, onSave = viewModel::saveReportEmail)
        TestingSection(
            games = current.games,
            onMockPass = { onGate(ProtectedAction.MockGamesPass(EnforcementRepository.TEST_OVERRIDE_MINUTES)) },
            onMockFail = viewModel::mockFail,
            onEndMock = viewModel::endMock,
        )
        SectionCard("Security") {
            MutedText("Guardians, emergency override and the audit log live in Settings.")
            SecondaryButton("Open Settings", onClick = onOpenSettings)
        }
    }

    pickerFor?.let { category ->
        GamePickerDialog(
            apps = apps,
            controlled = state?.lists?.get(category).orEmpty(),
            title = if (category == RestrictionCategory.GAMES) "CHOOSE A GAME" else "CHOOSE AN APP",
            gamesFirst = category == RestrictionCategory.GAMES,
            onPick = { pendingAdd = it },
            onDismiss = { pickerFor = null },
        )
    }

    val adding = pendingAdd
    val addCategory = pickerFor
    if (adding != null && addCategory != null) {
        AlertDialog(
            onDismissRequest = { pendingAdd = null },
            containerColor = KohliColors.SurfaceHigh,
            title = { Text("Restrict ${adding.label}?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "${adding.label} will be added to ${addCategory.label}. Removing it later requires " +
                        "Guardian Gate approval.",
                    color = KohliColors.Muted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addApp(addCategory, adding)
                    pendingAdd = null
                    pickerFor = null
                }) { Text("Add", color = KohliColors.Accent) }
            },
            dismissButton = { TextButton(onClick = { pendingAdd = null }) { Text("Cancel", color = KohliColors.Muted) } },
        )
    }
}

@Composable
private fun BiryaniSection(current: Float, onSave: (Float) -> Unit) {
    var pending by rememberSaveable(current) { mutableStateOf(current) }
    val changeColor = when {
        pending > current -> KohliColors.Logged
        pending < current -> KohliColors.Warning
        else -> KohliColors.Accent
    }

    KohliCard(borderColor = KohliColors.Accent.copy(alpha = 0.35f), verticalSpacing = 14.dp) {
        Eyebrow("Biryani Parameter")
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            StepButton("−") {
                pending = DisciplineViewModel.roundParameter((pending - STEP).coerceAtLeast(EnforcementRepository.MIN_PARAMETER))
            }
            Text(
                pending.toString(),
                style = MaterialTheme.typography.displaySmall.copy(fontSize = 56.sp, lineHeight = 60.sp),
                color = changeColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(150.dp),
            )
            StepButton("+") {
                pending = DisciplineViewModel.roundParameter((pending + STEP).coerceAtMost(EnforcementRepository.MAX_PARAMETER))
            }
        }
        Text(
            when {
                pending > current -> "Raising from $current — takes effect immediately."
                pending < current -> "Lowering from $current needs Guardian Gate approval."
                else -> "The minimum weekly average needed to unlock games."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (pending < current) KohliColors.Warning else KohliColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton(
            text = if (pending < current) "REQUEST GUARDIAN APPROVAL" else "SAVE",
            onClick = { onSave(pending) },
            enabled = pending != current,
        )
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(1.dp, KohliColors.OutlineStrong),
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(52.dp).bounceClick(0.9f),
    ) {
        Text(label, fontSize = 24.sp, color = KohliColors.Text)
    }
}

@Composable
private fun GamesSection(games: GameAccessState) {
    val unlocked = games.isUnlocked()
    AccentBanner(accent = if (unlocked) KohliColors.Logged else KohliColors.Warning) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Games", Modifier.weight(1f))
            StatusPill(if (unlocked) "Unlocked" else "Locked", if (unlocked) KohliColors.Logged else KohliColors.Warning)
        }
        Text(
            if (unlocked) "🎮 Games unlocked" else "🔒 Games locked",
            style = KohliType.Rating,
            color = if (unlocked) KohliColors.Logged else KohliColors.Text,
        )
        if (!unlocked) MutedText(games.lockReason())
    }
}

@Composable
private fun RestrictedAppsSection(
    lists: RestrictionLists,
    onAdd: (RestrictionCategory) -> Unit,
    onRemove: (RestrictionCategory, String, String) -> Unit,
) {
    val context = LocalContext.current
    SectionCard("Restricted apps") {
        MutedText("Adding an app is instant. Removing one needs a guardian's approval.")
        RestrictionCategory.entries.forEach { category ->
            Text(
                category.label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            lists[category].sorted().forEach { pkg ->
                val label = appLabel(context.packageManager, pkg)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    LetterAvatar(label ?: pkg)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(label ?: pkg, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            if (label != null) pkg else "$pkg · not installed",
                            style = MaterialTheme.typography.bodySmall,
                            color = KohliColors.Muted,
                        )
                    }
                    GhostButton("Remove", onClick = { onRemove(category, pkg, label ?: pkg) }, color = KohliColors.Muted)
                }
            }
            SecondaryButton(
                if (category == RestrictionCategory.GAMES) "+ Add a game" else "+ Add an app",
                onClick = { onAdd(category) },
            )
        }
    }
}

@Composable
private fun LetterAvatar(name: String) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(KohliColors.SurfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(name.first().uppercase(), style = MaterialTheme.typography.labelLarge, color = KohliColors.Accent)
    }
}

@Composable
private fun TestingSection(games: GameAccessState, onMockPass: () -> Unit, onMockFail: () -> Unit, onEndMock: () -> Unit) {
    SectionCard("Testing", borderColor = KohliColors.Accent.copy(alpha = 0.4f)) {
        MutedText(
            "Mock Weekly Evaluation — temporarily forces a pass or fail so the game lock can be tested " +
                "on the phone. Reverts to the real result after ${EnforcementRepository.TEST_OVERRIDE_MINUTES} " +
                "minutes and is recorded in the audit log."
        )
        if (games.isTestActive()) {
            val until = Instant.ofEpochMilli(games.testOverrideUntil).atZone(ZoneId.systemDefault()).format(clockTime)
            StatusPill("Active: ${if (games.testOverride == true) "PASS" else "FAIL"} until $until", KohliColors.Accent)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton("Mock PASS (guardian)", onClick = onMockPass, contentColor = KohliColors.Logged, modifier = Modifier.weight(1f))
            SecondaryButton("Mock FAIL", onClick = onMockFail, contentColor = KohliColors.Missing, modifier = Modifier.weight(1f))
        }
        if (games.isTestActive()) {
            GhostButton("End test now", onClick = onEndMock)
        }
    }
}

private fun appLabel(pm: PackageManager, pkg: String): String? = try {
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (e: PackageManager.NameNotFoundException) {
    null
}
