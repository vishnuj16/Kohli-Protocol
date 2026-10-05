package com.vishnu.kohliprotocol.data.reports

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.vishnu.kohliprotocol.data.ai.WeeklySummary
import com.vishnu.kohliprotocol.data.local.entity.DailyAnalysisEntity
import com.vishnu.kohliprotocol.data.local.entity.DailyCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** One row of the 7-day table. */
data class ReportDay(val date: LocalDate, val analysis: DailyAnalysisEntity?, val logged: Boolean)

/** Everything printed in a weekly report (spec §19). */
data class ReportData(
    val start: LocalDate,
    val end: LocalDate,
    val biryaniParameter: Float,
    val average: Float?,
    val logsComplete: Boolean,
    /** "SUCCESS", "WEEK FAILED" or "IN PROGRESS". */
    val result: String,
    /** Game access earned by this week; null while the week is still in progress. */
    val gamesUnlocked: Boolean?,
    val days: List<ReportDay>,
    val summary: WeeklySummary?,
    /** Why there is no summary, when there isn't one. */
    val summaryNote: String?,
    val isPreview: Boolean,
    val generatedAt: Long = System.currentTimeMillis(),
) {
    /** The five-category label for the weekly average (spec §19 "weekly category"). */
    val weeklyCategory: DailyCategory?
        get() = average?.takeIf { it > 0f }?.let { DailyCategory.fromRating(it.roundToInt().coerceIn(1, 5)) }
}

/** Renders [ReportData] to an A4 PDF with Android's built-in PdfDocument — no third-party libraries. */
class PdfReportGenerator {

