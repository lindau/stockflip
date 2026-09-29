package com.stockflip.ui.nav

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.stockflip.R

/** Rutter i den nya Compose-navigeringen. */
object Routes {
    const val WATCHLIST = "watchlist"
    const val MARKET = "market"
    const val SETTINGS = "settings"

    const val ARG_SYMBOL = "symbol"
    const val ARG_WATCH_ID = "watchId"
    const val ARG_INSIDER_ID = "insiderId"
    const val ARG_TITLE = "title"
    const val ARG_MESSAGE = "message"
    const val STOCK_DETAIL =
        "stock/{$ARG_SYMBOL}?$ARG_WATCH_ID={$ARG_WATCH_ID}&$ARG_INSIDER_ID={$ARG_INSIDER_ID}&$ARG_TITLE={$ARG_TITLE}&$ARG_MESSAGE={$ARG_MESSAGE}"

    fun stockDetail(symbol: String, launch: DetailLaunch = DetailLaunch()): String {
        val query = listOfNotNull(
            launch.watchId?.let { "$ARG_WATCH_ID=$it" },
            launch.insiderId?.let { "$ARG_INSIDER_ID=${Uri.encode(it)}" },
            launch.title?.let { "$ARG_TITLE=${Uri.encode(it)}" },
            launch.message?.let { "$ARG_MESSAGE=${Uri.encode(it)}" },
        )
        return "stock/${Uri.encode(symbol)}" + if (query.isEmpty()) "" else "?" + query.joinToString("&")
    }

    const val ARG_PAIR_ID = "pairId"
    const val PAIR_DETAIL = "pair/{$ARG_PAIR_ID}"

    fun pairDetail(watchItemId: Int) = "pair/$watchItemId"

    const val ARG_ASSET = "asset"
    const val DOCUMENT = "document/{$ARG_ASSET}"

    /** Manual eller ändringslogg (asset-namn, t.ex. `manual.md`). */
    fun document(asset: String) = "document/${Uri.encode(asset)}"
}

/** De tre flikarna i nedre navigeringen. */
enum class TopLevelTab(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Watchlist(Routes.WATCHLIST, R.string.nav_watchlist, Icons.Outlined.Notifications),
    Market(Routes.MARKET, R.string.nav_market, Icons.AutoMirrored.Outlined.ShowChart),
    Settings(Routes.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings),
}

/** Nedre navigeringen visas bara på flikarnas rotskärmar, inte på detaljsidor. */
fun isTopLevelRoute(route: String?): Boolean = TopLevelTab.entries.any { it.route == route }

/**
 * Sammanhang när aktiedetaljen öppnas från en notis: vilken bevakning eller insideraffär som ska
 * markeras och notisens text. Tomt vid vanlig navigering.
 */
data class DetailLaunch(
    val watchId: Int? = null,
    val insiderId: String? = null,
    val title: String? = null,
    val message: String? = null,
)
