package com.vishnu.kohliprotocol.data.reports

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vishnu.kohliprotocol.data.email.BrevoEmailSender
import com.vishnu.kohliprotocol.data.preferences.ApiKeyCipher
import com.vishnu.kohliprotocol.data.preferences.kohliDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/** What the UI may know about report email (never the API key). */
data class ReportEmailInfo(
    val senderEmail: String,
    val senderName: String,
    val recipientEmail: String,
    val hasApiKey: Boolean,
)

/** A ready-to-use sender plus the address reports go to. */
class ReportMailer(val sender: BrevoEmailSender, val recipientEmail: String)

/**
 * The report Brevo account (REPORT key/sender), kept completely separate from the Guardian
 * account: private weekly reports go to your own inbox through your own account, while Guardian
 * codes go through the guardian's. Also remembers which reports were already emailed.
 */
class ReportEmailStore(context: Context) {

    private val dataStore = context.applicationContext.kohliDataStore

    private val preferences: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val info: Flow<ReportEmailInfo?> = preferences.map { prefs ->
        val sender = prefs[KEY_SENDER] ?: return@map null
        ReportEmailInfo(
            senderEmail = sender,
            senderName = prefs[KEY_SENDER_NAME] ?: DEFAULT_SENDER_NAME,
            recipientEmail = prefs[KEY_RECIPIENT].orEmpty(),
            hasApiKey = prefs[KEY_API_KEY] != null,
        )
    }.distinctUntilChanged()

    /** [apiKey] null or blank keeps the stored key. */
    suspend fun save(apiKey: String?, senderEmail: String, senderName: String, recipientEmail: String) {
        val encrypted = apiKey?.trim()?.takeIf { it.isNotEmpty() }?.let(ApiKeyCipher::encrypt)
        dataStore.edit {
            it[KEY_SENDER] = senderEmail.trim()
            it[KEY_SENDER_NAME] = senderName.trim().ifEmpty { DEFAULT_SENDER_NAME }
            it[KEY_RECIPIENT] = recipientEmail.trim()
            if (encrypted != null) { it[KEY_API_KEY] = encrypted }
        }
    }

    /** Null until key, sender and recipient are all set. */
    suspend fun mailer(): ReportMailer? {
        val prefs = preferences.first()
        val sender = prefs[KEY_SENDER]?.takeIf { it.isNotBlank() } ?: return null
        val recipient = prefs[KEY_RECIPIENT]?.takeIf { it.isNotBlank() } ?: return null
        val key = prefs[KEY_API_KEY]?.let(ApiKeyCipher::decrypt) ?: return null
        return ReportMailer(BrevoEmailSender(key, sender, prefs[KEY_SENDER_NAME] ?: DEFAULT_SENDER_NAME), recipient)
    }

    val emailedReportIds: Flow<Set<String>> =
        preferences.map { it[KEY_EMAILED] ?: emptySet() }.distinctUntilChanged()

    suspend fun isEmailed(reportId: Long): Boolean = reportId.toString() in (preferences.first()[KEY_EMAILED] ?: emptySet())

    suspend fun markEmailed(reportId: Long) {
        dataStore.edit { it[KEY_EMAILED] = (it[KEY_EMAILED] ?: emptySet()) + reportId.toString() }
    }

    private companion object {
        const val DEFAULT_SENDER_NAME = "Kohli Protocol"

        val KEY_API_KEY = stringPreferencesKey("report_email_api_key")
        val KEY_SENDER = stringPreferencesKey("report_email_sender")
        val KEY_SENDER_NAME = stringPreferencesKey("report_email_sender_name")
        val KEY_RECIPIENT = stringPreferencesKey("report_email_recipient")
        val KEY_EMAILED = stringSetPreferencesKey("report_emailed_ids")
    }
}
