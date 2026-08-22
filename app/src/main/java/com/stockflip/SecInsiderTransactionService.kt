package com.stockflip

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

enum class InsiderTransactionType {
    BUY,
    SELL
}

data class InsiderPurchase(
    val id: String,
    val symbol: String,
    val cik: String,
    val accessionNumber: String,
    val reportingOwner: String,
    val relationship: String?,
    val transactionDate: String,
    val shares: Double?,
    val pricePerShare: Double?,
    val estimatedValue: Double?,
    val securityTitle: String?,
    val filingDate: String?,
    val acceptedAtMillis: Long?,
    val transactionType: InsiderTransactionType
) {
    fun toEntity(): InsiderTransactionEntity = InsiderTransactionEntity(
        id = id,
        symbol = symbol,
        cik = cik,
        accessionNumber = accessionNumber,
        reportingOwner = reportingOwner,
        relationship = relationship,
        transactionDate = transactionDate,
        shares = shares,
        pricePerShare = pricePerShare,
        estimatedValue = estimatedValue,
        securityTitle = securityTitle,
        filingDate = filingDate,
        acceptedAtMillis = acceptedAtMillis,
        transactionType = transactionType.name
    )
}

class SecInsiderTransactionService(
    private val client: OkHttpClient = OkHttpClient(),
    private val secBaseUrl: String = "https://data.sec.gov",
    private val secFilesBaseUrl: String = "https://www.sec.gov"
) {
    private var tickerMap: Map<String, String>? = null

    suspend fun getRecentInsiderPurchases(
        symbol: String,
        minAcceptedAtMillis: Long,
        maxFilings: Int = 25
    ): List<InsiderPurchase> = withContext(Dispatchers.IO) {
        val normalizedSymbol = normalizeSymbol(symbol)
        val cik = getCikForTicker(normalizedSymbol) ?: return@withContext emptyList()
        val submissions = requestJson("$secBaseUrl/submissions/CIK$cik.json")
        val recent = submissions
            .optJSONObject("filings")
            ?.optJSONObject("recent")
            ?: return@withContext emptyList()

        val forms = recent.optJSONArray("form") ?: return@withContext emptyList()
        val accessionNumbers = recent.optJSONArray("accessionNumber") ?: return@withContext emptyList()
        val primaryDocuments = recent.optJSONArray("primaryDocument") ?: return@withContext emptyList()
        val filingDates = recent.optJSONArray("filingDate")
        val acceptanceTimes = recent.optJSONArray("acceptanceDateTime")

        val purchases = mutableListOf<InsiderPurchase>()
        var inspectedFilings = 0

        for (index in 0 until forms.length()) {
            val form = forms.optString(index)
            if (form != "4" && form != "4/A") continue
            if (inspectedFilings >= maxFilings) break

            val accessionNumber = accessionNumbers.optString(index).takeIf { it.isNotBlank() } ?: continue
            val acceptedAtMillis = acceptanceTimes?.optString(index)?.let(::parseSecAcceptedAt)
            if (acceptedAtMillis != null && acceptedAtMillis < minAcceptedAtMillis) {
                continue
            }

            val primaryDocument = primaryDocuments.optString(index).takeIf { it.isNotBlank() } ?: continue
            val filingDate = filingDates?.optString(index)
            val xmlUrl = archiveUrl(cik, accessionNumber, primaryDocument)

            inspectedFilings++
            val xml = requestText(xmlUrl)
            purchases += parseOwnershipXml(
                symbol = normalizedSymbol,
                cik = cik,
                accessionNumber = accessionNumber,
                filingDate = filingDate,
                acceptedAtMillis = acceptedAtMillis,
                xml = xml
            ).filter { purchase ->
                purchase.acceptedAtMillis == null || purchase.acceptedAtMillis >= minAcceptedAtMillis
            }
        }

        purchases
    }

    suspend fun getCikForTicker(symbol: String): String? = withContext(Dispatchers.IO) {
        val normalizedSymbol = normalizeSymbol(symbol)
        val cached = tickerMap
        if (cached != null) return@withContext cached[normalizedSymbol]

        val json = requestJson("$secFilesBaseUrl/files/company_tickers.json")
        val map = buildMap {
            json.keys().forEach { key ->
                val tickerObject = json.optJSONObject(key) ?: return@forEach
                val ticker = normalizeSymbol(tickerObject.optString("ticker"))
                val cik = tickerObject.optLong("cik_str", 0L)
                    .takeIf { it > 0L }
                    ?.toString()
                    ?.padStart(10, '0')
                if (ticker.isNotBlank() && cik != null) {
                    put(ticker, cik)
                }
            }
        }
        tickerMap = map
        map[normalizedSymbol]
    }

    internal fun parseOwnershipXml(
        symbol: String,
        cik: String,
        accessionNumber: String,
        filingDate: String?,
        acceptedAtMillis: Long?,
        xml: String
    ): List<InsiderPurchase> {
        val document = DocumentBuilderFactory.newInstance()
            .apply {
                isNamespaceAware = false
                runCatching {
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                }
            }
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

        val owner = document.getElementsByTagName("reportingOwner").item(0) as? Element
        val ownerName = owner
            ?.firstText("rptOwnerName")
            ?.takeIf { it.isNotBlank() }
            ?: "Okänd insider"
        val relationship = owner?.relationshipLabel()

        val transactions = document.getElementsByTagName("nonDerivativeTransaction")
        val purchases = mutableListOf<InsiderPurchase>()
        for (index in 0 until transactions.length) {
            val transaction = transactions.item(index) as? Element ?: continue
            val transactionCode = transaction.firstText("transactionCode")
            val acquiredDisposed = transaction.firstText("transactionAcquiredDisposedCode")
            val direction = when {
                transactionCode == PURCHASE_TRANSACTION_CODE && acquiredDisposed == ACQUIRED_CODE -> InsiderTransactionType.BUY
                transactionCode == SALE_TRANSACTION_CODE && acquiredDisposed == DISPOSED_CODE -> InsiderTransactionType.SELL
                else -> null
            } ?: continue

            val shares = transaction.firstText("transactionShares")?.parseSecDouble()
            val price = transaction.firstText("transactionPricePerShare")?.parseSecDouble()
            val transactionDate = transaction.firstText("transactionDate").orEmpty()
            val securityTitle = transaction.firstText("securityTitle")
            val estimatedValue = if (shares != null && price != null) shares * price else null
            val id = listOf(accessionNumber, index, transactionDate, ownerName, shares, price)
                .joinToString(":")

            purchases += InsiderPurchase(
                id = id,
                symbol = normalizeSymbol(symbol),
                cik = cik,
                accessionNumber = accessionNumber,
                reportingOwner = ownerName,
                relationship = relationship,
                transactionDate = transactionDate,
                shares = shares,
                pricePerShare = price,
                estimatedValue = estimatedValue,
                securityTitle = securityTitle,
                filingDate = filingDate,
                acceptedAtMillis = acceptedAtMillis,
                transactionType = direction
            )
        }
        return purchases
    }

    private fun requestJson(url: String): JSONObject = JSONObject(requestText(url))

    private fun requestText(url: String): String {
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", SEC_USER_AGENT)
            .addHeader("Accept-Encoding", "gzip, deflate")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("SEC request failed: ${response.code} ${response.message}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun archiveUrl(cik: String, accessionNumber: String, primaryDocument: String): String {
        val cikWithoutLeadingZeros = cik.trimStart('0')
        val accessionWithoutDashes = accessionNumber.replace("-", "")
        return "$secFilesBaseUrl/Archives/edgar/data/$cikWithoutLeadingZeros/$accessionWithoutDashes/$primaryDocument"
    }

    private fun parseSecAcceptedAt(value: String): Long? {
        if (value.isBlank()) return null
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return runCatching { parser.parse(value)?.time }.getOrNull()
    }

    private fun normalizeSymbol(symbol: String): String =
        symbol.trim().uppercase(Locale.US)

    private fun Element.relationshipLabel(): String? {
        val relationship = getElementsByTagName("reportingOwnerRelationship").item(0) as? Element
            ?: return null
        val labels = mutableListOf<String>()
        if (relationship.firstText("isDirector") == "1") labels += "Styrelse"
        if (relationship.firstText("isOfficer") == "1") {
            labels += relationship.firstText("officerTitle")?.takeIf { it.isNotBlank() } ?: "Ledning"
        }
        if (relationship.firstText("isTenPercentOwner") == "1") labels += "10%+ ägare"
        relationship.firstText("otherText")?.takeIf { it.isNotBlank() }?.let { labels += it }
        return labels.joinToString(", ").takeIf { it.isNotBlank() }
    }

    private fun Element.firstText(tagName: String): String? {
        val node = getElementsByTagName(tagName).item(0) ?: return null
        return node.textContent?.trim()
    }

    private fun String.parseSecDouble(): Double? =
        replace(",", "").toDoubleOrNull()?.takeIf { !it.isNaN() }

    companion object {
        private const val PURCHASE_TRANSACTION_CODE = "P"
        private const val ACQUIRED_CODE = "A"
        private const val SALE_TRANSACTION_CODE = "S"
        private const val DISPOSED_CODE = "D"
        private const val SEC_USER_AGENT = "StockFlip/1.0 personal-android-app"
    }
}
