import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

class WatchCriteriaConverter {
    private val gson = Gson().newBuilder()
        .registerTypeAdapter(WatchCriteria::class.java, WatchCriteriaAdapter())
        .create()
    
    @TypeConverter
    fun fromWatchCriteria(watchCriteria: WatchCriteria?): String? {
        if (watchCriteria == null) {
            return null
        }
        return gson.toJson(watchCriteria)
    }
    
    @TypeConverter
    fun toWatchCriteria(watchCriteriaString: String?): WatchCriteria? {
        if (watchCriteriaString == null || watchCriteriaString.isEmpty()) {
            return null
        }
        return try {
            gson.fromJson(watchCriteriaString, WatchCriteria::class.java)
        } catch (e: Exception) {
            null
        }
    }
}

class WatchCriteriaAdapter : JsonSerializer<WatchCriteria>, JsonDeserializer<WatchCriteria> {
    override fun serialize(src: WatchCriteria, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val jsonObject = JsonObject()
        when (src) {
            is WatchCriteria.PriceTargetCriteria -> {
                jsonObject.addProperty("type", "PriceTargetCriteria")
                jsonObject.addProperty("threshold", src.threshold)
                jsonObject.addProperty("comparison", src.comparison.name)
            }
            is WatchCriteria.PERatioCriteria -> {
                jsonObject.addProperty("type", "PERatioCriteria")
                jsonObject.addProperty("threshold", src.threshold)
                jsonObject.addProperty("comparison", src.comparison.name)
            }
            is WatchCriteria.PSRatioCriteria -> {
                jsonObject.addProperty("type", "PSRatioCriteria")
                jsonObject.addProperty("threshold", src.threshold)
                jsonObject.addProperty("comparison", src.comparison.name)
            }
            is WatchCriteria.ATHDropCriteria -> {
                jsonObject.addProperty("type", "ATHDropCriteria")
                jsonObject.addProperty("dropPercentage", src.dropPercentage)
            }
            is WatchCriteria.DailyHighDropCriteria -> {
                jsonObject.addProperty("type", "DailyHighDropCriteria")
                jsonObject.addProperty("dropPercentage", src.dropPercentage)
            }
        }
        return jsonObject
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): WatchCriteria {
        val jsonObject = json.asJsonObject
        val type = jsonObject.get("type").asString
        
        return when (type) {
            "PriceTargetCriteria" -> {
                val threshold = jsonObject.get("threshold").asDouble
                val comparison = ComparisonType.valueOf(jsonObject.get("comparison").asString)
                WatchCriteria.PriceTargetCriteria(threshold, comparison)
            }
            "PERatioCriteria" -> {
                val threshold = jsonObject.get("threshold").asDouble
                val comparison = ComparisonType.valueOf(jsonObject.get("comparison").asString)
                WatchCriteria.PERatioCriteria(threshold, comparison)
            }
            "PSRatioCriteria" -> {
                val threshold = jsonObject.get("threshold").asDouble
                val comparison = ComparisonType.valueOf(jsonObject.get("comparison").asString)
                WatchCriteria.PSRatioCriteria(threshold, comparison)
            }
            "ATHDropCriteria" -> {
                val dropPercentage = jsonObject.get("dropPercentage").asDouble
                WatchCriteria.ATHDropCriteria(dropPercentage)
            }
            "DailyHighDropCriteria" -> {
                val dropPercentage = jsonObject.get("dropPercentage").asDouble
                WatchCriteria.DailyHighDropCriteria(dropPercentage)
            }
            else -> throw JsonParseException("Unknown WatchCriteria type: $type")
        }
    }
}

