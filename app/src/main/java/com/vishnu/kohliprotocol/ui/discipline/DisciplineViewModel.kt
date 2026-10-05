package com.vishnu.kohliprotocol.ui.discipline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vishnu.kohliprotocol.KohliApplication
import com.vishnu.kohliprotocol.data.apps.InstalledApp
import com.vishnu.kohliprotocol.data.apps.InstalledAppsSource
import com.vishnu.kohliprotocol.data.local.entity.AuditAction
import com.vishnu.kohliprotocol.data.local.entity.WeeklyReportEntity
import com.vishnu.kohliprotocol.data.reports.ReportEmailInfo
import com.vishnu.kohliprotocol.data.reports.ReportEmailStore
import com.vishnu.kohliprotocol.data.reports.WeeklyReportManager
import com.vishnu.kohliprotocol.data.repository.AuditRepository
import com.vishnu.kohliprotocol.data.repository.EnforcementRepository
import com.vishnu.kohliprotocol.data.repository.GameAccessState
import com.vishnu.kohliprotocol.data.restrictions.RestrictionCategory
import com.vishnu.kohliprotocol.data.restrictions.RestrictionLists
import com.vishnu.kohliprotocol.weekly.WeeklyEvaluationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

data class DisciplineState(
    val biryaniParameter: Float,
    val games: GameAccessState,
    val reports: List<WeeklyReportEntity>,
    /** The app's first launch; every week starts on this weekday. */
    val protocolStart: LocalDate?,
    val lists: RestrictionLists,
)

class DisciplineViewModel(
    private val enforcement: EnforcementRepository,
    private val weekly: WeeklyEvaluationManager,
    private val installedApps: InstalledAppsSource,
    private val reports: WeeklyReportManager,
    private val reportEmail: ReportEmailStore,
    private val audit: AuditRepository,
) : ViewModel() {

    val reportEmailInfo: StateFlow<ReportEmailInfo?> = reportEmail.info
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val emailedIds: StateFlow<Set<String>> = reportEmail.emailedReportIds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    private val _reportBusy = MutableStateFlow(false)
    val reportBusy: StateFlow<Boolean> = _reportBusy.asStateFlow()

    /** A PDF the screen should open (one-shot). */
    private val _pdfToOpen = MutableStateFlow<String?>(null)
    val pdfToOpen: StateFlow<String?> = _pdfToOpen.asStateFlow()

    private var lastPreview: java.io.File? = null

    fun pdfOpened() {
        _pdfToOpen.value = null
    }

    fun saveReportEmail(apiKey: String, sender: String, senderName: String, recipient: String) {
        if (sender.isBlank() || recipient.isBlank()) {
            _message.value = "Enter the verified sender email and your own email."
            return
        }
        viewModelScope.launch {
            reportEmail.save(apiKey, sender, senderName, recipient)
            audit.log(AuditAction.REPORT_EMAIL_CONFIGURED, "Weekly reports: ${sender.trim()} → ${recipient.trim()}")
            _message.value = "Report email saved."
        }
    }

    fun previewReport() = runReport {
        val outcome = reports.preview()
        lastPreview = outcome.file
        _pdfToOpen.value = outcome.file.absolutePath
        _message.value = "Preview ready." + (outcome.aiError?.let { " AI summary unavailable: $it" } ?: "")
    }

    fun emailPreview() = runReport {
        val file = lastPreview?.takeIf { it.isFile } ?: reports.preview().file.also { lastPreview = it }
        _message.value = reports.emailPreview(file) ?: "Test report emailed — check your inbox."
    }

    fun rebuildReport(report: WeeklyReportEntity) = runReport {
        when (val outcome = reports.process(report, freshSummary = true, forceEmail = true)) {
            is WeeklyReportManager.Outcome.Done -> {
                _message.value = listOfNotNull(
                    "Report rebuilt.",
                    if (outcome.emailed) "Emailed." else outcome.emailError?.let { "Not emailed: $it" },
                    outcome.aiError?.let { "AI summary unavailable: $it" },
                ).joinToString(" ")
            }
            is WeeklyReportManager.Outcome.RetryLater -> {
                _message.value = "AI busy: ${outcome.reason}"
            }
        }
    }

    private fun runReport(block: suspend () -> Unit) {
        if (_reportBusy.value) return
        _reportBusy.value = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _message.value = "Report failed: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                _reportBusy.value = false
            }
        }
    }

    val state: StateFlow<DisciplineState?> = combine(
        enforcement.biryaniParameter,
        enforcement.gameAccess,
        enforcement.observeWeeklyReports(),
        weekly.protocolStart,
        enforcement.restrictionLists,
    ) { parameter, games, reports, start, lists -> DisciplineState(parameter, games, reports, start, lists) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Raises only — the screen sends decreases to Guardian Gate. The repository refuses them anyway. */
    fun saveParameter(value: Float) {
        viewModelScope.launch {
            _message.value = when (val result = enforcement.setBiryaniParameter(roundParameter(value))) {
                is EnforcementRepository.BiryaniChange.Applied -> "Biryani Parameter set to ${result.current}."
                is EnforcementRepository.BiryaniChange.GuardianRequired -> "Lowering it needs Guardian Gate."
            }
        }
    }

    private val _apps = MutableStateFlow<List<InstalledApp>?>(null)

    /** Installed apps for the game picker; null while loading. */
    val apps: StateFlow<List<InstalledApp>?> = _apps.asStateFlow()

    /** Reloads every time the picker opens, so newly installed games appear. */
    fun loadApps() {
        _apps.value = null
        viewModelScope.launch { _apps.value = runCatching { installedApps.load() }.getOrDefault(emptyList()) }
    }

    /** Adding a restriction makes the protocol stricter, so no approval is needed. */
    fun addApp(category: RestrictionCategory, app: InstalledApp) {
        viewModelScope.launch {
            _message.value = runCatching { enforcement.addRestrictedApp(category, app.packageName, app.label) }.fold(
                onSuccess = { added -> if (added) "${app.label} added to ${category.label}." else "${app.label} is already there." },
                onFailure = { it.message ?: "Couldn't add ${app.label}." },
            )
        }
    }

    fun evaluateNow() {
        viewModelScope.launch {
            val outcomes = weekly.evaluatePendingWeeks()
            _message.value = if (outcomes.isEmpty()) {
                "No finished weeks awaiting evaluation."
            } else {
                outcomes.joinToString("\n") { outcome ->
                    when (outcome) {
                        is WeeklyEvaluationManager.WeekOutcome.Recorded ->
                            "Week of ${outcome.report.startDate}: ${if (outcome.report.isSuccess) "SUCCESS" else "WEEK FAILED"}"
                        is WeeklyEvaluationManager.WeekOutcome.Deferred ->
                            "Week of ${outcome.weekStart}: waiting — ${outcome.reason}"
                    }
                }
            }
        }
    }

    companion object {
        /** One decimal place, so +/- steps don't accumulate float noise (3.6000001). */
        fun roundParameter(value: Float): Float = (value * 10).roundToInt() / 10f

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as KohliApplication).container
                DisciplineViewModel(
                    container.enforcementRepository,
                    container.weeklyEvaluationManager,
                    InstalledAppsSource(this[APPLICATION_KEY] as KohliApplication),
                    container.weeklyReportManager,
                    container.reportEmailStore,
                    container.auditRepository,
                )
            }
        }
    }
}
