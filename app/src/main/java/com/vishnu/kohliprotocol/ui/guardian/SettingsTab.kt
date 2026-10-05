package com.vishnu.kohliprotocol.ui.guardian

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vishnu.kohliprotocol.KohliApplication
import com.vishnu.kohliprotocol.data.guardian.EmailDeliveryInfo
import com.vishnu.kohliprotocol.data.guardian.Guardian
import com.vishnu.kohliprotocol.data.guardian.GuardianChannel
import com.vishnu.kohliprotocol.data.guardian.GuardianStore
import com.vishnu.kohliprotocol.data.guardian.ProtectedAction
import com.vishnu.kohliprotocol.data.local.entity.AuditAction
import com.vishnu.kohliprotocol.data.repository.AuditRepository
import com.vishnu.kohliprotocol.data.repository.EnforcementRepository
import com.vishnu.kohliprotocol.data.restrictions.EmergencyOverrideState
import com.vishnu.kohliprotocol.data.restrictions.RestrictionCategory
import com.vishnu.kohliprotocol.ui.components.ChoiceChips
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SecurityViewModel(
    private val store: GuardianStore,
    enforcement: EnforcementRepository,
    private val audit: AuditRepository,
) : ViewModel() {

    val guardians: StateFlow<List<Guardian>?> = store.guardians
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val emailDelivery: StateFlow<EmailDeliveryInfo?> = store.emailDelivery
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val emergencyOverride: StateFlow<EmergencyOverrideState?> = enforcement.emergencyOverride
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Delivery settings are not a weakening action: a wrong key only stops codes arriving (fail closed). */
    fun saveEmailDelivery(apiKey: String, senderEmail: String, senderName: String) {
        if (senderEmail.isBlank()) {
            _message.value = "Enter the verified sender email."
            return
        }
        viewModelScope.launch {
            store.setEmailDelivery(apiKey, senderEmail, senderName)
            audit.log(AuditAction.GUARDIAN_DELIVERY_CONFIGURED, "Email delivery set to send from ${senderEmail.trim()}" +
                if (apiKey.isNotBlank()) " (new API key)" else "")
            _message.value = "Email delivery saved."
        }
    }

    fun setMessage(text: String?) {
        _message.value = text
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as KohliApplication).container
                SecurityViewModel(container.guardianStore, container.enforcementRepository, container.auditRepository)
            }
        }
    }
}

/** Editable copy of a guardian. [id] is kept for existing guardians so unchanged ones stay "the same". */
private data class GuardianDraft(
    val id: String?,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val channel: GuardianChannel = GuardianChannel.EMAIL,
    val addedAt: Long = 0L,
) {
    fun toGuardian(): Guardian =
        if (id == null) Guardian.new(name, email, phone, channel)
        else Guardian(id, name.trim(), email.trim().ifEmpty { null }, phone.trim().ifEmpty { null }, channel, addedAt)

    companion object {
        fun from(g: Guardian) = GuardianDraft(g.id, g.name, g.email.orEmpty(), g.phone.orEmpty(), g.channel, g.addedAt)
    }
}

private sealed interface EditMode {
    object None : EditMode
    object Full : EditMode
    data class Recovery(val lostId: String) : EditMode
}


private val untilFormat = DateTimeFormatter.ofPattern("HH:mm")

/**
 * The Settings tab: app settings shortcuts, guardians, Guardian code delivery, emergency override
 * and the audit log.
 */
@Composable
fun SettingsTab(
    viewModel: SecurityViewModel,
    contentPadding: PaddingValues,
    onGate: (ProtectedAction) -> Unit,
    onOpenAudit: () -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenSetup: () -> Unit,
) {
    val guardians by viewModel.guardians.collectAsStateWithLifecycle()
    val delivery by viewModel.emailDelivery.collectAsStateWithLifecycle()
    val override by viewModel.emergencyOverride.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val current = guardians ?: return
    PageColumn(contentPadding) {
        message?.let { InlineMessage(it) }
        KohliCard(contentPadding = PaddingValues(vertical = 6.dp), verticalSpacing = 0.dp) {
            NavRow(Icons.Filled.Star, "AI settings", "API keys, default key and models", onOpenAiSettings)
            RowDivider()
            NavRow(Icons.Filled.Settings, "Enforcement setup", "Accessibility, notifications, battery", onOpenSetup)
            RowDivider()
            NavRow(Icons.AutoMirrored.Filled.List, "Audit log", "Every unlock, approval, change and override", onOpenAudit)
        }
        GuardiansSection(current, onGate = onGate, onError = viewModel::setMessage)
        DeliverySection(delivery, onSave = viewModel::saveEmailDelivery)
        OverrideSection(override, hasGuardians = current.isNotEmpty(), onGate = onGate, onError = viewModel::setMessage)
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(0.98f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(KohliColors.Accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = KohliColors.Accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = KohliColors.Muted)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = KohliColors.Muted)
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = KohliColors.Outline, modifier = Modifier.padding(start = 68.dp))
}

