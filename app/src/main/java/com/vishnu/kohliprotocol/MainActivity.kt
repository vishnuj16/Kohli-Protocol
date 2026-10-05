package com.vishnu.kohliprotocol

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vishnu.kohliprotocol.analysis.AnalysisScheduler
import com.vishnu.kohliprotocol.enforcement.EnforcementForegroundService
import com.vishnu.kohliprotocol.security.SecureActivity
import com.vishnu.kohliprotocol.ui.audit.AuditLogActivity
import androidx.compose.ui.graphics.Color
import com.vishnu.kohliprotocol.ui.components.AppBackground
import com.vishnu.kohliprotocol.ui.components.KohliIcons
import com.vishnu.kohliprotocol.ui.components.KohliLogo
import com.vishnu.kohliprotocol.ui.components.LockedPanel
import com.vishnu.kohliprotocol.ui.components.bounceClick
import com.vishnu.kohliprotocol.ui.dashboard.DashboardTab
import com.vishnu.kohliprotocol.ui.dashboard.DashboardViewModel
import com.vishnu.kohliprotocol.ui.discipline.DisciplineTab
import com.vishnu.kohliprotocol.ui.discipline.DisciplineViewModel
import com.vishnu.kohliprotocol.ui.discipline.ReportFiles
import com.vishnu.kohliprotocol.ui.guardian.GuardianGateActivity
import com.vishnu.kohliprotocol.ui.guardian.SecurityViewModel
import com.vishnu.kohliprotocol.ui.guardian.SettingsTab
import com.vishnu.kohliprotocol.ui.motivation.MotivationTab
import com.vishnu.kohliprotocol.ui.motivation.MotivationViewModel
import com.vishnu.kohliprotocol.ui.review.DailyReviewActivity
import com.vishnu.kohliprotocol.ui.settings.AiSettingsActivity
import com.vishnu.kohliprotocol.ui.setup.SetupActivity
import com.vishnu.kohliprotocol.ui.theme.KohliColors
import com.vishnu.kohliprotocol.ui.theme.KohliType
import com.vishnu.kohliprotocol.weekly.WeeklyEvaluationWorker

enum class AppTab(val label: String) { DASHBOARD("Dashboard"), MOTIVATION("Motivation"), DISCIPLINE("Discipline"), SETTINGS("Settings") }

/** Launcher: the four-tab app shell (Dashboard, Motivation, Discipline, Settings). */
class MainActivity : SecureActivity() {

    override val screenName = "Food dashboard"

    private val dashboard: DashboardViewModel by viewModels { DashboardViewModel.Factory }
    private val motivation: MotivationViewModel by viewModels { MotivationViewModel.Factory }
    private val discipline: DisciplineViewModel by viewModels { DisciplineViewModel.Factory }
    private val security: SecurityViewModel by viewModels { SecurityViewModel.Factory }

    private var tab by mutableStateOf(AppTab.DASHBOARD)

    /** The Motivation tab needs its own fingerprint/PIN (valid for 5 minutes, see BiometricSecurityManager). */
    private var motivationUnlocked by mutableStateOf(false)

