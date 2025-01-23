import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException

class GoogleFinanceApi {
    companion object {
        private const val BASE_URL = "https://www.google.com/finance/quote/"

        fun getStockPrice(symbol: String): Double? {
            try {
                val url = "$BASE_URL$symbol:STO"
                val doc: Document = Jsoup.connect(url).get()
                val priceElement = doc.selectFirst("div[data-last-price]")
                return priceElement?.attr("data-last-price")?.toDoubleOrNull()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return null
        }
    }
}

