package com.vishnu.kohliprotocol.data.guardian

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import com.vishnu.kohliprotocol.data.email.BrevoEmailSender
import com.vishnu.kohliprotocol.data.email.EmailDeliveryException

class GuardianDeliveryException(message: String) : Exception(message)

/**
 * Delivers Guardian Gate messages over each guardian's configured channel:
 * - EMAIL via the Guardian Brevo account (key entered in Security settings, stored
 *   encrypted — never compiled into the APK; separate from the report account);
 * - SMS via this phone's SIM.
 */
class GuardianMessenger(context: Context, private val store: GuardianStore) {

    private val appContext = context.applicationContext

    /** Throws [GuardianDeliveryException] with a user-readable reason on failure. */
    suspend fun send(guardian: Guardian, subject: String, body: String) {
        val destination = guardian.destination
            ?: throw GuardianDeliveryException("${guardian.name} has no ${guardian.channel.label} contact")
        when (guardian.channel) {
            GuardianChannel.EMAIL -> sendEmail(destination, guardian.name, subject, body)
            GuardianChannel.SMS -> sendSms(destination, body)
        }
    }

    fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /** Uses the Guardian Brevo account (Security → Code delivery), never the report account. */
    private suspend fun sendEmail(to: String, toName: String, subject: String, body: String) {
        val credentials = store.emailCredentials()
            ?: throw GuardianDeliveryException("Email delivery isn't set up (Security → Code delivery)")
        try {
            BrevoEmailSender(credentials.apiKey, credentials.senderEmail, credentials.senderName)
                .send(to, toName, subject, body)
        } catch (e: EmailDeliveryException) {
            throw GuardianDeliveryException(e.message ?: "Email not sent")
        }
    }

    private fun sendSms(phone: String, body: String) {
        if (!hasSmsPermission()) throw GuardianDeliveryException("SMS permission not granted (Security → Allow SMS)")
        try {
            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appContext.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            sms.sendMultipartTextMessage(phone.replace(" ", ""), null, sms.divideMessage(body), null, null)
        } catch (e: Exception) {
            throw GuardianDeliveryException("SMS not sent: ${e.message ?: e.javaClass.simpleName}")
        }
    }

}