    /** The user cancelled the Motivation prompt: don't re-prompt on every resume until they ask. */
    private var motivationPromptDeclined = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSecureContent { AppShell() }
    }

    override fun onResume() {
        super.onResume()
        dashboard.refreshDate()
        EnforcementForegroundService.start(this)
        // Picks up yesterday (and older pending days) before the approval card is needed.
        AnalysisScheduler.enqueueCatchUp(this)
        WeeklyEvaluationWorker.runNow(this)
    }

    override fun onSessionLocked() {
        motivationUnlocked = false
    }

    override fun onSessionUnlocked() {
        // Coming back to the Motivation tab: the one session unlock also opens the gallery.
        if (tab == AppTab.MOTIVATION) {
            container.biometric.markMotivationAuthenticated()
            motivationUnlocked = true
        }
    }

    private fun selectTab(next: AppTab) {
        if (next == AppTab.MOTIVATION && tab != AppTab.MOTIVATION) motivationPromptDeclined = false
        tab = next
    }

    private fun unlockMotivation() = authenticateSection(
        reason = MOTIVATION_REASON,
        onDeclined = { motivationPromptDeclined = true },
    ) {
        container.biometric.markMotivationAuthenticated()
        motivationPromptDeclined = false
        motivationUnlocked = true
    }

    /**
     * Runs when the Motivation tab is shown and whenever the app resumes on it. Only acts while
     * Motivation is the *active* tab — the copy that is fading out during a tab switch never
     * prompts. Inside the 5-minute window it opens without asking.
     */
    private fun checkMotivationAccess() {
        if (tab != AppTab.MOTIVATION) return
        if (container.biometric.isMotivationSessionValid()) {
            motivationUnlocked = true
        } else {
            motivationUnlocked = false
            if (!motivationPromptDeclined) unlockMotivation()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun AppShell() {
        val dashboardState by dashboard.state.collectAsStateWithLifecycle()
        // The bar slides away while scrolling down and returns on scroll up, so it never sits
        // over content; it turns solid when content is underneath.
        val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
        LaunchedEffect(tab) {
            scrollBehavior.state.heightOffset = 0f
            scrollBehavior.state.contentOffset = 0f
        }

        BackHandler(enabled = tab != AppTab.DASHBOARD) { selectTab(AppTab.DASHBOARD) }

        AppBackground {
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { KohliLogo() },
                    scrollBehavior = scrollBehavior,
                    actions = {
                        dashboardState?.let {
                            BiryaniBadge(it.biryaniParameter, onClick = { selectTab(AppTab.DISCIPLINE) })
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = KohliColors.Background,
                        titleContentColor = KohliColors.Text,
                    ),
                )
            },
            bottomBar = { BottomBar(selected = tab, onSelect = ::selectTab) },
        ) { padding ->
            Crossfade(targetState = tab, animationSpec = tween(220), label = "tab") { current ->
                when (current) {
                    AppTab.DASHBOARD -> DashboardTab(
                        viewModel = dashboard,
                        contentPadding = padding,
                        onOpenAiSettings = { startActivity(Intent(this@MainActivity, AiSettingsActivity::class.java)) },
                        onOpenDiscipline = { selectTab(AppTab.DISCIPLINE) },
                        onOpenReview = { date -> startActivity(DailyReviewActivity.intent(this@MainActivity, date)) },
                    )
                    AppTab.MOTIVATION -> MotivationGate(padding)
                    AppTab.DISCIPLINE -> DisciplineTab(
                        viewModel = discipline,
                        contentPadding = padding,
                        onGate = { action -> startActivity(GuardianGateActivity.intent(this@MainActivity, action)) },
                        onOpenSettings = { selectTab(AppTab.SETTINGS) },
                        onViewPdf = { path -> ReportFiles.view(this@MainActivity, path) },
                        onSharePdf = { path -> ReportFiles.share(this@MainActivity, path) },
                    )
                    AppTab.SETTINGS -> SettingsTab(
                        viewModel = security,
                        contentPadding = padding,
                        onGate = { action -> startActivity(GuardianGateActivity.intent(this@MainActivity, action)) },
                        onOpenAudit = { startActivity(Intent(this@MainActivity, AuditLogActivity::class.java)) },
                        onOpenAiSettings = { startActivity(Intent(this@MainActivity, AiSettingsActivity::class.java)) },
                        onOpenSetup = { startActivity(Intent(this@MainActivity, SetupActivity::class.java)) },
                    )
                }
            }
        }
        }
    }

    /** Shows the gallery only after its own unlock; prompts once on entry, with a retry button. */
    @Composable
    private fun MotivationGate(padding: PaddingValues) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            // Adding the observer immediately replays ON_RESUME if the screen is already resumed.
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) checkMotivationAccess()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                // Leaving Motivation kills any pending prompt so nothing pops up mid-transition.
                container.biometric.cancelAuthentication(MOTIVATION_REASON)
            }
        }

        if (motivationUnlocked) {
            MotivationTab(motivation, padding)
        } else {
            Column(Modifier.padding(padding)) {
                LockedPanel(
                    title = "Motivation is private",
                    subtitle = "Your fingerprint or PIN is needed to open it. It stays open for 5 minutes.",
                    onUnlock = {
                        motivationPromptDeclined = false
                        unlockMotivation()
                    },
                )
            }
        }
    }

    private companion object {
        const val MOTIVATION_REASON = "Motivation gallery"
    }
}

private val AppTab.icon: ImageVector
    get() = when (this) {
        AppTab.DASHBOARD -> Icons.Filled.Home
        AppTab.MOTIVATION -> KohliIcons.AutoAwesome
        AppTab.DISCIPLINE -> KohliIcons.Shield
        AppTab.SETTINGS -> Icons.Filled.Settings
    }

/** The top-bar Biryani Parameter badge; tapping it jumps to Discipline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BiryaniBadge(value: Float, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = KohliColors.Surface,
        border = BorderStroke(1.dp, KohliColors.Outline),
        modifier = Modifier.padding(end = 12.dp).bounceClick(),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("BIRYANI PARAMETER", style = KohliType.Pill.copy(fontSize = 8.sp), color = KohliColors.Muted)
            Text(value.toString(), style = MaterialTheme.typography.titleLarge, color = KohliColors.Accent)
        }
    }
}

/** Translucent bottom navigation with a 1dp top border and an amber pill indicator. */
@Composable
private fun BottomBar(selected: AppTab, onSelect: (AppTab) -> Unit) {
    NavigationBar(
        containerColor = KohliColors.Surface.copy(alpha = 0.95f),
        tonalElevation = 0.dp,
        modifier = Modifier.drawBehind {
            drawLine(KohliColors.Outline, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
        },
    ) {
        AppTab.entries.forEach { item ->
            NavigationBarItem(
                selected = item == selected,
                onClick = { onSelect(item) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.3.sp)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = KohliColors.Accent,
                    selectedTextColor = KohliColors.Accent,
                    indicatorColor = KohliColors.Accent.copy(alpha = 0.14f),
                    unselectedIconColor = KohliColors.Muted,
                    unselectedTextColor = KohliColors.Muted,
                ),
                modifier = Modifier.bounceClick(0.92f),
            )
        }
    }
}
