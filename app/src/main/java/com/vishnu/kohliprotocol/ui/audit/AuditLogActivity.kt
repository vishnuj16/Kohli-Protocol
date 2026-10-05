package com.vishnu.kohliprotocol.ui.audit

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vishnu.kohliprotocol.KohliApplication
import com.vishnu.kohliprotocol.data.local.entity.AuditAction
import com.vishnu.kohliprotocol.data.local.entity.AuditEventEntity
import com.vishnu.kohliprotocol.data.repository.AuditRepository
import com.vishnu.kohliprotocol.security.SecureActivity
import com.vishnu.kohliprotocol.ui.components.ChoiceChips
import com.vishnu.kohliprotocol.ui.components.KohliCard
import com.vishnu.kohliprotocol.ui.components.MutedText
import com.vishnu.kohliprotocol.ui.components.PageScaffold
import com.vishnu.kohliprotocol.ui.components.StatusPill
import com.vishnu.kohliprotocol.ui.theme.KohliColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Read-only, fingerprint-protected history of everything recorded in the audit log. */
class AuditLogActivity : SecureActivity() {

    override val screenName = "Audit log"

    private val viewModel: AuditLogViewModel by viewModels { AuditLogViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSecureContent { AuditLogScreen(viewModel, onBack = ::finish) }
    }
}

class AuditLogViewModel(audit: AuditRepository) : ViewModel() {
    val events: StateFlow<List<AuditEventEntity>?> = audit.observeRecent(limit = 1000)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AuditLogViewModel((this[APPLICATION_KEY] as KohliApplication).container.auditRepository)
            }
        }
    }
}

private enum class AuditFilter(val label: String) { ALL("All"), SECURITY("Security"), FOOD_AI("Food & AI"), ENFORCEMENT("Enforcement") }

private fun AuditAction.group(): AuditFilter = when {
    name.startsWith("BIOMETRIC") || name.startsWith("GUARDIAN") || name.startsWith("EMERGENCY") ||
        name.startsWith("OVERRIDE") || name.startsWith("BIRYANI") || name.startsWith("RESTRICTED_APP") ||
        this == AuditAction.GAMES_TEST_OVERRIDE -> AuditFilter.SECURITY
    name.startsWith("FOOD") || name.startsWith("AI_") || this == AuditAction.MEAL_SKIPPED -> AuditFilter.FOOD_AI
    else -> AuditFilter.ENFORCEMENT
}

/** Events that indicate something went wrong or a rule was weakened are highlighted. */
private fun AuditAction.isAlert(): Boolean = this in setOf(
    AuditAction.BIOMETRIC_AUTH_FAILED, AuditAction.BIOMETRIC_UNAVAILABLE, AuditAction.GUARDIAN_AUTH_FAILED,
    AuditAction.GUARDIAN_AUTH_REJECTED, AuditAction.BIRYANI_PARAMETER_DECREASE_BLOCKED, AuditAction.SUSPICIOUS_CLOCK_CHANGE,
    AuditAction.ENFORCEMENT_DISABLED, AuditAction.EMERGENCY_OVERRIDE_APPROVED, AuditAction.RESTRICTED_APP_REMOVED,
)

private val timestampFormat = DateTimeFormatter.ofPattern("d MMM HH:mm:ss")

@Composable
private fun AuditLogScreen(viewModel: AuditLogViewModel, onBack: () -> Unit) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(AuditFilter.ALL) }

    PageScaffold(title = "Audit log", onBack = onBack) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ChoiceChips(
                options = AuditFilter.entries,
                selected = filter,
                label = { it.label },
                onSelect = { filter = it },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val visible = events.orEmpty().filter { filter == AuditFilter.ALL || it.actionType.group() == filter }
            if (events != null && visible.isEmpty()) {
                MutedText("Nothing recorded yet.", Modifier.padding(20.dp))
            }
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.id }) { event -> AuditRow(event) }
            }
        }
    }
}

@Composable
private fun AuditRow(event: AuditEventEntity) {
    val time = Instant.ofEpochMilli(event.timestamp).atZone(ZoneId.systemDefault()).format(timestampFormat)
    val color = if (event.actionType.isAlert()) KohliColors.Warning else KohliColors.Accent
    KohliCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), verticalSpacing = 4.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(event.actionType.name.replace('_', ' '), color)
            Spacer(Modifier.weight(1f))
            Text(time, style = MaterialTheme.typography.labelSmall, color = KohliColors.Muted)
        }
        Text(event.description, style = MaterialTheme.typography.bodyMedium)
        event.metadata?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = KohliColors.Muted) }
    }
}
