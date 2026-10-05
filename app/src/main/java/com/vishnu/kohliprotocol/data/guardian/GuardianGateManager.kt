package com.vishnu.kohliprotocol.data.guardian

import android.util.Log
import com.vishnu.kohliprotocol.data.local.entity.AuditAction
import com.vishnu.kohliprotocol.data.repository.AuditRepository
import com.vishnu.kohliprotocol.data.repository.EnforcementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Guardian Gate: authorization for anything that makes Kohli Protocol easier to bypass.
 *
 * ```
 * start(action) → policy decides approvers → one-time code sent to each guardian
 *   → user enters the codes the guardians chose to share → all required codes verified
 *   → the exact stored action is executed → request closed (single use) → audit log
 * ```
 * Codes expire with the request ([REQUEST_TTL_MILLIS]), are hashed with the exact action,
 * and are cleared once used. Wrong codes count toward a lockout.
 */
class GuardianGateManager(
    private val store: GuardianStore,
    private val messenger: GuardianMessenger,
    private val enforcement: EnforcementRepository,
    private val audit: AuditRepository,
) {
    sealed interface StartResult {
        data class Started(val request: AuthorizationRequest) : StartResult
        data class Refused(val reason: String) : StartResult
    }

    sealed interface CodeResult {
        /** This guardian's code is accepted; other approvals are still needed. */
        object Verified : CodeResult
        /** Every required approval is in and the change has been applied. */
        object Approved : CodeResult
        data class Wrong(val attemptsLeft: Int) : CodeResult
        data class Closed(val message: String) : CodeResult
    }

    private val mutex = Mutex()

    val guardians: Flow<List<Guardian>> = store.guardians

    fun observe(requestId: String): Flow<AuthorizationRequest?> = store.observeRequest(requestId)

    suspend fun decisionFor(action: ProtectedAction): AuthorizationPolicy.Decision =
        AuthorizationPolicy.evaluate(action, store.currentGuardians())

    /**
     * Opens (or resumes) a request for [action] and sends the codes. For "any one guardian"
     * actions, [approverId] picks which guardian to ask.
     */
    suspend fun start(action: ProtectedAction, approverId: String? = null): StartResult = mutex.withLock {
        val requirement = when (val decision = AuthorizationPolicy.evaluate(action, store.currentGuardians())) {
            is AuthorizationPolicy.Decision.Refused -> {
                audit.log(AuditAction.GUARDIAN_AUTH_REJECTED, "Refused before sending: ${action.describe()} — ${decision.reason}")
                return StartResult.Refused(decision.reason)
            }
            is AuthorizationPolicy.Decision.Allowed -> decision.requirement
        }

        // An identical request is already waiting: resume it instead of re-texting guardians.
        store.requests().firstOrNull {
            it.status == RequestStatus.PENDING && !it.isExpired() && runCatching { it.action }.getOrNull() == action
        }?.let { return StartResult.Started(it) }

        val approvers = when (requirement.rule) {
            AuthorizationPolicy.ApproverRule.ANY_ONE ->
                listOf(requirement.approvers.firstOrNull { it.id == approverId } ?: requirement.approvers.first())
            AuthorizationPolicy.ApproverRule.ALL -> requirement.approvers
        }
        val now = System.currentTimeMillis()
        var request = AuthorizationRequest(
            id = UUID.randomUUID().toString(),
            actionJson = action.toJson().toString(),
            rule = requirement.rule,
            createdAt = now,
            expiresAt = now + REQUEST_TTL_MILLIS,
            challenges = approvers.map { Challenge(it, ChallengePurpose.APPROVAL) } +
                requirement.contactChecks.map { Challenge(it, ChallengePurpose.CONTACT_CHECK) },
        )
        store.saveRequest(request)

        val needs = when {
            approvers.isEmpty() -> "contact confirmation only (first-time setup)"
            requirement.rule == AuthorizationPolicy.ApproverRule.ANY_ONE -> "approval from ${approvers.first().name}"
            else -> "approval from ${approvers.joinToString(" + ") { it.name }}"
        }
        audit.log(AuditAction.GUARDIAN_AUTH_REQUESTED, "${action.describe()} — needs $needs", metadata = "request=${request.id}")
        action.requestedAudit?.let { audit.log(it, action.describe(), metadata = "request=${request.id}") }

        for (challenge in request.challenges) request = deliver(request, challenge)
        StartResult.Started(request)
    }

    /** Sends a fresh code (the previous one stops working). Rate limited. */
    suspend fun resend(requestId: String, challengeKey: String): String? = mutex.withLock {
        val request = openRequest(requestId) ?: return "This request is no longer open."
        val challenge = request.challenges.firstOrNull { it.key == challengeKey } ?: return "Unknown guardian."
        if (challenge.isVerified) return null
        if (challenge.sendCount >= MAX_SENDS) return "Code already sent $MAX_SENDS times for this request."
        val sentAt = challenge.sentAt
        if (sentAt != null && System.currentTimeMillis() - sentAt < RESEND_COOLDOWN_MILLIS) {
            return "Wait a minute before sending another code."
        }
        deliver(request, challenge).challenges.first { it.key == challengeKey }.sendError
    }

    /** For "any one guardian" requests: also ask [guardianId] (e.g. the first one isn't answering). */
    suspend fun askAnotherGuardian(requestId: String, guardianId: String): String? = mutex.withLock {
        val request = openRequest(requestId) ?: return "This request is no longer open."
        if (request.rule != AuthorizationPolicy.ApproverRule.ANY_ONE) return "This request needs every guardian."
        if (request.approvals.any { it.guardian.id == guardianId }) return null
        val guardian = store.currentGuardians().firstOrNull { it.id == guardianId } ?: return "Unknown guardian."
        val challenge = Challenge(guardian, ChallengePurpose.APPROVAL)
        deliver(request.copy(challenges = request.challenges + challenge), challenge)
            .challenges.first { it.key == challenge.key }.sendError
    }

    suspend fun submitCode(requestId: String, challengeKey: String, code: String): CodeResult = mutex.withLock {
        val request = store.request(requestId) ?: return CodeResult.Closed("Request not found.")
        if (request.status != RequestStatus.PENDING) return CodeResult.Closed("This request is ${request.status.name.lowercase()}.")
        if (request.isExpired()) {
            expire(request)
            return CodeResult.Closed("This request expired. Start a new one.")
        }
        val challenge = request.challenges.firstOrNull { it.key == challengeKey } ?: return CodeResult.Closed("Unknown guardian.")
        if (challenge.isVerified) return CodeResult.Verified
        if (challenge.codeHash == null) return CodeResult.Closed("No code has been sent to ${challenge.guardian.name} yet.")

        if (!ChallengeCodes.matches(request, challenge, code)) {
            val attempts = challenge.attempts + 1
            var updated = request.withChallenge(challenge.copy(attempts = attempts))
            audit.log(
                AuditAction.GUARDIAN_AUTH_FAILED,
                "Wrong code for ${challenge.guardian.name} ($attempts/$MAX_ATTEMPTS)",
                metadata = "request=${request.id}",
            )
            if (attempts >= MAX_ATTEMPTS) {
                updated = updated.copy(status = RequestStatus.REJECTED, outcome = "Too many wrong codes")
                audit.log(AuditAction.GUARDIAN_AUTH_REJECTED, "Locked after $MAX_ATTEMPTS wrong codes: ${request.action.describe()}", metadata = "request=${request.id}")
            }
            store.saveRequest(updated)
            return if (updated.status == RequestStatus.REJECTED) {
                CodeResult.Closed("Too many wrong codes. This request is locked.")
            } else {
                CodeResult.Wrong(MAX_ATTEMPTS - attempts)
            }
        }

        // Correct: mark verified and forget the hash so the code can't be used again.
        var updated = request.withChallenge(challenge.copy(verifiedAt = System.currentTimeMillis(), codeHash = null))
        audit.log(
            AuditAction.GUARDIAN_AUTH_VERIFIED,
            "${challenge.guardian.name} ${if (challenge.purpose == ChallengePurpose.APPROVAL) "approved" else "confirmed their contact"}",
            metadata = "request=${request.id}; channel=${challenge.guardian.channel.label}",
        )
        if (!updated.isSatisfied) {
            store.saveRequest(updated)
            return CodeResult.Verified
        }
        updated = execute(updated)
        store.saveRequest(updated)
        if (updated.status == RequestStatus.APPROVED) CodeResult.Approved else CodeResult.Closed(updated.outcome ?: "Could not apply the change.")
    }

    suspend fun cancel(requestId: String) {
        mutex.withLock {
            val request = store.request(requestId) ?: return
            if (request.status != RequestStatus.PENDING) return
            store.saveRequest(request.copy(status = RequestStatus.CANCELLED, outcome = "Cancelled by user"))
            audit.log(AuditAction.GUARDIAN_AUTH_CANCELLED, request.action.describe(), metadata = "request=${request.id}")
        }
    }

    /** Expires stale requests and ended emergency overrides. Called every minute by the enforcement service. */
    suspend fun housekeeping() {
        mutex.withLock {
            store.requests()
                .filter { it.status == RequestStatus.PENDING && it.isExpired() }
                .forEach { expire(it) }
        }
        enforcement.expireEmergencyOverride()
    }

    // --- Internals --------------------------------------------------------------------------------

    private suspend fun openRequest(id: String): AuthorizationRequest? =
        store.request(id)?.takeIf { it.status == RequestStatus.PENDING && !it.isExpired() }

    private suspend fun expire(request: AuthorizationRequest) {
        store.saveRequest(request.copy(status = RequestStatus.EXPIRED, outcome = "Expired"))
        audit.log(AuditAction.GUARDIAN_AUTH_EXPIRED, request.action.describe(), metadata = "request=${request.id}")
    }

    /** Generates a new code, stores only its hash, and sends it. */
    private suspend fun deliver(request: AuthorizationRequest, challenge: Challenge): AuthorizationRequest {
        val code = ChallengeCodes.newCode()
        val hashed = challenge.copy(codeHash = ChallengeCodes.hash(request, challenge, code), attempts = 0)
        val (subject, body) = message(request, challenge, code)
        val updated = try {
            messenger.send(challenge.guardian, subject, body)
            audit.log(
                AuditAction.GUARDIAN_AUTH_SENT,
                "Code sent to ${challenge.guardian.name} by ${challenge.guardian.channel.label} (${challenge.guardian.maskedDestination})",
                metadata = "request=${request.id}; purpose=${challenge.purpose.name}",
            )
            hashed.copy(sentAt = System.currentTimeMillis(), sendCount = challenge.sendCount + 1, sendError = null)
        } catch (e: GuardianDeliveryException) {
            Log.w(TAG, "Guardian delivery failed: ${e.message}")
            audit.log(
                AuditAction.GUARDIAN_AUTH_SEND_FAILED,
                "Could not reach ${challenge.guardian.name}: ${e.message}",
                metadata = "request=${request.id}",
            )
            challenge.copy(codeHash = null, sendError = e.message)
        }
        val result = request.withChallenge(updated)
        store.saveRequest(result)
        return result
    }

    /** Performs exactly the stored action, after re-checking the approval still holds. */
    private suspend fun execute(request: AuthorizationRequest): AuthorizationRequest {
        val action = request.action
        val verifiedApprovers = request.approvals.filter { it.isVerified }.map { it.guardian }
        val approval = GuardianApproval(request.id, verifiedApprovers.map { it.name }.ifEmpty { listOf("first-time setup") })
        return try {
            check(stillAuthorized(action, request, verifiedApprovers)) {
                "The guardian configuration changed while this request was open"
            }
            when (action) {
                is ProtectedAction.LowerBiryaniParameter -> {
                    val current = enforcement.biryaniParameter.first()
                    check(current == action.from) { "The Biryani Parameter is now $current, not ${action.from}" }
                    enforcement.setBiryaniParameter(action.to, approval)
                }
                is ProtectedAction.RemoveRestrictedApp ->
                    enforcement.removeRestrictedApp(action.category, action.packageName, action.label, approval)
                is ProtectedAction.MockGamesPass ->
                    enforcement.startGamesTestOverride(unlocked = true, approval = approval)
                is ProtectedAction.EmergencyOverride ->
                    enforcement.startEmergencyOverride(action.categories, action.minutes, action.reason, approval)
                is ProtectedAction.ChangeGuardians -> applyGuardians(action, approval)
            }
            audit.log(AuditAction.GUARDIAN_AUTH_APPROVED, "Applied: ${action.describe()}", metadata = approval.auditMetadata)
            request.copy(status = RequestStatus.APPROVED, outcome = "Applied")
        } catch (e: Exception) {
            val reason = e.message ?: e.javaClass.simpleName
            audit.log(AuditAction.GUARDIAN_AUTH_REJECTED, "Approved but not applied: ${action.describe()} — $reason", metadata = approval.auditMetadata)
            request.copy(status = RequestStatus.FAILED, outcome = reason)
        }
    }

    /** Re-evaluates the policy against today's guardians: the verified approvers must still suffice. */
    private suspend fun stillAuthorized(
        action: ProtectedAction,
        request: AuthorizationRequest,
        verifiedApprovers: List<Guardian>,
    ): Boolean {
        val decision = AuthorizationPolicy.evaluate(action, store.currentGuardians())
        val requirement = (decision as? AuthorizationPolicy.Decision.Allowed)?.requirement ?: return false
        val verifiedIds = verifiedApprovers.map { it.id }.toSet()
        val approversOk = when (requirement.rule) {
            AuthorizationPolicy.ApproverRule.ANY_ONE -> requirement.approvers.any { it.id in verifiedIds }
            AuthorizationPolicy.ApproverRule.ALL -> requirement.approvers.all { it.id in verifiedIds }
        }
        val checkedContacts = request.challenges
            .filter { it.purpose == ChallengePurpose.CONTACT_CHECK && it.isVerified }
            .map { it.guardian }
        val contactsOk = requirement.contactChecks.all { needed -> checkedContacts.any { it.id == needed.id && it.sameContactAs(needed) } }
        return approversOk && contactsOk
    }

    private suspend fun applyGuardians(action: ProtectedAction.ChangeGuardians, approval: GuardianApproval) {
        val previous = store.currentGuardians()
        store.setGuardians(action.guardians)
        audit.log(
            AuditAction.GUARDIAN_CHANGED,
            "Guardians now: ${action.guardians.joinToString { "${it.name} (${it.channel.label} ${it.maskedDestination})" }}" +
                (if (action.recoveryOf != null) " — recovery" else ""),
            metadata = approval.auditMetadata,
        )
        // Tell anyone removed, so a guardian can't be silently swapped out.
        previous.filter { old -> action.guardians.none { it.id == old.id && it.sameContactAs(old) } }.forEach { removed ->
            runCatching {
                messenger.send(
                    removed,
                    "Kohli Protocol: you are no longer a guardian",
                    "Hi ${removed.name},\n\nVishnu's Kohli Protocol guardians were changed and this contact is no " +
                        "longer one of them. If you did not expect this, please talk to him.",
                )
            }
        }
    }

    private fun message(request: AuthorizationRequest, challenge: Challenge, code: String): Pair<String, String> {
        val expires = Instant.ofEpochMilli(request.expiresAt).atZone(ZoneId.systemDefault()).format(TIME)
        val name = challenge.guardian.name
        return when (challenge.purpose) {
            ChallengePurpose.APPROVAL -> "Kohli Protocol: approval needed" to
                "Hi $name,\n\nVishnu is asking you to approve a change to his Kohli Protocol food-discipline rules:\n\n" +
                "  ${request.action.describe()}\n\n" +
                "If you agree, tell him this code: $code\n\n" +
                "It only works for this exact request and expires at $expires. If you don't agree, just don't share it.\n\n" +
                "Request ${request.id.take(8)}"
            ChallengePurpose.CONTACT_CHECK -> "Kohli Protocol: confirm you are a guardian" to
                "Hi $name,\n\nVishnu is setting you up as a guardian for Kohli Protocol, his food-discipline app. " +
                "Guardians are asked to approve any attempt to relax his rules.\n\n" +
                "To confirm this contact works, give him this code: $code\n\n" +
                "It expires at $expires.\n\nRequest ${request.id.take(8)}"
        }
    }

    companion object {
        private const val TAG = "KohliProtocol"
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        const val REQUEST_TTL_MILLIS = 15 * 60 * 1000L
        const val MAX_ATTEMPTS = 5
        const val MAX_SENDS = 3
        const val RESEND_COOLDOWN_MILLIS = 60 * 1000L
    }
}
