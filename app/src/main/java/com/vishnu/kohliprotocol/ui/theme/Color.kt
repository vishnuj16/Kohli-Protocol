package com.vishnu.kohliprotocol.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Kohli Protocol palette (dark only). Surfaces step up in lightness:
 * Background → Surface (cards, level 1) → SurfaceHigh (sheets/dialogs, level 2),
 * all outlined with [Outline].
 */
object KohliColors {
    // Surfaces
    val Background = Color(0xFF0D0D10)   // deep matte charcoal — window background
    val Surface = Color(0xFF16161C)      // lifted charcoal — cards (level 1)
    val SurfaceHigh = Color(0xFF202028)  // modal sheets / dialogs (level 2)
    val Outline = Color(0xFF262630)      // 1dp border on every card
    val OutlineStrong = Color(0xFF34343A) // dashed "missing" indicators, dividers on cards

    // Text
    val Text = Color(0xFFEDEDED)
    val Muted = Color(0xFF9A9AA0)

    // Brand
    val Accent = Color(0xFFFFB000)       // Kohli gold
    val OnAccent = Color(0xFF000000)

    // Rating tiers (1 worst → 5 best)
    val Tier5 = Color(0xFF10B981)        // Bro is Kohli — emerald
    val Tier4 = Color(0xFF34D399)        // Fair play — sage
    val Tier3 = Color(0xFFFBBF24)        // Fine init — butter yellow
    val Tier2 = Color(0xFFF97316)        // Black hole — burnt orange
    val Tier1 = Color(0xFFEF4444)        // Fatass whale — crimson

    // Status
    val Logged = Tier5                   // logged / success
    val Missing = Tier1                  // missing / error
    val Skipped = Color(0xFF6E6E76)      // deliberately skipped
    val Warning = Color(0xFFFF3D00)      // lock & warning badges — vivid coral

    /** Colour for a daily rating 1–5 (or a weekly average rounded to one). */
    fun tier(rating: Int): Color = when {
        rating >= 5 -> Tier5
        rating == 4 -> Tier4
        rating == 3 -> Tier3
        rating == 2 -> Tier2
        else -> Tier1
    }
}
