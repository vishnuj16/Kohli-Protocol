package com.vishnu.kohliprotocol.data.guardian

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vishnu.kohliprotocol.data.preferences.ApiKeyCipher
import com.vishnu.kohliprotocol.data.preferences.kohliDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import java.io.IOException

/** What the UI may know about email delivery (never the API key itself). */
data class EmailDeliveryInfo(val senderEmail: String, val senderName: String, val hasApiKey: Boolean)

/** Decrypted delivery credentials, only for sending. */
class EmailCredentials(val apiKey: String, val senderEmail: String, val senderName: String)

/**
 * Local Guardian Gate state: guardian contacts, email-delivery settings (API key encrypted
 * with the Keystore — nothing is compiled into the APK), and recent authorization requests.
 */
class GuardianStore(context: Context) {

    private val dataStore = context.applicationContext.kohliDataStore

    private val preferences: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    // --- Guardians -----------------------------------------------------------------------------

    val guardians: Flow<List<Guardian>> = preferences.map { readGuardians(it) }.distinctUntilChanged()

    suspend fun currentGuardians(): List<Guardian> = readGuardians(preferences.first())

    /** Only [GuardianGateManager] calls this, after the change was authorized. */
    internal suspend fun setGuardians(guardians: List<Guardian>) {
        dataStore.edit { it[KEY_GUARDIANS] = JSONArray(guardians.map { g -> g.toJson() }).toString() }
    }

    private fun readGuardians(prefs: Preferences): List<Guardian> {
        val array = prefs[KEY_GUARDIANS]?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return (0 until array.length()).mapNotNull { runCatching { Guardian.fromJson(array.getJSONObject(it)) }.getOrNull() }
    }

    // --- Email delivery ------------------------------------------------------------------------

    val emailDelivery: Flow<EmailDeliveryInfo?> = preferences.map { prefs ->
        val sender = prefs[KEY_EMAIL_SENDER] ?: return@map null
        EmailDeliveryInfo(sender, prefs[KEY_EMAIL_SENDER_NAME] ?: DEFAULT_SENDER_NAME, prefs[KEY_EMAIL_API_KEY] != null)
    }.distinctUntilChanged()

    /** [apiKey] null or blank keeps the stored key. */
    suspend fun setEmailDelivery(apiKey: String?, senderEmail: String, senderName: String) {
        val encrypted = apiKey?.trim()?.takeIf { it.isNotEmpty() }?.let(ApiKeyCipher::encrypt)
        dataStore.edit {
            it[KEY_EMAIL_SENDER] = senderEmail.trim()
            it[KEY_EMAIL_SENDER_NAME] = senderName.trim().ifEmpty { DEFAULT_SENDER_NAME }
            if (encrypted != null) { it[KEY_EMAIL_API_KEY] = encrypted }
        }
    }

    suspend fun emailCredentials(): EmailCredentials? {
        val prefs = preferences.first()
        val sender = prefs[KEY_EMAIL_SENDER] ?: return null
        val key = prefs[KEY_EMAIL_API_KEY]?.let(ApiKeyCipher::decrypt) ?: return null
        return EmailCredentials(key, sender, prefs[KEY_EMAIL_SENDER_NAME] ?: DEFAULT_SENDER_NAME)
    }

    // --- Authorization requests -----------------------------------------------------------------

    fun observeRequest(id: String): Flow<AuthorizationRequest?> =
        preferences.map { prefs -> readRequests(prefs).firstOrNull { it.id == id } }.distinctUntilChanged()

    suspend fun requests(): List<AuthorizationRequest> = readRequests(preferences.first())

    suspend fun request(id: String): AuthorizationRequest? = requests().firstOrNull { it.id == id }

    /** Insert or replace; keeps only the most recent [MAX_REQUESTS]. */
    internal suspend fun saveRequest(request: AuthorizationRequest) {
        dataStore.edit { prefs ->
            val others = readRequests(prefs).filterNot { it.id == request.id }
            val kept = (others + request).sortedByDescending { it.createdAt }.take(MAX_REQUESTS)
            prefs[KEY_REQUESTS] = JSONArray(kept.map { it.toJson() }).toString()
        }
    }

    private fun readRequests(prefs: Preferences): List<AuthorizationRequest> {
        val array = prefs[KEY_REQUESTS]?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return (0 until array.length()).mapNotNull {
            // Requests for actions that no longer exist (e.g. the retired mock games pass) are
            // dropped rather than left to fail on every read.
            runCatching { AuthorizationRequest.fromJson(array.getJSONObject(it)).also { request -> request.action } }.getOrNull()
        }
    }

    private companion object {
        const val MAX_REQUESTS = 30
        const val DEFAULT_SENDER_NAME = "Kohli Protocol"

        val KEY_GUARDIANS = stringPreferencesKey("guardians")
        val KEY_REQUESTS = stringPreferencesKey("guardian_requests")
        val KEY_EMAIL_API_KEY = stringPreferencesKey("guardian_email_api_key")
        val KEY_EMAIL_SENDER = stringPreferencesKey("guardian_email_sender")
        val KEY_EMAIL_SENDER_NAME = stringPreferencesKey("guardian_email_sender_name")
    }
}
