package com.vishnu.kohliprotocol.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.vishnu.kohliprotocol.R
import com.vishnu.kohliprotocol.ui.theme.BebasNeue
import com.vishnu.kohliprotocol.ui.theme.KohliColors
import com.vishnu.kohliprotocol.ui.theme.Poppins

/** Kohli gold → ember: the brand gradient used on the logo, CTAs and highlights. */
val GoldGradient: Brush = Brush.linearGradient(listOf(Color(0xFFFFD24A), Color(0xFFFFB000), Color(0xFFFF7A00)))

/**
 * The Kohli Protocol wordmark: the gradient "K" emblem, "KOHLI" in gold Bebas Neue, and
 * "PROTOCOL" in wide-tracked small caps underneath.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
fun KohliLogo(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2B2433), Color(0xFF141419))))
                .border(1.dp, KohliColors.Accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(painterResource(R.drawable.ic_kohli_mark), contentDescription = null, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "KOHLI",
                style = TextStyle(
                    brush = GoldGradient,
                    fontFamily = BebasNeue,
                    fontSize = 30.sp,
                    lineHeight = 28.sp,
                    letterSpacing = 2.5.sp,
                ),
            )
            Text(
                "PROTOCOL",
                style = TextStyle(
                    fontFamily = Poppins,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 8.5.sp,
                    lineHeight = 10.sp,
                    letterSpacing = 4.2.sp,
                    color = KohliColors.Muted,
                ),
            )
        }
    }
}

/**
 * App-wide backdrop: a vertical charcoal gradient with a soft amber glow top-left and a cool
 * violet glow on the right, so screens never read as flat two-colour.
 */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF15131B), KohliColors.Background, Color(0xFF0B0B0E))))
            .drawWithCache {
                // Gradients are built once per size, not on every frame.
                val amberCenter = Offset(size.width * 0.15f, size.height * 0.04f)
                val amberRadius = size.width * 0.9f
                val amber = Brush.radialGradient(
                    colors = listOf(KohliColors.Accent.copy(alpha = 0.16f), Color.Transparent),
                    center = amberCenter,
                    radius = amberRadius,
                )
                val violetCenter = Offset(size.width * 1.05f, size.height * 0.38f)
                val violetRadius = size.width * 0.8f
                val violet = Brush.radialGradient(
                    colors = listOf(Color(0xFF7C5CFF).copy(alpha = 0.10f), Color.Transparent),
                    center = violetCenter,
                    radius = violetRadius,
                )
                onDrawBehind {
                    drawCircle(brush = amber, radius = amberRadius, center = amberCenter)
                    drawCircle(brush = violet, radius = violetRadius, center = violetCenter)
                }
            },
        content = content,
    )
}
