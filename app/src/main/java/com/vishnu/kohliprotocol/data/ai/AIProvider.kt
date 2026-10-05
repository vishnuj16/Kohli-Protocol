package com.vishnu.kohliprotocol.data.ai

import com.vishnu.kohliprotocol.data.local.entity.DailyCategory
import com.vishnu.kohliprotocol.data.local.entity.FoodEntryEntity
import com.vishnu.kohliprotocol.data.local.entity.MealWithEntries
import java.time.LocalDate

/**
 * An AI backend: estimates each day's calories and rating, and writes the weekly review.
 * AI output is an estimate only — the app's deterministic rules (weekly result, game access)
 * never live in a provider.
 */
interface AIProvider {
    val type: AiProviderType

    /** Throws [AiException] on any failure; returns only fully validated results. */
    suspend fun analyzeDay(day: DayLog): DailyAnalysisResult

    /** Structured weekly feedback (spec §18). Throws [AiException]; returns only validated output. */
    suspend fun summarizeWeek(week: WeekLog): WeeklySummary
}

/**
 * Claude: [ModelResolver] picks the newest Opus automatically; [fallbackModel] is used only until
 * the first successful lookup. Gemini: fixed free-tier chain starting at [fallbackModel]
 * (see [GeminiProvider.DEFAULT_MODEL_CHAIN]).
 */
enum class AiProviderType(val label: String, val flagshipFamily: String, val fallbackModel: String) {
    GEMINI("Gemini", "Gemini Flash", "gemini-3.5-flash"),
    CLAUDE("Claude", "Claude Opus", "claude-opus-5");

    companion object {
        /** Recognises a pasted key by its well-known prefix, or null if unsure. */
        fun detect(apiKey: String): AiProviderType? {
            val key = apiKey.trim()
            return when {
                key.startsWith("sk-ant-") -> CLAUDE
                key.startsWith("AIza") -> GEMINI
                else -> null
            }
        }
    }
}

/** Everything the AI sees about one day. */
data class DayLog(
    val date: LocalDate,
    val meals: List<MealWithEntries>,
    /** "+ Add Food" entries, which belong to no meal slot. */
    val extraFood: List<FoodEntryEntity>,
    /** Why the user rejected the previous analysis, when re-analyzing. */
    val userFeedback: String? = null,
    /** The rejected result, so the AI knows what was disputed. */
    val previousResult: DailyAnalysisResult? = null,
)

/** A validated AI verdict. Only [AnalysisResponseParser] creates these from AI output. */
data class DailyAnalysisResult(
    val minCalories: Int,
    val maxCalories: Int,
    val rating: Int,
    val category: DailyCategory,
    val confidence: String?,
    /** Validated per-meal estimates, re-serialized as JSON. */
    val mealEstimatesJson: String?,
    /** Two short sentences: what went well and what hurt the day. */
    val habitSummary: String? = null,
    /** Set when a fallback model or key produced this result (for the audit log). */
    val fallbackNote: String? = null,
)

sealed class AiException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Worth retrying later: rate limits, overload, server errors, no network. */
    abstract val retryable: Boolean

    class MissingApiKey :
        AiException("No API key saved. Add one in AI settings.") {
        override val retryable = false
    }

    class RateLimited(val retryAfterSeconds: Long?) :
        AiException("Rate limited by the AI provider" + (retryAfterSeconds?.let { " — retry in ${it}s" } ?: "")) {
        override val retryable = true
    }

    class Network(cause: Throwable) : AiException("Network error: ${cause.message ?: cause.javaClass.simpleName}", cause) {
        override val retryable = true
    }

    class Http(val code: Int, detail: String) : AiException("AI provider error $code: $detail") {
        override val retryable = code == 408 || code == 529 || code >= 500
    }

    class Auth(detail: String) : AiException("API key rejected: $detail") {
        override val retryable = false
    }

    /** The provider declined (safety filter / refusal). Retrying the same input rarely helps. */
    class Refused(detail: String) : AiException("AI declined the request: $detail") {
        override val retryable = false
    }

    /** The response was not valid JSON matching the schema. May succeed on another attempt. */
    class InvalidResponse(detail: String) : AiException("Invalid AI response: $detail") {
        override val retryable = true
    }
}
