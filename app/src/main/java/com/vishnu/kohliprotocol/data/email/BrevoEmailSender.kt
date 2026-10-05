package com.vishnu.kohliprotocol.data.email

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class EmailDeliveryException(message: String) : Exception(message)

/** A file to attach, sent base64-encoded. */
class EmailAttachment(val name: String, val bytes: ByteArray)

/**
 * Brevo transactional email (`POST /v3/smtp/email`). One instance per account: Guardian Gate
 * and weekly reports each use their own key and sender, entered at runtime and stored
 * encrypted — nothing is compiled into the APK.
 */
class BrevoEmailSender(
    private val apiKey: String,
    private val senderEmail: String,
    private val senderName: String,
) {
    /** Throws [EmailDeliveryException] with a user-readable reason on failure. */
    suspend fun send(
        to: String,
        toName: String,
        subject: String,
        text: String,
        attachments: List<EmailAttachment> = emptyList(),
    ) {
        val payload = JSONObject()
            .put("sender", JSONObject().put("email", senderEmail).put("name", senderName))
            .put("to", JSONArray().put(JSONObject().put("email", to).put("name", toName)))
            .put("subject", subject)
            .put("textContent", text)
        if (attachments.isNotEmpty()) {
            payload.put(
                "attachment",
                JSONArray(attachments.map {
                    JSONObject().put("name", it.name).put("content", Base64.encodeToString(it.bytes, Base64.NO_WRAP))
                }),
            )
        }
        val request = Request.Builder()
            .url(URL)
            .header("api-key", apiKey)
            .header("accept", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val detail = runCatching { JSONObject(response.body?.string().orEmpty()).optString("message") }
                            .getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP ${response.code}"
                        throw EmailDeliveryException("Email not sent: $detail")
                    }
                }
            } catch (e: IOException) {
                throw EmailDeliveryException("Email not sent: ${e.message ?: "network error"}")
            }
        }
    }

    private companion object {
        const val URL = "https://api.brevo.com/v3/smtp/email"

        val client: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
