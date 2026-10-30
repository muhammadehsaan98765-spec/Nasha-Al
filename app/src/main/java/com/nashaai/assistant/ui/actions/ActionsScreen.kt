package com.nashaai.assistant.ui.actions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nashaai.assistant.AppContainer
import com.nashaai.assistant.accessibility.AccessibilityStatusFlow
import com.nashaai.assistant.accessibility.AccessibilityUtils
import com.nashaai.assistant.data.actions.ActionLogEntry
import com.nashaai.assistant.data.settings.Shortcut
import com.nashaai.assistant.domain.actions.ActionExecutionStatus
import com.nashaai.assistant.domain.actions.ActionPreview
import com.nashaai.assistant.domain.actions.ActionResult
import com.nashaai.assistant.domain.actions.ActionRisk
import com.nashaai.assistant.domain.actions.ActionType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * BLOCK 15 FIX SUMMARY (this file):
 *   The Block 14 version defined a `clickableNoRipple` helper that called
 *   `androidx.compose.foundation.clickable(...)` without a Modifier
 *   receiver. `clickable` is an extension on `Modifier`, so that call is a
 *   compile error. Fixed by using `Modifier.clickable(onClick = ...)`
 *   directly in `ShortcutChip` and removing the invalid helper.
 *
 * All other behaviour from Block 14 is preserved:
 *   - installedShortcutPackages via produceState
 *   - ShortcutChip with "not installed" label for uninstalled apps
 *   - Shortcut removal via onRemove
 *   - Preview + confirmation flow
 */

@Composable
fun ActionsRoute(container: AppContainer, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var commandInput by rememberSaveable { mutableStateOf("") }
    var preview by remember { mutableStateOf<ActionPreview?>(null) }
    var lastResult by remember { mutableStateOf<ActionResult?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }

    val actionLog by container.actionLogRepository.log
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val shortcuts by container.shortcutRepository.shortcuts
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val accessibilityFlow = remember(context) {
        AccessibilityStatusFlow(context.applicationContext)
    }
    val accessibilityEnabled by accessibilityFlow.isEnabled
        .collectAsStateWithLifecycle(initialValue = false)

    val installedShortcutPackages by produceState<Set<String>>(
        initialValue = emptySet(),
        key1 = shortcuts
    ) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            shortcuts.mapNotNull { s ->
                try {
                    if (pm.getLaunchIntentForPackage(s.packageName) != null) {
                        s.packageName
                    } else null
                } catch (_: Exception) { null }
            }.toSet()
        }
    }

    ActionsScreen(
        commandInput = commandInput,
        onCommandChange = { commandInput = it; preview = null },
        preview = preview,
        lastResult = lastResult,
        shortcuts = shortcuts,
        installedShortcutPackages = installedShortcutPackages,
        onShortcutTap = { pkg ->
            commandInput = "/open $pkg"
            preview = container.actionEngine.preview(
                container.commandParser.parse("/open $pkg")
            )
            lastResult = null
        },
        onShortcutRemove = { pkg ->
            scope.launch { runCatching { container.shortcutRepository.remove(pkg) } }
        },
        onPreview = {
            val parsed = container.commandParser.parse(commandInput)
            preview = container.actionEngine.preview(parsed)
            lastResult = null
        },
        onQuickAction = { cmd ->
            commandInput = cmd
            preview = container.actionEngine.preview(container.commandParser.parse(cmd))
            lastResult = null
        },
        onConfirm = {
            val p = preview ?: return@ActionsScreen
            scope.launch {
                val result = container.actionEngine.execute(p.request, confirmed = true)
                lastResult = result; preview = null
            }
        },
        onCancelPreview = { preview = null; lastResult = null },
        actionLog = actionLog,
        accessibilityEnabled = accessibilityEnabled,
        onOpenAccessibilitySettings = {
            AccessibilityUtils.openAccessibilitySettings(context)
        },
        onOpenAppPicker = { showAppPicker = true },
        modifier = modifier
    )

    if (showAppPicker) {
        AppPickerDialog(
            onDismiss = { showAppPicker = false },
            onSelect = { pkg ->
                val label = try {
                    val pm = context.packageManager
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) { pkg }
                scope.launch {
                    runCatching { container.shortcutRepository.add(pkg, label) }
                    commandInput = "/open $pkg"
                }
                showAppPicker = false
            }
        )
    }
}

