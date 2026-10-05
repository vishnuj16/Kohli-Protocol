package com.vishnu.kohliprotocol.ui.guardian

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vishnu.kohliprotocol.KohliApplication
import com.vishnu.kohliprotocol.data.guardian.AuthorizationPolicy
import com.vishnu.kohliprotocol.data.guardian.AuthorizationRequest
import com.vishnu.kohliprotocol.data.guardian.Challenge
import com.vishnu.kohliprotocol.data.guardian.ChallengePurpose
import com.vishnu.kohliprotocol.data.guardian.Guardian
import com.vishnu.kohliprotocol.data.guardian.GuardianGateManager
import com.vishnu.kohliprotocol.data.guardian.ProtectedAction
import com.vishnu.kohliprotocol.data.guardian.RequestStatus
import com.vishnu.kohliprotocol.security.SecureActivity
import com.vishnu.kohliprotocol.ui.components.AccentBanner
import com.vishnu.kohliprotocol.ui.components.Eyebrow
import com.vishnu.kohliprotocol.ui.components.GhostButton
import com.vishnu.kohliprotocol.ui.components.InlineMessage
import com.vishnu.kohliprotocol.ui.components.KohliCard
import com.vishnu.kohliprotocol.ui.components.KohliIcons
import com.vishnu.kohliprotocol.ui.components.MessageTone
import com.vishnu.kohliprotocol.ui.components.MutedText
import com.vishnu.kohliprotocol.ui.components.PageColumn
import com.vishnu.kohliprotocol.ui.components.PageScaffold
import com.vishnu.kohliprotocol.ui.components.PrimaryButton
import com.vishnu.kohliprotocol.ui.components.SectionLabel
import com.vishnu.kohliprotocol.ui.components.StatusPill
import com.vishnu.kohliprotocol.ui.theme.KohliColors
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "Are you authorized to weaken this rule?" Opened with a [ProtectedAction] (starts or resumes
 * its request) or with an existing request id.
 */
class GuardianGateActivity : SecureActivity() {

    override val screenName = "Guardian Gate"

    private val viewModel: GuardianGateViewModel by viewModels {
        GuardianGateViewModel.factory(intent.getStringExtra(EXTRA_ACTION), intent.getStringExtra(EXTRA_REQUEST))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSecureContent { GuardianGateScreen(viewModel, onClose = ::finish) }
    }

    companion object {
        private const val EXTRA_ACTION = "protected_action"
        private const val EXTRA_REQUEST = "request_id"

        fun intent(context: Context, action: ProtectedAction): Intent =
            Intent(context, GuardianGateActivity::class.java).putExtra(EXTRA_ACTION, action.toJson().toString())
    }
}

sealed interface GatePhase {
    object Loading : GatePhase
    /** Nothing has been sent yet: the user confirms (and, if one guardian is enough, picks who). */
    data class Confirm(val requirement: AuthorizationPolicy.Requirement) : GatePhase
    data class Refused(val reason: String) : GatePhase
    object Active : GatePhase
}

@OptIn(ExperimentalCoroutinesApi::class)
class GuardianGateViewModel(
    private val gate: GuardianGateManager,
    actionJson: String?,
    requestId: String?,
) : ViewModel() {

    private val action: ProtectedAction? = actionJson?.let { runCatching { ProtectedAction.fromJson(JSONObject(it)) }.getOrNull() }

    private val _phase = MutableStateFlow<GatePhase>(if (requestId != null) GatePhase.Active else GatePhase.Loading)
    val phase: StateFlow<GatePhase> = _phase.asStateFlow()

    private val requestIdFlow = MutableStateFlow(requestId)
    val request: StateFlow<AuthorizationRequest?> = requestIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(null) else gate.observe(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val guardians: StateFlow<List<Guardian>> = gate.guardians
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val description: String? get() = action?.describe()

    init {
        if (requestId == null) viewModelScope.launch { decide() }
    }

    private suspend fun decide() {
        val action = action ?: run {
            _phase.value = GatePhase.Refused("Unknown request.")
            return
        }
        when (val decision = gate.decisionFor(action)) {
            is AuthorizationPolicy.Decision.Refused -> _phase.value = GatePhase.Refused(decision.reason)
            // Never send automatically: codes go out only after the user confirms.
            is AuthorizationPolicy.Decision.Allowed -> _phase.value = GatePhase.Confirm(decision.requirement)
        }
    }

    fun start(approverId: String?) {
        val action = action ?: return
        _busy.value = true
        viewModelScope.launch {
            when (val result = gate.start(action, approverId)) {
                is GuardianGateManager.StartResult.Started -> {
                    requestIdFlow.value = result.request.id
                    _phase.value = GatePhase.Active
                }
                is GuardianGateManager.StartResult.Refused -> _phase.value = GatePhase.Refused(result.reason)
            }
            _busy.value = false
        }
    }

    fun submit(challengeKey: String, code: String) {
        val id = requestIdFlow.value ?: return
        _busy.value = true
        viewModelScope.launch {
            _message.value = when (val result = gate.submitCode(id, challengeKey, code)) {
                GuardianGateManager.CodeResult.Verified -> "Code accepted."
                GuardianGateManager.CodeResult.Approved -> "Approved — the change has been applied."
                is GuardianGateManager.CodeResult.Wrong -> "Wrong code. ${result.attemptsLeft} attempt(s) left."
                is GuardianGateManager.CodeResult.Closed -> result.message
            }
            _busy.value = false
        }
    }

    fun resend(challengeKey: String) {
        val id = requestIdFlow.value ?: return
        viewModelScope.launch { _message.value = gate.resend(id, challengeKey) ?: "New code sent." }
    }

    fun askAnother(guardianId: String) {
        val id = requestIdFlow.value ?: return
        viewModelScope.launch { _message.value = gate.askAnotherGuardian(id, guardianId) ?: "Code sent." }
    }

    fun cancel() {
        val id = requestIdFlow.value ?: return
        viewModelScope.launch { gate.cancel(id) }
    }

    companion object {
        fun factory(actionJson: String?, requestId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as KohliApplication).container
                GuardianGateViewModel(container.guardianGate, actionJson, requestId)
            }
        }
    }
}

