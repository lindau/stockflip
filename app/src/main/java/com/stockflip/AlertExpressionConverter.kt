package com.stockflip

import org.json.JSONArray
import org.json.JSONObject

/**
 * Converter för att serialisera/deserialisera AlertExpression till/från JSON.
 * 
 * Används för att spara AlertExpression i Room-databasen.
 */
object AlertExpressionConverter {
    /**
     * Konverterar AlertExpression till JSON-sträng.
     */
    fun toJson(expression: AlertExpression): String {
        return expressionToJson(expression).toString()
    }

    /**
     * Konverterar JSON-sträng till AlertExpression.
     */
    fun fromJson(json: String): AlertExpression {
        return jsonToExpression(JSONObject(json))
    }

    private fun expressionToJson(expression: AlertExpression): JSONObject {
        return when (expression) {
            is AlertExpression.Single -> {
                JSONObject().apply {
                    put("type", "Single")
                    put("rule", ruleToJson(expression.rule))
                }
            }
            is AlertExpression.And -> {
                JSONObject().apply {
                    put("type", "And")
                    put("left", expressionToJson(expression.left))
                    put("right", expressionToJson(expression.right))
                }
            }
            is AlertExpression.Or -> {
                JSONObject().apply {
                    put("type", "Or")
                    put("left", expressionToJson(expression.left))
                    put("right", expressionToJson(expression.right))
                }
            }
            is AlertExpression.Not -> {
                JSONObject().apply {
                    put("type", "Not")
                    put("inner", expressionToJson(expression.inner))
                }
            }
        }
    }

    private fun jsonToExpression(json: JSONObject): AlertExpression {
        val type = json.getString("type")
        return when (type) {
            "Single" -> {
                AlertExpression.Single(jsonToRule(json.getJSONObject("rule")))
            }
            "And" -> {
                AlertExpression.And(
                    left = jsonToExpression(json.getJSONObject("left")),
                    right = jsonToExpression(json.getJSONObject("right"))
                )
            }
            "Or" -> {
                AlertExpression.Or(
                    left = jsonToExpression(json.getJSONObject("left")),
                    right = jsonToExpression(json.getJSONObject("right"))
                )
            }
            "Not" -> {
                AlertExpression.Not(
                    inner = jsonToExpression(json.getJSONObject("inner"))
                )
            }
            else -> throw IllegalArgumentException("Unknown expression type: $type")
        }
    }

    private fun ruleToJson(rule: AlertRule): JSONObject {
        return when (rule) {
            is AlertRule.PairSpread -> {
                JSONObject().apply {
                    put("ruleType", "PairSpread")
                    put("symbolA", rule.symbolA)
                    put("symbolB", rule.symbolB)
                    put("spreadTarget", rule.spreadTarget)
                    put("notifyWhenEqual", rule.notifyWhenEqual)
                }
            }
            is AlertRule.SinglePrice -> {
                JSONObject().apply {
                    put("ruleType", "SinglePrice")
                    put("symbol", rule.symbol)
                    put("comparisonType", rule.comparisonType.name)
                    put("priceLimit", rule.priceLimit)
                    if (rule.maxPrice != null) {
                        put("maxPrice", rule.maxPrice)
                    }
                }
            }
            is AlertRule.SingleDrawdownFromHigh -> {
                JSONObject().apply {
                    put("ruleType", "SingleDrawdownFromHigh")
                    put("symbol", rule.symbol)
                    put("dropType", rule.dropType.name)
                    put("dropValue", rule.dropValue)
                    put("reference", rule.reference.name)
                }
            }
            is AlertRule.SingleDailyMove -> {
                JSONObject().apply {
                    put("ruleType", "SingleDailyMove")
                    put("symbol", rule.symbol)
                    put("percentThreshold", rule.percentThreshold)
                    put("direction", rule.direction.name)
                }
            }
            is AlertRule.SingleKeyMetric -> {
                JSONObject().apply {
                    put("ruleType", "SingleKeyMetric")
                    put("symbol", rule.symbol)
                    put("metricType", rule.metricType.name)
                    put("targetValue", rule.targetValue)
                    put("direction", rule.direction.name)
                }
            }
        }
    }

    private fun jsonToRule(json: JSONObject): AlertRule {
        val ruleType = json.getString("ruleType")
        return when (ruleType) {
            "PairSpread" -> {
                AlertRule.PairSpread(
                    symbolA = json.getString("symbolA"),
                    symbolB = json.getString("symbolB"),
                    spreadTarget = json.getDouble("spreadTarget"),
                    notifyWhenEqual = json.optBoolean("notifyWhenEqual", false)
                )
            }
            "SinglePrice" -> {
                AlertRule.SinglePrice(
                    symbol = json.getString("symbol"),
                    comparisonType = AlertRule.PriceComparisonType.valueOf(json.getString("comparisonType")),
                    priceLimit = json.getDouble("priceLimit"),
                    maxPrice = if (json.has("maxPrice")) json.getDouble("maxPrice") else null
                )
            }
            "SingleDrawdownFromHigh" -> {
                AlertRule.SingleDrawdownFromHigh(
                    symbol = json.getString("symbol"),
                    dropType = AlertRule.DrawdownDropType.valueOf(json.getString("dropType")),
                    dropValue = json.getDouble("dropValue"),
                    reference = json.optString("reference")
                        .takeIf { it.isNotBlank() }
                        ?.let { AlertRule.HighReference.valueOf(it) }
                        ?: AlertRule.HighReference.FIFTY_TWO_WEEK_HIGH
                )
            }
            "SingleDrawdownFromAllTimeHigh" -> {
                AlertRule.SingleDrawdownFromHigh(
                    symbol = json.getString("symbol"),
                    dropType = AlertRule.DrawdownDropType.PERCENTAGE,
                    dropValue = json.getDouble("dropPercentage"),
                    reference = AlertRule.HighReference.ALL_TIME_HIGH
                )
            }
            "SingleDailyMove" -> {
                AlertRule.SingleDailyMove(
                    symbol = json.getString("symbol"),
                    percentThreshold = json.getDouble("percentThreshold"),
                    direction = AlertRule.DailyMoveDirection.valueOf(json.getString("direction"))
                )
            }
            "SingleKeyMetric" -> {
                AlertRule.SingleKeyMetric(
                    symbol = json.getString("symbol"),
                    metricType = AlertRule.KeyMetricType.valueOf(json.getString("metricType")),
                    targetValue = json.getDouble("targetValue"),
                    direction = AlertRule.PriceComparisonType.valueOf(json.getString("direction"))
                )
            }
            else -> throw IllegalArgumentException("Unknown rule type: $ruleType")
        }
    }
}
