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
    const val STOCK_DETAIL = "stock/{$ARG_SYMBOL}"

    fun stockDetail(symbol: String) = "stock/${Uri.encode(symbol)}"
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
