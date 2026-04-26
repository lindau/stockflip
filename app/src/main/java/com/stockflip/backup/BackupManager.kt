package com.stockflip.backup

import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.content.FileProvider
import com.stockflip.AppSecurityManager
import com.stockflip.StockPair
import com.stockflip.WatchItem
import com.stockflip.WatchTypeConverter
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupManager {

    private const val SUPPORTED_VERSION = 1
    private const val SIGNATURE_VERSION = 1
    private const val SIGNATURE_FIELD = "signature"
    private const val SIGNATURE_VERSION_FIELD = "signatureVersion"

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
        verifySignature(root)
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
        val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val file = File(backupDir, "stockflip_backup_$timestamp.json")
        file.writeText(createSignedBackupJson(json))

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

    private fun createSignedBackupJson(json: String): String {
        val root = JSONObject(json)
        root.put(SIGNATURE_VERSION_FIELD, SIGNATURE_VERSION)
        val signature = AppSecurityManager.signBackupPayload(canonicalPayload(root))
        root.put(SIGNATURE_FIELD, signature)
        return root.toString(2)
    }

    private fun verifySignature(root: JSONObject) {
        val signatureVersion = root.optInt(SIGNATURE_VERSION_FIELD, -1)
        val signature = root.optString(SIGNATURE_FIELD, "")

        if (signatureVersion != SIGNATURE_VERSION || signature.isBlank()) {
            throw IllegalArgumentException(
                "Backupen saknar giltig signatur. Endast signerade backuper från den här appinstallationen kan importeras."
            )
        }

        if (!AppSecurityManager.verifyBackupPayload(canonicalPayload(root), signature)) {
            throw IllegalArgumentException("Backupens signatur är ogiltig eller backupen har ändrats.")
        }
    }

    private fun canonicalPayload(root: JSONObject): String {
        val builder = StringBuilder()
        builder.append("version=").append(root.getInt("version")).append('\n')
        builder.append("exportedAt=").append(encodeValue(root.opt("exportedAt"))).append('\n')
        builder.append("watchItems=").append(canonicalWatchItems(root.getJSONArray("watchItems"))).append('\n')
        builder.append("stockPairs=").append(canonicalStockPairs(root.getJSONArray("stockPairs")))
        return builder.toString()
    }

    private fun canonicalWatchItems(array: JSONArray): String {
        val builder = StringBuilder()
        for (index in 0 until array.length()) {
            val obj = array.getJSONObject(index)
            appendField(builder, obj, "watchType")
            appendField(builder, obj, "ticker")
            appendField(builder, obj, "companyName")
            appendField(builder, obj, "ticker1")
            appendField(builder, obj, "ticker2")
            appendField(builder, obj, "companyName1")
            appendField(builder, obj, "companyName2")
            appendField(builder, obj, "lastTriggeredDate")
            appendField(builder, obj, "isTriggered")
            appendField(builder, obj, "isActive")
            builder.append('\n')
        }
        return builder.toString()
    }

    private fun canonicalStockPairs(array: JSONArray): String {
        val builder = StringBuilder()
        for (index in 0 until array.length()) {
            val obj = array.getJSONObject(index)
            appendField(builder, obj, "ticker1")
            appendField(builder, obj, "ticker2")
            appendField(builder, obj, "companyName1")
            appendField(builder, obj, "companyName2")
            appendField(builder, obj, "priceDifference")
            appendField(builder, obj, "notifyWhenEqual")
            builder.append('\n')
        }
        return builder.toString()
    }

    private fun appendField(builder: StringBuilder, obj: JSONObject, key: String) {
        builder.append(key)
            .append('=')
            .append(encodeValue(obj.opt(key)))
            .append('|')
    }

    private fun encodeValue(value: Any?): String {
        if (value == null || value == JSONObject.NULL) {
            return "null"
        }

        return when (value) {
            is Boolean, is Number -> value.toString()
            else -> Base64.encodeToString(
                value.toString().toByteArray(StandardCharsets.UTF_8),
                Base64.NO_WRAP
            )
        }
    }
}
