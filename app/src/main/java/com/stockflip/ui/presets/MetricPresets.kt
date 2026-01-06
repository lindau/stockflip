package com.stockflip.ui.presets

import com.stockflip.MetricHistorySummary
import com.stockflip.PeriodSummary

/**
 * Presets för att sätta tröskelvärden baserat på historisk data.
 * 
 * Enligt PRD Fas 2 ska UI erbjuda presets som:
 * - "Sätt larm under 5-årssnittet"
 * - "Sätt larm under 3-årsmin"
 * - "Sätt larm vid 1-årssnitt − 20%"
 */
object MetricPresets {
    /**
     * Beräknar preset-värde baserat på historik.
     * 
     * @param presetType Typ av preset
     * @param history Historisk sammanfattning
     * @return Preset-värde eller null om historik saknas
     */
    fun getPresetValue(
        presetType: PresetType,
        history: MetricHistorySummary
    ): Double? {
        return when (presetType) {
            PresetType.BELOW_5_YEAR_AVG -> {
                if (history.fiveYear.isEmpty()) null
                else history.fiveYear.average * 0.95 // 5% under snittet
            }
            PresetType.BELOW_3_YEAR_MIN -> {
                if (history.threeYear.isEmpty()) null
                else history.threeYear.min
            }
            PresetType.ONE_YEAR_AVG_MINUS_20 -> {
                if (history.oneYear.isEmpty()) null
                else history.oneYear.average * 0.80 // 20% under snittet
            }
            PresetType.BELOW_1_YEAR_MIN -> {
                if (history.oneYear.isEmpty()) null
                else history.oneYear.min
            }
            PresetType.ABOVE_5_YEAR_MAX -> {
                if (history.fiveYear.isEmpty()) null
                else history.fiveYear.max
            }
            PresetType.ABOVE_3_YEAR_AVG -> {
                if (history.threeYear.isEmpty()) null
                else history.threeYear.average * 1.05 // 5% över snittet
            }
            PresetType.AT_5_YEAR_AVG -> {
                if (history.fiveYear.isEmpty()) null
                else history.fiveYear.average
            }
            PresetType.AT_3_YEAR_AVG -> {
                if (history.threeYear.isEmpty()) null
                else history.threeYear.average
            }
            PresetType.AT_1_YEAR_AVG -> {
                if (history.oneYear.isEmpty()) null
                else history.oneYear.average
            }
        }
    }

    /**
     * Hämtar beskrivning av preset.
     */
    fun getPresetDescription(presetType: PresetType, history: MetricHistorySummary?): String {
        if (history == null) {
            return when (presetType) {
                PresetType.BELOW_5_YEAR_AVG -> "Under 5-årssnittet"
                PresetType.BELOW_3_YEAR_MIN -> "Under 3-årsmin"
                PresetType.ONE_YEAR_AVG_MINUS_20 -> "1-årssnitt − 20%"
                PresetType.BELOW_1_YEAR_MIN -> "Under 1-årsmin"
                PresetType.ABOVE_5_YEAR_MAX -> "Över 5-årsmax"
                PresetType.ABOVE_3_YEAR_AVG -> "Över 3-årssnittet"
                PresetType.AT_5_YEAR_AVG -> "Vid 5-årssnittet"
                PresetType.AT_3_YEAR_AVG -> "Vid 3-årssnittet"
                PresetType.AT_1_YEAR_AVG -> "Vid 1-årssnittet"
            }
        }

        val value = getPresetValue(presetType, history)
        return when (presetType) {
            PresetType.BELOW_5_YEAR_AVG -> "Under 5-årssnittet (${formatValue(value)})"
            PresetType.BELOW_3_YEAR_MIN -> "Under 3-årsmin (${formatValue(value)})"
            PresetType.ONE_YEAR_AVG_MINUS_20 -> "1-årssnitt − 20% (${formatValue(value)})"
            PresetType.BELOW_1_YEAR_MIN -> "Under 1-årsmin (${formatValue(value)})"
            PresetType.ABOVE_5_YEAR_MAX -> "Över 5-årsmax (${formatValue(value)})"
            PresetType.ABOVE_3_YEAR_AVG -> "Över 3-årssnittet (${formatValue(value)})"
            PresetType.AT_5_YEAR_AVG -> "Vid 5-årssnittet (${formatValue(value)})"
            PresetType.AT_3_YEAR_AVG -> "Vid 3-årssnittet (${formatValue(value)})"
            PresetType.AT_1_YEAR_AVG -> "Vid 1-årssnittet (${formatValue(value)})"
        }
    }

    private fun formatValue(value: Double?): String {
        if (value == null) return "N/A"
        return String.format("%.2f", value)
    }
}

/**
 * Typer av presets för nyckeltal-bevakningar.
 */
enum class PresetType {
    BELOW_5_YEAR_AVG,        // Under 5-årssnittet
    BELOW_3_YEAR_MIN,         // Under 3-årsmin
    ONE_YEAR_AVG_MINUS_20,   // 1-årssnitt − 20%
    BELOW_1_YEAR_MIN,         // Under 1-årsmin
    ABOVE_5_YEAR_MAX,         // Över 5-årsmax
    ABOVE_3_YEAR_AVG,        // Över 3-årssnittet
    AT_5_YEAR_AVG,           // Vid 5-årssnittet
    AT_3_YEAR_AVG,           // Vid 3-årssnittet
    AT_1_YEAR_AVG            // Vid 1-årssnittet
}

