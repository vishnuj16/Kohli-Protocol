package com.vishnu.kohliprotocol.data.guardian

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * One-time challenge codes. A code is never stored: only an HMAC of
 * (request id | challenge | exact action | code), keyed with a Keystore key that cannot leave
 * the device. Reading the app's storage therefore reveals nothing usable, and a hash for one
 * request/action can't validate a code for another.
 */
internal object ChallengeCodes {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "kohli_guardian_challenge_hmac"
    private const val DIGITS = 6

    private val random = SecureRandom()

    fun newCode(): String = buildString { repeat(DIGITS) { append(random.nextInt(10)) } }

    fun hash(request: AuthorizationRequest, challenge: Challenge, code: String): String {
        val mac = Mac.getInstance("HmacSHA256").apply { init(secretKey()) }
        val message = "${request.id}|${challenge.key}|${request.actionJson}|$code"
        return Base64.encodeToString(mac.doFinal(message.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    fun matches(request: AuthorizationRequest, challenge: Challenge, code: String): Boolean {
        val stored = challenge.codeHash ?: return false
        val candidate = hash(request, challenge, code.trim())
        return MessageDigest.isEqual(stored.toByteArray(), candidate.toByteArray())
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE).apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN).build())
        }.generateKey()
    }
}
