package com.vishnu.kohliprotocol.data.reports

import android.content.Context
import android.util.Log
import com.vishnu.kohliprotocol.data.ai.AiException
import com.vishnu.kohliprotocol.data.ai.AiProviderFactory
import com.vishnu.kohliprotocol.data.ai.WeekDay
import com.vishnu.kohliprotocol.data.ai.WeekLog
import com.vishnu.kohliprotocol.data.ai.WeeklySummary
import com.vishnu.kohliprotocol.data.email.EmailAttachment
import com.vishnu.kohliprotocol.data.email.EmailDeliveryException
import com.vishnu.kohliprotocol.data.local.entity.AuditAction
import com.vishnu.kohliprotocol.data.local.entity.WeeklyReportEntity
import com.vishnu.kohliprotocol.data.preferences.PreferencesManager
import com.vishnu.kohliprotocol.data.repository.AuditRepository
import com.vishnu.kohliprotocol.data.repository.EnforcementRepository
import com.vishnu.kohliprotocol.data.repository.FoodRepository
import com.vishnu.kohliprotocol.weekly.WeeklyEvaluationManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The weekly reporting pipeline (spec §18–19), run after each week is evaluated:
 * week data → AI summary (the provider abstraction; Gemini's free chain by default) →
 * A4 PDF in `files/reports/` → emailed to you through the REPORT Brevo account.
 *
 * The AI summary is retried a few times on temporary failures; after that the PDF and email go
 * out without it, so a flaky provider never blocks the report.
 */
class WeeklyReportManager(
    context: Context,
    private val food: FoodRepository,
    private val enforcement: EnforcementRepository,
    private val providers: AiProviderFactory,
    private val emailStore: ReportEmailStore,
    private val preferences: PreferencesManager,
    private val audit: AuditRepository,
) {
    sealed interface Outcome {
        data class Done(val file: File, val emailed: Boolean, val emailError: String?, val aiError: String?) : Outcome
        /** A temporary AI failure; try again later before sending a summary-less report. */
        data class RetryLater(val reason: String) : Outcome
    }

    private val reportsDir = File(context.filesDir, "reports")
    private val pdf = PdfReportGenerator()
    private val mutex = Mutex()

    /**
     * Builds and/or emails every recent report that still needs it. Returns true if a
     * temporary AI failure means the caller should retry later.
     */
    suspend fun processPending(allowAiRetry: Boolean): Boolean {
        val emailConfigured = emailStore.mailer() != null
        val cutoff = System.currentTimeMillis() - AUTO_EMAIL_WINDOW_MILLIS
        var retry = false
        for (report in enforcement.observeWeeklyReports().first().take(MAX_REPORTS)) {
            val needsPdf = report.pdfPath?.let { !File(it).isFile } ?: true
            val needsEmail = emailConfigured && report.generatedAt >= cutoff && !emailStore.isEmailed(report.id)
            if (!needsPdf && !needsEmail) continue
            if (process(report, allowAiRetry) is Outcome.RetryLater) retry = true
        }
        return retry
    }

    /**
     * Generates [report]'s PDF and emails it if not yet emailed.
     * [freshSummary] asks the AI again; [forceEmail] re-sends even if already emailed.
     */
    suspend fun process(
        report: WeeklyReportEntity,
        allowAiRetry: Boolean = false,
        freshSummary: Boolean = false,
        forceEmail: Boolean = false,
    ): Outcome = mutex.withLock {
        val week = weekLog(report.startDate, report.endDate, report.biryaniParameter, report.averageRating,
            report.logsComplete, if (report.isSuccess) "SUCCESS" else "WEEK FAILED")

        var summary = if (freshSummary) null else WeeklySummary.fromStored(report.aiSummary)
        var aiError: String? = null
        if (summary == null) {
            try {
                summary = providers.create().summarizeWeek(week)
                enforcement.setWeeklyAiSummary(report.id, summary.toJson().toString())
            } catch (e: AiException) {
                if (allowAiRetry && e.retryable) return@withLock Outcome.RetryLater(e.message ?: "AI unavailable")
                aiError = e.message
            }
        }

        val file = File(reportsDir, "weekly_report_${report.startDate}.pdf")
        pdf.render(reportData(week, report.isSuccess, summary, aiError, isPreview = false), file)
        enforcement.setWeeklyPdfPath(report.id, file.absolutePath)
        audit.log(AuditAction.WEEKLY_REPORT_PDF_CREATED, "Weekly report ${range(week)}", metadata = file.name)

        var emailed = false
        var emailError: String? = null
        if (forceEmail || !emailStore.isEmailed(report.id)) {
            emailError = email(file, week, summary, gamesUnlocked = report.isSuccess)
            if (emailError == null) {
                emailStore.markEmailed(report.id)
                emailed = true
            }
        }
        Outcome.Done(file, emailed, emailError, aiError)
    }

    /** A PDF of the week in progress, for checking the report and email setup mid-week. */
    suspend fun preview(today: LocalDate = LocalDate.now()): Outcome.Done = mutex.withLock {
        val start = WeeklyEvaluationManager.weekStart(today, preferences.ensureProtocolStartDate(today))
        val end = start.plusDays(6)
        val analyses = food.getAnalyses(start, end).filter { it.rejectionReason == null }
        val average = if (analyses.isEmpty()) null else analyses.sumOf { it.rating }.toFloat() / analyses.size
        val completeSoFar = food.isRangeComplete(start, today)
        val week = weekLog(start, end, enforcement.biryaniParameter.first(), average ?: 0f, completeSoFar, "IN PROGRESS")
            .copy(average = average)

        var aiError: String? = null
        val summary = try {
            providers.create().summarizeWeek(week)
        } catch (e: AiException) {
            aiError = e.message
            null
        }
        val file = File(reportsDir, "weekly_report_preview.pdf")
        pdf.render(reportData(week, gamesUnlocked = null, summary, aiError, isPreview = true), file)
        Outcome.Done(file, emailed = false, emailError = null, aiError = aiError)
    }

    /** Emails an already-rendered preview, to test the report Brevo settings. Returns an error or null. */
    suspend fun emailPreview(file: File): String? {
        val mailer = emailStore.mailer() ?: return "Report email isn't set up yet."
        return try {
            mailer.sender.send(
                to = mailer.recipientEmail,
                toName = "Vishnu",
                subject = "Kohli Protocol — test report email",
                text = "This is a test of weekly report delivery. The preview PDF is attached.",
                attachments = listOf(EmailAttachment(file.name, file.readBytes())),
            )
            null
        } catch (e: EmailDeliveryException) {
            e.message
        }
    }

    // --- Internals ---------------------------------------------------------------------------------

    private suspend fun weekLog(
        start: LocalDate,
        end: LocalDate,
        parameter: Float,
        average: Float,
        logsComplete: Boolean,
        result: String,
    ): WeekLog {
        val days = (0L..6L).map { offset ->
            val date = start.plusDays(offset)
            WeekDay(date, food.getMeals(date), food.getArbitraryFood(date), food.getAnalysis(date))
        }
        return WeekLog(start, end, parameter, average.takeIf { it > 0f }, logsComplete, result, days)
    }

    private fun reportData(
        week: WeekLog,
        gamesUnlocked: Boolean?,
        summary: WeeklySummary?,
        aiError: String?,
        isPreview: Boolean,
    ) = ReportData(
        start = week.start,
        end = week.end,
        biryaniParameter = week.biryaniParameter,
        average = week.average,
        logsComplete = week.logsComplete,
        result = week.result,
        gamesUnlocked = gamesUnlocked,
        days = week.days.map { day ->
            ReportDay(day.date, day.analysis, logged = day.meals.any { it.isLogged } || day.extraFood.isNotEmpty())
        },
        summary = summary,
        summaryNote = aiError,
        isPreview = isPreview,
    )

    /** Returns an error message, or null if sent. */
    private suspend fun email(file: File, week: WeekLog, summary: WeeklySummary?, gamesUnlocked: Boolean): String? {
        val mailer = emailStore.mailer() ?: return "Report email isn't set up."
        val averageText = week.average?.let { String.format(Locale.US, "%.2f", it) } ?: "—"
        val body = buildString {
            appendLine("Week ${range(week)}")
            appendLine("Biryani Parameter ${week.biryaniParameter} · weekly average $averageText")
            appendLine("Result: ${week.result} — games ${if (gamesUnlocked) "unlocked" else "locked"}")
            appendLine()
            summary?.let {
                appendLine(it.summary)
                appendLine()
                appendLine("Next week's focus: ${it.nextWeekFocus}")
                appendLine()
            }
            append("The full report is attached.")
        }
        return try {
            mailer.sender.send(
                to = mailer.recipientEmail,
                toName = "Vishnu",
                subject = "Kohli Protocol weekly report · ${range(week)} · ${week.result}",
                text = body,
                attachments = listOf(EmailAttachment(file.name, file.readBytes())),
            )
            audit.log(AuditAction.WEEKLY_REPORT_EMAILED, "Weekly report ${range(week)} emailed", metadata = file.name)
            null
        } catch (e: EmailDeliveryException) {
            Log.w(TAG, "Report email failed: ${e.message}")
            audit.log(AuditAction.WEEKLY_REPORT_EMAIL_FAILED, "Weekly report ${range(week)}: ${e.message}")
            e.message
        }
    }

    private fun range(week: WeekLog) = "${week.start.format(DAY)} – ${week.end.format(DAY)}"

    private companion object {
        const val TAG = "KohliProtocol"
        const val MAX_REPORTS = 8
        /** Reports older than this aren't auto-emailed when email is configured later. */
        const val AUTO_EMAIL_WINDOW_MILLIS = 14L * 24 * 60 * 60 * 1000
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
    }
}
