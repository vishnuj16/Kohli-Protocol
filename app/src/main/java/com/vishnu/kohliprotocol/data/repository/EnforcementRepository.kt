package com.vishnu.kohliprotocol.data.repository

import com.vishnu.kohliprotocol.data.guardian.GuardianApproval
import com.vishnu.kohliprotocol.data.local.dao.WeeklyReportDao
import com.vishnu.kohliprotocol.data.local.entity.AuditAction
import com.vishnu.kohliprotocol.data.local.entity.DailyCategory
import com.vishnu.kohliprotocol.data.local.entity.WeeklyReportEntity
import com.vishnu.kohliprotocol.data.preferences.PreferencesManager
import com.vishnu.kohliprotocol.data.restrictions.EmergencyOverrideState
import com.vishnu.kohliprotocol.data.restrictions.RestrictionCategory
import com.vishnu.kohliprotocol.data.restrictions.RestrictionLists
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Everything the game lock needs, as one snapshot. Access is decided only by the latest real
 * weekly evaluation against the Biryani Parameter.
 */
data class GameAccessState(
    /** The result of the latest weekly evaluation (games start locked). */
    val unlockedByEvaluation: Boolean,
    val latestReport: WeeklyReportEntity?,
    val gamePackages: Set<String>,
) {
    fun isUnlocked(): Boolean = unlockedByEvaluation

    /** Why games are locked, for the restriction overlay. */
    fun lockReason(): String {
        val report = latestReport ?: return "Games start locked until your first successful week."
        return when {
            !report.logsComplete -> "Last week's food logs were incomplete."
            else -> String.format(
                Locale.US,
                "Weekly average %.2f is below the Biryani Parameter %.2f.",
                report.averageRating,
                report.biryaniParameter,
            )
        }
    }
}

/** Everything the enforcement service needs to decide what to block, as one snapshot. */
data class EnforcementSnapshot(
    val lists: RestrictionLists,
    val games: GameAccessState,
    val emergencyOverride: EmergencyOverrideState?,
)

/**
 * The Biryani Parameter, restricted apps, game access, emergency overrides, weekly results and
 * enforcement/clock events. Anything that weakens enforcement requires a [GuardianApproval].
 */
