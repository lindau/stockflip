package com.stockflip.ui.builders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.stockflip.AlertRule
import com.stockflip.R

/**
 * Adapter för att visa och redigera villkor i kombinerade alerts.
 */
class ConditionBuilderAdapter(
    private val onConditionTypeChanged: (Int, String) -> Unit,
    private val onReferenceChanged: (Int, AlertRule.HighReference) -> Unit,
    private val onValueChanged: (Int, String) -> Unit,
    private val onOperatorChanged: (Int, String) -> Unit,
    private val onRemove: (Int) -> Unit
) : RecyclerView.Adapter<ConditionBuilderAdapter.ConditionViewHolder>() {

    data class ConditionData(
        var conditionType: String = "Pris",
        var highReference: AlertRule.HighReference = AlertRule.HighReference.FIFTY_TWO_WEEK_HIGH,
        var direction: String = "Över",
        var value: String = "",
        var operator: String? = null  // AND/OR, null för första villkoret
    )

    private val conditions = mutableListOf<ConditionData>()

    fun addCondition() {
        conditions.add(ConditionData())
        notifyItemInserted(conditions.size - 1)
    }

    fun addCondition(condition: ConditionData) {
        conditions.add(condition)
        notifyItemInserted(conditions.size - 1)
    }

    fun setConditions(newConditions: List<ConditionData>) {
        conditions.clear()
        conditions.addAll(newConditions)
        notifyDataSetChanged()
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
        private val operatorLayout = itemView.findViewById<TextInputLayout>(R.id.operatorLayout)
        private val operatorInput = itemView.findViewById<MaterialAutoCompleteTextView>(R.id.conditionOperatorInput)
        private val conditionTypeInput = itemView.findViewById<MaterialAutoCompleteTextView>(R.id.conditionTypeInput)
        private val highReferenceLayout = itemView.findViewById<TextInputLayout>(R.id.highReferenceLayout)
        private val highReferenceInput = itemView.findViewById<MaterialAutoCompleteTextView>(R.id.conditionHighReferenceInput)
        private val directionLayout = itemView.findViewById<TextInputLayout>(R.id.directionLayout)
        private val directionInput = itemView.findViewById<MaterialAutoCompleteTextView>(R.id.conditionDirectionInput)
        private val valueInput = itemView.findViewById<TextInputEditText>(R.id.conditionValueInput)

        fun bind(condition: ConditionData, position: Int) {
            conditionNumber.text = "Villkor ${position + 1}:"
            
            // Setup operator (visas för alla utom första villkoret)
            val isFirstCondition = position == 0
            operatorLayout.visibility = if (isFirstCondition) View.GONE else View.VISIBLE
            
            if (!isFirstCondition) {
                val operators = arrayOf("OCH", "ELLER")
                val operatorAdapter = ArrayAdapter(itemView.context, android.R.layout.simple_dropdown_item_1line, operators)
                operatorInput.setAdapter(operatorAdapter)
                operatorInput.setText(condition.operator ?: operators[0], false)
                operatorInput.setOnItemClickListener { _, _, _, _ ->
                    condition.operator = operatorInput.text.toString()
                    onOperatorChanged(position, condition.operator ?: "")
                }
            }

            // Setup condition type
	            val conditionTypes = arrayOf("Pris", "P/E-tal", "P/S-tal", "Utdelningsprocent", "Vinst/aktie", "Drawdown", "Dagsrörelse")
            val conditionTypeAdapter = ArrayAdapter(itemView.context, android.R.layout.simple_dropdown_item_1line, conditionTypes)
            conditionTypeInput.setAdapter(conditionTypeAdapter)
            conditionTypeInput.setText(condition.conditionType, false)
            conditionTypeInput.setOnItemClickListener { _, _, _, _ ->
                condition.conditionType = conditionTypeInput.text.toString()
                onConditionTypeChanged(position, condition.conditionType)
                updateReferenceVisibility(condition.conditionType)
                updateDirectionVisibility(condition.conditionType)
            }

            // Setup high reference (visas bara för drawdown)
            val references = arrayOf(
                highReferenceLabel(AlertRule.HighReference.FIFTY_TWO_WEEK_HIGH),
                highReferenceLabel(AlertRule.HighReference.ALL_TIME_HIGH)
            )
            highReferenceInput.setAdapter(ArrayAdapter(itemView.context, android.R.layout.simple_dropdown_item_1line, references))
            highReferenceInput.setText(highReferenceLabel(condition.highReference), false)
            highReferenceInput.setOnItemClickListener { _, _, _, _ ->
                condition.highReference = parseHighReference(highReferenceInput.text.toString())
                onReferenceChanged(position, condition.highReference)
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
            valueInput.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    condition.value = s?.toString() ?: ""
                    onValueChanged(position, condition.value)
                }
            })
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
            updateReferenceVisibility(condition.conditionType)
            updateDirectionVisibility(condition.conditionType)
        }

        private fun highReferenceLabel(reference: AlertRule.HighReference): String {
            return when (reference) {
                AlertRule.HighReference.FIFTY_TWO_WEEK_HIGH -> "52v högsta"
                AlertRule.HighReference.ALL_TIME_HIGH -> "Historiskt högsta"
            }
        }

        private fun parseHighReference(label: String): AlertRule.HighReference {
            return when (label) {
                "Historiskt högsta" -> AlertRule.HighReference.ALL_TIME_HIGH
                else -> AlertRule.HighReference.FIFTY_TWO_WEEK_HIGH
            }
        }

        private fun updateReferenceVisibility(conditionType: String) {
            highReferenceLayout.visibility = if (conditionType == "Drawdown") View.VISIBLE else View.GONE
        }

        private fun updateDirectionVisibility(conditionType: String) {
            // Dagsrörelse och drawdown-villkor har implicit "över tröskel".
            val needsDirection = conditionType != "Dagsrörelse" &&
                conditionType != "Drawdown"
            directionLayout.visibility = if (needsDirection) View.VISIBLE else View.GONE
        }
    }
}
