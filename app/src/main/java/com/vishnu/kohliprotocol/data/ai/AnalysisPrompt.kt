package com.vishnu.kohliprotocol.data.ai

import com.vishnu.kohliprotocol.data.local.entity.DailyCategory
import com.vishnu.kohliprotocol.data.local.entity.FoodEntryEntity
import com.vishnu.kohliprotocol.data.local.entity.MealType
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Provider-neutral prompt, day serialization and response schema. */
object AnalysisPrompt {

    /** Photos beyond this are left out of the request (and the AI is told so). */
    const val MAX_PHOTOS = 16

    val SYSTEM = """
        You are the food analyst for Kohli Protocol, a personal food-discipline app used by one
        person in India. Each day you receive what he logged in four meal slots (Breakfast,
        Lunch, Evening / snacks, Dinner) plus any extra food, with optional photos.

        Estimate calories as honest RANGES, never falsely precise numbers. Use the photos to
        judge portion sizes when present; assume typical Indian home or restaurant portions
        otherwise. A meal marked "SKIPPED" was deliberately not eaten — count it as zero and do
        not treat it as missing information.

        Rate the whole day from 1 to 5 for eating discipline, using exactly these categories:
        1 = Fatass whale: heavy overeating, binge, or mostly junk/fried/sugary food.
        2 = Black hole: clearly too much or poor choices through much of the day.
        3 = Fine init: acceptable but unremarkable; some slips.
        4 = Fair play: controlled portions, mostly good choices.
        5 = Bro is Kohli: disciplined, balanced, clean eating all day.

        Judge what was actually logged; do not invent food. Be blunt — this app is
        intentionally strict.

        Also write "habit_summary": exactly two short sentences naming specific foods or times —
        the first on what went well, the second on what hurt the day most.
        Reply with the JSON object only.
    """.trimIndent()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** A photo to attach, with the label the text refers to it by. */
    data class Photo(val label: String, val path: String)

    data class Content(val text: String, val photos: List<Photo>)

    fun build(day: DayLog): Content {
        val photos = mutableListOf<Photo>()
        val lines = mutableListOf("Date: ${day.date} (${day.date.dayOfWeek})", "")

        fun describe(entry: FoodEntryEntity): String {
            val time = Instant.ofEpochMilli(entry.timestamp).atZone(ZoneId.systemDefault()).format(timeFormatter)
            val photoNote = entry.photoPath?.let { path ->
                val label = "Photo ${photos.size + 1}"
                photos += Photo(label, path)
                " [$label]"
            }.orEmpty()
            return "  - $time: ${entry.description}$photoNote"
        }

        val byType = day.meals.associateBy { it.meal.mealType }
        MealType.entries.forEach { type ->
            val meal = byType[type]
            lines += "${type.label}:"
            when {
                meal == null || (!meal.meal.isSkipped && meal.entries.isEmpty()) -> lines += "  (not logged)"
                meal.meal.isSkipped -> lines += "  SKIPPED"
                else -> meal.entries.sortedBy { it.timestamp }.forEach { lines += describe(it) }
            }
        }
        lines += ""
        lines += "Extra food (outside meal slots):"
        if (day.extraFood.isEmpty()) lines += "  (none)" else day.extraFood.sortedBy { it.timestamp }.forEach { lines += describe(it) }

        val attached = photos.take(MAX_PHOTOS)
        if (photos.size > attached.size) {
            lines += ""
            lines += "Note: only the first ${attached.size} of ${photos.size} photos are attached."
        }

        if (day.userFeedback != null || day.previousResult != null) {
            lines += ""
            lines += "The user rejected a previous analysis of this day as not looking right."
            day.previousResult?.let {
                lines += "Previous result: ${it.minCalories}–${it.maxCalories} kcal, rating ${it.rating} (${it.category.label})."
            }
            day.userFeedback?.takeIf { it.isNotBlank() }?.let { lines += "User's comment: $it" }
            lines += "Re-examine the log independently; change the result only where the evidence supports it."
        }
        return Content(lines.joinToString("\n"), attached)
    }

    /**
     * JSON Schema for the response. Kept to the subset both providers accept (no numeric
     * ranges) — the ranges are enforced by [AnalysisResponseParser] instead.
     */
    fun responseSchema(): JSONObject {
        val mealEstimate = JSONObject()
            .put("type", "object")
            .put(
                "properties", JSONObject()
                    .put("meal", JSONObject().put("type", "string"))
                    .put("minimum_calories", JSONObject().put("type", "integer"))
                    .put("maximum_calories", JSONObject().put("type", "integer"))
            )
            .put("required", JSONArray(listOf("meal", "minimum_calories", "maximum_calories")))
            .put("additionalProperties", false)

        return JSONObject()
            .put("type", "object")
            .put(
                "properties", JSONObject()
                    .put("daily_minimum_calories", JSONObject().put("type", "integer"))
                    .put("daily_maximum_calories", JSONObject().put("type", "integer"))
                    .put("rating", JSONObject().put("type", "integer"))
                    .put(
                        "category", JSONObject()
                            .put("type", "string")
                            .put("enum", JSONArray(DailyCategory.entries.map { it.label }))
                    )
                    .put(
                        "confidence", JSONObject()
                            .put("type", "string")
                            .put("enum", JSONArray(listOf("low", "medium", "high")))
                    )
                    .put("meals", JSONObject().put("type", "array").put("items", mealEstimate))
                    .put("habit_summary", JSONObject().put("type", "string"))
            )
            .put(
                "required",
                JSONArray(
                    listOf(
                        "daily_minimum_calories", "daily_maximum_calories", "rating",
                        "category", "confidence", "meals", "habit_summary",
                    )
                )
            )
            .put("additionalProperties", false)
    }
}
