package com.vishnu.kohliprotocol.ui.settings

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vishnu.kohliprotocol.KohliApplication
import com.vishnu.kohliprotocol.data.ai.AiProviderFactory
import com.vishnu.kohliprotocol.data.ai.AiProviderType
import com.vishnu.kohliprotocol.data.ai.GeminiProvider
import com.vishnu.kohliprotocol.data.ai.ModelResolver
import com.vishnu.kohliprotocol.data.preferences.AiConfigStore
import com.vishnu.kohliprotocol.data.preferences.LatestModel
import com.vishnu.kohliprotocol.data.preferences.StoredApiKey
import com.vishnu.kohliprotocol.security.SecureActivity
import com.vishnu.kohliprotocol.ui.components.ChoiceChips
import com.vishnu.kohliprotocol.ui.components.Eyebrow
import com.vishnu.kohliprotocol.ui.components.GhostButton
import com.vishnu.kohliprotocol.ui.components.InlineMessage
import com.vishnu.kohliprotocol.ui.components.KohliCard
import com.vishnu.kohliprotocol.ui.components.MutedText
import com.vishnu.kohliprotocol.ui.components.PageColumn
import com.vishnu.kohliprotocol.ui.components.PageScaffold
import com.vishnu.kohliprotocol.ui.components.PrimaryButton
import com.vishnu.kohliprotocol.ui.components.SecondaryButton
import com.vishnu.kohliprotocol.ui.components.SectionCard
import com.vishnu.kohliprotocol.ui.components.SectionLabel
import com.vishnu.kohliprotocol.ui.components.StatusPill
import com.vishnu.kohliprotocol.ui.components.bounceClick
import com.vishnu.kohliprotocol.ui.theme.KohliColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Manage saved API keys (one is the default) and the model each provider uses. */
class AiSettingsActivity : SecureActivity() {

    override val screenName = "AI settings"

    private val viewModel: AiSettingsViewModel by viewModels { AiSettingsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSecureContent { AiSettingsScreen(viewModel, onBack = ::finish) }
    }
}

data class ProviderModelState(
    val provider: AiProviderType,
    val override: String?,
    val latest: LatestModel?,
    val checking: Boolean,
    /** True when the model is looked up live (Claude); false for a fixed chain (Gemini). */
    val supportsLookup: Boolean,
)

data class AiSettingsState(
    val keys: List<StoredApiKey>,
    val defaultKeyId: String?,
    /** Providers that have at least one usable key (saved or compiled in). */
    val models: List<ProviderModelState>,
    val localKeyProviders: List<AiProviderType>,
)

