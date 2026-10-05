package com.vishnu.kohliprotocol

import android.content.Context
import com.vishnu.kohliprotocol.analysis.DailyAnalysisManager
import com.vishnu.kohliprotocol.data.ai.AiProviderFactory
import com.vishnu.kohliprotocol.data.ai.ModelResolver
import com.vishnu.kohliprotocol.data.guardian.GuardianGateManager
import com.vishnu.kohliprotocol.data.guardian.GuardianMessenger
import com.vishnu.kohliprotocol.data.guardian.GuardianStore
import com.vishnu.kohliprotocol.data.local.KohliDatabase
import com.vishnu.kohliprotocol.data.preferences.AiConfigStore
import com.vishnu.kohliprotocol.data.preferences.PreferencesManager
import com.vishnu.kohliprotocol.data.reports.ReportEmailStore
import com.vishnu.kohliprotocol.data.reports.WeeklyReportManager
import com.vishnu.kohliprotocol.data.reports.WeeklyReportWorker
import com.vishnu.kohliprotocol.data.repository.AuditRepository
import com.vishnu.kohliprotocol.data.repository.EnforcementRepository
import com.vishnu.kohliprotocol.data.repository.FoodRepository
import com.vishnu.kohliprotocol.data.repository.MotivationRepository
import com.vishnu.kohliprotocol.data.storage.InternalStorageManager
import com.vishnu.kohliprotocol.security.BiometricSecurityManager
import com.vishnu.kohliprotocol.weekly.WeeklyEvaluationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-wide singletons, created lazily. Access via `(application as KohliApplication).container`. */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    /** For fire-and-forget work that must outlive a screen (e.g. audit writes). */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: KohliDatabase by lazy { KohliDatabase.build(appContext) }
    val preferences: PreferencesManager by lazy { PreferencesManager(appContext) }
    val storage: InternalStorageManager by lazy { InternalStorageManager(appContext) }

    val auditRepository: AuditRepository by lazy { AuditRepository(database.auditDao()) }
    val foodRepository: FoodRepository by lazy { FoodRepository(database, storage, auditRepository) }
    val enforcementRepository: EnforcementRepository by lazy {
        EnforcementRepository(database.weeklyReportDao(), preferences, auditRepository)
    }
    val motivationRepository: MotivationRepository by lazy {
        MotivationRepository(database.motivationDao(), storage)
    }

    val aiConfig: AiConfigStore by lazy { AiConfigStore(appContext) }
    val modelResolver: ModelResolver by lazy { ModelResolver(aiConfig) }
    val aiProviderFactory: AiProviderFactory by lazy { AiProviderFactory(aiConfig, modelResolver) }
    val analysisManager: DailyAnalysisManager by lazy {
        DailyAnalysisManager(foodRepository, aiProviderFactory, preferences)
    }

    val weeklyEvaluationManager: WeeklyEvaluationManager by lazy {
        WeeklyEvaluationManager(foodRepository, enforcementRepository, preferences) {
            WeeklyReportWorker.enqueue(appContext)
        }
    }

    val reportEmailStore: ReportEmailStore by lazy { ReportEmailStore(appContext) }
    val weeklyReportManager: WeeklyReportManager by lazy {
        WeeklyReportManager(
            appContext, foodRepository, enforcementRepository, aiProviderFactory,
            reportEmailStore, preferences, auditRepository,
        )
    }

    val guardianStore: GuardianStore by lazy { GuardianStore(appContext) }
    val guardianGate: GuardianGateManager by lazy {
        GuardianGateManager(guardianStore, GuardianMessenger(appContext, guardianStore), enforcementRepository, auditRepository)
    }
    val biometric: BiometricSecurityManager by lazy { BiometricSecurityManager(auditRepository, appScope) }
}
