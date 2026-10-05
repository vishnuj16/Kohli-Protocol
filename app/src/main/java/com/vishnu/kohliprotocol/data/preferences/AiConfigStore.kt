package com.vishnu.kohliprotocol.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vishnu.kohliprotocol.data.ai.AiProviderType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/** A saved API key as the UI sees it — never the secret itself. */
data class StoredApiKey(
    val id: String,
    val provider: AiProviderType,
    val label: String,
    /** Last four characters, for telling keys apart. */
    val hint: String,
    val addedAt: Long,
)

data class LatestModel(val model: String, val checkedAt: Long)

/** A decrypted key handed to a provider. [label] and [hint] are safe to log; [secret] is not. */
class ApiCredential(val label: String, val hint: String, val secret: String) {
    val displayName: String get() = "“$label” (…$hint)"
    override fun toString() = "ApiCredential($displayName)"
}

/**
 * AI configuration: every API key the user has saved (each encrypted with [ApiKeyCipher]),
 * which one is the default, per-provider model overrides, and the cached "latest model".
 */
class AiConfigStore(context: Context) {

    sealed interface AddResult {
        data class Added(val key: StoredApiKey) : AddResult
        /** That exact key is already saved; nothing was added. */
        data class Duplicate(val existing: StoredApiKey) : AddResult
    }

    private val dataStore = context.applicationContext.kohliDataStore

    private val preferences: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    /** Newest first. */
    val keys: Flow<List<StoredApiKey>> = preferences
        .map { prefs -> readEntries(prefs).map { it.info }.sortedByDescending { it.addedAt } }
        .distinctUntilChanged()

    /** Always one of [keys] when any exist. */
    val defaultKeyId: Flow<String?> = preferences
        .map { prefs -> defaultEntry(prefs, readEntries(prefs))?.info?.id }
        .distinctUntilChanged()

    suspend fun addKey(
        provider: AiProviderType,
        label: String,
        secret: String,
        makeDefault: Boolean,
    ): AddResult {
        val clean = secret.trim()
        require(clean.isNotEmpty()) { "API key is empty" }
        var result: AddResult? = null
        dataStore.edit { prefs ->
            val entries = readEntries(prefs)
            val existing = entries.firstOrNull { ApiKeyCipher.decrypt(it.encryptedSecret) == clean }
            if (existing != null) {
                if (makeDefault) prefs[KEY_DEFAULT_ID] = existing.info.id
                result = AddResult.Duplicate(existing.info)
                return@edit
            }
            val entry = Entry(
                info = StoredApiKey(
                    id = UUID.randomUUID().toString(),
                    provider = provider,
                    label = label.trim().ifEmpty { "${provider.label} key" },
                    hint = clean.takeLast(4),
                    addedAt = System.currentTimeMillis(),
                ),
                encryptedSecret = ApiKeyCipher.encrypt(clean),
            )
            writeEntries(prefs, entries + entry)
            // The first key saved becomes the default so there is always one.
            if (makeDefault || entries.isEmpty()) prefs[KEY_DEFAULT_ID] = entry.info.id
            result = AddResult.Added(entry.info)
        }
        return checkNotNull(result)
    }

    /** Deleting the default promotes the most recently added remaining key. */
    suspend fun deleteKey(id: String) {
        dataStore.edit { prefs ->
            val remaining = readEntries(prefs).filterNot { it.info.id == id }
            writeEntries(prefs, remaining)
            if (prefs[KEY_DEFAULT_ID] == id) {
                val next = remaining.maxByOrNull { it.info.addedAt }
                if (next == null) { prefs.remove(KEY_DEFAULT_ID) } else { prefs[KEY_DEFAULT_ID] = next.info.id }
            }
        }
    }

    suspend fun setDefault(id: String) {
        dataStore.edit { prefs ->
            if (readEntries(prefs).any { it.info.id == id }) { prefs[KEY_DEFAULT_ID] = id }
        }
    }

    /** The default key's provider and decrypted secret, or null if no usable key is saved. */
    suspend fun defaultKey(): Pair<AiProviderType, String>? {
        val prefs = preferences.first()
        val entry = defaultEntry(prefs, readEntries(prefs)) ?: return null
        val secret = ApiKeyCipher.decrypt(entry.encryptedSecret) ?: return null
        return entry.info.provider to secret
    }

    /**
     * Every usable key for [provider], in the order to try them: the default key first (if it
     * belongs to [provider]), then the rest newest first. Used for key fallback.
     */
    suspend fun credentialsFor(provider: AiProviderType): List<ApiCredential> {
        val prefs = preferences.first()
        val defaultId = prefs[KEY_DEFAULT_ID]
        return readEntries(prefs)
            .filter { it.info.provider == provider }
            .sortedWith(compareByDescending<Entry> { it.info.id == defaultId }.thenByDescending { it.info.addedAt })
            .mapNotNull { entry ->
                ApiKeyCipher.decrypt(entry.encryptedSecret)?.let { ApiCredential(entry.info.label, entry.info.hint, it) }
            }
    }