    suspend fun render(data: ReportData, file: File) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val document = PdfDocument()
        try {
            Writer(document, data.generatedAt).apply {
                header(data)
                stats(data)
                chart(data)
                table(data)
                aiSection(data)
                finish()
            }
            val temp = File(file.parentFile, "${file.name}.tmp")
            temp.outputStream().use { document.writeTo(it) }
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
        } finally {
            document.close()
        }
    }

    /** Cursor-based page writer: starts a new page whenever the next block won't fit. */
    private class Writer(private val document: PdfDocument, private val generatedAt: Long) {
        private var pageNumber = 0
        private lateinit var page: PdfDocument.Page
        private val canvas: Canvas get() = page.canvas
        private var y = 0f

        init {
            newPage()
        }

        private fun newPage() {
            if (pageNumber > 0) closePage()
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            y = MARGIN
        }

        private fun closePage() {
            val stamp = Instant.ofEpochMilli(generatedAt).atZone(ZoneId.systemDefault()).format(STAMP)
            canvas.drawLine(MARGIN, PAGE_H - 34f, PAGE_W - MARGIN, PAGE_H - 34f, rule)
            canvas.drawText("Kohli Protocol · generated $stamp", MARGIN, PAGE_H - 20f, small)
            val label = "page $pageNumber"
            canvas.drawText(label, PAGE_W - MARGIN - small.measureText(label), PAGE_H - 20f, small)
            document.finishPage(page)
        }

        fun finish() = closePage()

        private fun ensure(height: Float) {
            if (y + height > PAGE_H - FOOTER) newPage()
        }

        private fun paragraph(text: String, paint: TextPaint, indent: Float = 0f, after: Float = 6f) {
            val width = (CONTENT_W - indent).toInt()
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.15f)
                .build()
            ensure(layout.height.toFloat())
            canvas.save()
            canvas.translate(MARGIN + indent, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + after
        }

        fun header(data: ReportData) {
            canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 86f, fill(INK))
            canvas.drawText("KOHLI PROTOCOL — WEEKLY PERFORMANCE REPORT", MARGIN, 38f, title)
            val range = "${data.start.format(RANGE)} – ${data.end.format(RANGE_YEAR)}"
            canvas.drawText(if (data.isPreview) "$range · PREVIEW (week in progress)" else range, MARGIN, 62f, subtitle)
            y = 110f
        }

        fun stats(data: ReportData) {
            ensure(110f)
            val boxes = listOf(
                "BIRYANI PARAMETER" to data.biryaniParameter.toString(),
                "WEEKLY AVERAGE" to (data.average?.let { String.format(Locale.US, "%.2f", it) } ?: "—"),
                "WEEKLY CATEGORY" to (data.weeklyCategory?.label ?: "—"),
                "LOGS COMPLETE" to if (data.logsComplete) "Yes" else "No",
            )
            val gap = 10f
            val width = (CONTENT_W - gap * (boxes.size - 1)) / boxes.size
            boxes.forEachIndexed { i, (label, value) ->
                val left = MARGIN + i * (width + gap)
                canvas.drawRoundRect(RectF(left, y, left + width, y + 58f), 6f, 6f, fill(PANEL))
                canvas.drawText(label, left + 10f, y + 20f, small)
                canvas.drawText(value, left + 10f, y + 44f, statValue)
            }
            y += 74f

            val (badgeText, badgeColor) = when (data.gamesUnlocked) {
                true -> "GAMES UNLOCKED" to GREEN
                false -> "GAMES LOCKED" to RED
                null -> "RESULT PENDING" to GREY
            }
            val badgeWidth = badge.measureText(badgeText) + 28f
            canvas.drawRoundRect(RectF(MARGIN, y, MARGIN + badgeWidth, y + 28f), 14f, 14f, fill(badgeColor))
            canvas.drawText(badgeText, MARGIN + 14f, y + 19f, badge)
            canvas.drawText("Result: ${data.result}", MARGIN + badgeWidth + 14f, y + 19f, bodyBold)
            y += 46f
        }

        /** Daily rating bars against the Biryani Parameter line. */
        fun chart(data: ReportData) {
            val height = 150f
            ensure(height + 40f)
            canvas.drawText("DAILY RATINGS", MARGIN, y + 10f, sectionTitle)
            y += 22f
            val top = y
            val bottom = y + height - 20f
            val scale = (bottom - top) / 5f
            canvas.drawLine(MARGIN, bottom, PAGE_W - MARGIN, bottom, rule)

            val slot = CONTENT_W / data.days.size
            data.days.forEachIndexed { i, day ->
                val x = MARGIN + i * slot
                val rating = day.analysis?.takeIf { it.rejectionReason == null }?.rating
                if (rating != null) {
                    val barTop = bottom - rating * scale
                    val color = when {
                        rating <= 2 -> RED
                        rating.toFloat() < data.biryaniParameter -> AMBER
                        else -> GREEN
                    }
                    canvas.drawRect(x + slot * 0.25f, barTop, x + slot * 0.75f, bottom, fill(color))
                    canvas.drawText(rating.toString(), x + slot / 2 - 4f, barTop - 4f, bodyBold)
                } else {
                    canvas.drawText("—", x + slot / 2 - 4f, bottom - 6f, small)
                }
                val dayLabel = day.date.format(DAY_SHORT)
                canvas.drawText(dayLabel, x + slot / 2 - small.measureText(dayLabel) / 2, bottom + 14f, small)
            }
            val targetY = bottom - data.biryaniParameter * scale
            canvas.drawLine(MARGIN, targetY, PAGE_W - MARGIN, targetY, targetLine)
            canvas.drawText("target ${data.biryaniParameter}", PAGE_W - MARGIN - 70f, targetY - 4f, small)
            y = bottom + 34f
        }

        fun table(data: ReportData) {
            val columns = floatArrayOf(0f, 120f, 250f, 310f)
            val rowHeight = 22f
            ensure(rowHeight * (data.days.size + 2) + 20f)
            canvas.drawText("7-DAY BREAKDOWN", MARGIN, y + 10f, sectionTitle)
            y += 20f

            canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + rowHeight, fill(INK))
            listOf("Date", "Calories (kcal)", "Rating", "Category").forEachIndexed { i, h ->
                canvas.drawText(h, MARGIN + 8f + columns[i], y + 15f, tableHeader)
            }
            y += rowHeight

            data.days.forEachIndexed { index, day ->
                if (index % 2 == 1) canvas.drawRect(MARGIN, y, PAGE_W - MARGIN, y + rowHeight, fill(PANEL))
                val analysis = day.analysis
                val cells = listOf(
                    day.date.format(ROW_DATE),
                    analysis?.let { "${fmt(it.minCalories)} – ${fmt(it.maxCalories)}" } ?: "—",
                    analysis?.rating?.let { "$it / 5" } ?: "—",
                    when {
                        analysis != null && analysis.rejectionReason != null -> "${analysis.category.label} (disputed)"
                        analysis != null -> analysis.category.label
                        day.logged -> "not analyzed"
                        else -> "not logged"
                    },
                )
                cells.forEachIndexed { i, cell -> canvas.drawText(cell, MARGIN + 8f + columns[i], y + 15f, body) }
                y += rowHeight
            }
            y += 18f
        }

        fun aiSection(data: ReportData) {
            ensure(40f)
            canvas.drawText("AI WEEKLY REVIEW", MARGIN, y + 10f, sectionTitle)
            y += 22f
            val summary = data.summary
            if (summary == null) {
                paragraph("AI summary unavailable. ${data.summaryNote.orEmpty()}".trim(), bodyText)
                return
            }
            paragraph(summary.summary, bodyText, after = 12f)
            bulletList("Good behaviours", summary.goodBehaviours)
            bulletList("Poor behaviours", summary.poorBehaviours)
            bulletList("Recurring patterns", summary.recurringPatterns)
            bulletList("Recommendations", summary.recommendations)
            paragraph("Next week's focus", subheading, after = 2f)
            paragraph(summary.nextWeekFocus, bodyText, after = 10f)
        }

        private fun bulletList(heading: String, items: List<String>) {
            paragraph(heading, subheading, after = 2f)
            if (items.isEmpty()) {
                paragraph("—", bodyText, indent = 12f)
            } else {
                items.forEach { paragraph("•  $it", bodyText, indent = 12f, after = 3f) }
            }
            y += 8f
        }

        companion object {
            const val PAGE_W = 595   // A4 at 72 dpi
            const val PAGE_H = 842
            const val MARGIN = 40f
            const val FOOTER = 48f
            const val CONTENT_W = PAGE_W - 2 * MARGIN

            val INK = Color.rgb(20, 20, 24)
            val PANEL = Color.rgb(242, 242, 245)
            val GREEN = Color.rgb(46, 125, 50)
            val RED = Color.rgb(198, 40, 40)
            val AMBER = Color.rgb(239, 160, 0)
            val GREY = Color.rgb(120, 120, 128)

            private val RANGE = DateTimeFormatter.ofPattern("d MMM")
            private val RANGE_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy")
            private val ROW_DATE = DateTimeFormatter.ofPattern("EEE d MMM")
            private val DAY_SHORT = DateTimeFormatter.ofPattern("EEE")
            private val STAMP = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm")

            private fun fmt(value: Int) = String.format(Locale.US, "%,d", value)

            private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

            private fun textPaint(size: Float, color: Int = INK, bold: Boolean = false) =
                TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = size
                    this.color = color
                    typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
                }

            val title = textPaint(16f, Color.WHITE, bold = true)
            val subtitle = textPaint(11f, Color.rgb(200, 200, 205))
            val sectionTitle = textPaint(10f, GREY, bold = true).apply { letterSpacing = 0.12f }
            val statValue = textPaint(15f, bold = true)
            val badge = textPaint(11f, Color.WHITE, bold = true)
            val body = textPaint(10f)
            val bodyBold = textPaint(10f, bold = true)
            val bodyText = textPaint(10.5f)
            val subheading = textPaint(11f, bold = true)
            val tableHeader = textPaint(10f, Color.WHITE, bold = true)
            val small = textPaint(8.5f, GREY)
            val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(210, 210, 215); strokeWidth = 1f }
            val targetLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = INK
                strokeWidth = 1f
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(4f, 4f), 0f)
            }
        }
    }
}
