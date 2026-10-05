package com.vishnu.kohliprotocol.data.guardian

/**
 * Who must approve what — explicit, deterministic, and kept out of UI code.
 *
 * | Action                         | Approvers required                              |
 * |--------------------------------|-------------------------------------------------|
 * | Lower Biryani Parameter        | any one guardian                                |
 * | Remove a restricted app        | any one guardian                                |
 * | Emergency override             | every guardian                                  |
 * | Change guardians               | every current guardian                          |
 * | Guardian recovery (lost access)| every *other* current guardian                  |
 * | First-time guardian setup      | nobody (making the protocol stricter)           |
 *
 * In addition, every new or changed guardian contact must receive and return a code
 * ("contact check") so a typo can't lock the protocol forever — but a new guardian never
 * counts as an approver of their own addition.
 */
object AuthorizationPolicy {

    enum class ApproverRule { ANY_ONE, ALL }

    data class Requirement(
        val rule: ApproverRule,
        /** Guardians who may approve (ANY_ONE: one of them; ALL: all of them). */
        val approvers: List<Guardian>,
        /** New/changed contacts that must prove they receive codes. */
        val contactChecks: List<Guardian>,
    )

    sealed interface Decision {
        data class Allowed(val requirement: Requirement) : Decision
        data class Refused(val reason: String) : Decision
    }

    fun evaluate(action: ProtectedAction, current: List<Guardian>): Decision = when (action) {
        is ProtectedAction.LowerBiryaniParameter,
        is ProtectedAction.RemoveRestrictedApp ->
            if (current.isEmpty()) refusedNoGuardians()
            else Decision.Allowed(Requirement(ApproverRule.ANY_ONE, current, emptyList()))

        is ProtectedAction.EmergencyOverride ->
            if (current.isEmpty()) refusedNoGuardians()
            else Decision.Allowed(Requirement(ApproverRule.ALL, current, emptyList()))

        is ProtectedAction.ChangeGuardians -> changeGuardians(action, current)
    }

    private fun changeGuardians(action: ProtectedAction.ChangeGuardians, current: List<Guardian>): Decision {
        val proposed = action.guardians
        if (proposed.size !in 1..MAX_GUARDIANS) return Decision.Refused("Set up one or two guardians.")
        proposed.firstNotNullOfOrNull { it.validationError() }?.let { return Decision.Refused(it) }
        if (proposed.map { it.destination }.distinct().size != proposed.size) {
            return Decision.Refused("Each guardian needs their own contact.")
        }
        val contactChecks = proposed.filter { new -> current.none { it.id == new.id && it.sameContactAs(new) } }

        if (current.isEmpty()) {
            // First-time setup makes the protocol stricter, so no approval — only contact checks.
            return Decision.Allowed(Requirement(ApproverRule.ALL, emptyList(), proposed))
        }

        val lost = action.recoveryOf ?: return Decision.Allowed(Requirement(ApproverRule.ALL, current, contactChecks))

        // Recovery: only the lost guardian may change; the others must stay exactly as they are.
        val remaining = current.filter { it.id != lost }
        if (remaining.size == current.size) return Decision.Refused("Unknown guardian to recover.")
        if (remaining.isEmpty()) {
            return Decision.Refused("Recovery needs another guardian to approve it. With a single guardian there is no one to vouch for the change.")
        }
        val keptUnchanged = remaining.all { kept -> proposed.any { it.id == kept.id && it.sameContactAs(kept) && it.name == kept.name } }
        if (!keptUnchanged || proposed.size > current.size) {
            return Decision.Refused("Recovery can only replace the guardian who lost access.")
        }
        return Decision.Allowed(Requirement(ApproverRule.ALL, remaining, contactChecks))
    }

    private fun refusedNoGuardians() =
        Decision.Refused("No guardians are set up yet. Add them in Security before rules can be relaxed.")

    const val MAX_GUARDIANS = 2
}
