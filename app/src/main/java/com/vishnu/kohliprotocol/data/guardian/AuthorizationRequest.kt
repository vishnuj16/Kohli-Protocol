package com.vishnu.kohliprotocol.data.guardian

import org.json.JSONArray
import org.json.JSONObject

enum class ChallengePurpose { APPROVAL, CONTACT_CHECK }

enum class RequestStatus { PENDING, APPROVED, REJECTED, EXPIRED, CANCELLED, FAILED }

/** One code sent to one guardian for one request. Only a keyed hash of the code is stored. */
data class Challenge(
    val guardian: Guardian,
    val purpose: ChallengePurpose,
    val codeHash: String? = null,
    val sentAt: Long? = null,
    val sendCount: Int = 0,
    val sendError: String? = null,
    val attempts: Int = 0,
    val verifiedAt: Long? = null,
) {
    val key: String get() = "${guardian.id}:${purpose.name}"
    val isVerified: Boolean get() = verifiedAt != null

    fun toJson(): JSONObject = JSONObject()
        .put("guardian", guardian.toJson())
        .put("purpose", purpose.name)
        .put("code_hash", codeHash ?: JSONObject.NULL)
        .put("sent_at", sentAt ?: JSONObject.NULL)
        .put("send_count", sendCount)
        .put("send_error", sendError ?: JSONObject.NULL)
        .put("attempts", attempts)
        .put("verified_at", verifiedAt ?: JSONObject.NULL)

    companion object {
        fun fromJson(json: JSONObject) = Challenge(
            guardian = Guardian.fromJson(json.getJSONObject("guardian")),
            purpose = ChallengePurpose.valueOf(json.getString("purpose")),
            codeHash = json.optNullableString("code_hash"),
            sentAt = json.optNullableLong("sent_at"),
            sendCount = json.optInt("send_count"),
            sendError = json.optNullableString("send_error"),
            attempts = json.optInt("attempts"),
            verifiedAt = json.optNullableLong("verified_at"),
        )
    }
}

/**
 * A request to perform one [ProtectedAction]. Short-lived, single-use, and bound to the exact
 * serialized action in [actionJson] — codes are hashed together with it, so an approval for
 * one action can never authorize another.
 */
data class AuthorizationRequest(
    val id: String,
    val actionJson: String,
    val rule: AuthorizationPolicy.ApproverRule,
    val createdAt: Long,
    val expiresAt: Long,
    val challenges: List<Challenge>,
    val status: RequestStatus = RequestStatus.PENDING,
    /** Why it ended (rejected/failed reason, or "applied"). */
    val outcome: String? = null,
) {
    val action: ProtectedAction get() = ProtectedAction.fromJson(JSONObject(actionJson))

    fun isExpired(now: Long = System.currentTimeMillis()) = now >= expiresAt

    val approvals: List<Challenge> get() = challenges.filter { it.purpose == ChallengePurpose.APPROVAL }

    /** Every required approval and every contact check is verified. */
    val isSatisfied: Boolean
        get() {
            val approved = when (rule) {
                AuthorizationPolicy.ApproverRule.ANY_ONE -> approvals.any { it.isVerified }
                AuthorizationPolicy.ApproverRule.ALL -> approvals.all { it.isVerified }
            }
            return approved && challenges.filter { it.purpose == ChallengePurpose.CONTACT_CHECK }.all { it.isVerified }
        }

    fun withChallenge(updated: Challenge) =
        copy(challenges = challenges.map { if (it.key == updated.key) updated else it })

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("action", actionJson)
        .put("rule", rule.name)
        .put("created_at", createdAt)
        .put("expires_at", expiresAt)
        .put("challenges", JSONArray(challenges.map { it.toJson() }))
        .put("status", status.name)
        .put("outcome", outcome ?: JSONObject.NULL)

    companion object {
        fun fromJson(json: JSONObject): AuthorizationRequest {
            val challenges = json.getJSONArray("challenges")
            return AuthorizationRequest(
                id = json.getString("id"),
                actionJson = json.getString("action"),
                rule = AuthorizationPolicy.ApproverRule.valueOf(json.getString("rule")),
                createdAt = json.getLong("created_at"),
                expiresAt = json.getLong("expires_at"),
                challenges = (0 until challenges.length()).map { Challenge.fromJson(challenges.getJSONObject(it)) },
                status = RequestStatus.valueOf(json.getString("status")),
                outcome = json.optNullableString("outcome"),
            )
        }
    }
}

internal fun JSONObject.optNullableString(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null

internal fun JSONObject.optNullableLong(key: String): Long? =
    if (has(key) && !isNull(key)) getLong(key) else null
