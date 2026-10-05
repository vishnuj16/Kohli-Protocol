package com.vishnu.kohliprotocol.data.ai

import com.vishnu.kohliprotocol.data.local.entity.DailyCategory
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Strict validation of AI output. Nothing reaches Room unless every field is present, has the
 * right JSON type (no "4" for 4), is in range, and the category agrees with the rating.
 * Any violation throws [AiException.InvalidResponse] and the day stays "Analysis Pending".
 */
object AnalysisResponseParser {

    private const val MAX_DAILY_CALORIES = 20_000
    private const val MAX_MEAL_CALORIES = 10_000
    private val CONFIDENCE = setOf("low", "medium", "high")

    fun parse(raw: String): DailyAnalysisResult {
        val json = try {
            JSONObject(stripFences(raw))
        } catch (e: JSONException) {
            invalid("not a JSON object")
        }

        val min = requireInt(json, "daily_minimum_calories")
        val max = requireInt(json, "daily_maximum_calories")
        val rating = requireInt(json, "rating")
        val categoryLabel = requireString(json, "category")

        if (min !in 0..MAX_DAILY_CALORIES) invalid("daily_minimum_calories out of range: $min")
        if (max !in min..MAX_DAILY_CALORIES) invalid("daily_maximum_calories must be $min..$MAX_DAILY_CALORIES: $max")
        val category = DailyCategory.fromRating(rating) ?: invalid("rating must be 1–5: $rating")
        if (DailyCategory.fromLabel(categoryLabel) != category) {
            invalid("category \"$categoryLabel\" does not match rating $rating (${category.label})")
        }

        val confidence = json.opt("confidence")?.takeUnless { it == JSONObject.NULL }?.let {
            val value = (it as? String)?.lowercase() ?: invalid("confidence must be a string")
            if (value !in CONFIDENCE) invalid("confidence must be low/medium/high: $value")
            value
        }

        val meals = json.opt("meals")?.takeUnless { it == JSONObject.NULL }?.let {
            validateMeals(it as? JSONArray ?: invalid("meals must be an array"))
        }

        return DailyAnalysisResult(
            minCalories = min,
            maxCalories = max,
            rating = rating,
            category = category,
            confidence = confidence,
            mealEstimatesJson = meals?.toString(),
        )
    }

    private fun validateMeals(array: JSONArray): JSONArray {
        val clean = JSONArray()
        for (i in 0 until array.length()) {
            val meal = array.opt(i) as? JSONObject ?: invalid("meals[$i] must be an object")
            val name = requireString(meal, "meal")
            val min = requireInt(meal, "minimum_calories")
            val max = requireInt(meal, "maximum_calories")
            if (min !in 0..MAX_MEAL_CALORIES || max !in min..MAX_MEAL_CALORIES) {
                invalid("meals[$i] calorie range invalid: $min–$max")
            }
            clean.put(
                JSONObject().put("meal", name).put("minimum_calories", min).put("maximum_calories", max)
            )
        }
        return clean
    }

    /** Accepts only a JSON number with no fractional part. */
    private fun requireInt(json: JSONObject, key: String): Int {
        if (!json.has(key)) invalid("missing \"$key\"")
        val value = json.get(key)
        if (value !is Number) invalid("\"$key\" must be a number, got ${value.javaClass.simpleName}")
        val asDouble = value.toDouble()
        if (asDouble % 1.0 != 0.0 || asDouble !in Int.MIN_VALUE.toDouble()..Int.MAX_VALUE.toDouble()) {
            invalid("\"$key\" must be an integer: $value")
        }
        return asDouble.toInt()
    }

    private fun requireString(json: JSONObject, key: String): String {
        if (!json.has(key)) invalid("missing \"$key\"")
        val value = json.get(key) as? String ?: invalid("\"$key\" must be a string")
        if (value.isBlank()) invalid("\"$key\" is empty")
        return value.trim()
    }

    /** Tolerates a ```json fence around the object; nothing else. */
    private fun stripFences(raw: String): String {
        val text = raw.trim()
        if (!text.startsWith("```")) return text
        return text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    }

    private fun invalid(detail: String): Nothing = throw AiException.InvalidResponse(detail)
}
