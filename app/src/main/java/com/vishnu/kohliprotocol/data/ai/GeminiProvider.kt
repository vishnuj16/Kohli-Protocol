package com.vishnu.kohliprotocol.data.ai

import android.util.Log
import com.vishnu.kohliprotocol.data.preferences.ApiCredential
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

/**
 * Google Gemini via the Generative Language REST API (`generateContent`), built for the free
 * tier's "high demand" failures:
 *
 * 1. Each model call retries server errors (5xx) with backoff: 2s → 4s → 8s.
 * 2. If a model stays unavailable (5xx after retries, 429 quota, or 404 unknown model), the next
 *    model in [models] is tried.
 * 3. If every model fails that way, or a key is rejected, the next key in [credentials] is tried.
 *
 * Only when every key × model combination is exhausted does the last error reach the caller.
 * A result served by anything other than the first key and first model carries a
 * [DailyAnalysisResult.fallbackNote] so the fallback can be audited.
 */
class GeminiProvider(
    private val credentials: List<ApiCredential>,
    private val models: List<String>,
) : AIProvider {

    override val type = AiProviderType.GEMINI

    init {
        require(credentials.isNotEmpty()) { "at least one Gemini key is required" }
        require(models.isNotEmpty()) { "at least one Gemini model is required" }
    }

    override suspend fun analyzeDay(day: DayLog): DailyAnalysisResult {
        val (result, fallbackNote) = generate(buildBody(day), AnalysisResponseParser::parse)
        return if (fallbackNote != null) result.copy(fallbackNote = fallbackNote) else result
    }

    override suspend fun summarizeWeek(week: WeekLog): WeeklySummary {
        val body = requestBody(
            system = WeeklyPrompt.SYSTEM,
            parts = JSONArray().put(JSONObject().put("text", WeeklyPrompt.build(week))),
            schema = WeeklyPrompt.responseSchema(),
            temperature = 0.4,
        )
        return generate(body, WeeklySummaryParser::parse).first
    }

    /**
     * Runs [body] through every key × model combination until one answers, then parses the text
     * with [parse]. Returns the result and, if it didn't come from the first key and model, a note
     * describing the fallback. Parse errors are not retried on other models.
     */
    private suspend fun <T> generate(body: JSONObject, parse: (String) -> T): Pair<T, String?> {
        var lastError: AiException? = null

        keys@ for ((keyIndex, credential) in credentials.withIndex()) {
            for ((modelIndex, model) in models.withIndex()) {
                val response = try {
                    callWithBackoff(credential, model, body)
                } catch (e: AiException) {
                    lastError = e
                    when {
                        e is AiException.Auth -> {
                            Log.w(TAG, "Gemini key ${credential.displayName} rejected; trying next key")
                            continue@keys
                        }
                        isModelUnavailable(e) -> {
                            Log.w(TAG, "Gemini $model unavailable with key ${credential.displayName}: ${e.message}")
                            continue
                        }
                        else -> throw e
                    }
                }

                val result = parse(responseText(response))
                val fellBack = keyIndex > 0 || modelIndex > 0
                return Pair(result, if (fellBack) "model $model, key ${credential.displayName}" else null)
            }
        }

        val last = lastError ?: throw AiException.MissingApiKey()
        val tried = "${models.size} model(s) × ${credentials.size} key(s)"
        throw when (last) {
            is AiException.Http -> AiException.Http(last.code, "all Gemini fallbacks exhausted ($tried). Last: ${last.message}")
            else -> last
        }
    }

    /** One model on one key, retrying 5xx with exponential backoff. */
    private suspend fun callWithBackoff(credential: ApiCredential, model: String, body: JSONObject): JSONObject {
        suspend fun call() = AiHttp.postJson(
            url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            headers = mapOf("x-goog-api-key" to credential.secret),
            body = body,
        )

        BACKOFF_MILLIS.forEachIndexed { index, wait ->
            try {
                return call()
            } catch (e: AiException.Http) {
                if (e.code !in 500..599) throw e
                Log.i(TAG, "Gemini $model returned ${e.code}; retry ${index + 1}/${BACKOFF_MILLIS.size} in ${wait}ms")
                delay(wait)
            }
        }
        return call()  // final attempt; its error propagates
    }

    /** Errors where another model (or key) may succeed where this one did not. */
    private fun isModelUnavailable(e: AiException): Boolean = when (e) {
        is AiException.Http -> e.code in 500..599 || e.code == 404
        is AiException.RateLimited -> true  // free-tier quotas are per model and per key
        else -> false
    }

    private suspend fun buildBody(day: DayLog): JSONObject {
        val content = AnalysisPrompt.build(day)

        val parts = JSONArray().put(JSONObject().put("text", content.text))
        content.photos.forEach { photo ->
            val data = ImageEncoder.jpegBase64(photo.path) ?: return@forEach
            parts.put(JSONObject().put("text", "${photo.label}:"))
            parts.put(
                JSONObject().put(
                    "inlineData",
                    JSONObject().put("mimeType", "image/jpeg").put("data", data),
                )
            )
        }
        return requestBody(AnalysisPrompt.SYSTEM, parts, AnalysisPrompt.responseSchema(), temperature = 0.2)
    }

    private fun requestBody(system: String, parts: JSONArray, schema: JSONObject, temperature: Double): JSONObject =
        JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseMimeType", "application/json")
                    .put("responseSchema", toGeminiSchema(schema))
                    .put("temperature", temperature),
            )

    /** The answer text of a successful response; throws on blocks, truncation or empty output. */
    private fun responseText(response: JSONObject): String {
        response.optJSONObject("promptFeedback")?.optString("blockReason")
            ?.takeIf { it.isNotBlank() }
            ?.let { throw AiException.Refused(it) }

        val candidate = response.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw AiException.InvalidResponse("no candidates in response")
        when (val finish = candidate.optString("finishReason")) {
            "", "STOP" -> Unit
            "MAX_TOKENS" -> throw AiException.InvalidResponse("response was cut off (MAX_TOKENS)")
            "SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION" -> throw AiException.Refused(finish)
            else -> throw AiException.InvalidResponse("unexpected finishReason $finish")
        }

        val responseParts = candidate.optJSONObject("content")?.optJSONArray("parts")
            ?: throw AiException.InvalidResponse("candidate has no content")
        val text = buildString {
            for (i in 0 until responseParts.length()) {
                val part = responseParts.optJSONObject(i) ?: continue
                if (part.optBoolean("thought")) continue
                append(part.optString("text"))
            }
        }
        return text
    }

    /**
     * Gemini's `responseSchema` is an OpenAPI subset: upper-case type names and no
     * `additionalProperties`. Converts the shared JSON Schema recursively.
     */
    private fun toGeminiSchema(schema: JSONObject): JSONObject {
        val out = JSONObject()
        schema.keys().forEach { key ->
            when (key) {
                "additionalProperties" -> Unit
                "type" -> out.put("type", schema.getString("type").uppercase())
                "properties" -> {
                    val props = schema.getJSONObject("properties")
                    val converted = JSONObject()
                    props.keys().forEach { name -> converted.put(name, toGeminiSchema(props.getJSONObject(name))) }
                    out.put("properties", converted)
                }
                "items" -> out.put("items", toGeminiSchema(schema.getJSONObject("items")))
                else -> out.put(key, schema.get(key))
            }
        }
        return out
    }

    companion object {
        private const val TAG = "KohliProtocol"

        /** Waits before retries 1, 2 and 3 of a model that returned a 5xx. */
        private val BACKOFF_MILLIS = longArrayOf(2_000, 4_000, 8_000)

        /** Free-tier chain: the default model first, then lighter models for high demand. */
        val DEFAULT_MODEL_CHAIN = listOf("gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite")

        /** A pinned model goes first; the free-tier chain still backs it up. */
        fun modelChain(pinned: String?): List<String> =
            (listOfNotNull(pinned) + DEFAULT_MODEL_CHAIN).distinct()
    }
}
