package com.vishnu.kohliprotocol.data.local.entity

/** The four primary daily meal slots. Every one must be logged or explicitly skipped. */
enum class MealType(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    EVENING("Evening / snacks"),
    DINNER("Dinner"),
}
