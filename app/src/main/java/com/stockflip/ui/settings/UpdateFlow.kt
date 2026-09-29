package com.stockflip.ui.settings

import com.stockflip.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.stockflip.ApkSizeMismatchException
import com.stockflip.AppUpdateChecker
import com.stockflip.AppUpdateInstaller
import com.stockflip.AppUpdateSettings
import com.stockflip.BuildConfig
import com.stockflip.UpdateCheckResult
import com.stockflip.UpdateReleaseInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.io.File

/**
 * Uppdateringsflödet (kontroll, dialog, nedladdning, installation) för Compose-skalet.
 * Samma steg som i `MainActivity`; [installer] måste vara ett aktivitetsfält eftersom den
 * registrerar en `ActivityResult`-launcher.
 */
class UpdateFlow(private val installer: AppUpdateInstaller) {
    /** Utgåva som användaren ska tillfrågas om; `null` = ingen dialog. */
    var release by mutableStateOf<UpdateReleaseInfo?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    private var pendingApk: File? = null
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Tyst kontroll vid kallstart: ingen text vid "senaste version" eller nätverksfel. */
    suspend fun checkOnStartup() {
        val result = AppUpdateChecker().checkForUpdateRespectingSkip()
        AppUpdateSettings.setLastCheckTimestampMillis(System.currentTimeMillis())
        if (result is UpdateCheckResult.UpdateAvailable) release = result.release
    }

    suspend fun checkManually() {
        busy = true
        val result = try {
            AppUpdateChecker().checkForUpdate()
        } finally {
            busy = false
        }
        when (result) {
            is UpdateCheckResult.UpdateAvailable -> release = result.release
            UpdateCheckResult.UpToDate -> _messages.tryEmit("Du har den senaste versionen (v${BuildConfig.VERSION_NAME})")
            UpdateCheckResult.CheckFailed ->
                _messages.tryEmit("Kunde inte kontrollera uppdateringar. Kontrollera din internetanslutning.")
        }
    }

    fun dismiss() {
        release = null
    }

    fun skip(info: UpdateReleaseInfo) {
        AppUpdateSettings.setSkippedVersion(info.versionName)
        release = null
    }

    suspend fun downloadAndInstall(info: UpdateReleaseInfo) {
        release = null
        busy = true
        val apk = try {
            installer.downloadApk(info) { _, _ -> }
        } catch (e: ApkSizeMismatchException) {
            _messages.tryEmit("Den hämtade filen verkar skadad. Försök igen.")
            return
        } catch (e: Exception) {
            _messages.tryEmit("Hämtningen misslyckades. Kontrollera din internetanslutning och försök igen.")
            return
        } finally {
            busy = false
        }
        if (installer.canInstallPackages()) {
            installer.launchInstall(apk)
        } else {
            pendingApk = apk
            installer.requestInstallPermission()
        }
    }

    /** Anropas när användaren kommer tillbaka från systeminställningen "Installera okända appar". */
    fun resumeAfterSettingsReturn() {
        val apk = pendingApk ?: return
        if (installer.canInstallPackages()) {
            pendingApk = null
            installer.launchInstall(apk)
        } else {
            _messages.tryEmit("Installation avbröts — behörighet saknas")
        }
    }
}

@Composable
internal fun UpdateDialog(
    release: UpdateReleaseInfo,
    onInstall: () -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_uppdatering_tillganglig)) },
        text = {
            Text(
                "StockFlip v${release.versionName} finns tillgänglig.\n\n${release.releaseNotes}",
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = { TextButton(onClick = onInstall) { Text(stringResource(R.string.settings_hamta_och_installera)) } },
        dismissButton = {
            Row {
                TextButton(onClick = onSkip) { Text(stringResource(R.string.settings_hoppa_over)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_avbryt)) }
            }
        },
    )
}