@Composable
fun ActionsScreen(
    commandInput: String,
    onCommandChange: (String) -> Unit,
    preview: ActionPreview?,
    lastResult: ActionResult?,
    shortcuts: List<Shortcut>,
    installedShortcutPackages: Set<String>,
    onShortcutTap: (String) -> Unit,
    onShortcutRemove: (String) -> Unit,
    onPreview: () -> Unit,
    onQuickAction: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancelPreview: () -> Unit,
    actionLog: List<ActionLogEntry>,
    accessibilityEnabled: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Actions", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold)
        Text("Ask Nasha AI to open apps, search the web, copy text, or save " +
            "notes. Nothing runs until you confirm the preview.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        if (!accessibilityEnabled) AccessibilityBanner(onOpenAccessibilitySettings)

        SectionLabel("Your app shortcuts")
        if (shortcuts.isEmpty()) {
            Text("No shortcuts yet. Tap \"Add app shortcut\" below.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                shortcuts.forEach { s ->
                    val isInstalled = s.packageName in installedShortcutPackages
                    ShortcutChip(
                        shortcut = s,
                        isInstalled = isInstalled,
                        onTap = { if (isInstalled) onShortcutTap(s.packageName) },
                        onRemove = { onShortcutRemove(s.packageName) }
                    )
                }
            }
        }
        OutlinedButton(onClick = onOpenAppPicker) { Text("Add app shortcut") }

        SectionLabel("Quick actions")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onQuickAction("/settings") }) { Text("Settings") }
            OutlinedButton(onClick = { onQuickAction("/notifications") }) { Text("Notifications") }
            OutlinedButton(onClick = { onQuickAction("/home") }) { Text("Home") }
            OutlinedButton(onClick = { onQuickAction("/back") }) { Text("Back") }
            OutlinedButton(onClick = { onQuickAction("/recents") }) { Text("Recents") }
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        SectionLabel("Command")
        OutlinedTextField(
            value = commandInput, onValueChange = onCommandChange,
            modifier = Modifier.fillMaxWidth(), label = { Text("Command") },
            placeholder = { Text("/open <url|package>    /search <query>    " +
                "/copy <text>    /note <title>|<body>    /message <text>    /read <text>") },
            maxLines = 4
        )
        Button(onClick = onPreview, enabled = commandInput.isNotBlank()) {
            Text("Preview")
        }

        preview?.let { p ->
            PreviewCard(p, accessibilityEnabled, onConfirm, onCancelPreview,
                onOpenAccessibilitySettings)
        }
        lastResult?.let { ResultCard(it) }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))

        SectionLabel("Recent actions")
        if (actionLog.isEmpty()) {
            Text("No actions logged yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            actionLog.take(20).forEach { ActionLogRow(it) }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Shortcut chip.
 *
 * BLOCK 15 FIX: uses `Modifier.clickable(onClick = onTap)` directly.
 * Uninstalled shortcuts: muted colors, "not installed" label, no tap
 * action — but the remove button is still available.
 */
@Composable
private fun ShortcutChip(
    shortcut: Shortcut,
    isInstalled: Boolean,
    onTap: () -> Unit,
    onRemove: () -> Unit
) {
    val containerColor = if (isInstalled) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surface
    val contentColor = if (isInstalled) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val label = if (isInstalled) shortcut.label
        else "${shortcut.label} (not installed)"

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = Modifier.semantics {
            contentDescription = if (isInstalled) {
                "Open ${shortcut.label}"
            } else {
                "${shortcut.label} is not installed. Remove it or install the app."
            }
        }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                modifier = if (isInstalled) {
                    Modifier.clickable(onClick = onTap)
                } else {
                    Modifier
                }
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Filled.Close,
                    contentDescription = "Remove shortcut for ${shortcut.label}",
                    modifier = Modifier.size(14.dp),
                    tint = contentColor)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun AccessibilityBanner(onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(12.dp)) {
            Text("Accessibility service is off",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.height(4.dp))
            Text("Home, Back, and Recents require accessibility. Other actions " +
                "(settings, search, copy) work without it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onOpen) { Text("Open Accessibility Settings") }
        }
    }
}

@Composable
private fun PreviewCard(
    preview: ActionPreview, accessibilityEnabled: Boolean,
    onConfirm: () -> Unit, onCancel: () -> Unit, onOpenAccessibility: () -> Unit
) {
    val risk = preview.risk
    val containerColor = when (risk) {
        ActionRisk.LOW -> MaterialTheme.colorScheme.primaryContainer
        ActionRisk.MEDIUM -> MaterialTheme.colorScheme.tertiaryContainer
        ActionRisk.HIGH -> MaterialTheme.colorScheme.errorContainer
        ActionRisk.BLOCKED -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onColor = when (risk) {
        ActionRisk.LOW -> MaterialTheme.colorScheme.onPrimaryContainer
        ActionRisk.MEDIUM -> MaterialTheme.colorScheme.onTertiaryContainer
        ActionRisk.HIGH -> MaterialTheme.colorScheme.onErrorContainer
        ActionRisk.BLOCKED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val riskLabel = when (risk) {
        ActionRisk.LOW -> "Low risk"
        ActionRisk.MEDIUM -> "Medium risk"
        ActionRisk.HIGH -> "High risk"
        ActionRisk.BLOCKED -> "Blocked"
    }
    val needsAccessibility = preview.type in listOf(
        ActionType.ACCESSIBILITY_HOME,
        ActionType.ACCESSIBILITY_BACK,
        ActionType.ACCESSIBILITY_RECENTS
    )
    val blockedByAccessibility = needsAccessibility && !accessibilityEnabled

    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Preview", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = onColor)
                Spacer(Modifier.width(8.dp))
                Text("• $riskLabel", style = MaterialTheme.typography.labelSmall,
                    color = onColor)
            }
            Spacer(Modifier.height(6.dp))
            Text(preview.humanSummary, style = MaterialTheme.typography.bodyMedium,
                color = onColor)
            preview.warning?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = onColor)
            }
            if (blockedByAccessibility) {
                Spacer(Modifier.height(6.dp))
                Text("Accessibility service is not enabled. This action will fail.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onOpenAccessibility) {
                    Text("Open Accessibility Settings")
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Spacer(Modifier.width(6.dp))
                Button(onClick = onConfirm, enabled = preview.executable) {
                    Text(when (risk) {
                        ActionRisk.LOW -> "Run"
                        ActionRisk.MEDIUM, ActionRisk.HIGH -> "Confirm"
                        ActionRisk.BLOCKED -> "Blocked"
                    })
                }
            }
        }
    }
}

