package com.stockflip.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.stockflip.BuildConfig
import com.stockflip.CountryFlagHelper
import com.stockflip.StockSearchResult

/**
 * Bolagslogga som avatar. Hämtas från Logo.dev via ticker och faller tillbaka till samma
 * tonade flagg-/initialruta som tidigare var den enda visualiseringen — vid krypto-par
 * (ingen bolagslogga att visa), okänd symbol, eller nätverksfel.
 */
@Composable
fun CompanyLogoAvatar(
    symbol: String?,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val isCrypto = symbol != null && StockSearchResult.isCryptoSymbol(symbol)
    val isPair = symbol?.contains("÷") == true
    val flag = symbol?.let {
        CountryFlagHelper.getCountryCodeFromSymbol(it)?.let(CountryFlagHelper::getFlagEmoji)
    }
    var loadFailed by remember(symbol) { mutableStateOf(false) }
    val showLogo = !isCrypto && !isPair && symbol != null && !loadFailed

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center,
    ) {
        if (showLogo) {
            AsyncImage(
                model = "https://img.logo.dev/ticker/$symbol?token=${BuildConfig.LOGO_DEV_TOKEN}&size=128",
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
                onError = { loadFailed = true },
            )
        } else {
            Text(
                text = flag ?: (symbol?.take(1) ?: "?"),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
