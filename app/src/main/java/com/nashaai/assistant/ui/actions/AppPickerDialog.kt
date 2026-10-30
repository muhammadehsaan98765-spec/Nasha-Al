package com.nashaai.assistant.ui.actions

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Minimal data class for one installed launchable app. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?
)

/**
 * Modal dialog listing launchable apps via PackageManager.
 *
 * Visibility is governed by Android's package visibility rules. The
 * project's AndroidManifest.xml (Block 1) declares a <queries> intent for
 * ACTION_MAIN / CATEGORY_LAUNCHER, so this dialog can see every launcher
 * app on the device without QUERY_ALL_PACKAGES.
 *
 * Returns the chosen package name through [onSelect]. Duplicates (apps
 * with multiple launcher activities) are collapsed by package name.
 */
@Composable
fun AppPickerDialog(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }

    val apps by produceState<List<InstalledApp>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) {
            loadLaunchableApps(context.packageName, context.packageManager)
        }
    }

    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter {
            it.label.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 640.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "Choose an app",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search apps…") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                if (filtered.isEmpty()) {
                    Text(
                        text = if (apps.isEmpty()) {
                            "No launchable apps found. This device's package " +
                                "visibility rules may be restricting the list."
                        } else {
                            "No match"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 480.dp)
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            AppRow(app = app, onSelect = {
                                onSelect(app.packageName)
                                onDismiss()
                            })
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (app.icon != null) {
            androidx.compose.foundation.Image(
                bitmap = app.icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
        } else {
            Spacer(Modifier.size(28.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------------
// PackageManager loading
// ---------------------------------------------------------------------

private fun loadLaunchableApps(
    selfPackage: String,
    pm: PackageManager
): List<InstalledApp> {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolveInfos = try {
        pm.queryIntentActivities(intent, 0)
    } catch (_: Exception) {
        return emptyList()
    }

    // Collapse to one entry per package name (apps with multiple launcher
    // activities show up multiple times in queryIntentActivities).
    val seen = mutableSetOf<String>()
    val result = mutableListOf<InstalledApp>()
    for (ri in resolveInfos) {
        val pkg = ri.activityInfo?.packageName ?: continue
        if (pkg == selfPackage) continue
        if (!seen.add(pkg)) continue

        val label = try {
            ri.loadLabel(pm).toString()
        } catch (_: Exception) {
            pkg
        }
        val iconBitmap: ImageBitmap? = try {
            val d: Drawable = ri.loadIcon(pm)
            d.toBitmap(width = 96, height = 96).asImageBitmap()
        } catch (_: Exception) {
            null
        }
        result.add(
            InstalledApp(
                packageName = pkg,
                label = label.ifBlank { pkg },
                icon = iconBitmap
            )
        )
    }
    return result.sortedBy { it.label.lowercase() }
}