class AiSettingsViewModel(
    private val config: AiConfigStore,
    private val resolver: ModelResolver,
    private val providers: AiProviderFactory,
) : ViewModel() {

    private val checking = MutableStateFlow<Set<AiProviderType>>(emptySet())
    private val localKeyProviders = AiProviderType.entries.filter { providers.localKey(it) != null }

    val state: StateFlow<AiSettingsState?> = combine(
        config.keys,
        config.defaultKeyId,
        combine(AiProviderType.entries.map { config.modelOverride(it) }) { it.toList() },
        combine(AiProviderType.entries.map { config.latestModel(it) }) { it.toList() },
        checking,
    ) { keys, defaultId, overrides, latest, checkingSet ->
        val usable = AiProviderType.entries.filter { type ->
            keys.any { it.provider == type } || type in localKeyProviders
        }
        AiSettingsState(
            keys = keys,
            defaultKeyId = defaultId,
            models = usable.map { type ->
                val i = AiProviderType.entries.indexOf(type)
                ProviderModelState(type, overrides[i], latest[i], type in checkingSet, resolver.supportsLookup(type))
            },
            localKeyProviders = localKeyProviders,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch { config.migrateLegacyKeys() }
    }

    /** Returns false (with a message) if the key can't be saved. */
    fun addKey(secret: String, label: String, provider: AiProviderType?, makeDefault: Boolean): Boolean {
        if (secret.isBlank()) {
            _message.value = "Paste a key first."
            return false
        }
        val type = provider ?: AiProviderType.detect(secret)
        if (type == null) {
            _message.value = "Couldn't tell which provider this key is for — pick one."
            return false
        }
        viewModelScope.launch {
            _message.value = when (val result = config.addKey(type, label, secret, makeDefault)) {
                is AiConfigStore.AddResult.Added -> "Saved “${result.key.label}”."
                is AiConfigStore.AddResult.Duplicate -> "That key is already saved as “${result.existing.label}”."
            }
        }
        return true
    }

    fun setDefault(id: String) {
        viewModelScope.launch { config.setDefault(id) }
    }

    fun deleteKey(key: StoredApiKey) {
        viewModelScope.launch {
            config.deleteKey(key.id)
            _message.value = "Deleted “${key.label}”."
        }
    }

    fun setOverride(type: AiProviderType, model: String) {
        viewModelScope.launch {
            config.setModelOverride(type, model)
            _message.value = if (model.isBlank()) "${type.label}: automatic (latest ${type.flagshipFamily})."
            else "${type.label}: pinned to ${model.trim()}."
        }
    }

    fun checkLatest(type: AiProviderType) {
        if (type in checking.value) return
        checking.update { it + type }
        viewModelScope.launch {
            val secret = config.secretFor(type) ?: providers.localKey(type)
            _message.value = if (secret == null) {
                "No ${type.label} key saved."
            } else {
                runCatching { resolver.refresh(type, secret) }
                    .fold(
                        onSuccess = { "Latest ${type.flagshipFamily}: $it" },
                        onFailure = { "Couldn't check ${type.label}: ${it.message}" },
                    )
            }
            checking.update { it - type }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as KohliApplication).container
                AiSettingsViewModel(container.aiConfig, container.modelResolver, container.aiProviderFactory)
            }
        }
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM")
private val checkedFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm")

private fun format(epochMillis: Long, formatter: DateTimeFormatter) =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)

@Composable
private fun AiSettingsScreen(viewModel: AiSettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<StoredApiKey?>(null) }

    PageScaffold(title = "AI settings", onBack = onBack) { padding ->
        val current = state ?: return@PageScaffold
        PageColumn(padding) {
            message?.let { InlineMessage(it) }

            SectionCard("API keys") {
                if (current.keys.isEmpty()) {
                    MutedText(
                        if (current.localKeyProviders.isNotEmpty()) {
                            "No keys saved. Using the ${current.localKeyProviders.first().label} key from local.properties (dev build)."
                        } else {
                            "No keys saved yet — analysis stays pending until you add one."
                        }
                    )
                }
                current.keys.forEach { key ->
                    KeyRow(
                        key = key,
                        isDefault = key.id == current.defaultKeyId,
                        onSelect = { viewModel.setDefault(key.id) },
                        onDelete = { pendingDelete = key },
                    )
                }
            }

            SectionCard("Add a key") {
                AddKeyForm(isFirstKey = current.keys.isEmpty(), onAdd = viewModel::addKey)
            }

            if (current.models.isNotEmpty()) {
                SectionLabel("Models")
                current.models.forEach { model ->
                    ModelCard(
                        state = model,
                        onSetOverride = { viewModel.setOverride(model.provider, it) },
                        onCheck = { viewModel.checkLatest(model.provider) },
                    )
                }
            }

            val defaultProvider = current.keys.firstOrNull { it.id == current.defaultKeyId }?.provider
                ?: current.localKeyProviders.firstOrNull()
            MutedText(
                (defaultProvider?.let { "Analysis uses the default key (${it.label}). " } ?: "") +
                    "When a day is analyzed, that day's food descriptions and photos are sent to that " +
                    "provider. Keys are encrypted on this device. Nothing else leaves the phone.",
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    pendingDelete?.let { key ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = KohliColors.SurfaceHigh,
            title = { Text("Delete key?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("“${key.label}” (…${key.hint}) will be removed from this device.", color = KohliColors.Muted) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteKey(key)
                    pendingDelete = null
                }) { Text("Delete", color = KohliColors.Missing) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel", color = KohliColors.Muted) } },
        )
    }
}

@Composable
private fun KeyRow(key: StoredApiKey, isDefault: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isDefault) KohliColors.Accent.copy(alpha = 0.06f) else KohliColors.Background,
        border = BorderStroke(1.dp, if (isDefault) KohliColors.Accent else KohliColors.Outline),
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(0.98f)
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = isDefault, onClick = onSelect),
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = isDefault,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = KohliColors.Accent),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(key.label, style = MaterialTheme.typography.titleSmall)
                    if (isDefault) {
                        Spacer(Modifier.width(8.dp))
                        StatusPill("Default", KohliColors.Accent)
                    }
                }
                Text(
                    "${key.provider.label} · …${key.hint} · added ${format(key.addedAt, dateFormatter)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = KohliColors.Muted,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete key", tint = KohliColors.Muted)
            }
        }
    }
}

