package com.vishnu.kohliprotocol.data.guardian

import com.vishnu.kohliprotocol.data.local.entity.AuditAction
import com.vishnu.kohliprotocol.data.restrictions.RestrictionCategory
import org.json.JSONArray
import org.json.JSONObject

/**
 * One exact operation that weakens the protocol. A Guardian Gate approval covers this action
 * with these exact values and nothing else — the request stores the serialized action, and
 * that is what gets executed, never values re-supplied by the UI.
 */
sealed class ProtectedAction {

    /** The "*_REQUESTED" audit event for this kind of action, if it has one. */
    abstract val requestedAudit: AuditAction?

    /** Plain-language description shown to the user and sent to guardians. */
    abstract fun describe(): String

    abstract fun toJson(): JSONObject

    data class LowerBiryaniParameter(val from: Float, val to: Float) : ProtectedAction() {
        override val requestedAudit = AuditAction.BIRYANI_PARAMETER_CHANGE_REQUESTED
        override fun describe() = "Lower the Biryani Parameter from $from to $to"
        override fun toJson(): JSONObject = JSONObject().put("type", TYPE).put("from", from.toDouble()).put("to", to.toDouble())
        companion object { const val TYPE = "lower_biryani_parameter" }
    }

    data class RemoveRestrictedApp(
        val category: RestrictionCategory,
        val packageName: String,
        val label: String,
    ) : ProtectedAction() {
        override val requestedAudit = AuditAction.RESTRICTED_APP_REMOVAL_REQUESTED
        override fun describe() = "Stop restricting $label ($packageName) — ${category.label}"
        override fun toJson(): JSONObject = JSONObject()
            .put("type", TYPE).put("category", category.name).put("package", packageName).put("label", label)
        companion object { const val TYPE = "remove_restricted_app" }
    }

    data class EmergencyOverride(
        val categories: Set<RestrictionCategory>,
        val minutes: Int,
        val reason: String,
    ) : ProtectedAction() {
        override val requestedAudit = AuditAction.EMERGENCY_OVERRIDE_REQUESTED
        override fun describe() =
            "Emergency override: suspend ${categories.sortedBy { it.ordinal }.joinToString { it.label }} " +
                "for $minutes minutes. Reason: $reason"
        override fun toJson(): JSONObject = JSONObject()
            .put("type", TYPE)
            .put("categories", JSONArray(categories.sortedBy { it.ordinal }.map { it.name }))
            .put("minutes", minutes)
            .put("reason", reason)
        companion object { const val TYPE = "emergency_override" }
    }

    /**
     * Replace the whole guardian configuration. [recoveryOf] marks the recovery path: one
     * guardian lost access and is being replaced, approved by the remaining guardian(s).
     */
    data class ChangeGuardians(
        val guardians: List<Guardian>,
        val recoveryOf: String? = null,
    ) : ProtectedAction() {
        override val requestedAudit: AuditAction? = null
        override fun describe(): String {
            val list = guardians.joinToString { "${it.name} (${it.channel.label} ${it.maskedDestination})" }
            return if (recoveryOf != null) "Guardian recovery — new guardian configuration: $list"
            else "Set guardians to: $list"
        }
        override fun toJson(): JSONObject = JSONObject()
            .put("type", TYPE)
            .put("guardians", JSONArray(guardians.map { it.toJson() }))
            .put("recovery_of", recoveryOf ?: JSONObject.NULL)
        companion object { const val TYPE = "change_guardians" }
    }

    companion object {
        fun fromJson(json: JSONObject): ProtectedAction = when (val type = json.getString("type")) {
            LowerBiryaniParameter.TYPE ->
                LowerBiryaniParameter(json.getDouble("from").toFloat(), json.getDouble("to").toFloat())
            RemoveRestrictedApp.TYPE -> RemoveRestrictedApp(
                RestrictionCategory.valueOf(json.getString("category")),
                json.getString("package"),
                json.getString("label"),
            )
            EmergencyOverride.TYPE -> {
                val names = json.getJSONArray("categories")
                EmergencyOverride(
                    categories = (0 until names.length()).map { RestrictionCategory.valueOf(names.getString(it)) }.toSet(),
                    minutes = json.getInt("minutes"),
                    reason = json.getString("reason"),
                )
            }
            ChangeGuardians.TYPE -> {
                val array = json.getJSONArray("guardians")
                ChangeGuardians(
                    guardians = (0 until array.length()).map { Guardian.fromJson(array.getJSONObject(it)) },
                    recoveryOf = json.optString("recovery_of").takeIf { !json.isNull("recovery_of") && it.isNotEmpty() },
                )
            }
            else -> throw IllegalArgumentException("Unknown protected action: $type")
        }
    }
}
