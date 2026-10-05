package com.vishnu.kohliprotocol.enforcement

import com.vishnu.kohliprotocol.data.restrictions.RestrictionCategory
import com.vishnu.kohliprotocol.data.restrictions.RestrictionLists
import java.time.LocalTime

enum class RestrictionReason {
    /** Food-delivery apps: blocked at all times. */
    FOOD_DELIVERY,

    /** Payment/UPI apps inside the 21:00–09:00 window. */
    NIGHTTIME_PAYMENT,

    /** Payment/UPI apps while the system clock or timezone looks manipulated (fail closed). */
    UNTRUSTED_CLOCK,

    /** Controlled games while the latest weekly result has not unlocked them. */
    GAMES_LOCKED,
}

/**
 * The restriction rules Rational Vishnu has set. The app lists come from
 * [RestrictionLists] (built-in defaults + additions − Guardian-approved removals).
 */
object RestrictionPolicy {

    val NIGHT_START: LocalTime = LocalTime.of(21, 0)
    val NIGHT_END: LocalTime = LocalTime.of(9, 0)

    /** True for 21:00 (inclusive) through 09:00 (exclusive). */
    fun isNight(time: LocalTime): Boolean =
        !time.isBefore(NIGHT_START) || time.isBefore(NIGHT_END)

    /**
     * Returns why [packageName] must be blocked right now, or null if it may run.
     * [suspended] are categories paused by an active, Guardian-approved emergency override.
     */
    fun evaluate(
        packageName: String,
        clock: TrustedClock,
        lists: RestrictionLists,
        gamesLocked: Boolean,
        suspended: Set<RestrictionCategory> = emptySet(),
    ): RestrictionReason? {
        fun blocks(category: RestrictionCategory) = packageName in lists[category] && category !in suspended
        return when {
            blocks(RestrictionCategory.FOOD_DELIVERY) -> RestrictionReason.FOOD_DELIVERY
            blocks(RestrictionCategory.NIGHT_PAYMENT) -> {
                val reading = clock.now()
                when {
                    reading.isTampered -> RestrictionReason.UNTRUSTED_CLOCK
                    isNight(reading.localTime) -> RestrictionReason.NIGHTTIME_PAYMENT
                    else -> null
                }
            }
            gamesLocked && blocks(RestrictionCategory.GAMES) -> RestrictionReason.GAMES_LOCKED
            else -> null
        }
    }
}
