package com.vishnu.kohliprotocol.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Shared JSON-over-HTTP transport for AI providers, mapping failures to [AiException]. */
internal object AiHttp {

    private val JSON_TYPE = "application/json".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        // Models that reason before answering can take a few minutes on a photo-heavy day.
        .readTimeout(4, TimeUnit.MINUTES)
        .callTimeout(5, TimeUnit.MINUTES)
        .build()

    suspend fun postJson(url: String, headers: Map<String, String>, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .post(body.toString().toRequestBody(JSON_TYPE))
            .build()
        return execute(request)
    }

    private suspend fun execute(request: Request): JSONObject {
        val response = try {
            client.newCall(request).await()
        } catch (e: IOException) {
            throw AiException.Network(e)
        }

        return withContext(Dispatchers.IO) {
            response.use {
                val text = it.body?.string().orEmpty()
                if (!it.isSuccessful) throw httpError(it.code, it.header("retry-after"), text)
                try {
                    JSONObject(text)
                } catch (e: JSONException) {
                    throw AiException.InvalidResponse("provider returned a non-JSON body")
                }
            }
        }
    }

    suspend fun getJson(url: String, headers: Map<String, String>): JSONObject {
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .get()
            .build()
        return execute(request)
    }

    private fun httpError(code: Int, retryAfter: String?, body: String): AiException {
        // Both Anthropic and Google return {"error": {"message": ...}}.
        val detail = runCatching { JSONObject(body).getJSONObject("error").optString("message") }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: body.take(200).ifBlank { "HTTP $code" }
        return when (code) {
            401, 403 -> AiException.Auth(detail)
            429 -> AiException.RateLimited(retryAfter?.trim()?.toLongOrNull())
            else -> AiException.Http(code, detail)
        }
    }

    /** Cancellable bridge: cancelling the coroutine cancels the HTTP call. */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel() }
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) = cont.resume(response)
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        })
    }
}
