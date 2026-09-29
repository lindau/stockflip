package com.stockflip.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.stockflip.BuildConfig
import com.stockflip.MainViewModel
import com.stockflip.R
import com.stockflip.backup.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val MAX_IMPORT_BYTES = 1_048_576

/** Väntande import: JSON-texten och det tolkade innehållet som visas i bekräftelsedialogen. */
private class PendingImport(val json: String, val data: BackupManager.BackupData)

/**
 * Kopplar [SettingsScreen] mot [MainViewModel] (export/import), [UpdateFlow] och tema.
 * Hjälp och ändringslogg öppnas via [onOpenDocument] (asset-namn).
 */
@Composable
internal fun SettingsRoute(
    viewModel: MainViewModel,
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    updateFlow: UpdateFlow,
    snackbarHostState: SnackbarHostState,
    onOpenDocument: (asset: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val json = try {
                readBackupJson(context.contentResolver, uri)
            } catch (e: IllegalArgumentException) {
                snackbarHostState.showSnackbar(e.message ?: "Backupfilen kunde inte läsas")
                return@launch
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Backupfilen kunde inte läsas")
                return@launch
            }
            val data = try {
                BackupManager.importFromJson(json)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(
                    context.getString(R.string.import_error, BackupManager.importErrorMessage(e))
                )
                return@launch
            }
            pendingImport = PendingImport(json, data)
        }
    }

    SettingsScreen(
        themeMode = themeMode,
        versionName = BuildConfig.VERSION_NAME,
        busy = updateFlow.busy,
        onThemeChange = onThemeChange,
        onExport = { scope.launch { BackupManager.shareFile(context, viewModel.exportData()) } },
        onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
        onCheckUpdate = { scope.launch { updateFlow.checkManually() } },
        onOpenHelp = { onOpenDocument("manual.md") },
        onOpenChangelog = { onOpenDocument("changelog.md") },
        modifier = modifier,
    )

    pendingImport?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.import_confirm_message,
                        pending.data.watchItems.size,
                        pending.data.stockPairs.size,
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImport = null
                    scope.launch {
                        when (val result = viewModel.importData(pending.json)) {
                            is MainViewModel.ImportResult.Success -> {
                                viewModel.syncAfterImport()
                                snackbarHostState.showSnackbar(
                                    context.getString(R.string.import_success, result.watchCount, result.pairCount)
                                )
                            }
                            is MainViewModel.ImportResult.Error -> snackbarHostState.showSnackbar(
                                context.getString(R.string.import_error, result.message)
                            )
                        }
                    }
                }) { Text(stringResource(R.string.settings_importera)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.dialog_button_cancel)) }
            },
        )
    }
}

/** Läser en backupfil med storleksgräns 1 MB (kontrolleras både i förväg och under läsning). */
private suspend fun readBackupJson(resolver: ContentResolver, uri: Uri): String = withContext(Dispatchers.IO) {
    val tooBig = IllegalArgumentException("Backupfilen är för stor. Maxstorlek är 1 MB.")
    resolver.openAssetFileDescriptor(uri, "r")?.use { d -> if (d.length > MAX_IMPORT_BYTES) throw tooBig }
    val input = resolver.openInputStream(uri) ?: throw IllegalArgumentException("Backupfilen kunde inte läsas")
    input.use {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val n = it.read(buffer)
            if (n == -1) break
            total += n
            if (total > MAX_IMPORT_BYTES) throw tooBig
            out.write(buffer, 0, n)
        }
        out.toString(Charsets.UTF_8.name())
    }
}
