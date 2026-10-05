package com.vishnu.kohliprotocol.data.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * Anthropic Claude via the Messages API (raw HTTP, per the project's HTTP/JSON-only rule).
 * The response is constrained with structured outputs (`output_config.format`), then still
 * validated by [AnalysisResponseParser] before anything is stored.
 */
class ClaudeProvider(
    private val apiKey: String,
    private val model: String,
) : AIProvider {

    override val type = AiProviderType.CLAUDE

    override suspend fun analyzeDay(day: DayLog): DailyAnalysisResult {
        val content = AnalysisPrompt.build(day)

        // Images first, each preceded by the label the day text refers to, then the text.
        val blocks = JSONArray()
        content.photos.forEach { photo ->
            val data = ImageEncoder.jpegBase64(photo.path) ?: return@forEach
            blocks.put(JSONObject().put("type", "text").put("text", "${photo.label}:"))
            blocks.put(
                JSONObject()
                    .put("type", "image")
                    .put(
                        "source",
                        JSONObject()
                            .put("type", "base64")
                            .put("media_type", "image/jpeg")
                            .put("data", data),
                    )
            )
        }
        blocks.put(JSONObject().put("type", "text").put("text", content.text))
        return AnalysisResponseParser.parse(complete(AnalysisPrompt.SYSTEM, blocks, AnalysisPrompt.responseSchema()))
    }

    override suspend fun summarizeWeek(week: WeekLog): WeeklySummary {
        val blocks = JSONArray().put(JSONObject().put("type", "text").put("text", WeeklyPrompt.build(week)))
        return WeeklySummaryParser.parse(complete(WeeklyPrompt.SYSTEM, blocks, WeeklyPrompt.responseSchema()))
    }

    /** One Messages API call constrained to [schema]; returns the answer text. */
    private suspend fun complete(system: String, blocks: JSONArray, schema: JSONObject): String {
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 16_000)
            .put("system", system)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", blocks)))
            .put(
                "output_config",
                JSONObject().put("format", JSONObject().put("type", "json_schema").put("schema", schema)),
            )

        val headers = mutableMapOf(
            "x-api-key" to apiKey,
            "anthropic-version" to API_VERSION,
        )
        if (model in FALLBACK_ENABLED_MODELS) {
            // On a safety-classifier decline, re-run server-side on Anthropic's recommended fallback.
            body.put("fallbacks", "default")
            headers["anthropic-beta"] = FALLBACK_BETA
        }

        val response = AiHttp.postJson(MESSAGES_URL, headers, body)

        when (val stop = response.optString("stop_reason")) {
            "end_turn" -> Unit
            "refusal" -> {
                val category = response.optJSONObject("stop_details")?.optString("category")
                throw AiException.Refused(category?.takeIf { it.isNotBlank() && it != "null" } ?: "refusal")
            }
            "max_tokens" -> throw AiException.InvalidResponse("response was cut off (max_tokens)")
            else -> throw AiException.InvalidResponse("unexpected stop_reason $stop")
        }

        // Content may include thinking blocks; the answer is in the text block(s).
        val contentBlocks = response.optJSONArray("content")
            ?: throw AiException.InvalidResponse("response has no content")
        return buildString {
            for (i in 0 until contentBlocks.length()) {
                val block = contentBlocks.optJSONObject(i) ?: continue
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }
    }

    private companion object {
        const val MESSAGES_URL = "https://api.anthropic.com/v1/messages"
        const val API_VERSION = "2023-06-01"
        const val FALLBACK_BETA = "server-side-fallback-2026-07-01"

        /** Models the `fallbacks: "default"` option is known to be valid for. */
        val FALLBACK_ENABLED_MODELS = setOf("claude-opus-5")
    }
}
