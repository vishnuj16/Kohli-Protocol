package com.vishnu.kohliprotocol.data.ai

import com.vishnu.kohliprotocol.data.local.entity.DailyAnalysisEntity
import com.vishnu.kohliprotocol.data.local.entity.FoodEntryEntity
import com.vishnu.kohliprotocol.data.local.entity.MealType
import com.vishnu.kohliprotocol.data.local.entity.MealWithEntries
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

/** One day of the week, as the weekly AI summary sees it. */
data class WeekDay(
    val date: LocalDate,
    val meals: List<MealWithEntries>,
    val extraFood: List<FoodEntryEntity>,
    val analysis: DailyAnalysisEntity?,
)

/** Everything sent for the weekly report (spec §18). The app's result is fact, not AI opinion. */
data class WeekLog(
    val start: LocalDate,
    val end: LocalDate,
    val biryaniParameter: Float,
    val average: Float?,
    val logsComplete: Boolean,
    /** "SUCCESS", "WEEK FAILED", or "IN PROGRESS" for a preview. */
    val result: String,
    val days: List<WeekDay>,
)

/** The AI's structured weekly feedback. Created only by [WeeklySummaryParser]. */
data class WeeklySummary(
    val summary: String,
    val goodBehaviours: List<String>,
    val poorBehaviours: List<String>,
    val recurringPatterns: List<String>,
    val recommendations: List<String>,
    val nextWeekFocus: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put(KEY_SUMMARY, summary)
        .put(KEY_GOOD, JSONArray(goodBehaviours))
        .put(KEY_POOR, JSONArray(poorBehaviours))
        .put(KEY_PATTERNS, JSONArray(recurringPatterns))
        .put(KEY_RECOMMENDATIONS, JSONArray(recommendations))
        .put(KEY_FOCUS, nextWeekFocus)

    companion object {
        internal const val KEY_SUMMARY = "summary"
        internal const val KEY_GOOD = "good_behaviours"
        internal const val KEY_POOR = "poor_behaviours"
        internal const val KEY_PATTERNS = "recurring_patterns"
        internal const val KEY_RECOMMENDATIONS = "recommendations"
        internal const val KEY_FOCUS = "next_week_focus"

        /** Reads a summary stored in `WeeklyReportEntity.aiSummary`; null if absent or unreadable. */
        fun fromStored(json: String?): WeeklySummary? =
            json?.let { runCatching { WeeklySummaryParser.parse(it) }.getOrNull() }
    }
}

/** Prompt, schema and serialization for the weekly summary. */
object WeeklyPrompt {

    val SYSTEM = """
        You are the weekly reviewer for Kohli Protocol, a strict personal food-discipline app used
        by one person in India. You receive a full week: every meal slot, extra food, and each
        day's AI calorie range, rating (1 = Fatass whale … 5 = Bro is Kohli) and category, plus the
        weekly average, the target ("Biryani Parameter") and the app's result.

        The app has already decided the result — do not re-judge it. Write direct, honest feedback
        grounded only in what was logged: name specific foods, days and times. No generic advice,
        no padding, no praise that the log doesn't earn. Keep every list item to one or two
        sentences. Reply with the JSON object only.
    """.trimIndent()

    fun build(week: WeekLog): String {
        val lines = mutableListOf(
            "Week: ${week.start} to ${week.end}",
            "Biryani Parameter (target average): ${week.biryaniParameter}",
            "Weekly average rating: ${week.average?.let { String.format(Locale.US, "%.2f", it) } ?: "no ratings"}",
            "All meal slots logged every day: ${if (week.logsComplete) "yes" else "no"}",
            "Result decided by the app: ${week.result}",
            "",
        )
        week.days.forEach { day ->
            val analysis = day.analysis
            lines += "${day.date} (${day.date.dayOfWeek}): " + if (analysis != null) {
                "${analysis.minCalories}–${analysis.maxCalories} kcal, rating ${analysis.rating} (${analysis.category.label})"
            } else {
                "not analyzed"
            }
            val byType = day.meals.associateBy { it.meal.mealType }
            MealType.entries.forEach { type ->
                val meal = byType[type]
                val text = when {
                    meal == null || (!meal.meal.isSkipped && meal.entries.isEmpty()) -> "(not logged)"
                    meal.meal.isSkipped -> "SKIPPED"
                    else -> meal.entries.sortedBy { it.timestamp }.joinToString("; ") { it.description }
                }
                lines += "  ${type.label}: $text"
            }
            if (day.extraFood.isNotEmpty()) {
                lines += "  Extra: " + day.extraFood.sortedBy { it.timestamp }.joinToString("; ") { it.description }
            }
        }
        return lines.joinToString("\n")
    }

