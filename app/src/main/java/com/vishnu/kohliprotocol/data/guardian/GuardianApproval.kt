package com.vishnu.kohliprotocol.data.guardian

/**
 * Proof that Guardian Gate approved one specific request. Only [GuardianGateManager] creates
 * these (after every required guardian verified their code), and weakening operations in the
 * repositories require one — there is no boolean "authorized" flag to flip.
 */
class GuardianApproval internal constructor(
    val requestId: String,
    val guardianNames: List<String>,
) {
    val auditMetadata: String get() = "request=$requestId; approved by ${guardianNames.joinToString(" + ")}"
}
