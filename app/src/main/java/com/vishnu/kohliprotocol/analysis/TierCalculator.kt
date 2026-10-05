package com.vishnu.kohliprotocol.analysis

import com.vishnu.kohliprotocol.data.local.entity.DailyCategory

/**
 * Deterministic daily rating:
 * 1. calorie tier from the midpoint of the AI's calorie range;
 * 2. averaged with the AI's own 1–5 rating (rounded down — when they disagree, the stricter wins);
 * 3. capped at [INCOMPLETE_CAP] when the day's log is incomplete.
 * Without an AI rating (analyses from before it was stored) the calorie tier is used alone.
 */
object TierCalculator {

    /** Inclusive upper bound of each tier's midpoint calories, best tier first. */
    private val BANDS = listOf(
        1_650 to 5,  // Bro is Kohli   (≤ 1,650)
        2_100 to 4,  // Fair play      (1,651–2,100)
        2_600 to 3,  // Fine init      (2,101–2,600)
        3_300 to 2,  // Black hole     (2,601–3,300)
    )
    private const val WORST = 1     // Fatass whale (> 3,300 kcal)

    /** Highest rating a day can get while any meal slot is neither logged nor skipped. */
    const val INCOMPLETE_CAP = 3

    fun midpoint(minCalories: Int, maxCalories: Int): Int = (minCalories + maxCalories) / 2

    /** Rating 1–5 from the calorie midpoint alone. */
    fun ratingForMidpoint(midCalories: Int): Int =
        BANDS.firstOrNull { (upper, _) -> midCalories <= upper }?.second ?: WORST

    /** Average of the calorie tier and the AI's rating, rounded down (integer division). */
    fun blend(calorieTier: Int, aiRating: Int?): Int =
        if (aiRating == null) calorieTier else (calorieTier + aiRating.coerceIn(1, 5)) / 2

    /** The final daily rating: blended tier, capped at [INCOMPLETE_CAP] for incomplete logs. */
    fun rating(minCalories: Int, maxCalories: Int, aiRating: Int?, logsComplete: Boolean): Int {
        val tier = blend(ratingForMidpoint(midpoint(minCalories, maxCalories)), aiRating)
        return if (logsComplete) tier else minOf(tier, INCOMPLETE_CAP)
    }

    fun category(rating: Int): DailyCategory = requireNotNull(DailyCategory.fromRating(rating))
}