private val clock = DateTimeFormatter.ofPattern("HH:mm")

private fun time(epochMillis: Long) = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(clock)

@Composable
private fun GuardianGateScreen(viewModel: GuardianGateViewModel, onClose: () -> Unit) {
    val phase by viewModel.phase.collectAsStateWithLifecycle()
    val request by viewModel.request.collectAsStateWithLifecycle()
    val guardians by viewModel.guardians.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()

    PageScaffold(title = "Guardian Gate", onBack = onClose) { padding ->
        PageColumn(padding) {
            val description = request?.action?.describe() ?: viewModel.description
            KohliCard(borderColor = KohliColors.Accent.copy(alpha = 0.4f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(KohliColors.Accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(KohliIcons.Shield, contentDescription = null, tint = KohliColors.Accent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Eyebrow("You are asking to")
                }
                Text(description ?: "…", style = MaterialTheme.typography.titleLarge)
                MutedText("Making the protocol stricter is free. Making it easier needs someone other than you.")
            }

            message?.let { InlineMessage(it) }
            if (busy) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(50)),
                    color = KohliColors.Accent,
                    trackColor = KohliColors.Outline,
                )
            }

            when (val current = phase) {
                GatePhase.Loading -> Unit
                is GatePhase.Refused -> {
                    InlineMessage(current.reason, tone = MessageTone.ERROR)
                    PrimaryButton("Close", onClick = onClose)
                }
                is GatePhase.Confirm -> ConfirmSend(
                    requirement = current.requirement,
                    busy = busy,
                    onSend = viewModel::start,
                    onCancel = onClose,
                )
                GatePhase.Active -> request?.let { active ->
                    ActiveRequest(
                        request = active,
                        guardians = guardians,
                        busy = busy,
                        onSubmit = viewModel::submit,
                        onResend = viewModel::resend,
                        onAskAnother = viewModel::askAnother,
                        onCancel = viewModel::cancel,
                        onClose = onClose,
                    )
                }
            }
        }
    }
}

/**
 * The confirmation step shown before any code is sent: who will be contacted and how.
 * For "any one guardian" actions the user picks which guardian to ask.
 */
