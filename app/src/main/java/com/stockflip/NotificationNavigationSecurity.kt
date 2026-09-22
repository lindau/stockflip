package com.stockflip

/**
 * Navigeringsmål för en push-notis. Bestäms av notis-producenten (workers) och verifieras
 * på nytt av [MainActivity] utifrån intent-extras.
 *
 * VIKTIGT: producent och konsument måste välja variant i EXAKT samma ordning
 * (pair → stock → alerts → update), annars byggs olika kanoniska payloads och token avvisas.
 */
sealed interface NotificationDestination {
    data class PairWatch(val pairWatchItemId: Int) : NotificationDestination
    data class Stock(val ticker: String, val watchItemId: Int?) : NotificationDestination
    data class AlertList(val watchItemId: Int) : NotificationDestination
    data class AppUpdate(val versionName: String) : NotificationDestination
}

/**
 * Skyddar notis-navigation mot förfalskade intents mot den exporterade [MainActivity].
 *
 * Token = HMAC-SHA256 över en kanonisk representation av navigeringsmålet, signerad med en
 * Keystore-baserad hemlighet ([AppSecurityManager]). Stateless:
 *  - ingen TTL (notiser kan ligga kvar i systemfältet i dagar),
 *  - icke-konsumerande (samma notis kan tryckas flera gånger),
 *  - inga SharedPreferences-races mellan samtidiga workers,
 *  - överlever process-omstart och enhetsomstart.
 */
object NotificationNavigationSecurity {
    private const val PAYLOAD_VERSION = "v1"

    fun issueToken(destination: NotificationDestination): String =
        AppSecurityManager.signNotificationPayload(canonicalPayload(destination))

    fun verifyToken(destination: NotificationDestination, token: String?): Boolean {
        if (token.isNullOrBlank()) return false
        return AppSecurityManager.verifyNotificationPayload(canonicalPayload(destination), token)
    }

    /**
     * Kanonisk, entydig strängrepresentation av navigeringsmålet. Delimiter '|' — Yahoo-tickers
     * innehåller bara [A-Z0-9.^=-] så '|' kan aldrig förekomma i en ticker.
     */
    internal fun canonicalPayload(destination: NotificationDestination): String = when (destination) {
        is NotificationDestination.PairWatch ->
            "$PAYLOAD_VERSION|pair|${destination.pairWatchItemId}"
        is NotificationDestination.Stock ->
            "$PAYLOAD_VERSION|stock|${destination.ticker}|${destination.watchItemId ?: 0}"
        is NotificationDestination.AlertList ->
            "$PAYLOAD_VERSION|alerts|${destination.watchItemId}"
        is NotificationDestination.AppUpdate ->
            "$PAYLOAD_VERSION|update|${destination.versionName}"
    }
}
