package com.vishnu.kohliprotocol.ui.dashboard

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vishnu.kohliprotocol.KohliApplication
import com.vishnu.kohliprotocol.analysis.DailyAnalysisManager
import com.vishnu.kohliprotocol.data.local.entity.DailyAnalysisEntity
import com.vishnu.kohliprotocol.data.local.entity.FoodEntryEntity
import com.vishnu.kohliprotocol.data.local.entity.MealType
import com.vishnu.kohliprotocol.data.local.entity.MealWithEntries
import com.vishnu.kohliprotocol.data.preferences.PreferencesManager
import com.vishnu.kohliprotocol.data.repository.EnforcementRepository
import com.vishnu.kohliprotocol.data.repository.GameAccessState
import com.vishnu.kohliprotocol.data.repository.FoodRepository
import com.vishnu.kohliprotocol.data.storage.InternalStorageManager
import com.vishnu.kohliprotocol.weekly.WeekProgress
import com.vishnu.kohliprotocol.weekly.WeeklyEvaluationManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

enum class SlotStatus { EMPTY, LOGGED, SKIPPED }

data class SlotState(
    val mealType: MealType,
    val status: SlotStatus,
    val entries: List<FoodEntryEntity>,
)

data class FeedItem(val entry: FoodEntryEntity, val label: String)

data class DashboardState(
    val date: LocalDate,
    val slots: List<SlotState>,
    val feed: List<FeedItem>,
    val biryaniParameter: Float,
) {
    val loggedCount: Int get() = slots.count { it.status != SlotStatus.EMPTY }
}

/** AI analysis state for today and for yesterday's approval card. */
data class AnalysisUiState(
    val today: LocalDate,
    val todayAnalysis: DailyAnalysisEntity?,
    val todayError: String?,
    val yesterday: LocalDate,
    val yesterdayAnalysis: DailyAnalysisEntity?,
    val yesterdayHasLogs: Boolean,
    val yesterdayError: String?,
)

/** The current calendar week and game status, for the dashboard's week card. */
data class WeekUiState(
    val progress: WeekProgress,
    val biryaniParameter: Float,
    val games: GameAccessState,
)

sealed interface EditorTarget {
    data class Slot(val slot: SlotState) : EditorTarget
    object AddFood : EditorTarget
    data class Existing(val item: FeedItem) : EditorTarget
}

