package com.stockflip

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response

private const val ONE_WEEK_SECONDS = 7 * 24 * 60 * 60
private const val DISK_CACHE_MAX_BYTES = 20L * 1024 * 1024 // 20 MB

/**
 * Bygger den delade Coil-ImageLoadern för bolagsloggor (Logo.dev) och kryptoikoner (CoinCap).
 *
 * Båda API:erna skickar svaga/saknade Cache-Control-headers, vilket gör att Coil annars
 * anser cachen "stale" och hämtar om bilden vid varje kallstart. Vi tvingar därför fram en
 * frashetsperiod på en vecka på svaren innan de når Coils diskcache, så att loggor/ikoner
 * återanvänds från disk i ~7 dagar innan de hämtas om.
 *
 * Denna ImageLoader/OkHttpClient används enbart av [ui.components.CompanyLogoAvatar] just nu –
 * om fler Coil-bildkällor läggs till senare, ompröva om enveckasregeln fortfarande passar.
 */
object LogoImageLoader {

    fun build(context: Context): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(WeeklyFreshnessInterceptor)
            .build()

        val diskCache = DiskCache.Builder()
            .directory(context.cacheDir.resolve("logo_image_cache"))
            .maxSizeBytes(DISK_CACHE_MAX_BYTES)
            .build()

        return ImageLoader.Builder(context)
            .okHttpClient(okHttpClient)
            .diskCache(diskCache)
            .build()
    }

    /** Skriver om Cache-Control så Coil behandlar svaret som färskt i en vecka. */
    private object WeeklyFreshnessInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            return response.newBuilder()
                .header("Cache-Control", "max-age=$ONE_WEEK_SECONDS")
                .build()
        }
    }
}
