package com.vishnu.kohliprotocol.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vishnu.kohliprotocol.R

/**
 * Bundled fonts (SIL Open Font License, see assets/licenses):
 * - Bebas Neue — tall, sporty display face for the logo, screen titles and big numbers.
 * - Poppins — friendly geometric sans for everything else.
 */
val BebasNeue = FontFamily(Font(R.font.bebas_neue, FontWeight.Normal))

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_extrabold, FontWeight.ExtraBold),
    Font(R.font.poppins_extrabold, FontWeight.Black),
)

val KohliTypography = Typography(
    displayLarge = TextStyle(fontFamily = BebasNeue, fontSize = 64.sp, lineHeight = 64.sp, letterSpacing = 1.sp),
    displayMedium = TextStyle(fontFamily = BebasNeue, fontSize = 52.sp, lineHeight = 54.sp, letterSpacing = 1.sp),
    displaySmall = TextStyle(fontFamily = BebasNeue, fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = 1.sp),
    headlineLarge = TextStyle(fontFamily = BebasNeue, fontSize = 38.sp, lineHeight = 40.sp, letterSpacing = 1.sp),
    headlineMedium = TextStyle(fontFamily = BebasNeue, fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = 1.sp),
    headlineSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.6.sp),
)

/** Named styles used across screens. */
object KohliType {
    /** Screen titles in top bars: Bebas, generously tracked. */
    val Brand = TextStyle(fontFamily = BebasNeue, fontSize = 26.sp, lineHeight = 28.sp, letterSpacing = 1.6.sp)

    /** Small all-caps section labels ("MEALS", "BIRYANI PARAMETER"). */
    val Eyebrow = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 1.4.sp)

    /** Big numbers (parameter, averages). */
    val Metric = TextStyle(fontFamily = BebasNeue, fontSize = 42.sp, lineHeight = 42.sp, letterSpacing = 0.5.sp)

    /** "Rating: 4 — Fair play". */
    val Rating = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp)

    /** Status pills and badges. */
    val Pill = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 0.8.sp)
}
