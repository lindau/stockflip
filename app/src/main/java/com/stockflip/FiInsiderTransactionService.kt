package com.stockflip

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FiInsiderTransactionService(
    private val client: OkHttpClient = OkHttpClient(),
    private val baseUrl: String = "https://marknadssok.fi.se/Publiceringsklient/sv-SE/Search/Search"
) {
    suspend fun getRecentInsiderPurchases(
        symbol: String,
        issuerName: String?,
        minPublishedAtMillis: Long,
        lookbackDays: Int = 14
    ): List<InsiderPurchase> = withContext(Dispatchers.IO) {
        val issuerQuery = issuerSearchName(issuerName ?: symbol)
        if (issuerQuery.isBlank()) return@withContext emptyList()

        val toDate = Calendar.getInstance()
        val fromDate = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -lookbackDays)
        }
        val csv = requestExportCsv(
            issuer = issuerQuery,
            fromDate = DATE_FORMAT.format(fromDate.time),
            toDate = DATE_FORMAT.format(toDate.time)
        )

        mapRowsToPurchases(parseCsv(csv), symbol, minPublishedAtMillis)
    }

    internal fun mapRowsToPurchases(
        rows: List<Map<String, String>>,
        symbol: String,
        minPublishedAtMillis: Long
    ): List<InsiderPurchase> {
        return rows
            .filter { row ->
                (row["Karaktär"].equals("Förvärv", ignoreCase = true) || row["Karaktär"].equals("Avyttring", ignoreCase = true)) &&
                    row["Instrumenttyp"].equals("Aktie", ignoreCase = true)
            }
            .mapNotNull { row ->
                val publishedAt = parseFiDate(row["Publiceringsdatum"].orEmpty())
                if (publishedAt != null && publishedAt < minPublishedAtMillis) return@mapNotNull null
                val transactionType = if (row["Karaktär"].equals("Avyttring", ignoreCase = true)) {
                    InsiderTransactionType.SELL
                } else {
                    InsiderTransactionType.BUY
                }
                val transactionDate = row["Transaktionsdatum"].orEmpty().take(10)
                val volume = row["Volym"]?.parseFiDouble()
                val price = row["Pris"]?.parseFiDouble()
                val estimatedValue = if (volume != null && price != null) volume * price else null
                val issuer = row["Utgivare"].orEmpty()
                val owner = row["Person i ledande ställning"]
                    ?.takeIf { it.isNotBlank() }
                    ?: row["Anmälningsskyldig"]
                    ?: "Okänd insider"
                val isin = row["ISIN"].orEmpty()
                val id = listOf("FI", isin, row["Publiceringsdatum"], owner, transactionDate, volume, price)
                    .joinToString(":")

                InsiderPurchase(
                    id = id,
                    symbol = symbol.uppercase(Locale("sv", "SE")),
                    cik = "FI",
                    accessionNumber = id,
                    reportingOwner = owner,
                    relationship = row["Befattning"]?.takeIf { it.isNotBlank() },
                    transactionDate = transactionDate,
                    shares = volume,
                    pricePerShare = price,
                    estimatedValue = estimatedValue,
                    securityTitle = row["Instrumentnamn"]?.takeIf { it.isNotBlank() } ?: issuer,
                    filingDate = row["Publiceringsdatum"]?.take(10),
                    acceptedAtMillis = publishedAt,
                    transactionType = transactionType
                )
            }
    }

    internal fun parseCsv(csv: String): List<Map<String, String>> {
        val rows = csv
            .lineSequence()
            .map { it.trimEnd('\r') }
            .filter { it.isNotBlank() }
            .map(::parseCsvLine)
            .toList()
        if (rows.size < 2) return emptyList()

        val headers = rows.first().map { it.trim().trimStart('\uFEFF') }
        return rows.drop(1).map { values ->
            headers.mapIndexed { index, header ->
                header to values.getOrElse(index) { "" }.trim()
            }.toMap()
        }
    }

    private fun requestExportCsv(
        issuer: String,
        fromDate: String,
        toDate: String
    ): String {
        val url = "$baseUrl?SearchFunctionType=Insyn" +
            "&Utgivare=${issuer.urlEncode()}" +
            "&PersonILedandeSt%C3%A4llningNamn=" +
            "&Transaktionsdatum.From=" +
            "&Transaktionsdatum.To=" +
            "&Publiceringsdatum.From=$fromDate" +
            "&Publiceringsdatum.To=$toDate" +
            "&button=export" +
            "&Page=1"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "StockFlip/1.0 personal-android-app")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("FI request failed: ${response.code} ${response.message}")
            }
            val bytes = response.body?.bytes() ?: ByteArray(0)
            return decodeCsv(bytes)
        }
    }

    private fun decodeCsv(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            val first = bytes[0].toInt() and 0xFF
            val second = bytes[1].toInt() and 0xFF
            if (first == 0xFF && second == 0xFE) {
                return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
            }
            if (first == 0xFE && second == 0xFF) {
                return bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
            }
        }
        return bytes.toString(Charset.forName("UTF-16LE"))
    }

    private fun parseCsvLine(line: String): List<String> {
        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && inQuotes && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index++
                }
                char == '"' -> inQuotes = !inQuotes
                char == ';' && !inQuotes -> {
                    values += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
            index++
        }
        values += current.toString()
        return values
    }

    private fun issuerSearchName(value: String): String {
        return value
            .substringBefore(" - ")
            .substringBefore("(")
            .replace(Regex("\\b(publ|AB|ser\\.?\\s*[A-Z]|B)\\b", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseFiDate(value: String): Long? {
        val normalized = value.trim()
        return runCatching {
            when {
                normalized.length >= 19 -> DATE_TIME_FORMAT.parse(normalized.take(19))?.time
                normalized.length >= 10 -> DATE_FORMAT.parse(normalized.take(10))?.time
                else -> null
            }
        }.getOrNull()
    }

    private fun String.parseFiDouble(): Double? {
        return replace("\u00A0", "")
            .replace(" ", "")
            .replace(",", ".")
            .toDoubleOrNull()
            ?.takeIf { !it.isNaN() }
    }

    private fun String.urlEncode(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name())

    companion object {
        private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        private val DATE_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }
}
