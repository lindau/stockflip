package com.stockflip

import android.util.Log

/**
 * Evaluator för AlertExpression.
 * 
 * Utvärderar kombinerade alert-uttryck med logiska operatorer (AND, OR, NOT).
 * 
 * Enligt PRD Fas 3 ska detta möjliggöra evaluering av uttryck som:
 * - And(Single(PriceBelow), Single(PEBelow))
 * - Or(Single(PriceBelow), Single(DrawdownAbove))
 */
object ExpressionEvaluator {
    private const val TAG = "ExpressionEvaluator"

    /**
     * Utvärderar ett AlertExpression baserat på marknadsdata.
     * 
     * @param expression AlertExpression att utvärdera
     * @param snapshots Map av symbol -> MarketSnapshot för alla aktier som behövs
     * @return true om uttrycket är sant (alert ska trigga), false annars
     */
    fun evaluateExpression(
        expression: AlertExpression,
        snapshots: Map<String, MarketSnapshot>
    ): Boolean {
        return try {
            when (expression) {
                is AlertExpression.Single -> {
                    evaluateSingle(expression.rule, snapshots)
                }
                is AlertExpression.And -> {
                    val leftResult = evaluateExpression(expression.left, snapshots)
                    val rightResult = evaluateExpression(expression.right, snapshots)
                    leftResult && rightResult
                }
                is AlertExpression.Or -> {
                    val leftResult = evaluateExpression(expression.left, snapshots)
                    val rightResult = evaluateExpression(expression.right, snapshots)
                    leftResult || rightResult
                }
                is AlertExpression.Not -> {
                    val innerResult = evaluateExpression(expression.inner, snapshots)
                    !innerResult
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error evaluating expression: ${e.message}", e)
            false
        }
    }

    /**
     * Utvärderar en enskild AlertRule.
     * 
     * @param rule AlertRule att utvärdera
     * @param snapshots Map av symbol -> MarketSnapshot
     * @return true om regeln är sann, false annars
     */
    private fun evaluateSingle(
        rule: AlertRule,
        snapshots: Map<String, MarketSnapshot>
    ): Boolean {
        return when (rule) {
            is AlertRule.PairSpread -> {
                val snapshotA = snapshots[rule.symbolA]
                val snapshotB = snapshots[rule.symbolB]
                if (snapshotA == null || snapshotB == null) {
                    Log.w(TAG, "Missing snapshots for pair spread: ${rule.symbolA}, ${rule.symbolB}")
                    return false
                }
                AlertEvaluator.evaluate(rule, snapshotA, snapshotB)
            }
            is AlertRule.SinglePrice -> {
                val snapshot = snapshots[rule.symbol]
                if (snapshot == null) {
                    Log.w(TAG, "Missing snapshot for single price: ${rule.symbol}")
                    return false
                }
                AlertEvaluator.evaluate(rule, snapshot)
            }
            is AlertRule.SingleDrawdownFromHigh -> {
                val snapshot = snapshots[rule.symbol]
                if (snapshot == null) {
                    Log.w(TAG, "Missing snapshot for drawdown: ${rule.symbol}")
                    return false
                }
                AlertEvaluator.evaluate(rule, snapshot)
            }
            is AlertRule.SingleDailyMove -> {
                val snapshot = snapshots[rule.symbol]
                if (snapshot == null) {
                    Log.w(TAG, "Missing snapshot for daily move: ${rule.symbol}")
                    return false
                }
                AlertEvaluator.evaluate(rule, snapshot)
            }
            is AlertRule.SingleKeyMetric -> {
                val snapshot = snapshots[rule.symbol]
                if (snapshot == null) {
                    Log.w(TAG, "Missing snapshot for key metric: ${rule.symbol}")
                    return false
                }
                AlertEvaluator.evaluate(rule, snapshot)
            }
        }
    }

    /**
     * Validerar att alla nödvändiga snapshots finns för ett uttryck.
     * 
     * @param expression AlertExpression att validera
     * @param snapshots Map av symbol -> MarketSnapshot
     * @return true om alla nödvändiga snapshots finns, false annars
     */
    fun validateSnapshots(
        expression: AlertExpression,
        snapshots: Map<String, MarketSnapshot>
    ): Boolean {
        val requiredSymbols = expression.getSymbols()
        val missingSymbols = requiredSymbols.filter { !snapshots.containsKey(it) }
        
        if (missingSymbols.isNotEmpty()) {
            Log.w(TAG, "Missing snapshots for symbols: $missingSymbols")
            return false
        }
        
        return true
    }
}
