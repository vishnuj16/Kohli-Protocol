package com.vishnu.kohliprotocol.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vishnu.kohliprotocol.ui.theme.KohliColors
import com.vishnu.kohliprotocol.ui.theme.KohliType

// ---------------------------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------------------------

val CardShape: Shape = RoundedCornerShape(16.dp)
val ButtonShape: Shape = RoundedCornerShape(14.dp)

/**
 * The standard card: level-1 surface, 16dp corners, 1dp outline. Clickable cards get
 * [bounceClick] feedback.
 */
@Composable
fun KohliCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    borderColor: Color = KohliColors.Outline,
    containerColor: Color = KohliColors.Surface,
    shape: Shape = CardShape,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    verticalSpacing: Dp = 10.dp,
    /** Optional gradient drawn over [containerColor]. */
    background: Brush? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactive = if (onClick != null) {
        Modifier.bounceClick().clip(shape).clickable(onClick = onClick)
    } else {
        Modifier
    }
    Surface(
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.fillMaxWidth().then(interactive),
    ) {
        Column(
            Modifier
                .then(if (background != null) Modifier.background(background) else Modifier)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            content = content,
        )
    }
}

/** A card with a 6dp accent bar down its left edge ("performance banner"). */
@Composable
fun AccentBanner(
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // A faint wash of the accent colour so banners carry their tier/status at a glance.
    KohliCard(
        modifier = modifier,
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        background = Brush.horizontalGradient(listOf(accent.copy(alpha = 0.10f), Color.Transparent)),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(6.dp).fillMaxHeight().background(accent))
            Column(
                Modifier.weight(1f).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

/** A titled card: small all-caps eyebrow, then content. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    borderColor: Color = KohliColors.Outline,
    content: @Composable ColumnScope.() -> Unit,
) {
    KohliCard(modifier = modifier, borderColor = borderColor) {
        Eyebrow(title)
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// Text
// ---------------------------------------------------------------------------------------------

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = KohliColors.Muted) {
    Text(text.uppercase(), style = KohliType.Eyebrow, color = color, modifier = modifier)
}

/** A section heading between cards on a page. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Eyebrow(text, modifier = modifier.padding(top = 12.dp, bottom = 2.dp, start = 4.dp))
}

@Composable
fun MutedText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = KohliColors.Muted, modifier = modifier)
}

/** Tiny all-caps pill on a tint of its own colour. */
@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = KohliType.Pill,
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

enum class MessageTone { INFO, SUCCESS, ERROR }

/** Inline feedback banner (replaces bare coloured text). */
@Composable
fun InlineMessage(text: String, modifier: Modifier = Modifier, tone: MessageTone = MessageTone.INFO) {
    val color = when (tone) {
        MessageTone.INFO -> KohliColors.Accent
        MessageTone.SUCCESS -> KohliColors.Logged
        MessageTone.ERROR -> KohliColors.Missing
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.10f))
            .height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(color))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = KohliColors.Text, modifier = Modifier.padding(12.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

/** Solid Kohli-gold call to action: 52dp, 14dp corners, black bold label. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    fillWidth: Boolean = true,
    containerColor: Color = KohliColors.Accent,
    contentColor: Color = KohliColors.OnAccent,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = KohliColors.SurfaceHigh,
            disabledContentColor = KohliColors.Muted,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .height(52.dp)
            .bounceClick(),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Outlined secondary action. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = KohliColors.Text,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        border = BorderStroke(1.dp, KohliColors.OutlineStrong),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor, disabledContentColor = KohliColors.Muted),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.height(46.dp).bounceClick(),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Low-emphasis text action. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = KohliColors.Accent,
) {
    TextButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = if (enabled) color else KohliColors.Muted)
    }
}

// ---------------------------------------------------------------------------------------------
// Selection
// ---------------------------------------------------------------------------------------------

/** Single-select chip row (filters, provider pickers). */
@Composable
fun <T> ChoiceChips(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val active = option == selected
            Text(
                label(option),
                style = MaterialTheme.typography.labelMedium,
                color = if (active) KohliColors.OnAccent else KohliColors.Text,
                modifier = Modifier
                    .bounceClick()
                    .clip(RoundedCornerShape(50))
                    .background(if (active) KohliColors.Accent else KohliColors.Surface)
                    .border(1.dp, if (active) KohliColors.Accent else KohliColors.Outline, RoundedCornerShape(50))
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Page structure
// ---------------------------------------------------------------------------------------------

/** Minimal borderless top bar on the page background. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KohliTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title.uppercase(), style = KohliType.Brand) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = KohliColors.Text,
            navigationIconContentColor = KohliColors.Text,
            actionIconContentColor = KohliColors.Text,
        ),
    )
}

/** Standard sub-screen: top bar with back, then [content] with the scaffold padding. */
@Composable
fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = { KohliTopBar(title, onBack = onBack) },
            floatingActionButton = floatingActionButton,
            content = content,
        )
    }
}

/** Scrolling page body with standard gutters and spacing. */
@Composable
fun PageColumn(
    padding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** Full-screen "locked" state used by the fingerprint gates. */
@Composable
fun LockedPanel(
    title: String,
    subtitle: String,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KohliColors.Background)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(KohliColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            Text("🔒", fontSize = 40.sp)
        }
        Text(title.uppercase(), style = KohliType.Brand, textAlign = TextAlign.Center)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = KohliColors.Muted, textAlign = TextAlign.Center)
        PrimaryButton("Unlock", onClick = onUnlock, fillWidth = false)
    }
}
