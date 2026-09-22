package com.stockflip

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApkSizeMismatchException(expected: Long, actual: Long) :
    IOException("Nedladdad fil har fel storlek (förväntad $expected, fick $actual byte)")

/**
 * Hämtar och installerar en StockFlip-uppdatering. Den enda komponenten i uppdaterings-
 * flödet som behöver en Activity (inte bara en Context), eftersom "Installera okända
 * appar"-behörigheten kräver en registerForActivityResult-rundtur till Inställningar.
 * Måste instansieras som ett aktivitetsfält (t.ex. `by lazy`), aldrig lokalt i en
 * funktion -- registerForActivityResult kräver att launchern registreras innan
 * Activity:n startar.
 */
class AppUpdateInstaller(
    private val activity: ComponentActivity,
    private val onUnknownSourcesResult: () -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val unknownSourcesLauncher = activity.registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onUnknownSourcesResult() }

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission() {
        MaterialAlertDialogBuilder(activity)
            .setTitle("Installationsbehörighet krävs")
            .setMessage("StockFlip behöver tillstånd att installera appar från denna källa för att kunna uppdatera sig själv. Du tas nu till en systeminställning för att tillåta det.")
            .setPositiveButton("Fortsätt") { _, _ ->
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${activity.packageName}")
                )
                unknownSourcesLauncher.launch(intent)
            }
            .setNegativeButton("Avbryt", null)
            .show()
    }

    suspend fun downloadApk(
        release: UpdateReleaseInfo,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val updatesDir = File(activity.cacheDir, "updates").apply { mkdirs() }
        val outputFile = File(updatesDir, "stockflip-${release.versionName}.apk")

        val request = Request.Builder().url(release.downloadUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Nedladdning misslyckades: HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("Tomt svar vid nedladdning")
            var bytesRead = 0L
            outputFile.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        onProgress(bytesRead, release.apkSizeBytes)
                    }
                }
            }
        }

        if (outputFile.length() != release.apkSizeBytes) {
            val actualSize = outputFile.length()
            outputFile.delete()
            throw ApkSizeMismatchException(release.apkSizeBytes, actualSize)
        }

        outputFile
    }

    fun buildInstallIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun launchInstall(apkFile: File) {
        activity.startActivity(buildInstallIntent(activity, apkFile))
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8 * 1024
    }
}