@Composable
private fun GuardiansSection(current: List<Guardian>, onGate: (ProtectedAction) -> Unit, onError: (String?) -> Unit) {
    var mode by remember { mutableStateOf<EditMode>(EditMode.None) }
    val drafts = remember { mutableStateListOf<GuardianDraft>() }

    fun beginEdit(newMode: EditMode) {
        drafts.clear()
        drafts.addAll(current.map { GuardianDraft.from(it) })
        if (drafts.isEmpty()) drafts.add(GuardianDraft(id = null))
        if (newMode is EditMode.Recovery) {
            // The lost guardian is replaced by a fresh entry; the others stay locked as-is.
            val index = drafts.indexOfFirst { it.id == newMode.lostId }
            if (index >= 0) drafts[index] = GuardianDraft(id = null)
        }
        mode = newMode
    }

    SectionCard("Guardians") {
        MutedText(
            if (current.isEmpty()) {
                "No guardians yet. Set them up together with them: each receives a code to confirm their contact. " +
                    "After this, relaxing any rule needs their approval."
            } else {
                "Relaxing a rule needs a code from a guardian. Changing guardians needs every current guardian."
            }
        )

        if (mode == EditMode.None) {
            current.forEach { g ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(KohliColors.SurfaceHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(g.name.first().uppercase(), style = MaterialTheme.typography.titleSmall, color = KohliColors.Accent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(g.name, style = MaterialTheme.typography.titleSmall)
                        Text(g.maskedDestination, style = MaterialTheme.typography.bodySmall, color = KohliColors.Muted)
                    }
                    StatusPill(g.channel.label, KohliColors.Muted)
                }
            }
            PrimaryButton(if (current.isEmpty()) "Set up guardians" else "Change guardians", onClick = { beginEdit(EditMode.Full) })
            if (current.size > 1) {
                current.forEach { g ->
                    GhostButton("${g.name} lost access — replace", onClick = { beginEdit(EditMode.Recovery(g.id)) }, color = KohliColors.Muted)
                }
            }
        } else {
            val recovery = mode as? EditMode.Recovery
            drafts.forEachIndexed { index, draft ->
                val editable = recovery == null || draft.id == null
                GuardianEditor(
                    title = "Guardian ${'A' + index}",
                    draft = draft,
                    editable = editable,
                    onChange = { drafts[index] = it },
                    onRemove = if (recovery == null && drafts.size > 1) ({ drafts.removeAt(index) }) else null,
                )
            }
            if (recovery == null && drafts.size < 2) {
                GhostButton("+ Add second guardian", onClick = { drafts.add(GuardianDraft(id = null)) })
            }
            PrimaryButton(
                "Send codes & continue",
                onClick = {
                    val proposed = drafts.map { it.toGuardian() }
                    val error = proposed.firstNotNullOfOrNull { it.validationError() }
                    if (error != null) {
                        onError(error)
                    } else {
                        onError(null)
                        onGate(ProtectedAction.ChangeGuardians(proposed, recoveryOf = recovery?.lostId))
                        mode = EditMode.None
                    }
                },
            )
            GhostButton("Cancel", onClick = { mode = EditMode.None }, color = KohliColors.Muted)
        }
    }
}

@Composable
private fun GuardianEditor(
    title: String,
    draft: GuardianDraft,
    editable: Boolean,
    onChange: (GuardianDraft) -> Unit,
    onRemove: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = KohliColors.SurfaceHigh,
        border = BorderStroke(1.dp, KohliColors.Outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (!editable) StatusPill("Unchanged", KohliColors.Muted)
                onRemove?.let { GhostButton("Remove", onClick = it, color = KohliColors.Missing) }
            }
            OutlinedTextField(draft.name, { onChange(draft.copy(name = it)) }, label = { Text("Name") }, enabled = editable, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(draft.email, { onChange(draft.copy(email = it)) }, label = { Text("Email") }, enabled = editable, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(draft.phone, { onChange(draft.copy(phone = it)) }, label = { Text("Phone (+91…)") }, enabled = editable, singleLine = true, modifier = Modifier.fillMaxWidth())
            Eyebrow("Send codes by")
            if (editable) {
                ChoiceChips(
                    options = GuardianChannel.entries,
                    selected = draft.channel,
                    label = { it.label },
                    onSelect = { onChange(draft.copy(channel = it)) },
                )
            } else {
                StatusPill(draft.channel.label, KohliColors.Muted)
            }
        }
    }
}