    /** A usable secret for [provider]: the default key if it matches, else the newest one. */
    suspend fun secretFor(provider: AiProviderType): String? {
        val prefs = preferences.first()
        val entries = readEntries(prefs).filter { it.info.provider == provider }
        val entry = entries.firstOrNull { it.info.id == prefs[KEY_DEFAULT_ID] }
            ?: entries.maxByOrNull { it.info.addedAt }
            ?: return null
        return ApiKeyCipher.decrypt(entry.encryptedSecret)
    }

    // --- Models ----------------------------------------------------------------------------------

    /** A model the user pinned for [type], or null for automatic (latest). */
    fun modelOverride(type: AiProviderType): Flow<String?> =
        preferences.map { it[modelOverrideKey(type)]?.takeIf(String::isNotBlank) }.distinctUntilChanged()

    /** Blank or null returns [type] to automatic. */
    suspend fun setModelOverride(type: AiProviderType, model: String?) {
        dataStore.edit {
            val clean = model?.trim().orEmpty()
            if (clean.isEmpty()) { it.remove(modelOverrideKey(type)) } else { it[modelOverrideKey(type)] = clean }
        }
    }

    fun latestModel(type: AiProviderType): Flow<LatestModel?> = preferences.map { prefs ->
        val model = prefs[latestModelKey(type)] ?: return@map null
        LatestModel(model, prefs[latestCheckedKey(type)] ?: 0L)
    }.distinctUntilChanged()

    suspend fun setLatestModel(type: AiProviderType, model: String, checkedAt: Long) {
        dataStore.edit {
            it[latestModelKey(type)] = model
            it[latestCheckedKey(type)] = checkedAt
        }
    }

    // --- Migration -------------------------------------------------------------------------------

    /** Moves the old one-key-per-provider storage into the key list. Idempotent. */
    suspend fun migrateLegacyKeys() {
        if (AiProviderType.entries.none { preferences.first()[legacyKeyKey(it)] != null }) return
        dataStore.edit { prefs ->
            val entries = readEntries(prefs).toMutableList()
            val legacyProvider = prefs[LEGACY_PROVIDER]
            AiProviderType.entries.forEach { type ->
                val encrypted = prefs[legacyKeyKey(type)] ?: return@forEach
                prefs.remove(legacyKeyKey(type))
                val secret = ApiKeyCipher.decrypt(encrypted) ?: return@forEach
                if (entries.any { ApiKeyCipher.decrypt(it.encryptedSecret) == secret }) return@forEach
                val entry = Entry(
                    StoredApiKey(UUID.randomUUID().toString(), type, "${type.label} key", secret.takeLast(4), System.currentTimeMillis()),
                    encrypted,
                )
                entries += entry
                if (prefs[KEY_DEFAULT_ID] == null || type.name == legacyProvider) { prefs[KEY_DEFAULT_ID] = entry.info.id }
            }
            prefs.remove(LEGACY_PROVIDER)
            writeEntries(prefs, entries)
        }
    }

    // --- Storage ---------------------------------------------------------------------------------

    private data class Entry(val info: StoredApiKey, val encryptedSecret: String)

    private fun defaultEntry(prefs: Preferences, entries: List<Entry>): Entry? =
        entries.firstOrNull { it.info.id == prefs[KEY_DEFAULT_ID] } ?: entries.maxByOrNull { it.info.addedAt }

    private fun readEntries(prefs: Preferences): List<Entry> {
        val raw = prefs[KEY_KEYS] ?: return emptyList()
        val array = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val json = array.optJSONObject(i) ?: return@mapNotNull null
            val provider = AiProviderType.entries.firstOrNull { it.name == json.optString("provider") }
                ?: return@mapNotNull null
            Entry(
                info = StoredApiKey(
                    id = json.optString("id").ifEmpty { return@mapNotNull null },
                    provider = provider,
                    label = json.optString("label"),
                    hint = json.optString("hint"),
                    addedAt = json.optLong("added_at"),
                ),
                encryptedSecret = json.optString("secret").ifEmpty { return@mapNotNull null },
            )
        }
    }

    private fun writeEntries(prefs: MutablePreferences, entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.info.id)
                    .put("provider", entry.info.provider.name)
                    .put("label", entry.info.label)
                    .put("hint", entry.info.hint)
                    .put("added_at", entry.info.addedAt)
                    .put("secret", entry.encryptedSecret)
            )
        }
        prefs[KEY_KEYS] = array.toString()
    }

    private companion object {
        val KEY_KEYS = stringPreferencesKey("ai_keys")
        val KEY_DEFAULT_ID = stringPreferencesKey("ai_default_key_id")
        val LEGACY_PROVIDER = stringPreferencesKey("ai_provider")

        fun legacyKeyKey(type: AiProviderType) = stringPreferencesKey("ai_key_${type.name}")
        fun modelOverrideKey(type: AiProviderType) = stringPreferencesKey("ai_model_${type.name}")
        fun latestModelKey(type: AiProviderType) = stringPreferencesKey("ai_latest_model_${type.name}")
        fun latestCheckedKey(type: AiProviderType) = longPreferencesKey("ai_latest_checked_${type.name}")
    }
}
