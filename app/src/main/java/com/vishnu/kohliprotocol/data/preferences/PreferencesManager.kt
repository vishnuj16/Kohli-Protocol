package com.vishnu.kohliprotocol.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.vishnu.kohliprotocol.data.restrictions.EmergencyOverrideState
import com.vishnu.kohliprotocol.data.restrictions.RestrictionCategory
import com.vishnu.kohliprotocol.data.restrictions.RestrictionLists
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId

/** The app's single preferences file, shared by [PreferencesManager] and [AiConfigStore]. */
internal val Context.kohliDataStore: DataStore<Preferences> by preferencesDataStore(name = "kohli_preferences")

/** Key-value active state. Rule enforcement (e.g. who may lower the parameter) lives in repositories. */
class PreferencesManager(context: Context) {

    private val dataStore = context.applicationContext.kohliDataStore

    private val preferences: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    val biryaniParameter: Flow<Float> =
        preferences.map { it[KEY_BIRYANI_PARAMETER] ?: DEFAULT_BIRYANI_PARAMETER }.distinctUntilChanged()

    val isGamesUnlocked: Flow<Boolean> =
        preferences.map { it[KEY_GAMES_UNLOCKED] ?: false }.distinctUntilChanged()

    /** Epoch millis of the last trusted-clock check, or null if none has happened yet. */
    val lastClockCheck: Flow<Long?> =
        preferences.map { it[KEY_LAST_CLOCK_CHECK] }.distinctUntilChanged()

    /**
     * Atomically sets the Biryani Parameter. A decrease is refused unless [allowDecrease].
     * Returns the previous value if the change was applied, or null if it was refused.
     */
    suspend fun changeBiryaniParameter(newValue: Float, allowDecrease: Boolean): Float? {
        var previous: Float? = null
        dataStore.edit { prefs ->
            val current = prefs[KEY_BIRYANI_PARAMETER] ?: DEFAULT_BIRYANI_PARAMETER
            if (newValue < current && !allowDecrease) return@edit
            previous = current
            prefs[KEY_BIRYANI_PARAMETER] = newValue
        }
        return previous
    }

    /** Returns the previous value. */
    suspend fun setGamesUnlocked(unlocked: Boolean): Boolean {
        var previous = false
        dataStore.edit { prefs ->
            previous = prefs[KEY_GAMES_UNLOCKED] ?: false
            prefs[KEY_GAMES_UNLOCKED] = unlocked
        }
        return previous
    }

    suspend fun setLastClockCheck(epochMillis: Long) {
        dataStore.edit { it[KEY_LAST_CLOCK_CHECK] = epochMillis }
    }

    // --- Weekly evaluation & games ----------------------------------------------------------------

    /**
     * The day the protocol started (the app's first launch). Every evaluation week starts on
     * this weekday: first launched on a Tuesday means weeks run Tuesday → Monday.
     */
    val protocolStartDate: Flow<LocalDate?> = preferences
        .map { prefs -> prefs[KEY_PROTOCOL_START]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }
        .distinctUntilChanged()

    /**
     * Returns the protocol start date, recording [today] and the initialization timestamp if
     * none is stored yet. Installs that predate the timestamp get it backfilled from their
     * existing start date, so their week anchor never moves on upgrade. Also clears preference
     * keys left behind by retired features.
     */
    suspend fun ensureProtocolStartDate(today: LocalDate): LocalDate {
        var start: LocalDate? = null
        dataStore.edit { prefs ->
            val stored = prefs[KEY_PROTOCOL_START]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val resolved = stored ?: today.also { prefs[KEY_PROTOCOL_START] = it.toString() }
            if (prefs[KEY_APP_INITIALIZED_AT] == null) {
                prefs[KEY_APP_INITIALIZED_AT] = if (stored == null) {
                    System.currentTimeMillis()
                } else {
                    resolved.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }
            }
            prefs.remove(LEGACY_GAMES_TEST_OVERRIDE)
            prefs.remove(LEGACY_GAMES_TEST_UNTIL)
            start = resolved
        }
        return checkNotNull(start)
    }

    /** When the app was first initialized (epoch millis), or null on a fresh install. */
    val appInitializationTimestamp: Flow<Long?> = preferences
        .map { it[KEY_APP_INITIALIZED_AT] }
        .distinctUntilChanged()

    // --- Restricted apps ---------------------------------------------------------------------------

    /** Effective lists: built-in defaults + user additions − Guardian-approved removals. */
    val restrictionLists: Flow<RestrictionLists> = preferences.map { prefs ->
        RestrictionLists(
            RestrictionCategory.entries.associateWith { category ->
                (category.defaults + (prefs[addedKey(category)] ?: emptySet())) - (prefs[removedKey(category)] ?: emptySet())
            }
        )
    }.distinctUntilChanged()

