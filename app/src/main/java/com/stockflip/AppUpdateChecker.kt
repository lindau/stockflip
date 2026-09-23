package com.stockflip

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: UpdateReleaseInfo) : UpdateCheckResult
    object UpToDate : UpdateCheckResult
    // TEMP DIAGNOSTIC — ta bort diagnostic-fältet och gå tillbaka till `object CheckFailed`
    // när den verkliga orsaken till det misslyckade uppdateringskontroll-anropet är bekräftad.
    data class CheckFailed(val diagnostic: String? = null) : UpdateCheckResult
}

/**
 * Avgör om det finns en nyare, hämtningsbar version av StockFlip. Ren orkestrering --
 * inget Context-/Activity-beroende -- så den kan användas både från den periodiska
 * bakgrundskontrollen och från en manuell "Sök efter uppdateringar"-åtgärd utan att
 * dubblera jämförelselogik.
 */
class AppUpdateChecker(private val releaseService: GithubReleaseServiceContract = GithubReleaseService) {

    suspend fun checkForUpdate(currentVersionName: String = BuildConfig.VERSION_NAME): UpdateCheckResult {
        val release = releaseService.getLatestReleaseInfo()
            ?: return UpdateCheckResult.CheckFailed(releaseService.getLatestReleaseInfoDiagnostic()) // TEMP DIAGNOSTIC
        return if (isNewerVersion(currentVersionName, release.versionName)) {
            UpdateCheckResult.UpdateAvailable(release)
        } else {
            UpdateCheckResult.UpToDate
        }
    }

    /**
     * Som [checkForUpdate], men nedgraderar till [UpdateCheckResult.UpToDate] om den
     * hittade versionen är en användaren redan valt att hoppa över -- används bara av
     * den periodiska bakgrundskontrollen, så den inte notifierar om samma version om
     * och om igen. En manuell kontroll ska alltid visa en tillgänglig uppdatering, även
     * en tidigare hoppad, så den anropar [checkForUpdate] direkt i stället.
     */
    suspend fun checkForUpdateRespectingSkip(
        currentVersionName: String = BuildConfig.VERSION_NAME,
        skippedVersion: () -> String? = AppUpdateSettings::getSkippedVersion
    ): UpdateCheckResult {
        val result = checkForUpdate(currentVersionName)
        if (result is UpdateCheckResult.UpdateAvailable && result.release.versionName == skippedVersion()) {
            return UpdateCheckResult.UpToDate
        }
        return result
    }
}