class EnforcementRepository(
    private val weeklyReportDao: WeeklyReportDao,
    private val preferences: PreferencesManager,
    private val audit: AuditRepository,
) {
    sealed interface BiryaniChange {
        data class Applied(val previous: Float, val current: Float) : BiryaniChange
        /** Lowering the standard needs Guardian Gate authorization (Phase 6). */
        data class GuardianRequired(val current: Float, val requested: Float) : BiryaniChange
    }

    val biryaniParameter: Flow<Float> = preferences.biryaniParameter
    val isGamesUnlocked: Flow<Boolean> = preferences.isGamesUnlocked

    val restrictionLists: Flow<RestrictionLists> = preferences.restrictionLists

    val gameAccess: Flow<GameAccessState> = combine(
        preferences.isGamesUnlocked,
        weeklyReportDao.observeLatest(),
        preferences.restrictionLists,
    ) { unlocked, latest, lists ->
        GameAccessState(
            unlockedByEvaluation = unlocked,
            latestReport = latest,
            gamePackages = lists[RestrictionCategory.GAMES],
        )
    }

    val emergencyOverride: Flow<EmergencyOverrideState?> = preferences.emergencyOverride

    val snapshot: Flow<EnforcementSnapshot> =
        combine(restrictionLists, gameAccess, emergencyOverride) { lists, games, override ->
            EnforcementSnapshot(lists, games, override)
        }
    val lastClockCheck: Flow<Long?> = preferences.lastClockCheck

    // --- Biryani Parameter -------------------------------------------------------------------

    /**
     * Raising the parameter is always allowed. Lowering it is refused without a Guardian Gate
     * [approval] — the user cannot simply lower the standard during a difficult week.
     */
    suspend fun setBiryaniParameter(value: Float, approval: GuardianApproval? = null): BiryaniChange {
        require(value in MIN_PARAMETER..MAX_PARAMETER) {
            "Biryani Parameter must be between $MIN_PARAMETER and $MAX_PARAMETER: $value"
        }
        val previous = preferences.changeBiryaniParameter(value, allowDecrease = approval != null)
        if (previous == null) {
            val current = preferences.biryaniParameter.first()
            audit.log(
                AuditAction.BIRYANI_PARAMETER_DECREASE_BLOCKED,
                "Attempted to lower Biryani Parameter $current → $value without Guardian Gate",
            )
            return BiryaniChange.GuardianRequired(current, value)
        }
        if (previous != value) {
            audit.log(
                AuditAction.BIRYANI_PARAMETER_CHANGED,
                "Biryani Parameter $previous → $value",
                metadata = approval?.auditMetadata,
            )
        }
        return BiryaniChange.Applied(previous, value)
    }

    // --- Game access -------------------------------------------------------------------------

    suspend fun setGamesUnlocked(unlocked: Boolean, reason: String) {
        val previous = preferences.setGamesUnlocked(unlocked)
        if (previous != unlocked) {
            audit.log(
                if (unlocked) AuditAction.GAMES_UNLOCKED else AuditAction.GAMES_LOCKED,
                reason,
            )
        }
    }

    // --- Restricted apps -------------------------------------------------------------------

    /** Making enforcement stricter needs no approval. Returns false if already restricted. */
    suspend fun addRestrictedApp(category: RestrictionCategory, packageName: String, label: String): Boolean {
        val clean = packageName.trim()
        require(PACKAGE_NAME.matches(clean)) { "Not a valid package name: $clean" }
        val added = preferences.addRestrictedApp(category, clean)
        if (added) {
            audit.log(AuditAction.RESTRICTED_APP_ADDED, "$label added to ${category.label}", metadata = clean)
        }
        return added
    }

    suspend fun removeRestrictedApp(category: RestrictionCategory, packageName: String, label: String, approval: GuardianApproval) {
        preferences.removeRestrictedApp(category, packageName)
        audit.log(
            AuditAction.RESTRICTED_APP_REMOVED,
            "$label ($packageName) removed from ${category.label}",
            metadata = approval.auditMetadata,
        )
    }

    // --- Emergency override ----------------------------------------------------------------

    suspend fun startEmergencyOverride(
        categories: Set<RestrictionCategory>,
        minutes: Int,
        reason: String,
        approval: GuardianApproval,
    ): EmergencyOverrideState {
        require(categories.isNotEmpty()) { "Choose at least one restriction to suspend" }
        require(minutes in 1..MAX_OVERRIDE_MINUTES) { "Override must be 1–$MAX_OVERRIDE_MINUTES minutes" }
        val state = EmergencyOverrideState(
            categories = categories,
            untilEpochMillis = System.currentTimeMillis() + minutes * 60_000L,
            requestId = approval.requestId,
            reason = reason,
        )
        preferences.setEmergencyOverride(state)
        audit.log(
            AuditAction.EMERGENCY_OVERRIDE_APPROVED,
            "Suspended ${categories.joinToString { it.label }} for $minutes min until ${formatTime(state.untilEpochMillis)}. Reason: $reason",
            metadata = approval.auditMetadata,
        )
        return state
    }

    /** Clears an override whose time is up and records that normal enforcement resumed. */
    suspend fun expireEmergencyOverride(now: Long = System.currentTimeMillis()) {
        val state = preferences.emergencyOverride.first() ?: return
        if (state.isActive(now)) return
        preferences.setEmergencyOverride(null)
        audit.log(
            AuditAction.EMERGENCY_OVERRIDE_EXPIRED,
            "Emergency override ended at ${formatTime(state.untilEpochMillis)}; restrictions restored",
            metadata = "request=${state.requestId}",
        )
    }

    private fun formatTime(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))

    // --- Weekly results ----------------------------------------------------------------------

    fun observeWeeklyReports(): Flow<List<WeeklyReportEntity>> = weeklyReportDao.observeAll()

    fun observeLatestWeeklyReport(): Flow<WeeklyReportEntity?> = weeklyReportDao.observeLatest()

    suspend fun getWeeklyReport(startDate: LocalDate): WeeklyReportEntity? = weeklyReportDao.getByStartDate(startDate)

    suspend fun latestWeeklyReport(): WeeklyReportEntity? = weeklyReportDao.observeLatest().first()

    /**
     * Records a week's result against the Biryani Parameter currently in force. Success is
     * computed here, never supplied by the caller: complete logs AND average ≥ parameter.
     */
    suspend fun recordWeeklyResult(
        startDate: LocalDate,
        endDate: LocalDate,
        averageRating: Float,
        logsComplete: Boolean,
        aiSummary: String? = null,
    ): WeeklyReportEntity {
        require(!endDate.isBefore(startDate)) { "endDate before startDate" }
        require(averageRating in 0f..DailyCategory.MAX_RATING.toFloat()) {
            "averageRating out of range: $averageRating"
        }
        val parameter = preferences.biryaniParameter.first()
        val success = logsComplete && averageRating >= parameter

        val report = WeeklyReportEntity(
            startDate = startDate,
            endDate = endDate,
            averageRating = averageRating,
            biryaniParameter = parameter,
            logsComplete = logsComplete,
            isSuccess = success,
            aiSummary = aiSummary,
            generatedAt = System.currentTimeMillis(),
        )
        val id = weeklyReportDao.insert(report)
        audit.log(
            AuditAction.WEEKLY_RESULT_GENERATED,
            "$startDate–$endDate: ${if (success) "SUCCESS" else "WEEK FAILED"} " +
                "(average %.2f vs parameter %.2f, logs %s)".format(
                    averageRating,
                    parameter,
                    if (logsComplete) "complete" else "incomplete",
                ),
        )
        return report.copy(id = id)
    }

    suspend fun setWeeklyAiSummary(reportId: Long, summary: String) {
        weeklyReportDao.setAiSummary(reportId, summary)
    }

    suspend fun setWeeklyPdfPath(reportId: Long, pdfPath: String) {
        weeklyReportDao.setPdfPath(reportId, pdfPath)
    }

    // --- Enforcement & clock -----------------------------------------------------------------

    suspend fun recordClockCheck(epochMillis: Long) = preferences.setLastClockCheck(epochMillis)

    suspend fun logSuspiciousClockChange(description: String) =
        audit.log(AuditAction.SUSPICIOUS_CLOCK_CHANGE, description)

    suspend fun logEnforcementState(active: Boolean, description: String) = audit.log(
        if (active) AuditAction.ENFORCEMENT_RESTORED else AuditAction.ENFORCEMENT_DISABLED,
        description,
    )

    companion object {
        const val MIN_PARAMETER = 1.0f
        const val MAX_PARAMETER = 5.0f

        const val MAX_OVERRIDE_MINUTES = 60

        private val PACKAGE_NAME = Regex("""[a-zA-Z][a-zA-Z0-9_]*(\.[a-zA-Z][a-zA-Z0-9_]*)+""")
    }
}