@Composable
private fun ResultCard(result: ActionResult) {
    val (container, onContainer, label) = when (result.status) {
        ActionExecutionStatus.SUCCESS -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer, "Success")
        ActionExecutionStatus.FAILURE -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer, "Failed")
        ActionExecutionStatus.BLOCKED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant, "Blocked")
        ActionExecutionStatus.NOT_IMPLEMENTED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant, "Not implemented")
        ActionExecutionStatus.CANCELLED -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant, "Cancelled")
        ActionExecutionStatus.AWAITING_CONFIRMATION -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant, "Awaiting confirmation")
    }
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(12.dp)) {
            Text("$label: ${result.type.name}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, color = onContainer)
            if (result.detail.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(result.detail, style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace, color = onContainer)
            }
        }
    }
}

@Composable
private fun ActionLogRow(entry: ActionLogEntry) {
    val okColor = if (entry.success) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.error
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .semantics {
                contentDescription = "${entry.type} " +
                    (if (entry.success) "succeeded" else "failed") +
                    " at ${formatTimestamp(entry.timestamp)}"
            },
        verticalAlignment = Alignment.CenterVertically) {
        Text(if (entry.success) "✓" else "✗", color = okColor,
            fontWeight = FontWeight.Bold, modifier = Modifier.width(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(entry.type, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f))
        Text("${entry.risk} • ${formatTimestamp(entry.timestamp)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatTimestamp(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    return when {
        diff < 60_000L -> "just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        else -> SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(ts))
    }
}