@Composable
private fun DeliverySection(delivery: EmailDeliveryInfo?, onSave: (String, String, String) -> Unit) {
    val context = LocalContext.current
    var apiKey by rememberSaveable { mutableStateOf("") }
    var sender by rememberSaveable(delivery?.senderEmail) { mutableStateOf(delivery?.senderEmail.orEmpty()) }
    var senderName by rememberSaveable(delivery?.senderName) { mutableStateOf(delivery?.senderName ?: "Kohli Protocol") }
    var smsGranted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }
    val smsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { smsGranted = it }

    SectionCard("Guardian code delivery") {
        MutedText(
            "Email codes are sent through the Guardian Brevo account. Ideally a guardian owns it, so the " +
                "sent-email log isn't yours to read."
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(
                if (delivery?.hasApiKey == true) "Email ready" else "Email not set",
                if (delivery?.hasApiKey == true) KohliColors.Logged else KohliColors.Missing,
            )
            StatusPill(
                if (smsGranted) "SMS allowed" else "SMS needs permission",
                if (smsGranted) KohliColors.Logged else KohliColors.Missing,
            )
        }
        if (delivery?.hasApiKey == true) MutedText("Sending from ${delivery?.senderEmail}")
        OutlinedTextField(apiKey, { apiKey = it }, label = { Text("Brevo API key") },
            placeholder = { Text(if (delivery?.hasApiKey == true) "•••••••• (saved)" else "xkeysib-…") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(sender, { sender = it }, label = { Text("Verified sender email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(senderName, { senderName = it }, label = { Text("Sender name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        SecondaryButton("Save email delivery", onClick = { onSave(apiKey, sender, senderName); apiKey = "" })
        if (!smsGranted) {
            GhostButton("Allow SMS", onClick = { smsPermission.launch(Manifest.permission.SEND_SMS) })
        }
    }
}

@Composable
private fun OverrideSection(
    override: EmergencyOverrideState?,
    hasGuardians: Boolean,
    onGate: (ProtectedAction) -> Unit,
    onError: (String?) -> Unit,
) {
    var categories by remember { mutableStateOf(emptySet<RestrictionCategory>()) }
    var minutes by rememberSaveable { mutableStateOf(30) }
    var reason by rememberSaveable { mutableStateOf("") }
    val active = override != null && override.isActive()

    SectionCard("Emergency override", borderColor = if (active) KohliColors.Warning else KohliColors.Outline) {
        MutedText(
            "For a genuine exception only. Needs every guardian's code and a reason; suspends the chosen " +
                "restrictions temporarily, then restores them automatically. Everything is recorded."
        )
        if (override != null && active) {
            val until = Instant.ofEpochMilli(override.untilEpochMillis).atZone(ZoneId.systemDefault()).format(untilFormat)
            StatusPill("Active until $until", KohliColors.Warning)
            Text(override.categories.joinToString { it.label }, style = MaterialTheme.typography.titleSmall)
            MutedText("Reason: ${override.reason}")
        } else {
            RestrictionCategory.entries.forEach { category ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = category in categories,
                        onCheckedChange = { checked -> categories = if (checked) categories + category else categories - category },
                        colors = CheckboxDefaults.colors(checkedColor = KohliColors.Accent, checkmarkColor = KohliColors.OnAccent),
                    )
                    Text(category.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Eyebrow("Duration")
            ChoiceChips(
                options = listOf(15, 30, 60),
                selected = minutes,
                label = { "$it min" },
                onSelect = { minutes = it },
            )
            OutlinedTextField(reason, { reason = it }, label = { Text("Reason (required)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            PrimaryButton(
                "Request emergency override",
                containerColor = KohliColors.Warning,
                contentColor = KohliColors.Text,
                onClick = {
                    when {
                        !hasGuardians -> onError("Set up guardians first.")
                        categories.isEmpty() -> onError("Choose what to suspend.")
                        reason.isBlank() -> onError("A reason is required.")
                        else -> {
                            onError(null)
                            onGate(ProtectedAction.EmergencyOverride(categories, minutes, reason.trim()))
                        }
                    }
                },
            )
        }
    }
}
