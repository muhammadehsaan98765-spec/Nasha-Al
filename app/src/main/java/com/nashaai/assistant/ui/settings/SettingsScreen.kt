package com.nashaai.assistant.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nashaai.assistant.AppContainer
import com.nashaai.assistant.accessibility.AccessibilityStatusFlow
import com.nashaai.assistant.accessibility.AccessibilityUtils
import com.nashaai.assistant.data.settings.AiProviderType
import com.nashaai.assistant.data.settings.AppSettings
import com.nashaai.assistant.data.settings.SearchEngine
import com.nashaai.assistant.ui.about.AboutScreen
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------
// Route
// ---------------------------------------------------------------------

/**
 * Settings tab entry point.
 *
 * Holds an internal boolean for whether the About + Diagnostics screen
 * is displayed. When the user taps "About + Diagnostics", we swap in
 * [AboutScreen] while keeping the Settings tab active in the bottom bar
 * — a lightweight, dependency-free navigation pattern.
 */
@Composable
fun SettingsRoute(
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    val factory = remember(container) {
        viewModelFactory {
            initializer {
                SettingsViewModel(
                    settingsRepository = container.settingsRepository,
                    secureKeyStore = container.secureKeyStore,
                    chatRepository = container.chatRepository,
                    networkMonitor = container.networkMonitor
                )
            }
        }
    }
    val viewModel: SettingsViewModel = viewModel(factory = factory)

    var showAbout by rememberSaveable { mutableStateOf(false) }

    if (showAbout) {
        AboutScreen(
            container = container,
            onBack = { showAbout = false },
            modifier = modifier
        )
    } else {
        SettingsScreen(
            viewModel = viewModel,
            onOpenAbout = { showAbout = true },
            modifier = modifier
        )
    }
}

