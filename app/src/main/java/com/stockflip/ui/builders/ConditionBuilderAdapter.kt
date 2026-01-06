package com.stockflip.ui.builders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.stockflip.R
import com.stockflip.WatchType

/**
 * Adapter för att visa och redigera villkor i kombinerade alerts.
 */
class ConditionBuilderAdapter(
    private val stockAdapter: ArrayAdapter<*>,
    private val onStockSelected: (Int, String?) -> Unit,
    private val onConditionTypeChanged: (Int, String) -> Unit,
    private val onValueChanged: (Int, String) -> Unit,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<ConditionBuilderAdapter.ConditionViewHolder>() {

    data class ConditionData(
        var symbol: String? = null,
        var conditionType: String = "Pris",
        var direction: String = "Över",
        var value: String = ""
    )

    private val conditions = mutableListOf<ConditionData>()

    fun addCondition() {
        conditions.add(ConditionData())
        notifyItemInserted(conditions.size - 1)
    }

    fun removeCondition(position: Int) {
        if (position in conditions.indices) {
            conditions.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, conditions.size - position)
        }
    }

    fun getConditions(): List<ConditionData> = conditions.toList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConditionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_condition_builder, parent, false)
        return ConditionViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConditionViewHolder, position: Int) {
        holder.bind(conditions[position], position)
    }

    override fun getItemCount(): Int = conditions.size

    inner class ConditionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val conditionNumber = itemView.findViewById<android.widget.TextView>(R.id.conditionNumber)
        private val removeButton = itemView.findViewById<com.google.android.material.button.MaterialButton>(R.id.removeConditionButton)
        private val symbolInput = itemView.findViewById<MaterialAutoCompleteTextView>(R.id.conditionSymbolInput)
        private val conditionTypeInput = itemView.findViewById<MaterialAutoCompleteTextView>(R.id.conditionTypeInput)
        private val directionLayout = itemView.findViewById<TextInputLayout>(R.id.directionLayout)
        private val directionInput = itemView.findViewById<MaterialAutoCompleteTextView>(R.id.conditionDirectionInput)
        private val valueInput = itemView.findViewById<TextInputEditText>(R.id.conditionValueInput)

        fun bind(condition: ConditionData, position: Int) {
            conditionNumber.text = "Villkor ${position + 1}:"
            
            // Setup symbol input
            symbolInput.setAdapter(stockAdapter as ArrayAdapter<*>)
            symbolInput.setText(condition.symbol ?: "")
            symbolInput.setOnItemClickListener { _, _, itemPosition, _ ->
                // Extract symbol from StockSearchResult if needed
                val item = stockAdapter.getItem(itemPosition)
                val symbol = when (item) {
                    is com.stockflip.StockSearchResult -> item.symbol
                    is String -> item
                    else -> symbolInput.text.toString()
                }
                condition.symbol = symbol
                onStockSelected(position, condition.symbol)
            }

            // Setup condition type
            val conditionTypes = arrayOf("Pris", "P/E-tal", "P/S-tal", "Utdelningsprocent", "52w High Drop", "Dagsrörelse")
            val conditionTypeAdapter = ArrayAdapter(itemView.context, android.R.layout.simple_dropdown_item_1line, conditionTypes)
            conditionTypeInput.setAdapter(conditionTypeAdapter)
            conditionTypeInput.setText(condition.conditionType, false)
            conditionTypeInput.setOnItemClickListener { _, _, _, _ ->
                condition.conditionType = conditionTypeInput.text.toString()
                onConditionTypeChanged(position, condition.conditionType)
                updateDirectionVisibility(condition.conditionType)
            }

            // Setup direction
            val directions = arrayOf("Över", "Under")
            val directionAdapter = ArrayAdapter(itemView.context, android.R.layout.simple_dropdown_item_1line, directions)
            directionInput.setAdapter(directionAdapter)
            directionInput.setText(condition.direction, false)
            directionInput.setOnItemClickListener { _, _, _, _ ->
                condition.direction = directionInput.text.toString()
            }

            // Setup value
            valueInput.setText(condition.value)
            valueInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    condition.value = valueInput.text.toString()
                    onValueChanged(position, condition.value)
                }
            }

            // Remove button
            removeButton.setOnClickListener {
                onRemove(position)
            }

            // Initial visibility
            updateDirectionVisibility(condition.conditionType)
        }

        private fun updateDirectionVisibility(conditionType: String) {
            // Dagsrörelse behöver inte riktning (den har UP/DOWN/BOTH istället)
            val needsDirection = conditionType != "Dagsrörelse"
            directionLayout.visibility = if (needsDirection) View.VISIBLE else View.GONE
        }
    }
}

