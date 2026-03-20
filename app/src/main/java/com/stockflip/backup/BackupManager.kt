package com.stockflip.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.stockflip.StockPair
import com.stockflip.WatchItem
import com.stockflip.WatchTypeConverter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {

    private const val SUPPORTED_VERSION = 1

    fun exportToJson(watchItems: List<WatchItem>, stockPairs: List<StockPair>): String {
        val converter = WatchTypeConverter()

        val watchArray = JSONArray()
        for (item in watchItems) {
            val obj = JSONObject()
            obj.put("watchType", converter.fromWatchType(item.watchType))
            obj.put("ticker", item.ticker ?: JSONObject.NULL)
            obj.put("companyName", item.companyName ?: JSONObject.NULL)
            obj.put("ticker1", item.ticker1 ?: JSONObject.NULL)
            obj.put("ticker2", item.ticker2 ?: JSONObject.NULL)
            obj.put("companyName1", item.companyName1 ?: JSONObject.NULL)
            obj.put("companyName2", item.companyName2 ?: JSONObject.NULL)
            obj.put("lastTriggeredDate", item.lastTriggeredDate ?: JSONObject.NULL)
            obj.put("isTriggered", item.isTriggered)
            obj.put("isActive", item.isActive)
            watchArray.put(obj)
        }

        val pairsArray = JSONArray()
        for (pair in stockPairs) {
            val obj = JSONObject()
            obj.put("ticker1", pair.ticker1)
            obj.put("ticker2", pair.ticker2)
            obj.put("companyName1", pair.companyName1)
            obj.put("companyName2", pair.companyName2)
            obj.put("priceDifference", pair.priceDifference)
            obj.put("notifyWhenEqual", pair.notifyWhenEqual)
            pairsArray.put(obj)
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
        val root = JSONObject()
        root.put("version", SUPPORTED_VERSION)
        root.put("exportedAt", timestamp)
        root.put("watchItems", watchArray)
        root.put("stockPairs", pairsArray)

        return root.toString(2)
    }

    fun importFromJson(json: String): BackupData {
        val root = JSONObject(json)
        val version = root.getInt("version")
        if (version != SUPPORTED_VERSION) {
            throw IllegalArgumentException("Okänd backup-version: $version")
        }

        val converter = WatchTypeConverter()

        val watchItems = mutableListOf<WatchItem>()
        val watchArray = root.getJSONArray("watchItems")
        for (i in 0 until watchArray.length()) {
            val obj = watchArray.getJSONObject(i)
            val watchType = converter.toWatchType(obj.getString("watchType"))
            watchItems.add(
                WatchItem(
                    id = 0,
                    watchType = watchType,
                    ticker = obj.optString("ticker").takeIf { it.isNotEmpty() && it != "null" },
                    companyName = obj.optString("companyName").takeIf { it.isNotEmpty() && it != "null" },
                    ticker1 = obj.optString("ticker1").takeIf { it.isNotEmpty() && it != "null" },
                    ticker2 = obj.optString("ticker2").takeIf { it.isNotEmpty() && it != "null" },
                    companyName1 = obj.optString("companyName1").takeIf { it.isNotEmpty() && it != "null" },
                    companyName2 = obj.optString("companyName2").takeIf { it.isNotEmpty() && it != "null" },
                    lastTriggeredDate = obj.optString("lastTriggeredDate").takeIf { it.isNotEmpty() && it != "null" },
                    isTriggered = obj.optBoolean("isTriggered", false),
                    isActive = obj.optBoolean("isActive", true)
                )
            )
        }

        val stockPairs = mutableListOf<StockPair>()
        val pairsArray = root.getJSONArray("stockPairs")
        for (i in 0 until pairsArray.length()) {
            val obj = pairsArray.getJSONObject(i)
            stockPairs.add(
                StockPair(
                    id = 0,
                    ticker1 = obj.getString("ticker1"),
                    ticker2 = obj.getString("ticker2"),
                    companyName1 = obj.getString("companyName1"),
                    companyName2 = obj.getString("companyName2"),
                    priceDifference = obj.getDouble("priceDifference"),
                    notifyWhenEqual = obj.getBoolean("notifyWhenEqual")
                )
            )
        }

        return BackupData(watchItems, stockPairs)
    }

    fun shareFile(context: Context, json: String) {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(context.cacheDir, "stockflip_backup_$timestamp.json")
        file.writeText(json)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Exportera backup"))
    }

    data class BackupData(
        val watchItems: List<WatchItem>,
        val stockPairs: List<StockPair>
    )
}