// ---------------------------------------------------------------------
// Screen
// ---------------------------------------------------------------------

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val networkOnline by viewModel.networkOnline.collectAsStateWithLifecycle()
    val hasAiKey by viewModel.hasAiKey.collectAsStateWithLifecycle()
    val hasBraveKey by viewModel.hasBraveKey.collectAsStateWithLifecycle()
    val testResult by viewModel.connectionTest.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val accessibilityFlow = remember(context) {
        AccessibilityStatusFlow(context.applicationContext)
    }
    val accessibilityEnabled by accessibilityFlow.isEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    val accessibilityConnected by accessibilityFlow.isConnected
        .collectAsStateWithLifecycle(initialValue = false)

    // Auto-dismiss transient messages.
    LaunchedEffect(message) {
        if (message != null) {
            delay(3500)
            viewModel.clearMessage()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )

        // ---- Transient message -------------------------------------
        if (message != null) {
            Surface(
                color = if (message!!.isError) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = message!!.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (message!!.isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // ---- Status chips ------------------------------------------
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (networkOnline) "Network: Online" else "Network: Offline",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "•",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (hasAiKey) "Provider: configured" else "Provider: not configured",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- Provider card -----------------------------------------
        ProviderSection(
            settings = settings,
            hasAiKey = hasAiKey,
            testResult = testResult,
            onSetProvider = viewModel::setProvider,
            onSaveKey = viewModel::saveApiKey,
            onDeleteKey = viewModel::deleteApiKey,
            onSaveConfig = viewModel::saveProviderConfig,
            onTest = viewModel::testConnection
        )

        // ---- Search card ------------------------------------------
        SearchSection(
            settings = settings,
            hasBraveKey = hasBraveKey,
            onSetEngine = viewModel::setSearchEngine,
            onSaveCustomUrl = viewModel::saveCustomSearchUrl,
            onSaveBraveKey = viewModel::saveBraveKey,
            onDeleteBraveKey = viewModel::deleteBraveKey,
            onSetResultLimit = viewModel::setResultLimit,
            onSetSafeSearch = viewModel::setSafeSearch,
            onSetTimeout = viewModel::setTimeoutSeconds
        )

        // ---- Language card ----------------------------------------
        LanguageSection(
            current = settings.language,
            onSet = viewModel::setLanguage
        )

        // ---- History + AI action suggestions ----------------------
        HistorySection(
            settings = settings,
            onSetSaveHistory = viewModel::setSaveHistory,
            onClearHistory = viewModel::clearHistory,
            onSetAiActionSuggestions = viewModel::setAiActionSuggestions
        )

        // ---- Accessibility card -----------------------------------
        AccessibilitySection(
            enabled = accessibilityEnabled,
            connected = accessibilityConnected,
            onOpenSettings = {
                AccessibilityUtils.openAccessibilitySettings(context)
            }
        )

        // ---- Privacy card -----------------------------------------
        PrivacySection()

        // ---- About + Diagnostics entry ----------------------------
        OutlinedButton(
            onClick = onOpenAbout,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("About Nasha AI + Diagnostics")
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ---------------------------------------------------------------------
// Sections
// ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderSection(
    settings: AppSettings,
    hasAiKey: Boolean,
    testResult: ConnectionTestResult,
    onSetProvider: (AiProviderType) -> Unit,
    onSaveKey: (String) -> Unit,
    onDeleteKey: () -> Unit,
    onSaveConfig: (baseUrl: String, model: String) -> Unit,
    onTest: (baseUrl: String, model: String) -> Unit
) {
    var providerExpanded by remember { mutableStateOf(false) }
    var apiKeyDraft by rememberSaveable { mutableStateOf("") }
    var apiKeyVisible by rememberSaveable { mutableStateOf(false) }

    var baseUrlDraft by rememberSaveable { mutableStateOf("") }
    var userEditedBaseUrl by rememberSaveable { mutableStateOf(false) }
    var modelDraft by rememberSaveable { mutableStateOf("") }
    var userEditedModel by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(settings.baseUrl) {
        if (!userEditedBaseUrl) baseUrlDraft = settings.baseUrl
    }
    LaunchedEffect(settings.model) {
        if (!userEditedModel) modelDraft = settings.model
    }

    SectionCard(title = "AI Provider") {

        // Provider dropdown
        ExposedDropdownMenuBox(
            expanded = providerExpanded,
            onExpandedChange = { providerExpanded = !providerExpanded }
        ) {
            OutlinedTextField(
                value = when (settings.providerType) {
                    AiProviderType.GEMINI -> "Gemini"
                    AiProviderType.OPENAI_COMPATIBLE -> "OpenAI-compatible"
                },
                onValueChange = {},
                readOnly = true,
                label = { Text("Provider") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = providerExpanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = providerExpanded,
                onDismissRequest = { providerExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Gemini") },
                    onClick = {
                        onSetProvider(AiProviderType.GEMINI)
                        providerExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("OpenAI-compatible") },
                    onClick = {
                        onSetProvider(AiProviderType.OPENAI_COMPATIBLE)
                        providerExpanded = false
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // API key field
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = apiKeyDraft,
                onValueChange = { apiKeyDraft = it },
                label = { Text("API key") },
                singleLine = true,
                visualTransformation = if (apiKeyVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                trailingIcon = {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(
                            imageVector = if (apiKeyVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (apiKeyVisible) {
                                "Hide API key"
                            } else {
                                "Show API key"
                            }
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .semantics {
                        contentDescription =
                            if (hasAiKey) "API key (already saved)" else "API key (not saved)"
                    }
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = if (hasAiKey) "Key status: Saved" else "Key status: Not configured",
            style = MaterialTheme.typography.labelSmall,
            color = if (hasAiKey) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onSaveKey(apiKeyDraft)
                    apiKeyDraft = ""
                },
                enabled = apiKeyDraft.isNotBlank()
            ) { Text("Save key") }
            OutlinedButton(
                onClick = {
                    apiKeyDraft = ""
                    onDeleteKey()
                },
                enabled = hasAiKey
            ) { Text("Delete key") }
        }

        Spacer(Modifier.height(6.dp))
        val keyLink = when (settings.providerType) {
            AiProviderType.GEMINI -> "https://aistudio.google.com/app/apikey"
            AiProviderType.OPENAI_COMPATIBLE -> "https://platform.openai.com/api-keys"
        }
        Text(
            text = "Get an API key: $keyLink",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable {
                // Deliberately non-executable here — the user can copy this
                // URL manually. We do not launch a browser from Settings
                // because that would interrupt the current form.
            }
        )

        Spacer(Modifier.height(12.dp))
        Text(
            text = "A key stored on a device cannot be guaranteed secret from a " +
                "rooted or compromised device.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Base URL + Model
        OutlinedTextField(
            value = baseUrlDraft,
            onValueChange = {
                baseUrlDraft = it
                userEditedBaseUrl = true
            },
            label = { Text("Base URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = modelDraft,
            onValueChange = {
                modelDraft = it
                userEditedModel = true
            },
            label = { Text("Model") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                onSaveConfig(baseUrlDraft, modelDraft)
                userEditedBaseUrl = false
                userEditedModel = false
            }) { Text("Save") }
            OutlinedButton(onClick = { onTest(baseUrlDraft, modelDraft) }) {
                Text("Test connection")
            }
        }

        // Test result
        Spacer(Modifier.height(8.dp))
        when (val r = testResult) {
            ConnectionTestResult.Idle -> Unit
            ConnectionTestResult.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Testing…", style = MaterialTheme.typography.bodySmall)
            }
            is ConnectionTestResult.Success -> Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "Connected. Reply: ${r.preview}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(8.dp)
                )
            }
            is ConnectionTestResult.Failure -> Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = r.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Note: switching providers requires re-saving a key. " +
                "OpenAI API billing is separate from ChatGPT subscriptions.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchSection(
    settings: AppSettings,
    hasBraveKey: Boolean,
    onSetEngine: (SearchEngine) -> Unit,
    onSaveCustomUrl: (String) -> Unit,
    onSaveBraveKey: (String) -> Unit,
    onDeleteBraveKey: () -> Unit,
    onSetResultLimit: (Int) -> Unit,
    onSetSafeSearch: (Boolean) -> Unit,
    onSetTimeout: (Long) -> Unit
) {
    var engineExpanded by remember { mutableStateOf(false) }
    var customUrlDraft by rememberSaveable { mutableStateOf("") }
    var userEditedCustomUrl by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(settings.customSearchUrl) {
        if (!userEditedCustomUrl) customUrlDraft = settings.customSearchUrl
    }

    var braveKeyDraft by rememberSaveable { mutableStateOf("") }
    var braveVisible by rememberSaveable { mutableStateOf(false) }

    var draftLimit by remember(settings.resultLimit) {
        mutableLongStateOf(settings.resultLimit.toLong())
    }
    var draftTimeout by remember(settings.requestTimeoutSeconds) {
        mutableLongStateOf(settings.requestTimeoutSeconds)
    }

    SectionCard(title = "AI & Search configuration") {

        ExposedDropdownMenuBox(
            expanded = engineExpanded,
            onExpandedChange = { engineExpanded = !engineExpanded }
        ) {
            OutlinedTextField(
                value = engineLabel(settings.searchEngine),
                onValueChange = {},
                readOnly = true,
                label = { Text("Search engine") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = engineExpanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = engineExpanded,
                onDismissRequest = { engineExpanded = false }
            ) {
                SearchEngine.entries.forEach { engine ->
                    DropdownMenuItem(
                        text = { Text(engineLabel(engine)) },
                        onClick = {
                            onSetEngine(engine)
                            engineExpanded = false
                        }
                    )
                }
            }
        }

        if (settings.searchEngine == SearchEngine.CUSTOM) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = customUrlDraft,
                onValueChange = {
                    customUrlDraft = it
                    userEditedCustomUrl = true
                },
                label = { Text("Custom search URL template (ends with = or ?q=)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Button(onClick = {
                onSaveCustomUrl(customUrlDraft)
                userEditedCustomUrl = false
            }) { Text("Save URL") }
        }

        if (settings.searchEngine == SearchEngine.BRAVE) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = braveKeyDraft,
                onValueChange = { braveKeyDraft = it },
                label = { Text("Brave Search API key") },
                singleLine = true,
                visualTransformation = if (braveVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { braveVisible = !braveVisible }) {
                        Icon(
                            imageVector = if (braveVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (braveVisible) {
                                "Hide Brave key"
                            } else {
                                "Show Brave key"
                            }
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = if (hasBraveKey) "Brave key: Saved" else "Brave key: Not configured",
                style = MaterialTheme.typography.labelSmall,
                color = if (hasBraveKey) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onSaveBraveKey(braveKeyDraft)
                        braveKeyDraft = ""
                    },
                    enabled = braveKeyDraft.isNotBlank()
                ) { Text("Save") }
                OutlinedButton(
                    onClick = {
                        braveKeyDraft = ""
                        onDeleteBraveKey()
                    },
                    enabled = hasBraveKey
                ) { Text("Delete") }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        // Result limit
        Text(
            text = "Result limit: ${draftLimit.toInt()}",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = draftLimit.toFloat(),
            onValueChange = { draftLimit = it.toLong() },
            onValueChangeFinished = { onSetResultLimit(draftLimit.toInt()) },
            valueRange = 1f..20f,
            steps = 18
        )

        // Timeout
        Text(
            text = "Request timeout: ${draftTimeout}s",
            style = MaterialTheme.typography.bodyMedium
        )
        Slider(
            value = draftTimeout.toFloat(),
            onValueChange = { draftTimeout = it.toLong() },
            onValueChangeFinished = { onSetTimeout(draftTimeout) },
            valueRange = 5f..300f,
            steps = 58
        )

        // Safe search
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Safe search", modifier = Modifier.weight(1f))
            Switch(
                checked = settings.safeSearch,
                onCheckedChange = onSetSafeSearch
            )
        }
    }
}

@Composable
private fun LanguageSection(current: String, onSet: (String) -> Unit) {
    SectionCard(title = "Language") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LanguageChip("English", "en", current, onSet)
            LanguageChip("اردو", "ur", current, onSet)
            LanguageChip("Roman Urdu", "ur-Latn", current, onSet)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "This changes the interface language. Chat replies always " +
                "follow the language you write in.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LanguageChip(
    label: String,
    tag: String,
    current: String,
    onSet: (String) -> Unit
) {
    val selected = current == tag
    if (selected) {
        Button(onClick = { onSet(tag) }) { Text(label) }
    } else {
        OutlinedButton(onClick = { onSet(tag) }) { Text(label) }
    }
}

@Composable
private fun HistorySection(
    settings: AppSettings,
    onSetSaveHistory: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onSetAiActionSuggestions: (Boolean) -> Unit
) {
    var confirmClear by remember { mutableStateOf(false) }

    SectionCard(title = "History & AI") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Save chat history", modifier = Modifier.weight(1f))
            Switch(
                checked = settings.saveHistory,
                onCheckedChange = onSetSaveHistory
            )
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { confirmClear = true }) {
            Text("Clear all chat history")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("AI action suggestions")
                Text(
                    text = "Coming soon — requires a future chat update.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.aiActionSuggestionsEnabled,
                onCheckedChange = onSetAiActionSuggestions,
                enabled = false
            )
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear chat history?") },
            text = { Text("This deletes every saved session. This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearHistory()
                    confirmClear = false
                }) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AccessibilitySection(
    enabled: Boolean,
    connected: Boolean,
    onOpenSettings: () -> Unit
) {
    SectionCard(title = "Accessibility") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("User enabled: ", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (enabled) "Yes" else "No",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Service connected: ", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = if (connected) "Yes" else "No",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (connected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Nasha AI uses accessibility ONLY for Home, Back, and Recents. " +
                "It cannot read screen content and never bypasses the lock screen.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onOpenSettings) {
            Text("Open Android Accessibility Settings")
        }
    }
}

@Composable
private fun PrivacySection() {
    SectionCard(title = "Privacy") {
        Text(
            text = "• API keys are encrypted on this device using Android Keystore.\n" +
                "• Chat text is sent only to the AI provider you configure.\n" +
                "• Nasha AI does not upload contacts, SMS, files, or screen content.\n" +
                "• Message sending is never automatic — you complete it in the " +
                "target app.\n" +
                "• Accessibility is optional and only for Home/Back/Recents.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// ---------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

private fun engineLabel(engine: SearchEngine): String = when (engine) {
    SearchEngine.GOOGLE -> "Google"
    SearchEngine.BING -> "Bing"
    SearchEngine.DUCKDUCKGO -> "DuckDuckGo"
    SearchEngine.BRAVE -> "Brave (API)"
    SearchEngine.CUSTOM -> "Custom URL"
}
