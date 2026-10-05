package com.vishnu.kohliprotocol.data.ai

import com.vishnu.kohliprotocol.BuildConfig
import com.vishnu.kohliprotocol.data.preferences.AiConfigStore
import com.vishnu.kohliprotocol.data.preferences.ApiCredential
import kotlinx.coroutines.flow.first

/**
 * Builds the provider for the user's default API key.
 * - Gemini: every saved Gemini key (default first) and the free-tier model chain, so the
 *   provider can fall back across models and keys on high demand.
 * - Claude: the default key with the newest Opus (or a pinned model).
 */
class AiProviderFactory(
    private val config: AiConfigStore,
    private val models: ModelResolver,
) {

    /** Throws [AiException.MissingApiKey] when no key is saved and none is compiled in. */
    suspend fun create(): AIProvider {
        config.migrateLegacyKeys()
        val (type, defaultSecret) = config.defaultKey() ?: localDefault() ?: throw AiException.MissingApiKey()
        return when (type) {
            AiProviderType.GEMINI -> GeminiProvider(
                credentials = credentials(type).ifEmpty { listOf(localCredential(defaultSecret)) },
                models = GeminiProvider.modelChain(config.modelOverride(type).first()),
            )
            AiProviderType.CLAUDE -> ClaudeProvider(defaultSecret, models.resolve(type, defaultSecret))
        }
    }

    private suspend fun credentials(type: AiProviderType): List<ApiCredential> = config.credentialsFor(type)

    private fun localCredential(secret: String) = ApiCredential("local.properties", secret.takeLast(4), secret)

    /** Dev fallback compiled in from local.properties, used only when no key is saved in-app. */
    fun localKey(type: AiProviderType): String? = when (type) {
        AiProviderType.GEMINI -> BuildConfig.GEMINI_API_KEY
        AiProviderType.CLAUDE -> BuildConfig.ANTHROPIC_API_KEY
    }.takeIf { it.isNotBlank() }

    fun localDefault(): Pair<AiProviderType, String>? =
        AiProviderType.entries.firstNotNullOfOrNull { type -> localKey(type)?.let { type to it } }
}
