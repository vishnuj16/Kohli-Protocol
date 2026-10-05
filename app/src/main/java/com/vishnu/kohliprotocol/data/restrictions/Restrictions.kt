package com.vishnu.kohliprotocol.data.restrictions

/**
 * The kinds of restriction the enforcement service applies. Each has built-in defaults; the
 * user may add apps freely, but removing one (default or added) requires Guardian Gate.
 */
enum class RestrictionCategory(val label: String, val defaults: Set<String>) {
    FOOD_DELIVERY(
        "Food delivery (always blocked)",
        setOf(
            "in.swiggy.android",        // Swiggy
            "com.application.zomato",   // Zomato
            "com.zeptoconsumerapp",     // Zepto
            "com.grofers.customerapp",  // Blinkit
        ),
    ),
    NIGHT_PAYMENT(
        "Payments (blocked 21:00–09:00)",
        setOf("com.phonepe.app"),       // PhonePe
    ),
    GAMES(
        "Games (locked until a successful week)",
        setOf("com.chess", "jp.konami.pesam"),  // Chess.com, eFootball
    ),
}

/** The effective package list per category. */
data class RestrictionLists(val byCategory: Map<RestrictionCategory, Set<String>>) {
    operator fun get(category: RestrictionCategory): Set<String> = byCategory[category].orEmpty()

    companion object {
        /** Used until the stored lists have loaded (fail closed). */
        val DEFAULTS = RestrictionLists(RestrictionCategory.entries.associateWith { it.defaults })
    }
}

/** A Guardian-approved, temporary suspension of some restriction categories. */
data class EmergencyOverrideState(
    val categories: Set<RestrictionCategory>,
    val untilEpochMillis: Long,
    val requestId: String,
    val reason: String,
) {
    fun isActive(now: Long = System.currentTimeMillis()): Boolean = now < untilEpochMillis

    fun suspended(now: Long = System.currentTimeMillis()): Set<RestrictionCategory> =
        if (isActive(now)) categories else emptySet()
}
