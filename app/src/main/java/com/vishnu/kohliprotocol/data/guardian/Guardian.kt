package com.vishnu.kohliprotocol.data.guardian

import org.json.JSONObject
import java.util.UUID

enum class GuardianChannel(val label: String) { EMAIL("Email"), SMS("SMS") }

/**
 * A trusted guardian (e.g. mother, trusted friend). Only contact details are stored locally —
 * never a guardian password or any reusable secret.
 */
data class Guardian(
    val id: String,
    val name: String,
    val email: String?,
    val phone: String?,
    val channel: GuardianChannel,
    val addedAt: Long,
) {
    /** Where challenges go, for the chosen [channel]. */
    val destination: String?
        get() = when (channel) {
            GuardianChannel.EMAIL -> email
            GuardianChannel.SMS -> phone
        }?.trim()?.takeIf { it.isNotEmpty() }

    /** Safe to show on screen and in the audit log. */
    val maskedDestination: String
        get() {
            val value = destination ?: return "no contact"
            return when (channel) {
                GuardianChannel.EMAIL -> {
                    val at = value.indexOf('@')
                    if (at <= 1) "•••${value.substring(maxOf(at, 0))}" else "${value.first()}•••${value.substring(at)}"
                }
                GuardianChannel.SMS -> "•••• ${value.takeLast(3)}"
            }
        }

    fun sameContactAs(other: Guardian): Boolean = channel == other.channel && destination == other.destination

    /** Null if usable; otherwise what is wrong. */
    fun validationError(): String? = when {
        name.isBlank() -> "Every guardian needs a name."
        destination == null -> "$name needs ${if (channel == GuardianChannel.EMAIL) "an email address" else "a phone number"}."
        channel == GuardianChannel.EMAIL && !EMAIL.matches(destination!!) -> "$name's email address looks invalid."
        channel == GuardianChannel.SMS && !PHONE.matches(destination!!) -> "$name's phone number looks invalid (use +91…)."
        else -> null
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("email", email ?: JSONObject.NULL)
        .put("phone", phone ?: JSONObject.NULL)
        .put("channel", channel.name)
        .put("added_at", addedAt)

    companion object {
        private val EMAIL = Regex("""[^@\s]+@[^@\s]+\.[^@\s]+""")
        private val PHONE = Regex("""\+?[0-9 ]{8,16}""")

        fun new(name: String, email: String?, phone: String?, channel: GuardianChannel) = Guardian(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            email = email?.trim()?.ifEmpty { null },
            phone = phone?.trim()?.ifEmpty { null },
            channel = channel,
            addedAt = System.currentTimeMillis(),
        )

        fun fromJson(json: JSONObject) = Guardian(
            id = json.getString("id"),
            name = json.getString("name"),
            email = json.optString("email").takeIf { json.has("email") && !json.isNull("email") },
            phone = json.optString("phone").takeIf { json.has("phone") && !json.isNull("phone") },
            channel = GuardianChannel.valueOf(json.getString("channel")),
            addedAt = json.optLong("added_at"),
        )
    }
}