@Composable
private fun ConfirmSend(
    requirement: AuthorizationPolicy.Requirement,
    busy: Boolean,
    onSend: (String?) -> Unit,
    onCancel: () -> Unit,
) {
    val anyOne = requirement.rule == AuthorizationPolicy.ApproverRule.ANY_ONE
    var chosenId by rememberSaveable { mutableStateOf(requirement.approvers.firstOrNull()?.id) }
    val recipients = if (anyOne) {
        requirement.approvers.filter { it.id == chosenId } + requirement.contactChecks
    } else {
        requirement.approvers + requirement.contactChecks
    }

    SectionLabel(
        when {
            anyOne && requirement.approvers.size > 1 -> "One guardian's approval is enough — who should get the code?"
            anyOne -> "This needs your guardian's approval."
            requirement.approvers.isEmpty() -> "Each guardian will get a code to confirm their contact."
            else -> "This needs approval from every guardian."
        }
    )

    if (anyOne && requirement.approvers.size > 1) {
        requirement.approvers.forEach { guardian ->
            val selected = guardian.id == chosenId
            KohliCard(
                onClick = if (busy) null else ({ chosenId = guardian.id }),
                borderColor = if (selected) KohliColors.Accent else KohliColors.Outline,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(guardian.name, style = MaterialTheme.typography.titleMedium)
                        MutedText("${guardian.channel.label} · ${guardian.maskedDestination}")
                    }
                    if (selected) StatusPill("Selected", KohliColors.Accent)
                }
            }
        }
    }

    KohliCard {
        Eyebrow("A one-time code will be sent to")
        recipients.forEach { guardian ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(guardian.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                MutedText("${guardian.channel.label} · ${guardian.maskedDestination}")
            }
        }
        MutedText("Nothing is sent until you tap Send. Codes expire after 15 minutes.")
    }

    PrimaryButton(
        text = if (recipients.size > 1) "Send codes" else "Send code",
        onClick = { onSend(if (anyOne) chosenId else null) },
        enabled = !busy && recipients.isNotEmpty(),
    )
    GhostButton("Cancel — don't send anything", onClick = onCancel, color = KohliColors.Muted, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ActiveRequest(
    request: AuthorizationRequest,
    guardians: List<Guardian>,
    busy: Boolean,
    onSubmit: (String, String) -> Unit,
    onResend: (String) -> Unit,
    onAskAnother: (String) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
) {
    val pending = request.status == RequestStatus.PENDING && !request.isExpired()
    val (statusText, tone) = when {
        request.status == RequestStatus.APPROVED -> "✅ Approved — change applied." to MessageTone.SUCCESS
        request.status == RequestStatus.PENDING && request.isExpired() -> "⌛ Expired. Start again if you still need this." to MessageTone.ERROR
        request.status == RequestStatus.PENDING ->
            "Waiting for ${if (request.rule == AuthorizationPolicy.ApproverRule.ANY_ONE) "one guardian's" else "every guardian's"} code · expires ${time(request.expiresAt)}" to MessageTone.INFO
        else -> "${request.status.name.lowercase().replaceFirstChar { it.uppercase() }}: ${request.outcome ?: ""}" to MessageTone.ERROR
    }
    InlineMessage(statusText, tone = tone)

    request.challenges.forEach { challenge ->
        ChallengeCard(challenge, pending && !busy, onSubmit = { code -> onSubmit(challenge.key, code) }, onResend = { onResend(challenge.key) })
    }

    if (pending && request.rule == AuthorizationPolicy.ApproverRule.ANY_ONE) {
        guardians.filter { g -> request.approvals.none { it.guardian.id == g.id } }.forEach { other ->
            GhostButton("Ask ${other.name} instead", onClick = { onAskAnother(other.id) })
        }
    }

    PrimaryButton(if (pending) "Come back later" else "Done", onClick = onClose)
    if (pending) {
        GhostButton("Cancel request", onClick = onCancel, color = KohliColors.Missing, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ChallengeCard(challenge: Challenge, enabled: Boolean, onSubmit: (String) -> Unit, onResend: () -> Unit) {
    var code by rememberSaveable(challenge.key) { mutableStateOf("") }
    val guardian = challenge.guardian
    val accent = when {
        challenge.isVerified -> KohliColors.Logged
        challenge.sendError != null -> KohliColors.Missing
        else -> KohliColors.OutlineStrong
    }
    AccentBanner(accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(guardian.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusPill(
                if (challenge.purpose == ChallengePurpose.APPROVAL) "Approval" else "Confirm contact",
                KohliColors.Muted,
            )
        }
        when {
            challenge.isVerified -> Text("✓ Verified", style = MaterialTheme.typography.titleSmall, color = KohliColors.Logged)
            challenge.sendError != null -> Text(challenge.sendError, style = MaterialTheme.typography.bodySmall, color = KohliColors.Missing)
            challenge.sentAt != null -> MutedText(
                "Code sent by ${guardian.channel.label} to ${guardian.maskedDestination} at ${time(challenge.sentAt)}"
            )
            else -> MutedText("Sending…")
        }
        if (!challenge.isVerified && enabled) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                label = { Text("Code from ${guardian.name}") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 8.sp,
                    textAlign = TextAlign.Center,
                ),
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton("Verify", onClick = { onSubmit(code); code = "" }, enabled = code.length == 6, fillWidth = false)
                Spacer(Modifier.width(8.dp))
                GhostButton("Send a new code", onClick = onResend, color = KohliColors.Muted)
            }
        }
    }
}
