package com.vishnu.kohliprotocol.data.local.entity

/** The five daily categories. Numerical and linear: 1 is the worst, 5 is the best. */
enum class DailyCategory(val rating: Int, val label: String) {
    FATASS_WHALE(1, "Fatass whale"),
    BLACK_HOLE(2, "Black hole"),
    FINE_INIT(3, "Fine init"),
    FAIR_PLAY(4, "Fair play"),
    BRO_IS_KOHLI(5, "Bro is Kohli");

    companion object {
        const val MIN_RATING = 1
        const val MAX_RATING = 5

        fun fromRating(rating: Int): DailyCategory? = entries.firstOrNull { it.rating == rating }

        /** Case-insensitive, since AI providers return e.g. "fair play". */
        fun fromLabel(label: String): DailyCategory? =
            entries.firstOrNull { it.label.equals(label.trim(), ignoreCase = true) }
    }
}
