package com.vishnu.kohliprotocol

import android.app.Application
import com.vishnu.kohliprotocol.analysis.AnalysisScheduler
import com.vishnu.kohliprotocol.reminders.ReminderScheduler
import com.vishnu.kohliprotocol.security.SessionLock
import com.vishnu.kohliprotocol.weekly.WeeklyEvaluationWorker
import kotlinx.coroutines.launch
import java.time.LocalDate

class KohliApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(SessionLock)
        // The first launch fixes the weekday every evaluation week starts on.
        container.appScope.launch {
            container.preferences.ensureProtocolStartDate(LocalDate.now())
        }
        ReminderScheduler.scheduleAll(this)
        AnalysisScheduler.scheduleNightly(this)
        WeeklyEvaluationWorker.schedule(this)
    }
}