data class EditorState(
    val target: EditorTarget,
    val description: String = "",
    val photoPath: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(
    private val food: FoodRepository,
    private val enforcement: EnforcementRepository,
    private val storage: InternalStorageManager,
    private val analysis: DailyAnalysisManager,
    preferences: PreferencesManager,
    private val weekly: WeeklyEvaluationManager,
) : ViewModel() {

    private val date = MutableStateFlow(LocalDate.now())

    val state: StateFlow<DashboardState?> = date
        .flatMapLatest { day ->
            combine(
                food.observeMeals(day),
                food.observeAllFood(day),
                enforcement.biryaniParameter,
            ) { meals, entries, parameter -> buildState(day, meals, entries, parameter) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val analysisState: StateFlow<AnalysisUiState?> = date
        .flatMapLatest { day ->
            val yesterday = day.minusDays(1)
            combine(
                food.observeAnalysis(day),
                food.observeAnalysis(yesterday),
                food.observeMeals(yesterday),
                food.observeAllFood(yesterday),
                preferences.lastAnalysisError,
            ) { todayAnalysis, yesterdayAnalysis, yesterdayMeals, yesterdayFood, lastError ->
                val (errorDate, errorMessage) = DailyAnalysisManager.parseLastError(lastError) ?: (null to null)
                AnalysisUiState(
                    today = day,
                    todayAnalysis = todayAnalysis,
                    todayError = errorMessage.takeIf { errorDate == day && todayAnalysis == null },
                    yesterday = yesterday,
                    yesterdayAnalysis = yesterdayAnalysis,
                    yesterdayHasLogs = yesterdayMeals.any { it.isLogged } || yesterdayFood.isNotEmpty(),
                    yesterdayError = errorMessage.takeIf { errorDate == yesterday },
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val weekState: StateFlow<WeekUiState?> = combine(date, weekly.protocolStart) { day, start ->
        // Before the first launch has recorded the start date, today is the anchor.
        day to (start ?: day)
    }
        .flatMapLatest { (day, protocolStart) ->
            val weekStart = WeeklyEvaluationManager.weekStart(day, protocolStart)
            val weekEnd = weekStart.plusDays(6)
            combine(
                food.observeMealsRange(weekStart, weekEnd),
                food.observeAnalyses(weekStart, weekEnd),
                enforcement.biryaniParameter,
                enforcement.gameAccess,
            ) { meals, analyses, parameter, games ->
                WeekUiState(weekly.progress(day, protocolStart, meals, analyses), parameter, games)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _analyzing = MutableStateFlow<Set<LocalDate>>(emptySet())

    /** Days with an analysis request in flight from this screen. */
    val analyzing: StateFlow<Set<LocalDate>> = _analyzing.asStateFlow()

    private val _editor = MutableStateFlow<EditorState?>(null)
    val editor: StateFlow<EditorState?> = _editor.asStateFlow()

    /** Photos created while the editor is open and not yet saved; deleted if abandoned. */
    private val sessionPhotos = mutableSetOf<String>()

    /** Rolls the dashboard over to the new day after midnight. */
    fun refreshDate() {
        date.value = LocalDate.now()
    }

    // --- AI analysis -------------------------------------------------------------------------

    fun analyzeNow(day: LocalDate) = runAnalysis(day) { analysis.analyze(day) }

    fun acceptAnalysis(day: LocalDate) {
        viewModelScope.launch {
            food.acceptAnalysis(day)
            // Accepting the last day's rating may be what completes the previous week.
            weekly.evaluatePendingWeeks()
        }
    }

    /** "Doesn't look right": reject, then re-send the original log with the optional comment. */
    fun rejectAnalysis(day: LocalDate, comment: String) =
        runAnalysis(day) { analysis.rejectAndReanalyze(day, comment) }

    private fun runAnalysis(day: LocalDate, block: suspend () -> DailyAnalysisManager.Outcome) {
        if (day in _analyzing.value) return
        _analyzing.update { it + day }
        viewModelScope.launch {
            try {
                block()
            } finally {
                _analyzing.update { it - day }
            }
        }
    }

    // --- Editor ------------------------------------------------------------------------------

    fun openSlot(slot: SlotState) {
        _editor.value = EditorState(EditorTarget.Slot(slot))
    }

    fun openAddFood() {
        _editor.value = EditorState(EditorTarget.AddFood)
    }

    fun openEntry(item: FeedItem) {
        _editor.value = EditorState(
            target = EditorTarget.Existing(item),
            description = item.entry.description,
            photoPath = item.entry.photoPath,
        )
    }

    fun onDescriptionChange(text: String) = updateEditor { copy(description = text, error = null) }

    /** A private file for the camera to write into. */
    fun newCaptureFile(): File =
        storage.newCaptureTarget(InternalStorageManager.Directory.MEAL_PHOTOS)
            .also { sessionPhotos += it.absolutePath }

    fun captureUri(file: File): Uri = storage.contentUriFor(file)

    fun onPhotoCaptured(path: String, success: Boolean) {
        if (success && File(path).length() > 0) {
            // Camera files are several MB; shrink before showing so the preview is the stored file.
            updateEditor { copy(busy = true, error = null) }
            viewModelScope.launch {
                storage.optimizeInPlace(path)
                updateEditor { copy(photoPath = path, busy = false) }
            }
        } else {
            discard(path)
        }
    }

    fun onGalleryPicked(uri: Uri) {
        updateEditor { copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { storage.importFrom(uri, InternalStorageManager.Directory.MEAL_PHOTOS) }
                .onSuccess { file ->
                    sessionPhotos += file.absolutePath
                    updateEditor { copy(photoPath = file.absolutePath, busy = false) }
                }
                .onFailure { updateEditor { copy(busy = false, error = "Could not import photo") } }
        }
    }

    fun removePhoto() = updateEditor { copy(photoPath = null) }

    fun save() {
        val current = _editor.value ?: return
        if (current.description.isBlank()) {
            updateEditor { copy(error = "Describe what you ate") }
            return
        }
        runEditorAction(keepPhoto = current.photoPath) {
            when (val target = current.target) {
                is EditorTarget.Slot ->
                    food.logMealFood(date.value, target.slot.mealType, current.description, current.photoPath)
                EditorTarget.AddFood ->
                    food.addArbitraryFood(current.description, current.photoPath)
                is EditorTarget.Existing ->
                    food.editFoodEntry(target.item.entry.id, current.description, current.photoPath)
            }
        }
    }

    fun markSkipped() {
        val target = _editor.value?.target as? EditorTarget.Slot ?: return
        runEditorAction(keepPhoto = null) { food.markSkipped(date.value, target.slot.mealType) }
    }

    fun delete() {
        val target = _editor.value?.target as? EditorTarget.Existing ?: return
        runEditorAction(keepPhoto = null) { food.deleteFoodEntry(target.item.entry.id) }
    }

    fun dismissEditor() = closeEditor(keepPhoto = null)

    private fun runEditorAction(keepPhoto: String?, action: suspend () -> Unit) {
        if (_editor.value?.busy != false) return
        updateEditor { copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { closeEditor(keepPhoto) }
                .onFailure { e -> updateEditor { copy(busy = false, error = e.message ?: "Failed") } }
        }
    }

    private fun closeEditor(keepPhoto: String?) {
        val abandoned = sessionPhotos.filter { it != keepPhoto }
        sessionPhotos.clear()
        _editor.value = null
        abandoned.forEach(::discard)
    }

    private fun discard(path: String) {
        sessionPhotos -= path
        viewModelScope.launch { runCatching { storage.delete(path) } }
    }

    private inline fun updateEditor(crossinline transform: EditorState.() -> EditorState) {
        _editor.update { it?.transform() }
    }

    override fun onCleared() {
        // viewModelScope is already cancelled here, so delete synchronously.
        sessionPhotos.forEach { runCatching { storage.fileFor(it).delete() } }
        sessionPhotos.clear()
    }

    // --- State -------------------------------------------------------------------------------

    private fun buildState(
        day: LocalDate,
        meals: List<MealWithEntries>,
        entries: List<FoodEntryEntity>,
        parameter: Float,
    ): DashboardState {
        val byType = meals.associateBy { it.meal.mealType }
        val slots = MealType.entries.map { type ->
            val meal = byType[type]
            val slotEntries = meal?.entries.orEmpty().sortedBy { it.timestamp }
            val status = when {
                meal?.meal?.isSkipped == true -> SlotStatus.SKIPPED
                slotEntries.isNotEmpty() -> SlotStatus.LOGGED
                else -> SlotStatus.EMPTY
            }
            SlotState(type, status, slotEntries)
        }
        val typeByMealId = meals.associate { it.meal.id to it.meal.mealType }
        val feed = entries.map { entry ->
            FeedItem(entry, entry.mealId?.let(typeByMealId::get)?.label ?: "+ Food")
        }
        return DashboardState(day, slots, feed, parameter)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as KohliApplication).container
                DashboardViewModel(
                    container.foodRepository,
                    container.enforcementRepository,
                    container.storage,
                    container.analysisManager,
                    container.preferences,
                    container.weeklyEvaluationManager,
                )
            }
        }
    }
}