    fun responseSchema(): JSONObject {
        fun list() = JSONObject().put("type", "array").put("items", JSONObject().put("type", "string"))
        fun text() = JSONObject().put("type", "string")
        return JSONObject()
            .put("type", "object")
            .put(
                "properties", JSONObject()
                    .put(WeeklySummary.KEY_SUMMARY, text())
                    .put(WeeklySummary.KEY_GOOD, list())
                    .put(WeeklySummary.KEY_POOR, list())
                    .put(WeeklySummary.KEY_PATTERNS, list())
                    .put(WeeklySummary.KEY_RECOMMENDATIONS, list())
                    .put(WeeklySummary.KEY_FOCUS, text())
            )
            .put(
                "required", JSONArray(
                    listOf(
                        WeeklySummary.KEY_SUMMARY, WeeklySummary.KEY_GOOD, WeeklySummary.KEY_POOR,
                        WeeklySummary.KEY_PATTERNS, WeeklySummary.KEY_RECOMMENDATIONS, WeeklySummary.KEY_FOCUS,
                    )
                )
            )
            .put("additionalProperties", false)
    }
}

/** Strict validation of the weekly summary before it is stored or printed. */
object WeeklySummaryParser {

    private const val MAX_TEXT = 2_000
    private const val MAX_ITEM = 600
    private const val MAX_ITEMS = 10

    fun parse(raw: String): WeeklySummary {
        val text = raw.trim().let {
            if (it.startsWith("```")) it.removePrefix("```json").removePrefix("```").removeSuffix("```").trim() else it
        }
        val json = try {
            JSONObject(text)
        } catch (e: JSONException) {
            invalid("not a JSON object")
        }
        return WeeklySummary(
            summary = requireText(json, WeeklySummary.KEY_SUMMARY),
            goodBehaviours = requireList(json, WeeklySummary.KEY_GOOD),
            poorBehaviours = requireList(json, WeeklySummary.KEY_POOR),
            recurringPatterns = requireList(json, WeeklySummary.KEY_PATTERNS),
            recommendations = requireList(json, WeeklySummary.KEY_RECOMMENDATIONS),
            nextWeekFocus = requireText(json, WeeklySummary.KEY_FOCUS),
        )
    }

    private fun requireText(json: JSONObject, key: String): String {
        val value = json.opt(key) as? String ?: invalid("\"$key\" must be a string")
        val clean = value.trim()
        if (clean.isEmpty()) invalid("\"$key\" is empty")
        if (clean.length > MAX_TEXT) invalid("\"$key\" is too long")
        return clean
    }

    private fun requireList(json: JSONObject, key: String): List<String> {
        val array = json.opt(key) as? JSONArray ?: invalid("\"$key\" must be an array")
        if (array.length() > MAX_ITEMS) invalid("\"$key\" has too many items")
        return (0 until array.length()).map { i ->
            val item = (array.opt(i) as? String)?.trim() ?: invalid("\"$key\"[$i] must be a string")
            if (item.isEmpty() || item.length > MAX_ITEM) invalid("\"$key\"[$i] is empty or too long")
            item
        }
    }

    private fun invalid(detail: String): Nothing = throw AiException.InvalidResponse("weekly summary: $detail")
}
