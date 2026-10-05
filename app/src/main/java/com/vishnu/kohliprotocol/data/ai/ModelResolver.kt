package com.vishnu.kohliprotocol.data.ai

import android.util.Log
import com.vishnu.kohliprotocol.data.preferences.AiConfigStore
import kotlinx.coroutines.flow.first
import java.net.URLEncoder
import java.time.Instant

/**
 * Claude model selection: the user's pinned override if set, otherwise the newest Claude Opus
 * the key can access, looked up from the Models API and cached for a day. A failed lookup never
 * blocks analysis — it falls back to the last known model, then to [AiProviderType.fallbackModel].
 * (Gemini uses a fixed free-tier chain instead; see [GeminiProvider.DEFAULT_MODEL_CHAIN].)
 */
class ModelResolver(private val config: AiConfigStore) {

    suspend fun resolve(type: AiProviderType, apiKey: String): String {
        config.modelOverride(type).first()?.let { return it }
        val cached = config.latestModel(type).first()
        if (cached != null && System.currentTimeMillis() - cached.checkedAt < CACHE_MILLIS) return cached.model
        return runCatching { refresh(type, apiKey) }
            .onFailure { Log.w(TAG, "Latest-model lookup for ${type.label} failed: ${it.message}") }
            .getOrNull()
            ?: cached?.model
            ?: type.fallbackModel
    }

    /** Queries the provider now and caches the result. Throws [AiException] on failure. */
    suspend fun refresh(type: AiProviderType, apiKey: String): String {
        require(supportsLookup(type)) { "${type.label} uses a fixed model chain" }
        val latest = newestClaudeOpus(apiKey) ?: throw AiException.InvalidResponse("no ${type.flagshipFamily} model available to this key")
        config.setLatestModel(type, latest, System.currentTimeMillis())
        return latest
    }

    fun supportsLookup(type: AiProviderType) = type == AiProviderType.CLAUDE

    /** GET /v1/models, newest `claude-opus-*` by release date. */
    private suspend fun newestClaudeOpus(apiKey: String): String? {
        val headers = mapOf("x-api-key" to apiKey, "anthropic-version" to "2023-06-01")
        val candidates = mutableListOf<Pair<String, Instant>>()
        var afterId: String? = null
        repeat(MAX_PAGES) {
            val url = "https://api.anthropic.com/v1/models?limit=100" +
                (afterId?.let { "&after_id=" + encode(it) } ?: "")
            val page = AiHttp.getJson(url, headers)
            val data = page.optJSONArray("data") ?: return pickClaude(candidates)
            for (i in 0 until data.length()) {
                val model = data.optJSONObject(i) ?: continue
                val id = model.optString("id")
                if (!id.startsWith("claude-opus-")) continue
                val created = runCatching { Instant.parse(model.optString("created_at")) }.getOrNull() ?: continue
                candidates += id to created
            }
            if (!page.optBoolean("has_more")) return pickClaude(candidates)
            afterId = page.optString("last_id").ifEmpty { return pickClaude(candidates) }
        }
        return pickClaude(candidates)
    }

    /** Newest release; on a tie prefer the shorter alias over a dated snapshot id. */
    private fun pickClaude(candidates: List<Pair<String, Instant>>): String? =
        candidates.maxWithOrNull(compareBy<Pair<String, Instant>> { it.second }.thenBy { -it.first.length })?.first

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private companion object {
        const val TAG = "KohliProtocol"
        const val CACHE_MILLIS = 24 * 60 * 60 * 1000L
        const val MAX_PAGES = 5
    }
}