@Composable
private fun AddKeyForm(
    isFirstKey: Boolean,
    onAdd: (secret: String, label: String, provider: AiProviderType?, makeDefault: Boolean) -> Boolean,
) {
    var secret by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var chosen by rememberSaveable { mutableStateOf<AiProviderType?>(null) }
    var makeDefault by rememberSaveable { mutableStateOf(false) }
    val detected = AiProviderType.detect(secret)
    val provider = chosen ?: detected

    OutlinedTextField(
        value = secret,
        onValueChange = { secret = it },
        label = { Text("API key") },
        supportingText = {
            Text(detected?.let { "Looks like a ${it.label} key." } ?: "Paste a Gemini (AIza…) or Claude (sk-ant-…) key.")
        },
        visualTransformation = PasswordVisualTransformation(),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = label,
        onValueChange = { label = it },
        label = { Text("Label (optional)") },
        placeholder = { Text("e.g. Personal Gemini") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Eyebrow("Provider")
    ChoiceChips(
        options = AiProviderType.entries,
        selected = provider,
        label = { it?.label.orEmpty() },
        onSelect = { chosen = it },
    )
    if (!isFirstKey) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = makeDefault,
                onCheckedChange = { makeDefault = it },
                colors = CheckboxDefaults.colors(checkedColor = KohliColors.Accent, checkmarkColor = KohliColors.OnAccent),
            )
            Text("Make this the default key", style = MaterialTheme.typography.bodyMedium)
        }
    }
    PrimaryButton(
        "SAVE KEY",
        onClick = {
            if (onAdd(secret, label, provider, makeDefault || isFirstKey)) {
                secret = ""
                label = ""
                chosen = null
                makeDefault = false
            }
        },
    )
}

@Composable
private fun ModelCard(state: ProviderModelState, onSetOverride: (String) -> Unit, onCheck: () -> Unit) {
    var override by rememberSaveable(state.provider, state.override) { mutableStateOf(state.override.orEmpty()) }

    KohliCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.provider.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            StatusPill(if (state.override != null) "Pinned" else "Automatic", KohliColors.Accent)
        }
        MutedText(
            when {
                !state.supportsLookup ->
                    GeminiProvider.modelChain(state.override).joinToString(
                        separator = " → ",
                        prefix = if (state.override != null) "Pinned first, then fallbacks on high demand: " else "Default, with fallbacks on high demand: ",
                    )
                state.override != null -> "Pinned: ${state.override}"
                state.latest != null ->
                    "Automatic — newest ${state.provider.flagshipFamily}: ${state.latest.model} " +
                        "(checked ${format(state.latest.checkedAt, checkedFormatter)})"
                else ->
                    "Automatic — newest ${state.provider.flagshipFamily}. Not checked yet; " +
                        "uses ${state.provider.fallbackModel} until the first check."
            }
        )
        OutlinedTextField(
            value = override,
            onValueChange = { override = it },
            label = { Text("Pin a model (blank = automatic)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton("Save model", onClick = { onSetOverride(override) })
            if (state.supportsLookup) {
                Spacer(Modifier.width(8.dp))
                GhostButton(if (state.checking) "Checking…" else "Check latest now", onClick = onCheck, enabled = !state.checking)
            }
        }
    }
}