    /** Returns true if [packageName] was not already restricted in [category]. */
    suspend fun addRestrictedApp(category: RestrictionCategory, packageName: String): Boolean {
        var changed = false
        dataStore.edit { prefs ->
            val removed = prefs[removedKey(category)] ?: emptySet()
            val added = prefs[addedKey(category)] ?: emptySet()
            if (packageName in removed) {
                prefs[removedKey(category)] = removed - packageName
                changed = true
            }
            if (packageName !in category.defaults && packageName !in added) {
                prefs[addedKey(category)] = added + packageName
                changed = true
            }
        }
        return changed
    }

    /**
     * Stops restricting [packageName] in [category]. Only [com.vishnu.kohliprotocol.data.repository.EnforcementRepository]
     * calls this, and only with a Guardian Gate approval.
     */
    suspend fun removeRestrictedApp(category: RestrictionCategory, packageName: String) {
        dataStore.edit { prefs ->
            prefs[addedKey(category)] = (prefs[addedKey(category)] ?: emptySet()) - packageName
            if (packageName in category.defaults) {
                prefs[removedKey(category)] = (prefs[removedKey(category)] ?: emptySet()) + packageName
            }
        }
    }

    // --- Emergency override --------------------------------------------------------------------

    val emergencyOverride: Flow<EmergencyOverrideState?> = preferences.map { prefs ->
        val until = prefs[KEY_OVERRIDE_UNTIL] ?: return@map null
        EmergencyOverrideState(
            categories = (prefs[KEY_OVERRIDE_CATEGORIES] ?: emptySet())
                .mapNotNull { name -> RestrictionCategory.entries.firstOrNull { it.name == name } }
                .toSet(),
            untilEpochMillis = until,
            requestId = prefs[KEY_OVERRIDE_REQUEST].orEmpty(),
            reason = prefs[KEY_OVERRIDE_REASON].orEmpty(),
        )
    }.distinctUntilChanged()

    /** null clears it. */
    suspend fun setEmergencyOverride(state: EmergencyOverrideState?) {
        dataStore.edit {
            if (state == null) {
                it.remove(KEY_OVERRIDE_UNTIL)
                it.remove(KEY_OVERRIDE_CATEGORIES)
                it.remove(KEY_OVERRIDE_REQUEST)
                it.remove(KEY_OVERRIDE_REASON)
            } else {
                it[KEY_OVERRIDE_UNTIL] = state.untilEpochMillis
                it[KEY_OVERRIDE_CATEGORIES] = state.categories.map { c -> c.name }.toSet()
                it[KEY_OVERRIDE_REQUEST] = state.requestId
                it[KEY_OVERRIDE_REASON] = state.reason
            }
        }
    }

    // --- Analysis status -----------------------------------------------------------------------

    /** Human-readable reason the last analysis attempt failed, or null after a success. */
    val lastAnalysisError: Flow<String?> =
        preferences.map { it[KEY_LAST_ANALYSIS_ERROR] }.distinctUntilChanged()

    suspend fun setLastAnalysisError(message: String?) {
        dataStore.edit {
            if (message == null) { it.remove(KEY_LAST_ANALYSIS_ERROR) } else { it[KEY_LAST_ANALYSIS_ERROR] = message }
        }
    }

    companion object {
        const val DEFAULT_BIRYANI_PARAMETER = 3.5f

        private val KEY_LAST_ANALYSIS_ERROR = stringPreferencesKey("last_analysis_error")
        private val KEY_PROTOCOL_START = stringPreferencesKey("protocol_start_date")
        private val KEY_APP_INITIALIZED_AT = longPreferencesKey("app_initialization_timestamp")

        // Retired mock weekly evaluation; removed from existing installs on launch.
        private val LEGACY_GAMES_TEST_OVERRIDE = booleanPreferencesKey("games_test_override")
        private val LEGACY_GAMES_TEST_UNTIL = longPreferencesKey("games_test_override_until")
        private val KEY_OVERRIDE_UNTIL = longPreferencesKey("emergency_override_until")
        private val KEY_OVERRIDE_CATEGORIES = stringSetPreferencesKey("emergency_override_categories")
        private val KEY_OVERRIDE_REQUEST = stringPreferencesKey("emergency_override_request")
        private val KEY_OVERRIDE_REASON = stringPreferencesKey("emergency_override_reason")

        /** Games keep their Phase 5 key so previously added games carry over. */
        private fun addedKey(category: RestrictionCategory) = stringSetPreferencesKey(
            if (category == RestrictionCategory.GAMES) "custom_game_packages" else "restricted_added_${category.name}"
        )
        private fun removedKey(category: RestrictionCategory) = stringSetPreferencesKey("restricted_removed_${category.name}")

        private val KEY_BIRYANI_PARAMETER = floatPreferencesKey("biryani_parameter")
        private val KEY_GAMES_UNLOCKED = booleanPreferencesKey("is_games_unlocked")
        private val KEY_LAST_CLOCK_CHECK = longPreferencesKey("last_clock_check")
    }
}
